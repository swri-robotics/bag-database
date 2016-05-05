# Bag Database   [![Build Status](https://travis-ci.org/swri-robotics/bag-database.svg?branch=master)](https://travis-ci.org/swri-robotics/bag-database)

The Bag Database is a web-based application that monitors a directory for ROS bag 
files, parses their metadata, and provides a friendly web interface for searching 
for bags and downloading them.  Its goal is to make it easy to catalog thousands
of bag files, search through them for relevant data such as topic names and message
types, view information about them, and download them.

Currently it is intended primarily for internal use.  Any user can edit metadata
for bag files, and it is expected that users have access to the location where bags
are stored and will be manually uploading files there.

![Sample Screenshot](doc/bag-database.png)

## Features

- **Directory Monitoring**: It will watch a directory for any changes and
  automatically scan any bag files placed in that directory.
- **Full-text Searching**: The Search field will search the text fields selected by the user in every
  bag file for any strings containing the provided text; it can search:
  - The bag's file name
  - The path leading to the bag on the filesystem
  - A user-provided description of the bag
  - The name of the physical location where the bag was recorded
    - (Either this must be manually entered or support for Google's reverse 
      geocoding API must be enabled)
  - The name of the vehicle
  - The types of any messages used
  - The topics published
- **Filtering**: Every column in the grid can by filtered by arbitrary values;
  click the down arrow on a column header to set a filter.
- **Displaying Metadata**: All of the same information you could obtain with 
  `rosbag info` is extracted and stored so that it can be easily viewed for any
  bag file.
- **Message Types and Topics**: You can also easily view all of the different 
  message types and topics used in the bag file.
- **Displaying GPS Coordinates**: GPS coordinates recorded in a bag file are
  extracted and stored, and if support for Bing Maps or MapQuest is enabled you
  can view the vehicle's path on a map.
  - The following message types are supported:
    - `gps_common/GPSFix`
    - `sensor_msgs/NavSatFix`
    - `marti_gps_common/GPSFix`
  - It will attempt to find those message on the following topics, in order of priority:
    - `/localization/gps`
    - `gps`
    - `/vehicle/gps/fix`
    - `/localization/sensors/gps/novatel/raw`
    - `/localization/sensors/gps/novatel/fix`
    - `/imu_3dm_node/gps/fix`
    - `/local_xy_origin`
- **Downloading**: Every bag file can be downloaded from the interface without
  needing to find it on the host filesystem.

## Compiling

The bag database is a Spring-based web application that requires Java 8.0 or later.
To compile it, you need JDK 8.0 or later and Maven 3.0.5 or later.  To build a
WAR package, run:

`mvn package`

The preferred mechanism for running the bag database is as a 
[Docker](https://www.docker.com/) container.  To build the docker image, run:

`mvn package && sudo mvn docker:build`

## Running

### Docker (Preferred method)

The bag database can run standalone in order to demonstrate its functionality, but
if you do so it will have to rebuild the database every time it restarts.  Instead
you should link it to an external database.  MySQL and PostgreSQL are supported;
PostgreSQL is preferred.

To start a PostgreSQL container with PostGIS support:
```
docker run -d \
    --name bagdb-postgres \
    -e POSTGRES_PASSWORD=letmein \
    -e POSTGRES_USER=bag_database \
    -e POSTGRES_DB=bag_database \
    mdillon/postgis:latest
```

The bag database exposes port 8080 and expects to find bag files in a volume at /bags by default.  You can run it like so:
```
docker run -d \
    -p 8080:8080 \
    -v /bag/location:/bags \
    --name bagdb \
    --link bagdb-postgres:bagdb-postgres \
    -e DB_DRIVER=org.postgresql.Driver \
    -e DB_PASS=letmein \
    -e DB_URL="jdbc:postgresql://bagdb-postgres/bag_database" \
    -e DB_USER=bag_database \
    swrirobotics/bag-database:latest
```

After the bag database has successfully started, the bag database should be available at `http://127.0.0.1:8080`.  Modify your Docker parameters as desired to expose it on a different port or set up [HAProxy](https://hub.docker.com/_/haproxy/) if you want to enable SSL or have it accessible via a subdirectory.

#### Volumes

Several volumes within the Docker container may be useful to mount externally:

##### `/bags`

The location which will be monitored for bag files.

##### `/root/.ros-bag-database/indexes`

The location in which the bag database stores its Lucene database indexes.

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

##### GOOGLE_API_KEY

A Google API key that has permission to use the Google Maps GeoCoding API; this is necessary in order to resolve place names for GPS coordinates.

##### USE_MAPQUEST

Set this to `true` to use MapQuest for displaying map imagery; set it to `false` to disable MapQuest.  The default is `true`.

##### USE_BING

Set this to `true` to use Bing Maps for displaying map imagery; set it to `false` to disable Bing.  The default is `false`.

##### BING_KEY

The API key to use when connecting to Bing Maps.

### As An Application Server Servlet

Only Tomcat 8.0 with Java 8.0 has been tested.

1. Start up your application server and deploy the WAR file to it.
2. The bag database will automatically create a directory at `${HOME}/.ros-bag-database` and place its configuration inside there.
3. Edit `${HOME}/.ros-bag-database/settings.yml` and set your configuration, then restart the application.
4. Look inside the log file at `${TOMCAT_HOME}/logs/bag_database.log` to find the automatically-generated administrator password.
5. Log in through the GUI and use the Maintenance panel to change the password.
