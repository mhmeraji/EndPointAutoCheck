 (ns stellar.van-buren.web.api.routes
  (:require [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [io.pedestal.http.route :as route]
            [tick.core :as tick]

            [cheshire.core :as cheshire]

            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.http.content-negotiation :as contneg]

            [ring.util.response :as ring-resp]
            [clojure.string :as str]

            [stellar.van-buren.web.api.v1.routes :as v1]
            [stellar.van-buren.web.api.auth :as auth]
            [stellar.van-buren.web.api.admin.routes :as admin]
            [stellar.van-buren.web.makna-state.protocol :as makna-state.proto]

            [taoensso.timbre :as timbre]))

(defn- home-page
  [request]
  (ring-resp/response "Hello World!"))

(def log-everything-interceptor
  {:name  ::log-everything-interceptor
   :enter (fn [context] (timbre/info "ENTER -> " (-> context :request)) context)
   :leave (fn [context] (timbre/info "LEAVE -> " context) context)
   :error (fn [context ex]
          (timbre/info "Error ->>" (ex-data ex))
            (-> context
                (assoc ::interceptor.chain/error ex)))})

(def client-remote-addr-interceptor
  {:name  ::client-remote-addr-interceptor
   :enter (fn [context]
            (let [remote-addr
                  (or (get-in context
                              [:request :headers
                               "x-forwarded-for"])
                      (get-in context [:request :remote-addr]))

                  remote-addr
                  (str/replace
                    (last (str/split remote-addr #"\,"))
                    #" " "")]

              (-> context
                  (assoc-in [:request :remote-addr]
                            remote-addr))))})

(def no-cache-interceptor
  {:name  ::no-cache-interceptor
   :leave
   (fn [context]
     (-> context
         (assoc-in [:response :headers "Cache-Control"]
                   "no-cache, no-store, must-revalidate")
         (assoc-in [:response :headers "Pragma"]
                   "no-cache")
         (assoc-in [:response :headers "Expires"]
                   "0")))})

(def body-coercion-interceptor
  {:name  ::body-coercion-interceptor
   :leave (fn [context]
            (let [ctype (get-in context [:request :headers "accept"] "application/json")]
              (cond-> context
                (= ctype "application/json") (update-in [:response :body] cheshire/encode)
                (= ctype "application/json") (update-in [:response] #(ring-resp/content-type % "application/json"))

                (= ctype "application/edn")  (update-in [:response :body] prn-str)

                (= ctype "application/edn")  (update-in [:response] #(ring-resp/content-type % "application/edn")))))})

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(def supported-types
  ["application/edn" "application/json" "application/octet-stream" "application/csv" "application/.xlsx"])

(def content-negotiation-interceptor
  (contneg/negotiate-content
    supported-types
    {:no-match-fn (fn [context]
                    (throw (Exception. "Content Negotiation : No Match!")))}))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn web-error-handler
  [makna-state]
  {:name :web-error-handler
   :error (fn [ctx ex]

            (let [ctx (-> ctx
                          (assoc-in [:request :json-params :password]
                                    "******")
                          (assoc-in [:request :json-params :new-password]
                                    "******")
                          (assoc-in [:request :json-params :repeat-new-password]
                                    "******")
                          (assoc-in [:request :json-params :password]
                                    "******")
                          (assoc-in [:request :json-params :code-2fa]
                                    "******"))
                  _ (timbre/spy :error [ctx "\n=\n" ex])
                  ex-d (-> (ex-data ex)
                           :exception
                           Throwable->map)

                  data (:data ex-d)

                  message (:cause ex-d)

                  response (cond
                             (some? data)
                             (ring-resp/bad-request
                              {:message message
                               :data    data})
                             :else
                             {:status 500
                              :headers {}
                              :body {:message "UNKNOWN ERROR"
                                     :reason (ex-message ex)}})

                  lce-log-record  (-> ctx :request :lce-log-record)
                  auth-log-record (-> ctx :request :auth-log-record)
                  res-time        (str (tick/now))
                  status          (-> response :status)
                  explanation     (-> response :body :message)
                  complete-log-record
                  (merge
                    (or lce-log-record auth-log-record)
                    {:Result       (if (or
                                         (and
                                           (some? lce-log-record)
                                           (= status 200)
                                           (= :cmd-success
                                              (-> response :body :type)))
                                         (and
                                           (some? auth-log-record)
                                           (= status 200)))
                                     (->> "successful")
                                     (->> "failure"))
                     :ResponseTime res-time
                     :Explanation  explanation})]
              (when (and
                      (some? makna-state)
                      (some? complete-log-record))
                (makna-state.proto/conjugate-item!
                  makna-state complete-log-record))
              (-> ctx
                  (assoc :response response)
                  (assoc-in [:request :lce-log-record]
                            complete-log-record))))})

(def auth-interceptors
  #{:login-interceptor
    :logout-interceptor
    :send-verification-code-interceptor
    :change-password-interceptor})

(defn makna-lce-logger
  [makna-state]
  {:name  :makna-lce-logger
   :enter (fn [context]
            (try
              (if (and
                    (not= (-> context :route :method) :get)
                    (not (contains?
                           auth-interceptors
                           (-> context :route :route-name))))
                (let [route         (:route context)
                      request       (:request context)
                      path          (:path route)
                      method        (name (:method route))
                      username      (-> request :identity :username)
                      role          (-> request :identity :role)
                      user-ip       (-> request :remote-addr)
                      tag           (-> request :path-params :id)
                      api-id        (-> route :route-name)
                      req-time      (str (tick/now))]

                  (-> context
                      (assoc-in [:request :lce-log-record]
                                {:UserID      username
                                 :UserIP      user-ip
                                 :UserRole    role
                                 :AlgorithmID tag
                                 :APIID       api-id
                                 :RequestTime req-time
                                 :RequestPath path
                                 :Method      method})))
                (-> context))
              (catch Exception e
                (timbre/error ["Error In LCE Makna Logger Enter Stage" e])
                (-> context))))
   :leave (fn [context]
            (try
              (if (and
                    (not= (-> context :route :method) :get)
                    (some? (-> context :request :lce-log-record)))
                (let [response   (-> context :response)
                      status     (-> response :status)
                      log-record (-> context :request :lce-log-record)

                      res-time   (str (tick/now))
                      result     (if (or
                                       (and
                                         (= status 200)
                                         (= (-> context :route :route-name)
                                            :create-instances-interceptor))
                                       (and
                                         (= status 200)
                                         (= :cmd-success
                                            (-> response :body :type))))
                                   (->> "successful")
                                   (->> "failure"))

                      complete-log-record
                      (merge
                        log-record
                        {:Result       result
                         :ResponseTime res-time})]
                  (when (and
                          (some? complete-log-record)
                          (some? makna-state))
                    (makna-state.proto/conjugate-item!
                      makna-state complete-log-record))
                  (-> context
                      (assoc-in [:request :lce-log-record]
                                 complete-log-record)))
                (-> context))
              (catch Exception e
                (timbre/error ["Error In LCE Makna Logger Leave Stage" e])
                (-> context))))})

(defn makna-auth-logger
  [makna-state]
  {:name  :makna-auth-logger
   :enter (fn [context]
            (try
              (if (contains?
                    auth-interceptors
                    (-> context :route :route-name))
                (let [route         (:route context)
                      request       (:request context)
                      path          (:path route)
                      method        (name (:method route))
                      username      (or
                                      (-> request :json-params :username)
                                      (-> request :identity :username))
                      role          (-> request :identity :role)
                      user-ip       (-> request :remote-addr)
                      tag           (-> request :path-params :id)
                      api-id        (-> route :route-name)
                      req-time      (str (tick/now))]

                  (-> context
                      (assoc-in [:request :auth-log-record]
                                {:UserID      username
                                 :UserIP      user-ip
                                 :UserRole    role
                                 :AlgorithmID tag
                                 :APIID       api-id
                                 :RequestTime req-time
                                 :RequestPath path
                                 :Method      method})))
                (-> context))
              (catch Exception e
                (timbre/error ["Error In Auth Makna Logger Enter Stage" e])
                (-> context))))
   :leave (fn [context]
            (try
              (if (and
                    (not= (-> context :route :method) :get)
                    (some? (-> context :request :auth-log-record)))
                (let [response   (-> context :response)
                      status     (-> response :status)
                      request    (-> context :request)
                      log-record (-> context :request :auth-log-record)

                      log-record (if (nil? (:UserID log-record))
                                   (-> log-record
                                       (assoc :UserID
                                              (-> request :identity :username))
                                       (assoc :UserRole
                                              (-> request :identity :role)))
                                   (->> log-record))

                      res-time   (str (tick/now))
                      result     (if (= status 200)
                                   (->> "successful")
                                   (->> "failure"))

                      complete-log-record
                      (merge
                        log-record
                        {:Result       result
                         :ResponseTime res-time})]
                  (when (and
                          (some? makna-state)
                          (some? complete-log-record))
                    (makna-state.proto/conjugate-item!
                      makna-state complete-log-record))
                  (-> context
                      (assoc-in [:request :auth-log-record]
                                 complete-log-record)))
                (-> context))
              (catch Exception e
                (timbre/error ["Error In Auth Makna Logger Leave Stage" e])
                (-> context))))})

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn routes [im-da web-db lobster-da aggregate-da sms-sender
              {:keys [http-omit-added-headers?] :as _config}
              makna-state]
  [[:van-buren-web :http
    ["/web"
     ^:interceptors [client-remote-addr-interceptor
                     no-cache-interceptor
                     body-coercion-interceptor
                     content-negotiation-interceptor
                     (web-error-handler makna-state)
                     ;; log-everything-interceptor
                     (auth/authorization-error-handler
                      auth/auth-backend)
                     (body-params/body-params)
                     (makna-auth-logger makna-state)]
     {:get `home-page}
     ["/login"
      {:post (auth/login-interceptor
               web-db http-omit-added-headers?)}]
     ["/logout"     ^:interceptors
      [(auth/authentication-interceptor web-db)
       auth/authorization-interceptor]
      {:post (auth/logout-interceptor web-db)}]
     ["/captcha"
      {:get (auth/get-captcha-image-interceptor web-db)}]
     ["/code-for-2fa"
      {:post (auth/send-verification-code-interceptor
               web-db sms-sender)}]
     ["/change-password"
      {:post (auth/change-password-interceptor
               web-db http-omit-added-headers?)}]
     ["/check-login"
      ^:interceptors [(auth/authentication-interceptor web-db)
                      auth/authorization-interceptor]
      {:get  (auth/check-login-interceptor)}]
     ["/auth-activity"
      ^:interceptors [(auth/authentication-interceptor web-db)
                      auth/authorization-interceptor]
      {:get  (auth/get-auth-activity-interceptor web-db)}]
     ["/api"
      ^:interceptors [(auth/authentication-interceptor web-db)
                      auth/authorization-interceptor
                      (makna-lce-logger makna-state)
                      (ring-mw/multipart-params)]
      (v1/routes im-da web-db lobster-da aggregate-da)
      (admin/routes web-db im-da lobster-da sms-sender aggregate-da)
      (admin/ifb-routes web-db im-da lobster-da)]]]])
