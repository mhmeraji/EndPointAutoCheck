(ns stellar.van-buren.web.api.v1.auth-interceptors
  (:require [taoensso.timbre :as timbre]

            [buddy.auth :as buddy.auth]
            [clojure.set :as clj.set]

            [stellar.van-buren.web.db.protocol :as db.proto]
            [stellar.van-buren.web.im-data-access.protocols.instance :as instocol]
            [stellar.lib.algorithm-utils.access :as algutil]
            [stellar.van-buren.web.db.core :as db]))


(defn admin-access
  []
  {:name  ::check-admin-access
   :enter (fn [ctx]
            (let [{:keys [role]} (-> ctx :request :identity)]
              (if (= role db/user-roles-admin)
                ctx
                (buddy.auth/throw-unauthorized
                  {:reason "Acrion requires admin access"}))))})


(defn instance-ownership-interceptor
  [db]
  {:name  ::instance-ownership-interceptor
   :enter (fn [ctx]
            (let [{:keys [username role]} (-> ctx :request :identity)
                  iid      (-> ctx :request :path-params :id)]

              (if (or
                    (= role db/user-roles-admin)
                    (= role db/user-roles-ifb-supervisor))
                ctx
                (let [inst     (db.proto/find-instance db iid)]
                  (if (= username (:creator-id inst))
                    ctx
                    (buddy.auth/throw-unauthorized
                      {:reason "unauthorized instance access"}))))))})

(defn ifb-instance?
  [im-da web-db iid]
  (let [instance      (db.proto/find-instance web-db iid)
        alg-id        (:alg-id instance)
        im-instance   (instocol/<-instance im-da iid)

        required-data (:required-data im-instance)
        isins         (keys
                        (algutil/deserialize-required-data
                          {:alg-id                   alg-id
                           :serialized-required-data required-data}))]

    (db.proto/is-one-of-isins-ifb?
      web-db isins)))

(def ifb-allowed-interceptors
  #{:instance-run-info-interceptor
    :get-instances-info-interceptor
    :get-instance-config-interceptor
    :get-instances-all-orders-interceptor
    :get-instances-active-orders-interceptor})

(defn instance-act-permission-interceptor
  [im-da web-db]
  {:name  ::instance-act-permission-interceptor
   :enter (fn [ctx]
            (let [{:keys [username role]}
                  (-> ctx :request :identity)
                  iid (-> ctx :request :path-params :id)]

              (cond
                (= role db/user-roles-ifb-supervisor)
                (if (ifb-instance? im-da web-db iid)
                  (let [interceptors-stack
                        (get-in ctx [:route :interceptors])

                        interceptors-names
                        (->> interceptors-stack
                             (map #(get % :name))
                             (into #{}))]

                    (if (empty? (clj.set/intersection
                                  ifb-allowed-interceptors
                                  interceptors-names))
                      (buddy.auth/throw-unauthorized
                        {:reason "unauthorized instance access"})
                      (->> ctx)))
                  (buddy.auth/throw-unauthorized
                    {:reason "unauthorized instance access"})))

              (->> ctx)))})

(defn instance-bulk-ownership-interceptor
  [db]
  {:name  ::instance-bulk-ownership-interceptor
   :enter (fn [ctx]
            (let [{:keys [username role]} (-> ctx :request :identity)
                  {:keys [instances]} (-> ctx :request :json-params)]

              (if (= role db/user-roles-admin)
                ctx
                (reduce
                 (fn [ctx iid]
                   (let [inst     (db.proto/find-instance db iid)]
                     (if (= username (:creator-id inst))
                       ctx
                       (buddy.auth/throw-unauthorized
                         {:reason "unauthorized instance access"}))))
                 ctx
                 instances))))})
