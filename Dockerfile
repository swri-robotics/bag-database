FROM maven:3.6.3-openjdk-11 as base-layer

LABEL maintainer="preed@swri.org"

# Build the bag DB in a separate stage so that the final image doesn't need
# to have the maven build environment in it.

RUN mkdir -p /src
COPY . /src
RUN cd /src && mvn package

FROM tomcat:9-jdk11

LABEL maintainer="preed@swri.org"

VOLUME ["/bags", "/root/.ros-bag-database/indexes", "/usr/local/tomcat/logs"]
EXPOSE 8080

# Need to manually install ffmpeg and perl for streaming video
RUN apt-get update \
    && apt-get install -y ffmpeg perl \
    && rm -rf /var/lib/apt/lists/*
RUN rm -rf /usr/local/tomcat/webapps/
COPY --from=base-layer /src/target/*.war /usr/local/tomcat/webapps/ROOT.war
COPY src/main/docker/entrypoint.sh /
COPY src/main/docker/server.xml /usr/local/tomcat/conf/server.xml

CMD ["/entrypoint.sh"]
