(ns stellar.van-buren.web.aggregation-da.protocols.aggregate)

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defprotocol Aggregate
  (<-account-active-orders-count [da filters aid]
    "Gets active account orders count")

  (<-account-all-trades-history [da filters aid]
    "Gets Trade account orders count")

  (<-portfo-time-series [da filters aid]
    "Times series for portfo")

  (<-account-all-trade-aggregation-history
    [da filters aid]
    "Gets Aggregation history for account"))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
