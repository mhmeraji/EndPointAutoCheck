(ns stellar.van-buren.web.external-comm.sms-sender
  (:require

   [com.stuartsierra.component :as component]

   [hermes.lib.component.core :as hermes.component]

   [clj-http.client :as httpcli]

   [stellar.van-buren.web.external-comm.sms-protocol :as proto]
   [taoensso.timbre :as timbre]))

(defrecord Web-SMS-Sender [config sender-base-url]

  ;;----------------------------------------------------------------;;
  component/Lifecycle
  ;;----------------------------------------------------------------;;

  (start [component]
    (-> component
        (assoc :sender-base-url (:sender-base-url config))))

  (stop [component]

        (-> component))

  ;;----------------------------------------------------------------;;
  proto/SMS-Send

  (send-verification-code! [_ dest-number verification-code]
    (httpcli/post
     (str sender-base-url "/send-sms")
     {:form-params  {:send-to  dest-number
                     :message (str "کد تایید شما |"  verification-code "| است. van-buren")}
      :content-type :json}))

  (send-user-creation-confirm [_ dest-number username otp]
    (httpcli/post
     (str sender-base-url "/send-sms")
     {:form-params  {:send-to  dest-number
                     :message (str "حساب کاربری شما ایجاد شد. \n نام کاربری: \n"  username "\n رمز عبور یکبار مصرف: \n  " otp "\n van-buren")}
      :content-type :json})))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defmethod hermes.component/create-component [:external-comm :sms-sender]
  [definition]
  (-> {:config (:component/config definition)}
      (map->Web-SMS-Sender)))

(defmethod hermes.component/config-spec
  [:external-comm :sms-sender]

  [_]
  (-> any?))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
