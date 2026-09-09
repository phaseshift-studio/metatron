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
    network=>mynet]][MTronException<137>:exit 125: docker run -d --name web -p 8080:80 -p 443:443 -e NGINX_HOST=localhost -v myvol:/usr/share/nginx/html --network mynet nginx:alpine...]][exit 125: docker run -d --name web -p 8080:80 -p 443:443 -e NGINX_HOST=localhost -v myvol:/usr/share/nginx/html --network mynet nginx:alpine
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
    containers=>1,
    created_at=>datetime::<//2024.11:4/13/52/12/000?tz=-0700>,
    created_since=><22 months ago>,
    id=><35d26c822908>,
    repository=>mariadb,
    size=>mB::405.0000,
    tag=>11.2000]
==>[
    containers=>0,
    created_at=>datetime::<//2026.09:2/23/47/07/000?tz=-0600>,
    created_since=><5 days ago>,
    id=>b596dc322997,
    repository=>metatron,
    size=>mB::635.0000,
    tag=>local]
==>[
    containers=>0,
    created_at=>datetime::<//2026.09:4/00/38/58/000?tz=-0600>,
    created_since=><4 days ago>,
    id=><0aad55775c84>,
    repository=>metatron,
    size=>mB::635.0000,
    tag=>dev]
==>[
    containers=>0,
    created_at=>datetime::<//2025.11:13/16/22/24/000?tz=-0700>,
    created_since=><9 months ago>,
    id=><3105709a9740>,
    repository=>eclipse-temurin,
    size=>mB::391.0000,
    tag=>25]
==>[
    containers=>0,
    created_at=>datetime::<//2025.03:27/10/03/48/000?tz=-0600>,
    created_since=><17 months ago>,
    id=><56f8d43cf2e0>,
    repository=>maven,
    size=>mB::481.0000,
    tag=><3.9.9-eclipse-temurin-24>]
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
==>[ command=></app/entrypoint.sh [boot=><boot/agent-ide.boot.mtron>,log=>info]>,
    created_at=>datetime::<//2026.09:2/20/02/38/000?tz=-0600>,
    id=>bcfdf84a298f060eadb8d7a113a49ed556a8256e0c8b444cc8d70695ed4a6939,
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
    names=>A1F,
    ports=><8555/tcp, 8777/tcp>,
    running_for=><5 days ago>,
    ...(4 more)]
==>[ command=>'/app/entrypoint.sh [boot=><boot/docker.boot.mtron>,user=><k...',
    created_at=>datetime::<//2026.08:27/09/35/17/000?tz=-0600>,
    id=><1e5de17716a878b68faad08cdeacdb85c16410ff515a358a1a56efb35a1571cb>,
    image=>!*docker:image/sha256:a458e6353116963c566c300fb8220e210ea1c174b13927f0a8bebd4c0bab3bb5,
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
    running_for=><12 days ago>,
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
    running_for=><12 days ago>,
    size=>bB::0.0000,
    ...(3 more)]
==>[ command=></docker-entrypoint.sh nginx -g 'daemon off;>,
    created_at=>datetime::<//2026.09:7/03/50/19/000?tz=-0600>,
    id=>a45e8b32a9c61b2170dff261822aad4520f0147190f41a9dafede017e778bf3f,
    image=>!*docker:image/nginx,
    labels=>[
     <com.docker.compose.config-hash>=>f3bd03ea666f08645557a71e46313f367713e950177918b309e8a1e5bde8397c,
     <com.docker.compose.container-number>=>1,
     <com.docker.compose.image>=>sha256:4e5db4761e0ff445f7fd29aad680ad28e8abf7d204895557f145d65535abcc1c,
     <com.docker.compose.oneoff>=>False,
     <com.docker.compose.project.config_files>=></tmp/metatron-docker/my-stack/docker-compose.yml>,
     <com.docker.compose.project.working_dir>=>/tmp/metatron-docker/my-stack,
     <com.docker.compose.project>=>my-stack,
   ...
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
    mountpoint=>/var/lib/docker/volumes/3145968f1dc497633f70b63d52de55cd8c1b1216fa010aebcc5415a6b538b11f/_data,
    name=><3145968f1dc497633f70b63d52de55cd8c1b1216fa010aebcc5415a6b538b11f>,
    scope=>local]
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
    mountpoint=>/var/lib/docker/volumes/05db2a667915001a9ec7070129967121b9bb7488772fd89cf47a541bc92e3f9d/_data,
    name=><05db2a667915001a9ec7070129967121b9bb7488772fd89cf47a541bc92e3f9d>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/65bd6ac30590a8b65707a1ca30563c87ef80fa35b2f75bc78c045b715f313826/_data,
    name=><65bd6ac30590a8b65707a1ca30563c87ef80fa35b2f75bc78c045b715f313826>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/b00e05ec792e283dc3f20ac26893169ca15e165de57e7d935293979091a1cbba/_data,
    name=>b00e05ec792e283dc3f20ac26893169ca15e165de57e7d935293979091a1cbba,
    scope=>local,
    container=>[docker:container/vtest_a-a-1]]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/75031acd765dadc2102706be8f612c7170bc4f6848cee8b0f7b4150ddde7c4ec/_data,
    name=><75031acd765dadc2102706be8f612c7170bc4f6848cee8b0f7b4150ddde7c4ec>,
    scope=>local]
==>[
    driver=>local,
    labels=>[=>],
    mountpoint=>/var/lib/docker/volumes/36601db792b20f41259002027539cb64a4b3b6c5bbac44d5207c3f3b4765416e/_data,
    name=><36601db792b20f41259002027539cb64a4b3b6c5bbac44d5207c3f3b4765416e>,
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
    mountpoint=>/var/lib/docker/volumes/fc3b59145e4a7679ad9295f483eb2c4051b180f4ac16e6ca9882b691f3bb666f/_data,
    name=>fc3b59145e4a7679ad9295f483eb2c4051b180f4ac16e6ca9882b691f3bb666f,
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
     <com.docker.compose.network>=>default,
     <com.docker.compose.project>=>test_sqlite,
     <com.docker.compose.version>=><2.40.3>,
     <com.docker.compose.config-hash>=>dcfdb94ed5044fb11e16ff38ab584fd0bda75806da995978e02547c50430328a],
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
    created_at=>datetime::<//2026.09:7/03/50/19/007?tz=+0000>,
    driver=>bridge,
    id=><53c526cb9890>,
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
    created_at=>datetime::<//2026.09:8/18/38/55/000?tz=-0600>,
    id=>ceebff7d2dff0ca6d1e4e4bb91ad0b55cf8486ac195ba8e9689958657bdd1b41,
    image=>!*docker:image/nginx:alpine,
    labels=>[maintainer=>'NGINX Docker Maintainers <docker-maint@nginx.com>'],
    local_volumes=>1,
    names=>web,
    running_for=><30 seconds ago>,
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
==>fail::[apply failure:
   	[lhs]    │ docker:volume/myvol
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([driver=>local]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[driver=>local]][ProcessBuilder<1065>:fail[ProcessBuilder<1065>:(NullPointerException)] ← (NullPointerException)]][fail[ProcessBuilder<1065>:(NullPointerException)]][]@/sys/fail/1338
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
==>fail::[apply failure:
   	[lhs]    │ docker:network/mynet
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([driver=>bridge]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[driver=>bridge]][MTronException<137>:exit 1: docker network create mynet -d bridge...]][exit 1: docker network create mynet -d bridge
   ]@/sys/fail/1340
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
==>fail::[apply failure:
   	[lhs]    │ docker:container/sqlite
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([
     user=>root,
     image=>'keinos/sqlite3:latest',
     command=>[
      'sh',
      '-c',
      'sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite'],
     volumes=>['/tmp/mtron-dbs:/data']]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[
    user=>root,
    image=>'keinos/sqlite3:latest',
    command=>[
     'sh',
     '-c',
     'sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite'],
    volumes=>['/tmp/mtron-dbs:/data']]][MTronException<137>:exit 125: docker run -d --name sqlite --user root -v /tmp/mtron-dbs:/data keinos/sqlite3:latest sh -c sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite...]][exit 125: docker run -d --name sqlite --user root -v /tmp/mtron-dbs:/data keinos/sqlite3:latest sh -c sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite
   ]@/sys/fail/1342
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
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ /m/inst/explain_compute?rng=str&dom=noobj{0}(/m/tble/inst/rewrite/sql_count?int<=#{0}(people/+)){<j>}@<0>
   	 \_dom   │ noobj
   	 \_args  │ [/m/tble/inst/rewrite/sql_count?int<=#{0}(people/+)][Obj<681>:unable to convert minst to code[Obj<681>:class studio.phaseshift.metatron.isa.m.type.impl.MInst cannot be cast to class studio.phaseshift.metatron.isa.m.type.Code (studio.phaseshift.metatron.isa.m.type.impl.MInst and studio.phaseshift.metatron.isa.m.type.Code are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MInst cannot be cast to class studio.phaseshift.metatron.isa.m.type.Code (studio.phaseshift.metatron.isa.m.type.impl.MInst and studio.phaseshift.metatron.isa.m.type.Code are in unnamed module of loader 'app')]][unable to convert minst to code[Obj<681>:class studio.phaseshift.metatron.isa.m.type.impl.MInst cannot be cast to class studio.phaseshift.metatron.isa.m.type.Code (studio.phaseshift.metatron.isa.m.type.impl.MInst and studio.phaseshift.metatron.isa.m.type.Code are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MInst cannot be cast to class studio.phaseshift.metatron.isa.m.type.Code (studio.phaseshift.metatron.isa.m.type.impl.MInst and studio.phaseshift.metatron.isa.m.type.Code are in unnamed module of loader 'app')]@/sys/fail/1346
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