(ns stellar.van-buren.web.model.validation
  (:require [clojure.spec.alpha :as spec]))


(defn validate?! [validation-form  data]
  (if (spec/valid? validation-form data)
    true
    (throw (ex-info "Input invalid" (spec/explain-data validation-form data)))))
