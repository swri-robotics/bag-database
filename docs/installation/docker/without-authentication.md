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

If you are on an internal network and you trust the users who can access the server, the simplest way to set up a Bag Database is so that authentication is not required using [Docker compose](https://docs.docker.com/compose/). This method will pull the latest images of the software from the GitHub Docker registry (ghcr.io).

Here's an example [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/no_authentication/docker-compose.yml) file; this one will run:
- The Bag Database
- A PostGIS database server
- A Docker-in-Docker service (necessary for running scripts)

Review the comments in the file [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/no_authentication/docker-compose.yml) for things you may wish to customize. 

Note that with the way these containers are configured, the directory containing your bags must exist before starting everything up.  The example template assumes these bags are stored in ```${HOME}/public_html/bags```.

To start the bag database, save [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/no_authentication/docker-compose.yml) to a directory, ```cd``` to that directory, and run:

```bash
docker-compose up
```

After everything has started, you should be able to access your server at http://server_ip:8080/ .
