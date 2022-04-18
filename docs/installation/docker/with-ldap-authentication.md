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

See [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/ldap_authentication/docker-compose.yml) for an example of how to run the bag database with LDAP authentication. This example is similar to [Without Authentication](without-authentication), but it also starts an OpenLDAP server and configures the Bag Database to use it.  In this case, users must have accounts in the LDAP server and will be prompted to log in when they first access the Bag Database. Check [LDAP Configuration](../../configuration/ldap) for examples of how to configure an LDAP server.
