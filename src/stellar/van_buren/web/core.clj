(ns stellar.van-buren.web.core
  (:gen-class) ; for -main method in uberjar
  (:require [aero.core :as conf]
            [com.stuartsierra.component :as component]

            [hermes.lib.component.core :as hermes.component]

            ;; components
            [stellar.van-buren.web.logger.core]
            [stellar.van-buren.web.makna-db.postgres]
            [stellar.van-buren.web.makna-state.core]
            [stellar.van-buren.web.db.core]
            [stellar.van-buren.web.lobster-da.core]
            [stellar.van-buren.web.server.core]
            [stellar.van-buren.web.aggregation-da.core]
            [stellar.van-buren.web.aggregation-da.aggregation-db.core]
            [stellar.van-buren.web.im-data-access.core]
            [stellar.van-buren.web.im-data-access.im-db.core]
            [stellar.van-buren.web.external-comm.sms-sender]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn -main
  "path of the config.edn file is required"
  [config-path & args]
  (let [config (-> config-path conf/read-config)
        system (-> config hermes.component/create-component)]
    (-> system
        component/start)))

(defn run-dev []
  (let [config (-> "resources/config/config.edn" conf/read-config)
        system (-> config hermes.component/create-component)]
    (-> system component/start)))

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
