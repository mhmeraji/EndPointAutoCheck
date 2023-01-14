(ns endpoint-autocheck.api.auth
  (:require
   [buddy.sign.jwe :as jwe]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str]

   [buddy.auth :as buddy.auth]
   [buddy.hashers :as hashers]
   [buddy.auth.middleware :as middleware]
   [buddy.auth.protocols :as bauth.proto]
   [buddy.auth.http :as buddy.http]

   [buddy.core.codecs :as codecs]

   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.error :as error]
   [io.pedestal.interceptor.chain :as interceptor.chain]

   [tick.core :as tick]

   [ring.middleware.cookies :as ring-cookies]

   [endpoint-autocheck.db.protocol :as db.proto]

   [ring.util.response :as ring-resp]

   [taoensso.timbre :as timbre]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

;; NOTE that this should move to a proper location
(def secret "myjwtsecretpublickeywithextracha")

;;Utility
(defn inside?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(def auth-backend
  (reify
    bauth.proto/IAuthentication
    (-parse [_ request]
      (some->> (buddy.http/-get-header request "authorization")
               (re-find (re-pattern (str "^Token (.+)$")))
               (second)))

    (-authenticate [_ request data]
      (-> data
          (jwe/decrypt secret)
          codecs/bytes->str
          (jwt/unsign secret)))

    bauth.proto/IAuthorization
    (-handle-unauthorized [_ request metadata]
      (if (buddy.auth/authenticated? request)
        {:status 403 :headers {} :body (->
                                         {:message "Permission denied"}
                                         (merge metadata))}
        {:status 401 :headers {} :body (->
                                         {:message "Unauthorized"}
                                         (merge metadata))}))))


(defn- cookie-ring-adapter[request]
  (assoc-in request [:headers "Authorization"]
            (-> request :cookies (get "token") :value)))


(defn- validate-request-token-and-identity [ctx db]
  (let [token (-> ctx :request :cookies (get "token") :value)


        _ (when (nil? token)
            (buddy.auth/throw-unauthorized
              {:reason "no token!"}))

        username (-> ctx :request :identity :username)

        _ (when (nil? username)
            (buddy.auth/throw-unauthorized
              {:reason "No user identity"}))

        now (tick/now)

        valid-until (-> ctx :request :identity :valid-until)

        _ (when (or (nil? valid-until)
                    (tick/<= (tick/instant valid-until)
                             now))
            (db.proto/register-user-logout!
              db
              username
              (-> ctx :request :remote-addr)
              (tick/instant valid-until)
              true)
            (buddy.auth/throw-unauthorized
              {:reason "session token expired"}))

        user-data (db.proto/find-user-by-username db username)

        token-map (->> user-data
                       :tokens
                       (filter
                         #(= (:token %) token))
                       first)


        _ (when (nil? user-data)
            (buddy.auth/throw-unauthorized
              {:reason "user identity not recognized"}))

        _ (when (:is-blocked? user-data)
            (buddy.auth/throw-unauthorized
              {:reason "user is blocked by system admin"}))

        _ (when (-> (inside? (->> (:tokens user-data)
                                  (map :token)) token) not)
            (buddy.auth/throw-unauthorized
              {:reason "token mismatch"}))

        _ (when (or (nil? (:token-valid-until token-map))
                    (tick/<=
                      (tick/instant
                        (:token-valid-until token-map))
                      now))
            (buddy.auth/throw-unauthorized
              {:reason "user session expired"}))]

    ctx))

(defn authentication-interceptor
  "Port of buddy-auth's wrap-authentication middleware."
  [db]
  {:name  ::authenticate
   :enter (fn [ctx]
            (-> ctx
                (update :request ring-cookies/cookies-request auth-backend)
                (update :request cookie-ring-adapter)
                (update :request middleware/authentication-request auth-backend)
                (validate-request-token-and-identity db)))})

(def authorization-interceptor
  "Port of buddy-auth's wrap-authorization middleware."
  {:name  ::authorize
   :enter (fn [ctx]
            (if-let [userdata (-> ctx :request :identity)]
              ctx
              (buddy.auth/throw-unauthorized {:reason "no user identity"})))})


(defn- auth-error [ctx ex backend]
  (try
    (assoc ctx
           :response
           (middleware/authorization-error (:request ctx)
                                           ex
                                           backend))
    (catch Exception e
      (assoc ctx ::interceptor.chain/error e))))

(defn authorization-error-handler
  "Port of buddy-auth's wrap-authorization middleware."
  [backend]
  (error/error-dispatch [ctx ex]
                        [{:exception-type :clojure.lang.ExceptionInfo
                          :interceptor    ::authorize}]
                        (auth-error ctx ex backend)
                        [{:exception-type :clojure.lang.ExceptionInfo
                          :interceptor    ::authenticate}]
                        (auth-error ctx ex backend)
                        [{:stage          :enter :interceptor :login-interceptor
                          :exception-type :clojure.lang.ExceptionInfo}]
                        (auth-error ctx ex backend)
                        :else (assoc ctx ::interceptor.chain/error ex)))


(defn- check-that-user-exists [db username password]
  (do
    (when (or (nil? username)
              (nil? password))
      (throw (ex-info "wrong or nil credentials." {})))
    (if-let [user (db.proto/find-user-by-username-password
                    db username password)]
      user
      (throw (ex-info "wrong credentials"
                      {:username username})))))

(defn- create-user-token [user]
  {:username    (:username user)
   :role        (:role user)
   :valid-until (str (tick/>> (tick/now)
                              (tick/new-duration 8 :hours)))})

(defn register-interceptor [db]
  (interceptor/interceptor
    {:name  :register-interceptor
     :enter (fn register [context]
              (try
                (let [{:keys
                       [username
                        max-session-count
                        name
                        role
                        password]}
                      (-> context :request :json-params)

                      user-record
                      {:name              name
                       :username          username
                       :password          (hashers/encrypt password)
                       :role              role
                       :max-session-count max-session-count}]

                  (if (db.proto/user-exists? db username)
                    (throw (ex-info "Username Already Exists"
                                    {:username username
                                     :name     name}))
                    (db.proto/insert-user! db user-record))

                  (assoc context :response
                         (ring-resp/response
                           {:status   "OK"
                            :username username
                            :role     role})))
                (catch Exception e
                  (timbre/error ["Caught An Error When Registering : " e])
                  (let [e-message (ex-message e)
                        e-data    (ex-data e)]
                    (assoc context :response
                           (ring-resp/bad-request
                             (assoc e-data
                                    :status "Couldn't Register User!"
                                    :cause   e-message)))))))}))

(defn login-interceptor [db http-omit-added-headers? ]
  (interceptor/interceptor
    {:name  :login-interceptor
     :enter (fn login [context]
              (let [{:keys
                     [username
                      password]}
                    (-> context :request :json-params)

                    user (check-that-user-exists
                           db
                           username password)

                    active-session-count (db.proto/get-active-session-count db username)

                    user-maximum-session-count (-> (db.proto/find-user-by-username db username)
                                                   :max-session-count)

                    filtering-count (if (nil? user-maximum-session-count)
                                      0
                                      user-maximum-session-count)

                    deciding-umsc (if (some? user-maximum-session-count)
                                    user-maximum-session-count
                                    -1)

                    _ (when (> (+ active-session-count  1)
                               deciding-umsc)
                        (db.proto/keep-n-newest-sessions db username filtering-count))

                    _ (db.proto/register-user-login!
                        db
                        username
                        (-> context :request :remote-addr)
                        (-> context :request :headers (get "user-agent"))
                        (tick/now))]

                (let [token (create-user-token user)

                      token-signed (str "Token " (jwe/encrypt
                                                   (jwt/sign token secret)
                                                   secret))


                      _ (db.proto/update-user-session!
                          db username token-signed
                          (:valid-until token)
                          (-> context :request :remote-addr))]
                  (assoc context :response
                         (-> (ring-resp/response {:status   "OK"
                                                  :username username
                                                  :token    token-signed
                                                  :role     (:role user)})
                             (ring-resp/set-cookie
                               "token" token-signed
                               {:secure    (not http-omit-added-headers?)
                                :http-only (not http-omit-added-headers?)})
                             (ring-cookies/cookies-response))))))}))

(defn logout-interceptor
  [db]
  (interceptor/interceptor
    {:name  :logout-interceptor
     :enter (fn [ctx]
              (let [username (-> ctx :request :identity :username)

                    token (-> ctx :request :cookies (get "token") :value)

                    _ (db.proto/remove-user-session-token!
                        db username token)

                    _ (db.proto/register-user-logout!
                        db
                        username
                        (-> ctx :request :remote-addr)
                        (tick/now))])
              (assoc ctx :response
                     (-> (ring-resp/response {:status "OK"})
                         (ring-resp/set-cookie "token" "Token deleted!")
                         (ring-cookies/cookies-response))))}))

(defn check-login-interceptor
  []
  (interceptor/interceptor
    {:name  :login-check-interceptor
     :enter (fn [ctx]
              (let [username (-> ctx :request :identity :username)
                    role     (-> ctx :request :identity :role)]
                (assoc ctx :response (ring-resp/response {:status   "OK"
                                                          :username username
                                                          :role     role}))))}))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
