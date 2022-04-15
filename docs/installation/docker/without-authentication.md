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

Here's an example [docker-compose.yml](../../../docker/docker-compose.yml) file; this one will run:
- The Bag Database
- A PostGIS database server
- A Docker-in-Docker service (necessary for running scripts)

Review the comments in the file [docker-compose.yml](../../../docker/docker-compose.yml) for things you may wish to customize. 

First, note that with the way these containers are configured, the directory containing your bags must exist before starting everything up.  The example template assumes these bags are stored in ```/bags``` on the host computer in both the ```docker``` service and the ```bagdb``` service. Both paths in these services must be the same, and should be changed to the directory containing your bag files. 

Second, in the ```volumes``` section, you may wish to modify the ```device``` path so that it points to a different location for the web server files.

To run this, save [docker-compose.yml](../../../docker/docker-compose.yml) to a directory, ```cd``` to that directory, and run:

```bash
docker-compose up
```

After everything has started, you should be able to access your server at http://server_ip:8080/ .
