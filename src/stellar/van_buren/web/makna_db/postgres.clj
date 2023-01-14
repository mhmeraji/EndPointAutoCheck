
(ns stellar.van-buren.web.makna-db.postgres
  (:require
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre]

   [honeysql.helpers :as honey]
   [honeysql-postgres.helpers :as psqlh]
   [honeysql.core :as sql]

   [hermes.lib.component.core :as hermes.component]
   [stellar.van-buren.web.makna-db.protocol :as proto]
   [stellar.van-buren.web.im-data-access.im-db.postgres.connection-pool
    :as gool]))

;;------------------------------------------------------------------;;
;; Query & DB Functions
;;------------------------------------------------------------------;;

(defn- <-latest-hash-query
  []
  (-> (honey/select :HashField)
      (honey/from :life-cycle-events)
      (honey/order-by [:persisted-at :desc])
      (honey/limit 1)))

(defn- <-latest-hash
  [conn]
  (->> (<-latest-hash-query)
       (gool/query- conn)))

(defn- ->insert-log-query
  [record]
  (-> (honey/insert-into :life-cycle-events)
      (honey/values
        [record])
      (psqlh/returning :id)))

(defn- ->insert-log
  [conn record]
  (->> (->insert-log-query record)
       (gool/query- conn)
       first))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defrecord Web-MAKNA-DB-Module
    [config access]

    component/Lifecycle

    (start [component]
      (timbre/info ["Starting Makna DB Component" config])
      (->> (proto/create-pool! component)
           (assoc component :access)))

    (stop [component]
      (timbre/info "Stopping Makna DB Component")
      (when (some? access)
        (proto/close-pool! component))
      (assoc component
             :access nil))

    ;;--------------------------------------------------------;;
    proto/Pooling
    ;;--------------------------------------------------------;;

    (create-pool!
      [db]
      (gool/create-pool! config))

    (close-pool!
      [db]
      (-> access
          (gool/close-pool!)))

    (transact!
      [db op-fn]
      (gool/transact! access op-fn))

    (read-only-transact!
      [db op-fn]
      (gool/read-only-transact! access op-fn))

    (get-connection
      [db]
      (gool/get-connection access))

    ;;--------------------------------------------------------;;
    proto/Access
    ;;--------------------------------------------------------;;

    (->insert-log!  [db record]
      (when (some? (:Result record))
        (proto/transact!
          db (fn [conn]
               (->insert-log conn record)))))

    (<-latest-hash [db]
      (proto/read-only-transact!
        db (fn [conn]
             (<-latest-hash conn)))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component
  [:web :makna-db :postgres :v-0.0.1]
  [definition]
  (-> {:config (-> definition :component/config)}
      (map->Web-MAKNA-DB-Module)))

(defmethod hermes.component/config-spec
  [:web :makna-db :postgres :v-0.0.1]
  [_config]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
