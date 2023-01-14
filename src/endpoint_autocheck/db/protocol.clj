(ns endpoint-autocheck.db.protocol)

(defprotocol Web-DB

  ;; USER

  (user-exists? [db username]
    "Checks to see if username is already taken by a user.
     Returns Boolean.")

  (insert-user! [db user-data]
    "Inserts a user using `user-data`, and sets password using `password`")




  (update-user-known-algs [db username algs]
    "Updates the `:algorithms-known` of the user with `username`")

  (update-user-known-accs [db username accs]
    "Updates the `:accounts-known` of the user with `username`")

  (find-user-by-username-password [db username password]
    "Finds and returns a user matching given credentials, or `nil` if not found.")

  (get-active-session-count [db username]
    "Gets the active session counts by counting the number of live tokens in the database")

  (find-user-by-username [db username]
    "Finds and returns a user matching given username, or `nil` if not found.")

  (block-user! [db username]
    "Blocks user.")

  (keep-n-newest-sessions [db username n]
    "Keeps the N newest sessions on a user")

  (update-user-session! [db username token token-valid-until token-ip]
    "Adds session info to user record **upon successful login**")


  (remove-user-session-token! [db username token]
    "Removes token info from user record")

  (update-user-password! [db username new-password password-update-time]
    "Updates user password and marks `password-update-time` as its `:last-password-update`")

  (set-user-2fa-code! [db username code set-time]
    "Sets user's 2fa code.")

  (increment-2fa-error-count! [db username]
    "Increments number of error counts for 2fa for the user")

  (register-user-login! [db username user-ip user-agent time]
    "Adds a record of login for user.")

  (register-failed-login! [db username user-ip user-agent time msg]
    "Adds a record of failed login for user.")

  (register-user-logout!
    [db username user-ip time]
    [db username user-ip time auto-initiated?]
    "Adds a record of login for user.")

  (get-user-auth-activity [db username]
    "Finds and returns user activity as registered by `register-user-login!` and `register-user-logout!`")

  (find-all-user-role-users [db]
    "Finds and returns a list of users with role `db/user-roles-user`")

  ;; CAPTCHA
  (register-captcha-puzzle! [db msg]
    "Stores a captcha puzzle to db. Returns pid")

  (find-captcha-puzzle [db pid]
    "Finds and returns puzzle by pid")

  (invaidate-captcha! [db pid]
    "Invalidates captcha puzzle")

  ;; ;; INSTANCE

  (find-instance [db iid]
    "finds instance by given `iid`")

  (find-all-instances [db]
    "Finds and returns all instances.")

  (find-all-user-instances [db username]
    "Finds and returns all instances owned by user")

  (insert-instances [db instances]
    "Inserts instances into db")

  (remove-instance [db iid]
    "Removes instance")

  (lock-instance! [db iid]
    "Locks instance")

  (unlock-instance! [db iid]
    "Unlocks instance")

  (is-igroup-name-duplicate? [db iname user-id]
    "Checks to see if igroup name already exists for user
     Returns a Boolean")

  (insert-igroup [db igname user-id]
    "Inserts igroup for name and userid")

  ;; ALGORITHMS
  (find-all-algorithms [db]
    "Returns all algorithms from web-db")

  ;; ACCOUNTS
  (find-all-accounts [db]
    "Returns all accounts from web-db")

  (find-account [db aid]
    "Returns the one account for `aid`")

  ;; SYMBOLS
  (find-all-symbols [db]
    "Finds and returns symbols")


  (is-one-of-isins-ifb? [db isins]
    "Checks to see if at least one provided isin is ifb
     Returns a Boolean")

  ;; NOTIFICATIONS
  (insert-notifications [db notificationss]
    "Batch inserts `notifications` to db, adding `:created-at` timestamp to the map.")

  (find-notifications-for [db user-id] [db user-id limit]
    "Returns notifications for user, given `user-id`.
     When `limit` is provided, gives last `limit` notifications based on their timestamp"))
