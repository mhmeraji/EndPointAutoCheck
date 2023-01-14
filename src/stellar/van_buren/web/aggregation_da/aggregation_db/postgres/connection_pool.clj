(ns stellar.van-buren.web.aggregation-da.aggregation-db.postgres.connection-pool
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource))
  (:require
   [clojure.java.jdbc :as jdbc]
   [taoensso.timbre :as timbre]

   [honeysql.core :as sql]))

;;------------------------------------------------------------------;;
;; connection pool
;;------------------------------------------------------------------;;

(defmacro ^:private resolve-new
  [class]
  (when-let [resolved (resolve class)]
    `(new ~resolved)))

(defn- as-properties
  [m]
  (let [p (java.util.Properties.)]
    (doseq [[k v] m]
      (.setProperty p (name k) (str v)))
    p))

(defn- pool
  [{:keys [subprotocol
           subname
           classname
           excess-timeout
           idle-timeout
           initial-pool-size
           minimum-pool-size
           maximum-pool-size
           test-connection-query
           idle-connection-test-period
           test-connection-on-checkin
           test-connection-on-checkout]
    :or   {excess-timeout              (* 30 60)
           idle-timeout                (* 3 60 60)
           initial-pool-size           3
           minimum-pool-size           3
           maximum-pool-size           15
           test-connection-query       nil
           idle-connection-test-period 0
           test-connection-on-checkin  false
           test-connection-on-checkout false}
    :as   spec}]
  {:datasource
   (doto (resolve-new ComboPooledDataSource)
     (.setDriverClass classname)
     (.setJdbcUrl (str "jdbc:" subprotocol ":" subname))
     (.setProperties (as-properties
                       (dissoc spec
                               :classname
                               :subprotocol
                               :subname
                               :naming
                               :delimiters
                               :alias-delimiter
                               :excess-timeout
                               :idle-timeout
                               :initial-pool-size
                               :minimum-pool-size
                               :maximum-pool-size
                               :test-connection-query
                               :idle-connection-test-period
                               :test-connection-on-checkin
                               :test-connection-on-checkout)))
     (.setMaxIdleTimeExcessConnections excess-timeout)
     (.setMaxIdleTime idle-timeout)
     (.setInitialPoolSize initial-pool-size)
     (.setMinPoolSize minimum-pool-size)
     (.setMaxPoolSize maximum-pool-size)
     (.setIdleConnectionTestPeriod idle-connection-test-period)
     (.setTestConnectionOnCheckin test-connection-on-checkin)
     (.setTestConnectionOnCheckout test-connection-on-checkout)
     (.setPreferredTestQuery test-connection-query))})

(defn close-pool!
  [{{:keys [datasource]} :pool}]
  (doto ^ComboPooledDataSource datasource
    (.close)))

(defn create-pool!
  [config]
  {:pool    (-> config (pool))
   :options {:naming          {:fields identity :keys identity}
             :delimiters      ["\"" "\""]
             :alias-delimiter " AS "}})

(defn get-connection
  [{:keys [pool]}]
  
  pool)

;;------------------------------------------------------------------;;
;; Query and Transaction Facilities
;;------------------------------------------------------------------;;

(defn format-sql
  [sql-map & params]
  (apply
    sql/format
    sql-map
    (conj
      (into [] params)
      :quoting :ansi
      :allow-dashed-names? true)))

(defn exec!-
  [conn op]
  (jdbc/execute! conn (format-sql op)))

(defn transact!
  [db operation-fn]
  (jdbc/with-db-transaction [conn (get-connection db)]
    (operation-fn conn)))

(defn query-
  [conn sql-map & params]
  (jdbc/query conn (apply format-sql sql-map params)))

(defn query
  [conn-pool sql-map & params]
  (jdbc/with-db-connection [conn (get-connection conn-pool)]
    (apply query- conn sql-map params)))

(defn read-only-transact!
  [db operation-fn]
  (jdbc/with-db-transaction [conn (get-connection db) {:read-only? true}]
    (operation-fn conn)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
