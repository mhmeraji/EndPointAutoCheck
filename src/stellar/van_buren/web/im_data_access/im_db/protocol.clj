(ns stellar.van-buren.web.im-data-access.im-db.protocol)


(defprotocol Pooling
  (create-pool! [db])
  (close-pool! [db])
  (transact! [db fn])
  (read-only-transact! [db fn])
  (get-connection [db]))



(defprotocol IM-DB
  (<-all-algorithms [db]
    "Queries and returns all algorithms")

  (<-all-accounts [db]
    "Queries and returns all accounts")

  (<-all-instances [db]
    "Queries and returns all instances")

  (<-active-instances [db]
    "Queries and returns active instances")

  (<-instance [db iid]
    "Queries and returns instance for the `iid` provided")

  (<-instance-lifecycle-events [db iid]
    "Queries and returns instance lifecycle events for the `iid` provided")

  (<-instance-run-info [db iid]
    "Queries and returns instance run info for the `iid` provided")

  (<-instance-state-record [db iid version]
    "Queries and returns state data for an `iid` and specific `version`")

  (<-instance-last-state-record [db iid]
    "Queries and returns last state data for an `iid`.")

  (<-instance-all-latest-responses [db iid limit]
    "Queries for active responses")

  (<-instance-latest-meta-trades
    [db iids]
    "Queries for the latest states with
    the iid vector returns a vector of all matching states")

  (<-instance-all-orders [db iid limit]
    "Queries and returns all orders for instance w/ `iid`
     TODO: add pagination helpers")

  (<-instance-all-responses [db iid limit]
    "Queries and returns all responses for instance w/ `iid`
     TODO: add pagination helpers")

  (<-order-history [db tag]
    "Queries and returns all responses for order with `tag`
     Will return in ascending historical order."))
