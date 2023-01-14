(ns stellar.van-buren.web.logger.core
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [tick.core :as tick]
   [buddy.core.hash :as hash]
   [buddy.core.codecs :as codecs]

   [taoensso.timbre :as timbre]

   [hermes.lib.component.core :as hermes.component]
   [stellar.lib.schema.stell.model.sle.core :as stesle.core]

   [stellar.van-buren.web.makna-db.protocol :as db.proto]
   [stellar.van-buren.web.makna-state.protocol :as state.proto])
  (:import
   [java.sql Timestamp]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- <-db-ready-log-record
  [record]
  (-> record
      (update :ResponseTime
              #(Timestamp/from
                 ^java.time.Instant
                 (tick/instant (or % (str (tick/now))))))
      (update :RequestTime
              #(Timestamp/from
                 ^java.time.Instant
                 (tick/instant (or % (str (tick/now))))))
      (update :APIID name)
      (update :APIID
              #(str/replace % #"-interceptor" ""))))

(defn- <-hash!
  [state new-record]
  (let [prv-hash (state.proto/<-hash state)
        new-hash (if (nil? prv-hash)
                   (-> (hash/sha256 (str new-record))
                       (codecs/bytes->hex))
                   (-> (hash/sha256 (str prv-hash prv-hash))
                       (codecs/bytes->hex)))]
    (state.proto/update-hash! state new-hash)
    (->> new-hash)))

(defn- start-logging
  [l-interval interrupt-ch
   state db]
  (async/thread
    (try
      (loop []
        (when-let [items (state.proto/<-items state)]
          ;; (timbre/info ["Logged Items : " items])
          (doseq [item items]
            (when (and
                    (some? item)
                    (some? (:APIID item)))
              (let [record-log (<-db-ready-log-record
                                   item)
                    new-hash   (<-hash! state record-log)
                    record-log (assoc record-log :HashField new-hash)]

                (timbre/info (str "(LCE) MAKNA-LOG : " record-log))

                (db.proto/->insert-log! db record-log))))
          (state.proto/remove-items! state (count items)))

        (let [[v p] (async/alts!! [interrupt-ch] :default "continue")]
          (if (= p :default)
            (do
              (Thread/sleep l-interval)
              (recur)))))
      (catch Exception e
        (timbre/error ["Error In Logging Component" e])
        (async/close! interrupt-ch)))))

(defn- recover-latest-hash
  [state db]
  (let [_ (println (db.proto/<-latest-hash db))
        hash   (-> (db.proto/<-latest-hash db)
                   first
                   :hashfield)]
    (state.proto/update-hash!
      state hash)))

(defrecord Web-Logger-v-0-1-0

    [config
     fetch-interval

     makna-db
     makna-state

     interrupt-ch]

  ;;----------------------------------------------------------------;;
    component/Lifecycle
    ;;----------------------------------------------------------------;;

    (start [component]
      (timbre/info ["Starting Logger Component" config])
      (let [interval     (:fetch-interval config)
            interrupt-ch (async/chan)]
        (recover-latest-hash makna-state makna-db)
        (start-logging interval interrupt-ch makna-state makna-db)
        (-> component
            (assoc
              :interrupt-ch     interrupt-ch
              :fetch-interval   interval))))

    (stop [component]
      (timbre/info "Stopping Logger Component")

      (when (some? interrupt-ch)
        (async/close! interrupt-ch))

      (-> component
          (assoc
            :interrupt-ch nil))))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component
  [:web :logger :v-0.0.1]
  [definition]
  (-> {:config (-> definition :component/config)}
      (map->Web-Logger-v-0-1-0)))

(defmethod hermes.component/config-spec
  [:web :logger :v-0.0.1]
  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
