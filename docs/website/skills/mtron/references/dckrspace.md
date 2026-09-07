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
    network=>mynet]][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/42
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
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/+][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/46
mtron> *docker:container/+                  [-- all containers                           --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/+][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/50
mtron> *docker:volume/+                     [-- all volumes                              --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:volume/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:volume/+][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/54
mtron> *docker:network/+                    [-- all networks                             --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:network/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:network/+][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/58
```
### Inspect a resource

```mtron
mtron> *docker:image/nginx:alpine           [-- full image rec --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/62
mtron> *docker:container/web                [-- full container rec --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/web
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/web][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/66
mtron> *docker:image/nginx:alpine/size      [-- specific field --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/size
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/size][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/70
mtron> *docker:container/web/state          [-- container state (running, exited, ...) --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/web/state
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/web/state][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/74
```
### Graph navigation

```mtron
mtron> *docker:container/web/image           [-- uri ref to the container's image             --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/web/image
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/web/image][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/78
mtron> *docker:image/nginx:alpine/containers [-- list of container refs using this image      --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/containers
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/containers][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/82
mtron> *docker:container/web/networks        [-- uri ref to the container's network           --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/web/networks
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/web/networks][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/86
mtron> *docker:network/bridge/containers     [-- list of container refs on this network       --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:network/bridge/containers
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:network/bridge/containers][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/90
mtron> *docker:container/web/mounts          [-- list of volume refs mounted on the container --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:container/web/mounts
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:container/web/mounts][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/94
mtron> *docker:volume/data/containers        [-- list of container refs using this volume     --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:volume/data/containers
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:volume/data/containers][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/98
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
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/id
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/id][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/102
mtron> *docker:image/nginx:alpine/size        [-- mB::142.0 --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/size
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/size][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/106
mtron> *docker:image/nginx:alpine/repository  [-- nginx --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/repository
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/repository][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/110
mtron> *docker:image/nginx:alpine/tag         [-- alpine --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *docker:image/nginx:alpine/tag
   	 \_dom   │ #{?}::T
   	 \_args  │ [docker:image/nginx:alpine/tag][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/114
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
   	 \_args  │ [[driver=>local]][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/118
```
### Remove a volume

```mtron
mtron> docker:volume/myvol -> noobj
==>fail::[apply failure:
   	[lhs]    │ docker:volume/myvol
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#(noobj){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [noobj][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/122
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
   	 \_args  │ [[driver=>bridge]][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/126
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
==>fail::[apply failure:
   	[lhs]    │ docker:compose/my-stack
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([services=>[
    web=>[
     image=>'nginx:alpine',
     ports=>[<8080:80>]],
    db=>[
     image=>'postgres:16',
     environment=>[POSTGRES_PASSWORD=>secret]]]]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[services=>[
    web=>[
     image=>'nginx:alpine',
     ports=>[<8080:80>]],
    db=>[
     image=>'postgres:16',
     environment=>[POSTGRES_PASSWORD=>secret]]]]][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/130
```
Compose YAML is generated to `/tmp/metatron-docker/<name>/docker-compose.yml` and
`docker compose up -d` is executed. Progress streams through the widget.

### Stop a stack

```mtron
mtron> docker:compose/my-stack -> noobj     [-- docker compose down + cleanup --]
==>fail::[apply failure:
   	[lhs]    │ docker:compose/my-stack
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#(noobj){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [noobj][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/134
```
### Read compose config

```mtron
mtron> *docker:compose/my-stack/services/web/image    [-- nginx:alpine --]
==>'nginx:alpine'
mtron> *docker:compose/my-stack/services              [-- all services --]
==>[
    web=>[
     image=>'nginx:alpine',
     ports=>[<8080:80>]],
    db=>[
     image=>'postgres:16',
     environment=>[POSTGRES_PASSWORD=>secret]]]
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
    volumes=>['/tmp/mtron-dbs:/data']]][MTronException<137>:docker cli not found: Cannot run program "docker": error=2, No such file or directory]][docker cli not found: Cannot run program "docker": error=2, No such file or directory]@/sys/fail/138
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
==>ERROR: unable to construct tblespace::T: fail::[apply failure:
	[lhs]    │ [
 pattern=>mydb:#,
 host=><sqlite:/tmp/mtron-dbs/mydb.sqlite>,
 table=>[,],
 route=>[mydb:=><>],
 driver=><org.sqlite.JDBC>]@/sys/space/mydb
	 \_type  │ /m/rec
	  \_pred │ []
	[inst]   │ ctor?rng=tblespace&dom=#{?}([
  pattern=>mydb:#,
  host=><sqlite:/tmp/mtron-dbs/mydb.sqlite>,
  table=>[,],
  route=>[mydb:=><>],
  driver=><org.sqlite.JDBC>]@/sys/space/mydb){<j>}
	 \_dom   │ #{?}::T
	 \_args  │ [[
 pattern=>mydb:#,
 host=><sqlite:/tmp/mtron-dbs/mydb.sqlite>,
 table=>[,],
 route=>[mydb:=><>],
 driver=><org.sqlite.JDBC>]@/sys/space/mydb][DB<1182>:[SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)[DB<1182>:[SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)] ← [SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)]][[SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)[DB<1182>:[SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)]][[SQLITE_CANTOPEN] Unable to open the database file (unable to open database file)]@/sys/fail/146
```
### Step 3: Create tables and insert data

```mtron
mtron> [-- insert tble rows --]
mtron> mydb:people/1 -> [name=>'marko',role=>architect]
==>fail::[apply failure:
   	[lhs]    │ mydb:people/1
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([name=>'marko',role=>architect]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[name=>'marko',role=>architect]][MTronException<137>:no active space supports pattern mydb:people/1]][no active space supports pattern mydb:people/1]@/sys/fail/150
mtron> mydb:people/2 -> [name=>'stynx',role=>developer]
==>fail::[apply failure:
   	[lhs]    │ mydb:people/2
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([name=>'stynx',role=>developer]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[name=>'stynx',role=>developer]][MTronException<137>:no active space supports pattern mydb:people/2]][no active space supports pattern mydb:people/2]@/sys/fail/154
mtron> mydb:people/3 -> [name=>'metis',role=>oracle]
==>fail::[apply failure:
   	[lhs]    │ mydb:people/3
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#([name=>'metis',role=>oracle]){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ [[name=>'metis',role=>oracle]][MTronException<137>:no active space supports pattern mydb:people/3]][no active space supports pattern mydb:people/3]@/sys/fail/158
```
### Step 4: Query from mtron

```mtron
mtron> *mydb:people/+                                           [-- all rows              --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *mydb:people/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [mydb:people/+][MTronException<137>:no active space supports pattern mydb:people/+]][no active space supports pattern mydb:people/+]@/sys/fail/162
mtron> *mydb:people/+/                                          [-- all rows keyed by uri --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *mydb:people/+/
   	 \_dom   │ #{?}::T
   	 \_args  │ [mydb:people/+/][MTronException<137>:no active space supports pattern mydb:people/+/]][no active space supports pattern mydb:people/+/]@/sys/fail/166
mtron> *mydb:people/+/name                                      [-- all names             --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *mydb:people/+/name
   	 \_dom   │ #{?}::T
   	 \_args  │ [mydb:people/+/name][MTronException<137>:no active space supports pattern mydb:people/+/name]][no active space supports pattern mydb:people/+/name]@/sys/fail/170
mtron> *mydb:people/+.=?=[role=>developer]==[name=>_]           [-- all developer names   --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *mydb:people/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [mydb:people/+][MTronException<137>:no active space supports pattern mydb:people/+]][no active space supports pattern mydb:people/+]@/sys/fail/210
mtron> *mydb:people/+.=?=[role=>developer]==[name=>_].explain() [-- sql rewrite usage     --]
==>"""
    op      dom      rng      args               f    desc      c_dom  c_rng 
    from    #{?}::T  #{*}::T  mydb:people/+      <j>  standard  {?}    {*}   
    where   #::T     #::T     [role=>developer]  <?>  mapper    {1}    {1}   
    select  #::T     #::T     [name=>id()]       <?>  mapper    {1}    {1}   
   """
mtron> *mydb:people/+.count()                                   [-- number of rows        --]
==>1
mtron> *mydb:people/+.count().explain()                         [-- sql rewrite usage     --]
==>"""
    op     dom      rng      args           f    desc      c_dom  c_rng 
    from   #{?}::T  #{*}::T  mydb:people/+  <j>  standard  {?}    {*}   
    count  #{*}::T  int::T                  <j>  reducer   {*}    {1}   
   """
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