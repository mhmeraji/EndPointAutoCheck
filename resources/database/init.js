db.createUser({
    user: "ep-web-dev",
    pwd: "ep-web-dev-pass",
    roles: [{
        role: "readWrite",
        db: "endpoint-autocheck"
    }]
});
