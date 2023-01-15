(ns endpoint-autocheck.state.core
  (:require [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [hermes.lib.component.core :as hermes.component]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- init-state
  []
  (ref {}))

(defrecord Storage [config web-db

                    state]

  component/Lifecycle

  (start [component]
    (timbre/info ["Starting State Module Component"
                  (some? web-db)])
    (let [state (init-state)]
      (-> component
          (assoc :state state))))

  (stop [component]
    (timbre/info "Stopping State Module Component")
    (-> component
        (assoc :state nil))))

;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component
  [:web-state]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Storage)))

(defmethod hermes.component/config-spec
  [:web-state]
  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
