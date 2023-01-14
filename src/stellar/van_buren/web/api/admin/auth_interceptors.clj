(ns stellar.van-buren.web.api.admin.auth-interceptors
  (:require
   [buddy.auth :as buddy.auth]

   [stellar.van-buren.web.db.core :as db]))

(defn admin-access
  []
  {:name  ::check-admin-access
   :enter (fn [ctx]
            (let [{:keys [role]} (-> ctx :request :identity)]
              (if (= role db/user-roles-admin)
                ctx
                (buddy.auth/throw-unauthorized {:reason "Action requires admin access."}))))})

(defn ifb-admin-access
  []
  {:name  ::check-ifb-admin-access
   :enter (fn [ctx]
            (let [{:keys [role]} (-> ctx :request :identity)]
              (if (= role db/user-roles-ifb-supervisor)
                ctx
                (buddy.auth/throw-unauthorized {:reason "Action requires IFB admin access."}))))})
