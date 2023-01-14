(ns stellar.van-buren.web.rest-da-util
  (:require [clojure.string :as string]
            [clojure.edn :as edn]

            [cheshire.core :as cheshire]

            [taoensso.timbre :as timbre]

            [clj-http.client :as httpcli]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defn- url-join* [this that]
  (let [this       (cond-> this
                     (string/ends-with? this "/")
                     (subs 0 (-> this count dec)))
        that-parts (remove string/blank? (string/split that #"/"))]
    (string/join "/" (concat [this] that-parts))))

;;------------------------------------------------------------------;;

(defn- url-join [base & parts]
  (reduce url-join* base parts))


(defn get-raw [base-url sub-url]
  (let [{:keys [status
                body]
         :as   data} (httpcli/get
                       (url-join base-url sub-url)
                       {:accept :edn})]
    (if (= 200 status)
      body
      (throw (ex-info "request failed" data)))))

(defn get-from [base-url sub-url]
  (edn/read-string (get-raw base-url sub-url)))


(defn get-with-body [base-url sub-url body-data]
  (let [{:keys [status
                body]
         :as   data} (httpcli/request
                      {:method :get
                       :url (url-join base-url sub-url)
                       :content-type :json
                       :accept :edn
                       :body   (cheshire/generate-string body-data)})]
    (if (= 200 status)
      (-> body edn/read-string)
      (throw (ex-info "request failed" data)))))

;;------------------------------------------------------------------;;

;; TODO: we might need a separate case handling multipart data
(defn post-to

  ([base-url sub-url]
   (post-to base-url sub-url {}))

  ([base-url sub-url post-data]
   (let [{:keys [status
                 body]
          :as   data} (httpcli/post
                       (url-join base-url sub-url)
                       {:form-params  post-data
                        :content-type :edn
                        :accept       :edn})]
     (if (= 200 status)
       (-> body edn/read-string)
       (throw (ex-info "request failed" data))))))

;;------------------------------------------------------------------;;

(defn delete-from [base-url sub-url]
  (let [{:keys [status
                body]
         :as   data} (httpcli/delete
                       (url-join base-url sub-url)
                       {:accept :edn})]
    (if (= 200 status)
      (-> body edn/read-string)
      (throw (ex-info "request failed" data)))))


(defn delete-with-body [base-url sub-url body-data]
  (let [{:keys [status
                body]
         :as   data} (httpcli/request
                      {:method :delete
                       :url
                       (url-join base-url sub-url)
                       :content-type :json
                       :accept :edn
                       :body (cheshire/generate-string body-data)})]
    (if (= 200 status)
      (-> body edn/read-string)
      (throw (ex-info "request failed" data)))))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(comment

  (def base-url "/")

  (url-join base-url "/foo" "/bar/bar2//bar3/" "baz")


  (get-from base-url "/instance")

  (post-to "/instance" {:bar "bar"})


  )

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
