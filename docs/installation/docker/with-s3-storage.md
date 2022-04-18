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
storage backend, rather than local storage.  Note that doing so requires more complex configuration than is allowed through environment variables, so we create a custom `settings.yml` file  that is copied into the Bag Database's container that is used to configure the storage.

To use this example, follow theese steps.

1. Copy the contents of [this configuration](https://github.com/swri-robotics/bag-database/blob/master/docker/s3/settings.yml) into a file named ```settings.yml```
2. Place [this Docker Compose configuration](https://github.com/swri-robotics/bag-database/blob/master/docker/s3/docker-compose.yml) into a file named ```docker-compose.yml``` in the same directory as your ```settings.yml``` file.
3. With both of those files in the same directory, run:
  ```bash
  docker-compose up
  ```

After everything has started, you should be able to access your server at http://server_ip:8080/ .
