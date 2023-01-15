(ns endpoint-autocheck.db.core
  (:require [monger.core :as monger]
            [monger.collection :as moncol]
            [monger.credentials :as mcred]
            [monger.query :as monquery]
            [monger.operators :as mongopr]
            [monger.util :as mongutil]

            [com.stuartsierra.component :as component]
            [tick.core :as tick]

            [taoensso.timbre :as timbre]
            [buddy.hashers :as hashers]

            [hermes.lib.component.core :as hermes.component]
            [endpoint-autocheck.db.protocol :as db.proto]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(def dbs-users "users")
(def dbs-user-activity "user-activity")

(def user-roles-admin "ADMIN")
(def user-roles-user "USER")

(defrecord Web-MongoDB [config connection db]

  ;;----------------------------------------------------------------;;
  component/Lifecycle
  ;;----------------------------------------------------------------;;

  (start [component]
    (let [{:keys [db-name username password connection-info]} config
          conn                                                (monger/connect-with-credentials
                                                                (:host connection-info) (:port connection-info)
                                                                (mcred/create username db-name password))]
      (timbre/spy :info ["Starting Web-MongoDB Module" config])
      (-> component
          (assoc :connection conn)
          (assoc :db   (monger/get-db conn db-name)))))

  (stop [component]
    (let [conn (:connection component)]
      (timbre/spy :info ["Stopping Web-MongoDB Module" config])
      (when (some? conn)
        (monger/disconnect conn))
      (-> component
          (assoc :connection nil)
          (assoc :db nil))))

  ;;----------------------------------------------------------------;;
  db.proto/Web-DB
  ;;----------------------------------------------------------------;;

  (user-exists? [_ username]
    (moncol/any? db dbs-users {:username username}))

  (insert-user! [_ user-data]
    (moncol/insert db dbs-users user-data))

  (get-endpoints-count [component username]
    (->> (db.proto/get-endpoint-s component username)
         (count)))

  (register-user-login! [_ username user-ip user-agent time]
    (moncol/insert db dbs-user-activity {:activity "LOGIN"
                                         :username username
                                         :ip       user-ip
                                         :agent    user-agent
                                         :time     time}))

  (register-failed-login! [_ username user-ip user-agent time msg]
    (moncol/insert db dbs-user-activity {:activity "FAILED LOGIN"
                                         :username username
                                         :ip       user-ip
                                         :agent    user-agent
                                         :time     time
                                         :msg      msg}))

  (register-user-logout! [this username user-ip time]
    (db.proto/register-user-logout! this username user-ip time false))

  (register-user-logout! [_ username user-ip time auto-initiated?]
    (moncol/insert db dbs-user-activity {:activity "LOGOUT"
                                         :username username
                                         :ip       user-ip
                                         :time     time
                                         :auto-initiated?
                                         auto-initiated?}))

  (find-user-by-username [_ username]
    (let [user (moncol/find-one-as-map
                 db dbs-users
                 {:username username})]
      (when (some? user)
        (dissoc user :password))))

  (find-user-by-username-password [_ username password]
    (let [user (moncol/find-one-as-map
                 db dbs-users
                 {:username username})]
      (when (and
              (some? user)
              (hashers/check password (:password user)))
        (dissoc user :password))))

  (get-active-session-count [_ username]
    (-> (moncol/find-one-as-map
          db dbs-users
          {:username username})
        :tokens
        count))

  (update-user-session! [_ username token token-valid-until token-ip]
    (moncol/update
      db dbs-users {:username username}
      {mongopr/$push
       {:tokens
        {:token             token
         :token-valid-until token-valid-until
         :token-ip          token-ip}}}))

  (remove-user-session-token!
    [_ username token]
    (let [user               (moncol/find-one-as-map
                               db dbs-users
                               {:username username})
          previous-token-map (:tokens user)
          new-token-map      (filter #(not= token (:token %))
                                     previous-token-map)]

      (moncol/update
        db dbs-users {:username username}
        {mongopr/$set {:tokens new-token-map}})))

  (add-endpoint [_ username ep-record]
    (moncol/update
      db dbs-users {:username username}
      {mongopr/$push
       {:endpoints
        ep-record}}))

  (get-endpoint-s [_ username]
    (-> (moncol/find-one-as-map db dbs-users {:username username})
        :endpoints))

  (update-endpoint [component username ep-record]
    (let [endpoints (->> username
                         (db.proto/get-endpoint-s component)
                         (remove #(= (:url %) (:url ep-record))))
          endpoints (conj endpoints ep-record)]
      (moncol/update
        db dbs-users {:username username}
        {mongopr/$set {:endpoints endpoints}})))

  (get-endpoint [component username url]
    (->> (db.proto/get-endpoint-s component username)
         (filter #(= url (:url %)))
         (first)))

  (keep-n-newest-sessions
    [_ username n]
    (let [user               (moncol/find-one-as-map
                               db dbs-users
                               {:username username})
          previous-token-map (:tokens user)
          new-token-map      (->> previous-token-map
                                  (sort-by #(-> %
                                                :token-valid-until
                                                tick.core/instant
                                                inst-ms))
                                  reverse
                                  (take n))]
      (moncol/update
        db dbs-users {:username username}
        {mongopr/$set {:tokens new-token-map}}))))


;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:web-db :mongo]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Web-MongoDB)))

(defmethod hermes.component/config-spec
  [:web-db :mongo]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
