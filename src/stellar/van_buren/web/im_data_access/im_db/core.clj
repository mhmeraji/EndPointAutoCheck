(ns stellar.van-buren.web.im-data-access.im-db.core
  (:require

   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre]
   [taoensso.nippy :as nippy]

   [stellar.van-buren.web.im-data-access.im-db.postgres.core :as pore]
   [aero.core :as aero]
   [stellar.van-buren.web.im-data-access.im-db.postgres.connection-pool :as gool]

   [hermes.lib.component.core :as hermes.component]
   [stellar.van-buren.web.im-data-access.im-db.protocol :as im-db.proto]
   [clojure.java.jdbc :as jdbc]))

(defrecord IM-PostgresDB [config access]

  ;;--------------------------------------------------------;;
  component/Lifecycle
  ;;--------------------------------------------------------;;
  (start [db]
    (timbre/spy :info ["Connecting to IM-DB ..." config])
    (->> (im-db.proto/create-pool! db)
         (assoc db :access)))

  (stop [db]
    (timbre/spy :info ["Closing connections to IM-DB ..." config])
    (case (not (some? access))
      true  db
      false (do (im-db.proto/close-pool! db)
                (assoc db :access nil))))

  ;;--------------------------------------------------------;;
  im-db.proto/Pooling
  ;;--------------------------------------------------------;;

  (create-pool!
    [_]
    (gool/create-pool! config))

  (close-pool!
    [_]
    (-> access
        (gool/close-pool!)))

  (transact!
    [_ op-fn]
    (gool/transact! access op-fn))

  (read-only-transact!
    [_ op-fn]
    (gool/read-only-transact! access op-fn))

  (get-connection
    [_]
    (gool/get-connection access))

  ;;--------------------------------------------------------;;
  im-db.proto/IM-DB
  ;;--------------------------------------------------------;;

  (<-all-algorithms [db]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-all-algorithms)))))

  (<-all-accounts [db]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-all-accounts)))))

  (<-instance [db iid]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance iid)))))

  (<-instance-lifecycle-events [db iid]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-lifecycle-events iid)))))

  (<-instance-run-info [db iid]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-run-info iid)))))

  (<-all-instances [db]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-all-instances)))))

  (<-active-instances [db]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-active-instances)))))

  (<-instance-state-record [db iid version]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-state iid version)))))

  (<-instance-last-state-record [db iid]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-last-state iid)))))

  (<-instance-all-orders [db iid limit]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-all-orders iid limit)))))

  (<-instance-all-latest-responses [db iid limit]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-all-latest-responses iid limit)))))

  (<-instance-latest-meta-trades [db iids]
    (let [persisted-states
          (im-db.proto/read-only-transact!
           db
           (fn [conn]
             (-> conn
                 (pore/<-latest-persisted-states-for-iids iids))))]
      (apply  merge (map
                     (fn [x]
                       (let [iid (:inst-id x)
                             deserialized-trade-data (-> x :meta-trade nippy/thaw)]
                         {iid deserialized-trade-data}))
                     persisted-states))))

  (<-instance-all-responses [db iid limit]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-instance-all-responses iid limit)))))

  (<-order-history [db tag]
    (im-db.proto/read-only-transact!
     db (fn [conn]
          (-> conn
              (pore/<-order-all-responses tag))))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:im-db :postgres]
  [definition & _]
  (-> {:config (-> definition :component/config)}
      (map->IM-PostgresDB)))

(defmethod hermes.component/config-spec
  [:im-db :postgres]

  [_]
  (-> any?))



;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;



(comment
  ;;TEST for current branch
  (def definition

    (-> "/home/pooriatajmehrabi/Desktop/hermes/project-van-buren-umbrella-new/checkouts/van-buren-web/resources/definitions/components/im-db-postgres.edn" aero.core/read-config))

  (def system

    (-> definition
        hermes.component/create-component))

  (def started-system (-> system component/start))
  (def stopped-system (-> system component/stop))
  (def access (:access started-system))

  (def conn (gool/get-connection access))
  (clojure.java.jdbc/execute!
   conn
   ["INSERT INTO portfo_snapshot (freeze, free, asset, source, timestamp, aid) VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)" 1089.6547 513.33344 "USDT" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 0.0 799.90955 "ADA" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 1.3050025E7 1.76567264E8 "TMN" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 0.41147 0.10443427 "ETH" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 306.6 1033.6272 "TRX" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 0.005221 0.01262531 "BTC" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1" 0.0 7912139.5 "SHIB" "wallex"  "2022-07-31T11:21:21.223-00:00" "wallex::1"])
  )
