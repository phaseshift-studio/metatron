---
name: dckrspace
description: |
  manipulating docker's graph of images, containers, volumes, networks, and the lifecycle of what it creates
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
==>docs::[obj=>space::T[?[{?}host=>uri::T,{?}progress=>rec::T]][ctor?dckrspace<=#{?}(dckrspace::T){<j>}]@/m/dckr/space/dckrspace,
          desc=>'[structural] a docker daemon space',
          example=>['docker:compose/my-stack -> [servic...',
                    '*docker:container/+',
                    '*docker:image/nginx/<nginx:latest>']]
```
```mtron
dckrspace::[pattern   => docker:#,
            route     => [docker: => <>],
            host      => <tcp://192.168.1.100:2375>,          [-- optional remote host --]
            owner     => /usr/avatar,                          [-- optional: who owns what this space creates --]
            on_close  => docker:compose/session-db -> noobj,   [-- optional: run when the space closes --]
            progress  => progress_table::[=>]]@/sys/space/docker   [-- console only: see Gotchas --]
```

```mtron
mtron> dckrspace::[pattern   => docker:#,
                   route     => [docker: => <>],
                   progress  => progress_table::[=>]]@/sys/space/docker
```
| Field     | Required | Description                                                                                        |
|-----------|----------|----------------------------------------------------------------------------------------------------|
| `pattern` | yes      | URI prefix for this space                                                                          |
| `route`   | yes      | Maps pattern prefix into space                                                                     |
| `host`    | no       | Remote Docker daemon (`tcp://`, `unix://`, `ssh://`). Omit for the local socket.                   |
| `owner`   | no       | The scope that owns what this space creates — the docker label plus the `owner` field (see below). |
| `on_close`| no       | An instruction applied when the space closes (see Ownership & Reaping).                            |
| `progress`| no       | `progress_table::T` for live pull progress — **console only**; omit it headless (see Gotchas).     |

## Ownership & Reaping

`dckrspace::T` provisions on demand — `from(docker:container/x).else(… up …)` — which raises a question the daemon can
answer but the VM's cache cannot: **what did this space create, and is its creator still alive?**

Two config fields answer it.

`owner` names the scope that owns everything this space creates. It is stamped on the objects themselves as the docker
label `com.phaseshift.metatron.owner=<owner>`:

* a container run (`docker:container/<name> -> […]`) gets `--label com.phaseshift.metatron.owner=<owner>`;
* a compose stack (`docker:compose/<stack> -> [services => […]]`) gets it in the `labels:` of **every** service, in a
  copy of the config — the rec you wrote is never mutated.

The label then travels with the objects (it outlives the VM), and every container rec carries it as a first-class
`owner` field, so "what belongs to me?" is a read:

```mtron
*docker:container/+.?[owner=></usr/avatar>]      [-- everything this owner created --]
```

`on_close` is the other half: an instruction applied when the space closes. The web/ws/http spaces already take
lifecycle hooks this way (`on_open`, `on_message`, `on_error`); `on_close` gives *any* space an mtron-controllable
shutdown hook, which is what makes reaping declarative rather than hard-coded in Java.

```mtron
[-- a space that reaps what it owns when it goes away --]
dckrspace::[pattern  => docker:#,
            route    => [docker: => <>],
            owner    => /usr/avatar,
            on_close => *docker:container/+.?[owner=></usr/avatar>] -> noobj]@/sys/space/docker
```

**When it runs.** `close()` is the space's farewell, and it is called wherever a space is released:
`Machine.close()` closes a level's memory (so a frame's spaces go with it), `Machine.pop()` gives a frame back, and the
shutdown hook closes the root. That is exactly the scoping a reaper wants — *what a frame created, that frame's close
collects* — and it is the same lifetime model as a forked peer (`PeerSpawn`), applied to containers.

**Order.** `on_close` is applied *before* the space is released, so the instruction can still route through the space
it is closing (`sjvm` is closed after it, and unregistration after that).

**Reap by name, or by owner.** A stack the boot knows by name is the simplest — and this is the form the test suite
exercises against a live daemon:

```mtron
on_close => docker:compose/session-db -> noobj
```

The owner query is for the general case: select what the label marks, then force each match to `noobj`
(`x -> noobj` is the write that stops and removes a container). Note that a failing `on_close` should be treated as a
warning, not a reason to abandon the rest of a shutdown.


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
configured (**console only** — see Gotchas). When the space declares an `owner`, the container is also labelled
`com.phaseshift.metatron.owner=<owner>` on the way up, which is what makes it reapable later (see Ownership & Reaping).

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
`docker compose up -d --wait --timeout 600` is executed, so the write returns once the stack reports healthy (per
service healthchecks) — a boot can therefore bind a space that must be accepting connections immediately after the
`up` returns. Progress streams through the widget when there is a console.

Two things about that file and those names are worth knowing before you guard a stack with `from(…).else(…)`:

* **The file is ephemeral.** It lives in `/tmp/metatron-docker`, which does not survive a VM restart — nor a container
  recreate, where `/tmp` belongs to the container. `-> noobj` therefore tears a stack down in two ways: `docker
  compose -p <name> down` when the file is still there, and otherwise a sweep of the objects compose labelled
  `com.docker.compose.project=<name>` (containers removed, then the network). Volumes are kept either way, matching
  `down` without `-v` — a stack's data outlives its containers.
* **Naming, and why it matters for a guard.** A **single-service** stack's container is named exactly the stack
  (`container_name: <stack>`), so a guard written as `from(docker:container/<stack>).else(… up …)` adopts it on the
  next boot instead of re-running the `up`. A multi-service stack keeps compose's default naming
  (`<stack>-<service>-1`) — there is no single name for the guard to ask for — and a `container_name` you set in the
  config yourself wins either way. Without that parity the guard never matches the stack it just created: **every**
  boot re-runs the `up`, and once the config hash drifts that becomes a **Recreate** — the slow path, and the one a
  second concurrent compose run on the same project will deadlock against.

Each service takes docker compose's own fields — `labels` (a map), `healthcheck`, `restart`, `volumes`, `command`, …
— and when the space declares an `owner` its label is added to every service's `labels`.

### Stop a stack

```mtron
docker:compose/my-stack -> noobj     [-- compose down if the file is there, label sweep if it is not --]
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

## Gotchas

* **Reads are read-through.** A read refreshes from the daemon before it answers — images, networks, volumes and
  containers, up to four `docker … ls --format json` calls. That is what keeps `*docker:container/+` honest with no
  watcher, and it is also why a read is not free (a remote `host` pays for the round trip each time).
* **Reading over a console needs the mtron mime type.** The `/mtron` websocket defaults to JSON out, which cannot
  represent the richer objs here (`datetime::`, unit types like `bB::0.0`, uri-valued keys, `!*` auto-refs) and answers
  them blank. Use `ws://<host>:8555/mtron?out=application/x-mtron` (or the MCP eval tool's native flag) before
  concluding that a read found nothing.
* **A path handed to the daemon is a HOST path.** `volumes => ['/data:/db']` is resolved by the daemon, not by the VM;
  when the VM itself runs in a container (docker-outside-of-docker) it will not see `/data` unless the same absolute
  path is mounted into it too.
* **`progress` wants a terminal.** A `progress_table::T` in the config of a space with no console was observed to leave
  its reads returning `noobj`; omit `progress` on a headless server.
* **Ordering and teardown.** A container rec's `image`, `networks` and `mounts` are lazy `!*` refs (resolve on access);
  `labels` is a rec keyed by label name; and a stack's teardown depends on a file under `/tmp` (see Docker Compose).
* **Implementation note (maintainers).** A docker command is a child process, and a child whose stderr stops being
  read blocks on a full pipe with the caller waiting forever. `exec` drives zt-exec's `ProcessExecutor` with
  `readOutput(true)` and `redirectError(…)` so both streams are always consumed, and it touches no UI — a hand-rolled
  `ProcessBuilder` drain thread, or rendering a widget from one, reintroduces that deadlock.

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
mtron> *mydb:people/+.?[role=>developer]==[name=>_]           [-- all developer names   --]
==>[name=>'stynx']
mtron> *mydb:people/+.?[role=>developer]==[name=>_].explain() [-- sql rewrite usage     --]
==>explanation::[format=>!inst?str<=#{?}(){<j>},
                 desc=>[rng=><#>,
                        insts=>2,
                        rewrite=>explanation::[
                         format=>!inst?str<=#{?}(){<j>},
                         desc=>[rng=><#>,insts=>3],
                         per_inst=>[[op=>from,
                                     args=>[mydb:people/+],
                                     f=>'<j>',
                                     form=>standard,
                                     c_dom=>0,
                                     c_rng=>0],
                                    [op=>isa,
                                     dom=><#>,
                                     rng=><#>,
                                     args=>[[role=>developer]],
                                     form=>mapper,
                                     c_dom=>1,
                                     c_rng=>1],
                                    [op=>select,
                                     dom=><#>,
                                     rng=><#>,
                                     args=>[[name=>id()]],
                                     form=>mapper,
                                     c_dom=>1,
                                     c_rng=>1]]]],
                 per_inst=>[[op=>sql_where,
                             args=>[people/+,"role = 'developer'"],
                             f=>'<j>',
                             form=>standard,
                             c_dom=>0,
                             c_rng=>0],
                            [op=>select,
                             dom=><#>,
                             rng=><#>,
                             args=>[[name=>id()]],
                             form=>mapper,
                             c_dom=>1,
                             c_rng=>1]]]
mtron> *mydb:people/+.count()                                   [-- number of rows        --]
==>3
mtron> *mydb:people/+.count().explain()                         [-- sql rewrite usage     --]
==>explanation::[format=>!inst?str<=#{?}(){<j>},
                 desc=>[rng=>int,
                        insts=>1,
                        rewrite=>explanation::[
                         format=>!inst?str<=#{?}(){<j>},
                         desc=>[rng=>int,insts=>2],
                         per_inst=>[[op=>from,
                                     args=>[mydb:people/+],
                                     f=>'<j>',
                                     form=>standard,
                                     c_dom=>0,
                                     c_rng=>0],
                                    [op=>count,
                                     rng=>int,
                                     args=>[,],
                                     f=>'<j>',
                                     form=>reducer,
                                     c_dom=>0,
                                     c_rng=>1]]]],
                 per_inst=>[[op=>sql_count,
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
| Own what you create   | Add `owner => /usr/avatar` to the space config (label `com.phaseshift.metatron.owner`) |
| What an owner created | `*docker:container/+.?[owner=></usr/avatar>]`                              |
| Reap on close         | Add `on_close => docker:compose/<name> -> noobj` to the space config       |
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
| Stack container name  | `<name>` for a single-service stack, so its own guard can adopt it         |
| Teardown without yaml | Automatic: the sweep by `com.docker.compose.project` label                |
| Remote host           | Add `host => <tcp://host:port>` to space config                            |