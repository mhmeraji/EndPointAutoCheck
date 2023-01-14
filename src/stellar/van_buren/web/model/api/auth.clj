(ns stellar.van-buren.web.model.api.auth
  (:require [clojure.spec.alpha :as spec]))


(spec/def ::username string?)
(spec/def ::password string?)

(spec/def :login-input-form/json-params (spec/keys :req-un [::username ::password]))

(spec/def ::login-api-input (spec/keys :req-un [:login-input-form/json-params]))
