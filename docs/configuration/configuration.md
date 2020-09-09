---
layout: default
title: Configuration
nav_order: 3
has_children: true
description: "Configuring the Bag Database"
permalink: /configuration/
---

# Configuration

The Bag Database stores its configuration in a file at `${HOME}/.ros-bag-database/settings.yml`,
where `${HOME}` is the home directory of the user that the Tomcat server is running as.  If you
are running the Bag Database a Docker container, you don't need to edit this file; it will be
automatically generated from the environment variables set on the container.

For a detailed list of the environment variables used to configure the Bag Database when it is
running as a Docker container, see [Docker](../installation/docker).

## File Format

For reference, this is what a normal `settings.yml` file looks like:
```yaml
!com.github.swrirobotics.support.web.Configuration
bagPath: /var/local/bags
dockerHost: http://localhost:2375
driver: org.postgresql.Driver
gpsTopics: 
- /localization/gps
- gps
- /vehicle/gps/fix
- /localization/sensors/gps/novatel/raw
- /localization/sensors/gps/novatel/fix
- /imu_3dm_node/gps/fix
- /local_xy_origin
scriptTmpPath: "/var/lib/tomcat9/bagdb_scripts/"
jdbcPassword: letmein
jdbcUrl: jdbc:postgresql://localhost/bag_database
jdbcUsername: bag_database
ldapBindDn: cn=admin,dc=example,dc=com
ldapBindPassword: P@ssw0rd
ldapSearchBase: ou=People,dc=example,dc=com
ldapServer: 
ldapUserPattern: uid={0},ou=People,dc=example,dc=com
metadataTopics: 
- /metadata
vehicleNameTopics: 
- /vms/vehicle_name
- /vehicle_name
```

Again, you should not edit this if you are running the Bag Database as a Docker container.  Any
changes will be overwritten the next time the container restarts.

On the other hand, if you are running the Bag Database in a standalone Tomcat server, you may want
to edit these; you can also edit them through the [Configuration](../web-interface/administration#bag-database-configuration)
panel.
