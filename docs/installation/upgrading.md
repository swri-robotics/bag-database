---
layout: default
title: Upgrading
nav_order: 3
parent: Installation
description: "How to upgrade the Bag Database"
permalink: /installation/upgrading
---

# Upgrading

## Before You Upgrade

It's a good idea to make a backup of your PostgreSQL database before upgrading.
Use the [pg_dump](https://www.postgresql.org/docs/11/backup-dump.html#BACKUP-DUMP-ALL)
tool to make a backup, or shutdown the Bag Database's containers and just make a
copy of the database container's volume.

## Performing the Upgrade

As long as you're using the same version of PostgreSQL, upgrading should be
painless.  Pull the latest version of the Bag Database image
(`docker pull ghcr.io/swri-robotics/bag-database:latest`) and restarts its container;
if there are any database schema changes, it will automatically update everything.

If you are also updating your PostgreSQL container, you will need to manually
migrate your data to the new database.  Make a backup as described above, then
create a new container with a fresh volume and
[restore your backup](https://www.postgresql.org/docs/11/backup-dump.html#BACKUP-DUMP-RESTORE).
After the new database has been initialized, you can start up your Bag Database
container and it will take care of any remaining work.

## If Something Goes Wrong

If you have any issues during an upgrade, first, please [submit an issue](https://github.com/swri-robotics/bag-database/issues)
with a copy of your Docker container configurations (omitting any passwords!) and
the complete log output that was printed when starting the containers.  After that,
roll back your containers to the previous versions and restore the database from
the backup you made.
