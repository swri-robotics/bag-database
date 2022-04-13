---
layout: default
title: With LDAP Authentication
parent: Docker
grand_parent: Installation
nav_order: 4
description: "Using docker-compose to start up a Bag Database with an LDAP server"
permalink: /installation/docker/with-ldap-authentication
---

# With LDAP Authentication

This example is similar to [Without Authentication](without-authentication), but it also
starts an OpenLDAP server and configures the Bag Database to use it.  In this case,
users must have accounts in the LDAP server and will be prompted to log in when they first
access the Bag Database.

```yaml
version: '3.6'
services:
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
        image: ghcr.io/swri-robotics/bag-database/bag-database:latest
        networks:
            - bagdb
        depends_on:
            - postgres
            - openldap
        ports:
            - "8080:8080"
        volumes:
            - bags:/bags # Replace this with the path to your bag files
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
        environment:
            ADMIN_PASSWORD: "letmein"
            DB_DRIVER: org.postgresql.Driver
            DB_PASS: letmein  # Should match POSTGRES_PASSWORD below
            DB_URL: "jdbc:postgresql://postgres/bag_database"  # Should reference POSTGRES_DB below
            DB_USER: bag_database  # Should match POSTGRES_USER below
            DOCKER_HOST: "http://docker:2375"
            GPS_TOPICS: "/localization/gps, gps, /vehicle/gps/fix, /localization/sensors/gps/novatel/raw, /localization/sensors/gps/novatel/fix, /imu_3dm_node/gps/fix, /local_xy_origin"
            LDAP_BINDDN: "cn=admin,dc=example,dc=com" # Replace this with the admin DN for your LDAP server
            LDAP_BIND_PASSWORD: "P@ssw0rd" # Replace this with the password for your admin DN
            LDAP_SEARCH_BASE: "ou=People,dc=example,dc=com" # Replace this with the search base for your LDAP server
            LDAP_SERVER: 'ldap://openldap'
            LDAP_USER_PATTERN: "uid={0},ou=People,dc=example,dc=com" # Replace this with the user pattern for your LDAP server 
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
            # This port is exposed to make it easy for you to connect to the database with a
            # SQL client to perform operations on it.   If you don't need to do so, this
            # port does not need to be exposed.
        environment:
            POSTGRES_PASSWORD: letmein  # Change this to something more secure if you leave that port exposed
            POSTGRES_USER: bag_database
            POSTGRES_DB: bag_database
    openldap:
        image: osixia/openldap:1.3.0
        networks:
            - bagdb
        volumes:
            - ldap:/var/lib/ldap
            - slapd:/etc/ldap/slapd.d
        ports:
            - "389:389"
            - "636:636"
            # Similarly, these ports are exposed so you can easily connect to
            # the container to add data.  Feel free to not expose them if you
            # don't need to do so.
        environment:
            LDAP_ORGANIZATION: "Example Organization"
            LDAP_DOMAIN: "example.com"
            LDAP_ADMIN_PASSWORD: "P@ssw0rd"
            # Change all of these values to something more appropriate
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

The Bag Database can be started using this `docker-compose.yml` file in the same matter as
the one in [Without Authentication](without-authentication), but you will need to add users to
your LDAP server.  Check [LDAP Configuration](../../configuration/ldap) for examples of how
to do that.
