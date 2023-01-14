(ns stellar.van-buren.web.im-data-access.protocols.instance)

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;

(defprotocol Instance

  (<-instance [da iid]
    "Reads and returns instance from IM, given the `iid`")

  (<-instance-last-state [da iid alg-id]
    "Reads and returns last state of instance given `iid`.
     `alg-id` should be provided at this state, since we're not querying
     the instance itself; and to use `algutil`, we need the `alg-id`.
     Throws error when `inst-version` is not present in state data
     presuming the instance doesn't exist.")

  (<-instance-last-state-details [da iid alg-id]
    "Reads and returns deserialized last state of instance given `iid`.
     `alg-id` should be provided at this state, since we're not querying
     the instance itself; and to use `algutil`, we need the `alg-id`.
     Throws error when `inst-version` is not present in state data
     presuming the instance doesn't exist.")

  (<-instance-state [da iid version alg-id]
    "Reads and returns a specific version of instance's state given `iid` and `version`.
     `alg-id` should be provided at this state, since we're not querying
     the instance itself; and to use `algutil`, we need the `alg-id`.
     Throws error when `inst-version` is not present in state data
     presuming the instance doesn't exist.")

  (<-instance-state-details [da iid version alg-id]
    "Reads and returns deserialized state of instance for a specifiv `version` given `iid`.
     `alg-id` should be provided at this state, since we're not querying
     the instance itself; and to use `algutil`, we need the `alg-id`.
     Throws error when `inst-version` is not present in state data
     presuming the instance doesn't exist.")

  (<-instance-latest-meta-trades [da iids]
    "Returns the instance latest persisted states when given a vectors of iids")

  (<-instance-subview [da iid alg-id]
    "Returns instance subview as extracted from `<-instance-last-state`")

  (<-instance-algorithm-table [da iid alg-id version]
    "Returns instance algorithm table as extracted from `<-instance-state`")

  (<-instance-all-orders [da iid limit]
    "Returns all orders of an instance with `iid`.
      TODO: add helpers for pagination")

  (<-instance-all-responses [da iid limit]
    "Returns all responses of an instance with `iid`.
      TODO: add helpers for pagination")
  (<-instance-all-latest-responses [da iid limit])

  (<-instance-lifecycle-events [da iid]
    "Returns instance lifecycle events given `iid`")

  (<-instance-run-info [da iid]
    "Returns instance run info given `iid`")

  (<-all-instances [da]
    "Queries and returns a list of all instances present in `im-db`")

  (<-active-instances [da]
    "Queries and returns a list of *active* instances present in `im-db`")

  (<-meta-trade [da specification]
    "Queries and returns a list of meta-trade table rows")

  (->instance! [da config accounts user-info user-ip]
    "Sends create-instance command to im given config and accounts
     returns creation result including id")

  ;; TODO: Should be deprecated
  (->instances! [da configs accounts user-info user-ip]
    "calls `->instance!` for each config in configs
     returns creation results including ids")

  (->remove-instance! [da id user-info user-ip]
    "Sends delete instance to im")

  (->reconfig-instance! [da id config user-info user-ip]
    "Sends reconfig command to im")

  (->start-instance! [da id user-info user-ip]
    "Sends single start command to im")

  (->stop-instance! [da id reason user-info user-ip]
    "Sends single stop command to im")

  (->force-stop-instance! [da id reason user-info user-ip]
    "Sends force stop command to im")

  (->force-start-instance! [da id user-info user-ip]
    "Sends force start command to im")

  (->recover-instance-from-orphan!
    [da id alg-id run# version sell-vol
     buy-vol state meta-trade user-info user-ip]
    "Sends recover command alongside with (last) state to im")

  (->hazardous-reconfig-instance-table!
    [da id config user-info user-ip]
    "Sends hazardous reconfig to im and returns response")

  (->lock-instance!
    [da id user-info user-ip]
    "Sends single lock command to im")

  (->unlock-instance!
    [da id alg-id run# version sell-vol buy-vol state
     meta-trade user-info user-ip]
    "Sends unlock command alongside with (last) state to im"))

;;------------------------------------------------------------------;;
;;------------------------------------------------------------------;;
