(ns stellar.van-buren.web.makna-state.protocol)

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defprotocol Write
  (conjugate-item! [state log-record])
  (update-hash!    [state new-hash])
  (remove-items!   [state pos]))

(defprotocol Read
  (<-state [state])
  (<-items [state])
  (<-hash  [state]))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
