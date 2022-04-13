---
layout: default
title: Without Authentication
parent: Docker
grand_parent: Installation
nav_order: 1
description: "Using docker-compose to start up a Bag Database without Authentication"
permalink: /installation/docker/without-authentication
---

# Without Authentication

If you're on an internal network and you trust the users who can access the server,
the simplest way to set up a Bag Database is so that authentication is not required.

Here's an example `docker-compose.yml` file; this one will run:
- The Bag Database
- A PostGIS database server
- A Docker-in-Docker service (necessary for running scripts)

Review the comments in the file for things you may wish to customize.

```yaml
version: '3.6'
services:
    docker:
        image: docker:dind
        privileged: yes
        networks:
            - bagdb
        volumes:
            - bags:/bags:ro
            - scripts:/scripts
            - docker_cache:/var/lib/docker
        command: ["dockerd", "--host=tcp://0.0.0.0:2375"]
        # SSL is disabled since only the Bag Database can access this anyway.
        # Be careful about allowing anything else to access this service!  
    bagdb:
        image: ghcr.io/swri-robotics/bag-database/bag-database:latest
        networks:
            - bagdb
        depends_on:
            - postgres
        ports:
            - "8080:8080"
        volumes:
            - bags:/bags
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
        environment:
            ADMIN_PASSWORD: "letmein"  # Change this to something more secure
            DB_DRIVER: org.postgresql.Driver
            DB_PASS: letmein  # Should match POSTGRES_PASSWORD below
            DB_URL: "jdbc:postgresql://postgres/bag_database"  # Should reference POSTGRES_DB below
            DB_USER: bag_database  # Should match POSTGRES_USER below
            DOCKER_HOST: "http://docker:2375"
            GPS_TOPICS: "/localization/gps, gps, /vehicle/gps/fix, /localization/sensors/gps/novatel/raw, /localization/sensors/gps/novatel/fix, /imu_3dm_node/gps/fix, /local_xy_origin"  # Add topics where you publish GPS coordinates
            METADATA_TOPICS: "/metadata"
            VEHICLE_NAME_TOPICS: "/vms/vehicle_name, /vehicle_name"  # Replace with a topic on which you publish your vehicle's name
    postgres:
        image: postgis/postgis:11-2.5
        networks:
            - bagdb
        volumes:
            - postgres:/var/lib/postgresql/data
        ports:
            - "5432:5432"
            # This port is exposed to make it easy for you to connect to the database with a
            # SQL client to perform operations on it.   If you don't need to do so, this
            # port does not need to be exposed.
        environment:
            POSTGRES_PASSWORD: letmein # If you do expose it, it's a good idea to change this password to something more secure.
            POSTGRES_USER: bag_database
            POSTGRES_DB: bag_database
networks:
    bagdb: {}
volumes:
    bags:
        driver: local
        driver_opts:
            type: 'none'
            o: 'bind'
            device: '/var/local/bags'  # Replace this with the path to your bags
    docker_cache:
    postgres:
    ldap:
    slapd:
    indexes:
    scripts:
        driver_opts:
            type: 'tmpfs'
            device: 'tmpfs'
```

First, note that with the way these containers are configured, the directory
containing your bags must exist before starting everything up.  The example uses
`/var/local/bags`.

Save that `docker-compose.yml` file to a directory, cd to that directory, and run:

```bash
docker-compose up
```

After everything has started, you should be able to access your server at http://server_ip:8080/ .
