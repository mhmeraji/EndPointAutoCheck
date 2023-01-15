(ns endpoint-autocheck.db.protocol)

(defprotocol Web-DB

  ;; USER

  (user-exists? [db username]
    "Checks to see if username is already taken by a user.
     Returns Boolean.")

  (insert-user! [db user-data]
    "Inserts a user using `user-data`, and sets password using `password`")

  (register-user-login! [db username user-ip user-agent time]
    "Adds a record of login for user.")

  (register-failed-login! [db username user-ip user-agent time msg]
    "Adds a record of failed login for user.")

  (register-user-logout!
    [db username user-ip time]
    [db username user-ip time auto-initiated?]
    "Adds a record of login for user.")

  (find-user-by-username-password [db username password]
    "Finds and returns a user matching given credentials, or `nil` if not found.")

  (get-active-session-count [db username]
    "Gets the active session counts by counting the number of live tokens in the database")

  (find-user-by-username [db username]
    "Finds and returns a user matching given username, or `nil` if not found.")

  (update-user-session! [db username token token-valid-until token-ip]
    "Adds session info to user record **upon successful login**")

  (keep-n-newest-sessions [db username n]
    "Keeps the N newest sessions on a user")

  (remove-user-session-token! [db username token]
    "Removes token info from user record")

  ;; Endpoints

  (add-endpoint [db username ep-record]
    "Add a new endpoint specification for the user")

  (get-endpoint-s [db username]
    "Gets the list of endpoints for the user")

  (update-endpoint [db username ep-record]
    "Add one endpiont to the user's list")

  (get-endpoint [db username url]
    "Get endpoint map for a specific url")

  (get-endpoints-count [db username]
    "Get the number of endpoints enlisted for a user")

  )
