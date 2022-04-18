---
layout: default
title: With Locally Built Version 
parent: Docker
grand_parent: Installation
nav_order: 8
description: "Running a Locally Built Bag Database Instance"
permalink: /installation/docker/with-local-build
---

# With a Locally Built Bag Database Instance

Sometimes it is convenient for using a locally built version of the Bag Database, without pulling a version from the public docker registry. For instance, this procedure can be used to test local modifications and branches.

To build the bag database from the local source folder, simple run

```docker-compose -f docker/build/docker-compose.yml up```

from the root repository of this repository. Note that it is important to do it from the root of the repository because of the context used in the Docker Compose file and the relative file paths accessed in the Dockerfile.
