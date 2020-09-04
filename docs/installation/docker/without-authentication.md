---
# Feel free to add content and custom Front Matter to this file.
# To modify the layout, see https://jekyllrb.com/docs/themes/#overriding-theme-defaults

layout: default
title: Without Authentication
parent: Docker
grand_parent: Installation
nav_order: 1
description: "Using docker-compose to start up a Bag Database without Authentication"
permalink: /installation/docker/without-authentication
---

## docker-compose without Authentication

If you're on an internal network and you trust the users who can access the server,
the simplest way to set up a Bag Database is so that authentication is not required.

Here's an example `docker-compose.yml` file that will start up everything:

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
    bagdb:
        image: swri-robotics/bag-database:latest
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
            DB_PASS: letmein
            DB_URL: "jdbc:postgresql://postgres/bag_database"
            DB_USER: bag_database
            DOCKER_HOST: "http://docker:2375"
            USE_BING: "false"
            USE_TILE_MAP: "true"
            METADATA_TOPICS: "/metadata"
            VEHICLE_NAME_TOPICS: "/vms/vehicle_name, /vehicle_name"  # Replace with a topic on which you publish your vehicle's name
            GPS_TOPICS: "/localization/gps, gps, /vehicle/gps/fix, /localization/sensors/gps/novatel/raw, /localization/sensors/gps/novatel/fix, /imu_3dm_node/gps/fix, /local_xy_origin"  # Add topics where you publish GPS coordinates
            LDAP_BINDDN: ""
            LDAP_BIND_PASSWORD: ""
            LDAP_SEARCH_BASE: ""
            LDAP_SERVER: ""
            LDAP_USER_PATTERN: ""
    postgres:
        image: postgis/postgis:11-2.5
        networks:
            - bagdb
        volumes:
            - postgres:/var/lib/postgresql/data
        ports:
            - "5432:5432"
        environment:
            POSTGRES_PASSWORD: letmein
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
            device: '/var/local/bags'  # Replace this with the bath to your bags
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

Save that file to a directory, cd to that directory, and run:

```bash
docker-compose up
```

After everything has started, you should be able to access your server at http://server_ip:8080/ .