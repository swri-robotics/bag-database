---
layout: default
title: With Webviz
parent: Docker
grand_parent: Installation
nav_order: 7
description: "Running the Bag Database with a Self-Hosted Webviz Instance behind a Reverse Proxy"
permalink: /installation/docker/with-webviz
---

# With a Self-Hosted Webviz Instance Behind a Reverse Proxy

This is a complex example that incorporates all the smaller subcomponents in the other examples. This is useful for production environments that require the full functionality of the system.

This Bag Database will:
- Use LDAP for authentication
- Run the Bag Database and [Webviz](https://webviz.io/) behind a [Traefik](https://traefik.io/) reverse proxy
- Use Traefik to enforce SSL on all connections
- Automatically generate a signed SSL certificate for `bagdb.example.com` through [Let's Encrypt](https://letsencrypt.org/)
- Use the self-hosted Webviz instance for opening bag files

To make the docker-compose.yml file a bit cleaner, environment variables for many of the containers have been pulled out into separate files. These configuration files, and the associated Docker compose file, are located [here](../../../docker/webviz/). All of these files should be saved in the same directory, and if you are going to run a server based on them, make sure you edit them to add your own passwords, domain name, and user information.

## Configuration Files

- [bagdb.env](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/bagdb.env)
  - Environment variables for the Bag Database, such as importantant ROS topics, server connections, etc.
- [openldap.env](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/openldap.env)
   -  Environment variables for the LDAP server.
- [postgres.env](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/postgres.env)
  - Environment variables for the PostGIS server.
- [webviz-default.conf](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/webviz-default.conf)
  - A custom configuration file for the Webviz's nginx server.
  - Change the ```location``` variable here because it will be running under an alias at ```/webviz``` in our reverse proxy.
- [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/docker-compose.yml)
  - Main docker-compose.yml file. If everything is configured correctly, the system can be started with ```docker-compose up -d```
  - After everything is running, you will be able to access the server at `https://bagdb.example.com`.
- [people.ldif](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/people.ldif)
  - An example LDIF file for creating a "People" group in your LDAP server.
  - After starting the server, run
    
    ```ldapadd -x -D cn=admin,dc=example,dc=com -W -f people.ldif```

    to add this group.
- [user.ldif](https://github.com/swri-robotics/bag-database/blob/master/docker/webviz/user.ldif)
  - An example LDIF file that defines a single user.
  - Customize this for each user
  - After you've added the People group, run
  
    ```ldapadd -x -D cn=admin,dc=example,dc=com -W -f user.ldif```

    to add this person to the server.
