 (ns stellar.van-buren.web.aggregation-da.aggregation-db.postgres.query
   (:require [honeysql.helpers :as honey]
             [honeysql.format :as honey-format]
             [tick.core :as tick]
             [taoensso.timbre :as timbre]
             [honeysql.core :as sql])
   (:import
    (java.sql Timestamp)
    (java.time LocalDateTime)
    (java.time Instant)))

;;------------------------------------------------------------------;;
;; Monitoring
;;------------------------------------------------------------------;;
(defn <-all-trades-query [filters aid]
  (let [batch-size (if (some? (:query-range filters))
                     (read-string (:query-range filters))
                     100)
        {start-time :start-time
         end-time :end-time} filters]
    (-> (honey/select :t.*)
        (honey/from [:trades :t])
        (honey/where [:and [:= :t.aid aid]
                      (when (some? start-time)
                        [:>= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       start-time))])
                      (when (some? end-time)
                        [:<= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       end-time))])])
        (honey/order-by [:timestamp :asc])
        (honey/limit batch-size))))

(defn <-all-agregation-history-query [filters aid]  
  (let [batch-size (if (some? (:query-range filters))
                     (read-string (:query-range filters))
                     100)
        isin (:isin filters)
        {start-time :start-time
         end-time :end-time} filters]
    (timbre/spy filters)
    (-> (honey/select :sell_weighted_sum :buy_weighted_sum
                      :sell_executed_sum :buy_executed_sum
                      :isin :timestamp)
        (honey/from [:trade_aggregation :t])
        (honey/where [:and
                      [:= :t.aid aid]
                      [:= :t.isin  isin]
                     (when (some? start-time)
                        [:>= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       start-time))])
                      (when (some? end-time)
                        [:<= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       end-time))])])
        (honey/order-by [:timestamp :asc])
        (honey/limit batch-size))))


(defn <-portfo-time-series-query [filters aid]
  (let [batch-size (if (some? (:query-range filters))
                     (read-string (:query-range filters))
                     100)
        {start-time :start-time
         end-time :end-time} filters]
    (timbre/spy filters)
    (-> (honey/select :*)
        (honey/from [:portfo_snapshot :t])
        (honey/where [:and
                      [:= :t.aid aid]
                      (when (some? start-time)
                        [:>= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       start-time))])
                      (when (some? end-time)
                        [:<= :t.timestamp (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                                       end-time))])])
        (honey/order-by [:timestamp :asc])
        (honey/limit batch-size))))

(defn <-active-orders-count-history [filters aid]
  (let [batch-size (if (some? (:query-range filters))
                     (read-string (:query-range filters))
                     100)
        {start-time :start-time
         end-time :end-time} filters]
    
    (->
     (honey/select :a.*)
     (honey/from [:active_orders_count :a])
     (honey/where [:and [:= :a.aid aid]
                   (when
                   (some? start-time) 
                    [:>= :a.timestamp
                     (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                  start-time))])
                   (when
                    (some? end-time)
                     [:<= :a.timestamp
                      (Timestamp/valueOf ^ LocalDateTime (tick/date-time
                                                   end-time))])])
     (honey/order-by [:timestamp :asc])
     (honey/limit batch-size))))


