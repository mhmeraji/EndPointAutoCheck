(ns endpoint-autocheck.core
  (:require
   [aero.core :as conf]
   [com.stuartsierra.component :as component]

   [hermes.lib.component.core :as hermes.component]

   ;; components
   [endpoint-autocheck.state.core]
   [endpoint-autocheck.server.core]
   [endpoint-autocheck.db.core]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn -main
  "path of the config.edn file is required"
  [config-path & args]
  (let [config (-> config-path conf/read-config)
        system (-> config hermes.component/create-component)]
    (-> system
        component/start)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(comment

  (def definition
    (-> "resources/definitions/system.edn" conf/read-config))

  (def system
    (-> definition
        hermes.component/create-component))

  (def started-system (-> system component/start))

  (-> started-system component/stop)

  )

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
