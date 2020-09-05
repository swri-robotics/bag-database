---
layout: default
title: Tomcat
parent: Installation
nav_order: 3
description: "Running the Bag Database as a Tomcat webapp"
permalink: /installation/tomcat
---

# Running in a Tomcat Server

## Building

The bag database is a Spring-based web application that requires Java 11 or later
and has been tested in Tomcat 9.

To compile it, you need JDK 11.0 or later and Maven 3.0.5 or later.  To build a
WAR package, run:

`mvn package`

This will produce a WAR file that is suitable for deploying to a Tomcat 9 application
server.

## Tomcat Configuration

1. Start up a PostgreSQL server; create an empty database and a user with access to it.
2. Start up your application server and deploy the WAR file to it.  The easiest way
to do this is to copy the WAR file into Tomcat's `webapps` directory.
3. The bag database will automatically create a directory at `${HOME}/.ros-bag-database`
and place its configuration inside there, where `${HOME}` is the home directory of
the user that the `tomcat` service is running as.
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
