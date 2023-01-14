(ns stellar.van-buren.web.server.core
  (:require [taoensso.timbre :as timbre]

            [com.stuartsierra.component :as component]

            [io.pedestal.http :as http]

            [hermes.lib.component.core :as hermes.component]

            [stellar.van-buren.web.server.service :as service]
            [stellar.van-buren.web.api.routes :as api.routes]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn test?
  [service-map]
  (= :test (:env service-map)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defrecord Web-Server [config runnable-service
                       ;; dependencies
                       im-da web-db lobster-da aggregation-da sms-sender
                       makna-state]

  component/Lifecycle

  (start [component]
    (let [routes      (api.routes/routes
                        im-da web-db lobster-da aggregation-da
                        sms-sender config makna-state)
          service-map (service/get-service-map config routes)]
      (timbre/spy :info ["Starting Web Module" config])
      (if (some? runnable-service)
        component
        (cond-> service-map
          true                      http/create-server
          (not (test? service-map)) http/start
          true                      ((partial assoc component
                                              :runnable-service))))))

  (stop [component]
    (do (timbre/spy :info ["Stopping Web Module" config])
        (when (and (some? runnable-service) (not (test? config)))
          (timbre/spy :error ["Stopping HTTP Server ..."])
          (http/stop runnable-service))
        (assoc component :runnable-service nil))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:web-server]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Web-Server)))

(defmethod hermes.component/config-spec
  [:web-server]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
