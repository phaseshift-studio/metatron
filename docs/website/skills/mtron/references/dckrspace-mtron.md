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
mtron> !*dckrspace?docq
==>docs::[
    obj=>space::T[?[
     {?}host=>uri::T,
     {?}progress=>rec::T]][ctor?rng=dckrspace&dom=#{?}(dckrspace::T){<j>}]@/m/dckr/space/dckrspace,
    desc=>'[structural] a docker daemon space',
    example=>[
     'docker:compose/my-stack -> [servic...',
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

**A note on what runs here.** Every block this far — the type, its doc, the space configuration — evaluates in the
docs environment, because none of them touches a daemon: `dckrspace::T` is lazy, and it reports
`docker cli not found` the moment a command would actually go out. Everything below that speaks to a daemon is
therefore *shown* (a plain `mtron` block) rather than run, and the end-to-end section marks which of its steps the
headless loop executes.

## Writing — Containers

### Run a container

```mtron
[-- runs against a live daemon --]
docker:container/web -> [/
  image       => 'nginx:alpine',/
  ports       => [<8080:80>, <443:443>],/
  environment => [NGINX_HOST => localhost],/
  volumes     => ['myvol:/usr/share/nginx/html'],/
  network     => mynet]
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
*docker:image/+.take(5)              [-- first 5 images (keyed by repository:tag) --]
*docker:container/+                  [-- all containers                           --]
*docker:volume/+                     [-- all volumes                              --]
*docker:network/+                    [-- all networks                             --]
```

### Inspect a resource

```mtron
*docker:image/nginx:alpine           [-- full image rec --]
*docker:container/web                [-- full container rec --]
*docker:image/nginx:alpine/size      [-- specific field --]
*docker:container/web/state          [-- container state (running, exited, ...) --]
```

### Graph navigation

```mtron
*docker:container/web/image           [-- uri ref to the container's image             --]
*docker:image/nginx:alpine/containers [-- list of container refs using this image      --]
*docker:container/web/networks        [-- uri ref to the container's network           --]
*docker:network/bridge/containers     [-- list of container refs on this network       --]
*docker:container/web/mounts          [-- list of volume refs mounted on the container --]
*docker:volume/data/containers        [-- list of container refs using this volume     --]
```

The `image`, `networks`, and `mounts` fields on containers are **uri refs**. The `containers`
field on images/networks/volumes is a **lst of uri refs**. This allows for graph navigation without accessing a single
node in the graph and pulling the entire uri space into the result. uri auto-refs are lazy links that are resolved to
their referent upon access.

### Stop and remove a container

```mtron
docker:container/web -> noobj       [-- stops and removes the container --]
```

## Reading — Images

Images are read-only from Docker Hub. The space auto-discovers images from
`docker image ls` and from containers' image references:

```mtron
*docker:image/nginx:alpine/id          [-- the Docker hash --]
*docker:image/nginx:alpine/size        [-- the size, in a unit type --]
*docker:image/nginx:alpine/repository  [-- the repository name --]
*docker:image/nginx:alpine/tag         [-- the tag --]
```

## Writing — Volumes

### Create a volume

```mtron
docker:volume/myvol -> [driver => local]
```

### Remove a volume

```mtron
docker:volume/myvol -> noobj
```

## Writing — Networks

### Create a network

```mtron
docker:network/mynet -> [driver => bridge]
```

### Remove a network

```mtron
docker:network/mynet -> noobj
```

## Docker Compose

### Start a stack

```mtron
docker:compose/my-stack -> [/
  services => [/
    web => [/
      image => 'nginx:alpine',/
      ports => [<8080:80>]/
    ],/
    db => [/
      image => 'postgres:16',/
      environment => [POSTGRES_PASSWORD => secret]/
    ]/
  ]/
]
```

Compose YAML is generated to `/tmp/metatron-docker/<name>/docker-compose.yml` and
`docker compose up -d` is executed. Progress streams through the widget.

### Stop a stack

```mtron
docker:compose/my-stack -> noobj     [-- docker compose down + cleanup --]
```

### Read compose config

```mtron
*docker:compose/my-stack/services/web/image    [-- nginx:alpine --]
*docker:compose/my-stack/services              [-- all services --]
```

## Remote Docker Hosts

Connect to a remote Docker daemon by specifying `host` in the space config:

```mtron
dckrspace::[host => <tcp://192.168.1.100:2375>, ...]@/sys/space/remote
```

All Docker CLI commands are prefixed with `-H <host>`. Supports `tcp://`, `unix://`, and `ssh://` schemes.

## End-to-End: SQLite Container + tbleSpace

This example pulls a SQLite Docker image, runs it with a bind-mounted data directory, and exposes the database through
`tblespace::T` — all from mtron. The daemon-facing steps (1 and 6) are *shown*; the data steps (2 through 4) run in
every docs build, and they run identically against a local socket — the tble half needs only the file.

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
[-- runs against a live daemon; the bind mount is what creates the host directory the steps below use --]
docker:container/sqlite -> [/
  user    => root,/
  image   => 'keinos/sqlite3:latest',/
  command => ['sh', '-c', 'sqlite3 /data/mydb.sqlite ".databases" && chmod 777 /data /data/mydb.sqlite'],/
  volumes => ['/tmp/mtron-dbs:/data']/
]
```

The bind mount `'/tmp/mtron-dbs:/data'` maps the host directory into the container. The user `root` is necessary for
command permissions.

### Step 2: Mount the database via tbleSpace

```mtron
mtron> [-- on a live daemon step 1 created this directory; here the loop creates the same scratch path itself --]
mtron> bash('mkdir -p /tmp/mtron-dbs')
==>['']
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
==>[
    name=>'marko',
    role=>architect]
mtron> mydb:people/2 -> [name=>'stynx',role=>developer]
==>[
    name=>'stynx',
    role=>developer]
mtron> mydb:people/3 -> [name=>'metis',role=>oracle]
==>[
    name=>'metis',
    role=>oracle]
```
### Step 4: Query from mtron

```mtron
mtron> *mydb:people/+                                           [-- all rows              --]
==>[
    name=>'marko',
    role=>architect]
==>[
    name=>'metis',
    role=>oracle]
==>[
    name=>'stynx',
    role=>developer]
mtron> *mydb:people/+/                                          [-- all rows keyed by uri --]
==>mydb:people/1=>[
    name=>'marko',
    role=>architect]
==>mydb:people/3=>[
    name=>'metis',
    role=>oracle]
==>mydb:people/2=>[
    name=>'stynx',
    role=>developer]
mtron> *mydb:people/+/name                                      [-- all names             --]
==>'stynx'
==>'marko'
==>'metis'
mtron> *mydb:people/+.?[role=>developer]==[name=>_]           [-- all developer names   --]
==>[name=>'stynx']
mtron> *mydb:people/+.?[role=>developer]==[name=>_].explain() [-- sql rewrite usage     --]
==>explanation::[
    format=>!inst?rng=str&dom=#{?}(){<j>},
    desc=>[
     rng=><#>,
     insts=>2],
    per_inst=>[
     [
      op=>sql_where,
      args=>[people/+,"role = 'developer'"],
      f=>'<j>',
      form=>gather,
      c_dom=>0,
      c_rng=>0],
     [
      op=>select,
      dom=><#>,
      rng=><#>,
      args=>[[name=>id()]],
      form=>mapper,
      c_dom=>1,
      c_rng=>1]]]
mtron> *mydb:people/+.count()                                   [-- number of rows        --]
==>3
mtron> *mydb:people/+.count().explain()                         [-- sql rewrite usage     --]
==>explanation::[
    format=>!inst?rng=str&dom=#{?}(){<j>},
    desc=>[
     rng=>int,
     insts=>1],
    per_inst=>[[
    op=>sql_count,
    rng=>int,
    args=>[people/+],
    f=>'<j>',
    form=>initial,
    c_dom=>0,
    c_rng=>1]]]
```
### Step 5: The container sees the same data

The Docker container has the database mounted at `/data/mydb.sqlite`. Any process inside the container can read and
write the same file. mtron-backed writes go through tbleSpace → JDBC → the file → visible inside the container.
Container writes go to the file → visible to tbleSpace on next read.

### Step 6: Tear down

```mtron
[-- runs against a live daemon --]
docker:container/sqlite -> noobj     [-- stop + remove the container --]
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
| Mount space           | `dckrspace::[pattern=>docker:#,route=>[docker:=>...]]@/sys/space/docker` |
| Run container         | `docker:container/<name> -> [image => 'img:tag']`                          |
| Run with ports        | `docker:container/<name> -> [image => 'img', ports => [<8080:80>]]`        |
| Run with env          | `[image => 'img', environment => [KEY => val]]`                            |
| Run with volume       | `[image => 'img', volumes => ['vol:/path']]`                               |
| Run with network      | `[image => 'img', network => netname]`                                     |
| Stop container        | `docker:container/<name> -> noobj`                                       |
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
| Remove volume         | `docker:volume/<name> -> noobj`                                          |
| Create network        | `docker:network/<name> -> [driver => bridge]`                              |
| Remove network        | `docker:network/<name> -> noobj`                                         |
| Start compose stack   | `docker:compose/<name> -> [services => [...]]`                             |
| Stop compose stack    | `docker:compose/<name> -> noobj`                                         |
| Remote host           | Add `host => <tcp://host:port>` to space config                            |