# EndPointAutoCheck
A piece of web application to automatically check the user inserted endpoints according to IE cource specifications

## How to run?
Navigate to the introspect folder and run the docker-compose file via ``` docker-compose up ``` command

## Components
The service is made of 3 main components which are as follows:
* db: This component is responsible for maintaining an active connection to the mongoDB and answer other component's queries in a agreed upon form
* server: This component serves as jetty web servers and passes through the user requests to other main components and send the results back in a proper format.
* state: Basically a mutable object that serves as storage in runtime (e.g. keeping track of the threads that check the endpoints)

## Configuration
Config the service using the file in ```resources/definitions```

## APIs
* /ping (GET) : accepts no body and pings the server, note that if you receive ```pong``` it means that the web server is up and running
* /epcheck/register (POST) : accepts a body of the form
```
{
    "name": "name",
    "username": "username",
    "password": "password",
    "max-session-count": 1,
    "role": "USER"
}
```
and registers the user in the system if the username is unique. ```max-session-count``` indicates the number of token that this user is allowed to have simultaneously.
If successful returns a response of the form
```
{
  "status": "OK",
  "username": "username",
  "role": "USER"
}
```
* /epcheck/login (POST) : responsible for logging in the system, accepts a body of the form
```
{
    "username": "username",
    "password": "password"
}
```
and returns a jwt signed token if the credentials were correct. The response body sample is
```
{
  "status": "OK",
  "username": "username",
  "token": "Token",
  "role": "USER"
}
```
* /epcheck/logout (POST) (Token Needed!) : depricates the token that was send as a ```Authorization``` header.
* /epcheck/api/v1/endpoint (POST) (Token Needed!) : Adds a new url to the watch list of the service, the acceptable body is of the form
```
{
    "url": "https://google.com",
    "duration": 10,
    "alert-limit": 2
}
```
which causes the system to check the url with a GET request in durations specified by the ```duration``` field in the body. Note that if the number of failures for the specified url exceeds the ```alert-limit```, an alert message with a timestamp will be recorded in the database.
* /epcheck/api/v1/endpoint (GET) (Token Needed!) : Lists the number of urls regitered for the user.
* /epcheck/api/v1/endpoint/report (GET) (Token Needed!) : Response is a report on the url specified by the user in the request body.
* /epcheck/api/v1/endpoint/alerts (GET) (Token Needed!) : Response is a list of alerts encountered during the auto check for the specified url with teir exact date-time of occurence.

## Source Code:
* https://github.com/mhmeraji/EndpointAutoCheck
