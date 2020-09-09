---
layout: default
title: Behind a Reverse Proxy
parent: Docker
grand_parent: Installation
nav_order: 4
description: "Running the Bag Database behind a Reverse HTTP Proxy"
permalink: /installation/docker/behind-a-reverse-proxy
---

# Reverse Proxy

If you want to require HTTPS to access the Bag Database, or if you want to run it on
a server with multiple other web pages, the easiest way to do so is to run it behind
a reverse proxy.

This Dockerfile is similar to the one in the
[Without Authentication](without-authentication) example, but it
uses [traefik](https://docs.traefik.io/) as a reverse proxy to serve the Bag Database
at http://bagdb.docker.localhost/.  Consult [traefik's HTTP and SSL documentation](https://docs.traefik.io/https/overview/)
for more information about setting up encryption.

```yaml
version: '3.6'
services:
    reverse-proxy:
        image: traefik:v2.2
        command: --api.insecure=true --providers.docker
        networks:
            - bagdb
        ports:
            - "80:80"  # Port to expose services
            - "8080:8080"  # Traefik's management API
        volumes:
            - /var/run/docker.sock:/var/run/docker.sock
    docker:
        image: docker:dind
        privileged: yes
        networks:
            - bagdb
        volumes:
            - bags:/bags:ro # Needs to match the path in the bagdb container
            - scripts:/scripts
            - docker_cache:/var/lib/docker
        command: ["dockerd", "--host=tcp://0.0.0.0:2375"]
    bagdb:
        image: swri-robotics/bag-database:latest
        networks:
            - bagdb
        depends_on:
            - postgres
        labels:
            # This label tells traefik to direct all traffic for "bagdb.docker.localhost"
            # to this container.  Change the hostname to whatever is appropriate.
            - "traefik.http.routers.bagdb.rule=Host(`bagdb.docker.localhost`)"
        volumes:
            - bags:/bags # Replace this with the path to your bag files
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
        environment:
            ADMIN_PASSWORD: "letmein"
            DB_DRIVER: org.postgresql.Driver
            DB_PASS: letmein
            DB_URL: "jdbc:postgresql://postgres/bag_database"
            DB_USER: bag_database
            DOCKER_HOST: "http://docker:2375"
            GPS_TOPICS: "/localization/gps, gps, /vehicle/gps/fix, /localization/sensors/gps/novatel/raw, /localization/sensors/gps/novatel/fix, /imu_3dm_node/gps/fix, /local_xy_origin"
            METADATA_TOPICS: "/metadata"
            VEHICLE_NAME_TOPICS: "/vms/vehicle_name, /vehicle_name"
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
            device: '/home/preed/public_html/bags'
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
