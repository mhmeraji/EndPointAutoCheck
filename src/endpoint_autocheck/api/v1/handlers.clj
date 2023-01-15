(ns endpoint-autocheck.api.v1.handlers
  (:require [io.pedestal.interceptor :as interceptor]
            [endpoint-autocheck.db.protocol :as db.proto]
            [clojure.core.async :as async]
            [ring.util.response :as ring-resp]
            [taoensso.timbre :as timbre]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [tick.core :as tick]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- valid-url?
  [url]
  (try
    (let [response (http/get url)
          code     (get response :status)]
      (-> code (as-> x (<= 200 x 299))))
    (catch Exception e
      (timbre/warn ["Endpoint Failed the check " e])
      (->> false))))


(defn- initiate-auto-check
  [db username url duration alert-limit interrupt-ch]
  (async/thread
    (try
      (loop []
        (let [ep-record  (db.proto/get-endpoint db username url)
              valid-url? (valid-url? url)
              ep-record  (update ep-record :total inc)
              ep-record  (if valid-url?
                           (-> ep-record
                               (update :success inc))
                           (-> ep-record
                               (update :fail inc)))
              alert?     (>= (:fail ep-record) alert-limit)
              new-record (if alert?
                           (-> ep-record
                               (update :alerts
                                       conj {:date (str (tick/now))})
                               (assoc :fail 0))
                           (-> ep-record))]
          (timbre/info ["The new endpoint check report : " new-record])
          (db.proto/update-endpoint db username new-record))

        (let [[_ p] (async/alts!! [interrupt-ch] :default "continue")]
          (when (= p :default)
            (Thread/sleep (* 1000 duration))
            (recur))))
      (catch Exception e
        (timbre/error ["Error In Response Processing Thread : " e])
        (component/stop db)))))

(defn add-endpoint [db]
  (interceptor/interceptor
    {:name  :add-endpoint-interceptor
     :enter (fn add-endpoint [context]
              (try
                (let [username (-> context :request :identity :username)
                      {:keys [url
                              duration
                              alert-limit]}
                      (-> context :request :json-params)

                      url-count    (db.proto/get-endpoints-count db username)
                      interrupt-ch (async/chan)
                      ep-record    {:url         url
                                    :duration    duration
                                    :alert-limit alert-limit
                                    :alerts      []
                                    :total       0
                                    :success     0
                                    :fail        0}]
                  (if (< url-count 20)
                    (do
                      (db.proto/update-endpoint db username ep-record)
                      (initiate-auto-check db username url duration alert-limit interrupt-ch)
                      (assoc context :response
                             (ring-resp/response
                               {:status   "OK"
                                :username username
                                :url      url})))
                    (assoc context :response
                           (ring-resp/bad-request
                             {:status   "You have registered more than 20 endpoints!"
                              :username username
                              :url      url}))))
                (catch Exception e
                  (timbre/error ["Caught An Error While Adding An Endpoint : " e])
                  (let [e-message (ex-message e)
                        e-data    (ex-data e)]
                    (assoc context :response
                           (ring-resp/bad-request
                             (assoc e-data
                                    :status "Couldn't Register Endpoint!"
                                    :cause   e-message)))))))}))

(defn get-endpoint [db]
  (interceptor/interceptor
    {:name  :get-endpoint-interceptor
     :enter (fn get-endpoint [context]
              (try
                (let [username (-> context :request :identity :username)
                      ep-s     (db.proto/get-endpoint-s db username)
                      url-s    (->> ep-s
                                    (map :url)
                                    (into []))]
                  (assoc context :response
                         (ring-resp/response
                           {:status   "OK"
                            :username username
                            :url      url-s})))
                (catch Exception e
                  (timbre/error ["Caught An Error While Getting Endpoints : " e])
                  (let [e-message (ex-message e)
                        e-data    (ex-data e)]
                    (assoc context :response
                           (ring-resp/bad-request
                             (assoc e-data
                                    :status "Couldn't Get Endpoints!"
                                    :cause   e-message)))))))}))

(defn get-report [db]
  (interceptor/interceptor
    {:name  :get-report-interceptor
     :enter (fn get-report [context]
              (try
                (let [username      (-> context :request :identity :username)
                      {:keys [url]} (-> context :request :json-params)
                      ep-s          (db.proto/get-endpoint-s db username)
                      ep            (->> ep-s
                                         (filter #(= url (:url %)))
                                         (first))]
                  (if (nil? ep)
                    (assoc context :response
                           (ring-resp/bad-request
                             {:url   url
                              :cause "This url isn't registered for the user!"}))
                    (assoc context :response
                           (ring-resp/response
                             (merge {:status   "OK"
                                     :username username}
                                    (dissoc ep :alerts :fail))))))
                (catch Exception e
                  (timbre/error ["Caught An Error While Getting Report : " e])
                  (let [e-message (ex-message e)
                        e-data    (ex-data e)]
                    (assoc context :response
                           (ring-resp/bad-request
                             (assoc e-data
                                    :status "Couldn't Get Report!"
                                    :cause   e-message)))))))}))

(defn get-alert [db]
  (interceptor/interceptor
    {:name  :get-alert-interceptor
     :enter (fn get-alert [context]
              (try
                (let [username      (-> context :request :identity :username)
                      {:keys [url]} (-> context :request :json-params)
                      ep-s          (db.proto/get-endpoint-s db username)
                      ep            (->> ep-s
                                         (filter #(= url (:url %)))
                                         (first))]
                  (if (nil? ep)
                    (assoc context :response
                           (ring-resp/bad-request
                             {:url   url
                              :cause "This url isn't registered for the user!"}))
                    (assoc context :response
                           (ring-resp/response
                             {:status   "OK"
                              :username username
                              :alerts   (:alerts ep)}))))
                (catch Exception e
                  (timbre/error ["Caught An Error While Getting Report : " e])
                  (let [e-message (ex-message e)
                        e-data    (ex-data e)]
                    (assoc context :response
                           (ring-resp/bad-request
                             (assoc e-data
                                    :status "Couldn't Get Report!"
                                    :cause   e-message))))))
              )}))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
