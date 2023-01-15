(ns endpoint-autocheck.server.core
  (:require [taoensso.timbre :as timbre]

            [com.stuartsierra.component :as component]

            [io.pedestal.http :as http]

            [hermes.lib.component.core :as hermes.component]

            [endpoint-autocheck.server.service :as service]
            [endpoint-autocheck.api.routes :as api.routes]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn test?
  [service-map]
  (= :test (:env service-map)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defrecord Web-Server [config runnable-service
                       web-db web-state]

  component/Lifecycle

  (start [component]
    (let [routes      (api.routes/routes
                        web-db web-state config)
          service-map (service/get-service-map config routes)]
      (timbre/spy :info ["Starting Web Module"
                         (some? web-db) (some? web-state)
                         config])
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
          (http/stop runnable-service))
        (assoc component :runnable-service nil))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component
  [:web-server]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Web-Server)))

(defmethod hermes.component/config-spec
  [:web-server]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
