---
layout: default
title: With Webviz
parent: Docker
grand_parent: Installation
nav_order: 7
description: "Running the Bag Database with a Self-Hosted Webviz Instance behind a Reverse Proxy"
permalink: /installation/docker/with-webviz
---

# With a Self-Hosted Webviz Instance behind a Reverse Proxy

This is a fairly complex example that resembles something more like what you might use
in an actual production environment.  It combines quite a bit from the other, simpler examples.

This Bag Database will:
- Use LDAP for authentication
- Run the Bag Database and [Webviz](https://webviz.io/) behind a [Traefik](https://traefik.io/) reverse proxy
- Use Traefik to enforce SSL on all connections
- Automatically generate a signed SSL certificate for `bagdb.example.com` through [Let's Encrypt](https://letsencrypt.org/)
- Use the self-hosted Webviz instance for opening bag files

To make the docker-compose.yml file a bit cleaner, environment variables for many of the containers
have been pulled out into separate files.  All of these files should be saved in the same directory,
and if you're going to run a server based on them, make sure you edit them to add your own passwords,
domain name, and user information.

## Files

### `bagdb.env`

Environment variable for the Bag Database.

```
ADMIN_PASSWORD=random_bagdb_password
DB_DRIVER=org.postgresql.Driver
DB_PASS=random_postgres_password
DB_URL=jdbc:postgresql://postgres/bag_database
DB_USER=bag_database
DOCKER_HOST=http://docker:2375
GPS_TOPICS=/localization/gps, gps, /vehicle/gps/fix, /localization/sensors/gps/novatel/raw, /localization/sensors/gps/novatel/fix, /imu_3dm_node/gps/fix, /local_xy_origin
LDAP_BINDDN=cn=admin,dc=example,dc=com
LDAP_BIND_PASSWORD=random_ldap_password
LDAP_SEARCH_BASE=ou=People,dc=example,dc=com
LDAP_SERVER=ldap://openldap
LDAP_USER_PATTERN=uid={0},ou=People,dc=example,dc=com
METADATA_TOPICS=/metadata
OPEN_WITH_URLS={'Webviz':['https://bagdb.example.com/webviz/?', 'remote-bag-url']}
VEHICLE_NAME_TOPICS=/vms/vehicle_name, /vehicle_name
```

### `openldap.env`

Environment variables for the LDAP server.

```
LDAP_ORGANIZATION=My Company
LDAP_DOMAIN=example.com
LDAP_ADMIN_PASSWORD=random_ldap_password
```

### `postgres.env`

Environment variables for the PostGIS server.

```
POSTGRES_PASSWORD=random_postgres_password
POSTGRES_USER=bag_database
POSTGRES_DB=bag_database
```

### `webviz-default.conf`

A custom configuration file for Webviz's nginx server.  We need to change the `location` here
because it will be running under an alias at `/webviz` in our reverse proxy.

```
server {
    listen 8080;
    server_name  localhost;

    #access_log  /var/log/nginx/host.access.log  main;

    location /webviz {
        alias  /usr/share/nginx/html;
        index  index.html index.htm;
    }

    #error_page  404              /404.html;

    # redirect server error pages to the static page /50x.html
    #
    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   /usr/share/nginx/html;
    }

    # proxy the PHP scripts to Apache listening on 127.0.0.1:80
    #
    #location ~ \.php$ {
    #    proxy_pass   http://127.0.0.1;
    #}

    # pass the PHP scripts to FastCGI server listening on 127.0.0.1:9000
    #
    #location ~ \.php$ {
    #    root           html;
    #    fastcgi_pass   127.0.0.1:9000;
    #    fastcgi_index  index.php;
    #    fastcgi_param  SCRIPT_FILENAME  /scripts$fastcgi_script_name;
    #    include        fastcgi_params;
    #}

    # deny access to .htaccess files, if Apache's document root
    # concurs with nginx's one
    #
    #location ~ /\.ht {
    #    deny  all;
    #}
}
```

### `docker-compose.yml`

The main docker-compose.yml file.  If everything is in place, you can start the server with
`docker-compose up -d`.

After everything is running, you will be able to access the server at `https://bagdb.example.com`.

```yaml
version: '3.6'
services:
    traefik:
        image: traefik:v2.4
        command:
            - --api.insecure=true
            - --providers.docker
            - --entrypoints.web.address=:80
            - --entrypoints.websecure.address=:443
            - --entrypoints.web.http.redirections.entrypoint.to=websecure
            - --entrypoints.web.http.redirections.entrypoint.scheme=https
            - --certificatesresolvers.bagdbresolver.acme.email=youremail@example.com
            - --certificatesresolvers.bagdbresolver.acme.storage=/etc/traefik/acme/acme.json
            - --certificatesresolvers.bagdbresolver.acme.httpchallenge.entrypoint=web
        networks:
            - bagdb
        ports:
            - "80:80"
            - "443:443"
            - "8080:8080"
        volumes:
            - /var/run/docker.sock:/var/run/docker.sock
            - acme:/etc/traefik/acme
    docker:
        image: docker:dind
        privileged: yes
        networks:
            - bagdb
        volumes:
            - bags:/bags:ro # Needs to match the path in the bagdb container
            - scripts:/scripts
            - docker_cache:/var/lib/docker
        command: ["dockerd", "--host=tcp://0.0.0.0:2375"]
    bagdb:
        image: ghcr.io/hatchbed/bag-database:latest
        networks:
            - bagdb
        depends_on:
            - postgres
            - openldap
        labels:
            - "traefik.http.routers.bagdb.rule=Host(`bagdb.exaple.com`)"
            - "traefik.http.routers.bagdb.tls=true"
            - "traefik.http.routers.bagdb.tls.certresolver=bagdbresolver"
        volumes:
            - bags:/bags
            - indexes:/root/.ros-bag-database/indexes
            - scripts:/scripts
        env_file:
            - bagdb.env
    postgres:
        image: postgis/postgis:11-2.5
        networks:
            - bagdb
        volumes:
            - postgres:/var/lib/postgresql/data
        env_file:
            - postgres.env
    openldap:
        image: osixia/openldap:1.3.0
        networks:
            - bagdb
        volumes:
            - ldap:/var/lib/ldap
            - slapd:/etc/ldap/slapd.d
        ports:
            - "389:389"
            - "636:636"
            # These ports are exposed so you can easily connect to the container to add
            # data by, for example, using the LDIF files below.  You can hide these ports
            # if you don't need to connect to the container, or perhaps even add another
            # web server behind the reverse proxy to act as an LDAP administration interface.
        env_file:
            - openldap.env
    webviz:
        image: cruise/webviz
        networks:
            - bagdb
        volumes:
            - ./webviz-default.conf:/etc/nginx/conf.d/default.conf:ro
        labels:
            - "traefik.http.routers.webviz.rule=Host(`bagdb.example.com`) && PathPrefix(`/webviz`)"
            - "traefik.http.routers.webviz.tls=true"
            - "traefik.http.routers.webviz.tls.certresolver=bagdbresolver"
            - "traefik.http.services.webviz.loadbalancer.server.port=8080"
networks:
    bagdb: {}
volumes:
    acme:
    bags:
    docker_cache:
    postgres:
    ldap:
    slapd:
    indexes:
    scripts:
        driver_opts:
            type: 'tmpfs'
            device: 'tmpfs'
```

### `people.ldif`

An example LDIF file for creating a "People" group in your LDAP server.  After starting the server,
run `ldapadd -x -D cn=admin,dc=example,dc=com -W -f people.ldif` to add this group.

```
dn: ou=People,dc=example,dc=com
objectClass: organizationalUnit
ou: People
```

### `user.ldif`

An example LDIF file that defines a single user.  Customize this for each user, and after you've
added the People group, run `ldapadd -x -D cn=admin,dc=example,dc=com -W -f user.ldif` to add
this person to the server.

```
dn: uid=jdoe,ou=People,dc=example,dc=com
objectClass: inetOrgPerson
uid: jdoe
sn: Doe
givenName: John
cn: jdoe
displayName: jdoe
userPassword: letmein
```