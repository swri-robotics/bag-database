FROM maven:3.5.2-jdk-8-slim as base-layer

LABEL maintainer="preed@swri.org"

ARG BAGDB_REPO="https://github.com/swri-robotics/bag-database.git"
ARG BAGDB_BRANCH="2.7.0"

# Build the bag DB in a separate stage so that the final image doesn't need
# to have the maven build environment in it.

RUN apt-get update \
    && apt-get install -y git libopencv2.4-java \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /src \
    && cd /src \
    && git clone ${BAGDB_REPO} \
    && cd bag-database \
    && git checkout ${BAGDB_BRANCH} \
    && mvn package

FROM tomcat:9-alpine

LABEL maintainer="preed@swri.org"

VOLUME ["/bags", "/root/.ros-bag-database/indexes", "/usr/local/tomcat/logs"]
EXPOSE 8080

RUN apk add --no-cache ffmpeg
RUN rm -rf /usr/local/tomcat/webapps/
COPY --from=base-layer /src/bag-database/target/*.war /usr/local/tomcat/webapps/ROOT.war
COPY entrypoint.sh /
COPY server.xml /usr/local/tomcat/conf/server.xml

CMD ["/entrypoint.sh"]
