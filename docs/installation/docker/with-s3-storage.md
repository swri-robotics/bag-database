---
layout: default
title: With S3 Storage
parent: Docker
grand_parent: Installation
nav_order: 6
description: "Using docker-compose to start up a Bag Database that stores files in an S3 bucket"
permalink: /installation/docker/with-s3-storage
---

# With S3 Storage

This is similar to [Without Authentication](without-authentication.md), except it uses an S3-compatible
storage backend.  Note that doing so requires more complex configuration than is allowed through
environment variables, so we create a custom `settings.yml` file and map that inside the Bag Database's
container.

Put this in a file named `settings.yml`:

```yaml
!com.github.swrirobotics.support.web.Configuration
dockerHost: http://docker:2375
driver: org.postgresql.Driver
gpsTopics:
    - /localization/gps
    - gps
    - /vehicle/gps/fix
    - /localization/sensors/gps/novatel/raw
    - /localization/sensors/gps/novatel/fix
    - /imu_3dm_node/gps/fix
    - /local_xy_origin
jdbcPassword: letmein
jdbcUrl: jdbc:postgresql://postgres/bag_database
jdbcUsername: bag_database
metadataTopics:
    - /metadata
vehicleNameTopics:
    - /vms/vehicle_name
    - /vehicle_name
storageConfigurations:
    - !com.github.swrirobotics.bags.storage.s3.S3BagStorageConfigImpl
        storageId: 'YOUR_STORAGE_NAME'  # Replace with a friendly name to identify your storage backend
        accessKey: 'YOUR_ACCESS_KEY'  # Replace with your access key
        secretKey: 'YOUR_SECRET_KEY'  # Replace with your secret key
        endPoint: 'YOUR_END_POINT'  # Replace with your endpoint if not using Amazon; otherwise delete this line
        region: 'YOUR_REGION_HERE'  # Replace with your region if using Amazon; otherwise delete this line
        bucket: 'YOUR_BUCKET_NAME'  # Replace with your bucket name
```

Then put this in `docker-compose.yml`:

```yaml
version: '3.6'
services:
    docker:
        image: docker:dind
        privileged: yes
        networks:
            - bagdb
        volumes:
            - scripts:/scripts
            - docker_cache:/var/lib/docker
        command: ["dockerd", "--host=tcp://0.0.0.0:2375"]
    bagdb:
        build: .
        networks:
            - bagdb
        depends_on:
            - postgres
        ports:
            - "8080:8080"
        volumes:
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
            - ./settings.yml:/root/.ros-bag-database/settings.yml:ro  # Note that this is read-only so the entrypoint script won't overwrite it!
    postgres:
        image: postgis/postgis:11-2.5
        networks:
            - bagdb
        volumes:
            - postgres:/var/lib/postgresql/data
        environment:
            POSTGRES_PASSWORD: letmein  # Consider replacing with a more secure password; update settings.yml if you do
            POSTGRES_USER: bag_database
            POSTGRES_DB: bag_database
networks:
    bagdb: {}
volumes:
    docker_cache:
    postgres:
    indexes:
    scripts:
        driver_opts:
            type: 'tmpfs'
            device: 'tmpfs'
```

With both of those files in the same directory, run:

```bash
docker-compose up
```

After everything has started, you should be able to access your server at http://server_ip:8080/ .
