(ns stellar.van-buren.web.makna-state.core
  (:require
   [com.stuartsierra.component :as component]

   [taoensso.timbre :as timbre]

   [stellar.lib.schema.stell.model.sle.core :as stesle.core]
   [hermes.lib.component.core :as hermes.component]
   [stellar.van-buren.web.makna-state.protocol :as proto]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- initiate-state []
  (ref {:items  []
        :hash   nil}))

(defrecord Web-MAKNA-STATE-v-0-1-0

    [config ref-state]

  ;;----------------------------------------------------------------;;
  component/Lifecycle
  ;;----------------------------------------------------------------;;

  (start [component]
    (timbre/info ["Starting Makna State Component" config])
    (let [init-state (initiate-state)]
      (-> component
          (assoc :ref-state init-state))))

  (stop [component]
    (timbre/info "Stopping Makna State Component")
    (-> component))

  ;;----------------------------------------------------------------;;
  proto/Read
  ;;----------------------------------------------------------------;;

  (<-state [state]
    (deref ref-state))

  (<-items [state]
    (->> (proto/<-state state)
         :items))

  (<-hash  [state]
    (-> (proto/<-state state)
        (get :hash)))

  ;;----------------------------------------------------------------;;
  proto/Write
  ;;----------------------------------------------------------------;;

  (conjugate-item!
    [state log-record]
    (timbre/info ["Conjugated Item : " log-record])
    (dosync
      (ref-set
        ref-state
        (update (proto/<-state state)
                :items
                conj log-record))))

  (remove-items! [state pos]
    (dosync
      (ref-set
        ref-state
        (assoc (proto/<-state state)
               :items
               (subvec (proto/<-items state) pos)))))

  (update-hash! [state new-hash]
    (dosync
      (ref-set
        ref-state
        (assoc (proto/<-state state)
               :hash
               new-hash))))

  )

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component
  [:web :makna-state :ref :v-0.0.1]
  [definition]
  (-> {:config (-> definition :component/config)}
      (map->Web-MAKNA-STATE-v-0-1-0)))

(defmethod hermes.component/config-spec
  [:web :makna-state :ref :v-0.0.1]
  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
