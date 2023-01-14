(ns stellar.van-buren.web.api.admin.aggregations)
;;------------------------------------------------------------------;;
;; ------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
;;util
;;------------------------------------------------------------------;;

(defn keys-in [m]
  (if (map? m)
    (vec
     (mapcat (fn [[k v]]
               (let [sub (keys-in v)
                     nested (map #(into [k] %) (filter (comp not empty?) sub))]
                 (if (seq nested)
                   nested
                   [[k]])))
             m))
    []))

(defn in?
  "true if coll contains elm"
  [elm coll]
  (true? (some #(= elm %) coll)))

(defn assoc-report-data [meta-data-record]
  (let [{modified :modified
         cancelled :cancelled
         created :created
         executed :executed} meta-data-record
        {executed-volume :value
         executed-quantity :quantity
         executed-number  :number} executed

        {cancelled-volume :value
         cancelled-quantity :quantity
         cancelled-number  :number} cancelled
        {created-volume :value
         created-quantity :quantity
         created-number  :number} created
        {modified-volume :value
         modified-quantity :quantity
         modified-number  :number} modified]
    (-> meta-data-record
        (assoc :cq  (or  created-quantity 0))
        (assoc :cv  (or  created-volume 0))
        (assoc :eq  (or  executed-quantity 0))
        (assoc :ev  (or  executed-volume 0))
        (assoc :mq  (or  modified-quantity 0))
        (assoc :mv  (or  modified-volume 0))
        (assoc :kq  (or  cancelled-quantity 0))
        (assoc :kv  (or  cancelled-volume 0))
        (assoc :ecq  (if (and (some?  executed-quantity)
                              (some? created-quantity))
                       (/ executed-quantity created-quantity)
                       0))
        (assoc :ecv (if (and (some?  executed-quantity)
                             (some? created-quantity))
                      (/ executed-volume created-volume)
                      0))
        (dissoc :created
                :modified
                :executed
                :cancelled))))
(defn build-inner-trade-map [general-key map-value]
  (let [[iid source aid isin side _ _] general-key]
    (assoc-report-data
     (merge
      {:source source
       :aid aid
       :isin isin
       :side side
       :iid iid}
      map-value))))

(defn filter-build-trade-format [fetched-data aids isins sides]
  (reduce
   (fn [collection key-vector]
     (conj collection (build-inner-trade-map
                       key-vector
                       (get-in fetched-data
                               (vec key-vector)))))
   []
   (reduce
    (fn [coll x]

      (conj coll (take 5 x)))
    #{}
    (filter
     (fn [input]
       (let [[_ source aid isin side _ _] input]
         ;; TODO
         (and (= source :tse)
              (or (in? aid aids)
                  (= aids "all"))
              (or (in? isin isins)
                  (= isins "all"))
              (or (= (name side) sides)
                  (= sides "both")))))
     (keys-in fetched-data)))))

(defn update-with-avg [m val-count]
  (->
   m
   (update :mq / val-count)
   (update :kq / val-count)
   (update :kv / val-count)
   (update :cv / val-count)
   (update :ecv / val-count)
   (update :eq / val-count)
   (update :ev / val-count)
   (update :cq / val-count)
   (update :ecq / val-count)
   (update :mv / val-count)))

(defn aggregate-trade-data [op-string group-by-field ungrouped-data]
  (map
   (fn [x]
     (let [val (second x)
           key (first x)
           val-count (count val)
           initial-post-data   (merge
                                (apply merge-with
                                       (cond
                                         (= op-string "sum") +
                                         (= op-string "avg") +
                                         :else identity)
                                       (map (fn [el]
                                              (select-keys
                                               el
                                               [:cq :ecq :mv :ev :eq
                                                :ecv :cv :kv :mq :kq]))
                                            val))
                                {(keyword group-by-field)
                                 key})]
       (if (= op-string "sum")
         initial-post-data
         (update-with-avg initial-post-data val-count))))
   (group-by
    (keyword group-by-field)
    ungrouped-data)))

(defn sort-trade-data [semi-final-data sort-field sort-direction]
  (let [asc-sort (sort-by (keyword sort-field) semi-final-data)]
    (if (= sort-direction "asc")
      asc-sort
      (reverse asc-sort))))

(defn remove-all-zeors-result
  [data]
  (reduce
    (fn [acc elem]
      (let [raw-vals      (vals elem)
            filtered-vals (filter
                            #(and
                               (or (int? %)
                                   (float? %)
                                   (double? %))
                               (< 0 %))
                            raw-vals)
            sum           (reduce + filtered-vals)]
        (if (not= sum 0)
          (conj acc elem)
          (->> acc))))
    []
    data))


(comment

  ;;Test data
  (def sources [:tse])
  (def aids "all")
  (def isins "all")
  (def sides "all")
  (def o-field :cq )
  (def o-direction "desc")
  (def another-sample {"d529a3d0-d208-11ec-8649-140106e8a5c9"
                       {:tse
                        {"mazdax::1"
                         {"BTCUSDT"
                          {:sell
                           {:created
                            {:quantity -2.9201380000000006,
                             :value -85731.94058358998,
                             :number -14}}}}}}
                       "d529a3d0-d208-11ec-8649-140106e8a5c1"
                       {:tse
                        {"mazdax::2"
                         {"ETHUSDT"
                          {:sell
                           {:created
                            {:quantity 5,
                             :value -85731.94058358998,
                             :number -14}}}}}}})
  (def ungrouped-trade-data (filter-build-trade-format another-sample aids isins sides))
  (clojure.pprint/pprint ungrouped-trade-data)
  (def unsorted-result (aggregate-trade-data "sum" "isin" ungrouped-trade-data))
  (clojure.pprint/pprint unsorted-result)
  (def sorted-result (sort-trade-data unsorted-result o-field o-direction ))
  (clojure.pprint/pprint sorted-result )

  )
