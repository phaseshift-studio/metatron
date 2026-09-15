---
name: web instruction set
description: |
  The `/m/web` vocabulary for exposing mtron objs over http, ws and mcp: protocol surfaces, MIME document
  types, route tables, and the conventions a mount follows.
  TRIGGER: When mounting an obj to a route, serving mtron objs over http or websockets, making an obj an MCP
  server, choosing a `?mimeq=` rendering, or wondering why a route, an image, or a css file does not serve.
---

# web instruction set (`/m/web`)

`/m/web` is the vocabulary the web carriers speak: the *types* that say what a protocol surface is, the MIME
types that say what a document is, and `route::T`, which types a mount table. The spaces themselves live
elsewhere — `/sys/space/web/http` (`httpspace`) and `/sys/space/web/ws` (`wsspace`) — each carrying a `route` rec of
mounts and reading that vocabulary.

The organizing idea is that **a protocol is a projection of an obj, not a copy of it**. An `mcp_server` rides http,
websockets and stdio alike; a str that is css is `css::T` whoever asks for it. A mount then only has to say *which
obj* and *which surface*.

## the space these examples use

A mount is only as real as the space behind it, so this document builds one first. Everything below addresses
`/data/person/1` and `/data/person/2`, and every block after this one runs against these objs. (`mtron_pre`
blocks are executed by the docs pipeline and inlined with their results; a plain `mtron` block is only shown.)

```mtron_pre
memspace::[pattern=>/data/#]@/sys/space/data
person::[name=>'marko',age=>29]@/data/person/1
person::[name=>'grant',age=>25]@/data/person/2
```

## types

| type                      | vid                                            | refines       | meaning                                                                    |
|---------------------------|------------------------------------------------|---------------|----------------------------------------------------------------------------|
| `protocol::T`             | `/m/web/protocol`                              | —             | the umbrella: **a union** of the surfaces below, so it classifies *values* |
| `http::T`                 | `/m/web/http`                                  | `protocol::T` | the http vocabulary                                                        |
| `rest::T`                 | `/m/web/http/rest`                             | `http::T`     | a REST surface — what a web root is served as                              |
| `ws::T`                   | `/m/web/ws`                                    | `protocol::T` | the websocket vocabulary                                                   |
| `mcp::T`                  | `/m/web/mcp`                                   | `protocol::T` | an MCP surface                                                             |
| `mtron::T`                | `/m/web/mtron`                                 | `protocol::T` | the mtron-eval surface                                                     |
| `stream::T`               | `/m/web/stream`                                | `protocol::T` | the raw byte / server-sent-event stream surface                            |
| `sse::T`                  | `/m/web/sse`                                   | `stream::T`   | a server-sent-events response — a chunked `text/event-stream` over http    |
| `mcp_server::T`           | `/m/web/mcp/mcp_server`                        | `mcp::T`      | an MCP server obj — **transport-agnostic**                                 |
| `web_http`                | `/m/web/http/web_http`                         | `rest::T`     | the handler that serves a web root                                         |
| `mcp_http` / `mcp_ws`     | `/m/web/mcp/mcp_http`, `/m/web/mcp/mcp_ws`     | `mcp::T`      | an `mcp_server` bound to a carrier                                         |
| `mtron_http` / `mtron_ws` | `/m/web/http/mtron_http`, `/m/web/ws/mtron_ws` | `mtron::T`    | the mtron-eval surface on each carrier                                     |
| `route::T`                | `/m/web/route`                                 | `rec::T`      | a mount table: `uri => target`                                             |

Two ways to ask "is this a protocol surface?", and they are not interchangeable:

* **a value** — `value?protocol::T` works, because `protocol::T` is a union whose predicate is applied to the
  objs on its lhs (an `mcp_server` value classifies as a protocol surface).
* **a type** — ask the *declared* parent chain: `rest::T → http::T → protocol::T` is
  true, while `rest::T?protocol::T` is **noobj**, because a type is not a protocol value.

## MIME document types

A document is typed by its MIME. Every document type under `/m/web/mime/+` refines `str::T` — `html`, `markdown`,
`java`, `yaml`, `xsv`, `csv`, `json`, `xml`, `css`. This is
a contract, not a coincidence — `fsspace::T` reads a file by typing its
bytes with the MIME, so a document type that did not accept a `str::T` would throw
on read. A MIME type is a **predicate on text**, not a structural transformation: `html::"<html>…"` is a `str::T` that
verifies as HTML -- aka "typed strings".

## binary documents are bytes

A file that is not text is read as `bytes::T`. Decoding binary through UTF-8 and re-encoding it
on the way out does not fail, it *grows*: a 1337-byte favicon was served as 2231 bytes, one replacement character per
non-ASCII byte, and a browser draws a broken image — which reads as "the image is missing" rather than as a bug. The
rule has two halves, because either alone leaves a hole:

* the read (`fsspace::T`): not text, or not valid UTF-8 at all (an unlisted extension falls back to `text/plain`), is
  `bytes`;
* the write (`MIME.MIMEType.toBytes`): bytes are written verbatim, whatever serializer the MIME would otherwise use.

## `?mimeq=` — choosing the rendering

The rendering is chosen per request with `?mimeq=<media type>`:

```mtron
/docker/image?mimeq=application/json     [-- the docker images as JSON --]
/docker/image?mimeq=text/plain           [-- the same objs, mtron-typed, as plain text --]
/docker/image                            [-- the native mtron rendering --]
```

`application/x-mtron` is the **structural parse gate**: it asks for the content parsed into mtron objs rather than
handed back as text. Read any mount's own documentation with `?docq`.

A JSON reader decides how much a JSON string is trusted. `ObjJSONSerializer.literal()` keeps every string as
written, which is what a document whose fields are all strings needs: docker reports `"Tag":"11.2"`, and reading
that as a *real* left the image addressable only as `<mariadb:11.2000>` — invisibly, because from the JSON face a
number and a string render alike. `simple()` and `web()` keep the content-sniffing behaviour their callers depend on.

## route tables

A `route` rec maps a request path to a target. Four rules:

* **keys are literal prefixes.** The carrier matches a context by literal longest prefix, so `/people/#` mounts a
  context whose subtree cannot reach it — `/people/34` is unmounted, silently, and the context is reachable only by
  a path containing a literal `#` (percent-encoded `%23`). A literal key mounts its whole subtree; that is the idiom
  every live mount uses (`/mcp`, `/docker`, `/`).
* **a prefix value is a root**, extended by the request's remaining path: `/docs => mfs:docs/website/` serves
  `/docs/x/y` from `mfs:docs/website/x/y`.
* **a templated value is the address.** It is evaluated with the request uri as its lhs, so the mount has already
  consumed the path and nothing is appended:

```mtron_pre
/person/1.map(</data/person/${name()}>)                  [-- the tail segment --]
```

```mtron_pre
/person/1.map(</data/person/${as(rec::T).>>path/2}>)     [-- the same, positionally --]
```

Both are `/data/person/1`: segment *n* is `path/(n+1)`, the path being 0-indexed with an empty leading element.
The expressions see the whole request uri, so the pattern never has to carry the capture — there is no capture
binding step, and a route is debuggable by looking at the request uri.

* **an address resolved for a request travels in the request rec** as `web_root`, and the handler is cached per (mount,
  client, resolved *target*) — never per address, since a templated mount resolves a different address per
  request while the same protocol engine serves them all.

So a mount over it:

```mtron
/person => </data/person/${name()}>
```

```mtron_pre
*</data/person/1>
```

`/person/1` serves that obj and `/person/2` serves the other, through the *same* handler — and `/person/3` is a 404,
because the space is live rather than a lookup table of two.

Only a templated value is resolved per request. Anything else — a plain uri, a code — is resolved once at mount
time, so a route value that *builds* its target (a `code` such as `*dr.as(skill::T).as(mcp_server::T)`) is not
rebuilt on every request.

## mounting an obj

```mtron
[-- an mcp_server over http: the protocol follows from the obj --]
/mcp       => mcp_mtron
[-- the same agent object over websockets --]
/drstynx   => *dr.as(skill::T).as(mcp_server::T)
[-- a web root --]
/          => mfs:docs/website/
```

A websocket has no per-request uri, only a handshake, so a templated ws mount addresses per *connection*.

## streaming a response (`sse::T`)

A mount is normally request/response — one body, then the handler closes. `sse::T` is the streaming exception: an
http handler opens a chunked `text/event-stream` response and pushes any number of events before it closes. The
primitive is `SseStream` (`isa/web/space/http/SseStream.java`), opened from an `HttpRec` subclass with `openSse()`:

```java
final SseStream sse = this.openSse();     // 200, text/event-stream, chunked (length 0)
sse.send("message", "{\"ping\":true}"); // event: message + data: {...}
sse.comment("ping");                      // : ping   (heartbeat, ignored by clients)
sse.close();                              // flush + end the stream
```

Each event is `event:`/`data:`/`:` lines terminated by a blank line; a multi-line payload becomes repeated `data:`
fields. Writes are synchronized and flushed immediately, so a producing thread (`?subq`, a future LLM token stream)
can push events across the handler thread.

The first consumer is `mcp_httpHandler.doGet` — the Streamable-HTTP GET. It opens an `sse::T` stream (only for
`Accept: text/event-stream`), drains the server's subscription outbox, and streams each
`notifications/resources/updated` as an `event: message`, then holds the stream open with heartbeats until the
client disconnects.


## not available yet

* the **surface vocabulary** — `*dr.as(mcp::T)`, `person::T.as(rest::T)` — needs `as` rows projecting an obj to a
  protocol surface; today `*dr.as(mcp::T)` fails with *"agent … is not a protocol::T@/m/web/mcp"*, which is why the
  projection chain above is spelled `.as(skill::T).as(mcp_server::T)`.
* **pattern route keys** and one route table shared by both carriers both wait on the same missing piece: a resolver
  that checks a surface against the carrier, so http cannot mount a ws handler.

## see also

* the webSpace design record at `docs/design/webspace.md` — the lattice, the route contract, and the migration
  stages in full.
* [httpPage Fetching](web_instset_mtron.md) — the client side: `http://` dereference, HTML parse trees, traversal.
* [MCP server architecture](mcp-server-architecture.md) — `mcp_server`, tools, resources, notifications.
* [type system](type-system-mtron.md) — nominal vs structural typing, and why a union classifies values.
