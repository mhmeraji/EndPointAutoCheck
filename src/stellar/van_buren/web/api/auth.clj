(ns stellar.van-buren.web.api.auth
  (:require
   [buddy.sign.jwe :as jwe]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str]

   [buddy.auth :as buddy.auth]

   [buddy.auth.middleware :as middleware]
   [buddy.auth.protocols :as bauth.proto]
   [buddy.auth.http :as buddy.http]

   [buddy.core.codecs :as codecs]

   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.error :as error]
   [io.pedestal.interceptor.chain :as interceptor.chain]

   [tick.core :as tick]

   [ring.middleware.cookies :as ring-cookies]

   [stellar.van-buren.web.db.protocol :as db.proto]

   [ring.util.response :as ring-resp]

   [stellar.van-buren.web.external-comm.sms-protocol :as extsms-proto]

   [taoensso.timbre :as timbre])
  (:import java.util.Base64
           java.io.ByteArrayOutputStream))

;; TODO: remove
;; (def secret stellar.van-buren.web.auth/secret)
(def secret "mysecretauthsecretwithlonglength")

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


;; TODO: This is a travesty!!!!!
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
            ;; TODO: FIXME: HAPPENS REPEATEDLY
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
                          :interceptor ::authorize}]
                        (auth-error ctx ex backend)
                        [{:exception-type :clojure.lang.ExceptionInfo
                          :interceptor ::authenticate}]
                        (auth-error ctx ex backend)
                        [{:stage :enter :interceptor :login-interceptor
                          :exception-type :clojure.lang.ExceptionInfo}]
                        (auth-error ctx ex backend)
                        :else (assoc ctx ::interceptor.chain/error ex)))


(defn- check-that-user-exists [db username password]
  (do
    (when (or (nil? username)
              (nil? password))
      (throw (ex-info "wrong or nil credentials." {})))
    (if-let [user          (db.proto/find-user-by-username-password
                            db username password)]
      user
      (throw (ex-info "wrong credentials"
                      {:username username})))))

(defn- create-user-token [user]
  ;; TODO: put durations in config
  {:username (:username user)
   :role     (:role user)
   :valid-until (str (tick/>> (tick/now)
                              (tick/new-duration 8 :hours)))})

(defn- rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn render-image [message]
  (let [width      150 height 92
        bi         (java.awt.image.BufferedImage.
                     width height
                     java.awt.image.BufferedImage/TYPE_INT_ARGB)
        font       (java.awt.Font. "TimesRoman" java.awt.Font/BOLD 20)
        ig         (doto ^java.awt.Graphics2D
                       (.createGraphics bi)
                     (.setFont font))

        metrics    (.getFontMetrics ig)
        str-width  (.stringWidth metrics message)
        str-height (.getAscent metrics)]
    (doto ig
      (.setPaint java.awt.Color/BLACK)
      (.drawString ^String message
                   (int (/ (- width str-width) 2))
                   (int (+ (/ height 2) (/ str-height 4)))))
    bi))

(defn- <-generate-captcha!
  [web-db]
  (let [code (rand-str 5)

        pid (db.proto/register-captcha-puzzle! web-db code)

        img (render-image code)
        bs  (ByteArrayOutputStream.)
        _   (javax.imageio.ImageIO/write
              ^java.awt.image.BufferedImage img
              "PNG" bs)

        imgstr (.encodeToString
                 (Base64/getEncoder)
                 (.toByteArray bs))]
    (->> {:pid  pid
          :pimg imgstr})))

(defn- check-2fa-code [db username code-2fa]
  (let [rec (db.proto/find-user-by-username db username)]
    (when-not (= true (:skip-check-2fa? rec))
      (when (and (some? (:error-count-2fa rec))
                 (> (:error-count-2fa rec) 2))
        (throw (ex-info "2FA Error count exceeded"  {:username username})))
      (when (nil? (:initiation-time-for-2fa rec))
        (throw (ex-info "2FA code not initialized!" {:username username})))
      (when (tick/> (tick/now)
                    (tick/>> (tick/instant (:initiation-time-for-2fa rec))
                             (tick/new-duration 5 :minutes)))
        (throw (ex-info "2FA code expired!" {:username username})))
      (when-not (= (:code-for-2fa rec) code-2fa)
        (db.proto/increment-2fa-error-count! db username)
        (throw (ex-info "Incorrect-code" {:username username}))))))

(defn- check-captcha [db pid input]
  (let [rec (db.proto/find-captcha-puzzle db pid)]
    (when (or
            (not (string? input))
            (nil? rec)
            (:invalid? rec))
      (throw (ex-info "Invalid captcha." {:pid pid})))
    (when (and (some? (:error-count rec))
               (> (:error-count rec) 2))
      (throw (ex-info "Expired captcha."  {:pid pid})))
    (when-not (= (:code rec) (.toUpperCase ^String input))
      (throw (ex-info "Incorrect-code" {:code input})))
    (db.proto/invaidate-captcha! db pid)
    (<-generate-captcha! db)))

(defn login-interceptor [db http-omit-added-headers? ]
  (interceptor/interceptor
   {:name  :login-interceptor
    :enter (fn login [context]
             (let [{:keys
                    [username
                     password
                     code-2fa
                     captcha
                     captcha-pid]}
                   (-> context :request :json-params)

                  ;; NOTE: throw their own exceptions
                   user        (try
                                 (let [_   (check-captcha
                                            db captcha-pid captcha)
                                       user (check-that-user-exists
                                             db
                                             username password)
                                       _   (check-2fa-code db username code-2fa)]
                                   user)
                                 (catch clojure.lang.ExceptionInfo e
                                   (db.proto/register-failed-login!
                                    db
                                    username
                                    (-> context :request :remote-addr)
                                    (-> context :request :headers
                                        (get "user-agent"))
                                    (tick/now)
                                    (ex-message e))
                                   (throw e)))

                   active-session-count (db.proto/get-active-session-count db username)

                   user-maximum-session-count (-> (db.proto/find-user-by-username db username)
                                                  :max-session-count)

                   filtering-count (if (nil? user-maximum-session-count)
                                     0
                                     user-maximum-session-count)

                   deciding-umsc (if (some? user-maximum-session-count)
                                   user-maximum-session-count
                                   -1 ;;Meraj said this
                                   )

                   _             (when (> (+ active-session-count  1)
                                          deciding-umsc)
                                   (db.proto/keep-n-newest-sessions db username filtering-count))

                   _           (db.proto/register-user-login!
                                db
                                username
                                (-> context :request :remote-addr)
                                (-> context :request :headers (get "user-agent"))
                                (tick/now))

                  ;; TODO: put durations + secret in config
                   need-pswd-update?
                   (or (nil? (:last-password-update user))
                       (tick/<
                        (tick/>> (tick/instant
                                  (:last-password-update user))
                                 (tick/new-duration 90 :days))
                        (tick/now)))]

               (if need-pswd-update?
                 (assoc context :response
                        {:status 401
                         :headers {}
                         :body {:status  "FAILED"
                                :message :require-password-update
                                :username username}})
                 (let [token       (create-user-token user)

                       token-signed (str "Token " (jwe/encrypt
                                                   (jwt/sign token secret)
                                                   secret))


                       _           (db.proto/update-user-session!
                                    db username token-signed
                                    (:valid-until token)
                                    (-> context :request :remote-addr))]
                   (assoc context :response
                          (-> (ring-resp/response {:status "OK"
                                                   :username username
                                                   :role     (:role user)})
                              (ring-resp/set-cookie
                                "token" token-signed
                                {:secure (not http-omit-added-headers?)
                                 :http-only (not http-omit-added-headers?)})
                              (ring-cookies/cookies-response)))))))}))

(defn logout-interceptor
  [db]
  (interceptor/interceptor
   {:name :logout-interceptor
    :enter (fn [ctx]
             (let [username (-> ctx :request :identity :username)

                   token (-> ctx :request :cookies (get "token") :value)

                   _        (db.proto/remove-user-session-token!
                             db username token)

                   _        (db.proto/register-user-logout!
                             db
                             username
                             (-> ctx :request :remote-addr)
                             (tick/now))])
             (assoc ctx :response
                    (-> (ring-resp/response {:status "OK"})
                        (ring-resp/set-cookie "token" "Token deleted!")
                        (ring-cookies/cookies-response))))}))

(defn get-auth-activity-interceptor
  [db]
  (interceptor/interceptor
   {:name :get-auth-activity-interceptor
    :enter (fn [ctx]
             (let [username (-> ctx :request :identity :username)
                   auth-activity (into []
                                       (db.proto/get-user-auth-activity
                                         db username))

                   auth-activity
                   (->> auth-activity
                        (map
                          #(update % :time
                                   (fn [time]
                                     (-> time
                                         (tick/date-time)
                                         (tick/in "Asia/Tehran")
                                         (str)
                                         (str/split #"\+")
                                         (first)))))
                        (into []))]

               (assoc ctx :response (ring-resp/response auth-activity))))}))

(defn check-login-interceptor
  []
  (interceptor/interceptor
   {:name :login-check-interceptor
    :enter (fn [ctx]
             (let [username (-> ctx :request :identity :username)
                   role    (-> ctx :request :identity :role)]
               (assoc ctx :response (ring-resp/response {:status "OK"
                                                         :username username
                                                         :role     role}))))}))

(defn- re-contains? [re s] (some? (re-find re s)))

(defn- in-range? [n min max]
  (and (>= n min) (< n max)))
(defn- contains-symbol?
  "return boolean whether password contain symbols or not"
  [s]
  (some?
   (some #(let [in? (partial in-range? (int %))]
            (or (in? 33 48) (in? 58 65) (in? 91 96) (in? 123 127)))
         s)))


(defn- password-strength [password]
  (let [len   (count password)
        kinds (cond-> 0
                (re-contains? #"[a-z]" password)
                inc
                (re-contains? #"[A-Z]" password)
                inc
                (re-contains? #"[0-9]" password)
                inc
                (contains-symbol? password)
                inc)]
    (if (< len 8)
      (min (* len kinds 2) 15)
      (min (* len kinds 3) 100))))

(defn- validate-new-password
  "Validates a password on the grounds of:
  * Being at least 8 chars
  * Being \"fairly strong\"
  * TBD"
  [new-password old-password]
  (when (= new-password old-password)
    (throw (ex-info "You can't re-use your old password!" {})))
  (when (< (count new-password) 8)
    (throw (ex-info "Password too short!" {:password new-password})))
  (when (< (password-strength new-password) 16)
    (throw (ex-info "Password too weak!" {:password new-password})))
  (when-not (and (or (re-contains? #"[a-z]" new-password)
                     (re-contains? #"[A-Z]" new-password))
                 (re-contains? #"[0-9]" new-password))
    (throw (ex-info "Password must include letters and numbers." {:password new-password})))
  new-password)

(defn get-captcha-image-interceptor [web-db]
  (interceptor/interceptor
    {:name  :get-captcha-image-interceptor
     :enter (fn captchimage [context]
              (let [c-map (<-generate-captcha! web-db)
                    response
                    (ring-resp/response c-map)]
                (assoc context :response response)))}))

(defn- rand-number-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 10) 48))))))

(defn- check-2fa-request-rate-limit [init-2fa]
  (when (tick/< (tick/now)
                (tick/>> (tick/instant init-2fa)
                         (tick/new-duration 30 :seconds)))
    (throw (ex-info "Too many 2fa code requests!
                     You Have To Wait 30 Seconds"
                    {:last-request init-2fa}))))

(defn send-verification-code-interceptor [db sms-sender]
  (interceptor/interceptor
   {:name  :send-verification-code-interceptor
    :enter (fn login [context]
             (let [{:keys
                    [username
                     password
                     captcha
                     captcha-pid]} (-> context :request :json-params)

                  ;; NOTE: throw their own exceptions
                   user        (check-that-user-exists db username password)

                   init-2fa    (:initiation-time-for-2fa user)

                   _           (when (some? init-2fa)
                                 (check-2fa-request-rate-limit init-2fa))
                   _           (check-captcha db captcha-pid captcha)
                   user-phone# (:phone-number user)

                   _           (when (nil? user-phone#)
                                 (throw (ex-info "No phone number registered."
                                                 {:username username})))

                   code        (rand-number-str 5)

                   _           (db.proto/set-user-2fa-code!
                                 db username code
                                 (str (tick/now)))

                   _           (extsms-proto/send-verification-code!
                                sms-sender user-phone# code)]
               (assoc context :response
                      (-> (ring-resp/response {:status "OK"})))))}))

(defn- password-valid?
  [new-password]
  (if (str/includes? new-password "#")
    (throw (ex-info "Password contains sharp sign which is invalid"
                    {:pass new-password}))
    (->> true)))

(defn change-password-interceptor [db http-omit-added-headers?]
  (interceptor/interceptor
   {:name  :change-password-interceptor
    :enter (fn login [context]
             (let [{:keys
                    [username
                     password
                     new-password
                     code-2fa
                     captcha
                     captcha-pid]} (-> context :request :json-params)

                   _           (check-captcha db captcha-pid captcha)

                   _ (password-valid? new-password)

                   ;; NOTE: throw their own exceptions
                   user        (check-that-user-exists db username password)
                   _           (check-2fa-code db username code-2fa)
                   _           (validate-new-password new-password password)
                   token       (create-user-token user)

                   ;; TODO: put secrets in their own place
                   token-signed (str "Token " (jwe/encrypt
                                               (jwt/sign token secret)
                                               secret))

                   _           (db.proto/update-user-password!
                                 db username new-password (str (tick/now)))

                   _           (db.proto/update-user-session!
                                 db username token-signed
                                 (:valid-until token)
                                 (-> context :request :remote-addr))]
               (assoc context :response
                      (-> (ring-resp/response {:status "OK"
                                               :username username
                                               :role     (:role user)})
                          (ring-resp/set-cookie
                            "token" token-signed
                            {:secure (not http-omit-added-headers?)
                             :http-only (not http-omit-added-headers?)})
                          (ring-cookies/cookies-response)))))}))
