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
* /ping : accepts no body and pings the server, note that if you receive ```pong``` it means that the web server is up and running
* /epcheck/register : accepts a body of the form
```
{
    "name": "name",
    "username": "username",
    "password": "password",
    "max-session-count": 1,
    "role": "USER"
}
```
and registers the user in the system if the username is unique.
If successful returns a response of the form
```
{
  "status": "OK",
  "username": "username",
  "role": "USER"
}
```

## Source Code:
* https://github.com/mhmeraji/EndpointAutoCheck
