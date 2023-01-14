(ns stellar.van-buren.web.im-data-access.core
  (:require
   [com.stuartsierra.component :as component]

   [stellar.lib.algorithm-utils.access :as algutil.access]

   [stellar.van-buren.web.im-data-access.im-db.protocol :as im-db.proto]

   [stellar.van-buren.web.rest-da-util :as imdatil]

   [hermes.lib.component.core :as hermes.component]

   [stellar.van-buren.web.im-data-access.protocols.instance :as instocol]
   [stellar.van-buren.web.im-data-access.protocols.algorithm :as algocol]
   [stellar.van-buren.web.im-data-access.protocols.orders :as ordocol]

   [taoensso.timbre :as timbre]

   ;; Importing algorithms
   ;; [stellar.logic.mm.access.stateful]
   ;; [stellar.logic.ca.access.stateful]
   [hermes.logic.kraken.access.stateful]
   [hermes.logic.tmn-kraken.access.stateful]
   [hermes.logic.procurator.access.stateful]
   [hermes.logic.arbitrageur.access.stateful]
   [hermes.logic.flucture.access.stateful]
   [hermes.logic.runner.access.stateful]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

;; (defn- <-meta-trade-specification-format-valid?
;;   [{:keys [ordering filter agg-fn] :as specification}]
;;   (let [ordering-field     (get ordering "field")
;;         ordering-direction (get ordering "direction")

;;         filter-iids        (get filter "instance")
;;         filter-aids        (get filter "account")
;;         filter-isins       (get filter "isin")]
;;     (when-not
;;      (contains? #{"sum" "max" "min" "avg" nil}
;;                 agg-fn)
;;       (throw
;;        (ex-info
;;         "Unknown aggregation function"
;;         {:specification specification
;;          :agg-fn        agg-fn})))

;;     (when-not
;;      (contains? #{"iid" "aid" "isin" "cq"
;;                   "cv" "mq" "mv" "eq" "ev"
;;                   "kq" "kv" "ecq" "ecv" nil}
;;                 ordering-field)
;;       (throw
;;        (ex-info
;;         "Unknown ordering-field"
;;         {:specification  specification
;;          :ordering-field ordering-field})))

;;     (when-not
;;      (contains? #{"asc" "desc" nil}
;;                 {:specification specification
;;                  :ordering-direction ordering-direction}
;;                 ordering-direction)
;;       (throw
;;        (ex-info
;;         "Unknown ordering-direction"
;;         {:specification  specification
;;          :ordering-direction ordering-direction})))

;;     (when-not
;;      (or (list? filter-iids)
;;          (= filter-iids "all")
;;          (list? filter-aids)
;;          (= filter-aids "all")
;;          (list? filter-isins)
;;          (= filter-isins "all"))
;;       (throw
;;        (ex-info
;;         "Unknown filtering attribute"
;;         {:specification specification
;;          :filter filter})))
;;     (->> [ordering-field ordering-direction
;;           filter-iids filter-aids filter-isins agg-fn])))

(defrecord IM-DataAccess [config base-url im-db]

  ;;------------------------------------------------------------------;;
  component/Lifecycle
  ;;------------------------------------------------------------------;;

  (start [component]
    (-> component
        (assoc :base-url (str (:proto config)
                              "://"
                              (:host config)
                              ":"
                              (:port config)))))

  (stop [component]
    component)

;;------------------------------------------------------------------;;
  ordocol/Order
  ;;------------------------------------------------------------------;;

  (<-all-order-responses [_ tag]
    (im-db.proto/<-order-history im-db tag))

  ;;------------------------------------------------------------------;;
  algocol/Algorithm
  ;;------------------------------------------------------------------;;

  (<-algorithm-config-spec [_ id]
    (let [data (algutil.access/get-sendable-config-spec {:alg-id id})]
      data))

  ;;------------------------------------------------------------------;;
  instocol/Instance
  ;;------------------------------------------------------------------;;

  (<-instance [_ iid]
    (im-db.proto/<-instance im-db iid))

  (<-all-instances [_]
    (im-db.proto/<-all-instances im-db))

  (<-active-instances [_]
    (im-db.proto/<-active-instances im-db))

  (<-instance-lifecycle-events [_ iid]
    (im-db.proto/<-instance-lifecycle-events im-db iid))

  (<-instance-run-info [_ iid]
    (im-db.proto/<-instance-run-info im-db iid))

  (<-instance-last-state
    [_ iid alg-id]

    (let [{inst-version :version :as state-record}
          (im-db.proto/<-instance-last-state-record im-db iid)]

      (when-not (some? inst-version)
        (throw
         (ex-info
          "Instance doesn't seem to be there!"
          {:state-record state-record})))

      state-record))

  (<-instance-state
    [_ iid version alg-id]

    (let [{inst-version :version :as state-record}
          (im-db.proto/<-instance-state-record im-db iid version)]

      (when-not (some? inst-version)
        (throw
         (ex-info
          "Instance doesn't seem to be there!"
          state-record)))

      state-record))

  (<-instance-last-state-details
    [_ iid alg-id]
    (let [{state :state inst-version :version :as state-record}
          (im-db.proto/<-instance-last-state-record im-db iid)]

      (when-not (some? inst-version)
        (throw
         (ex-info
          "Instance doesn't seem to be there!"
          state-record)))

      (algutil.access/deserialize-inst-record
       {:alg-id            alg-id
        :serialized-record state})))

  (<-instance-state-details
    [_ iid version alg-id]
    (let [{state :state inst-version :version :as state-record}
          (im-db.proto/<-instance-state-record im-db iid version)]

      (when-not (some? inst-version)
        (throw
         (ex-info
          "Instance doesn't seem to be there!"
          state-record)))

      (algutil.access/deserialize-inst-record
       {:alg-id            alg-id
        :serialized-record state})))

  (<-instance-subview [im-da iid alg-id]
    (let [translated-record (instocol/<-instance-last-state
                             im-da iid alg-id)]
      (algutil.access/<-subview translated-record)))

  (<-instance-algorithm-table [im-da iid alg-id version]
    (let [record (instocol/<-instance-state im-da iid version alg-id)]
      (algutil.access/<-subview record)))

  (<-instance-all-orders
    [_ iid limit]
    (im-db.proto/<-instance-all-orders im-db iid limit))

  (<-instance-all-responses
    [_ iid limit]
    (im-db.proto/<-instance-all-responses im-db iid limit))

  (<-instance-all-latest-responses
    [_ iid limit]
    (im-db.proto/<-instance-all-latest-responses im-db iid limit))

  (<-instance-latest-meta-trades
    [_ iids]
    (im-db.proto/<-instance-latest-meta-trades im-db iids))

  (->instance!
    [_ config accounts
     user-info user-ip]
    (imdatil/post-to
     base-url "/api/v1/create-instance"
     {:name     (:iname config)
      :user-ip   user-ip
      :user-info user-info
      :config   config
      :accounts accounts}))

  ;; TODO: remove, move `reduce` to request handler
  (->instances!
    [im-da configs accounts
     user-info user-ip]
    (reduce (fn [instances-map config]
              (assoc instances-map
                     (:iname config)
                     (instocol/->instance! im-da config accounts
                                           user-info user-ip)))
            {}
            configs))

  (->remove-instance!
    [_ iid
     user-info user-ip]
    (imdatil/delete-with-body
     base-url (str "/api/v1/instance/" iid)
     {:user-info user-info
      :user-ip   user-ip}))

  (->reconfig-instance!
    [_ iid config
     user-info user-ip]
    (imdatil/post-to
     base-url
     (str "/api/v1/instance/"
          iid "/normal-reconfigure")
     (merge config
            {:user-info user-info
             :user-ip   user-ip})))

  (->start-instance!
    [_ id
     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/start")
     {:user-info user-info
      :user-ip   user-ip}))

  (->stop-instance!
    [_ id reason
     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/stop")
     {:reason reason
      :user-info user-info
      :user-ip   user-ip}))

  (->force-stop-instance!
    [_ id reason
     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/force-stop")
     {:reason reason
      :user-info user-info
      :user-ip   user-ip}))

  (->force-start-instance!
    [_ id
     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/force-start")
     {:user-info user-info
      :user-ip   user-ip}))

  (->recover-instance-from-orphan!
    [_ id alg-id run# version
     sell-vol buy-vol state meta-trade
     user-info user-ip]
    (imdatil/post-to
     base-url "/api/v1/recover/"
     {:iid id
      :user-info user-info
      :user-ip user-ip
      :alg-id alg-id
      :run-round run#
      :version version
      :sell-volume sell-vol
      :buy-volume buy-vol
      :persisted-state state
      :meta-trade meta-trade}))

  (->hazardous-reconfig-instance-table!
    [_ id config
     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/hazardous-reconfigure")
     (merge config
            {:user-info user-info
             :user-ip user-ip})))

  (->lock-instance! [_ id
                     user-info user-ip]
    (imdatil/post-to
     base-url (str "/api/v1/instance/"
                   id "/lock")
     {:user-info user-info
      :user-ip   user-ip}))

  (->unlock-instance!
    [_ id alg-id run# version sell-vol buy-vol state meta-trade
     user-info user-ip]
    (imdatil/post-to
     base-url "/api/v1/recover/"
     {:iid id
      :user-info user-info
      :user-ip user-ip
      :alg-id alg-id
      :run-round run#
      :version version
      :sell-volume sell-vol
      :buy-volume buy-vol
      :persisted-state state
      :meta-trade meta-trade})))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:im-data-access]
  [definition]
  (-> {:config (:component/config definition)}
      (map->IM-DataAccess)))

(defmethod hermes.component/config-spec
  [:im-data-access]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
