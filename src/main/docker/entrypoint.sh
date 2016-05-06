#!/usr/bin/env bash

ADMIN_PASSWORD=${ADMIN_PASSWORD:-}
BING_KEY=${BING_KEY:-}
DB_DRIVER=${DB_DRIVER:-org.hsqldb.jdbcDriver}
DB_PASS=${DB_PASS:-}
DB_URL=${DB_URL:-jdbc:hsqldb:mem:testdb}
DB_USER=${DB_USER:-sa}
GOOGLE_API_KEY=${GOOGLE_API_KEY:-}
USE_BING=${USE_BING:-false}
USE_MAPQUEST=${USE_MAPQUEST:-true}
VEHICLE_NAME_TOPICS='['`echo ${VEHICLE_NAME_TOPICS} | perl -pe 's#([/\w+]+)#"\1"#g'`']'
GPS_TOPICS='['`echo ${GPS_TOPICS} | perl -pe 's#([/\w+]+)#"\1"#g'`']'
DEBUG_JAVASCRIPT=${DEBUG_JAVASCRIPT:-false}

if [ ! -f ${HOME}/.ros-bag-database/settings.yml ]
then
    mkdir ${HOME}/.ros-bag-database
    echo "!com.github.swrirobotics.support.web.Configuration
adminPassword: ${ADMIN_PASSWORD}
bingKey: ${BING_KEY}
driver: ${DB_DRIVER}
googleApiKey: ${GOOGLE_API_KEY}
jdbcPassword: ${DB_PASS}
jdbcUrl: ${DB_URL}
jdbcUsername: ${DB_USER}
useBing: ${USE_BING}
useMapQuest: ${USE_MAPQUEST}
vehicleNameTopics: ${VEHICLE_NAME_TOPICS}
gpsTopics: ${GPS_TOPICS}
debugJavascript: ${DEBUG_JAVASCRIPT}
" > ${HOME}/.ros-bag-database/settings.yml
fi

catalina.sh run
