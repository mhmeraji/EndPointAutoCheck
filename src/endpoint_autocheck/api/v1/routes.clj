(ns endpoint-autocheck.api.v1.routes
  (:require [endpoint-autocheck.api.v1.handlers :as handlers]))


;;   We should be able to replace `interceptor/interceptor` with another function (or macro)
;; that apart from creating an interceptor, includes all the boilerplate code within each
;; interceptor; after this, each interceptor will be a handler (responding to a request)
;; and receiving all the injected dependencies as arguments after `request`.

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn routes [web-db]
  "Routes independant from authrorization and authentication mechanisms,
  dedicated to handle the primary functionality of the service"
  ["/v1"
   ["/endpoint"
    {:get  (handlers/get-endpoint web-db)
     :post (handlers/add-endpoint web-db)}]
   ["/report"
    {:get (handlers/get-report web-db)}]
   ["/alerts"
    {:get (handlers/get-alert web-db)}]])

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
