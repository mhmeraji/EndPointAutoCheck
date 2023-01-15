FROM clojure:openjdk-11-lein-2.9.3
ARG NEXUS_USERNAME
ARG NEXUS_PASSWORD
ENV NEXUS_USERNAME=$NEXUS_USERNAME
ENV NEXUS_PASSWORD=$NEXUS_PASSWORD
RUN echo $NEXUS_USERNAME
RUN echo $NEXUS_PASSWORD
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
WORKDIR /usr/src/app
ENTRYPOINT ["java", "-jar", "app-standalone.jar"]

# supply NEXUS_USERNAME NEXUS_PASSWORD  with --build-args
