---
layout: default
title: Without Compose
parent: Docker
grand_parent: Installation
nav_order: 3
description: "How to run it without docker-compose"
permalink: /installation/docker/without-compose
---

# Without docker-compose

If you plan to manage the other services the Bag Database needs on its own, you
can just start it up using the normal docker command like so:

```bash
docker run -d \
    -p 8080:8080 \
    -v /bag/location:/bags \
    --name bagdb \
    --net bagdb \
    -e DB_DRIVER=org.postgresql.Driver \
    -e DB_PASS=letmein \
    -e DB_URL="jdbc:postgresql://bagdb-postgres/bag_database" \
    -e DB_USER=bag_database \
    -e METADATA_TOPICS="/metadata" \
    -e VEHICLE_NAME_TOPICS="/vehicle_name" \
    -e GPS_TOPICS="/localization/gps, /gps, /imu/fix" \
    swrirobotics/bag-database:latest
```

After the bag database has successfully started, the bag database should be
available at `http://127.0.0.1:8080`.  Modify your Docker parameters as to change its
configuration as necessary.
