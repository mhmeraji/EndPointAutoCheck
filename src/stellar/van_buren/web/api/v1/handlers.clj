(ns stellar.van-buren.web.api.v1.handlers
  (:require [clojure.java.io :as io]

            [clojure.string :as string]
            [clojure.pprint :as pprint]

            [ring.util.response :as ring-resp]
            [ring.util.io :as ring-io]

            [taoensso.nippy :as nippy]

            [io.pedestal.interceptor :as interceptor]
            [stellar.lib.schema.stell.model.rlc.core :as sterlc.core]

            [stellar.lib.algorithm-utils.access :as algutil]

            [stellar.van-buren.web.db.protocol :as db.proto]
            [stellar.van-buren.web.db.core :as db]

            [stellar.van-buren.web.model.validation :as validation]
            [stellar.van-buren.web.model.api.v1.instance :as inst-model]

            [stellar.van-buren.web.aggregation-da.protocols.aggregate :as aggrocol]
            [stellar.van-buren.web.im-data-access.protocols.instance :as instocol]
            [stellar.van-buren.web.im-data-access.protocols.algorithm :as algocol]
            [stellar.van-buren.web.im-data-access.protocols.orders :as ordocol]

            [stellar.van-buren.web.lobster-da.protocol :as lob-da.proto]
            [stellar.van-buren.web.api.admin.handlers :as admin-orders]

            [taoensso.timbre :as timbre])
  (:import java.util.Base64))

(defn get-active-orders-history [aggregate-da]
  (interceptor/interceptor
   {:name  :user-get-active-orders-history-interceptor
    :enter (fn get-active-orders-history
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)

                   filters            (-> request :json-params)

                   active-orders-history (aggrocol/<-account-active-orders-count aggregate-da filters aid)
                   response         (ring-resp/response active-orders-history)]
               (assoc context :response response)))}))

(defn get-portfo-time-series [aggregate-da]
  (interceptor/interceptor
   {:name  :user-get-portfo-time-series
    :enter (fn get-portfo-time-series
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)
                   filters            (-> request :json-params)

                   portfo-time-series (aggrocol/<-portfo-time-series aggregate-da
                                                                     filters
                                                                     aid)

                   response         (ring-resp/response portfo-time-series)]
               (assoc context :response response)))}))


(defn get-trade-history [aggregate-da]
  (interceptor/interceptor
   {:name  :user-get-trade-history-interceptor
    :enter (fn get-trade-history
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)
                   filters            (-> request :json-params)

                   trade-history (aggrocol/<-account-all-trades-history aggregate-da
                                                                        filters
                                                                        aid)

                   response         (ring-resp/response trade-history)]
               (assoc context :response response)))}))


(defn get-aggregation-history [aggregate-da]
  (interceptor/interceptor
   {:name  :user-get-aggregation-history-interceptor
    :enter (fn get-aggregation-history
             [{request :request :as context}]
             (let [aid               (-> request :path-params :id)

                   filters            (-> request :json-params) ;;TODO make a filtering mechanism

                   get-aggregation-history (aggrocol/<-account-all-trade-aggregation-history aggregate-da filters aid)
                   response         (ring-resp/response get-aggregation-history)]
               (assoc context :response response)))}))

(defn ifb-instance-filter [web-db]
  (interceptor/interceptor
   {:name :ifb-instance-filter
    :leave (fn ifb-instance-filter [context]
             (let [instances     (-> context :response :body)
                   {role :role}  (-> context :request :identity)
                   filtered (if (= role db/user-roles-ifb-supervisor)
                              (into {}
                                    (filter
                                     (fn [[_ {isins :isins}]]
                                       (db.proto/is-one-of-isins-ifb?
                                        web-db
                                        isins))
                                     instances))
                              instances)]
               (assoc-in context [:response :body] filtered)))}))

(defn all-instances-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :all-instances-interceptor
    :enter (fn all-instances
             [context]
             (let [{:keys
                    [role
                     username]}  (-> context :request :identity)

                   instances     (condp = role
                                   db/user-roles-admin
                                   (db.proto/find-all-instances web-db)

                                   db/user-roles-ifb-supervisor
                                   (db.proto/find-all-instances web-db)

                                   (db.proto/find-all-user-instances
                                    web-db username))

                   im-instances  (reduce (fn [instances im-inst]
                                           (assoc
                                            instances
                                            (:iid im-inst)
                                            im-inst))
                                         {}
                                         (instocol/<-all-instances im-da))

                   instances-map
                   (reduce
                    (fn [insts-map {:keys [iid] :as inst-map}]
                      (try
                        (let [{:keys [alg-id
                                      required-data] :as im-inst}
                              (get im-instances iid)
                              state-info  (instocol/<-instance-last-state
                                           im-da iid alg-id)
                              inst-isins
                              (->>
                               (algutil/deserialize-required-data
                                {:alg-id                   alg-id
                                 :serialized-required-data required-data})
                               (keys)
                               (remove #(string/includes? % "::")))]
                          (assoc
                           insts-map
                           iid
                           (-> im-inst
                               (assoc :iname (:iname inst-map))
                               (assoc :isins inst-isins)
                               (assoc :accounts (:accounts inst-map))
                               (assoc :trade-data
                                      (select-keys
                                       state-info
                                       [:buy-volume :sell-volume
                                        :active-orders#ve-orders#]))
                               (dissoc :required-data))))
                        (catch Exception _
                          insts-map)))
                    {}
                    instances)

                   instances-map (cond
                                   (= role db/user-roles-ifb-supervisor)
                                   (->> instances-map
                                        (filter
                                         (fn [[_ {isins :isins}]]
                                           (db.proto/is-one-of-isins-ifb?
                                            web-db
                                            isins)))
                                        (into {}))
                                   :else
                                   (->> instances-map))

                   response      (ring-resp/response instances-map)]
               (assoc context :response response)))}))

(defn active-instances-interceptor [im-da]
  (interceptor/interceptor
   {:name  :active-instances-interceptor
    :enter (fn active-instances
             [context]
             (let [_        (throw (ex-info "NOT IMPLEMENTED! "
                                            {:handler :active-instances}))
                   response (ring-resp/response
                             (map :iid (instocol/<-active-instances im-da)))]
               (assoc context :response response)))}))

(defn all-algorithms-interceptor [web-db]
  (interceptor/interceptor
   {:name  :all-algorithms-interceptor
    :enter (fn all-algorithms
             [context]
             (let [{:keys
                    [role
                     username]}  (-> context :request :identity)

                   all-algs   (db.proto/find-all-algorithms web-db)

                   algs     (into [] (if (= db/user-roles-admin role)
                                       all-algs
                                       (let [user (db.proto/find-user-by-username web-db username)
                                             user-algs (:algorithms-known user)]
                                         (filter (fn [x] (some #(= % (:id x)) user-algs)) all-algs))))
                   response (ring-resp/response algs)]
               (assoc context :response response)))}))


(defn algorithm-config-spec-interceptor [im-da]
  (interceptor/interceptor
   {:name  :algorithm-config-spec-interceptor
    :enter (fn algorithm-config-spec
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   response (ring-resp/response
                             (algocol/<-algorithm-config-spec im-da id))]
               (assoc context :response response)))}))

(defn get-instance-lifecycle-events-interceptor [im-da]
  (interceptor/interceptor
   {:name  :instance-lifecycle-events-interceptor
    :enter (fn instance-lifecycle-events
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   response (ring-resp/response (instocol/<-instance-lifecycle-events im-da id))]
               (assoc context :response response)))}))

(defn download-instance-lifecycle-events-interceptor [im-da]
  (interceptor/interceptor
   {:name  :download-lifecycle-events-interceptor
    :enter (fn instance-lifecycle-events
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)

                   response (instocol/<-instance-lifecycle-events im-da id)

                   xlsx-byte-array (admin-orders/lifecycle-events->work-sheet-io-stream response)

                   stream (ring-io/piped-input-stream xlsx-byte-array)
                   ring-res  (-> (ring-resp/response stream)
                                 (ring-resp/content-type "application/octet-stream"))]
               (assoc context :response ring-res)))}))


(defn get-instance-run-info-interceptor [im-da]
  (interceptor/interceptor
   {:name  :instance-run-info-interceptor
    :enter (fn instance-run-info
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   response (ring-resp/response (instocol/<-instance-run-info im-da id))]
               (assoc context :response response)))}))

(defn get-instance-config-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instance-config-interceptor
    :enter (fn get-instance-config
             [{request :request :as context}]
             (let [iid        (-> request :path-params :id)
                   inst-map   (db.proto/find-instance web-db iid)
                   state-info (instocol/<-instance-last-state im-da iid (:alg-id inst-map))
                   response   (ring-resp/response
                               (algutil/deserialize-inst-config {:alg-id            (:alg-id inst-map)
                                                                 :serialized-config (:config state-info)}))]
               (assoc context :response response)))}))

(defn get-instance-alg-state-snapshot-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instance-algorithm-state-snapshot-interceptor
    :enter (fn get-instance-algorithm-state-snapshot
             [{request :request :as context}]
             (let [id               (-> request :path-params :id)
                   {alg-id :alg-id} (db.proto/find-instance web-db id)
                   state-info       (instocol/<-instance-last-state im-da id alg-id)
                   queue            (-> state-info :queue)
                   rlc              (algutil/deserialize-inst-queue {:alg-id           alg-id
                                                                     :serialized-queue queue})

                   active-orders (->> state-info
                                      :active-orders
                                      nippy/thaw
                                      vals
                                      (reduce
                                       (fn [aorders order]
                                         (assoc-in
                                          aorders
                                          [(:source order) (:isin order) (:side order) (:price order) :alg-order]
                                          (-> order
                                              (update :state #(or % "PENDING"))
                                              (select-keys [:price :quantity :state]))))
                                       rlc)
                                      (into {}))
                   response      (ring-resp/response active-orders)]
               (assoc context :response response)))}))

(defn get-instance-last-algorithm-table-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instance-last-algorithm-table-interceptor
    :enter (fn get-instance-last-algorithm-table
             [{request :request :as context}]
             (let [id               (-> request :path-params :id)
                   {side :side}     (:query-params request)
                   {alg-id :alg-id} (db.proto/find-instance web-db id)

                   state           (instocol/<-instance-last-state-details im-da id alg-id)
                   algorithm-table (-> state :state :table (get (keyword side) {}))
                   io-stream       (ring-io/piped-input-stream (algutil/save-excel-algorithm-table {:alg-id          alg-id
                                                                                                    :algorithm-table algorithm-table}))
                   response        (-> (ring-resp/response io-stream)
                                       (ring-resp/content-type "application/octet-stream"))]
               (assoc context :response response)))}))

(defn get-instance-algorithm-table-interval-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instance-algorithm-table-interval-interceptor
    :enter (fn get-instance-algorithm-table-interval
             [{request :request :as context}]
             (let [iid              (-> request :path-params :id)
                   {side :side}     (:query-params request)
                   {alg-id :alg-id} (db.proto/find-instance web-db iid)
                   state            (instocol/<-instance-last-state-details im-da iid alg-id)
                   response         (ring-resp/response (-> state :state :table (get (keyword side) {})))]
               (assoc context :response response)))}))


(defn- get-mm-subview [state query-map]
  (let [{{sell-limit       :entries-#
          sell-page-number :page-number
          {sell-order-by   :sort-field
           sell-order-dir  :sort-dir}
          :ordering} :sell
         {buy-limit       :entries-#
          buy-page-number :page-number
          {buy-order-by   :sort-field
           buy-order-dir  :sort-dir}
          :ordering} :buy}  query-map

        subview             (-> state :state :table)
        buy-subview         (get subview :buy {})
        sell-subview        (get subview :sell {})
        buy-#-of-pages      (int (Math/ceil (/ (count buy-subview) buy-limit)))
        sell-#-of-pages     (int (Math/ceil (/ (count sell-subview) sell-limit)))

        sell-page-number    (if (> sell-page-number sell-#-of-pages)
                              sell-#-of-pages
                              sell-page-number)

        buy-page-number    (if (> buy-page-number buy-#-of-pages)
                             buy-#-of-pages
                             buy-page-number)

        sell-ltd-subview   (subvec (vec (sort-by
                                         (keyword sell-order-by)
                                         (case sell-order-dir "asc" < "desc" >)
                                         (map second sell-subview)))
                                   (* sell-limit (- sell-page-number 1))
                                   (min (* sell-limit sell-page-number)
                                        (count sell-subview)))

        buy-ltd-subview   (subvec (vec (sort-by
                                        (keyword buy-order-by)
                                        (case buy-order-dir "asc" < "desc" >)
                                        (map second buy-subview)))
                                  (* buy-limit (- buy-page-number 1))
                                  (min (* buy-limit buy-page-number)
                                       (count buy-subview)))]
    {:sell {:number-of-pages sell-#-of-pages
            :table sell-ltd-subview}
     :buy  {:number-of-pages buy-#-of-pages
            :table buy-ltd-subview}}))

(defn- ytm-percent [table-row]
  (-> table-row
      (update :open-ytm #(format "%.2f %%" (* % 100)))
      (update :offset-ytm #(format "%.2f %%" (* % 100)))))


(defn- get-overall-performance [pmap]
  (let [pdata (reduce (fn [res [price {ytm :ytm value :value}]]
                        (-> res
                            (update :ytm conj (* ytm value))
                            (update :value conj value)
                            (update :qty conj (/ value price))))
                      {:ytm [] :value [] :qty []}
                      pmap)
        count     (-> pdata :value count)
        v-sum     (apply + (:value pdata))
        q-sum     (apply + (:qty pdata))]
    (if (= 0 count)
      {:total-value 0
       :avg-ytm-val nil
       :avg-price-val nil}
      {:total-value v-sum
       :avg-ytm-val (format "%.2f %%"
                            (*
                             (float (-> (/ (apply + (:ytm pdata)) v-sum)))
                             100))

       :avg-price-qty (float (/ v-sum q-sum))})))


(defn- merge-p
  [& pmaps]
  (reduce (fn [{tvalue :value} {:keys [ytm value]}]
            {:ytm ytm
             :value (+ tvalue value)})
          {:value 0 :ytm nil}
          pmaps))

(defn- merge-isin
  [& isinmaps]
  (apply
   merge-with
   (partial merge-with merge-p)
   isinmaps))

(defn- get-kraken-subview [rec query-map]
  (algutil/<-subview rec))

(defn- get-flucture-subview [rec query-map]
  (algutil/<-subview rec))

(defn- get-kraken-subview-tmn [rec query-map]
  (algutil/<-subview rec))

(defn- get-procurator-subview [rec query-map]
  (algutil/<-subview rec))

(defn- get-arbitrageur-subview [state query-map]
  (let [subview-d (reduce
                   (fn [res [isin isin-query-map]]
                     (let [stocks-trade      (get-in state [:state :core :stocks-trades (name isin)])
                           buy-overall-perf  (get-overall-performance (:buy stocks-trade))
                           sell-overall-perf (get-overall-performance (:sell stocks-trade))

                           {{sell-limit       :entries-#
                             sell-page-number :page-number
                             {sell-order-by  :sort-field
                              sell-order-dir :sort-dir}
                             :ordering} :sell
                            {buy-limit       :entries-#
                             buy-page-number :page-number
                             {buy-order-by  :sort-field
                              buy-order-dir :sort-dir}
                             :ordering} :buy} isin-query-map

                           subview (get-in state [:state :core :tables (name isin)])

                           buy-subview     (get subview :buy {})
                           sell-subview    (get subview :sell {})
                           buy-#-of-pages  (int (Math/ceil (/ (count buy-subview) buy-limit)))
                           sell-#-of-pages (int (Math/ceil (/ (count sell-subview) sell-limit)))

                           sell-page-number (if (> sell-page-number sell-#-of-pages)
                                              sell-#-of-pages
                                              sell-page-number)

                           buy-page-number (if (> buy-page-number buy-#-of-pages)
                                             buy-#-of-pages
                                             buy-page-number)

                           sell-ltd-subview (subvec (vec (sort-by
                                                          (keyword sell-order-by)
                                                          (case sell-order-dir "asc" < "desc" >)
                                                          (map second sell-subview)))
                                                    (* sell-limit (- sell-page-number 1))
                                                    (min (* sell-limit sell-page-number)
                                                         (count sell-subview)))

                           buy-ltd-subview (subvec (vec (sort-by
                                                         (keyword buy-order-by)
                                                         (case buy-order-dir "asc" < "desc" >)
                                                         (map second buy-subview)))
                                                   (* buy-limit (- buy-page-number 1))
                                                   (min (* buy-limit buy-page-number)
                                                        (count buy-subview)))

                           sell-sbv-table (into [] (map ytm-percent sell-ltd-subview))
                           buy-sbv-table  (into [] (map ytm-percent buy-ltd-subview))]

                       (assoc res isin {:sell {:number-of-pages sell-#-of-pages
                                               :table           sell-sbv-table
                                               :overall         sell-overall-perf}
                                        :buy  {:number-of-pages buy-#-of-pages
                                               :table           buy-sbv-table
                                               :overall         buy-overall-perf}})))
                   {}
                   query-map)
        stocks-trade (get-in state [:state :core :stocks-trades])

        overall-data (map (fn [[date d-stocks-trade]]
                            [date
                             (let [dmerge (apply merge-with (partial merge-with merge-p) (vals d-stocks-trade))]
                               (-> (reduce (fn [isinmap [isin pmap]]
                                             (assoc isinmap isin
                                                    {:sell (get-overall-performance (:sell pmap))
                                                     :buy  (get-overall-performance (:buy pmap))}))
                                           {}
                                           d-stocks-trade)
                                   (assoc :total {:sell (get-overall-performance (:sell dmerge))
                                                  :buy  (get-overall-performance (:buy dmerge))})))])
                          stocks-trade)

        overall-agg (apply (partial merge-with merge-isin) (vals stocks-trade))

        total-agg (apply
                   merge-isin
                   (vals overall-agg))

        total-data-total {:sell (get-overall-performance (:sell total-agg))
                          :buy  (get-overall-performance (:buy total-agg))}

        overall-data-total (into {}
                                 (map (fn [[isin ovt]]
                                        [isin {:sell (get-overall-performance (:sell ovt))
                                               :buy  (get-overall-performance (:buy ovt))}])
                                      overall-agg))

        orphan-value (get-in state [:state :general :orphan-value])]
    (assoc subview-d
           :orphan-value orphan-value
           :overall (into {"OVERALL" (-> overall-data-total
                                         (assoc :total total-data-total))} overall-data)
           :net-executed (get-in state [:state :core :net-executed]))))


(defn get-instance-subview-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instance-subview-interceptor
    :enter (fn get-instance-subview
             [{request :request :as context}]
             (let [id               (-> request :path-params :id)
                   {alg-id :alg-id} (db.proto/find-instance web-db id)
                   rec              (instocol/<-instance-last-state-details im-da id alg-id)
                    ;; TODO: must be added to `algutil` or similar interface as a multimethod

                   query-map (-> request
                                 :json-params
                                 :controls)
                   subview   (case alg-id
                               "Market-Making-Algorithm" (get-mm-subview
                                                          rec query-map)
                               "Arbitrageur-Algorithm"   (get-arbitrageur-subview
                                                          rec query-map)
                               "Kraken-Algorithm"        (get-kraken-subview
                                                          rec query-map)
                               "Flucture-Algorithm"      (get-flucture-subview
                                                          rec query-map)
                               "TMN-Kraken-Algorithm"    (get-kraken-subview-tmn
                                                          rec query-map)
                               "Procurator-Algorithm"    (get-procurator-subview
                                                          rec query-map)
                               {:result "Subview not provided"})
                   response  (ring-resp/response subview)]
               (assoc context :response response)))}))

(defn req-data->source-based-required-data
  [req-data]
  (reduce
   (fn [source->isins x]
     (let [sources-data-type (vec (map
                                   (fn [x]
                                     (when (= (second x)
                                              sterlc.core/TYPE-QUEUE)
                                       (-> x
                                           first
                                           name)))
                                   (second x)))
           isin        (first x)
           sources-data-type (remove nil? sources-data-type)]
       (conj
        source->isins
        (reduce
         (fn [acc source]
           (update acc source conj isin))
         source->isins
         sources-data-type))))
   {}
   req-data))

(defn get-instance-info-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :get-instances-info-interceptor
    :enter (fn get-instance-info
             [{request :request :as context}]
             (let [iid                         (-> request :path-params :id)
                   {req-data :required-data
                    alg-id   :alg-id :as inst} (instocol/<-instance im-da iid)

                   inst-map                    (db.proto/find-instance
                                                web-db iid)
                   state-info                  (instocol/<-instance-last-state
                                                im-da iid alg-id)

                   deserialized-required-data
                   (algutil/deserialize-required-data
                    {:alg-id                   alg-id
                     :serialized-required-data req-data})
                   inst-isins                  (keys deserialized-required-data)
                   sources-and-isins
                   (req-data->source-based-required-data
                    deserialized-required-data)
                   response
                   (ring-resp/response
                    (-> inst
                        (assoc :iname (:iname inst-map))
                        (assoc :isins inst-isins)
                        (assoc :sources-and-isin sources-and-isins)
                        (assoc :accounts (:accounts inst-map))
                        (assoc :trade-data
                               (select-keys state-info
                                            [:buy-volume :sell-volume
                                             :active-orders#]))
                        (dissoc :required-data)))]
               (assoc context :response response)))}))

(defn get-instance-active-orders-interceptor [im-da lobster-da web-db]
  (interceptor/interceptor
   {:name  :get-instances-active-orders-interceptor
    :enter (fn get-instance-active-orders
             [{request :request :as context}]
             (let [iid              (-> request :path-params :id)
                   {alg-id :alg-id} (db.proto/find-instance web-db iid)
                   state-info       (instocol/<-instance-last-state
                                     im-da iid alg-id)
                   response    (-> state-info
                                   :active-orders
                                   nippy/thaw
                                   (->> (reduce
                                         (fn [omap [k v]]
                                           (assoc omap k
                                                  (dissoc v :advented-at)))
                                         {})))]
               (assoc context :response (ring-resp/response response))))}))

(defn get-instance-all-orders-interceptor [im-da]
  (interceptor/interceptor
   {:name  :get-instances-all-orders-interceptor
    :enter (fn get-instance-all-orders
             [{request :request :as context}]

             (let [iid           (-> request :path-params :id)
                   {limit       :limit
                    page-number :page-number
                    order-by    :sort-field
                    order-dir   :sort-dir}  (:query-params request)

                   limit                    (Integer/parseInt limit)
                   page-number              (Integer/parseInt page-number)
                   order-by                 (keyword order-by)
                   order-dir                (keyword order-dir)

                   ;; TODO: use query for pagination
                   ;; FIXME: These queries will not work!
                  ;;  all-orders    (instocol/<-instance-all-orders im-da iid limit)
                  ;;  all-responses (instocol/<-instance-all-responses im-da iid limit)

                   all-responses                (instocol/<-instance-all-latest-responses im-da iid limit)
                   full-set all-responses

                   number-of-pages          (int (Math/ceil (/ (count full-set) limit)))

                   page-number              (max 1 (min page-number number-of-pages))

                   ret-orders (subvec (vec (sort-by
                                            order-by
                                            full-set))
                                      (* limit (- page-number 1))
                                      (min (* limit page-number)
                                           (count full-set)))

                   ret-orders (if (= :desc order-dir) (reverse ret-orders) ret-orders)
                   response      (ring-resp/response ret-orders)]
               (assoc context :response response)))}))

(defn download-instance-all-orders-interceptor [im-da]
  (interceptor/interceptor
   {:name  :download-instances-all-orders-interceptor
    :enter (fn get-instance-all-orders
             [{request :request :as context}]

             (let [iid           (-> request :path-params :id)
                   {limit       :limit
                    page-number :page-number
                    order-by    :sort-field
                    order-dir   :sort-dir}  (:query-params request)

                   limit                    (Integer/parseInt limit)
                   page-number              (Integer/parseInt page-number)
                   order-by                 (keyword order-by)
                   order-dir                (keyword order-dir)

                   ;; TODO: use query for pagination
                   ;; FIXME: These queries will not work!
                  ;;  all-orders    (instocol/<-instance-all-orders im-da iid limit)
                  ;;  all-responses (instocol/<-instance-all-responses im-da iid limit)

                   all-responses                (instocol/<-instance-all-latest-responses im-da iid limit)
                   full-set all-responses

                   number-of-pages          (int (Math/ceil (/ (count full-set) limit)))

                   page-number              (max 1 (min page-number number-of-pages))

                   ret-orders (subvec (vec (sort-by
                                            order-by
                                            full-set))
                                      (* limit (- page-number 1))
                                      (min (* limit page-number)
                                           (count full-set)))

                   ret-orders (if (= :desc order-dir) (reverse ret-orders) ret-orders)

                   xlsx-byte-array (admin-orders/order-response-map->xlsx-io-stream  ret-orders)
                   stream (ring-io/piped-input-stream xlsx-byte-array)
                   ring-res  (-> (ring-resp/response stream)
                                 (ring-resp/content-type "application/octet-stream"))]
               (assoc context :response ring-res)))}))


(defn check-igroup-name-duplicate-interceptor [web-db]
  (interceptor/interceptor
   {:name  :check-igroup-name-duplicate-interceptor
    :enter (fn check-igroup-name-duplicate [{request :request :as context}]
             (let [user-id  (-> request :identity :username)
                   igname   (-> request :query-params :igname)
                   response (ring-resp/response
                             (if (some? igname)
                               {:duplicate? (db.proto/is-igroup-name-duplicate?
                                             web-db igname user-id)}
                               {:error "Empty IGroup Name."}))]
               (assoc context :response response)))}))

(defn- instance-create-data [insert-results configs accounts creator-id]
  (reduce (fn [insts [iname insert-result]]
            (case (:type insert-result)
              :cmd-success (conj insts {:iid        (:iid insert-result)
                                        :iname      iname
                                        :accounts   accounts
                                        :creator-id creator-id
                                        :alg-id     (:alg-id (first (filter #(= (:iname %) iname) configs)))})
              :cmd-failure insts)) [] insert-results))


(defn- check-create-instance-input [web-db iname user-id acc-ids]
  (let [ign-duplicate? (db.proto/is-igroup-name-duplicate? web-db iname user-id)
        no-accounts?   (empty? acc-ids)]
    (cond
      ign-duplicate? [true "Duplicate Instance Name!"]
      no-accounts? [true "No Accounts Selected!"]
      :else [false nil])))

(defn- check-create-instance-permissions [web-db username alg-id acc-ids]
  (let [user (db.proto/find-user-by-username web-db username)

        user-algs (:algorithms-known user)
        user-accs (:accounts-known user)

        user-admin? (= (:role user) db/user-roles-admin)]

    (when-not user-admin?

      (when (empty? (filter #(= alg-id %) user-algs))
        (throw (ex-info "Algorithm Access denied." {:username username
                                                    :alg-id   alg-id})))

      (when (seq (filter
                  (fn [x] (every? #(not= % x) user-accs))
                  acc-ids))
        (throw (ex-info "Accounts Access denied" {:username username
                                                  :accouts  acc-ids}))))))

(defn create-instances-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :create-instances-interceptor
    :enter (fn create-instances
             [{request :request :as context}]
             {:pre [(validation/validate?! ::inst-model/create-api-input request)]}
             (let [user-role   (-> request :identity :role)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)

                   _           (when-not (= user-role db/user-roles-user)
                                 (throw (ex-info "Only system user can create instances"
                                                 {})))

                   params      (-> request :multipart-params)
                   iname       (get params "name")
                   user-id     (-> request :identity :username)
                   alg-id      (get params "algorithm")
                   acc-ids     (into [] (filter seq (string/split (get params "accounts") #"\s")))

                   _           (check-create-instance-permissions
                                web-db user-id alg-id acc-ids)

                   config-data (get params "config")
                   stream      (io/input-stream (:tempfile config-data))
                   [errors? error] (check-create-instance-input web-db iname user-id acc-ids)]
               (if errors?
                 (let [response (ring-resp/response {:successful-create-# 0
                                                     :failed-create-#     0
                                                     :message             error})]
                   (assoc context :response response))
                 (let [;; generating config from input file
                      ;; TODO: remove `iname`
                       configs (algutil/read-xlsx-multi-config {:alg-id      alg-id
                                                                :file-stream stream
                                                                :acc-ids     acc-ids})
                       configs (map-indexed
                                (fn [idx config]
                                  (assoc config :iname
                                         (str iname "-" (inc idx))))
                                configs)

                       accounts  (db.proto/find-all-accounts web-db)
                       results   (instocol/->instances!
                                  im-da configs accounts
                                  user-info user-ip)
                       notifs    (map (fn [[iname {type :type :as res}]]
                                        (-> res
                                            (assoc :type
                                                   (case type
                                                     :cmd-success :instance-insert-success
                                                     :cmd-failure :insert-instance-fail))
                                            (assoc :user-id user-id)
                                            (assoc :iname iname))) results)
                       _         (db.proto/insert-notifications web-db notifs)
                       instances (instance-create-data results configs acc-ids user-id)
                       _         (when (some? instances)
                                   (db.proto/insert-instances web-db instances)
                                   (db.proto/insert-igroup web-db iname user-id))
                      ;; FIXME : add messages for failed creates
                       response  (ring-resp/response
                                  (reduce
                                   (fn [cmap [_ {type :type}]]
                                     (case type
                                       :cmd-success (update cmap :successful-create-# inc)
                                       :cmd-failure (update cmap :failed-create-# inc)))
                                   {:successful-create-# 0
                                    :failed-create-#     0}
                                   results))]
                   (assoc context :response response)))))}))


(defn remove-instance-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :remove-instance-interceptor
    :enter (fn remove-instance
             [{request :request :as context}]
             (let [iid      (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   im-response (instocol/->remove-instance!
                                im-da iid user-info user-ip)

                   error?     (= :cmd-failure (:type im-response))

                   _        (when-not error?
                              (db.proto/remove-instance web-db iid))]
               (assoc context :response
                      (if error?
                        (ring-resp/bad-request {:error (:cause im-response)})
                        (ring-resp/response {:status "OK"})))))}))

(defn start-instance-interceptor [im-da]
  (interceptor/interceptor
   {:name  :start-instance-interceptor
    :enter (fn start-instance
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   response (ring-resp/response
                             (instocol/->start-instance!
                              im-da id user-info user-ip))]
               (assoc context :response response)))}))


(defn force-start-instance-interceptor [im-da]
  (interceptor/interceptor
   {:name  :force-start-instance-interceptor
    :enter (fn force-start-instance
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   response (ring-resp/response
                             (instocol/->force-start-instance!
                              im-da id user-info user-ip))]
               (assoc context :response response)))}))

(defn stop-instance-interceptor [im-da]
  (interceptor/interceptor
   {:name  :stop-instance-interceptor
    :enter (fn stop-instance
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)

                   {:keys
                    [reason]} (-> request :json-params)


                   response (ring-resp/response
                             (instocol/->stop-instance!
                              im-da id reason
                              user-info user-ip))]
               (assoc context :response response)))}))


(defn force-stop-instance-interceptor [im-da]
  (interceptor/interceptor
   {:name  :force-stop-instance-interceptor
    :enter (fn force-stop-instances
             [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   {:keys
                    [reason]} (-> request :json-params)

                   response (ring-resp/response
                             (instocol/->force-stop-instance!
                              im-da id reason
                              user-info user-ip))]
               (assoc context :response response)))}))


(defn reconfig-instances-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :reconfig-instances-interceptor
    :enter (fn reconfig-instances
             [{request :request :as context}]
             {:pre [(validation/validate?! ::inst-model/reconfigure-api-input request)]}
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   config   (-> request :json-params :config)
                  ;; TODO: check response and see if it was OK!
                   rec-res  (instocol/->reconfig-instance!
                             im-da id config
                             user-info user-ip)
                   response (if (= (:type rec-res) :cmd-failure)
                              (ring-resp/bad-request rec-res)
                              (ring-resp/response rec-res))]
               (assoc context :response response)))}))

(defn recover-from-orphan-interceptor [im-da web-db]
  (interceptor/interceptor
   {:name  :recover-instance-from-orphan-interceptor
    :enter (fn recover-instance-from-orphan-instance
             [{request :request :as context}]
             (let [iid       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)

                   {alg-id :alg-id} (db.proto/find-instance web-db iid)
                   state-info       (instocol/<-instance-last-state im-da iid alg-id)
                   response (ring-resp/response
                             (instocol/->recover-instance-from-orphan!
                              im-da iid alg-id
                              (:run state-info) (:version state-info)
                              (:sell-volume state-info)
                              (:buy-volume state-info)
                              (.encodeToString
                               (Base64/getEncoder) (:state state-info))
                              (.encodeToString
                               (Base64/getEncoder) (:meta-trade state-info))
                              user-info user-ip))]
               (assoc context :response response)))}))

(defn all-accounts-interceptor [web-db]
  (interceptor/interceptor
   {:name  :all-accounts-interceptor
    :enter (fn all-accounts
             [context]
             (let [{:keys
                    [role
                     username]}  (-> context :request :identity)

                   all-accounts (db.proto/find-all-accounts web-db)

                   accounts (into []
                                  (if (or (= role db/user-roles-admin)
                                          (= role db/user-roles-ifb-supervisor))
                                    all-accounts
                                    (let [user (db.proto/find-user-by-username web-db username)
                                          user-accs (:accounts-known user)]
                                      (filter (fn [x] (some #(= % (:aid x)) user-accs)) all-accounts))))

                   response (ring-resp/response accounts)]
               (assoc context :response response)))}))

(defn get-account-info-interceptor [web-db]
  (interceptor/interceptor
   {:name :get-account-interceptor
    :enter (fn account [{request :request :as context}]
             (let [id       (-> request :path-params :id)
                   response (ring-resp/response (db.proto/find-account web-db id))]
               (assoc context :response response)))}))


(defn- table-reconfig-arbitrageur-flatten [config]
  (into [] (apply concat (map
                          (fn [[isin-kw isin-c]]
                            (map
                             (fn [sided-map]
                               (assoc sided-map :isin (name isin-kw)))
                             (apply
                              concat
                              (map
                               (fn [[side-kw side-c]]
                                 (map
                                  (fn [idxd-map]
                                    (assoc idxd-map :side (name side-kw)))
                                  (map
                                   (fn [[idx-kw rec-map]]
                                     (assoc rec-map :index (Integer/parseInt
                                                            (name idx-kw))))
                                   side-c)))
                               isin-c))))
                          config))))

(defn reconfig-instance-table-interceptor [im-da]
  (interceptor/interceptor
   {:name  :reconfig-instance-table-interceptor
    :enter (fn reconfig-instance-table
             [{request :request :as context}]
            ;; {:pre [(validation/validate?! ::inst-model/reconfig-api-input request)]}
             (let [id       (-> request :path-params :id)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   config   (-> request :json-params)
                   config   (table-reconfig-arbitrageur-flatten
                             config)
                   im-resp  (instocol/->hazardous-reconfig-instance-table!
                             im-da id config user-info user-ip)
                   _ (when (= (:type im-resp) :cmd-failure)
                       (throw (ex-info "Hazardous Reconfiguration Failed"
                                       im-resp)))
                   response (ring-resp/response im-resp)]
               (assoc context :response response)))}))

(defn get-notifications [web-db]
  (interceptor/interceptor
   {:name  :get-notifications-interceptor
    :enter (fn get-notifications
             [{request :request :as context}]
             (let [username (-> request :identity :username)
                  ;; TODO: FIXME:
                   limit    (-> request
                                :query-params
                                :limit
                                (or "5")
                                Integer/parseInt)
                   notifs   (db.proto/find-notifications-for web-db username limit)
                   response (ring-resp/response (into [] notifs))]
               (assoc context :response response)))}))

(defn download-notifications-interceptor [web-db]
  (interceptor/interceptor
   {:name  :download-notifications-interceptor
    :enter (fn get-notifications
             [{request :request :as context}]
             (let [username (-> request :identity :username)
                   notifs   (db.proto/find-notifications-for web-db username)

                   io-stream (ring-io/piped-input-stream
                              #(->> (io/make-writer % {})
                                    (pprint/pprint (into [] notifs))
                                    ;; .flush
                                    ))
                   response  (-> (ring-resp/response io-stream)
                                 (ring-resp/content-type "application/edn"))]
               (assoc context :response response)))}))

(defn instance-bulk-actions [im-da web-db]
  (interceptor/interceptor
   {:name  :instance-bulk-actions-interceptor
    :enter (fn bulk-actions
             [{request :request :as context}]
             (let [{:keys [action
                           instances]} (-> request :json-params)
                   user-info (:identity request)
                   user-ip   (:remote-addr request)
                   results             (reduce
                                        (fn [results iid]
                                          (assoc
                                           results iid
                                           (case action
                                             "start"
                                             (instocol/->start-instance! im-da iid user-info user-ip)
                                             "stop"
                                             (instocol/->stop-instance! im-da iid
                                                                        "bulk"
                                                                        user-info user-ip)
                                             "force-start"
                                             (instocol/->force-start-instance! im-da iid user-info user-ip)
                                             "force-stop"
                                             (instocol/->force-stop-instance!  im-da iid
                                                                               "bulk"
                                                                               user-info user-ip)
                                             "remove"
                                             (instocol/->remove-instance! im-da iid user-info user-ip)
                                             "recover"
                                             (let [user-info (:identity request)
                                                   user-ip   (:remote-addr request)

                                                   {alg-id :alg-id} (db.proto/find-instance web-db iid)
                                                   state-info       (instocol/<-instance-last-state im-da iid alg-id)]
                                               (instocol/->recover-instance-from-orphan!
                                                im-da iid alg-id
                                                (:run state-info) (:version state-info)
                                                (:sell-volume state-info)
                                                (:buy-volume state-info)
                                                (.encodeToString
                                                 (Base64/getEncoder) (:state state-info))
                                                (.encodeToString
                                                 (Base64/getEncoder) (:meta-trade state-info))
                                                user-info user-ip))

                                             (throw (ex-info "Action invalid" {:action action})))))
                                        {} instances)
                   response            (ring-resp/response results)]
               (assoc context :response response)))}))

(defn get-symbols-interceptor [web-db]
  (interceptor/interceptor
   {:name  :get-symbols-interceptor
    :enter (fn get-symbols [context]
             (let [db-res (db.proto/find-all-symbols web-db)

                   response (ring-resp/response
                             (reduce
                              (fn [res {:keys [isin short-name]}]
                                (assoc-in res [isin :short-name] short-name))
                              {}
                              db-res))]
               (assoc context :response response)))}))

(defn get-order-history-interceptor [im-da]
  (interceptor/interceptor
   {:name  :get-order-history-interceptor
    :enter (fn get-order-history
             [{request :request :as context}]
             (let [tag      (-> request :path-params :id)
                   res      (ordocol/<-all-order-responses im-da tag)
                   response (ring-resp/response res)]
               (assoc context :response response)))}))


(defn download-order-history-interceptor [im-da]
  (interceptor/interceptor
   {:name  :user-download-order-history-interceptor
    :enter (fn get-order-history
             [{request :request :as context}]
             (let [tag      (-> request :path-params :id)

                   res      (ordocol/<-all-order-responses im-da tag)

                   xlsx-byte-array (admin-orders/order-history->work-sheet-io-stream res)

                   stream (ring-io/piped-input-stream xlsx-byte-array)
                   ring-res  (-> (ring-resp/response stream)
                                 (ring-resp/content-type "application/octet-stream"))]
               (assoc context :response ring-res)))}))
