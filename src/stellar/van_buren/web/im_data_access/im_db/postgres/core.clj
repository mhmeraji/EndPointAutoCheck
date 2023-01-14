(ns stellar.van-buren.web.im-data-access.im-db.postgres.core
  (:require [taoensso.timbre :as timbre]

            [java-time :as jtime]
            [stellar.van-buren.web.im-data-access.im-db.postgres.connection-pool :as gool]
            [stellar.van-buren.web.im-data-access.im-db.postgres.query :as query]))

;;------------------------------------------------------------------;;
;; Algorithm
;;------------------------------------------------------------------;;



(defn <-all-algorithms
  [conn]
  (->> (query/<-all-algorithms)
       (gool/query- conn)
       (into [])))

;;------------------------------------------------------------------;;
;; Account
;;------------------------------------------------------------------;;
(defn to-time-stamp [string-date]
  (-> string-date
      (java.time.OffsetDateTime/parse)
      jtime/instant
      inst-ms))

(defn group-by-and-take-latest-response
  [response-vector]
  (map
   (fn [x]
     (let [value (second x)
           mediate-list (map
                         (fn [value]
                           (let [timestamp
                                 (->  value
                                      :received-at
                                      to-time-stamp)]
                             (assoc
                              value
                              :received-at
                              timestamp)))
                         value)]
       (apply max-key :received-at mediate-list)))
   (group-by :tag response-vector)))


(defn <-all-accounts
  [conn]
  (->> (query/<-all-accounts)
       (gool/query- conn)
       (into [])))

;;------------------------------------------------------------------;;
;; Instance
;;------------------------------------------------------------------;;

(defn <-instance [conn iid]
  (->> (query/<-instance iid)
       (gool/query- conn)
       (into [])
       (first)))

(defn <-instance-lifecycle-events [conn iid]
  (->> (query/<-instance-lifecycle-events iid)
       (gool/query- conn)
       (into [])))

(defn <-instance-run-info [conn iid]
  (->> (query/<-instance-run-info iid)
       (gool/query- conn)
       (into [])))

(defn <-all-instances [conn]
  (->> (query/<-all-instances)
       (gool/query- conn)
       (into [])))


(defn <-active-instances [conn]
  (->> (query/<-active-instances)
       (gool/query- conn)
       (into [])))


(defn <-instance-all-orders [conn iid limit]
  (->> (query/<-instance-all-orders iid limit)
       (gool/query- conn)
       (into [])))

(defn <-instance-all-responses [conn iid limit]
  (->> (query/<-instance-all-responses iid limit)
       (gool/query- conn)
       (into [])))

(defn <-instance-all-latest-responses [conn iid limit]
  (group-by-and-take-latest-response
   (->> (query/<-instance-all-responses iid limit)
        (gool/query- conn)
        (into []))))

;;------------------------------------------------------------------;;
;; State
;;------------------------------------------------------------------;;

(defn <-instance-last-state
  [conn iid]
  (->> (query/<-instance-last-state iid)
       (gool/query- conn)
       (into [])
       (first)))

(defn <-latest-persisted-states-for-iids
  [conn iids]
  (->> (query/<-latest-persisted-states iids)
       (gool/query- conn)
       (into [])))

(defn <-instance-state
  [conn iid version]
  (->> (query/<-instance-state iid version)
       (gool/query- conn)
       (into [])
       (first)))

;;------------------------------------------------------------------;;
;; Order
;;------------------------------------------------------------------;;

(defn <-order-all-responses [conn tag]
  (->> (query/<-order-all-responses-historical-order tag)
       (gool/query- conn)
       (into [])))

;;------------------------------------------------------------------;;
;; Monitoring
;;------------------------------------------------------------------;;

