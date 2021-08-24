---
layout: default
title: Storage Configuration
parent: Configuration
nav_order: 3
description: "Configuring Storage"
permalink: /configuration/storage
---

# Storage

For scenarios where you just want the Bag Database to index bag files in a single directory on
the local filesystem, see [Configuration](configuration.md).

The Bag Database can also be configured to index files in multiple local directories or S3-compatible
storage buckets, in which case you will need to create a custom `settings.yml` file.  This file
should be mounted at `${HOME}/.ros-bag-database/settings.yml` inside the Docker container, or outside
the container in the Tomcat user's home directory if running as a standalone Tomcat server.

Storage backends are configured by adding a `storageConfigurations` key to the `settings.yml` file
that contains a list of serialized YAML configuration objects.  Each of these objects maps to a
configuration class under the Bag Database's `com.github.swrirobotics.bags.storage` package.

If no `storageConfigurations` are present, the Bag Database will create a default
`FilesystemBagStorageImpl` backend with a `storageId` of `default` and a `basePath` of `/bags`.  This
is backwards-compatible with the behavior of versions of the Bag Database prior to 3.3.0.

Read through [Options](#options) for a list of storage backends and their configuration options, or
see [Example](#example) for an example `settings.yml` and `docker-compose.yml` that use advanced storage
options.

## Options

Every storage backend has a parameter named `storageId`.  This is a unique key identifying that backend
and is used to internally track which bag files are stored on that backend; after you have added a
backend, changing its `storageId` will cause the Bag Database to forget and re-index every bag file
in it.

### Local Filesystem

Indexes bags in a directory on the local filesystem.  In order to run scripts on these bags, their
directory must also be mounted inside a Docker-in-Docker container running alongside the Bag Database.

Configuration Class: `com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageConfigImpl`

| Field | Description | Default Value |
| ----- | ----------- | ------------- |
| `storageId` | Unique identifier for this backend | |
| `basePath` | Directory to search for bag files; in a Docker container, this will be the mount point inside the container | `/bags` |
| `isLocal` | Enables performance improvements for local filesystems; should always be true | `true` |
| `dockerPath` | The mount point where this directory is mounted inside the Docker-in-Docker container | `/bags` |

### S3-Compatible Storage

Indexes bags in an S3-compatible storage service.  The service will be polled at a specified interval
for new bags.  Keep in mind that the API calls and downloads from this service may incur charges.

Operations that can cause the Bag Database to download a bag include:
1. Indexing a new bag
2. Downloading a bag through the web interface
3. Viewing an image
4. Streaming a video topic
5. Running a script on a bag

Configuration Class: `com.github.swrirobotics.bags.storage.s3.S3BagStorageConfigImpl`

| Field | Description | Default Value |
| ----- | ----------- | ------------- |
| `storageId` | Unique identifier for this backend | |
| `accessKey` | Access key for this S3 bucket; *required* | |
| `secretKey` | Secret key for this S3 bucket; *required* | |
| `endPoint` | Connection endpoint for this S3 bucket; only required for non-Amazon services | |
| `bucket` | Name of the bucket to use for bag storage | |
| `region` | Region for this S3 bucket; only required for Amazon services | |
| `updateIntervalMs` | How often to check the bucket for new files, in milliseconds | `10000` |

## Example

Here's an example that will mount two local directories, `/home/user/bags` and `/home/user/bags`, and
an S3 bucket hosted at [Backblaze](https://www.backblaze.com/).

### `settings.yml`:

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
    - !com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageConfigImpl
        basePath: /bags
        isLocal: true
        dockerPath: /bags
        storageId: 'default'
    - !com.github.swrirobotics.bags.storage.filesystem.FilesystemBagStorageConfigImpl
        basePath: /bags2
        storageId: 'Local Storage 2'
        isLocal: true
        dockerPath: /bags2
    - !com.github.swrirobotics.bags.storage.s3.S3BagStorageConfigImpl
        storageId: 'Backblaze'
        accessKey: 'YOUR_ACCESS_KEY'
        secretKey: 'YOUR_SECRET_KEY'
        endPoint: 'YOUR_END_POINT'
        bucket: 'YOUR_BUCKET_NAME'
```

### `docker-compose.yml`:

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
            - bags2:/bags2:ro # Needs to match the path in the bagdb container
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
            - bags:/bags
            - bags2:/bags2
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
            - ./settings.yml:/root/.ros-bag-database/settings.yml:ro
    postgres:
        image: postgis/postgis:11-2.5
        networks:
            - bagdb
        volumes:
            - postgres:/var/lib/postgresql/data
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
            device: '/home/user/bags'  # Replace this with the path to your bag files
    bags2:
        driver: local
        driver_opts:
            type: 'none'
            o: 'bind'
            device: '/home/user2/bags2'  # Replace this with the path to your other bag files
    docker_cache:
    postgres:
    indexes:
    scripts:
        driver_opts:
            type: 'tmpfs'
            device: 'tmpfs'
```