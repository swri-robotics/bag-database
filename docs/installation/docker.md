---
# Feel free to add content and custom Front Matter to this file.
# To modify the layout, see https://jekyllrb.com/docs/themes/#overriding-theme-defaults

layout: default
title: Docker
parent: Installation
nav_order: 2
description: "Installing with Docker"
permalink: /installation/docker
---

## Docker

The bag database can run standalone in order to demonstrate its functionality, but
if you do so it will have to rebuild the database every time it restarts.  Instead
you should link it to an external database.  PostgreSQL 11 with PostGIS 2.5 is
the only supported database.

The instructions here will describe how to manually create Docker containers, but
you may find it easier to use [Docker Compose](https://docs.docker.com/compose/) to
run the included `docker-compose.yml` file instead; just customize it to your needs.

First, create a virtual network for the containers:
```bash
docker network create bagdb
```

Start a PostgreSQL container with PostGIS support:
```
docker run -d \
    --name bagdb-postgres \
    --net bagdb \
    -v /var/lib/bagdb-postgres:/var/lib/postgresql/data \
    -e POSTGRES_PASSWORD=letmein \
    -e POSTGRES_USER=bag_database \
    -e POSTGRES_DB=bag_database \
    postgis/postgis:11-2.5
```

The bag database exposes port 8080 and expects to find bag files in a volume at /bags by default.  You can run it like so:
```
docker run -d \
    -p 8080:8080 \
    -v /bag/location:/bags \
    --name bagdb \
    --net bagdb \
    -e DB_DRIVER=org.postgresql.Driver \
    -e DB_PASS=letmein \
    -e DB_URL="jdbc:postgresql://bagdb-postgres/bag_database" \
    -e DB_USER=bag_database \
    -e METADATA_TOPICS="/metadata" \
    -e VEHICLE_NAME_TOPICS="/vehicle_name" \
    -e GPS_TOPICS="/localization/gps, /gps, /imu/fix" \
    swrirobotics/bag-database:latest
```

After the bag database has successfully started, the bag database should be available at `http://127.0.0.1:8080`.  Modify your Docker parameters as desired to expose it on a different port or set up [HAProxy](https://hub.docker.com/_/haproxy/) if you want to enable SSL or have it accessible via a subdirectory.

#### Volumes

Several volumes within the Docker container may be useful to mount externally:

##### `/bags`

The location which will be monitored for bag files.

##### `/usr/local/tomcat/logs`

The location where Tomcat places its log files.

#### Environment Variables

Several environment variables can be set to configure the Docker container:

##### ADMIN_PASSWORD

The default password for administrative access.  If this is not set, one will be randomly generated and printed to the log file on initial startup.

##### DB_DRIVER

The class name of the JDBC driver to use.

##### DB_PASS

The password to use when connecting to the database.

##### DB_URL

The JDBC URL for connecting to the database.

##### DB_USER

The username to use when connecting to the database.

##### DOCKER_HOST

The URL to use to connect to a Docker service.  This can be empty if you do not intend to run scripts on bag files.

##### GOOGLE_API_KEY

A Google API key that has permission to use the Google Maps GeoCoding API; this is necessary in order to resolve place names for GPS coordinates.

##### TMP_SCRIPT_PATH

Path to write temporary script files.  This can be empty if you do not intend to run scripts.  It must be writable by the bag database, and the Docker service that runs the scripts must have it mounted as a volume at the same location.

##### METADATA_TOPICS

A comma-separated list of `std_msgs/String` topics in bag files that will be searched for metadata.  The messages on the topic should be newline-separated tags that are made of colon-separated key/value pairs; for example:
```
name: John Doe
email: jdoe@example.com
```
Every value will be read from every topic specified, but if there are any duplicate keys, the last-read values will take precedence.

##### USE_TILE_MAP

Set this to `true` to use a WMTS tile map for displaying map imagery; set it to `false` to disable MapQuest.  The default is `true`.

##### TILE_MAP_URL

If `USE_TILE_MAP` is `true`, this URL will be used as a template for retrieving 
map tiles.  See the documentation for the `url` property of OpenLayers' 
[ol.source.XYZ](http://openlayers.org/en/latest/apidoc/ol.source.XYZ.html) class.  
The default value is `http://{a-d}.tile.stamen.com/terrain/{z}/{x}/{y}.jpg`, which will
use the terrain map provided by [Stamen](http://maps.stamen.com/).

##### TILE_WIDTH_PX

The width of the tiles returned from the tile map in pixels.  The default is `256`.

##### TILE_HEIGHT_PX

The height of the tiles returned from the tile map in pixels.  The default is `256`.

##### USE_BING

Set this to `true` to use Bing Maps for displaying map imagery; set it to `false` to disable Bing.  The default is `false`.

##### BING_KEY

The API key to use when connecting to Bing Maps.

##### VEHICLE_NAME_TOPICS

A comma-separated list of `std_msg/String` topics that will be searched for a vehicle name; the first one found will be used.

##### GPS_TOPICS

A comma-separated list of topics to search for GPS messages; the first one found will be used.  Any message that has the following fields will work:
```
float64 latitude
float64 longitude
Header header
```
If there are no topics configured or none of them are found, it will try to use the first topic it can find that publishes the `sensor_msgs/NavSatFix`, `gps_common/GPSFix`, or `marti_gps_common/GPSFix` messages, in that order.

##### LDAP_BINDDN

If authenticating against an LDAP server that requires authentication, the Bind DN.  If this is left blank, it will not attempt to authenticate.

##### LDAP_BIND_PASSWORD

If authenticating against an LDAP server that requires authentication, the password for the Bind DN.

##### LDAP_SEARCH_BASE

The search base for finding users in the LDAP server.

##### LDAP_SERVER

The LDAP server for authentication.  If set to an empty string, LDAP authentication will not be enabled, and anonymous users may connect.

##### LDAP_USER_PATTERN

The pattern for finding user DNs in the LDAP server.  `{0}` will be replaced with the username from the login form.

##### DEBUG_JAVASCRIPT

Set this to `true` to force the application to load non-minified versions of Javascript files.  This will increase load times.  The default is `false`.

### As An Application Server Servlet

This has been tested with Java 11 and Tomcat 9.

1. Start up a PostgreSQL server; create an empty database and a user with access to it.
2. Start up your application server and deploy the WAR file to it.
3. The bag database will automatically create a directory at `${HOME}/.ros-bag-database` and place its configuration inside there.
4. Edit `${HOME}/.ros-bag-database/settings.yml` and set your configuration, then restart the application.  Here's an example of a valid config file; keys you don't want to set can be omitted.

    ```yml
    !com.github.swrirobotics.support.web.Configuration
    bingKey: PKnOQDvUxRJ0bEZdBH7m
    dockerHost: http://localhost:2375
    driver: org.postgresql.Driver
    googleApiKey: PKnOQDvUxRJ0bEZdBH7m
    gpsTopics: 
    - /localization/gps
    - /gps
    - /vehicle/gps/fix
    jdbcPassword: letmein
    jdbcUrl: jdbc:postgresql://bagdb-postgres/bag_database
    jdbcUsername: bag_database
    useBing: true
    useMapQuest: false
    vehicleNameTopics: 
    - /vms/vehicle_name
    ```

5. Look inside the log file at `${TOMCAT_HOME}/logs/bag_database.log` to find the automatically-generated administrator password.
6. Log in through the GUI and use the Maintenance panel to change the password.
7. Note that in order for video streaming to work, `ffmpeg` version 3 or higher must be available on the system path.
