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
mtron> bash('ls')
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(9 more)]
mtron> bash(cmd=>'whoami', timeout=>second::5.0)
==>['ubuntu']
```
Batch over a rec (indexed) or a lst (flat):

```mtron
mtron> {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
==>['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(9 more)]]
==>['whoami'=>['ubuntu']]
==>['df -h'=>[
    'Filesystem      Size  Used Avail Use% Mounted on',
    'overlay         916G  492G  377G  57% /',
    'tmpfs            64M     0   64M   0% /dev',
    'shm              64M     0   64M   0% /dev/shm',
    '/dev/nvme1n1p2  916G  492G  377G  57% /work',
    'tmpfs            31G     0   31G   0% /proc/acpi',
    'tmpfs            31G     0   31G   0% /proc/asound',
    'tmpfs            31G     0   31G   0% /proc/scsi',
    'tmpfs            31G     0   31G   0% /sys/devices/virtual/...',
    'tmpfs            31G     0   31G   0% /sys/firmware']]
mtron> ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
==>[
    ['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(9 more)]],
    ['whoami'=>['ubuntu']],
    ['df -h'=>[
    'Filesystem      Size  Used Avail Use% Mounted on',
    'overlay         916G  492G  377G  57% /',
    'tmpfs            64M     0   64M   0% /dev',
    'shm              64M     0   64M   0% /dev/shm',
    '/dev/nvme1n1p2  916G  492G  377G  57% /work',
    'tmpfs            31G     0   31G   0% /proc/acpi',
    'tmpfs            31G     0   31G   0% /proc/asound',
    'tmpfs            31G     0   31G   0% /proc/scsi',
    'tmpfs            31G     0   31G   0% /sys/devices/virtual/...',
    'tmpfs            31G     0   31G   0% /sys/firmware']]]
```
Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
mtron> bash('ls').>>.bash("stat -c 'U' ${_}")            [-- drain: owners coalesce to a multiset --]
==>{19}['U']
mtron> bash('ls').mapp(bash("stat -c 'U' ${_}"))         [-- map: one result lst per file --]
==>[['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],...(9 more)]
mtron> bash('ls').mapp(-<[_=>bash("stat -c 'U' ${_}")])  [-- indexed map: file => owner --]
==>[['AGENTS.md'=>['U']],['bin'=>['U']],['boot'=>['U']],['conf'=>['U']],['CONTRIBUTING.md'=>['U']],['dist'=>['U']],['docs'=>['U']],['dsh-plugins'=>['U']],['language.properties'=>['U']],['LICENSE'=>['U']],...(9 more)]
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
```

```mtron
mtron> */sys/thread/+.count()               [-- number of threads        --]
==>44
mtron> */sys/thread/+.=?=[state=>run]       [-- number of active threads --]
mtron> */sys/thread/+?docq                  [-- thread documentation     --]
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/main,
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/805?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/1f64065f,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/974?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/8c28862a,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/main,
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/812?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/d4c4c50c,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/main,
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/46/292?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/74e020f0,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/811?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/ab7ebce6,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/20/267?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/main,
    desc=>'this root thread waits till all child threads are complete ...']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/723?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/6e6400ce,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/main,
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/977?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/45b1f0db,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/711?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/28aa7cc4,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/810?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/23391fc2,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/46/166?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/6c5bcc9c,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/46/171?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/5cc9bc22,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/main,
     state=>stop,
     time=>datetime::<//2026.09:15/18/35/45/720?tz=Z>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/b3bc6815,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
   ...
```
# file system space (fsspace::T)

An `fsspace::T` mounts a subset of a file system into the metatron graph. Files are addressed via the space's
scheme and path prefix.

**IMPORTANT**: Every uri can be wrapped in angle brackets `< >`, but it is only required for those uris that have `.`
(periods), ` ` (spaces), and/or special characters such as `~` (tildes) in them. For instance, `/a/b/c` can be written
as is, but `</a/b/c.txt>` requires angle brackets.

## Configuration

A typical `fsspace` definition:

```mtron
mtron> fsspace::[
         pattern => <mfs:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [mfs: => <~/software/metatron>]]@/sys/space/fs/mfs
==>fsspace::[
    pattern=>mfs:#,
    q=>[
     mimeq::[
      pattern=>mimeq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T)],
     lineq::[
      pattern=>lineq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)]],
    route=>[mfs:=>/home/ubuntu/software/metatron]]@/sys/space/fs/mfs
```
- **`pattern`** — the URI pattern this space handles (`mfs:#` matches `<mfs:file.txt>`, `<mfs:sub/dir/file.md>`,
  etc.)
- **`route`** — maps the pattern prefix (`mfs:`) to a filesystem path (`<~/software/metatron>`)
- **`q`** — query processors: `mimeq` for MIME type tagging/conversion, `lineq` for line-level reads/writes

## mime type handling

`fsspace::T` detects a file's MIME type from its extension (and optionally the OS content probe) and returns a **typed
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

### mime-to-tid mapping

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

### the `mimeq` query processor

The `?mimeq=` query parameter on a file URI controls what the space returns:

```mtron
mtron> [-- default: typed string (predicate-validated) --]
mtron> *<mfs:docs/website/index.html>
mtron> [-- explicit type tag (same as default for .html files) --]
mtron> *<mfs:docs/website/index.html?mimeq=text/html>
mtron> [-- structural parse via application/x-mtron --]
mtron> *<mfs:docs/website/index.html?mimeq=application/x-mtron>
```
`mimeq` is implemented in `QCollection.mimeQ()` as a space-level `postRead` query processor. It:

1. **Probes** the content type from the object's existing TID (or falls back to URI/file extension if the TID is bare
   `STR_TID`)
2. **Tags** the string with the correct MIME TID — this triggers predicate validation (e.g., `html::T` validates the
   string is valid HTML)
3. **Structural parse** — if `?mimeq=application/x-mtron`, runs the content-type-specific serializer
   (`ObjHTMLSerializer` for HTML, `ObjJSONSerializer` for JSON, etc.) to produce the `rec::T` DOM tree

## reading and writing files

### basic read/write

```mtron
mtron> [-- Read a file (returns typed string by default) --]
mtron> *<mfs:README.md>
```
```mtron
[-- Write a string to a file --]
<mfs:README.md> -> "## new content"
```

### reading with structural parse

```mtron
mtron> [-- Read markdown as a rec::T structure --]
mtron> *<mfs:README.md?mimeq=application/x-mtron>
mtron> [-- Read JSON, then walk into rec fields --]
mtron> *<mfs:config.json?mimeq=application/x-mtron>/database/host
==>/database/host
```
### binary files

Files without a recognized text MIME type are read as `bytes::T`. Executable files (with shebangs)
are treated as `inst::T` and can be invoked directly:

```mtron
mtron> *<mfs:script.sh>        [-- bytes::T if binary, str::T if text                    --]
mtron> <mfs:script.sh>()       [-- execute (shell scripts, via application/x-mtron exec) --]
==>fail::[unable to locate inst-f of mfs:script.sh()@<0>]@/sys/fail/2076
```
## pattern-based access

fsSpace supports wildcard patterns in reads:

```mtron
mtron> [-- List all files in a directory --]
mtron> *<mfs:+/>
mtron> [-- Read all .txt files --]
mtron> *<mfs:+/+>.where([name => -<'.'>>1.is('txt')])
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<mfs:src/main.java?lineq=10..20>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "10..20"]@/sys/fail/2080
mtron> [-- Replace lines 5-10 with new content --]
mtron> <mfs:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "5..10"]@/sys/fail/2084
```
### boot configuration example

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
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "1..50"]@/sys/fail/2088
mtron> <local:index.html?mimeq=application/x-mtron>/html/head/title
==>ERROR: monad obj coefficient is greater than inst dom coefficient:
	<local:index.html?mimeq=application/x-mtron> [{1} X=> {0}] start?rng=A{**}&dom=noobj{0}(/html/head/title){<j>}@<1>
```
## type round-trip

The full read-modify-write cycle preserves types:

```mtron
mtron> [-- Read HTML, cast to rec, modify, cast back to html string, write --]
mtron> <local:page.html> -> *<local:page.html?mimeq=application/x-mtron>
         .at(html/head/title -> 'New Title')
         .as(html::T)
==>fail::[inst apply failure: 'New Title' [str::T] unable to convert uri::T]@/sys/fail/2126
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[inst apply failure: 'new-host' [str::T] unable to convert uri::T]@/sys/fail/2162
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).