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
(def dbs-instances "instances")
(def dbs-instance-groups "igroups")
(def dbs-algorithms "algorithms")
(def dbs-accounts "accounts")
(def dbs-symbols "symbols")
(def dbs-notifications "notifications")
(def dbs-captcha "captcha")
(def dbs-user-activity "user-activity")

(def user-roles-admin "ADMIN")
(def user-roles-ifb-supervisor "IFB-SUPERVISOR")
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

  (find-user-by-username-password [_ username password]
    (let [user (moncol/find-one-as-map
                 db dbs-users
                 {:username username})]
      (when (and
              (some? user)
              (hashers/check password (:password user)))
        (dissoc user :password))))

  (insert-user! [_ user-data]
    (moncol/insert db dbs-users user-data))

  (find-user-by-username [_ username]
    (let [user (moncol/find-one-as-map
                 db dbs-users
                 {:username username})]
      (when (some? user)
        (dissoc user :password))))

  (block-user! [_ username]
    (moncol/update
      db dbs-users {:username username}
      {mongopr/$set {:is-blocked? true}}))

  (user-exists? [_ username]
    (moncol/any? db dbs-users {:username username}))

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

  (update-user-password! [_ username new-password password-update-time]
    (moncol/update
      db dbs-users {:username username}
      {mongopr/$set {:password             (hashers/encrypt new-password)
                     :last-password-update password-update-time}}))

  (update-user-known-algs [_ username algs]
    (moncol/update
      db dbs-users {:username username}
      {mongopr/$set {:algorithms-known algs}}))

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
    (moncol/insert db dbs-user-activity {:activity        "LOGOUT"
                                         :username        username
                                         :ip              user-ip
                                         :time            time
                                         :auto-initiated? auto-initiated?}))

  (get-user-auth-activity [_ username]
    (map
      #(dissoc % :_id)
      (moncol/find-maps db dbs-user-activity {:username username})))

  (find-all-user-role-users [_]
    (let [users (moncol/find-maps db dbs-users {:role user-roles-user})]
      (map #(dissoc % :_id) users)))

  (register-captcha-puzzle! [_ code]
    (moncol/remove db dbs-captcha)
    (.toString ^org.bson.types.ObjectId
               (mongutil/get-id
                 (moncol/insert-and-return
                   db
                   dbs-captcha {:code code}))))
  (get-active-session-count [_ username]
    (-> (moncol/find-one-as-map
          db dbs-users
          {:username username})
        :tokens
        count))

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
        {mongopr/$set {:tokens new-token-map}})))

  (find-captcha-puzzle [_ pid]
    (moncol/find-map-by-id db dbs-captcha (mongutil/object-id pid)))

  (invaidate-captcha! [_ pid]
    (moncol/update
      db dbs-captcha {:_id (mongutil/object-id pid)}
      {mongopr/$set {:invalid? true}}))

  (find-instance [_ iid]
    (let [inst (moncol/find-one-as-map db dbs-instances {:iid        iid
                                                         :is-active? true})]
      (dissoc inst :_id)))

  (remove-instance [_ iid]
    (moncol/update
      db dbs-instances {:iid iid}
      {mongopr/$set {:is-active? false}}))

  (lock-instance! [_ iid]
    (moncol/update
      db dbs-instances {:iid iid}
      {mongopr/$set {:is-locked? true}}))

  (unlock-instance! [_ iid]
    (moncol/update
      db dbs-instances {:iid iid}
      {mongopr/$set {:is-locked? false}}))

  (find-all-instances [_]
    (let [insts (moncol/find-maps db dbs-instances {:is-active? true})]
      (map #(dissoc % :_id) insts)))

  (find-all-user-instances [_ username]
    (let [insts (moncol/find-maps db dbs-instances {:creator-id username
                                                    :is-active? true})]
      (map #(dissoc % :_id) insts)))

  ;; TODO: move igname to instances; remove collection;
  (is-igroup-name-duplicate?
    [_ igname user-id]
    (moncol/any? db dbs-instance-groups {:creator-id user-id :group-name igname}))

  (insert-igroup [_ igname user-id]
    (moncol/insert db dbs-instance-groups {:creator-id user-id :group-name igname}))

  (insert-instances [_ instances]
    (moncol/insert-batch db dbs-instances (into [] (map
                                                     #(assoc % :is-active? true)
                                                     instances))))

  (find-all-algorithms [_]
    (let [algs (moncol/find-maps db dbs-algorithms {})]
      (map #(dissoc % :_id) algs)))

  (find-all-accounts [_]
    (let [accs (moncol/find-maps db dbs-accounts {})]
      (map #(dissoc % :_id) accs)))

  (find-account [_ aid]
    (let [acc (moncol/find-one-as-map db dbs-accounts {:aid aid})]
      (dissoc acc :_id)))

  (find-all-symbols [_]
    (let [syms (moncol/find-maps db dbs-symbols {})]
      (map #(dissoc % :_id) syms)))

  (is-one-of-isins-ifb? [_ isins]
    (if (or (empty? isins)
            (nil? isins))
      (->> false)
      (moncol/any? db dbs-symbols
                   {:isin      {mongopr/$in isins}
                    :faraboors "true"})))

  (insert-notifications [_ notifs]
    ;; FIXME;
    (moncol/insert-batch db dbs-notifications (into [] (map #(assoc % :created-at (java.util.Date.)) notifs))))

  (find-notifications-for [_ user-id]
    (let [notifs (moncol/find-maps db dbs-notifications {:user-id user-id})]
      (map #(dissoc % :_id) notifs)))

  (find-notifications-for [_ user-id limit]
    (let [notifs (monquery/with-collection db dbs-notifications
                   (monquery/find {:user-id user-id})
                   (monquery/sort {:created-at -1})
                   (monquery/limit limit))]
      (map #(dissoc % :_id) notifs))))


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

(comment

  ;; db.users.insert({
  ;;                  "name"              :        "Normal User",
  ;;                  "username"          :        "user",
  ;;                  "password"          :        "bcrypt+sha512$9004c81c66cb6d2f1e3fc5b2dd96224c$12$e64b01a096ee463c36e426f3c22d3aea85b9a79262cf3088",
  ;;                  "role"              :        "USER",
  ;;                  "max-session-count" :        1,
  ;;                  });

  (def db "van-buren-web")

  (moncol/insert db dbs-users {:name     "System Administrator"
                               :username "admin"
                               :password (hashers/encrypt "adminpass")
                               :role     user-roles-admin})

  (moncol/insert db dbs-users {:name     "Some User"
                               :username "user1"
                               :password (hashers/encrypt "user1pass")
                               :role     user-roles-user})

  (timbre/spy (hashers/encrypt "hermes123456"))

  (timbre/set-level! :debug)

  (moncol/find-map-by-id db dbs-captcha "621106d1e18ce3dfd531cd81")

  (timbre/spy {:name     "Market Supervisor"
               :username "ifb-1"
               :password (hashers/encrypt "1")
               :role     user-roles-ifb-supervisor}))

;;------------------------------------------------------------------;;
;
