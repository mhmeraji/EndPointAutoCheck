(ns stellar.van-buren.web.aggregation-da.aggregation-db.postgres.core
  (:require [taoensso.timbre :as timbre]
            [java-time :as jtime]
            [stellar.van-buren.web.aggregation-da.aggregation-db.postgres.connection-pool :as gool]
            [stellar.van-buren.web.aggregation-da.aggregation-db.postgres.query :as query]))

;;------------------------------------------------------------------;;
;; Monitoring
;;------------------------------------------------------------------;;
(defn <-active-orders-count-history [conn filters aid]
  (->> (query/<-active-orders-count-history filters aid)
       (gool/query- conn)
       (into [])))

(defn <-all-trades-history [conn filters aid]
  (->> (query/<-all-trades-query filters aid)
       (gool/query- conn)
       (into [])))

(defn <-all-aggregation-history [conn filters aid]
  (->> (query/<-all-agregation-history-query filters aid)
       (gool/query- conn)
       (into [])))

(defn <-portfo-time-series-query [conn filters aid]
  (->> (query/<-portfo-time-series-query filters aid)
       (gool/query- conn)
       (into [])))
