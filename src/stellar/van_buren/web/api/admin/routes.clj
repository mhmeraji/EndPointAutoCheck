(ns stellar.van-buren.web.api.admin.routes
  (:require
   [stellar.van-buren.web.api.admin.auth-interceptors :as admin-auth]

   [stellar.van-buren.web.api.admin.handlers :as handlers]))


(defn routes [web-db im-da lobster-da
              sms-sender aggregate-da]
  ["/admin"
   ^:interceptors [(admin-auth/admin-access)]
   ["/users-info"
    {:get (handlers/get-all-users-info-interceptor web-db)}]
   ["/user"
    {:get  `handlers/all-users
     :post (handlers/create-user-interceptor web-db sms-sender)
     :put  `handlers/update-user}
    ["/:username"
     {:get    (handlers/get-user-info-interceptor web-db)}
     ["/block" {:post (handlers/block-user-interceptor web-db)}]
     ["/algorithm-access"
      ["/deny" {:post (handlers/deny-user-alg-interceptor web-db)}]
      ["/grant" {:post (handlers/grant-user-alg-interceptor web-db)}]]
     ["/account-access"
      ["/deny" {:post (handlers/deny-user-acc-interceptor web-db)}]
      ["/grant" {:post (handlers/grant-user-acc-interceptor web-db)}]]]]
   ["/instance/:id"
    ^:constraints {:id #"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"}
    {:get `handlers/instance-info}
    ["/lock" {:post (handlers/lock-instance-interceptor web-db im-da)}]
    ["/unlock" {:post (handlers/unlock-instance-interceptor web-db im-da)}]
    ["/orders" {:delete (handlers/cancel-instance-orders lobster-da)}] ;;CHECK 
    ]
   ["/orders" ;;CHECK
    ["/bulk-cancel"
     {:post (handlers/cancel-orders-in-bulk lobster-da)}]
    ["/aggregate-data"
     {:post (handlers/get-order-aggregate-data-admin im-da)}]
    ["/download-aggregate-data"
     {:post (handlers/download-aggregate-data-admin im-da)}]]
   ["/order"
    {:get (handlers/get-all-placed-orders web-db lobster-da)}
    ["/:id" {:post (handlers/cancel-single-order lobster-da)}]]
   ["/account/:id"
    ["/orders" {:delete (handlers/cancel-account-orders lobster-da)}]
    ["/get-persisted-trade-history"
     {:post (handlers/get-trade-history aggregate-da)}]
    ["/get-portfo-time-series"
     {:post (handlers/get-portfo-time-series aggregate-da)}]
    ["/get-active-orders-history"
     {:post (handlers/get-active-orders-history aggregate-da)}]
    ["/get-aggregation-history"
     {:post (handlers/get-aggregation-history aggregate-da)}]]])

(defn ifb-routes [web-db im-da lobster-da]
  ["/ifb-admin"
   ^:interceptors [(admin-auth/ifb-admin-access)]
   ["/order"
    {:get (handlers/get-ifb-placed-orders web-db lobster-da)}
    ["/aggregate-data"
     {:post (handlers/get-order-aggregate-data-ifb-admin im-da)}]
    ["/download-aggregate-data"
     {:post (handlers/download-aggregate-data-ifb-admin im-da)}]]])
