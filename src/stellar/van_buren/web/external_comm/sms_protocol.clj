(ns stellar.van-buren.web.external-comm.sms-protocol)


(defprotocol SMS-Send
  (send-verification-code! [sms-sender dest-number verification-code])

  (send-user-creation-confirm [sms-sender dest-number username otp]))
