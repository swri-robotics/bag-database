#!/usr/bin/env bash

# Set default values for any environment variables that were not explicitly specified
ADMIN_PASSWORD=${ADMIN_PASSWORD:-}
BAGDB_PATH=${BAGDB_PATH:-}
BING_KEY=${BING_KEY:-}
DB_DRIVER=${DB_DRIVER:-org.hsqldb.jdbcDriver}
DB_PASS=${DB_PASS:-}
DB_URL=${DB_URL:-jdbc:hsqldb:mem:testdb}
DB_USER=${DB_USER:-sa}
DEBUG_JAVASCRIPT=${DEBUG_JAVASCRIPT:-false}
DOCKER_HOST=${DOCKER_HOST:-}
FASTER_CODEC=${FASTER_CODEC:-false}
GOOGLE_API_KEY=${GOOGLE_API_KEY:-}
GPS_TOPICS='['$(echo "${GPS_TOPICS}" | perl -pe 's#([/\w+]+)#"\1"#g')']'
LDAP_BINDDN=${LDAP_BINDDN:-}
LDAP_BIND_PASSWORD=${LDAP_BIND_PASSWORD:-}
LDAP_SEARCH_BASE=${LDAP_SEARCH_BASE:-}
LDAP_SERVER=${LDAP_SERVER:-}
LDAP_USER_PATTERN=${LDAP_USER_PATTERN:-}
METADATA_TOPICS='['$(echo "${METADATA_TOPICS}" | perl -pe 's#([/\w+]+)#"\1"#g')']'
OPEN_WITH_URLS=${OPEN_WITH_URLS:-"{'Webviz':['https://webviz.io/app/?', 'remote-bag-url']}"}
SCRIPT_TMP_PATH=${SCRIPT_TMP_PATH:-/scripts}
TILE_HEIGHT_PX=${TILE_HEIGHT_PX-256}
TILE_MAP_URL=${TILE_MAP_URL-"http://{a-d}.tile.stamen.com/terrain/{z}/{x}/{y}.jpg"}
TILE_WIDTH_PX=${TILE_WIDTH_PX-256}
USE_BING=${USE_BING:-false}
USE_MAPQUEST=${USE_MAPQUEST:-true}
USE_TILE_MAP=${USE_TILE_MAP:-true}
VEHICLE_NAME_TOPICS='['$(echo "${VEHICLE_NAME_TOPICS}" | perl -pe 's#([/\w+]+)#"\1"#g')']'

# Don't overwrite an existing settings.yml file, but if we don't have one, write all of
# the variables out to it
if [ ! -f "${HOME}/.ros-bag-database/settings.yml" ]
then
    mkdir "${HOME}/.ros-bag-database"
    echo "!com.github.swrirobotics.support.web.Configuration
adminPassword: ${ADMIN_PASSWORD}
bingKey: ${BING_KEY}
debugJavascript: ${DEBUG_JAVASCRIPT}
dockerHost: ${DOCKER_HOST}
driver: ${DB_DRIVER}
fasterCodec: ${FASTER_CODEC}
googleApiKey: ${GOOGLE_API_KEY}
gpsTopics: ${GPS_TOPICS}
jdbcPassword: ${DB_PASS}
jdbcUrl: ${DB_URL}
jdbcUsername: ${DB_USER}
ldapBindDn: ${LDAP_BINDDN}
ldapBindPassword: ${LDAP_BIND_PASSWORD}
ldapSearchBase: ${LDAP_SEARCH_BASE}
ldapServer: ${LDAP_SERVER}
ldapUserPattern: ${LDAP_USER_PATTERN}
metadataTopics: ${METADATA_TOPICS}
openWithUrls: ${OPEN_WITH_URLS}
scriptTmpPath: ${SCRIPT_TMP_PATH}
tileHeightPx: ${TILE_HEIGHT_PX}
tileMapUrl: ${TILE_MAP_URL}
tileWidthPx: ${TILE_WIDTH_PX}
useBing: ${USE_BING}
useMapQuest: ${USE_TILE_MAP}
vehicleNameTopics: ${VEHICLE_NAME_TOPICS}
" > "${HOME}/.ros-bag-database/settings.yml"
fi

# If BAGDB_PATH was specified, rename the ROOT.war file; Tomcat will
# automatically extract it to the new path
if [ -f "/usr/local/tomcat/webapps/ROOT.war" ] && [ -n "${BAGDB_PATH}" ]
then
  mv "/usr/local/tomcat/webapps/ROOT.war" "/usr/local/tomcat/webapps/${BAGDB_PATH}.war"
fi


catalina.sh run
