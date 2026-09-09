---
name: dckrspace
description: |
  manipulating docker's graph of images, containers, volumes, networks
---

# dckrspace

## Architecture

`dckrspace::T` bridges a docker daemon into metatron's uri space. Every docker resource (container, image, volume,
network) is a first-class uri-addressable `obj`. Writes trigger docker cli operations; reads refresh state from the
daemon.

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
==>space::T[?[{?}host=>uri::T,{?}progress=>rec::T]][ctor?dckrspace<=#{?}(dckrspace::T)]@/m/dckr/space/dckrspace
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
==>dckrspace::[
    pattern=>docker:#,
    route=>[docker:=><>],
    progress=>progress_table::[=>]]@/sys/space/docker
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
==>fail::[inst apply failure: exit 125: docker run -d --name web -p 8080:80 -p 443:443 -e NGINX_HOST=localhost -v myvol:/usr/share/nginx/html --network mynet nginx:alpine
   ]@/sys/fail/998
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
    created_at=>datetime::<//2026.09:2/23/47/07/000?tz=-0600>,
    created_since=><6 days ago>,
    id=>b596dc322997,
    repository=>metatron,
    size=>mB::635.0000,
    tag=>local]
==>[
    containers=>1,
    created_at=>datetime::<//2026.07:15/17/57/33/000?tz=-0600>,
    created_since=><7 weeks ago>,
    id=>f0ba77f796e5,
    repository=>nginx,
    size=>mB::62.4000,
    tag=>alpine,
    container=>[!*docker:container/web]]
==>[
    containers=>0,
    created_at=>datetime::<//2026.08:13/13/16/08/000?tz=-0600>,
    created_since=><3 weeks ago>,
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
    tag=>start(26).minus(ea-26-jdk)]
==>[
    containers=>0,
    created_at=>datetime::<//2026.07:16/16/06/26/000?tz=-0600>,
    created_since=><7 weeks ago>,
    id=><9a18aed4ecdd>,
    repository=>postgres,
    size=>mB::445.0000,
    tag=>15]
mtron> *docker:container/+                  [-- all containers                           --]
==>[ command=></app/entrypoint.sh --help>,
    created_at=>datetime::<//2026.09:2/12/04/38/000?tz=-0600>,
    id=>ceca8355aa92d350e2fe559146569f74206e2a31bce4bf26c5e2ea224109644b,
    image=>!*<docker:image/ghcr.io/phaseshift-studio/metatron:latest>,
    labels=>[
     <org.opencontainers.image.description>=>'a distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    names=>laughing_galileo,
    running_for=><6 days ago>,
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
    running_for=><13 days ago>,
    size=>bB::0.0000,
    state=>exited,
    ...(2 more)]
==>[ command=></app/entrypoint.sh [boot=><boot/_T2.boot.mtron>,log=>info]>,
    created_at=>datetime::<//2026.09:2/19/41/54/000?tz=-0600>,
    id=>f1373026426174fb2d705e8c246fe2893fac897df538d6619d52542fd06369a5,
    image=>!*<docker:image/ghcr.io/phaseshift-studio/metatron:main>,
    labels=>[
     <org.opencontainers.image.description>=>'a distributed data-oriented computing language and virtual ...',
     <org.opencontainers.image.licenses>=><AGPL-3.0>,
     <org.opencontainers.image.source>=><https://github.com/phaseshift-studio/metatron>,
     <org.opencontainers.image.title>=>metatron,
     <org.opencontainers.image.vendor>=>'PhaseShift Studio',
     <org.opencontainers.image.version>=>start(0.1000).minus(SNAPSHOT)],
    local_volumes=>0,
    mounts=>/home/killswitch/software/metatron/boot,/home/killswitch/software/metatron,
    names=><T2>,
    ports=><8555/tcp, 8777/tcp>,
    running_for=><6 days ago>,
    ...(4 more)]
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
    running_for=><12 days ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.09:9/03/16/34/000?tz=-0600>,
    id=><035165026a7c80906898b2d951f67e8329be00497315021bcf4915db7111bf5f>,
    image=>!*docker:image/nginx:alpine,
    labels=>[maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>1,
    names=>web,
    running_for=><5 seconds ago>,
    size=>bB::0.0000,
    state=>created,
    ...(3 more)]
==>[ command=><docker-entrypoint.sh mariadbd>,
    created_at=>datetime::<//2026.09:2/02/36/13/000?tz=-0600>,
    id=><8673b240288781fb2d0dddc76a2cf3cac3fce71bca6830f95e7ac8a75ea7c70d>,
    image=>!*<docker:image/mariadb:11.2>,
    labels=>[  <com.docker.compose.config-hash>=>c0d9b351c9f96fc5f6c650960fd4fee862d6ef67c0e79cbc28ddd1b365a63f81,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:35d26c822908fba9f7ff591a95e6abcd7e9c6a0b354af8cb0f4dbd6441dac0fb,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/vtest_a/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/vtest_a,
     <com.docker.compose.project>=>vtest_a,
     <com.docker.compose.service>=>a,
     <com.docker.compose.version>=><2.40.3>,
     <org.opencontainers.image.authors>=>'MariaDB Community',
     ...(10 more)],
    local_volumes=>1,
    names=>vtest_a-a-1,
    running_for=><7 days ago>,
   ...
mtron> *docker:volume/+                     [-- all volumes                              --]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/b56d1026f908d3a4d8a840b85a3c897d41e584781c6aae7fde43f1bc0d1a3f59/_data,
    name=>b56d1026f908d3a4d8a840b85a3c897d41e584781c6aae7fde43f1bc0d1a3f59,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/b780bbf5a83db7463af32b8ee20a7ebeecfa949499d5acfcdfec8d0e2b19eea8/_data,
    name=>b780bbf5a83db7463af32b8ee20a7ebeecfa949499d5acfcdfec8d0e2b19eea8,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/585c8c78d99f10093ca27e1de2ceea340f61026b9b039a8be992edcbcd25a259/_data,
    name=><585c8c78d99f10093ca27e1de2ceea340f61026b9b039a8be992edcbcd25a259>,
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
    mountpoint=>/var/lib/docker/volumes/c9597f1f46528a54161dafffdadf1f1a61e2728475e4014f9c108a791410331a/_data,
    name=>c9597f1f46528a54161dafffdadf1f1a61e2728475e4014f9c108a791410331a,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/75031acd765dadc2102706be8f612c7170bc4f6848cee8b0f7b4150ddde7c4ec/_data,
    name=><75031acd765dadc2102706be8f612c7170bc4f6848cee8b0f7b4150ddde7c4ec>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/cddc3a93669463bf127a4dd6ab682cf9af4ae96da5f20b2760c3372284a5aaa1/_data,
    name=>cddc3a93669463bf127a4dd6ab682cf9af4ae96da5f20b2760c3372284a5aaa1,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/7c3a17f1505e008a326fcb38f4f10361ff42c0ac284ca13c87f8a2bf9ff5f8d9/_data,
    name=><7c3a17f1505e008a326fcb38f4f10361ff42c0ac284ca13c87f8a2bf9ff5f8d9>,
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
    mountpoint=>/var/lib/docker/volumes/4d4e411d7a6eb5e05c50cda6238f183d2dec5eeea8807648bcf429c142c9dafd/_data,
    name=><4d4e411d7a6eb5e05c50cda6238f183d2dec5eeea8807648bcf429c142c9dafd>,
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
    mountpoint=>/var/lib/docker/volumes/43a4aaa45e7342b5869ca61a417800210e699e6f1af8689f0992118d3eb30365/_data,
    name=><43a4aaa45e7342b5869ca61a417800210e699e6f1af8689f0992118d3eb30365>,
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
    mountpoint=>/var/lib/docker/volumes/0454140adc3aade618ce2082739cc046814093a4e10526d1d91698b5d2a8b7e5/_data,
    name=><0454140adc3aade618ce2082739cc046814093a4e10526d1d91698b5d2a8b7e5>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/7dc192fc64c983d86ea8d6f53b782d8ee091a12f1db9713e0b64e4a5b6115b0f/_data,
   ...
mtron> *docker:network/+                    [-- all networks                             --]
==>[
    created_at=>datetime::<//2026.08:6/13/53/33/644?tz=+0000>,
    driver=>bridge,
    id=><357c897ce4c7>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=>dcfdb94ed5044fb11e16ff38ab584fd0bda75806da995978e02547c50430328a,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>test_sqlite,
     <com.docker.compose.version>=><2.40.3>],
    name=>test_sqlite_default,
    scope=>local,
    container=>[!*docker:container/test_sqlite-sqlite-1]]
==>[
    created_at=>datetime::<//2026.09:1/04/21/03/992?tz=+0000>,
    driver=>bridge,
    id=><60b9d1edae74>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=>f850c0d7969233d546cdeb36790876382fa1b66d873393e19366fd7f86e9de62,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>l3a,
     <com.docker.compose.version>=><2.40.3>],
    name=>l3a_default,
    scope=>local]
==>[
    created_at=>datetime::<//2026.09:1/04/21/05/612?tz=+0000>,
    driver=>bridge,
    id=><509f2a48ccdd>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=><64438a15959f521cf8044f612b6482db47ef1a809493a4688a6a7f5ec0863028>,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>l3b,
     <com.docker.compose.version>=><2.40.3>],
    name=>l3b_default,
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
    container=>[!*docker:container/metatron2,!*docker:container/A1F,!*docker:container/T2,!*docker:container/D4,!*docker:container/S7,!*docker:container/B4,!*docker:container/laughing_galileo,!*docker:container/hindsight,!*docker:container/webprobe-630547,!*docker:container/metatron,...(4 more)]]
==>[
    created_at=>datetime::<//2026.08:6/14/09/25/425?tz=+0000>,
    driver=>bridge,
    id=><12f02ba20a4f>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>dr,
     <com.docker.compose.version>=><2.40.3>,
     <com.docker.compose.config-hash>=>b82bc38b085051212ab2999d5d8a56646d5a2f6cd18fb5f3cbf5d7d6624c75fc],
    name=>dr_default,
    scope=>local,
    container=>[!*docker:container/dr-sqlite-1]]
==>[
    created_at=>datetime::<//2026.09:1/03/04/22/809?tz=+0000>,
    driver=>bridge,
    id=>adbf60a6c0cd,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=><8b17a0c82f43430a8621208acc30c2772c02a8888c3002543e019dbc96184751>,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>wtest_a,
     <com.docker.compose.version>=><2.40.3>],
    name=>wtest_a_default,
    scope=>local]
==>[
    created_at=>datetime::<//2026.09:1/04/27/23/379?tz=+0000>,
    driver=>bridge,
    id=><774da16cc6f7>,
    ipv4=>true,
    ipv6=>false,
    internal=>false,
    labels=>[
     <com.docker.compose.config-hash>=><9d7009a3047bdf2dc1c415111e39b9a6547b84dc375bcdf59d01fea94b7d428d>,
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>l3,
     <com.docker.compose.version>=><2.40.3>],
    name=>l3_default,
    scope=>local]
==>[
    created_at=>datetime::<//2026.09:1/02/54/30/746?tz=+0000>,
    driver=>bridge,
    id=><6b91b0807ad3>,
   ...
```
### Inspect a resource

```mtron
mtron> *docker:image/nginx:alpine           [-- full image rec --]
==>[
    containers=>1,
    created_at=>datetime::<//2026.07:15/17/57/33/000?tz=-0600>,
    created_since=><7 weeks ago>,
    id=>f0ba77f796e5,
    repository=>nginx,
    size=>mB::62.4000,
    tag=>alpine,
    container=>[!*docker:container/web]]
mtron> *docker:container/web                [-- full container rec --]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.09:9/03/16/34/000?tz=-0600>,
    id=><035165026a7c80906898b2d951f67e8329be00497315021bcf4915db7111bf5f>,
    image=>!*docker:image/nginx:alpine,
    labels=>[maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>1,
    names=>web,
    running_for=><14 seconds ago>,
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
    created_since=><7 weeks ago>,
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
==>fail::[inst apply failure: fail[ProcessBuilder<1065>:(NullPointerException)]]@/sys/fail/1002
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
==>fail::[inst apply failure: exit 1: docker network create mynet -d bridge
   ]@/sys/fail/1004
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
`docker compose up -d` is executed. Progress streams through the widget.

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
==>fail::[inst apply failure: exit 125: docker run -d --name sqlite --user root -v /tmp/mtron-dbs:/data keinos/sqlite3:latest sh -c sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite
   ]@/sys/fail/1006
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
==>tblespace::[
    pattern=>mydb:#,
    host=><sqlite:/tmp/mtron-dbs/mydb.sqlite>,
    table=>[,],
    route=>[mydb:=><>],
    driver=><org.sqlite.JDBC>]@/sys/space/mydb
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
==>fail::[args do not match inst args:
   	[X=>/m/tble/inst/rewrite/sql_count?int<=#{0}(people/+)]]@/sys/fail/1010
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