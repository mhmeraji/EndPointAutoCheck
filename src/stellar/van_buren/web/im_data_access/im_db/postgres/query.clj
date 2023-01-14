 (ns stellar.van-buren.web.im-data-access.im-db.postgres.query
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
;; Algorithm
;;------------------------------------------------------------------;;

(defn <-all-algorithms []
  (-> (honey/select :*)
      (honey/from :algorithms)))

;;------------------------------------------------------------------;;
;; Account
;;------------------------------------------------------------------;;

(defn <-all-accounts []
  (-> (honey/select :*)
      (honey/from :accounts)))

;;------------------------------------------------------------------;;
;; Instance
;;------------------------------------------------------------------;;

(defn <-instance [iid]
  (-> (honey/select :*)
      (honey/where [:= :iid iid])
      (honey/from :instances)))


(defn <-instance-lifecycle-events [iid]
  (-> (honey/select :trig.*)
      (honey/from [:triggers :trig])
      (honey/join [:transitions :t] [:= :trig.transition :t.id])
      (honey/merge-join [:states :s] [:= :t.to :s.id])
      (honey/where [:and [:= :s.inst-id iid] [:in :trig.type ["cmd->activate"
                                                              "cmd->create"
                                                              "cmd->recover"
                                                              "cmd->lock"
                                                              "cmd->unlock"
                                                              "cmd->delete"
                                                              "cmd->error"
                                                              "cmd-fs-activate"
                                                              "cmd->gracing-down"
                                                              "cmd->reconfigure"
                                                              "cmd->table-reconfigure"
                                                              "sync->force-down"]]])))

(defn <-instance-run-info [iid]
  (-> (honey/select [:%min.version :start-version]
                    [:%max.version :end-version]
                    [:%min.persisted-at :start-date]
                    [:%max.persisted-at :end-date]
                    :run)
      (honey/from :states)
      (honey/where [:= :inst-id iid])
      (honey/group :run)
      (honey/order-by [:run :desc])))

(defn <-all-instances []
  (-> (honey/select :*)
      (honey/from :instances)))

(defn <-active-instances []
  (-> (honey/select :*)
      (honey/from :instances)
      (honey/where [:= :status "active"])))

(defn <-instance-all-orders [iid limit]
  (-> (honey/select :e.*)
      (honey/from [:effects :e])
      (honey/join [:transitions :t] [:= :e.transition :t.id])
      (honey/merge-join [:states :s] [:= :t.to :s.id])
      (honey/where [:= :s.inst-id iid])
      (honey/limit limit)))

(defn <-instance-all-responses [iid limit]
  (-> (honey/select :trig.*)
      (honey/from [:triggers :trig])
      (honey/join [:transitions :t] [:= :trig.transition :t.id])
      (honey/merge-join [:states :s] [:= :t.to :s.id])
      (honey/where [:and [:= :s.inst-id iid] [:= :trig.type "response"]])
      (honey/limit limit)))



;;------------------------------------------------------------------;;
;; States
;;------------------------------------------------------------------;;

(defn <-instance-last-state [iid]
  (-> (honey/select :*)
      (honey/from :states)
      (honey/where [:= :inst-id iid])
      (honey/order-by [:version :desc] [:run :desc])
      (honey/limit 1)))

(defn <-latest-persisted-states [instance-ids]
  {:pre [(coll? instance-ids)]}
  (-> (honey/select  :st1.*)
      (honey/from [:states :st1])
      (honey/join
       [(-> (honey/select :inst-id [:%max.id :max_id])
            (honey/from :states)
            (honey/where [:in :inst-id instance-ids])
            (honey/group :inst-id))
        :st2]
       [:= :st1.id :st2.max_id])))

(defn <-instance-state [iid version]
  (-> (honey/select :*)
      (honey/from :states)
      (honey/where [:and
                    [:= :inst-id iid]
                    [:= :version version]])
      (honey/order-by [:run :desc])
      (honey/limit 1)))

;;------------------------------------------------------------------;;
;; Order
;;------------------------------------------------------------------;;


(defn <-order-all-responses-historical-order [tag]
  (-> (honey/select :trig.*)
      (honey/from [:triggers :trig])
      (honey/where [:= :trig.tag tag])
      (honey/order-by [:received-at :asc])))

;;------------------------------------------------------------------;;
;; Monitoring
;;------------------------------------------------------------------;;





