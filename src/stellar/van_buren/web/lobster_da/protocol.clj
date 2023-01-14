(ns stellar.van-buren.web.lobster-da.protocol)



(defprotocol Lobster-DA

  (<-all-placed-orders [lob-da account-ids iids]
    "Returns all placed orders given list of account ids. iids")

  (<-instance-all-orders [lob-da iid]
    "Returns all orders of instance with `iid`")

  (->cancel-single-order-by-id [lob-da aid order-id reason]
    "Cancels order given its `order-id`")

  (->cancel-account-orders [lob-da account-id reason]
    "Cancels all orders for given accounts")

  (->cancel-instance-orders [lob-da iid]
    "Cancels all orders for given instance"))
