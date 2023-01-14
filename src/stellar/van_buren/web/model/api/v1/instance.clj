(ns stellar.van-buren.web.model.api.v1.instance
  (:require [clojure.spec.alpha :as spec]

            [taoensso.timbre :as timbre]))


(spec/def ::ids (spec/coll-of int?))


(defn- is-valid-multipart-input? [data]
  (and (contains? data "config")
       (contains? data "name")
       (contains? data "algorithm")
       (contains? data "accounts")))


(spec/def :create-input-form/multipart-params is-valid-multipart-input?)
(spec/def ::create-api-input (spec/keys :req-un [:create-input-form/multipart-params]))

(spec/def :remove-input-forms/json-params (spec/keys :req-un[::ids]))
(spec/def ::remove-api-input (spec/keys :req-un [:remove-input-forms/json-params]))

(spec/def :start-input-forms/json-params (spec/keys :req-un[::ids]))
(spec/def ::start-api-input (spec/keys :req-un [:start-input-forms/json-params]))

(spec/def :stop-input-forms/json-params (spec/keys :req-un[::ids]))
(spec/def ::stop-api-input (spec/keys :req-un [:stop-input-forms/json-params]))

(spec/def :reconfigure-input-forms/json-params (spec/keys :req-un [::config]))
(spec/def :reconfigure-input-forms/path-params (spec/keys :req-un [::id]))
(spec/def ::reconfigure-api-input (spec/keys :req-un [:reconfigure-input-forms/json-params
                                                      :reconfigure-input-forms/path-params]))
