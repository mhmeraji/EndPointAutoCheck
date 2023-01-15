(ns endpoint-autocheck.api.routes
  (:require [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [tick.core :as tick]

            [cheshire.core :as cheshire]

            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.http.content-negotiation :as contneg]

            [ring.util.response :as ring-resp]
            [clojure.string :as str]

            [endpoint-autocheck.api.auth :as auth]

            [taoensso.timbre :as timbre]
            [endpoint-autocheck.api.v1.routes :as v1]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- home-page
  [_]
  (ring-resp/response "Pong!"))

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
  {:name ::no-cache-interceptor
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

                (= ctype "application/edn") (update-in [:response :body] prn-str)

                (= ctype "application/edn") (update-in [:response] #(ring-resp/content-type % "application/edn")))))})

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
  []
  {:name  :web-error-handler
   :error (fn [ctx ex]

            (let [ex-d (-> (ex-data ex)
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
                             {:status  500
                              :headers {}
                              :body    {:message "UNKNOWN ERROR"
                                        :reason  (ex-message ex)}})]

              (-> ctx
                  (assoc :response response))))})

(def auth-interceptors
  #{:login-interceptor
    :logout-interceptor})

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn routes [web-db web-state
              {:keys [http-omit-added-headers?] :as _config}]
  [[:van-buren-web :http
    ["/epcheck"
     ^:interceptors [client-remote-addr-interceptor
                     no-cache-interceptor
                     body-coercion-interceptor
                     content-negotiation-interceptor
                     (web-error-handler)
                     ;; log-everything-interceptor
                     (auth/authorization-error-handler
                       auth/auth-backend)
                     (body-params/body-params)]
     {:get `home-page}
     ["/register"
      {:post (auth/register-interceptor
               web-db)}]
     ["/login"
      {:post (auth/login-interceptor
               web-db http-omit-added-headers?)}]
     ["/logout"     ^:interceptors
      [(auth/authentication-interceptor web-db)
       auth/authorization-interceptor]
      {:post (auth/logout-interceptor web-db)}]
     ["/check-login"
      ^:interceptors [(auth/authentication-interceptor web-db)
                      auth/authorization-interceptor]
      {:get (auth/check-login-interceptor)}]
     ["/api"
      ^:interceptors [(auth/authentication-interceptor web-db)
                      auth/authorization-interceptor
                      (ring-mw/multipart-params)]
      (v1/routes web-db)]]]])

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
