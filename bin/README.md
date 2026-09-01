# bin/ — connecting to metatron

A running metatron (a container, a remote host, or another instance) serves a WebSocket endpoint at
`ws://<host>:8555/mtron` — a live mtron REPL where each message is evaluated and the result returned. This file catalogs
the ways to connect to that endpoint, from the minimal REPL to the full console UI.

## The endpoints

| Endpoint                 | What it serves                                                      |
|--------------------------|---------------------------------------------------------------------|
| `ws://<host>:8555/mtron` | a mtron REPL — send an expression, get the result                   |
| `ws://<host>:8555/mcp`   | MCP over WebSocket                                                  |
| `http://<host>:8777/mcp` | MCP over HTTP (POST JSON-RPC; SSE is gone — LangChain4j dropped it) |
| `http://<host>:8777/`    | a web root (serves `boot/space/web/index.html` in the container)    |

## Ways to connect

### 1. `wsplus` — the minimal REPL (recommended for quick pokes)

A small console client in this directory. Presents a persistent `mtron> / ==>` REPL; session state carries across lines
(write `/usr/alice/x -> [1,2,3]`, then
`*/usr/alice/x/1` still sees it).

```bash
# on your host, against any running metatron:
bin/wsplus ws://localhost:8555/mtron

# inside the metatron container:
docker exec -it metatron wsplus ws://localhost:8555/mtron
```

`wsplus` is a Python script (uses the `websockets` package) with readline history

+ arrow keys on a TTY.

### 2. `remote_console.mtron` — the full Java Console, remote-driven

The complete metatron console UI (menus, widgets, etc.), running locally but redirected over `/mtron` to a
remote/headless metatron.  `*</m/web/helper.remote_console>`
wires the local console's I/O through a `wsclient`.

```bash
bin/metatron "[boot=><boot/remote_console.mtron>,host/ws=><ws://<server>:8555>,log=>info]"
```

### 3. `console::` — the full Console, local (not headless)

Mounting a console in a boot loads the full interactive console UI on that instance and blocks (keeps the process
alive — the opposite of the headless latch park):

```mtron
console::[=>]@/usr/ui/console;
```

`drstynx.boot.mtron` is an example of a local full-console boot.
`boot/spaces.boot.mtron` is the docker-only boot — mariadb/gremlin/chromadb
backends as containers via dckrspace, then this local console (docker is the
only host requirement).

### 4. ttyd / web terminal — a console in the browser (historical / future route)

An older approach wrapped `bin/metatron` in `ttyd` to expose the console as a web terminal (`metatron-server-console`).
The current ttyd-era launcher was removed; if the web-console route returns, it should be a "server loads the console
and idles, clients connect for a full remote Console" model rather than ttyd.

```bash
if [ $# -eq 0 ]; then
  set -- "[host=><ws://0.0.0.0:8999>,boot=><boot/boot.mtron>,log=>info]"
docker pull docker.phaseshift.studio/metatron:0.1-SNAPSHOT
lsof -ti:8999 | xargs kill
ttyd --credential metatron:nortatem --writable --port 8111 bin/metatron "$@"
```

---

## Summary

| Route                  | Client                         | Server-side                            |
|------------------------|--------------------------------|----------------------------------------|
| `wsplus`               | minimal REPL (`mtron> / ==>`)  | headless `/mtron` endpoint             |
| `remote_console.mtron` | full Console UI, remote-driven | headless `/mtron` endpoint             |
| `console::`            | full Console UI, local         | boots with a console (not headless)    |
| ttyd / web             | browser terminal               | historical — removed; future model TBD |
