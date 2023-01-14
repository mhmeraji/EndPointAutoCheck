(ns stellar.van-buren.web.aggregation-da.aggregation-db.protocol)

(defprotocol Pooling
  
  (create-pool! [db])
  
  (close-pool! [db])
  
  (transact! [db fn])
  
  (read-only-transact! [db fn])
  
  (get-connection [db]))

(defprotocol AGGREGATION-DB

  (<-all-trades-history [db filters aid]
    "Queries and returns all trade history with filters")

  (<-active-orders-count-history [db filters aid]
    "Queries and returns all active-orders-history history with filters")

  (<-portfo-time-series
    [db filters aid])
  
  (<-trade-aggregation-history
    [db filters aid]
    "Queries and returns all aggregation history with filters"))
