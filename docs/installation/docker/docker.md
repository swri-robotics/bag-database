---
layout: default
title: Docker
parent: Installation
nav_order: 1
has_children: true
description: "Installing with Docker"
permalink: /installation/docker/
---

# Docker

The Bag Database supports running in multiple different configurations, and depending upon
your needs, you may want to also have PostgreSQL, OpenLDAP, and Docker containers.  The
easiest way to manage all of these is with [docker-compose](https://docs.docker.com/compose/).

Follow the official instructions for setting up docker-compose, then look at the examples
here to get an idea of how you want to set up your Bag Database.  This page describes
volumes and parameters that can be used to configure the Docker container.

## Configuration

### Volumes

| Volume | Description |
| ------ | ----------- |
| `/bags` | Will be monitored for bag files. |
| `/usr/local/tomcat/logs` | Where Tomcat places its log files. |
| `/root/.ros-bag-database/indexes` | Where the Bag Database stores its Elasticsearch indexes. |

### Environment Variables

With the exception of `BAGDB_PATH`, all of these variables are used in the Bag Database container's
`entrypoint.sh` script to generate a file at `${HOME}/.ros-bag-database/settings.yml` that configures
the Bag Database.  You can generate this file yourself if you have more complex configuration needs;
see [Storage](../../configuration/storage.md) for an example.

| Variable | Description | Default |
| -------- | ----------- | ------- |
| `ADMIN_PASSWORD` | The default password for administrative access.  If this is not set, one will be randomly generated and printed to the log file on initial startup. | |
| `BAGDB_PATH` | The subdirectory to serve the Bag DB from.  For example, if this is `bagdb`, you might access it from `http://localhost:8080/bagdb/`.  Leaving this empty (the default) will serve it from the root path. | |
| `BING_KEY` | The API key to use when connecting to Bing Maps.  You can get an API Key through the [Bing Maps Portal](https://www.bingmapsportal.com/). | |
| `DB_DRIVER` | The class name of the JDBC driver to use. | org.hsqldb.jdbcDriver |
| `DB_PASS` | The password to use when connecting to the database. | |
| `DB_URL` | The JDBC URL for connecting to the database. | jdbc:hsqldb:mem:testdb |
| `DB_USER` | The username to use when connecting to the database. | sa |
| `DEBUG_JAVASCRIPT` | Set this to `true` to force the application to load non-minified versions of Javascript files. This will increase load times. | false |
| `DOCKER_HOST` | The URL to use to connect to a Docker service.  This can be empty if you do not intend to run scripts on bag files. It must be able to mount volumes on the same paths that the Bag Database can access them. TCP URLs should begin with `http://`. | |
| `GOOGLE_API_KEY` | A Google API key that has permission to use the Google Maps GeoCoding API; this is necessary in order to resolve place names for GPS coordinates.  You can get an API key in the [Google Maps Platform](https://developers.google.com/maps/documentation/geocoding/get-api-key) documentation. | |
| `GPS_TOPICS` | A comma-separated list of topics to search for GPS messages; the first one found will be used.  Any message that has the fields `float64 latitude`, `float64 longitude`, and `Header header` will work.  If there are no topics configured or none of them are found, it will try to use the first topic it can find that publishes the `sensor_msgs/NavSatFix`, `gps_common/GPSFix`, or `marti_gps_common/GPSFix` messages, in that order. | |
| `LDAP_BINDDN` | If authenticating against an LDAP server that requires authentication, the Bind DN.  If this is left blank, it will not attempt to authenticate. | |
| `LDAP_BIND_PASSWORD` | If authenticating against an LDAP server that requires authentication, the password for the Bind DN. | |
| `LDAP_SEARCH_BASE` | If authenticating against an LDAP server that requires authentication, the password for the Bind DN. | |
| `LDAP_SERVER` | The LDAP server for authentication.  If set to an empty string, LDAP authentication will not be enabled, and anonymous users may connect. | |
| `LDAP_USER_PATTERN` | The pattern for finding user DNs in the LDAP server.  `{0}` will be replaced with the username from the login form. | |
| `METADATA_TOPICS` | A comma-separated list of `std_msgs/String` topics in bag files that will be searched for metadata.  The messages on the topic should be newline-separated tags that are made of colon-separated key/value pairs; see [Metadata Example](#metadata-example) for an example. | |
| `SCRIPT_TMP_PATH` | Path to write temporary script files and bag files downloaded from remote storage backends.  This can be empty if you do not intend to run scripts or use remote storage. It must be writable by the bag database, and the Docker service that runs the scripts **must have it mounted as a volume at the same location as the Bag Database**. | /scripts | 
| `TILE_HEIGHT_PX` | The height of the tiles returned from the tile map in pixels. | 256 |
| `TILE_MAP_URL` | If `USE_TILE_MAP` is `true`, this URL will be used as a template for retrieving map tiles from a WMTS tile server.  See the documentation for the `url` property of OpenLayers' [ol.source.XYZ](http://openlayers.org/en/latest/apidoc/ol.source.XYZ.html) class.  The default value will use the terrain map provided by [Stamen](http://maps.stamen.com/). | http://{a-d}.tile.stamen.com/terrain/{z}/{x}/{y}.jpg |
| `TILE_WIDTH_PX` | The width of the tiles returned from the tile map in pixels. | 256 |
| `USE_BING` | Set this to `true` to use Bing Maps for displaying map imagery; set it to `false` to disable Bing. | false |
| `USE_TILE_MAP` | Set this to `true` to use a WMTS tile map for displaying map imagery; set it to `false` to disable WMTS maps. | true |
| `VEHICLE_NAME_TOPICS` | A comma-separated list of `std_msg/String` topics that will be searched for a vehicle name; the first one found will be used. | |

#### Metadata Example

An example message on the `/metadata` topic might look like:

```
name: John Doe
email: jdoe@example.com
```

This would add two tags: one with the name `name` and value `John Doe`, and
another with the name `email` and value `jdoe@example.com`.

Tag names are unique.  If a topic has multiple messages and there are any tags with duplicate names, later ones
will overwrite earlier ones.
