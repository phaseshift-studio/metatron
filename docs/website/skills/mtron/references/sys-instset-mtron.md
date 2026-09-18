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
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(8 more)]
mtron> bash(cmd=>'whoami', timeout=>second::5.0)
==>['killswitch']
```
Batch over a rec (indexed) or a lst (flat):

```mtron
mtron> {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
==>['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(8 more)]]
==>['whoami'=>['killswitch']]
==>['df -h'=>['Filesystem             Size  Used Avail Use% Mounted on','tmpfs                  6.1G  6.3M  6.1G   1% /run','efivarfs               128K   42K   82K  34% /sys/firmware/...','/dev/nvme1n1p2         916G  494G  376G  57% /','tmpfs                   31G  502M   30G   2% /dev/shm','tmpfs                  5.0M   20K  5.0M   1% /run/lock','tmpfs                   31G     0   31G   0% /run/qemu','/dev/nvme1n1p1         511M  6.2M  505M   2% /boot/efi','tmpfs                  6.1G  872K  6.1G   1% /run/user/1000','/dev/nvme0n1p2         932G  240G  692G  26% /media/hdd0',...(1 more)]]
mtron> ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
==>[
    ['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(8 more)]],
    ['whoami'=>['killswitch']],
    ['df -h'=>['Filesystem             Size  Used Avail Use% Mounted on','tmpfs                  6.1G  6.3M  6.1G   1% /run','efivarfs               128K   42K   82K  34% /sys/firmware/...','/dev/nvme1n1p2         916G  494G  376G  57% /','tmpfs                   31G  502M   30G   2% /dev/shm','tmpfs                  5.0M   20K  5.0M   1% /run/lock','tmpfs                   31G     0   31G   0% /run/qemu','/dev/nvme1n1p1         511M  6.2M  505M   2% /boot/efi','tmpfs                  6.1G  872K  6.1G   1% /run/user/1000','/dev/nvme0n1p2         932G  240G  692G  26% /media/hdd0',...(1 more)]]]
```
Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
mtron> bash('ls').>>.bash("stat -c 'U' ${_}")            [-- drain: owners coalesce to a multiset --]
==>{18}['U']
mtron> bash('ls').mapp(bash("stat -c 'U' ${_}"))         [-- map: one result lst per file --]
==>[['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],...(8 more)]
mtron> bash('ls').mapp(-<[_=>bash("stat -c 'U' ${_}")])  [-- indexed map: file => owner --]
==>[['AGENTS.md'=>['U']],['bin'=>['U']],['boot'=>['U']],['conf'=>['U']],['CONTRIBUTING.md'=>['U']],['dist'=>['U']],['docs'=>['U']],['dsh-plugins'=>['U']],['language.properties'=>['U']],['LICENSE'=>['U']],...(8 more)]
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
==>124
mtron> */sys/thread/+.=?=[state=>run]       [-- number of active threads --]
mtron> */sys/thread/+?docq                  [-- thread documentation     --]
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/935?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/60077e9e,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/b7f94326,
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/478?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/71acdf94,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/690?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/71138946,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/663?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/5c16dc9e,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/05/094?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/cf5501de,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/664?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/291dab3e,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/471?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/c3f7c79e,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/907?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/ea014253,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/893?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/fde090b8,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/05/079?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/b3502fa3,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/326?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/8b874e47,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/1aabf84a,
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/673?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/5112d3eb,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/916?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/77bd5002,
    desc=>'metatron-thread']
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     source=>!*/sys/thread/95c66534,
     state=>stop,
     time=>datetime::<//2026.09:18/04/34/04/460?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/3e595679,
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
    route=>[mfs:=>/home/killswitch/software/metatron]]@/sys/space/fs/mfs
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
==>html::"""<!--
     ~ Metatron: A Distributed Computing Language and Virtual Machine
     ~  Copyright (C) 2025- PhaseShift Studio, LLC
     ~  
     ~ This program is free software: you can redistribute it and/or modify
     ~ it under the terms of the GNU Affero General Public License as published by
     ~ the Free Software Foundation, either version 3 of the License, or
     ~ (at your option) any later version.
     ~  
     ~ This program is distributed in the hope that it will be useful,
     ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
     ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     ~ GNU Affero General Public License for more details.
     ~
     ~ You should have received a copy of the GNU Affero General Public License
     ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
     -->
   
   <!DOCTYPE html>
   <html lang="en">
   ...
mtron> [-- explicit type tag (same as default for .html files) --]
mtron> *<mfs:docs/website/index.html?mimeq=text/html>
==>html::"""<!--
     ~ Metatron: A Distributed Computing Language and Virtual Machine
     ~  Copyright (C) 2025- PhaseShift Studio, LLC
     ~  
     ~ This program is free software: you can redistribute it and/or modify
     ~ it under the terms of the GNU Affero General Public License as published by
     ~ the Free Software Foundation, either version 3 of the License, or
     ~ (at your option) any later version.
     ~  
     ~ This program is distributed in the hope that it will be useful,
     ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
     ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     ~ GNU Affero General Public License for more details.
     ~
     ~ You should have received a copy of the GNU Affero General Public License
     ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
     -->
   
   <!DOCTYPE html>
   <html lang="en">
   ...
mtron> [-- structural parse via application/x-mtron --]
mtron> *<mfs:docs/website/index.html?mimeq=application/x-mtron>
==>[html=>[
    head=>[
     title=>'metatron',
     out=>[[tag=>meta,charset=>'utf-8'],[
      tag=>meta,
      name=>'viewport',
      content=>'width=device-width, initial-scale=1.0'],[
      tag=>meta,
      name=>'keywords',
      content=>'metatron programming data graph database llm'],[
      tag=>meta,
      name=>'description',
      content=>'Next Generation Data Technologies'],[
      tag=>meta,
      name=>'theme-color',
      content=>'#ffffff'],[
      tag=>link,
      rel=>'apple-touch-icon',
      sizes=>'114x114',
      href=><images/apple-touch-icon.png>],[
   ...
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
==>markdown::"""# metatron
   
   <a href="http://metatron.phaseshift.studio"><img src="http://metatron.phaseshift.studio/images/metatron-character.png" width="200px"></a>
   """
```
```mtron
[-- Write a string to a file --]
<mfs:README.md> -> "## new content"
```

### reading with structural parse

```mtron
mtron> [-- Read markdown as a rec::T structure --]
mtron> *<mfs:README.md?mimeq=application/x-mtron>
==>[
    type=>doc,
    out=>[
     [
      type=>head,
      level=>1,
      text=>'metatron',
      out=>[[type=>text,content=>'metatron']]],
     [
      type=>p,
      text=>'<a href="http://metatron.phaseshift.studio"><img src="http:...',
      out=>[
       [
        type=>html_inline,
        html=>'<a href="http://metatron.phaseshift.studio">'],
       [
        type=>html_inline,
        html=>'<img src="http://metatron.phaseshift.studio/images/metatron...'],
       [type=>html_inline,html=>'</a>']]]]]
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
==>fail::[unable to locate inst-f of mfs:script.sh()@<0>]@/sys/fail/1980
```
## pattern-based access

fsSpace supports wildcard patterns in reads:

```mtron
mtron> [-- List all files in a directory --]
mtron> *<mfs:+/>
==>mfs:/LICENSE=>fail::[parse error at line 1, col 4:
     GNU AFFERO GENERAL PUBLIC LICENSE
            ...
        ^
     could not parse at ' ' — unclosed single-quote — missing closing '''?]
==>mfs:/docs=>mfs:/docs
==><mfs:/.mtron-classpath>=>'/home/killswitch/.m2/repository/org/apache/lucene/lucene-core/10.5.1/lucene-core-10.5.1.jar:/home/killswitch/.m2/repository/org/apache/lucene/lucene-analysis-common/10.5.1/lucene-analysis-common-10.5.1.jar:/home/killswitch/.m2/repository/org/apache/lucene/lucene-suggest/10.5.1/lucene-suggest-10.5.1.jar:/home/killswitch/.m2/repository/org/zeroturnaround/zt-exec/1.13.0/zt-exec-1.13.0.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-all/0.64.8/flexmark-all-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark/0.64.8/flexmark-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-abbreviation/0.64.8/flexmark-ext-abbreviation-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-admonition/0.64.8/flexmark-ext-admonition-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-anchorlink/0.64.8/flexmark-ext-anchorlink-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-aside/0.64.8/flexmark-ext-aside-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-attributes/0.64.8/flexmark-ext-attributes-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-autolink/0.64.8/flexmark-ext-autolink-0.64.8.jar:/home/killswitch/.m2/repository/org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-definition/0.64.8/flexmark-ext-definition-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-emoji/0.64.8/flexmark-ext-emoji-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-enumerated-reference/0.64.8/flexmark-ext-enumerated-reference-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-escaped-character/0.64.8/flexmark-ext-escaped-character-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-footnotes/0.64.8/flexmark-ext-footnotes-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-gfm-issues/0.64.8/flexmark-ext-gfm-issues-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.64.8/flexmark-ext-gfm-strikethrough-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.64.8/flexmark-ext-gfm-tasklist-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-gfm-users/0.64.8/flexmark-ext-gfm-users-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-gitlab/0.64.8/flexmark-ext-gitlab-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-jekyll-front-matter/0.64.8/flexmark-ext-jekyll-front-matter-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-jekyll-tag/0.64.8/flexmark-ext-jekyll-tag-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-media-tags/0.64.8/flexmark-ext-media-tags-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-resizable-image/0.64.8/flexmark-ext-resizable-image-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-macros/0.64.8/flexmark-ext-macros-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-ins/0.64.8/flexmark-ext-ins-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-xwiki-macros/0.64.8/flexmark-ext-xwiki-macros-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-superscript/0.64.8/flexmark-ext-superscript-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-tables/0.64.8/flexmark-ext-tables-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-toc/0.64.8/flexmark-ext-toc-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-typographic/0.64.8/flexmark-ext-typographic-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-wikilink/0.64.8/flexmark-ext-wikilink-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.64.8/flexmark-ext-yaml-front-matter-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-ext-youtube-embedded/0.64.8/flexmark-ext-youtube-embedded-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-jira-converter/0.64.8/flexmark-jira-converter-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-pdf-converter/0.64.8/flexmark-pdf-converter-0.64.8.jar:/home/killswitch/.m2/repository/com/ibm/icu/icu4j/72.1/icu4j-72.1.jar:/home/killswitch/.m2/repository/com/openhtmltopdf/openhtmltopdf-core/1.0.10/openhtmltopdf-core-1.0.10.jar:/home/killswitch/.m2/repository/com/openhtmltopdf/openhtmltopdf-pdfbox/1.0.10/openhtmltopdf-pdfbox-1.0.10.jar:/home/killswitch/.m2/repository/org/apache/pdfbox/pdfbox/2.0.24/pdfbox-2.0.24.jar:/home/killswitch/.m2/repository/org/apache/pdfbox/fontbox/2.0.24/fontbox-2.0.24.jar:/home/killswitch/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar:/home/killswitch/.m2/repository/org/apache/pdfbox/xmpbox/2.0.24/xmpbox-2.0.24.jar:/home/killswitch/.m2/repository/de/rototor/pdfbox/graphics2d/0.32/graphics2d-0.32.jar:/home/killswitch/.m2/repository/com/openhtmltopdf/openhtmltopdf-rtl-support/1.0.10/openhtmltopdf-rtl-support-1.0.10.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-profile-pegdown/0.64.8/flexmark-profile-pegdown-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-ast/0.64.8/flexmark-util-ast-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-builder/0.64.8/flexmark-util-builder-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-collection/0.64.8/flexmark-util-collection-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-data/0.64.8/flexmark-util-data-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-dependency/0.64.8/flexmark-util-dependency-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-format/0.64.8/flexmark-util-format-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-html/0.64.8/flexmark-util-html-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-misc/0.64.8/flexmark-util-misc-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-options/0.64.8/flexmark-util-options-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-sequence/0.64.8/flexmark-util-sequence-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util-visitor/0.64.8/flexmark-util-visitor-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-youtrack-converter/0.64.8/flexmark-youtrack-converter-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-html2md-converter/0.64.8/flexmark-html2md-converter-0.64.8.jar:/home/killswitch/.m2/repository/com/vladsch/flexmark/flexmark-util/0.64.8/flexmark-util-0.64.8.jar:/home/killswitch/.m2/repository/ch/usi/si/seart/java-tree-sitter/1.12.0/java-tree-sitter-1.12.0.jar:/home/killswitch/.m2/repository/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar:/home/killswitch/.m2/repository/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar:/home/killswitch/.m2/repository/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar:/home/killswitch/.m2/repository/com/oath/cyclops/cyclops/10.4.1/cyclops-10.4.1.jar:/home/killswitch/.m2/repository/org/agrona/Agrona/0.9.1/Agrona-0.9.1.jar:/home/killswitch/.m2/repository/org/reactivestreams/reactive-streams/1.0.0/reactive-streams-1.0.0.jar:/home/killswitch/.m2/repository/io/kindedj/kindedj/1.1.0/kindedj-1.1.0.jar:/home/killswitch/.m2/repository/org/eclipse/store/storage-embedded/4.2.0/storage-embedded-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/store/storage/4.2.0/storage-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/store/afs-nio/4.2.0/afs-nio-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/serializer/afs/4.2.0/afs-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/serializer/base/4.2.0/base-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/serializer/persistence-binary/4.2.0/persistence-binary-4.2.0.jar:/home/killswitch/.m2/repository/org/eclipse/serializer/persistence/4.2.0/persistence-4.2.0.jar:/home/killswitch/.m2/repository/com/fazecast/jSerialComm/2.11.4/jSerialComm-2.11.4.jar:/home/killswitch/.m2/repository/org/mariadb/jdbc/mariadb-java-client/3.5.10/mariadb-java-client-3.5.10.jar:/home/killswitch/.m2/repository/com/mysql/mysql-connector-j/26.7.0/mysql-connector-j-26.7.0.jar:/home/killswitch/.m2/repository/com/google/protobuf/protobuf-java/4.31.1/protobuf-java-4.31.1.jar:/home/killswitch/.m2/repository/org/mongodb/mongodb-driver-sync/5.11.1/mongodb-driver-sync-5.11.1.jar:/home/killswitch/.m2/repository/org/mongodb/bson/5.11.1/bson-5.11.1.jar:/home/killswitch/.m2/repository/org/mongodb/mongodb-driver-core/5.11.1/mongodb-driver-core-5.11.1.jar:/home/killswitch/.m2/repository/org/mongodb/bson-record-codec/5.11.1/bson-record-codec-5.11.1.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-mcp/1.20.0-beta30/langchain4j-mcp-1.20.0-beta30.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j/1.20.0/langchain4j-1.20.0.jar:/home/killswitch/.m2/repository/org/apache/opennlp/opennlp-tools/2.5.11/opennlp-tools-2.5.11.jar:/home/killswitch/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.22.1/jackson-databind-2.22.1.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-http-client/1.20.0/langchain4j-http-client-1.20.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-http-client-jdk/1.20.0/langchain4j-http-client-jdk-1.20.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-skills/1.20.0-beta30/langchain4j-skills-1.20.0-beta30.jar:/home/killswitch/.m2/repository/org/commonmark/commonmark-ext-yaml-front-matter/0.28.0/commonmark-ext-yaml-front-matter-0.28.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-agentic/1.20.0-beta30/langchain4j-agentic-1.20.0-beta30.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-local-ai/1.20.0-beta30/langchain4j-local-ai-1.20.0-beta30.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-anthropic/1.20.0/langchain4j-anthropic-1.20.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-core/1.20.0/langchain4j-core-1.20.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-reactive-streaming/1.20.0-beta30/langchain4j-reactive-streaming-1.20.0-beta30.jar:/home/killswitch/.m2/repository/io/smallrye/reactive/mutiny-zero/1.3.1/mutiny-zero-1.3.1.jar:/home/killswitch/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar:/home/killswitch/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.22.1/jackson-core-2.22.1.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-open-ai/1.20.0/langchain4j-open-ai-1.20.0.jar:/home/killswitch/.m2/repository/com/knuddels/jtokkit/1.1.0/jtokkit-1.1.0.jar:/home/killswitch/.m2/repository/dev/langchain4j/langchain4j-ollama/1.20.0/langchain4j-ollama-1.20.0.jar:/home/killswitch/.m2/repository/com/llama4j/gguf/0.1.1/gguf-0.1.1.jar:/home/killswitch/.m2/repository/io/github/ollama4j/ollama4j/1.1.7/ollama4j-1.1.7.jar:/home/killswitch/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.21.0/jackson-dataformat-yaml-2.21.0.jar:/home/killswitch/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.21.0/jackson-datatype-jsr310-2.21.0.jar:/home/killswitch/.m2/repository/io/prometheus/simpleclient/0.16.0/simpleclient-0.16.0.jar:/home/killswitch/.m2/repository/io/prometheus/simpleclient_tracer_otel/0.16.0/simpleclient_tracer_otel-0.16.0.jar:/home/killswitch/.m2/repository/io/prometheus/simpleclient_tracer_common/0.16.0/simpleclient_tracer_common-0.16.0.jar:/home/killswitch/.m2/repository/io/prometheus/simpleclient_tracer_otel_agent/0.16.0/simpleclient_tracer_otel_agent-0.16.0.jar:/home/killswitch/.m2/repository/com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar:/home/killswitch/.m2/repository/com/google/guava/failureaccess/1.0.3/failureaccess-1.0.3.jar:/home/killswitch/.m2/repository/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar:/home/killswitch/.m2/repository/com/google/j2objc/j2objc-annotations/3.1/j2objc-annotations-3.1.jar:/home/killswitch/.m2/repository/org/janusgraph/janusgraph-driver/1.1.0/janusgraph-driver-1.1.0.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-driver/3.7.3/gremlin-driver-3.7.3.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-util/3.7.3/gremlin-util-3.7.3.jar:/home/killswitch/.m2/repository/io/netty/netty-all/4.1.101.Final/netty-all-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-dns/4.1.101.Final/netty-codec-dns-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-haproxy/4.1.101.Final/netty-codec-haproxy-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-http2/4.1.101.Final/netty-codec-http2-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-memcache/4.1.101.Final/netty-codec-memcache-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-redis/4.1.101.Final/netty-codec-redis-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-smtp/4.1.101.Final/netty-codec-smtp-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-socks/4.1.101.Final/netty-codec-socks-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-stomp/4.1.101.Final/netty-codec-stomp-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-xml/4.1.101.Final/netty-codec-xml-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-handler-proxy/4.1.101.Final/netty-handler-proxy-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-handler-ssl-ocsp/4.1.101.Final/netty-handler-ssl-ocsp-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-resolver-dns/4.1.101.Final/netty-resolver-dns-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-rxtx/4.1.101.Final/netty-transport-rxtx-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-sctp/4.1.101.Final/netty-transport-sctp-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-udt/4.1.101.Final/netty-transport-udt-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-resolver-dns-classes-macos/4.1.101.Final/netty-resolver-dns-classes-macos-4.1.101.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-resolver-dns-native-macos/4.1.101.Final/netty-resolver-dns-native-macos-4.1.101.Final-osx-x86_64.jar:/home/killswitch/.m2/repository/io/netty/netty-resolver-dns-native-macos/4.1.101.Final/netty-resolver-dns-native-macos-4.1.101.Final-osx-aarch_64.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-groovy/3.7.3/gremlin-groovy-3.7.3.jar:/home/killswitch/.m2/repository/org/apache/ivy/ivy/2.5.2/ivy-2.5.2.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy/4.0.23/groovy-4.0.23.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-groovysh/4.0.23/groovy-groovysh-4.0.23.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-console/4.0.23/groovy-console-4.0.23.jar:/home/killswitch/.m2/repository/com/github/javaparser/javaparser-core/3.26.2/javaparser-core-3.26.2.jar:/home/killswitch/.m2/repository/org/abego/treelayout/org.abego.treelayout.core/1.0.3/org.abego.treelayout.core-1.0.3.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-swing/4.0.23/groovy-swing-4.0.23.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-templates/4.0.23/groovy-templates-4.0.23.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-xml/4.0.23/groovy-xml-4.0.23.jar:/home/killswitch/.m2/repository/jline/jline/2.14.6/jline-2.14.6.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-json/4.0.23/groovy-json-4.0.23.jar:/home/killswitch/.m2/repository/org/apache/groovy/groovy-jsr223/4.0.23/groovy-jsr223-4.0.23.jar:/home/killswitch/.m2/repository/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar:/home/killswitch/.m2/repository/org/noggit/noggit/0.8/noggit-0.8.jar:/home/killswitch/.m2/repository/org/locationtech/spatial4j/spatial4j/0.8/spatial4j-0.8.jar:/home/killswitch/.m2/repository/org/locationtech/jts/jts-core/1.17.0/jts-core-1.17.0.jar:/home/killswitch/.m2/repository/org/apache/commons/commons-text/1.12.0/commons-text-1.12.0.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-core/3.8.2/gremlin-core-3.8.2.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-shaded/3.8.2/gremlin-shaded-3.8.2.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/gremlin-language/3.8.2/gremlin-language-3.8.2.jar:/home/killswitch/.m2/repository/org/antlr/antlr4-runtime/4.9.1/antlr4-runtime-4.9.1.jar:/home/killswitch/.m2/repository/org/javatuples/javatuples/1.2/javatuples-1.2.jar:/home/killswitch/.m2/repository/org/slf4j/jcl-over-slf4j/1.7.25/jcl-over-slf4j-1.7.25.jar:/home/killswitch/.m2/repository/org/apache/commons/commons-configuration2/2.15.1/commons-configuration2-2.15.1.jar:/home/killswitch/.m2/repository/commons-beanutils/commons-beanutils/1.11.0/commons-beanutils-1.11.0.jar:/home/killswitch/.m2/repository/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar:/home/killswitch/.m2/repository/com/carrotsearch/hppc/0.7.1/hppc-0.7.1.jar:/home/killswitch/.m2/repository/com/squareup/javapoet/1.13.0/javapoet-1.13.0.jar:/home/killswitch/.m2/repository/com/github/ben-manes/caffeine/caffeine/2.3.1/caffeine-2.3.1.jar:/home/killswitch/.m2/repository/org/apache/tinkerpop/tinkergraph-gremlin/3.8.2/tinkergraph-gremlin-3.8.2.jar:/home/killswitch/.m2/repository/org/mozilla/rhino/1.9.1/rhino-1.9.1.jar:/home/killswitch/.m2/repository/cat/inspiracio/rhino-js-engine/1.7.14/rhino-js-engine-1.7.14.jar:/home/killswitch/.m2/repository/org/buildobjects/jproc/2.8.2/jproc-2.8.2.jar:/home/killswitch/.m2/repository/org/java-websocket/Java-WebSocket/1.6.0/Java-WebSocket-1.6.0.jar:/home/killswitch/.m2/repository/org/commonmark/commonmark/0.30.0/commonmark-0.30.0.jar:/home/killswitch/.m2/repository/net/objecthunter/exp4j/0.4.8/exp4j-0.4.8.jar:/home/killswitch/.m2/repository/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar:/home/killswitch/.m2/repository/com/google/errorprone/error_prone_annotations/2.48.0/error_prone_annotations-2.48.0.jar:/home/killswitch/.m2/repository/org/yaml/snakeyaml/2.7/snakeyaml-2.7.jar:/home/killswitch/.m2/repository/org/jsoup/jsoup/1.23.2/jsoup-1.23.2.jar:/home/killswitch/.m2/repository/org/asciidoctor/asciidoctorj/3.0.1/asciidoctorj-3.0.1.jar:/home/killswitch/.m2/repository/org/asciidoctor/asciidoctorj-api/3.0.1/asciidoctorj-api-3.0.1.jar:/home/killswitch/.m2/repository/org/jruby/jruby/9.4.14.0/jruby-9.4.14.0.jar:/home/killswitch/.m2/repository/org/jruby/jruby-base/9.4.14.0/jruby-base-9.4.14.0.jar:/home/killswitch/.m2/repository/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar:/home/killswitch/.m2/repository/org/ow2/asm/asm-commons/9.7.1/asm-commons-9.7.1.jar:/home/killswitch/.m2/repository/org/ow2/asm/asm-tree/9.7.1/asm-tree-9.7.1.jar:/home/killswitch/.m2/repository/org/ow2/asm/asm-util/9.7.1/asm-util-9.7.1.jar:/home/killswitch/.m2/repository/org/ow2/asm/asm-analysis/9.7.1/asm-analysis-9.7.1.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-netdb/1.2.0/jnr-netdb-1.2.0.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-enxio/0.32.18/jnr-enxio-0.32.18.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-unixsocket/0.38.23/jnr-unixsocket-0.38.23.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-posix/3.1.20/jnr-posix-3.1.20.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-constants/0.10.4/jnr-constants-0.10.4.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-ffi/2.2.17/jnr-ffi-2.2.17.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-a64asm/1.0.0/jnr-a64asm-1.0.0.jar:/home/killswitch/.m2/repository/com/github/jnr/jnr-x86asm/1.0.2/jnr-x86asm-1.0.2.jar:/home/killswitch/.m2/repository/com/github/jnr/jffi/1.3.13/jffi-1.3.13.jar:/home/killswitch/.m2/repository/com/github/jnr/jffi/1.3.13/jffi-1.3.13-native.jar:/home/killswitch/.m2/repository/org/jruby/joni/joni/2.2.5/joni-2.2.5.jar:/home/killswitch/.m2/repository/org/jruby/jcodings/jcodings/1.0.63/jcodings-1.0.63.jar:/home/killswitch/.m2/repository/org/jruby/dirgra/0.3/dirgra-0.3.jar:/home/killswitch/.m2/repository/com/headius/invokebinder/1.13/invokebinder-1.13.jar:/home/killswitch/.m2/repository/com/headius/options/1.6/options-1.6.jar:/home/killswitch/.m2/repository/org/jruby/jzlib/1.1.5/jzlib-1.1.5.jar:/home/killswitch/.m2/repository/joda-time/joda-time/2.12.7/joda-time-2.12.7.jar:/home/killswitch/.m2/repository/me/qmx/jitescript/jitescript/0.4.1/jitescript-0.4.1.jar:/home/killswitch/.m2/repository/com/headius/backport9/1.13/backport9-1.13.jar:/home/killswitch/.m2/repository/org/crac/crac/1.5.0/crac-1.5.0.jar:/home/killswitch/.m2/repository/org/jruby/jruby-stdlib/9.4.14.0/jruby-stdlib-9.4.14.0.jar:/home/killswitch/.m2/repository/com/bmuschko/asciidoctorj-tabbed-code-extension/0.3/asciidoctorj-tabbed-code-extension-0.3.jar:/home/killswitch/.m2/repository/org/jline/jline/3.30.17/jline-3.30.17.jar:/home/killswitch/.m2/repository/org/jline/jline-terminal-jansi/3.30.17/jline-terminal-jansi-3.30.17.jar:/home/killswitch/.m2/repository/org/fusesource/jansi/jansi/2.4.3/jansi-2.4.3.jar:/home/killswitch/.m2/repository/org/jline/jline-terminal/3.30.17/jline-terminal-3.30.17.jar:/home/killswitch/.m2/repository/org/jline/jline-native/3.30.17/jline-native-3.30.17.jar:/home/killswitch/.m2/repository/org/slf4j/slf4j-api/2.0.19/slf4j-api-2.0.19.jar:/home/killswitch/.m2/repository/ch/qos/logback/logback-core/1.6.3/logback-core-1.6.3.jar:/home/killswitch/.m2/repository/ch/qos/logback/logback-classic/1.6.3/logback-classic-1.6.3.jar:/home/killswitch/.m2/repository/org/slf4j/jul-to-slf4j/2.0.19/jul-to-slf4j-2.0.19.jar:/home/killswitch/.m2/repository/com/github/petitparser/petitparser-core/2.4.0/petitparser-core-2.4.0.jar:/home/killswitch/.m2/repository/com/hivemq/hivemq-mqtt-client/1.4.0/hivemq-mqtt-client-1.4.0.jar:/home/killswitch/.m2/repository/io/reactivex/rxjava2/rxjava/2.2.21/rxjava-2.2.21.jar:/home/killswitch/.m2/repository/org/jetbrains/annotations/26.1.0/annotations-26.1.0.jar:/home/killswitch/.m2/repository/io/netty/netty-buffer/4.1.137.Final/netty-buffer-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec/4.1.137.Final/netty-codec-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-common/4.1.137.Final/netty-common-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-handler/4.1.137.Final/netty-handler-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-resolver/4.1.137.Final/netty-resolver-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-native-unix-common/4.1.137.Final/netty-transport-native-unix-common-4.1.137.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport/4.1.137.Final/netty-transport-4.1.137.Final.jar:/home/killswitch/.m2/repository/org/jctools/jctools-core/4.0.7/jctools-core-4.0.7.jar:/home/killswitch/.m2/repository/com/google/dagger/dagger/2.42/dagger-2.42.jar:/home/killswitch/.m2/repository/javax/inject/javax.inject/1/javax.inject-1.jar:/home/killswitch/.m2/repository/io/modelcontextprotocol/sdk/mcp/2.0.1/mcp-2.0.1.jar:/home/killswitch/.m2/repository/io/modelcontextprotocol/sdk/mcp-json-jackson3/2.0.1/mcp-json-jackson3-2.0.1.jar:/home/killswitch/.m2/repository/tools/jackson/core/jackson-databind/3.1.4/jackson-databind-3.1.4.jar:/home/killswitch/.m2/repository/tools/jackson/core/jackson-core/3.1.4/jackson-core-3.1.4.jar:/home/killswitch/.m2/repository/com/networknt/json-schema-validator/3.0.6/json-schema-validator-3.0.6.jar:/home/killswitch/.m2/repository/com/ethlo/time/itu/1.14.0/itu-1.14.0.jar:/home/killswitch/.m2/repository/tools/jackson/dataformat/jackson-dataformat-yaml/3.1.4/jackson-dataformat-yaml-3.1.4.jar:/home/killswitch/.m2/repository/org/snakeyaml/snakeyaml-engine/3.0.1/snakeyaml-engine-3.0.1.jar:/home/killswitch/.m2/repository/io/modelcontextprotocol/sdk/mcp-core/2.0.1/mcp-core-2.0.1.jar:/home/killswitch/.m2/repository/io/projectreactor/reactor-core/3.7.0/reactor-core-3.7.0.jar:/home/killswitch/.m2/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar:/home/killswitch/.m2/repository/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-http/4.1.116.Final/netty-codec-http-4.1.116.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-codec-mqtt/4.1.116.Final/netty-codec-mqtt-4.1.116.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-native-epoll/4.1.116.Final/netty-transport-native-epoll-4.1.116.Final-linux-x86_64.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-classes-epoll/4.1.116.Final/netty-transport-classes-epoll-4.1.116.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-native-epoll/4.1.116.Final/netty-transport-native-epoll-4.1.116.Final-linux-aarch_64.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-native-kqueue/4.1.116.Final/netty-transport-native-kqueue-4.1.116.Final-osx-aarch_64.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-classes-kqueue/4.1.116.Final/netty-transport-classes-kqueue-4.1.116.Final.jar:/home/killswitch/.m2/repository/io/netty/netty-transport-native-kqueue/4.1.116.Final/netty-transport-native-kqueue-4.1.116.Final-osx-x86_64.jar'
==><mfs:/.cursor>=><mfs:/.cursor>
==><mfs:/.openclaude>=><mfs:/.openclaude>
==><mfs:/.metatron>=><mfs:/.metatron>
==><mfs:/.agentbridge>=><mfs:/.agentbridge>
==><mfs:/.gitignore>=>""".*
   *.sqlite
   opencode.json
   !.metatron/
   # mvnw reads .mvn/wrapper/maven-wrapper.properties at runtime — it must ship
   # in clones, but `.*` above swallows the whole .mvn/ dir. Re-include it.
   !.mvn/
   !.mvn/wrapper/
   !.mvn/wrapper/maven-wrapper.properties
   # `.*` above also swallows the docker build context and new GitHub workflows.
   !.dockerignore
   !.github/
   !.github/workflows/
   !.github/workflows/docker.yml
   target/
   node_modules/
   .metatron.history
   *.iml
   /.venv/
   __pycache__/
   *.pyc
   *.pyo
   # Compiled class file
   *.class
   
   benchmark/*.json
   .open*
   .worktrees
   
   # Log file
   *.log
   
   # BlueJ files
   *.ctxt
   
   # Mobile Tools for Java (J2ME)
   .mtj.tmp/
   
   # Package Files #
   *.jar
   *.war
   *.nar
   *.ear
   *.zip
   *.tar.gz
   *.rar
   
   # virtual machine crash logs, see http://www.java.com/en/download/help/error_hotspot.xml
   hs_err_pid*
   replay_pid*
   
   .ai/
   """
==><mfs:/.github>=><mfs:/.github>
==><mfs:/.classpath>=>"""<?xml version="1.0" encoding="UTF-8"?>
   <classpath>
   	<classpathentry kind="src" output="target/classes" path="src/main/java">
   		<attributes>
   			<attribute name="optional" value="true"/>
   			<attribute name="maven.pomderived" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry excluding="**" kind="src" output="target/classes" path="src/main/resources">
   		<attributes>
   			<attribute name="maven.pomderived" value="true"/>
   			<attribute name="optional" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry kind="src" output="target/test-classes" path="src/test/java">
   		<attributes>
   			<attribute name="optional" value="true"/>
   			<attribute name="maven.pomderived" value="true"/>
   			<attribute name="test" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry excluding="**" kind="src" output="target/test-classes" path="src/test/resources">
   		<attributes>
   			<attribute name="maven.pomderived" value="true"/>
   			<attribute name="test" value="true"/>
   			<attribute name="optional" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-21">
   		<attributes>
   			<attribute name="maven.pomderived" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry kind="con" path="org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER">
   		<attributes>
   ...
mtron> [-- Read all .txt files --]
mtron> *<mfs:+/+>.where([name => -<'.'>>1.is('txt')])
==>fail::[inst apply failure: java.io.UncheckedIOException: java.nio.file.AccessDeniedException: /backends]@/sys/fail/2980
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<mfs:src/main.java?lineq=10..20>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "10..20"]@/sys/fail/2984
mtron> [-- Replace lines 5-10 with new content --]
mtron> <mfs:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "5..10"]@/sys/fail/2988
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
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "1..50"]@/sys/fail/2992
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
==>fail::[inst apply failure: 'New Title' [str::T] unable to convert uri::T]@/sys/fail/3030
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[inst apply failure: 'new-host' [str::T] unable to convert uri::T]@/sys/fail/3066
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).