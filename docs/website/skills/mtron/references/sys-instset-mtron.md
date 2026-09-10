---
name: sys instruction set
description: file system, bash, sleep, process i/o
---

# system instruction set (`/m/sys`)

`/sys` is a required system space — created at boot, it holds the system objs (router, typer,
rewriter, env, thread). The sys instset (`/m/sys`) is its instruction companion: a guarded `bash` shell and
the `sleep`/`stdout`/`stdin` I/O primitives. Instructions live under `/m/sys/inst/...`; read any of them with
`?docq` before use.

## instructions

| inst     | dom → rng         | arg               | description                                    |
|----------|-------------------|-------------------|------------------------------------------------|
| `bash`   | `#{?} → lst[str]` | `cmd`, `timeout?` | guarded shell (`bash -c`), stdout split to lst |
| `sleep`  | `A{?} → A{?}`     | `time`            | pause the current thread, pass lhs through     |
| `stdout` | `A{?} → A{?}`     | `#{?}`            | print the arg's jvm obj, pass lhs through      |
| `stdin`  | `A{?} → str`      | —                 | read one line from the terminal                |

## bash (`/m/sys/inst/bash`)

The shell instruction — the function-call primitive: reach into the shell with `bash`, shape the result with
metatron's data structures. `cmd` is the terminal command, evaluated with `bash -c`. `timeout` is an optional
`time::T` (`millis::1000.0`, `second::1.0`, ...) with a 30-second default. The result is a `lst[str::T]`, one
entry per stdout line; a non-zero exit is a `fail::T`.

```mtron
bash('ls')
bash(cmd=>'whoami', timeout=>second::5.0)
```

Batch over a rec (indexed) or a lst (flat):

```mtron
{"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
```

Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
bash('ls').>>.bash("stat -c '%U' ${_}")            [-- drain: owners coalesce to a multiset --]
bash('ls').mapp(bash("stat -c '%U' ${_}"))         [-- map: one result lst per file --]
bash('ls').mapp(-<[_=>bash("stat -c '%U' ${_}")])  [-- indexed map: file => owner --]
```

### security modulators (q-params)

`bash` is hardened at the *instruction* level, not the call site. `allow`/`reject`/`env`/`dir` attach as query
parameters on the tid, so a tool registration bakes the policy in once — `!*bash?reject=['\brm\b']` — rather
than trusting the caller.

| q-param  | type            | semantics                                              |
|----------|-----------------|--------------------------------------------------------|
| `allow`  | `lst[str::T]`   | whitelist regexes, matched whole-command (`matches()`) |
| `reject` | `lst[str::T]`   | blacklist regexes, matched anywhere (`find()`)         |
| `env`    | `rec[uri=>str]` | environment vars injected into the process             |
| `dir`    | `str`           | working directory of the process                       |

`allow`, when non-empty, requires the command to match at least one pattern; `reject` fails when any pattern
matches anywhere. Both apply before the process spawns.

```mtron
!*bash?reject=['\brm\b']      [-- a bash that refuses rm --]
!*bash?env=[USER=>'metatron'] [-- a bash with a fixed env --]
```

### worked example — bash + the graph

The split in one line: `bash` supplies the function (`ls`, `stat`), metatron the shape — find the `Size` line,
regex out its byte count, type it `bB::T`, and keep the files over 30 kB. The unit types auto-convert, so `bB`
compares against `kB` directly (no `awk`, no `grep`, no `du`).

```mtron
bash('ls').mapp(-<[_=>bash("stat ${_}")]).>>.==[_=> >>.has("Size").regex('\s*(\d+)')>><0/0>.as?int<=str(int::T).as(bB::T)]==[_=>?>kB::30.0]
```

## sleep / stdout / stdin

```mtron
sleep(second::1.0)   [-- pause, pass lhs through --]
stdout('hello')      [-- print to the terminal, pass lhs through --]
stdin()              [-- block for one line, emit it as a str --]
```

## mounted state

Two recs are mounted under `/sys` at boot — not instructions, but read like any other space:

- **`/sys/env`** — the process environment, one `KEY => str` per variable.
- **`/sys/thread`** — the thread registry, one `id => thread` per thread, each documented via `?docq`.

```mtron
*/sys/env              [-- the environment as a rec --]
*/sys/env/HOME         [-- one variable --]
*/sys/thread/+?docq    [-- every thread's description --]
```

# FileSystem Space (fsSpace)

An `fsspace` mounts a subset of a file system into the metatron graph. Files are addressed via the space's
scheme (e.g., `local:`) and path prefix.

**IMPORTANT**: Every uri can be wrapped in angle brackets `< >`, but it is only required for those uris that have `.`
(periods), ` ` (spaces), and/or special characters such as `~` (tildes) in them. For instance, `/a/b/c` can be written
as is, but
`</a/b/c.txt>` requires angle brackets.

## Configuration

A typical `fsspace` definition:

```mtron
mtron> fsspace::[
         pattern => <local:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [local: => <~/my-project>]]@/sys/space/fs/local
==>fsspace::[
    pattern=>local:#,
    q=>[
     mimeq::[
      pattern=>mimeq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T)],
     lineq::[
      pattern=>lineq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)]],
    route=>[local:=>/home/killswitch/my-project]]@/sys/space/fs/local
```
- **`pattern`** — the URI pattern this space handles (`local:#` matches `<local:file.txt>`, `<local:sub/dir/file.md>`,
  etc.)
- **`route`** — maps the pattern prefix (`local:`) to a filesystem path (`<~/my-project>`)
- **`q`** — query processors: `mimeq` for MIME type tagging/conversion, `lineq` for line-level reads/writes

## MIME Type Handling

fsSpace detects a file's MIME type from its extension (and optionally the OS content probe) and returns a **typed
string** — a refined `str::T` such as `html::T`, `json::T`, `markdown::T`, etc.

```
file.html  →  html::"<html>...</html>"     (predicate-validated HTML string)
file.json  →  json::"{\"key\":\"value\"}"  (predicate-validated JSON string)
file.txt   →  str::"plain text"            (bare string, no special type)
file.md    →  markdown::"# Title"          (predicate-validated markdown string)
```

The MIME type acts as a **predicate** on the string content. For example, `html::T`'s predicate validates that the
string is valid HTML. The structural representation (`rec::T` DOM tree) is opt-in via `?mimeq=application/x-mtron` or
`.as(rec::T)`.

### MIME-to-TID Mapping

`MIME.MIMEType.toTid()` maps file extensions to type TIDs:

| Extension       | MIME Type             | TID                    |
|-----------------|-----------------------|------------------------|
| `.html`, `.htm` | `text/html`           | `/m/web/mime/html`     |
| `.json`         | `application/json`    | `/m/web/mime/json`     |
| `.xml`          | `application/xml`     | `/m/web/mime/xml`      |
| `.md`           | `text/markdown`       | `/m/web/mime/markdown` |
| `.css`          | `text/css`            | `/m/web/mime/css`      |
| `.java`         | `text/x-java`         | `/m/web/mime/java`     |
| `.yaml`, `.yml` | `application/yaml`    | `/m/web/mime/yaml`     |
| `.mtron`        | `application/x-mtron` | `/m/rec`               |
| `.txt`          | `text/plain`          | `/m/str`               |
| _other_         | `text/plain` / probe  | `/m/str`               |

### The `mimeq` Query Processor

The `?mimeq=` query parameter on a file URI controls what the space returns:

```mtron
mtron> [-- Default: typed string (predicate-validated) --]
mtron> *<local:index.html>
mtron> [-- Explicit type tag (same as default for .html files) --]
mtron> *<local:index.html?mimeq=text/html>
mtron> [-- Structural parse via application/x-mtron --]
mtron> *<local:index.html?mimeq=application/x-mtron>
```
`mimeq` is implemented in `QCollection.mimeQ()` as a space-level `postRead` query processor. It:

1. **Probes** the content type from the object's existing TID (or falls back to URI/file extension if the TID is bare
   `STR_TID`)
2. **Tags** the string with the correct MIME TID — this triggers predicate validation (e.g., `html::T` validates the
   string is valid HTML)
3. **Structural parse** — if `?mimeq=application/x-mtron`, runs the content-type-specific serializer
   (`ObjHTMLSerializer` for HTML, `ObjJSONSerializer` for JSON, etc.) to produce the `rec::T` DOM tree

## Reading and Writing Files

### Basic Read/Write

```mtron
mtron> [-- Read a file (returns typed string by default) --]
mtron> *<local:test.md>
==>markdown::'## new content'
mtron> [-- Write a string to a file --]
mtron> <local:test.md> -> "## new content"
==>'## new content'
```
### Reading with Structural Parse

```mtron
mtron> [-- Read markdown as a rec::T structure --]
mtron> *<local:test.md?mimeq=application/x-mtron>
==>[
    type=>doc,
    out=>[[
    type=>head,
    level=>2,
    text=>'new content',
    out=>[[type=>text,content=>'new content']]]]]
mtron> [-- Read JSON, then walk into rec fields --]
mtron> *<local:config.json?mimeq=application/x-mtron>/database/host
==>/database/host
```
### Binary Files

Files without a recognized text MIME type are read as `bytes::T`. Executable files (with shebangs)
are treated as `inst::T` and can be invoked directly:

```mtron
mtron> *<local:script.sh>        [-- bytes::T if binary, str::T if text                    --]
mtron> <local:script.sh>()       [-- execute (shell scripts, via application/x-mtron exec) --]
==>fail::[unable to locate inst-f of local:script.sh()@<0>]@/sys/fail/136
```
## Pattern-Based Access

fsSpace supports wildcard patterns in reads:

```mtron
mtron> [-- List all files in a directory --]
mtron> *<local:+/>
==><local:/test.md>=>markdown::'## new content'
mtron> [-- Read all .txt files --]
mtron> *<local:+/+>.where([name => -<'.'>>1.is('txt')])
==>markdown::'## new content'
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<local:src/main.java?lineq=10..20>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "10..20"]@/sys/fail/160
mtron> [-- Replace lines 5-10 with new content --]
mtron> <local:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "5..10"]@/sys/fail/172
```
### Boot Configuration Example

```mtron
mtron> fsspace::[
         pattern => <local:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [local: => ~/src]]@/sys/space/fs/src
==>fsspace::[
    pattern=>local:#,
    q=>[
     mimeq::[
      pattern=>mimeq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T)],
     lineq::[
      pattern=>lineq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)]],
    route=>[local:=>/m/inst/thread(/src)]]@/sys/space/fs/src
mtron> [-- Then use in expressions: --]
mtron> *<local:Main.java?lineq=1..50>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "1..50"]@/sys/fail/186
mtron> <local:index.html?mimeq=application/x-mtron>/html/head/title
==>ERROR: monad obj coefficient is greater than inst dom coefficient:
	<local:index.html?mimeq=application/x-mtron> [{1} X=> {0}] start?rng=A{**}&dom=noobj{0}(/html/head/title){<j>}@<1>
```
## Type Round-Trip

The full read-modify-write cycle preserves types:

```mtron
mtron> [-- Read HTML, cast to rec, modify, cast back to html string, write --]
mtron> <local:page.html> -> *<local:page.html?mimeq=application/x-mtron>
         .at(html/head/title -> 'New Title')
         .as(html::T)
==>fail::[inst apply failure: 'New Title' [str::T] unable to convert uri::T]@/sys/fail/238
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[inst apply failure: 'new-host' [str::T] unable to convert uri::T]@/sys/fail/286
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).