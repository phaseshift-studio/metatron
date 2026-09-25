---
name: web instruction set
description: |
  The `/m/web` vocabulary for exposing mtron objs over http, ws and mcp: protocol surfaces, MIME document
  types, route tables, the `mtron::T` apply protocol, and the conventions a mount follows.
  TRIGGER: When mounting an obj to a route, serving mtron objs over http or websockets, making an obj an MCP
  server, shipping an obj for remote evaluation (`mtron::T`), choosing a `?mimeq=` rendering, or wondering why
  a route, an image, or a css file does not serve.
---

# web instruction set (`/m/web`)

`/m/web` is the instruction set has web *transport protocol* types, *MIME*
types, and *endpoint* types to provide server logic. The two spaces of `/m/web` are
`httpspace` and `wsspace` — each carrying a `route::T` rec for routing connections to mounted services.

```mtron
mtron> httpspace::[pattern=> http://#,
                   host   => http://localhost:8777,
                   route  => [/mcp       => mcp_mtron,
                              /docker    => docker:,
                              /usr       => /usr,
                              /mfs       => mfs:,
                              /          => mfs:docs/website/]]@/sys/space/web/http
==>fail::[unable to construct httpspace::T: fail::[inst apply failure: Address already in use [Net<-2>] (at /m/inst/ctor) [Net<-2>]]@/sys/fail/2888
   	while parsing: httpspace::[pattern=> http://#,
                      host   => http://localhost:8777,
                      route  => [/mcp       => mcp_mtron,
                                 /docker    => docker:,
                                 /usr       => /usr,
                                 /mfs       => mfs:,
                                 /          => mfs:docs/website/]]@/sys/space/web/http]@/sys/fail/2890
mtron> wsspace::[pattern=> ws:#,
                 host   => ws://localhost:8555
                 route  => [/drstynx   => *dr.as(skill::T).as(mcp_server::T)]]@/sys/space/web/ws
==>ERROR: infinite recursion detected in parser: parser consumed 0 characters at 'wsspace::[pattern=> ws:#,
                 host   => ws://localhost:8555
                 route  => [/drstynx   => *dr.as(skill::T).as(mcp_server::T)]]@/sys/space/web/ws'
```
The organizing idea is that **a protocol is a projection of an obj, not a copy of it**. An `mcp_server` rides http,
websockets and stdio alike; a `str` that is css is `css::T` whoever asks for it. A mount then only has to say which
obj and which encoding. Below is metatron accessing this document.

```mtron
mtron> *<http://metatron.phaseshift.studio/skills/mtron/references/web-instset-mtron.md>
==>markdown::"""---
   name: web instruction set
   description: |
     The `/m/web` vocabulary for exposing mtron objs over http, ws and mcp: protocol surfaces, MIME document
     types, route tables, the `mtron::T` apply protocol, and the conventions a mount follows.
     TRIGGER: When mounting an obj to a route, serving mtron objs over http or websockets, making an obj an MCP
     server, shipping an obj for remote evaluation (`mtron::T`), choosing a `?mimeq=` rendering, or wondering why
     a route, an image, or a css file does not serve.
   ---
   
   ...
mtron> *<http://metatron.phaseshift.studio/skills/mtron/references/web-instset-mtron.md>.as(html::T)
==>html::"""<h1>web instruction set (<code>/m/web</code>)</h1>
   <p><code>/m/web</code> is the vocabulary the web carriers speak: the <em>types</em> that say what a protocol surface is, the MIME
   types that say what a document is, and <code>route::T</code>, which types a mount table. The spaces themselves live
   elsewhere — <code>/sys/space/web/http</code> (<code>httpspace</code>) and <code>/sys/space/web/ws</code> (<code>wsspace</code>) — each carrying a <code>route</code> rec of
   mounts and reading that vocabulary.</p>
   <p>The organizing idea is that <strong>a protocol is a projection of an obj, not a copy of it</strong>. An <code>mcp_server</code> rides http,
   websockets and stdio alike; a str that is css is <code>css::T</code> whoever asks for it. A mount then only has to say <em>which
   obj</em> and <em>which surface</em>.</p>
   <h2>the space these examples use</h2>
   <p>A mount is only as real as the space behind it, so this document builds one first. Everything below addresses
   ...
mtron> *<http://metatron.phaseshift.studio/skills/mtron/references/web-instset-mtron.md>.as(html::T).as(rec::T)
==>[html=>[head=>[=>],body=>[out=>[[tag=>h1,out=>[[tag=>code,text=>'/m/web']],text=>'web instruction set ()'],[tag=>p,out=>[[tag=>code,text=>'/m/web'],[tag=>em,text=>'types'],[tag=>code,text=>'route::T'],[tag=>code,text=>'/sys/space/web/http'],[tag=>code,text=>'httpspace'],[tag=>code,text=>'/sys/space/web/ws'],[tag=>code,text=>'wsspace'],[tag=>code,text=>'route']],text=>'is the vocabulary the web carriers speak: the that say what a protocol surface is, the MIME types that say what a document is, and , which types a mount table. The spaces themselves live elsewhere — () and () — each carrying a rec of mounts and reading that vocabulary.'],[tag=>p,out=>[[tag=>strong,text=>'a protocol is a projection of an obj, not a copy of it'],[tag=>code,text=>'mcp_server'],[tag=>code,text=>'css::T'],[tag=>em,text=>'which obj'],[tag=>em,text=>'which surface']],text=>'The organizing idea is that . An rides http, websockets and stdio alike; a str that is css is whoever asks for it. A mount then only has to say and .'],[tag=>h2,text=>'the space these examples use'],[tag=>p,out=>[[tag=>code,text=>'/data/person/1'],[tag=>code,text=>'/data/person/2'],[tag=>code,text=>'mtron_pre'],[tag=>code,text=>'mtron']],text=>'A mount is only as real as the space behind it, so this document builds one first. Everything below addresses and , and every block after this one runs against these objs. ( blocks are executed by the docs pipeline and inlined with their results; a plain block is only shown.)'],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>"""mtron> memspace::[pattern=>/data/#]@/sys/space/data
   ==>memspace::[pattern=>/data/#]@/sys/space/data
   mtron> person::[name=>'marko',age=>29]@/data/person/1
   ==>person::[name=>'marko',age=>29]@/data/person/1
   mtron> person::[name=>'grant',age=>25]@/data/person/2
   ==>person::[name=>'grant',age=>25]@/data/person/2"""]]],[tag=>h2,text=>'types'],[tag=>table,out=>[[tag=>thead,out=>[[tag=>tr,out=>[[tag=>th,text=>'type'],[tag=>th,text=>'vid'],[tag=>th,text=>'refines'],[tag=>th,text=>'meaning']]]]],[tag=>tbody,out=>[[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/protocol']]],[tag=>td,text=>'—'],[tag=>td,out=>[[tag=>strong,text=>'a union'],[tag=>em,text=>'values']],text=>'the umbrella: of the surfaces below, so it classifies']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'http::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/http']]],[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,text=>'the http vocabulary']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'rest::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/http/rest']]],[tag=>td,out=>[[tag=>code,text=>'http::T']]],[tag=>td,text=>'a REST surface — get reads, put replaces, patch updates, delete unlinks, post creates']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'ws::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/ws']]],[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,text=>'the websocket vocabulary']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'mcp::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/mcp']]],[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,text=>'an MCP surface']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'mtron::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/mtron']]],[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,text=>'the mtron-eval surface — ship an obj, it is applied, the result returns']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'stream::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/stream']]],[tag=>td,out=>[[tag=>code,text=>'protocol::T']]],[tag=>td,text=>'the raw byte / server-sent-event stream surface']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'sse::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/sse']]],[tag=>td,out=>[[tag=>code,text=>'stream::T']]],[tag=>td,out=>[[tag=>code,text=>'text/event-stream']],text=>'a server-sent-events response — a chunked over http']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'mcp_server::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/mcp/mcp_server']]],[tag=>td,out=>[[tag=>code,text=>'mcp::T']]],[tag=>td,out=>[[tag=>strong,text=>'transport-agnostic']],text=>'an MCP server obj —']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'web_http']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/http/web_http']]],[tag=>td,out=>[[tag=>code,text=>'rest::T']]],[tag=>td,text=>'the handler that serves a web root']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'mcp_http'],[tag=>code,text=>'mcp_ws']],text=>'/'],[tag=>td,out=>[[tag=>code,text=>'/m/web/mcp/mcp_http'],[tag=>code,text=>'/m/web/mcp/mcp_ws']],text=>','],[tag=>td,out=>[[tag=>code,text=>'mcp::T']]],[tag=>td,out=>[[tag=>code,text=>'mcp_server']],text=>'an bound to a carrier']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'mtron_http'],[tag=>code,text=>'mtron_ws']],text=>'/'],[tag=>td,out=>[[tag=>code,text=>'/m/web/http/mtron_http'],[tag=>code,text=>'/m/web/ws/mtron_ws']],text=>','],[tag=>td,out=>[[tag=>code,text=>'mtron::T']]],[tag=>td,text=>'the mtron-eval surface on each carrier']]],[tag=>tr,out=>[[tag=>td,out=>[[tag=>code,text=>'route::T']]],[tag=>td,out=>[[tag=>code,text=>'/m/web/route']]],[tag=>td,out=>[[tag=>code,text=>'rec::T']]],[tag=>td,out=>[[tag=>code,text=>'uri => target']],text=>'a mount table:']]]]]]],[tag=>p,text=>'Two ways to ask "is this a protocol surface?", and they are not interchangeable:'],[tag=>ul,out=>[[tag=>li,out=>[[tag=>strong,text=>'a value'],[tag=>code,text=>'value?protocol::T'],[tag=>code,text=>'protocol::T'],[tag=>code,text=>'mcp_server']],text=>'— works, because is a union whose predicate is applied to the objs on its lhs (an value classifies as a protocol surface).'],[tag=>li,out=>[[tag=>strong,text=>'a type'],[tag=>em,text=>'declared'],[tag=>code,text=>'rest::T → http::T → protocol::T'],[tag=>code,text=>'rest::T?protocol::T'],[tag=>strong,text=>'noobj']],text=>'— ask the parent chain: is true, while is , because a type is not a protocol value.']]],[tag=>h2,text=>'MIME document types'],[tag=>p,out=>[[tag=>code,text=>'/m/web/mime/+'],[tag=>code,text=>'str::T'],[tag=>code,text=>'html'],[tag=>code,text=>'markdown'],[tag=>code,text=>'java'],[tag=>code,text=>'yaml'],[tag=>code,text=>'xsv'],[tag=>code,text=>'csv'],[tag=>code,text=>'json'],[tag=>code,text=>'xml'],[tag=>code,text=>'css'],[tag=>code,text=>'fsspace::T'],[tag=>code,text=>'str::T'],[tag=>strong,text=>'predicate on text'],[tag=>code,text=>'html::"<html>…"'],[tag=>code,text=>'str::T']],text=>'A document is typed by its MIME. Every document type under refines — , , , , , , , , . This is a contract, not a coincidence — reads a file by typing its bytes with the MIME, so a document type that did not accept a would throw on read. A MIME type is a , not a structural transformation: is a that verifies as HTML -- aka "typed strings".'],[tag=>h2,text=>'binary documents are bytes'],[tag=>p,out=>[[tag=>code,text=>'bytes::T'],[tag=>em,text=>'grows']],text=>'A file that is not text is read as . Decoding binary through UTF-8 and re-encoding it on the way out does not fail, it : a 1337-byte favicon was served as 2231 bytes, one replacement character per non-ASCII byte, and a browser draws a broken image — which reads as "the image is missing" rather than as a bug. The rule has two halves, because either alone leaves a hole:'],[tag=>ul,out=>[[tag=>li,out=>[[tag=>code,text=>'fsspace::T'],[tag=>code,text=>'text/plain'],[tag=>code,text=>'bytes']],text=>'the read (): not text, or not valid UTF-8 at all (an unlisted extension falls back to ), is ;'],[tag=>li,out=>[[tag=>code,text=>'MIME.MIMEType.toBytes']],text=>'the write (): bytes are written verbatim, whatever serializer the MIME would otherwise use.']]],[tag=>h2,out=>[[tag=>code,text=>'?mimeq=']],text=>'— choosing the rendering'],[tag=>p,out=>[[tag=>code,text=>'?mimeq=<media type>']],text=>'The rendering is chosen per request with :'],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>"""/docker/image?mimeq=application/json     [-- the docker images as JSON --]
   /docker/image?mimeq=text/plain           [-- the same objs, mtron-typed, as plain text --]
   /docker/image                            [-- the native mtron rendering --]"""]]],[tag=>p,out=>[[tag=>code,text=>'application/x-mtron'],[tag=>strong,text=>'structural parse gate'],[tag=>code,text=>'?docq']],text=>"is the : it asks for the content parsed into mtron objs rather than handed back as text. Read any mount's own documentation with ."],[tag=>p,out=>[[tag=>code,text=>'q => [mimeq::[pattern=>mimeq, post_read=>inst?#{*}<=#{?}(uri::T,#::T)]]'],[tag=>code,text=>'post_read'],[tag=>em,text=>'after'],[tag=>em,text=>'before'],[tag=>code,text=>'?mimeq=']],text=>'The mechanism is a q-proc: the live carrier declares — a inst that runs the read and serialization. is a server-side as-transform.'],[tag=>p,out=>[[tag=>code,text=>'ObjJSONSerializer.literal()'],[tag=>code,text=>'"Tag":"11.2"'],[tag=>em,text=>'real'],[tag=>code,text=>'<mariadb:11.2000>'],[tag=>code,text=>'simple()'],[tag=>code,text=>'web()']],text=>'A JSON reader decides how much a JSON string is trusted. keeps every string as written, which is what a document whose fields are all strings needs: docker reports , and reading that as a left the image addressable only as — invisibly, because from the JSON face a number and a string render alike. and keep the content-sniffing behaviour their callers depend on.'],[tag=>h2,text=>'route tables'],[tag=>p,out=>[[tag=>code,text=>'route']],text=>'A rec maps a request path to a target. Four rules:'],[tag=>ul,out=>[[tag=>li,out=>[[tag=>strong,text=>'keys are literal prefixes.'],[tag=>code,text=>'/people/#'],[tag=>code,text=>'/people/34'],[tag=>code,text=>'#'],[tag=>code,text=>'%23'],[tag=>code,text=>'/mcp'],[tag=>code,text=>'/docker'],[tag=>code,text=>'/']],text=>'The carrier matches a context by literal longest prefix, so mounts a context whose subtree cannot reach it — is unmounted, silently, and the context is reachable only by a path containing a literal (percent-encoded ). A literal key mounts its whole subtree; that is the idiom every live mount uses (, , ).'],[tag=>li,out=>[[tag=>strong,text=>'a prefix value is a root'],[tag=>code,text=>'/docs => mfs:docs/website/'],[tag=>code,text=>'/docs/x/y'],[tag=>code,text=>'mfs:docs/website/x/y']],text=>", extended by the request's remaining path: serves from ."],[tag=>li,out=>[[tag=>strong,text=>'a templated value is the address.']],text=>'It is evaluated with the request uri as its lhs, so the mount has already consumed the path and nothing is appended:']]],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>"""mtron> /person/1.map(</data/person/${name()}>)                  [-- the tail segment --]
   ==>/data/person/1"""]]],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>"""mtron> /person/1.map(</data/person/${as(rec::T).>>path/2}>)     [-- the same, positionally --]
   ==>/data/person/1"""]]],[tag=>p,out=>[[tag=>code,text=>'/data/person/1'],[tag=>em,text=>'n'],[tag=>code,text=>'path/(n+1)']],text=>'Both are : segment is , the path being 0-indexed with an empty leading element. The expressions see the whole request uri, so the pattern never has to carry the capture — there is no capture binding step, and a route is debuggable by looking at the request uri.'],[tag=>ul,out=>[[tag=>li,out=>[[tag=>strong,text=>'an address resolved for a request travels in the request rec'],[tag=>code,text=>'web_root'],[tag=>em,text=>'target']],text=>'as , and the handler is cached per (mount, client, resolved ) — never per address, since a templated mount resolves a different address per request while the same protocol engine serves them all.']]],[tag=>p,text=>'So a mount over it:'],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>'/person => </data/person/${name()}>']]],[tag=>pre,out=>[[tag=>code,class=>'language-mtron',text=>"""mtron> *</data/person/1>
   ...
```
## the space these examples use

A mount is only as real as the space behind it, so this document builds one first. Everything below addresses
`/usr/person/1` and `/usr/person/2`, and every block after this one runs against these objs. (`mtron_pre`
blocks are executed by the docs pipeline and inlined with their results; a plain `mtron` block is only shown.)

```mtron
mtron> memspace::[pattern=>/usr/#]@/sys/space/usr
==>memspace::[pattern=>/usr/#]@/sys/space/usr
mtron> person::[name=>'marko',age=>29]@/usr/person/1
==>person::[name=>'marko',age=>29]@/usr/person/1
mtron> person::[name=>'grant',age=>25]@/usr/person/2
==>person::[name=>'grant',age=>25]@/usr/person/2
```
## types

| type                      | vid                                            | refines       | meaning                                                                               |
|---------------------------|------------------------------------------------|---------------|---------------------------------------------------------------------------------------|
| `protocol::T`             | `/m/web/protocol`                              |               | the umbrella: **a union** of the surfaces below, so it classifies *values*            |
| `http::T`                 | `/m/web/http`                                  | `protocol::T` | the http vocabulary                                                                   |
| `rest::T`                 | `/m/web/http/rest`                             | `http::T`     | a REST surface — get reads, put replaces, patch updates, delete unlinks, post creates |
| `ws::T`                   | `/m/web/ws`                                    | `protocol::T` | the websocket vocabulary                                                              |
| `mcp::T`                  | `/m/web/mcp`                                   | `protocol::T` | an MCP surface                                                                        |
| `mtron::T`                | `/m/web/mtron`                                 | `protocol::T` | the mtron-eval surface — ship an obj, it is applied, the result returns               |
| `stream::T`               | `/m/web/stream`                                | `protocol::T` | the raw byte / server-sent-event stream surface                                       |
| `sse::T`                  | `/m/web/sse`                                   | `stream::T`   | a server-sent-events response — a chunked `text/event-stream` over http               |
| `mcp_server::T`           | `/m/web/mcp/mcp_server`                        | `mcp::T`      | an MCP server obj — **transport-agnostic**                                            |
| `web_http`                | `/m/web/http/web_http`                         | `rest::T`     | the handler that serves a web root                                                    |
| `mcp_http` / `mcp_ws`     | `/m/web/mcp/mcp_http`, `/m/web/mcp/mcp_ws`     | `mcp::T`      | an `mcp_server` bound to a carrier                                                    |
| `mtron_http` / `mtron_ws` | `/m/web/http/mtron_http`, `/m/web/ws/mtron_ws` | `mtron::T`    | the mtron-eval surface on each carrier                                                |
| `route::T`                | `/m/web/route`                                 | `rec::T`      | a mount table: `uri => target`                                                        |

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
mtron> http://localhost:8777/docker/image?mimeq=application/json      [-- the docker images as JSON --]
==>http://localhost:8777/docker/image?mimeq=application/json
mtron> */docker/image?mimeq=text/plain                                [-- the same objs, mtron-typed, as plain text --]
==>fail::[inst apply failure: no active space supports pattern /docker/image?mimeq=text/plain (at /m/inst/from@0)]@/sys/fail/2904
mtron> */docker/image                                                 [-- the native mtron rendering --]
==>fail::[inst apply failure: no active space supports pattern /docker/image (at /m/inst/from@0)]@/sys/fail/2908
```
`application/x-mtron` is the **structural parse gate**: it asks for the content parsed into mtron objs rather than
handed back as text. Read any mount's own documentation with `?docq`.

The mechanism is a q-proc: the live carrier declares
`q => [mimeq::[pattern=>mimeq, post_read=>inst?#{*}<=#{?}(uri::T,#::T)]]` — a `post_read` inst that runs *after*
the read and *before* serialization. `?mimeq=` is a server-side as-transform.

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

```mtron
mtron> /person/1.map(</usr/person/${name()}>)                  [-- the tail segment --]
==>/usr/person/1
```
```mtron
mtron> /person/1.map(</usr/person/${as(rec::T).>>path/2}>)     [-- the same, positionally --]
==>/usr/person/1
```
Both are `/usr/person/1`: segment *n* is `path/(n+1)`, the path being 0-indexed with an empty leading element.
The expressions see the whole request uri, so the pattern never has to carry the capture — there is no capture
binding step, and a route is debuggable by looking at the request uri.

* **an address resolved for a request travels in the request rec** as `web_root`, and the handler is cached per (mount,
  client, resolved *target*) — never per address, since a templated mount resolves a different address per
  request while the same protocol engine serves them all.

```mtron
mtron> */usr/person/1
==>person::[name=>'marko',age=>29]
mtron> *http://localhost:8777/usr/person/1
==>"person::[name=>'marko',age=>29]@/usr/person/1"
mtron> *http://localhost:8777/usr/person/1?mimeq=application/json
==>fail::[inst apply failure: no mimeq query processor attached to /sys/space/web/http [http://#] (at /m/inst/from@0)]@/sys/fail/2978
```
`/person/1` serves that obj and `/person/2` serves the other, through the *same* handler — and `/person/3` is a 404,
because the space is live rather than a lookup table of two.

Only a templated value is resolved per request. Anything else — a plain uri, a code — is resolved once at mount
time, so a route value that *builds* its target (a `code` such as `*dr.as(skill::T).as(mcp_server::T)`) is not
rebuilt on every request.

### route values: five flavors

Whatever a key mounts, the value is an *obj applied to the request uri* — and per the rule above, only templated
values re-resolve per request; the rest resolve once at mount time. The live carriers use five shapes:

| value                | example                                          | what it mounts                                                             |
|----------------------|--------------------------------------------------|----------------------------------------------------------------------------|
| identity path        | `/usr => /usr`                                   | this machine's own space, under its own address                            |
| scheme namespace     | `/mfs => mfs:`, `/docker => docker:`             | another space, addressed by scheme                                         |
| short name           | `/mcp => mcp_mtron`                              | the router's redirect table — a *type with a constructor*, built on demand |
| evaluated expression | `/drstynx => *dr.as(skill::T).as(mcp_server::T)` | the result, mounted as an obj                                              |
| fallback             | `/ => mfs:docs/website/`                         | every request no more-specific key caught                                  |

A scheme-keyed entry (`http: => http:`) is the sixth shape: an identity on the scheme itself, which is how
`httpSpace` serves *and* fetches — outbound `http://` derefs stay in the carrier.

The mount table is also the **exposure boundary**. What is not mounted is not reachable: the live carrier mounts
`/usr`, `/mfs`, `/mcp`, `/message`, `/docker` and `/`, while `/sys`, `/m` and everything else are simply absent
from the table — and absent from the web. Security by omission; fail-closed by construction.

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

`mcp_mtron` in the first mount is a short-name redirect to a `mcp_server::T` **type with a constructor** — the
mount is a factory: dereferencing the name returns the type, and the carrier constructs the handler from it.

## a URL is a deref chain

Past the mount, the remaining path is a traversal with the space's own read semantics, unchanged:

* **wildcards are honored.** `GET /docker/image/+` is a pattern read — every image, as a lst. The literal-prefix
  rule constrains mount *keys*, not the request path once a key has matched.
* **the path may continue through a result.** `/docker/image/+/container/0` matches the images, descends
  `container`, and indexes `0` — one traversal, expressed as path segments.
* **results may hold `!*` auto-refs.** A listing returns pointers, not copies —
  `container => [!*docker:container/vtest_a-a-1]`. Continuing the path *into* a ref forces its resolution;
  stopping before it leaves the subtree untouched. The ref is the fourth redirect, at the data level:

  router prefixes → the router's redirect table → mount tables → `!*` data edges

## the `mtron::T` apply protocol

`mtron::T` is the code-execution surface: ship an obj, the handler **applies** it, the result comes back. Both
realizations are the same three lines — `mtron_wsHandler`'s `ON_MESSAGE` is `lhs.apply(noobj())` then
`send(rhs)`; `mtron_httpHandler` does the same in `ON_GET`/`ON_POST`, with the request rec as the lhs argument.
Everything else the protocol needs follows from that:

* **the incoming obj is the program.** There is no command envelope. Shipped code derefs through the machine's
  own Router, so the handler never knows which space holds the data — `*<http://…>` from the console is this
  protocol.
* **failures are results.** A thrown exception is sent as `fail(e)` — the wire carries `fail` objs like any obj.
* **the wire format is mtron text.** Both handlers override `SEND` with `application/x-mtron` serialization.

The carriers differ in one respect: http applies the body *to the request rec* (headers and params are visible to
the code), while ws applies to `noobj()`. Code that must behave identically over both should be self-contained.

## streaming a response (`sse::T`)

A mount is normally request/response — one body, then the handler closes. `sse::T` is the streaming exception: an
http handler opens a chunked `text/event-stream` response and pushes any number of events before it closes. The
primitive is `SseStream` (`isa/web/space/http/SseStream.java`), opened from an `HttpRec` subclass with `openSse()`:

```java
final SseStream sse = this.openSse();     // 200, text/event-stream, chunked (length 0)
sse.

send("message","{\"ping\":true}"); // event: message + data: {...}
sse.

comment("ping");                      // : ping   (heartbeat, ignored by clients)
sse.

close();                              // flush + end the stream
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
* **pattern route keys** — the shape already exists in the type (`route::T`'s own doc example is
  `[/dr/+ => *dr.as(mcp::T), /docs/# => <mfs:docs/website/#>]`, and `webHelper.routeProblems(rec)` validates a
  table), but the carrier resolver still matches literal prefixes only. One route table shared by both carriers
  waits on the same missing piece: a resolver that checks a surface against the carrier, so http cannot mount a
  ws handler.

## see also

* the webSpace design record at `docs/design/webspace.md` — the lattice, the route contract, and the migration
  stages in full.
* [MCP server architecture](mcp-server-architecture.md) — `mcp_server`, tools, resources, notifications.
* [type system](type-system-mtron.md) — nominal vs structural typing, and why a union classifies values.