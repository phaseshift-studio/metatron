---
name: dckrspace
description: |
  Docker management through dckrSpace — pull, run, stop containers; manage images, volumes,
  networks; docker-compose up/down; navigate container↔resource graph links. All from mtron.
  TRIGGER: When working with Docker in metatron, dckrSpace, dockerspace::, docker: URIs,
  container run/stop, image pull, volume or network management, docker-compose in mtron.
---

# dckrSpace — Docker in mtron

## Architecture

`dckrspace::T` bridges a Docker daemon into metatron's URI space. Every Docker resource (container, image, volume,
network)
is a first-class uri-addressable `obj`. Writes trigger Docker CLI operations; reads refresh state from the daemon.

The space maintains a bidirectional graph: containers link to their images, networks, and volumes, and each resource
links back to its containers.

```
┌───────────────────────────────────────────────────────────────┐
│                         dckrspace::T                          │
│                                                               │
│  docker:image/nginx:alpine ──── containers ──► [ref]          │
│       ▲                                          │            │
│       │ image                                    │            │
│       │                                          ▼            │
│  docker:container/web ──── networks ──► docker:network/bridge │
│       │                                       ▲               │
│       │ mounts                                │               │
│       ▼                                       │               │
│  docker:volume/data ───── containers ─────► [ref]             │
│                                                               │
│  docker:compose/stack ─── docker-compose.yml                  │
└───────────────────────────────────────────────────────────────┘
```

## Space Configuration

```mtron
mtron> dckrspace::T
mtron> !*dckrspace?docq
==>docs::[
    obj=>dckrspace::T,
    desc=>'a docker daemon space',
    example=>[
     'docker:compose/my-stack -> [services=>[web=>[image=>"nginx"...',
     '*docker:container/+',
     '*docker:image/nginx/<nginx:latest>']]
```
```mtron
dckrspace::[pattern   => docker:#,
            route     => [docker: => <>],
            host      => <tcp://192.168.1.100:2375>,   [-- optional remote host --]
            progress  => progress_table::[=>]]@/sys/space/docker
```

```mtron
mtron> dckrspace::[pattern   => docker:#,
                   route     => [docker: => <>],
                   progress  => progress_table::[=>]]@/sys/space/docker
```
| Field            | Required | Description                                                        |
|------------------|----------|--------------------------------------------------------------------|
| `pattern`        | yes      | URI prefix for this space                                          |
| `route`          | yes      | Maps pattern prefix into space                                     |
| `host`           | no       | Remote Docker daemon (`tcp://`, `unix://`). Omit for local socket. |
| `progress_table` | no       | `ProgressTableWidget` for live pull-progress display               |

## URI Address Space

```
docker:image/<repository:tag>        -- image by repo:tag
docker:image/<hash>                  -- image by Docker hash (if available)
docker:container/<name>              -- container by name
docker:volume/<name>                 -- volume by name
docker:network/<name>                -- network by name
docker:compose/<stack-name>          -- compose config/stack
```

## Writing — Containers

### Run a container

```mtron
mtron> docker:container/web -> [
         image       => 'nginx:alpine',
         ports       => [<8080:80>, <443:443>],
         environment => [NGINX_HOST => localhost],
         volumes     => ['myvol:/usr/share/nginx/html'],
         network     => mynet]
==>fail::[apply failure:
   	[lhs]    │ docker:container/web
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([
     image=>'nginx:alpine',
     ports=>[<8080:80>,<443:443>],
     environment=>[NGINX_HOST=>localhost],
     volumes=>['myvol:/usr/share/nginx/html'],
     network=>mynet]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[
    image=>'nginx:alpine',
    ports=>[<8080:80>,<443:443>],
    environment=>[NGINX_HOST=>localhost],
    volumes=>['myvol:/usr/share/nginx/html'],
    network=>mynet]][MTronException<127>:exit 125: docker run -d --name web -p 8080:80 -p 443:443 -e NGINX_HOST=localhost -v myvol:/usr/share/nginx/html --network mynet nginx:alpine...]][exit 125: docker run -d --name web -p 8080:80 -p 443:443 -e NGINX_HOST=localhost -v myvol:/usr/share/nginx/html --network mynet nginx:alpine
   ]@/sys/fail/172
```
Image pull happens automatically via `docker run`. Pull progress streams through the `progress_table::T` widget if
configured.

| Field         | Type           | Docker flag    | Example          |
|---------------|----------------|----------------|------------------|
| `image`       | str (required) | positional     | `'nginx:alpine'` |
| `ports`       | lst of str     | `-p` each      | `[<8080:80>]`    |
| `environment` | rec            | `-e KEY=VALUE` | `[FOO => bar]`   |
| `volumes`     | lst of str     | `-v` each      | `['data:/data']` |
| `network`     | str            | `--network`    | `mynet`          |

## Reading Summary

### List all resources

```mtron
mtron> *docker:image/+.take(5)              [-- first 5 images (keyed by repository:tag) --]
==>[
    containers=>0,
    created_at=>datetime::<//2026.05:19/17/28/38/000?tz=-0600>,
    created_since=><3 months ago>,
    id=>acc96c360f47,
    repository=>node,
    size=>mB::227.0000,
    tag=><22-slim>]
==>[
    containers=>0,
    created_at=>datetime::<//2026.08:29/14/32/34/000?tz=-0600>,
    created_since=><2 hours ago>,
    id=>ab0c83888045,
    repository=>metatron-build,
    size=>mB::483.0000,
    tag=>24]
==>[
    containers=>1,
    created_at=>datetime::<//2026.08:27/09/35/17/000?tz=-0600>,
    created_since=><2 days ago>,
    id=>a458e6353116,
    repository=>metatron,
    size=>mB::633.0000,
    tag=>dev,
    container=>[!*docker:container/metatron]]
==>[
    containers=>0,
    created_at=>datetime::<//2026.08:13/13/16/08/000?tz=-0600>,
    created_since=><2 weeks ago>,
    id=><19d88319bea9>,
    repository=>postgres,
    size=>mB::451.0000,
    tag=>16]
==>[
    containers=>0,
    created_at=>datetime::<//2025.12:2/14/30/17/000?tz=-0700>,
    created_since=><9 months ago>,
    id=>dd2395ffc43b,
    repository=>openjdk,
    size=>mB::617.0000,
    tag=><26-ea-26-jdk>]
mtron> *docker:container/+                  [-- all containers                           --]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.08:29/16/08/01/000?tz=-0600>,
    id=><5d2b2ba3034feeb925cc5dcf91bcbac67c5b1d7e7a6abd48e0a361bbc76dd598>,
    image=>!*docker:image/nginx,
    labels=>[
     <com.docker.compose.config-hash>=>f3bd03ea666f08645557a71e46313f367713e950177918b309e8a1e5bde8397c,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:4e5db4761e0ff445f7fd29aad680ad28e8abf7d204895557f145d65535abcc1c,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/my-stack/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/my-stack,
     <com.docker.compose.project>=>my-stack,
     <com.docker.compose.service>=>web,
     <com.docker.compose.version>=><2.40.3>,
     maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>0,
    names=>my-stack-web-1,
    ports=>'0.0.0.0:8080->80/tcp, [::]:8080->80/tcp',
    running_for=><13 minutes ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=><java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/sun.nio.cs=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar /app/metatron.jar [boot=><boot/boot.mtron>,log=>info]>,
    created_at=>datetime::<//2026.08:26/06/48/14/000?tz=-0600>,
    id=><0c62cb4b1bc232db84bb98c8f7b58d9ab8d8d3a06f306deb4ea3e119745ed054>,
    image=>!*docker:image/sha256:480de7fa4a90c026913580f891d726e50318e263781ee9f003b0e3aac5fea50b,
    labels=>[
     <org.opencontainers.image.description>=>'A distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    names=>admiring_lalande,
    running_for=><3 days ago>,
    size=>bB::0.0000,
    state=>exited,
    ...(2 more)]
==>[ command=>"""/sbin/tini -g -- sh -c 'sqlite3 /data/dr.sqlite \".database...""",
    created_at=>datetime::<//2026.08:6/13/44/53/000?tz=-0600>,
    id=><92497c549f1fea0779b32c4c8c75c86a01277c198ac063d7a880dc90bea63d40>,
    image=>!*docker:image/keinos/sqlite3:latest,
    local_volumes=>0,
    mounts=></home/killswitch/.metatron>,
    names=>dr_sqlite,
    running_for=><3 weeks ago>,
    size=>bB::0.0000,
    state=>exited,
    ...(2 more)]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.08:29/16/21/45/000?tz=-0600>,
    id=>bb0d3b258c2fa7f0e262d66322cb00b874e728e19d8618fb9c7ca22a62902d9c,
    image=>!*docker:image/nginx:alpine,
    labels=>[maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>1,
    names=>web,
    running_for=><1 second ago>,
    size=>bB::0.0000,
    state=>created,
    ...(3 more)]
==>[ command=>"/bin/sh -c 'apt-get update     && apt-get install -y --no-i...",
    created_at=>datetime::<//2026.08:26/18/03/36/000?tz=-0600>,
    id=>f194ebdf3c891dbf185394e7d03a3d5d67458e79f4644294c99ed308343ea154,
    image=>!*docker:image/sha256:a6e008c6cf7e3746e3d9aa2b4d9cebd80463124d39997e51cb8e1c2efec481e8,
    labels=>[
     <org.opencontainers.image.description>=>'a distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    names=>awesome_haibt,
    running_for=><2 days ago>,
    size=>bB::0.0000,
    state=>exited,
    ...(2 more)]
==>[ command=>"/bin/sh -c 'apt-get update     && apt-get install -y --no-i...",
    created_at=>datetime::<//2026.08:26/17/57/04/000?tz=-0600>,
    id=><0461485aefc37a9895ac0cb3ee79e8bc73612029e59a7fac96037b131044e1e3>,
    image=>!*docker:image/sha256:a6e008c6cf7e3746e3d9aa2b4d9cebd80463124d39997e51cb8e1c2efec481e8,
    labels=>[
     <org.opencontainers.image.description>=>'a distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    names=>stupefied_burnell,
    running_for=><2 days ago>,
    size=>bB::0.0000,
    state=>exited,
    ...(2 more)]
==>[ command=>"""/sbin/tini -g -- sh -c 'sqlite3 /data/dr.sqlite \".database...""",
    created_at=>datetime::<//2026.08:6/14/10/11/000?tz=-0600>,
    id=><461d48c7bd9c9a67a5723ff145de6814b89a84b1edd0bbf9c7ff703bac14f346>,
    image=>!*docker:image/keinos/sqlite3:latest,
    labels=>[
     <com.docker.compose.config-hash>=><66b3453120ba0a109508db556cf836d0c09d14fea2283ee9f3a27167bc2ee06e>,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:f7949ff704155043d694069c4ec579d237c22151fb3ca3e9fe25829d1c5d7ea7,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/dr/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/dr,
     <com.docker.compose.project>=>dr,
     <com.docker.compose.replace>=>sqlite-1,
     <com.docker.compose.service>=>sqlite,
     <com.docker.compose.version>=><2.40.3>],
    local_volumes=>0,
    mounts=></home/killswitch/.metatron>,
    names=>dr-sqlite-1,
    running_for=><3 weeks ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=><tail -f /dev/null>,
    created_at=>datetime::<//2026.08:27/08/33/01/000?tz=-0600>,
    id=><14ffc8d6d3a69e2a2fa0070cd1dd8d0b1d748cb52555c594bbe42e79a55abce1>,
    image=>!*docker:image/hibitdev/sqlite:latest,
    labels=>[
     <com.docker.compose.config-hash>=>c9c4e6a1111dc264bc25cd6ea400d6781d6073334ce4a5211f661f2469e9128d,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:5edbbb6fc06708277219996bdca7bd2921e2f340d8fac1156ae4615d4af2735c,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/metatron_sqlite/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/metatron_sqlite,
     <com.docker.compose.project>=>metatron_sqlite,
     <com.docker.compose.service>=>sqlite,
     <com.docker.compose.version>=><2.40.3>],
    local_volumes=>0,
    mounts=>/tmp/metatron_data,
    names=>metatron_sqlite-sqlite-1,
    running_for=><2 days ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=>'/app/entrypoint.sh [boot=><boot/docker.boot.mtron>,user=><k...',
    created_at=>datetime::<//2026.08:27/09/35/17/000?tz=-0600>,
    id=><1e5de17716a878b68faad08cdeacdb85c16410ff515a358a1a56efb35a1571cb>,
    image=>!*docker:image/metatron:dev,
    labels=>[
     <org.opencontainers.image.description>=>'a distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    mounts=></home/killswitch/software/metatron/boot,/home/killswitch/software/metatron/conf,/var/run/docker.sock>,
    names=>metatron,
    running_for=><2 days ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=><tail -f /dev/null>,
    created_at=>datetime::<//2026.08:6/14/41/18/000?tz=-0600>,
    id=><940ada5b423fc56f3b0c5e946d2825d31a340b2b076ea086ecca8165e00c6bc8>,
    image=>!*docker:image/hibitdev/sqlite:latest,
    labels=>[
     <com.docker.compose.config-hash>=>cd837e1126fb2ccc11ba09a68cba12778244699fd07e49790a70bcb69051fae6,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:5edbbb6fc06708277219996bdca7bd2921e2f340d8fac1156ae4615d4af2735c,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/dr_sqlite/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/dr_sqlite,
     <com.docker.compose.project>=>dr_sqlite,
     <com.docker.compose.replace>=>sqlite-1,
     <com.docker.compose.service>=>sqlite,
     <com.docker.compose.version>=><2.40.3>],
    local_volumes=>0,
    mounts=></home/killswitch/.metatron>,
    names=>dr_sqlite-sqlite-1,
    running_for=><3 weeks ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=></sbin/tini -g -- sqlite3 /data/dr.sqlite .databases>,
    created_at=>datetime::<//2026.08:6/14/05/24/000?tz=-0600>,
    id=><49194fe901ab03ee0936575362ff6f3121430d4dabf94d41f9f90ee2f985039e>,
    image=>!*docker:image/keinos/sqlite3:latest,
    labels=>[
     <com.docker.compose.config-hash>=><8ec280f751ccdf65e1062e10096d6bd6252890da2d97db6dff207f1b6b7157cd>,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:f7949ff704155043d694069c4ec579d237c22151fb3ca3e9fe25829d1c5d7ea7,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/test_sqlite/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/test_sqlite,
     <com.docker.compose.project>=>test_sqlite,
     <com.docker.compose.replace>=>sqlite-1,
     <com.docker.compose.service>=>sqlite,
     <com.docker.compose.version>=><2.40.3>],
    local_volumes=>0,
    mounts=></home/killswitch/.metatron>,
    names=>test_sqlite-sqlite-1,
    running_for=><3 weeks ago>,
    size=>bB::0.0000,
    ...(3 more)]
mtron> *docker:volume/+                     [-- all volumes                              --]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/0e43bb3fc84194a58954343e1f4d1a3420400d4c0b32f35cc6124a0610e6372f/_data,
    name=><0e43bb3fc84194a58954343e1f4d1a3420400d4c0b32f35cc6124a0610e6372f>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/dc1dc6d421ed0e3f554b65edcecb86267650a5030ba79be9fa3af4c039e15606/_data,
    name=>dc1dc6d421ed0e3f554b65edcecb86267650a5030ba79be9fa3af4c039e15606,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/f28b7ade1c140fc82c033f7e6c60860d460ae4467ed99b2a38436eb221596b95/_data,
    name=>f28b7ade1c140fc82c033f7e6c60860d460ae4467ed99b2a38436eb221596b95,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/6b5e0f05a976195281987a3e1e2f2c58ffc4068dfd93a0693bd82c647490e47b/_data,
    name=><6b5e0f05a976195281987a3e1e2f2c58ffc4068dfd93a0693bd82c647490e47b>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/4b55f5b2c0e56d658e7c291078459bb261e4cbd757b07030b166cc5ee3479c49/_data,
    name=><4b55f5b2c0e56d658e7c291078459bb261e4cbd757b07030b166cc5ee3479c49>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/8f6e4a6c76f65dce98045ff40d42e8e3497cb409587fe1354c6d72708dc46e21/_data,
    name=><8f6e4a6c76f65dce98045ff40d42e8e3497cb409587fe1354c6d72708dc46e21>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/0454140adc3aade618ce2082739cc046814093a4e10526d1d91698b5d2a8b7e5/_data,
    name=><0454140adc3aade618ce2082739cc046814093a4e10526d1d91698b5d2a8b7e5>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/7dc192fc64c983d86ea8d6f53b782d8ee091a12f1db9713e0b64e4a5b6115b0f/_data,
    name=><7dc192fc64c983d86ea8d6f53b782d8ee091a12f1db9713e0b64e4a5b6115b0f>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/548ab54c3a130e02e5ee38444dbc08e8fcc297415b55d5dd56e99aa2440d07b1/_data,
    name=><548ab54c3a130e02e5ee38444dbc08e8fcc297415b55d5dd56e99aa2440d07b1>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/c786241a6175764655894c69a0101f812766ec6056bd58d03509b62dd6f58478/_data,
    name=>c786241a6175764655894c69a0101f812766ec6056bd58d03509b62dd6f58478,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/7f082e531d5a480825d6f7f7ff262e1ee66c8e95a85834e3f47bd8e122a054c5/_data,
    name=><7f082e531d5a480825d6f7f7ff262e1ee66c8e95a85834e3f47bd8e122a054c5>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/c78a134e31ab93f90b8f2632cc767a78e8b755b1815822c1ae3e03d41391ea49/_data,
    name=>c78a134e31ab93f90b8f2632cc767a78e8b755b1815822c1ae3e03d41391ea49,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/ae92ac148d70046c73d0b5e94cd2713f417b09191d37e312574cced4e4496f3e/_data,
    name=>ae92ac148d70046c73d0b5e94cd2713f417b09191d37e312574cced4e4496f3e,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/bc5af4c60140cceea92b061e4bbfcad6f9d58b7b729b3c09f790bddfeb67cf34/_data,
    name=>bc5af4c60140cceea92b061e4bbfcad6f9d58b7b729b3c09f790bddfeb67cf34,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/1f7558809ec3879593daae1b898ca9d95187ccb282e8e710dcbd9c558085bf25/_data,
    name=><1f7558809ec3879593daae1b898ca9d95187ccb282e8e710dcbd9c558085bf25>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/ba4de75e457c5c6f2340f0c19650fcca3a1e3a14b15daf7fab7eecdc472815e3/_data,
    name=>ba4de75e457c5c6f2340f0c19650fcca3a1e3a14b15daf7fab7eecdc472815e3,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/4d4e411d7a6eb5e05c50cda6238f183d2dec5eeea8807648bcf429c142c9dafd/_data,
    name=><4d4e411d7a6eb5e05c50cda6238f183d2dec5eeea8807648bcf429c142c9dafd>,
    scope=>local]
==>[
    driver=>local,
    mountpoint=>/var/lib/docker/volumes/myvol/_data,
    name=>myvol,
    scope=>local,
    container=>[docker:container/web]]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/2e6937f5c4661d5f83b08cb451c05d6ce6df412e42ab5ad4b68abc220bf75100/_data,
    name=><2e6937f5c4661d5f83b08cb451c05d6ce6df412e42ab5ad4b68abc220bf75100>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/7bdaf667ce61d7ef1b033726edfaec05a20f77c7bb42a5f1959578d383b4993c/_data,
    name=><7bdaf667ce61d7ef1b033726edfaec05a20f77c7bb42a5f1959578d383b4993c>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/9000b84a39160189684fa7149b69a58c888808f0c74e09f88cd7dc621db5fe67/_data,
    name=><9000b84a39160189684fa7149b69a58c888808f0c74e09f88cd7dc621db5fe67>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/d5bd4e119597c8439b6992dd86e93518a456f43839fbac90d14e2013a90779a0/_data,
    name=>d5bd4e119597c8439b6992dd86e93518a456f43839fbac90d14e2013a90779a0,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/a254d53a9c9a5f83c168448e5af5e5f3cc9c3d9e8a3532a1b4a69492293c98c0/_data,
    name=>a254d53a9c9a5f83c168448e5af5e5f3cc9c3d9e8a3532a1b4a69492293c98c0,
    scope=>local]
mtron> *docker:network/+                    [-- all networks                             --]
==>[
    created_at=>datetime::<//2026.08:6/13/53/33/644?tz=+0000>,
    driver=>bridge,
    id=><357c897ce4c7>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.version>=><2.40.3>,
     <com.docker.compose.config-hash>=>dcfdb94ed5044fb11e16ff38ab584fd0bda75806da995978e02547c50430328a,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>test_sqlite],
    name=>test_sqlite_default,
    scope=>local,
    container=>[!*docker:container/test_sqlite-sqlite-1]]
==>[
    created_at=>datetime::<//2026.08:27/08/33/01/479?tz=+0000>,
    driver=>bridge,
    id=>f1cc77f79643,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.version>=><2.40.3>,
     <com.docker.compose.config-hash>=>edf7769ebc38e22fe8be401f97ddeabc2328a412e6a2f42ed79e2e88fa012357,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>metatron_sqlite],
    name=>metatron_sqlite_default,
    scope=>local,
    container=>[!*docker:container/metatron_sqlite-sqlite-1]]
==>[
    created_at=>datetime::<//2026.08:6/14/12/04/985?tz=+0000>,
    driver=>bridge,
    id=><04cebb297272>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=><454211c5597511dbc4de2c2d7054ec34854741b9a0a3020c77fa2de25572c260>,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>dr_sqlite,
     <com.docker.compose.version>=><2.40.3>],
    name=>dr_sqlite_default,
    scope=>local,
    container=>[!*docker:container/dr_sqlite-sqlite-1]]
==>[
    created_at=>datetime::<//2026.08:29/16/08/01/176?tz=+0000>,
    driver=>bridge,
    id=>a78cd8df9eb8,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=><58c75ad45450a419e19489859bceed95b344afac255832f7cedc5ea678321155>,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>my-stack,
     <com.docker.compose.version>=><2.40.3>],
    name=>my-stack_default,
    scope=>local,
    container=>[!*docker:container/my-stack-web-1]]
==>[
    created_at=>datetime::<//2025.11:8/13/37/14/409?tz=+0000>,
    driver=>host,
    id=>be91c71a3135,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    name=>host,
    scope=>local]
==>[
    created_at=>datetime::<//2026.08:28/08/55/54/969?tz=+0000>,
    driver=>bridge,
    id=><1cfee6f07616>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    name=>bridge,
    scope=>local,
    container=>[
     !*docker:container/metatron,
     !*docker:container/awesome_haibt,
     !*docker:container/stupefied_burnell,
     !*docker:container/admiring_lalande,
     !*docker:container/dr_sqlite]]
==>[
    created_at=>datetime::<//2026.08:6/14/09/25/425?tz=+0000>,
    driver=>bridge,
    id=><12f02ba20a4f>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=>b82bc38b085051212ab2999d5d8a56646d5a2f6cd18fb5f3cbf5d7d6624c75fc,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>dr,
     <com.docker.compose.version>=><2.40.3>],
    name=>dr_default,
    scope=>local,
    container=>[!*docker:container/dr-sqlite-1]]
```
### Inspect a resource

```mtron
mtron> *docker:image/nginx:alpine           [-- full image rec --]
==>[
    containers=>1,
    created_at=>datetime::<//2026.07:15/17/57/33/000?tz=-0600>,
    created_since=><6 weeks ago>,
    id=>f0ba77f796e5,
    repository=>nginx,
    size=>mB::62.4000,
    tag=>alpine,
    container=>[!*docker:container/web]]
mtron> *docker:container/web                [-- full container rec --]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.08:29/16/21/45/000?tz=-0600>,
    id=>bb0d3b258c2fa7f0e262d66322cb00b874e728e19d8618fb9c7ca22a62902d9c,
    image=>!*docker:image/nginx:alpine,
    labels=>[maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>1,
    names=>web,
    running_for=><3 seconds ago>,
    size=>bB::0.0000,
    state=>created,
    ...(3 more)]
mtron> *docker:image/nginx:alpine/size      [-- specific field --]
==>mB::62.4000
mtron> *docker:container/web/state          [-- container state (running, exited, ...) --]
==>created
```
### Graph navigation

```mtron
mtron> *docker:container/web/image           [-- uri ref to the container's image             --]
==>[
    containers=>1,
    created_at=>datetime::<//2026.07:15/17/57/33/000?tz=-0600>,
    created_since=><6 weeks ago>,
    id=>f0ba77f796e5,
    repository=>nginx,
    size=>mB::62.4000,
    tag=>alpine,
    container=>[!*docker:container/web]]
mtron> *docker:image/nginx:alpine/containers [-- list of container refs using this image      --]
==>1
mtron> *docker:container/web/networks        [-- uri ref to the container's network           --]
mtron> *docker:network/bridge/containers     [-- list of container refs on this network       --]
mtron> *docker:container/web/mounts          [-- list of volume refs mounted on the container --]
mtron> *docker:volume/data/containers        [-- list of container refs using this volume     --]
```
The `image`, `networks`, and `mounts` fields on containers are **uri refs**. The `containers`
field on images/networks/volumes is a **lst of uri refs**. This allows for graph navigation without accessing a single
node in the graph and pulling the entire uri space into the result. uri auto-refs are lazy links that are resolved to
their referent upon access.

### Stop and remove a container

```mtron
mtron> docker:container/web -> noobj       [-- stops and removes container --]
```
## Writing — Images

Images are read-only from Docker Hub. The space auto-discovers images from
`docker image ls` and from containers' image references.

```mtron
mtron> *docker:image/nginx:alpine/id          [-- Docker hash (f7949ff70415) --]
==>f0ba77f796e5
mtron> *docker:image/nginx:alpine/size        [-- mB::142.0 --]
==>mB::62.4000
mtron> *docker:image/nginx:alpine/repository  [-- nginx --]
==>nginx
mtron> *docker:image/nginx:alpine/tag         [-- alpine --]
==>alpine
```
## Writing — Volumes

### Create a volume

```mtron
mtron> docker:volume/myvol -> [driver => local]
==>fail::[apply failure:
   	[lhs]    │ docker:volume/myvol
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([driver=>local]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[driver=>local]][ProcessBuilder<1065>:fail[ProcessBuilder<1065>:(NullPointerException)] ← (NullPointerException)]][fail[ProcessBuilder<1065>:(NullPointerException)]][]@/sys/fail/174
```
### Remove a volume

```mtron
mtron> docker:volume/myvol -> noobj
==>[
    driver=>local,
    mountpoint=>/var/lib/docker/volumes/myvol/_data,
    name=>myvol,
    scope=>local]
```
## Writing — Networks

### Create a network

```mtron
mtron> docker:network/mynet -> [driver => bridge]
==>[
    created_at=>datetime::<//2026.08:29/16/21/57/667?tz=+0000>,
    driver=>bridge,
    id=>af0f1ac6f9d4,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    name=>mynet,
    scope=>local]
```
### Remove a network

```mtron
mtron> docker:network/mynet -> noobj
```
## Docker Compose

### Start a stack

```mtron
mtron> docker:compose/my-stack -> [
         services => [
           web => [
             image => 'nginx:alpine',
             ports => [<8080:80>]
           ],
           db => [
             image => 'postgres:16',
             environment => [POSTGRES_PASSWORD => secret]
           ]
         ]
       ]
==>[services=>[
    web=>[
     image=>'nginx:alpine',
     ports=>[<8080:80>]],
    db=>[
     image=>'postgres:16',
     environment=>[POSTGRES_PASSWORD=>secret]]]]
```
Compose YAML is generated to `/tmp/metatron-docker/<name>/docker-compose.yml` and
`docker compose up -d --wait` is executed — the write blocks until the stack
reports healthy.  Give each service a `healthcheck` (e.g. `test =>
['CMD','mysqladmin','ping','--silent']`) so a boot file can register a space on
the backend immediately after the up returns; without a healthcheck `--wait`
satisfies on container start. Progress streams through the widget.

### Stop a stack

```mtron
mtron> docker:compose/my-stack -> noobj     [-- docker compose down + cleanup --]
```
### Read compose config

```mtron
mtron> *docker:compose/my-stack/services/web/image    [-- nginx:alpine --]
mtron> *docker:compose/my-stack/services              [-- all services --]
```
## Remote Docker Hosts

Connect to a remote Docker daemon by specifying `host` in the boot config:

```mtron
dockerspace::[host => <tcp://192.168.1.100:2375>, ...]@/sys/space/remote
```

All Docker CLI commands are prefixed with `-H <host>`. Supports `tcp://`, `unix://`, and `ssh://` schemes.

## End-to-End: SQLite Container + tbleSpace

This example pulls a SQLite Docker image, runs it with a bind-mounted data directory, and exposes the database through
tbleSpace — all from mtron.

```
┌────────────────────────────────────────┐
│  Host filesystem                       │
│  /tmp/mtron-dbs/                       │
│  └── mydb.sqlite ◄── tbleSpace (JDBC)  │
│       ▲                                │
│       │ bind mount                     │
│       │                                │
│  ┌────┴───────────────┐                │
│  │  Docker container  │                │
│  │  keinos/sqlite3    │                │
│  │  /data/            │                │
│  │  └── mydb.sqlite   │                │
│  └────────────────────┘                │
└────────────────────────────────────────┘
```

### Step 1: Pull + run the SQLite container

```mtron
mtron> docker:container/sqlite -> [
         user    => root,
         image   => 'keinos/sqlite3:latest',
         command => ['sh', '-c', 'sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite'],
         volumes => ['/tmp/mtron-dbs:/data']
       ]
==>[ command=>"""/sbin/tini -g -- sh -c 'sqlite3 /data/mydb.sqlite \".databa...""",
    created_at=>datetime::<//2026.08:29/16/22/10/000?tz=-0600>,
    id=>a2eca72f1c18e89bd263eb6def421a3192512f9b08233c30c79b0562e2c1dd74,
    image=>!*docker:image/keinos/sqlite3:latest,
    local_volumes=>0,
    mounts=>/tmp/mtron-dbs,
    names=>sqlite,
    running_for=><Less than a second ago>,
    size=>bB::0.0000,
    state=>running,
    ...(2 more)]
```
The bind mount `'/tmp/mtron-dbs:/data'` maps the host directory into the container. The user `root` is necessary for
command permissions.

### Step 2: Mount the database via tbleSpace

```mtron
mtron> tblespace::[pattern => mydb:#,
                   host    => <sqlite:/tmp/mtron-dbs/mydb.sqlite>,
                   table   => [,],
                   route   => [mydb: => <>],
                   driver  => <org.sqlite.JDBC>]@/sys/space/mydb
```
### Step 3: Create tables and insert data

```mtron
mtron> [-- insert tble rows --]
mtron> mydb:people/1 -> [name=>'marko',role=>architect]
==>[name=>'marko',role=>architect]
mtron> mydb:people/2 -> [name=>'stynx',role=>developer]
==>[name=>'stynx',role=>developer]
mtron> mydb:people/3 -> [name=>'metis',role=>oracle]
==>[name=>'metis',role=>oracle]
```
### Step 4: Query from mtron

```mtron
mtron> *mydb:people/+                                           [-- all rows              --]
==>[name=>'marko',role=>architect]
==>[name=>'metis',role=>oracle]
==>[name=>'stynx',role=>developer]
mtron> *mydb:people/+/                                          [-- all rows keyed by uri --]
==>mydb:people/1=>[name=>'marko',role=>architect]
==>mydb:people/3=>[name=>'metis',role=>oracle]
==>mydb:people/2=>[name=>'stynx',role=>developer]
mtron> *mydb:people/+/name                                      [-- all names             --]
==>'stynx'
==>'marko'
==>'metis'
mtron> *mydb:people/+.=?=[role=>developer]==[name=>_]           [-- all developer names   --]
==>[name=>'stynx']
mtron> *mydb:people/+.=?=[role=>developer]==[name=>_].explain() [-- sql rewrite usage     --]
==>"""
    op         dom      rng      args                           f    desc    c_dom  c_rng 
    sql_where  #{*}::T  #{*}::T  people/+,"role = 'developer'"  <j>  gather  {*}    {*}   
    select     #::T     #::T     [name=>id()]                   <?>  mapper  {1}    {1}   
   """
mtron> *mydb:people/+.count()                                   [-- number of rows        --]
==>3
mtron> *mydb:people/+.count().explain()                         [-- sql rewrite usage     --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ /m/inst/explain_compute?rng=str&dom=noobj{0}(/m/tble/inst/rewrite/sql_count?int<=#{0}(people/+)){<j>}@<0>
   	 \_dom   │ noobj
   	 \_args  │ [/m/tble/inst/rewrite/sql_count?int<=#{0}(people/+)][MTronException<162>:fail[no stack element<0>:(ClassCastException)] ← (ClassCastException)]][fail[no stack element<0>:(ClassCastException)]][]@/sys/fail/176
```
### Step 5: The container sees the same data

The Docker container has the database mounted at `/data/mydb.sqlite`. Any process inside the container can read and
write the same file. mtron-backed writes go through tbleSpace → JDBC → the file → visible inside the container.
Container writes go to the file → visible to tbleSpace on next read.

### Step 6: Tear down

```mtron
mtron> docker:container/sqlite -> noobj     [-- stop + remove container --]
```
The database file persists at `/tmp/mtron-dbs/mydb.sqlite`. Re-mount `tblespace::T` later to pick up where the database
state was left off.

### How it works

| Layer          | Technology   | Role                                                                              |
|----------------|--------------|-----------------------------------------------------------------------------------|
| `dckrspace::T` | Docker CLI   | Pulls image, runs container, bind-mounts volume                                   |
| `tblespace::T` | SQLite JDBC  | Auto-creates `.sqlite` file, creates tables from record schemas, executes queries |
| bind mount     | Linux kernel | Shares filesystem between host and container                                      |
| mtron          | URI graph    | Uniform `mydb:people -> [...]` write / `*mydb:people/+` read across both spaces   |

The key insight: **`dckrspace::T` manages the container lifecycle; `tblespace::T` manages the data**. They meet at the
bind-mounted directory. No `docker exec`, no separate SQL client, no out-of-band setup — the database is born from a
mtron write.

## Quick Reference

| Task                  | Expression                                                                 |
|-----------------------|----------------------------------------------------------------------------|
| Mount space           | `dockerspace::[pattern=>docker:#,route=>[docker:=>...]]@/sys/space/docker` |
| Run container         | `docker:container/<name> -> [image => 'img:tag']`                          |
| Run with ports        | `docker:container/<name> -> [image => 'img', ports => [<8080:80>]]`        |
| Run with env          | `[image => 'img', environment => [KEY => val]]`                            |
| Run with volume       | `[image => 'img', volumes => ['vol:/path']]`                               |
| Run with network      | `[image => 'img', network => netname]`                                     |
| Stop container        | `docker:container/<name> -> {0}id()`                                       |
| List images           | `*docker:image/+`                                                          |
| List containers       | `*docker:container/+`                                                      |
| Inspect image         | `*docker:image/repo:tag`                                                   |
| Inspect container     | `*docker:container/<name>`                                                 |
| Container's image ref | `*docker:container/<name>/image`                                           |
| Image's containers    | `*docker:image/repo:tag/containers`                                        |
| Container's network   | `*docker:container/<name>/networks`                                        |
| Network's containers  | `*docker:network/<name>/containers`                                        |
| Container's volumes   | `*docker:container/<name>/mounts`                                          |
| Volume's containers   | `*docker:volume/<name>/containers`                                         |
| Create volume         | `docker:volume/<name> -> [driver => local]`                                |
| Remove volume         | `docker:volume/<name> -> {0}id()`                                          |
| Create network        | `docker:network/<name> -> [driver => bridge]`                              |
| Remove network        | `docker:network/<name> -> {0}id()`                                         |
| Start compose stack   | `docker:compose/<name> -> [services => [...]]`                             |
| Stop compose stack    | `docker:compose/<name> -> {0}id()`                                         |
| Remote host           | Add `host => <tcp://host:port>` to space config                            |