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
METADATA_TOPICS='['`echo ${METADATA_TOPICS} | perl -pe 's#([/\w+]+)#"\1"#g'`']'
GPS_TOPICS='['`echo ${GPS_TOPICS} | perl -pe 's#([/\w+]+)#"\1"#g'`']'
DEBUG_JAVASCRIPT=${DEBUG_JAVASCRIPT:-false}
USE_TILE_MAP=${USE_TILE_MAP:-true}
TILE_MAP_URL=${TILE_MAP_URL-"http://{a-d}.tile.stamen.com/terrain/{z}/{x}/{y}.jpg"}
TILE_WIDTH_PX=${TILE_WIDTH_PX-256}
TILE_HEIGHT_PX=${TILE_HEIGHT_PX-256}
FASTER_CODEC=${FASTER_CODEC:-false}

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
useMapQuest: ${USE_TILE_MAP}
tileMapUrl: ${TILE_MAP_URL}
tileWidthPx: ${TILE_WIDTH_PX}
tileHeightPx: ${TILE_HEIGHT_PX}
vehicleNameTopics: ${VEHICLE_NAME_TOPICS}
metadataTopics: ${METADATA_TOPICS}
gpsTopics: ${GPS_TOPICS}
debugJavascript: ${DEBUG_JAVASCRIPT}
fasterCodec: ${FASTER_CODEC}
" > ${HOME}/.ros-bag-database/settings.yml
fi

catalina.sh run
