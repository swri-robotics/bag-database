---
layout: default
title: Behind a Reverse Proxy
parent: Docker
grand_parent: Installation
nav_order: 5
description: "Running the Bag Database behind a Reverse HTTP Proxy"
permalink: /installation/docker/behind-a-reverse-proxy
---

# Reverse Proxy

If you want to require HTTPS to access the Bag Database, or if you want to run it on a server with multiple other web pages, the easiest way to do so is to run it behind a reverse proxy. An example Docker compose file for this configuration is located [here](https://github.com/swri-robotics/bag-database/blob/master/docker/reverse_proxy/docker-compose.yml). 

This Compose file is similar to the one in the [Without Authentication](without-authentication) example, but it uses [traefik](https://docs.traefik.io/) as a reverse proxy to serve the Bag Database at http://localhost/bagdb. Consult [traefik's HTTP and SSL documentation](https://docs.traefik.io/https/overview/) for more information about setting up encryption.

If you intend to have a reverse proxy that serves multiple servers behind different subdirectories, you should make sure to set the Bag DB's `BAGDB_PATH` environment variable to match the path you intend to serve it from; see the [Docker](docker) documentation. The linked example serves it from the path `bagdb`.
