(ns stellar.van-buren.web.api.v1.routes
  (:require [io.pedestal.interceptor :as interceptor]

            [stellar.van-buren.web.api.v1.auth-interceptors :as v1-auth]
            [stellar.van-buren.web.api.v1.handlers :as handlers]))

;; TODO:
;;   We should be able to replace `interceptor/interceptor` with another function (or macro)
;; that apart from creating an interceptor, includes all the boilerplate code within each
;; interceptor; after this, each interceptor will be a handler (responding to a request)
;; and receiving all the injected dependencies as arguments after `request`.
;;
;; e.g.:
;; (definterceptor all-instances [request im-da]
;;    (response (imda.proto/<-all-instances im-da (:ids request))))
;;
;;   This approach significantly increases readability
;;



(defn routes [im-da web-db lobster-da aggregate-da]
  ["/v1"
   ["/account/:id"
    ["/get-persisted-trade-history-user"
     {:post (handlers/get-trade-history aggregate-da)}]
    ["/get-portfo-time-series-user"
     {:post (handlers/get-portfo-time-series aggregate-da)}]
    ["/get-active-orders-history-user"
     {:post (handlers/get-active-orders-history aggregate-da)}]
    ["/get-aggregation-history-user"
     {:post (handlers/get-aggregation-history aggregate-da)}]]
   ["/ignamecheck" {:get (handlers/check-igroup-name-duplicate-interceptor web-db)}]
   ["/notifications"
    {:get (handlers/get-notifications web-db)}
    ["/dl" {:get (handlers/download-notifications-interceptor web-db)}]]
   ["/market-symbols" {:get (handlers/get-symbols-interceptor web-db)}]
   ["/algorithms" {:get (handlers/all-algorithms-interceptor web-db)}
    ["/:id/config-spec" {:get (handlers/algorithm-config-spec-interceptor im-da)}]]
   ["/accounts" {:get (handlers/all-accounts-interceptor web-db)}
    ["/:id" ^:constraints {:id #"[0-9]+"}
     {:get (handlers/get-account-info-interceptor web-db)}]]

   ["/active-instances" {:get (handlers/active-instances-interceptor im-da)}]
   ["/instance/bulk-action"
    ^:interceptors [(v1-auth/instance-bulk-ownership-interceptor web-db)]
    {:post (handlers/instance-bulk-actions im-da web-db)}]
   ["/instances"
    ;; ^:interceptors [(handlers/ifb-instance-filter web-db)]
    {:get  (handlers/all-instances-interceptor im-da web-db)
     :post (handlers/create-instances-interceptor im-da web-db)}
    ;; NOTE 1: This is the wildcard prefix that could cause ring to ignore everything above
    ;; NOTE 2: Regex matches uuids
    ["/:id"
     ^:constraints {:id #"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"}
     ^:interceptors [(v1-auth/instance-ownership-interceptor web-db)
                     (v1-auth/instance-act-permission-interceptor
                      im-da web-db)]
     {:get    (handlers/get-instance-info-interceptor im-da web-db)

      :delete (handlers/remove-instance-interceptor im-da web-db)}
     ["/start" {:post (handlers/start-instance-interceptor im-da)}]
     ["/stop" {:post (handlers/stop-instance-interceptor im-da)}]
     ["/force-stop" {:post (handlers/force-stop-instance-interceptor im-da)}]
     ["/force-start" {:post (handlers/force-start-instance-interceptor im-da)}]
     ["/recover-from-orphan" {:post (handlers/recover-from-orphan-interceptor im-da web-db)}]
     ["/algorithm-state-snapshot"
      {:get (handlers/get-instance-alg-state-snapshot-interceptor im-da web-db)}]
     ["/active-orders"
      {:get (handlers/get-instance-active-orders-interceptor im-da lobster-da web-db)}]
     ["/all-orders"
      {:get (handlers/get-instance-all-orders-interceptor im-da)}]
     ["/dl-all-orders"
      {:get (handlers/download-instance-all-orders-interceptor im-da)}]
     ["/lifecycle-events"
      {:get (handlers/get-instance-lifecycle-events-interceptor im-da)}]
     ["/dl-lifecycle-events"
      {:get (handlers/download-instance-lifecycle-events-interceptor im-da)}]
     ["/run-info"
      {:get (handlers/get-instance-run-info-interceptor im-da)}]
     ;; TODO: what is `algorithm-table-interval`?
     ;; RENAME to sth more meaningful
     ["/algorithm-table-interval"
      {:get (handlers/get-instance-algorithm-table-interval-interceptor im-da web-db)}]
     ["/algorithm-table"
      {:post (handlers/reconfig-instance-table-interceptor im-da)}]
     ["/last-algorithm-table"
      {:get (handlers/get-instance-last-algorithm-table-interceptor im-da web-db)}]
     ["/subview"
      {:post (handlers/get-instance-subview-interceptor im-da web-db)}]
     ["/config"
      {:get  (handlers/get-instance-config-interceptor im-da web-db)
       ;; NOTE: could be replaced w/ `:put`
       :post (handlers/reconfig-instances-interceptor im-da web-db)}]]]
   ["/orders/:id/history"
    {:get (handlers/get-order-history-interceptor im-da)}]
   ["/orders/:id/dl-history"
    {:get (handlers/download-order-history-interceptor im-da)}]])
