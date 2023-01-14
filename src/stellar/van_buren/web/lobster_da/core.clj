(ns stellar.van-buren.web.lobster-da.core
  (:require
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre]
   [clojure.spec.alpha :as spec]

   [hermes.lib.component.core :as hermes.component]

   [stellar.van-buren.web.rest-da-util :as restil]
   [stellar.van-buren.web.lobster-da.protocol :as proto]))


;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defrecord Lobster-DataAccess [config
                               base-url]

  component/Lifecycle
  (start [component]
    (-> component
        (assoc :base-url (:base-address config))))

  (stop [component]
    component)

  proto/Lobster-DA

  (<-all-placed-orders [_ account-ids iids]
    (restil/get-with-body base-url "/sle-corrector/active-orders"
                          {:aids account-ids
                           :iids iids}))

  (<-instance-all-orders
    [_ iid]
    (timbre/spy
     (restil/get-with-body base-url "/sle-corrector/active-orders"
                           {:iids [iid]})))


  (->cancel-single-order-by-id [_ order-id aid reason]

    (restil/delete-with-body
     base-url "/sle-corrector/single-order"
     {:aid (str aid)
      :reason reason
      :order-id (str order-id)}))

  (->cancel-account-orders [this account-id reason]
    (let [account-orders (:placed-orders-fail-safe
                          (proto/<-all-placed-orders this [account-id] []))]
      (doseq
       [order account-orders]
        (let [order-id (:order-id order)]
          (try
            (restil/delete-with-body
             base-url "/sle-corrector/single-order"
             {:aid (str account-id)
              :reason reason
              :order-id (str order-id)})
            (catch Exception e
              (timbre/error ["Order with order id"  order-id "Was not cancelled"])))))))

  (->cancel-instance-orders [_ iid]
    (restil/delete-from
     base-url
     (str "/sle-corrector/ordering/" iid "/instance-orders"))))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:lobster-data-access]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Lobster-DataAccess)))

(spec/def ::base-address string?)
(spec/def ::config (spec/keys :req-un [::base-address]))

(defmethod hermes.component/config-spec
  [:lobster-data-access]
  [_]
  (-> ::config))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
