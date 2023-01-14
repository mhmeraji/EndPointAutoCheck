(ns stellar.van-buren.web.im-data-access.protocols.algorithm)

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defprotocol Algorithm

  ;; (<-all-algorithms [da]
  ;;   "Sends get-all-algorithms command to im and returns results")
  (<-algorithm-config-spec [da id]
    "Returns config spec for algorithm w/ supplied id"))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
