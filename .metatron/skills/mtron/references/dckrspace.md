---
name: dckrspace
description: >
  Docker management through dckrSpace — pull, run, stop containers; manage images, volumes,
  networks; docker-compose up/down; navigate container↔resource graph links. All from mtron.
  TRIGGER: When working with Docker in metatron, dckrSpace, dockerspace::, docker: URIs,
  container run/stop, image pull, volume or network management, docker-compose in mtron.
---

# dckrSpace — Docker in mtron

## Architecture

`dckrSpace` bridges a Docker daemon into metatron's URI space. Every Docker resource
(container, image, volume, network) is a first-class Obj addressable by URI. Writes
trigger Docker CLI operations; reads refresh state from the daemon.

The space maintains a bidirectional graph: containers link to their images, networks, and
volumes, and each resource links back to its containers.

```
┌──────────────────────────────────────────────────────┐
│                  dckrSpace                            │
│                                                       │
│  docker:image/nginx:alpine ──── containers ──► [ref] │
│       ▲                                            │  │
│       │ image                                      │  │
│       │                                            ▼  │
│  docker:container/web ──── networks ──► docker:network/bridge
│       │                                            ▲  │
│       │ mounts                                     │  │
│       ▼                                            │  │
│  docker:volume/data ───── containers ─────► [ref]  │
│                                                       │
│  docker:compose/stack ─── docker-compose.yml          │
└──────────────────────────────────────────────────────┘
```

## Boot Configuration

```mtron
dockerspace::[pattern   => docker:#,
              route     => [docker: => <>],
              host      => <tcp://192.168.1.100:2375>,   -- optional remote host
              progress  => progress::[=>]]@/sys/space/docker
```

| Field | Required | Description |
|-------|----------|-------------|
| `pattern` | yes | URI prefix for this space |
| `route` | yes | Maps pattern prefix into space |
| `host` | no | Remote Docker daemon (`tcp://`, `unix://`). Omit for local socket. |
| `progress` | no | `ProgressTableWidget` for live pull-progress display |

## URI Address Space

```
docker:image/<repository:tag>        -- image by repo:tag
docker:image/<hash>                  -- image by Docker hash (if available)
docker:container/<name>              -- container by name
docker:volume/<name>                 -- volume by name
docker:network/<name>                -- network by name
docker:compose/<stack-name>          -- compose config/stack
```

## Reading

### List all resources

```mtron
*docker:image/+                      -- all images (keyed by repository:tag)
*docker:container/+                  -- all containers
*docker:volume/+                     -- all volumes
*docker:network/+                    -- all networks
```

### Inspect a resource

```mtron
*docker:image/nginx:alpine           -- full image rec
*docker:container/web                -- full container rec
*docker:image/nginx:alpine/size      -- specific field
*docker:container/web/state          -- container state (running, exited, ...)
```

### Graph navigation

```mtron
*docker:container/web/image           -- URI ref to the container's image
*docker:image/nginx:alpine/containers -- list of container refs using this image
*docker:container/web/networks        -- URI ref to the container's network
*docker:network/bridge/containers     -- list of container refs on this network
*docker:container/web/mounts          -- list of volume refs mounted on the container
*docker:volume/data/containers        -- list of container refs using this volume
```

The `image`, `networks`, and `mounts` fields on containers are **URI refs** (not raw
Docker strings). The `containers` field on images/networks/volumes is a **lst of URI
refs** (not Docker's integer count).

## Writing — Containers

### Run a container

```mtron
docker:container/web -> [
  image       => 'nginx:alpine',
  ports       => [<8080:80>, <443:443>],
  environment => [NGINX_HOST => example.com],
  volumes     => ['myvol:/usr/share/nginx/html'],
  network     => mynet
]
```

> **Important**: Always quote the image value (`'nginx:alpine'`). Bare
> `nginx:alpine` parses as a URI whose `name()` drops the `nginx/` prefix.

Image pull happens automatically via `docker run`. Pull progress streams through
the `ProgressTableWidget` if configured.

| Field | Type | Docker flag | Example |
|-------|------|-------------|---------|
| `image` | str (required) | positional | `'nginx:alpine'` |
| `ports` | lst of str | `-p` each | `[<8080:80>]` |
| `environment` | rec | `-e KEY=VALUE` | `[FOO => bar]` |
| `volumes` | lst of str | `-v` each | `['data:/data']` |
| `network` | str | `--network` | `mynet` |

### Stop and remove a container

```mtron
docker:container/web -> {0}id()       -- writes noobj → docker stop + rm
```

## Writing — Images

Images are read-only from Docker Hub. The space auto-discovers images from
`docker image ls` and from containers' image references.

```mtron
*docker:image/nginx:alpine/id          -- Docker hash (f7949ff70415)
*docker:image/nginx:alpine/size        -- mB::142.0
*docker:image/nginx:alpine/repository  -- nginx
*docker:image/nginx:alpine/tag         -- alpine
```

## Writing — Volumes

### Create a volume

```mtron
docker:volume/myvol -> [driver => local]
```

### Remove a volume

```mtron
docker:volume/myvol -> {0}id()
```

## Writing — Networks

### Create a network

```mtron
docker:network/mynet -> [driver => bridge]
```

### Remove a network

```mtron
docker:network/mynet -> {0}id()
```

## Docker Compose

### Start a stack

```mtron
docker:compose/my-stack -> [
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
```

Compose YAML is generated to `/tmp/metatron-docker/<name>/docker-compose.yml` and
`docker compose up -d` is executed. Progress streams through the widget.

### Stop a stack

```mtron
docker:compose/my-stack -> {0}id()     -- docker compose down + cleanup
```

### Read compose config

```mtron
*docker:compose/my-stack/services/web/image   -- nginx:alpine
*docker:compose/my-stack/services              -- all services
```

## Remote Docker Hosts

Connect to a remote Docker daemon by specifying `host` in the boot config:

```mtron
dockerspace::[host => <tcp://192.168.1.100:2375>, ...]@/sys/space/remote
```

All Docker CLI commands are prefixed with `-H <host>`. Supports `tcp://`, `unix://`,
and `ssh://` schemes.

## End-to-End: SQLite Container + tbleSpace

This example pulls a SQLite Docker image, runs it with a bind-mounted data directory,
and exposes the database through tbleSpace — all from mtron.

```
┌─────────────────────────────────────────────────────────┐
│  Host filesystem                                         │
│  /tmp/mtron-dbs/                                         │
│  └── mydb.sqlite    ◄── tbleSpace (JDBC)                 │
│       ▲                                                  │
│       │ bind mount                                       │
│       │                                                  │
│  ┌────┴──────────────┐                                   │
│  │  Docker container  │                                  │
│  │  keinos/sqlite3    │                                  │
│  │  /data/            │                                  │
│  │  └── mydb.sqlite   │                                  │
│  └───────────────────┘                                   │
└─────────────────────────────────────────────────────────┘
```

### Step 1: Pull + run the SQLite container

```mtron
docker:container/sqlite -> [
  image   => 'keinos/sqlite3:latest',
  volumes => ['/tmp/mtron-dbs:/data']
]
```

The bind mount `'/tmp/mtron-dbs:/data'` maps the host directory into the container.
Pull progress streams through the `ProgressTableWidget` if configured.

### Step 2: Mount the database via tbleSpace

```mtron
tblespace::[pattern => mydb:#,
            host    => <sqlite:/tmp/mtron-dbs/mydb.sqlite>,
            table   => [,],
            route   => [mydb: => <>],
            driver  => <org.sqlite.JDBC>]@/sys/space/mydb
```

The `.sqlite` file doesn't need to exist yet — the SQLite JDBC driver creates it on
first connection. `table => [,]` enables `createTableFromRecord` so tables are
auto-created from the schema of the first record written.

### Step 3: Create tables and insert data

```mtron
# Create a people table. The first write defines the schema.
mydb:people -> [id => jnt::T, name => str::T, role => str::T]

# Insert records
mydb:people -> [id => 1, name => 'Marko',  role => 'architect']
mydb:people -> [id => 2, name => 'Stynx',  role => 'developer']
mydb:people -> [id => 3, name => 'Metis',  role => 'oracle']
```

### Step 4: Query from mtron

```mtron
*mydb:people/+                        -- all records
*mydb:people/+/name                   -- all names
*mydb:people/+.where(role='developer') -- filtered
*mydb:people/+.count()                -- 3
```

### Step 5: The container sees the same data

The Docker container has the database mounted at `/data/mydb.sqlite`. Any process
inside the container can read and write the same file. mtron-backed writes go through
tbleSpace → JDBC → the file → visible inside the container. Container writes go to
the file → visible to tbleSpace on next read.

### Step 6: Tear down

```mtron
docker:container/sqlite -> {0}id()     -- stop + remove container
# Database file persists at /tmp/mtron-dbs/mydb.sqlite
# Re-mount tbleSpace later to pick up where you left off
```

### How it works

| Layer | Technology | Role |
|-------|-----------|------|
| dckrSpace | Docker CLI | Pulls image, runs container, bind-mounts volume |
| tbleSpace | SQLite JDBC | Auto-creates `.sqlite` file, creates tables from record schemas, executes queries |
| Bind mount | Linux kernel | Shares filesystem between host and container |
| mtron | URI graph | Uniform `mydb:people -> [...]` write / `*mydb:people/+` read across both spaces |

The key insight: **dckrSpace manages the container lifecycle; tbleSpace manages the
data**. They meet at the bind-mounted directory. No `docker exec`, no separate SQL
client, no out-of-band setup — the database is born from a mtron write.

## Quick Reference

| Task | Expression |
|------|------------|
| Mount space | `dockerspace::[pattern=>docker:#,route=>[docker:=>...]]@/sys/space/docker` |
| Run container | `docker:container/<name> -> [image => 'img:tag']` |
| Run with ports | `docker:container/<name> -> [image => 'img', ports => [<8080:80>]]` |
| Run with env | `[image => 'img', environment => [KEY => val]]` |
| Run with volume | `[image => 'img', volumes => ['vol:/path']]` |
| Run with network | `[image => 'img', network => netname]` |
| Stop container | `docker:container/<name> -> {0}id()` |
| List images | `*docker:image/+` |
| List containers | `*docker:container/+` |
| Inspect image | `*docker:image/repo:tag` |
| Inspect container | `*docker:container/<name>` |
| Container's image ref | `*docker:container/<name>/image` |
| Image's containers | `*docker:image/repo:tag/containers` |
| Container's network | `*docker:container/<name>/networks` |
| Network's containers | `*docker:network/<name>/containers` |
| Container's volumes | `*docker:container/<name>/mounts` |
| Volume's containers | `*docker:volume/<name>/containers` |
| Create volume | `docker:volume/<name> -> [driver => local]` |
| Remove volume | `docker:volume/<name> -> {0}id()` |
| Create network | `docker:network/<name> -> [driver => bridge]` |
| Remove network | `docker:network/<name> -> {0}id()` |
| Start compose stack | `docker:compose/<name> -> [services => [...]]` |
| Stop compose stack | `docker:compose/<name> -> {0}id()` |
| Remote host | Add `host => <tcp://host:port>` to space config |
