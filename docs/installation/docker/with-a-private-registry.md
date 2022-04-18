---
layout: default
title: With a Private Registry
parent: Docker
grand_parent: Installation
nav_order: 3
description: "Using docker-compose to start up a Bag Database with a private Docker registry"
permalink: /installation/docker/with-a-private-registry
---

# With a Private Registry

If you want to store Docker images for running scripts locally, it makes sense to have your own
Docker registry.  It's straightforward to add a registry container with Docker compose,
but keep in mind that unless you also configure it to use SSL with a valid certificate, you will
also need to configure the Bag Database's docker-in-docker container to allow it as an insecure registry. Please see [docker-compose.yml](https://github.com/swri-robotics/bag-database/blob/master/docker/private_registry/docker-compose.yml) as an example.

**Note**: The [docker-java-api](https://github.com/amihaiemil/docker-java-api) library that the Bag
Database is currently using does not support authenticating with a registry.  Until support is added or
it's modified to use a different library, it will only be possible to pull images from registries that
do not require authentication.

Note that the `docker` container configuration mounts a volume at `/etc/docker/daemon.json`. To allow connecting to `registry:5000` insecurely, that file should contain:

```yaml
{
  "insecure-registries" : ["registry:5000"]
}
```

This configuration is otherwise the same as the example [Without Authentication](docker/without-authentication).

After starting the containers, test it by pulling an image, tagging it, pushing it to your registry,
and then running it with a script.

```bash
$ docker pull ros:melodic
melodic: Pulling from library/ros
...
Digest: sha256:9b73abfadca0246a5eb77e0e03277e6afce2a488616a0750680f5dd85dcc0516
Status: Downloaded newer image for ros:melodic
docker.io/library/ros:melodic
$ docker tag ros:melodic localhost:5000/ros:melodic
$ docker push localhost:5000/ros:melodic
The push refers to repository [localhost:5000/ros]
...
melodic: digest: sha256:828286c15eb8e624503154e8af4db1caeec181510ee2f3b94dfdb289897ea0a2 size: 2833
```

Now create and run a script that uses the image `registry:5000/ros:melodic`, and you should be able to
see it pull that image in the log file.

![Example Script](../../assets/images/private-registry.png)

```
15:23:41.413 [http-nio-8080-exec-8] INFO  c.g.s.scripts.ScriptController - runScript: 10
15:23:41.431 [http-nio-8080-exec-8] DEBUG c.g.s.scripts.ScriptService - Dispatching script to executor.
15:23:41.431 [ThreadPoolTaskExecutor-1] INFO  c.g.s.scripts.RunnableScript - Starting RunnableScript task for [Registry Test]
15:23:41.432 [ThreadPoolTaskExecutor-1] DEBUG c.g.s.scripts.RunnableScript - Writing script to temporary directory: /var/lib/tomcat9/bagdb_scripts
15:23:41.440 [ThreadPoolTaskExecutor-1] INFO  c.g.s.scripts.RunnableScript - Pulling Docker image: [registry:5000/ros] with tag: [melodic]
15:23:41.451 [ThreadPoolTaskExecutor-1] DEBUG c.g.s.scripts.RunnableScript - Container config:
{"NetworkDisable":true,"Image":"registry:5000/ros:melodic","HostConfig":{"Binds":["/var/lib/tomcat9/bagdb_scripts/bagdb3030777406234527103py:/script.py","/home/preed/public_html/bags//ranger__2020-07-24-22-29-32.bag:/ranger__2020-07-24-22-29-32.bag:"]},"Cmd":["/script.py","/ranger__2020-07-24-22-29-32.bag"]}
15:23:41.480 [ThreadPoolTaskExecutor-1] DEBUG c.g.s.scripts.RunnableScript - Created container: 25a44c3c90b77033b03c57c4702baaf856bac4d59164103e5ba63469f64a47a8
15:23:41.733 [ThreadPoolTaskExecutor-1] DEBUG c.g.s.scripts.RunnableScript - Started container: 25a44c3c90b77033b03c57c4702baaf856bac4d59164103e5ba63469f64a47a8
15:23:41.768 [MessageBroker-1] DEBUG c.g.s.scripts.ScriptService - Before check: 1 scripts currently running.
15:23:41.768 [MessageBroker-1] DEBUG c.g.s.scripts.ScriptService - 1 scripts currently running.
15:23:42.097 [ThreadPoolTaskExecutor-1] DEBUG c.g.s.scripts.RunnableScript - Output:
{ 'success': true }
```
