(ns stellar.van-buren.web.aggregation-da.aggregation-db.core
  (:require

   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre]
   [taoensso.nippy :as nippy]

   [stellar.van-buren.web.aggregation-da.aggregation-db.postgres.core :as pore]
   [aero.core :as aero]
   [stellar.van-buren.web.aggregation-da.aggregation-db.postgres.connection-pool :as gool]

   [hermes.lib.component.core :as hermes.component]
   [stellar.van-buren.web.aggregation-da.aggregation-db.protocol :as aggregation-db.proto]
   [clojure.java.jdbc :as jdbc]))

(defrecord Aggregation-DB [config access]

  ;;--------------------------------------------------------;;
  component/Lifecycle
  ;;--------------------------------------------------------;;
  (start [db]
    (timbre/spy :info ["Connecting to Aggregation-db ..." config])
    (->> (aggregation-db.proto/create-pool! db)
         (assoc db :access)))

  (stop [db]
    (timbre/spy :info ["Closing connections to Aggregation-db ..." config])
    (case (not (some? access))
      true  db
      false (do (aggregation-db.proto/close-pool! db)
                (assoc db :access nil))))

  ;;--------------------------------------------------------;;
  aggregation-db.proto/Pooling
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
  aggregation-db.proto/AGGREGATION-DB
  ;;--------------------------------------------------------;;

  (<-active-orders-count-history [db filters aid]

    (aggregation-db.proto/read-only-transact!
     db
     (fn [conn]
       (vec (-> conn
                (pore/<-active-orders-count-history filters aid))))))

  (<-all-trades-history [db filters aid]
    (aggregation-db.proto/read-only-transact!
     db
     (fn [conn]
       (vec (-> conn
                (pore/<-all-trades-history  filters aid))))))
  (<-trade-aggregation-history
    [db filters aid]
    (aggregation-db.proto/read-only-transact!
     db
     (fn [conn]
       (vec (-> conn
                (pore/<-all-aggregation-history  filters aid))))))
  (<-portfo-time-series
    [db filters aid]
    (aggregation-db.proto/read-only-transact!
     db
     (fn [conn]
       (vec (-> conn
                (pore/<-portfo-time-series-query   filters aid)))))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:aggregation-db :postgres]
  [definition & _]
  (-> {:config (-> definition :component/config)}
      (map->Aggregation-DB)))

(defmethod hermes.component/config-spec
  [:aggregation-db :postgres]

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
