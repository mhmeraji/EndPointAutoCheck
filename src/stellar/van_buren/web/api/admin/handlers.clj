(ns stellar.van-buren.web.api.admin.handlers
  (:require
   [clojure.string :as string]

   [ring.util.response :as ring-resp]
   [ring.util.io :as ring-io]
   [io.pedestal.interceptor :as interceptor]
   [dk.ative.docjure.spreadsheet :as sheet]
   [stellar.van-buren.web.db.core :as db]
   [stellar.van-buren.web.db.protocol :as db.proto]
   [stellar.van-buren.web.lobster-da.protocol :as lob-da.proto]
   [stellar.van-buren.web.aggregation-da.protocols.aggregate :as aggrocol]
   [stellar.van-buren.web.im-data-access.protocols.instance :as instocol]
   [stellar.van-buren.web.external-comm.sms-protocol :as extsms-proto]
   [stellar.van-buren.web.api.admin.aggregations :as aggregations]

   [taoensso.timbre :as timbre])
  (:import java.util.Base64))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

;;Constants and Definitions For XLSX Generation
(def ALL-ORDER-XLSX-COLUMNS
  ["Type"
   "Description"
   "Side"
   "State"
   "Persisted At"
   "Reason"
   "Id"
   "ISIN"
   "Tag"
   "Account ID"
   "Quantity"
   "Price"
   "Message"
   "Executed"
   "Received At"])

(def AGGREGATE-COLUMNS
  ["Created Quantity"
   "Executed Created Quantity Ratio"
   "Modified Order Value"
   "Executed Order Value"
   "Exceuted Order Quantity"
   "Executed Created Value Ratio"
   "Created Order Volume"
   "Cancelled Order Volume"
   "Instance Id"
   "Modified Order Quantity"
   "Source"
   "ISIN"
   "Side"
   "Account"
   "Cancelled Quantity"])

(def LIFECYLCE-COLUMNS
  ["Reason"
   "Timestamp"
   "Type"])

(def ORDER-HISTORY-COLUMNS
  ["Description"
   "Type"
   "State"
   "Received at"
   "Side"
   "Account"
   "Quantity"
   "Tag"
   "Message"
   "Executed"
   "Reason"])
;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn XLSX-io-opener [workbook]
  (fn [output-stream]
    (with-open [^java.io.OutputStream writer output-stream]
      (sheet/save-workbook-into-stream! writer workbook))))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn lifecycle-events->work-sheet-io-stream [edn-vec]
  (let [scontent (conj
                  (->> (vec edn-vec)
                       (map #(vector
                              (-> % :reason)
                              (-> % :persisted-at str)
                              (-> % :type))))
                  LIFECYLCE-COLUMNS)
        workbook (sheet/create-workbook
                  (str "Lifecycle Events") scontent)]
    (XLSX-io-opener workbook)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;


(defn order-history->work-sheet-io-stream [edn-vec]
  (let [scontent (conj
                  (->> (vec edn-vec)
                       (map #(vector
                              (-> % :description)
                              (-> % :type)
                              (-> % :state)
                              (-> % :received-at str)
                              (-> % :side str)
                              (-> % :aid str)
                              (-> % :quantity)
                              (-> % :tag str)
                              (-> % :message)
                              (-> % :executed)
                              (-> % :reason))))
                  ORDER-HISTORY-COLUMNS)
        workbook (sheet/create-workbook
                  (str "Lifecycle Events") scontent)]
    (XLSX-io-opener workbook)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn aggregate-excel->work-sheet-io-stream [edn-vec]
  (let [scontent (conj
                  (->> (vec edn-vec)
                       (map #(vector
                              (-> % :cq)
                              (-> % :ecq)
                              (-> % :mv)
                              (-> % :ev)
                              (-> % :eq)
                              (-> % :ecv)
                              (-> % :cv)
                              (-> % :kv)
                              (-> % :iid)
                              (-> % :mq)
                              (-> % :source str)
                              (-> % :isin str)
                              (-> % :side str)
                              (-> % :aid str)
                              (-> % :kq))))
                  AGGREGATE-COLUMNS)
        workbook (sheet/create-workbook
                  (str "Order Aggregation") scontent)]
    (XLSX-io-opener workbook)))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn order-response-map->xlsx-io-stream [edn-seq]
  (let [scontent (conj
                  (->>
                   edn-seq
                   (vec)
                   (map #(vector
                          (-> % :type str)
                          (-> % :description str)
                          (-> % :side str)
                          (-> % :state str)
                          (-> % :persisted-at str)
                          (-> % :reason str)
                          (-> % :id str)
                          (-> % :isin str)
                          (-> % :tag str)
                          (-> % :aid :str)
                          (-> % :quantity)
                          (-> % :price)
                          (-> % :message)
                          (-> % :executed)
                          (-> % :received-at str))))
                  ALL-ORDER-XLSX-COLUMNS)
        workbook (sheet/create-workbook
                  (str "All Orders") scontent)]
    (XLSX-io-opener workbook)))


(defn all-users
  [request]
  (throw (ex-info "'all-users' not implemented yet" {})))

(defn instance-info
  [request]
  (throw (ex-info "'instance-info' not implemented yet" {})))


(defn update-user
  [request]
  (throw (ex-info "'update-user' not implemented yet" {})))

(defn- phone-number-valid? [number]
  (and
   (string? number)
   (= 10 (count number))
   (= \9 (first number))))



(defn- create-otp []
  (apply str (mapcat str
                     (take 5 (repeatedly #(char (+ (rand 26) 65))))
                     (take 5 (repeatedly #(char (+ (rand 10) 48))))
                     (take 5 (repeatedly #(char (+ (rand 26) 97)))))))

(defn create-user-interceptor [web-db sms-sender]
  (interceptor/interceptor
   {:name :add-user-interceptor
    :enter (fn add-user [{request :request :as context}]
             (let [{:keys [name
                           username
                           phone-number]} (->
                                           request
                                           :json-params)
                   errors  (cond-> []
                             (or (not (string? username))
                                 (empty? username))
                             (conj "Username is not valid.")
                             ;;
                             (empty? name)
                             (conj "Name cannot be empty.")
                             ;;
                             (db.proto/user-exists? web-db username)
                             (conj (str "User with username '" username "' already exists."))
                             ;;
                             (not (phone-number-valid? phone-number))
                             (conj "Phone number is not valid."))]
               (if (empty? errors)
                 (let [password  (create-otp)
                       phone-number (str "98" phone-number)
                       user-data {:name name
                                  :username username
                                  :phone-number phone-number
                                  :role db/user-roles-user}
                       _ (db.proto/insert-user! web-db user-data password)
                       _ (extsms-proto/send-user-creation-confirm sms-sender
                                                                  phone-number
                                                                  username
                                                                  password)]
                   (assoc context :response (ring-resp/response {:status "OK"})))
                 (assoc context :response (ring-resp/bad-request errors)))))}))



(defn block-user-interceptor [web-db]
  (interceptor/interceptor
   {:name :block-user-interceptor
    :enter (fn block-user [{request :request :as context}]
             (let [username  (-> request :path-params :username)
                   errors    (cond-> []
                               (or (not (string? username))
                                   (empty? username))
                               (conj "Username is not valid.")
                               ;;
                               (not (db.proto/user-exists? web-db username))
                               (conj (str "User with username '" username "' doesn't exist.")))

                   user-instances (db.proto/find-all-user-instances web-db username)

                   unlocked-instances (filter
                                       #(not= true (:is-locked? %))
                                       user-instances)

                   errors     (if (empty? unlocked-instances)
                                errors
                                (conj errors "User has unlocked instances."))]
               (if (empty? errors)
                 (do
                   (db.proto/block-user! web-db username)
                   (assoc context :response (ring-resp/response {:status "OK"})))
                 (assoc context :response (ring-resp/bad-request errors)))))}))


(defn- user-instances [web-db username]
  (let [instances (db.proto/find-all-user-instances web-db username)]
    (into [] (map #(select-keys % [:iid :iname]) instances))))

(defn get-all-users-info-interceptor [web-db]
  (interceptor/interceptor
   {:name :get-all-users-info-for-admin-interceptor
    :enter (fn get-users-info [context]
             (let [users (db.proto/find-all-user-role-users web-db)
                   users-info (map #(select-keys % [:username
                                                    :algorithms-known
                                                    :accounts-known])
                                   users)

                   users-info (reduce (fn [info user-info]
                                        (conj
                                         info
                                         (assoc
                                          user-info
                                          :instances
                                          (user-instances
                                           web-db (:username user-info)))))
                                      []
                                      users-info)]
               (assoc context :response (ring-resp/response
                                         users-info))))}))

(defn get-user-info-interceptor [web-db]
  (interceptor/interceptor
   {:name :get-user-info-for-admin
    :enter (fn get-user-info [{request :request :as context}]
             (let [username   (-> request :path-params :username)
                   user       (db.proto/find-user-by-username web-db username)

                   replacer-fn (fn [x] (if (nil? x) [] x))

                   user-info  (-> user
                                  (select-keys [:username
                                                :name
                                                :phone-number
                                                :algorithms-known
                                                :accounts-known
                                                :token-ip])
                                  (update :accounts-known replacer-fn)
                                  (update :algorithms-known replacer-fn))]
               (assoc context :response (ring-resp/response
                                         user-info))))}))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn grant-user-acc-interceptor [web-db]
  (interceptor/interceptor
   {:name :grant-user-account-interceptor
    :enter (fn grant-user-acc [{request :request :as context}]
             (let [username   (-> request :path-params :username)
                   user       (db.proto/find-user-by-username web-db username)

                   aid        (-> request :json-params :aid)

                   accs       (or (:accounts-known user) [])
                   _          (when-not (some #(= % aid) accs)
                                (db.proto/update-user-known-accs
                                 web-db
                                 username
                                 (conj accs aid)))]
               (assoc context :response (ring-resp/response
                                         {:status "GRANTED"}))))}))

(defn deny-user-acc-interceptor [web-db]
  (interceptor/interceptor
   {:name :deny-user-account-interceptor
    :enter (fn deny-user-acc [{request :request :as context}]
             (let [username   (-> request :path-params :username)
                   user       (db.proto/find-user-by-username web-db username)

                   aid        (-> request :json-params :aid)

                   accs       (or (:accounts-known user) [])
                   _          (when (some #(= % aid) accs)
                                (db.proto/update-user-known-accs
                                 web-db
                                 username
                                 (remove #(= % aid) accs)))]
               (assoc context :response (ring-resp/response
                                         {:status "DENIED"}))))}))

;;------------------------------------------------------------------;;

(defn grant-user-alg-interceptor [web-db]
  (interceptor/interceptor
   {:name :grant-user-algorithm-interceptor
    :enter (fn grant-user-alg [{request :request :as context}]
             (let [username   (-> request :path-params :username)
                   user       (db.proto/find-user-by-username web-db username)

                   alg-id     (-> request :json-params :algorithm-id)

                   algs       (or (:algorithms-known user) [])
                   _          (when-not (some #(= % alg-id) algs)
                                (db.proto/update-user-known-algs
                                 web-db
                                 username
                                 (conj algs alg-id)))]
               (assoc context :response (ring-resp/response
                                         {:status "GRANTED"}))))}))

(defn deny-user-alg-interceptor [web-db]
  (interceptor/interceptor
   {:name :deny-user-algorithm-interceptor
    :enter (fn deny-user-alg [{request :request :as context}]
             (let [username   (-> request :path-params :username)
                   user       (db.proto/find-user-by-username web-db username)

                   alg-id     (-> request :json-params :algorithm-id)
                   algs       (or (:algorithms-known user) [])
                   _          (when (some #(= % alg-id) algs)
                                (db.proto/update-user-known-algs
                                 web-db
                                 username
                                 (remove #(= % alg-id) algs)))]
               (assoc context :response (ring-resp/response
                                         {:status "DENIED"}))))}))


;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn lock-instance-interceptor [web-db im-da]
  (interceptor/interceptor
   {:name  :lock-instance-interceptor
    :enter (fn lock-instance
             [{request :request :as context}]
             (let [id          (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   im-response (instocol/->lock-instance!
                                im-da id user-info user-ip)

                   error?     (= :cmd-failure (:type im-response))

                   _        (when-not error?
                              (db.proto/lock-instance! web-db id))]
               (assoc context :response
                      (if error?
                        (ring-resp/bad-request {:error (:cause im-response)})
                        (ring-resp/response {:status "OK"})))))}))

(defn unlock-instance-interceptor [web-db im-da]
  (interceptor/interceptor
   {:name  :unlock-instance-interceptor
    :enter (fn unlock-instance
             [{request :request :as context}]
             (let [iid       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   {alg-id :alg-id} (db.proto/find-instance web-db iid)
                   state-info       (instocol/<-instance-last-state im-da iid alg-id)
                   im-response (instocol/->unlock-instance!
                                im-da iid alg-id
                                (:run state-info) (:version state-info)
                                (:sell-volume state-info)
                                (:buy-volume state-info)
                                (.encodeToString
                                 (Base64/getEncoder) (:state state-info))
                                (.encodeToString
                                 (Base64/getEncoder)
                                 (:meta-trade state-info))
                                user-info user-ip)

                   error?     (= :cmd-failure (:type im-response))
                   _          (when-not error?
                                (db.proto/unlock-instance! web-db iid))]
               (assoc context :response
                      (if error?
                        (ring-resp/bad-request {:error (:cause im-response)})
                        (ring-resp/response {:status "OK"})))))}))


;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn get-all-placed-orders [web-db lobster-da]
  (interceptor/interceptor
   {:name  :get-all-placed-orders
    :enter (fn get-all-placed-orders
             [context]
             (let [acc-ids (mapv :aid (db.proto/find-all-accounts web-db))
                   filtered-instances []

                   res (lob-da.proto/<-all-placed-orders lobster-da acc-ids filtered-instances)

                   _ (when-not (:succesful? res)
                       (throw
                        (ex-info
                         "Unsuccessful Lobster Access" {})))

                   resmap (reduce
                           (fn [resmap order]
                             (if-let [tag (:tag order)]
                               (assoc resmap (:order-id order)
                                      (assoc order :iid
                                             (first (string/split tag #"::"))))
                               (assoc resmap (:order-id order)
                                      (assoc order :orphaned? true))))
                           {}
                           (:placed-orders-fail-safe res))]
               (assoc context
                      :response
                      (ring-resp/response resmap))))}))



(defn get-ifb-placed-orders [web-db lobster-da]
  (interceptor/interceptor
   {:name  :get-ifb-placed-orders
    :enter (fn get-all-placed-orders
             [context]
             (let [acc-ids (mapv :aid (db.proto/find-all-accounts web-db))

                   filtered-instances []
                   res (lob-da.proto/<-all-placed-orders lobster-da acc-ids filtered-instances)

                   ;; : filter-out non-ifb orders
                   _ (when-not (:succesful? res)
                       (throw
                        (ex-info
                         "Unsuccessful Lobster Access" {})))

                   resmap (reduce
                           (fn [resmap order]
                             (if-let [tag (:tag order)]
                               (assoc resmap tag
                                      (assoc order :iid
                                             (first (string/split tag #"::"))))
                               (assoc resmap (:order-id order)
                                      (assoc order :orphaned? true))))
                           {}
                           (:placed-orders-fail-safe res))]
               (assoc context
                      :response
                      (ring-resp/response resmap))))}))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- cancel-single-order-by-id [lobster-da order-id aid reason]
  (lob-da.proto/->cancel-single-order-by-id lobster-da  order-id aid reason))


(defn cancel-single-order [lobster-da]
  (interceptor/interceptor
   {:name  :cancel-single-order
    :enter (fn [{request :request :as context}]

             (let [order-id (-> request :path-params :id)
                   aid (-> request :json-params :aid)
                   reason (-> request :json-params :reason)
                   res (cancel-single-order-by-id  lobster-da order-id aid reason)]
               (assoc context :response (ring-resp/response
                                         {:res res}))))}))

(defn cancel-account-orders [lobster-da]
  (interceptor/interceptor
   {:name :cancel-account-orders
    :enter (fn [{request :request :as context}]
             (let [aid (-> request :path-params :id)
                   reason (-> request :json-params :reason)
                   res (lob-da.proto/->cancel-account-orders lobster-da aid reason)]
               (assoc context :response (ring-resp/response
                                         {:res res}))))}))

(defn get-portfo-time-series [im-da]
  (interceptor/interceptor
   {:name  :get-portfo-time-series
    :enter (fn get-instance-subview
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)
                   filters            (-> request :json-params)

                   portfo-time-series (aggrocol/<-portfo-time-series im-da
                                                                filters
                                                                aid)

                   response         (ring-resp/response portfo-time-series)]
               (assoc context :response response)))}))



(defn get-trade-history [im-da]
  (interceptor/interceptor
   {:name  :get-trade-history-interceptor
    :enter (fn get-instance-subview
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)
                   filters            (-> request :json-params)

                   trade-history (aggrocol/<-account-all-trades-history im-da
                                                                        filters
                                                                        aid)

                   response         (ring-resp/response trade-history)]
               (assoc context :response response)))}))



(defn get-active-orders-history [im-da]
  (interceptor/interceptor
   {:name  :get-active-orders-history-interceptor
    :enter (fn get-instance-subview
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)

                   filters            (-> request :json-params)

                   active-orders-history (aggrocol/<-account-active-orders-count im-da filters aid)
                   response         (ring-resp/response active-orders-history)]
               (assoc context :response response)))}))


(defn get-aggregation-history [im-da]
  (interceptor/interceptor
   {:name  :get-aggregation-history-interceptor
    :enter (fn get-instance-subview
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)

                   filters            (-> request :json-params) ;;TODO make a filtering mechanism

                   get-aggregation-history (aggrocol/<-account-all-trade-aggregation-history im-da filters aid)
                   response         (ring-resp/response get-aggregation-history)]
               (assoc context :response response)))}))


(defn cancel-instance-orders [lobster-da]
  (interceptor/interceptor
   {:name :cancel-instance-orders
    :enter (fn [{request :request :as context}]
             (let [iid (-> request :path-params :id)
                   reason (-> request :json-params :reason)
                   res (lob-da.proto/->cancel-instance-orders lobster-da iid)]
               (assoc context :response (ring-resp/response
                                         {:res res}))))}))

(defn cancel-orders-in-bulk [lobster-da]
  (interceptor/interceptor
   {:name :cancel-bulk-orders
    :enter (fn [{request :request :as context}]
             (let [ids+aids (-> request
                                :json-params
                                :orders)

                   response (reduce
                             (fn [res order]
                               (let [{aid :aid
                                      order-id :order-id}
                                     order]
                                 (assoc res order
                                        (cancel-single-order-by-id
                                         lobster-da
                                         order-id
                                         aid
                                         "dummy"))))
                             {}
                             ids+aids)]
               (assoc context :response (ring-resp/response
                                         response))))}))

(defn- extract-aggregate-data
  [{request :request :as context} im-da]
  (let [{{o-field :field
          o-direction :dir} :ordering
         {a-field :aggregate-field
          a-fn    :aggregate-fn} :aggregate
         {f-iids   :instance
          f-aids   :account
          f-isins  :isin
          f-sides  :side} :filter
         :as spec}
        (-> request :json-params)

        f-iids (case f-iids
                 "all"
                 (map :iid (instocol/<-all-instances im-da))
                 f-iids)
        trades (instocol/<-instance-latest-meta-trades
                im-da
                f-iids)


        ungrouped-trade-data (aggregations/filter-build-trade-format
                              trades
                              f-aids
                              f-isins
                              f-sides)

        unsorted-result (if (= a-fn "noop")
                          ungrouped-trade-data
                          (aggregations/aggregate-trade-data
                           a-fn a-field ungrouped-trade-data))

        unsorted-result (aggregations/remove-all-zeors-result
                         unsorted-result)

        sorted-result (aggregations/sort-trade-data
                       unsorted-result o-field o-direction)]

    sorted-result))

(defn get-order-aggregate-data-admin [im-da]
  (interceptor/interceptor
   {:name :get-order-aggregate-data-admin
    :enter (fn [context]
             (assoc
              context
              :response
              (ring-resp/response
               (extract-aggregate-data
                context
                im-da))))}))

(defn download-aggregate-data-admin [im-da]
  (interceptor/interceptor
   {:name  :download-aggregate-data-admin
    :enter (fn [x]
             (let [aggregate-data (extract-aggregate-data
                                   x
                                   im-da)
                   xlsx-byte-array (aggregate-excel->work-sheet-io-stream aggregate-data)

                   stream (ring-io/piped-input-stream xlsx-byte-array)
                   ring-res  (-> (ring-resp/response stream)
                                 (ring-resp/content-type "application/octet-stream"))]
               (assoc x :response ring-res)))}))


(defn get-order-aggregate-data-ifb-admin [im-da]
  (interceptor/interceptor
   {:name  :get-order-aggregate-data-ifb-admin
    :enter (fn [x] (assoc
                    x
                    :response
                    (ring-resp/response
                     (extract-aggregate-data
                      x
                      im-da))))}))

(defn download-aggregate-data-ifb-admin [im-da]
  (interceptor/interceptor
   {:name  :download-aggregate-data-ifb-admin
    :enter (fn [x]
             (let [aggregate-data (extract-aggregate-data
                                   x
                                   im-da)

                   xlsx-byte-array (aggregate-excel->work-sheet-io-stream aggregate-data)
                   stream (ring-io/piped-input-stream xlsx-byte-array)
                   ring-res  (-> (ring-resp/response stream)
                                 (ring-resp/content-type "application/octet-stream"))]
               (assoc x :response ring-res)))}))


;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
