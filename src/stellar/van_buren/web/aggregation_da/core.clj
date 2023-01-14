(ns stellar.van-buren.web.aggregation-da.core
  (:require
   [com.stuartsierra.component :as component]


   [stellar.van-buren.web.aggregation-da.protocols.aggregate :as aggregate.proto]
   [stellar.van-buren.web.aggregation-da.aggregation-db.protocol :as aggregate-db.proto]

   [hermes.lib.component.core :as hermes.component]
   [taoensso.timbre :as timbre]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;


(defrecord Aggregation-Data-Access [config  aggregation-db]

  ;;------------------------------------------------------------------;;
  component/Lifecycle
  ;;------------------------------------------------------------------;;

  (start [component]
    (-> component))

  (stop [component]
    component)

  ;;------------------------------------------------------------------;;
  aggregate.proto/Aggregate
  (<-account-all-trades-history
    [_ filters aid]
    (aggregate-db.proto/<-all-trades-history aggregation-db filters aid))

  (<-account-all-trade-aggregation-history
    [_ filters aid]
    (aggregate-db.proto/<-trade-aggregation-history aggregation-db filters aid))
  (<-portfo-time-series
    [_ filters aid]
    (aggregate-db.proto/<-portfo-time-series aggregation-db filters aid))
  (<-account-active-orders-count
    [_  filters aid]
    (aggregate-db.proto/<-active-orders-count-history aggregation-db filters aid)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:aggregation-data-access]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Aggregation-Data-Access)))

(defmethod hermes.component/config-spec
  [:aggregation-data-access]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
