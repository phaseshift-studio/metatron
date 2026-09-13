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
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(10 more)]
mtron> bash(cmd=>'whoami', timeout=>second::5.0)
==>['killswitch']
```
Batch over a rec (indexed) or a lst (flat):

```mtron
mtron> {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
==>['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(10 more)]]
==>['whoami'=>['killswitch']]
==>['df -h'=>['Filesystem             Size  Used Avail Use% Mounted on','tmpfs                  6.1G  6.2M  6.1G   1% /run','efivarfs               128K   42K   82K  34% /sys/firmware/...','/dev/nvme1n1p2         916G  490G  379G  57% /','tmpfs                   31G  270M   31G   1% /dev/shm','tmpfs                  5.0M   20K  5.0M   1% /run/lock','tmpfs                   31G     0   31G   0% /run/qemu','/dev/nvme1n1p1         511M  6.2M  505M   2% /boot/efi','tmpfs                  6.1G  248K  6.1G   1% /run/user/1000','/dev/nvme0n1p2         932G  240G  692G  26% /media/hdd0',...(1 more)]]
mtron> ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
==>[
    ['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE',...(10 more)]],
    ['whoami'=>['killswitch']],
    ['df -h'=>['Filesystem             Size  Used Avail Use% Mounted on','tmpfs                  6.1G  6.2M  6.1G   1% /run','efivarfs               128K   42K   82K  34% /sys/firmware/...','/dev/nvme1n1p2         916G  490G  379G  57% /','tmpfs                   31G  270M   31G   1% /dev/shm','tmpfs                  5.0M   20K  5.0M   1% /run/lock','tmpfs                   31G     0   31G   0% /run/qemu','/dev/nvme1n1p1         511M  6.2M  505M   2% /boot/efi','tmpfs                  6.1G  248K  6.1G   1% /run/user/1000','/dev/nvme0n1p2         932G  240G  692G  26% /media/hdd0',...(1 more)]]]
```
Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
mtron> bash('ls').>>.bash("stat -c 'U' ${_}")            [-- drain: owners coalesce to a multiset --]
==>{20}['U']
mtron> bash('ls').mapp(bash("stat -c 'U' ${_}"))         [-- map: one result lst per file --]
==>[['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],['U'],...(10 more)]
mtron> bash('ls').mapp(-<[_=>bash("stat -c 'U' ${_}")])  [-- indexed map: file => owner --]
==>[['AGENTS.md'=>['U']],['bin'=>['U']],['boot'=>['U']],['conf'=>['U']],['CONTRIBUTING.md'=>['U']],['dist'=>['U']],['docs'=>['U']],['dsh-plugins'=>['U']],['language.properties'=>['U']],['LICENSE'=>['U']],...(10 more)]
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
==>2
mtron> */sys/thread/+.=?=[state=>run]       [-- number of active threads --]
==>[
    code=>inst?#{*}<=#{?}(#{*}::T),
    state=>run,
    time=>datetime::<//2026.09:13/15/21/08/439?tz=-0600>,
    runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/main
mtron> */sys/thread/+?docq                  [-- thread documentation     --]
==>docs::[
    obj=>[
     code=>inst?#{*}<=#{?}(#{*}::T),
     state=>run,
     time=>datetime::<//2026.09:13/15/21/08/439?tz=-0600>,
     runtime=>!inst?#{*}<=#{?}(#{*}::T)]@/sys/thread/main,
    desc=>'this root thread waits till all child threads are complete ...']
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
   <head>
       <title>PhaseShift Studio</title>
       <!-- METADATA -->
       <meta charset="utf-8">
       <meta name="viewport" content="width=device-width, initial-scale=1.0">
       <meta name="keywords" content="metatron programming data graph database llm">
       <meta name="description" content="Next Generation Data Technologies">
       <meta name="theme-color" content="#ffffff">
       <!-- FAVICON -->
       <link rel="apple-touch-icon" sizes="114x114" href="images/apple-touch-icon.png">
       <link rel="icon" type="image/png" sizes="32x32" href="images/favicon-32x32.png">
       <link rel="icon" type="image/png" sizes="16x16" href="images/favicon-16x16.png">
       <link rel="mask-icon" href="images/safari-pinned-tab.svg" color="#5bbad5">
       <!-- FONTS -->
       <link rel="preconnect" href="https://fonts.googleapis.com">
       <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
       <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:ital,wght@0,400;0,500;0,600;1,400&family=JetBrains+Mono:ital,wght@0,400;0,500;0,700;1,400&family=Oswald:wght@400;500;600;700&display=swap" rel="stylesheet">
       <!-- CSS PACKAGES -->
       <link href="https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.css" rel="stylesheet"/>
       <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.10.0/css/all.min.css" rel="stylesheet">
       <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css" rel="stylesheet">
       <link href="css/bootstrap.min.css" rel="stylesheet">
       <link href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.5.0/styles/night-owl.min.css" rel="stylesheet">
       <!-- JAVASCRIPT PACKAGES -->
       <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js"
               type="text/javascript"></script>
       <script src="./highlight/highlight.min.js" type="text/javascript"></script>
       <script src="https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/mtron.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/java.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/bash.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/sql.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/json.min.js" type="text/javascript"></script>
       <script src="https://code.jquery.com/jquery-3.4.1.min.js" type="text/javascript"></script>
       <script async src="https://cdn.jsdelivr.net/npm/mathjax@4/tex-mml-chtml.js" type="text/javascript"></script>
       <!-- <script src="data/console.txt"></script>-->
       <!-- CUSTOM CSS/JAVASCRIPT -->
       <link href="css/metatron.css" rel="stylesheet">
       <style>
           .hidden {
               display: none;
           }
   
           .switch {
               border-width: 1px 1px 0 1px;
               border-style: solid;
               border-color: #7a2518;
               display: inline-block;
               border-radius: 4px 4px 0 0;
           }
   
           .switch--item {
               padding: 4px 14px;
               font-size: 0.85rem;
               background-color: #ffffff;
               color: #7a2518;
               display: inline-block;
               cursor: pointer;
           }
   
           .switch--item:first-child {
               border-radius: 3px 0 0 0;
           }
   
           .switch--item:last-child {
               border-radius: 0 3px 0 0;
           }
   
           .switch--item.selected {
               background-color: #7a2519;
               color: #ffffff;
           }
   
           /* click-to-copy download commands: the whole block is the copy target */
           .terminal-box.click-to-copy {
               cursor: pointer;
               -webkit-user-select: none;
               user-select: none;
           }
   
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
   <head>
       <title>PhaseShift Studio</title>
       <!-- METADATA -->
       <meta charset="utf-8">
       <meta name="viewport" content="width=device-width, initial-scale=1.0">
       <meta name="keywords" content="metatron programming data graph database llm">
       <meta name="description" content="Next Generation Data Technologies">
       <meta name="theme-color" content="#ffffff">
       <!-- FAVICON -->
       <link rel="apple-touch-icon" sizes="114x114" href="images/apple-touch-icon.png">
       <link rel="icon" type="image/png" sizes="32x32" href="images/favicon-32x32.png">
       <link rel="icon" type="image/png" sizes="16x16" href="images/favicon-16x16.png">
       <link rel="mask-icon" href="images/safari-pinned-tab.svg" color="#5bbad5">
       <!-- FONTS -->
       <link rel="preconnect" href="https://fonts.googleapis.com">
       <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
       <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:ital,wght@0,400;0,500;0,600;1,400&family=JetBrains+Mono:ital,wght@0,400;0,500;0,700;1,400&family=Oswald:wght@400;500;600;700&display=swap" rel="stylesheet">
       <!-- CSS PACKAGES -->
       <link href="https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.css" rel="stylesheet"/>
       <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.10.0/css/all.min.css" rel="stylesheet">
       <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css" rel="stylesheet">
       <link href="css/bootstrap.min.css" rel="stylesheet">
       <link href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.5.0/styles/night-owl.min.css" rel="stylesheet">
       <!-- JAVASCRIPT PACKAGES -->
       <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js"
               type="text/javascript"></script>
       <script src="./highlight/highlight.min.js" type="text/javascript"></script>
       <script src="https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/mtron.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/java.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/bash.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/sql.min.js" type="text/javascript"></script>
       <script src="./highlight/languages/json.min.js" type="text/javascript"></script>
       <script src="https://code.jquery.com/jquery-3.4.1.min.js" type="text/javascript"></script>
       <script async src="https://cdn.jsdelivr.net/npm/mathjax@4/tex-mml-chtml.js" type="text/javascript"></script>
       <!-- <script src="data/console.txt"></script>-->
       <!-- CUSTOM CSS/JAVASCRIPT -->
       <link href="css/metatron.css" rel="stylesheet">
       <style>
           .hidden {
               display: none;
           }
   
           .switch {
               border-width: 1px 1px 0 1px;
               border-style: solid;
               border-color: #7a2518;
               display: inline-block;
               border-radius: 4px 4px 0 0;
           }
   
           .switch--item {
               padding: 4px 14px;
               font-size: 0.85rem;
               background-color: #ffffff;
               color: #7a2518;
               display: inline-block;
               cursor: pointer;
           }
   
           .switch--item:first-child {
               border-radius: 3px 0 0 0;
           }
   
           .switch--item:last-child {
               border-radius: 0 3px 0 0;
           }
   
           .switch--item.selected {
               background-color: #7a2519;
               color: #ffffff;
           }
   
           /* click-to-copy download commands: the whole block is the copy target */
           .terminal-box.click-to-copy {
               cursor: pointer;
               -webkit-user-select: none;
               user-select: none;
           }
   
   ...
mtron> [-- structural parse via application/x-mtron --]
mtron> *<mfs:docs/website/index.html?mimeq=application/x-mtron>
==>[html=>[
    head=>[
     title=>'PhaseShift Studio',
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
      tag=>link,
      rel=>'icon',
      type=>'image/png',
      sizes=>'32x32',
      href=><images/favicon-32x32.png>],[
      tag=>link,
      rel=>'icon',
      type=>'image/png',
      sizes=>'16x16',
      href=><images/favicon-16x16.png>],[
      tag=>link,
      rel=>'mask-icon',
      href=><images/safari-pinned-tab.svg>,
      color=>'#5bbad5'],[
      tag=>link,
      rel=>'preconnect',
      href=><https://fonts.googleapis.com>],...(20 more)]],
    body=>[
     out=>[
      [
       tag=>div,
       id=>'spinner',
       class=>'show bg-dark position-fixed translate-middle w-100 vh-100 t...',
       out=>[[
    tag=>div,
    class=>'spinner-grow text-primary',
    style=>'width: 3rem; height: 3rem;',
    role=>'status',
    out=>[[
    tag=>span,
    class=>'sr-only',
    text=>'Loading...']]]]],
      [
       tag=>nav,
       class=>'navbar navbar-expand-lg navbar-dark sticky-top py-lg-0 px-l...',
       data-wow-delay=>'0.1s',
       out=>[
        [
         tag=>h2,
         class=>'mb-0 navbar-brand nav-brand ms-4 ms-lg-0',
         out=>[
          [
           tag=>a,
           href=><index.html>,
           title=>'metatron',
           out=>[[
    tag=>img,
    src=><images/favicon-32x32.png>,
    alt=>'PhaseShift Studio']]],
          [
           tag=>a,
           href=><http://phaseshift.studio>,
           text=>'PhaseShift Studio']]],
        [
         tag=>div,
         class=>'collapse navbar-collapse',
         id=>'navbarCollapse',
         out=>[[
    tag=>div,
    class=>'navbar-nav ms-auto p-4 p-lg-0',
    out=>[
     [
      tag=>li,
      class=>'nav-item dropdown',
      out=>[
       [
        tag=>a,
        id=>'download_dropdown',
        class=>'nav-link dropdown-toggle',
        href=><#>,
        role=>'button',
        data-bs-toggle=>'dropdown',
        aria-expanded=>'false',
        text=>'download'],
       [
        tag=>ul,
        class=>'dropdown-menu dropdown-menu-dark',
        aria-labelledby=>'download_dropdown',
        out=>[
         [
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
==>fail::[unable to locate inst-f of mfs:script.sh()@<0>]@/sys/fail/232
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
==><mfs:/.agentbridge>=><mfs:/.agentbridge>
==><mfs:/metatron.iml>=>"""<?xml version="1.0" encoding="UTF-8"?>
   <module version="4">
     <component name="AdditionalModuleElements">
       <content url="file://$MODULE_DIR$" dumb="true">
         <sourceFolder url="file://$MODULE_DIR$/.worktrees/agent/src/main/mpython/py" isTestSource="false" />
         <sourceFolder url="file://$MODULE_DIR$/src/main/js" isTestSource="false" />
         <sourceFolder url="file://$MODULE_DIR$/src/main/mpython" isTestSource="false" />
         <sourceFolder url="file://$MODULE_DIR$/src/main/python" isTestSource="false" />
         <sourceFolder url="file://$MODULE_DIR$/src/test/python" isTestSource="true" />
         <excludeFolder url="file://$MODULE_DIR$/.venv" />
       </content>
     </component>
   </module>"""
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
   ...
mtron> [-- Read all .txt files --]
mtron> *<mfs:+/+>.where([name => -<'.'>>1.is('txt')])
==>fail::[inst apply failure: java.io.UncheckedIOException: java.nio.file.AccessDeniedException: /backends]@/sys/fail/1612
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<mfs:src/main.java?lineq=10..20>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "10..20"]@/sys/fail/1622
mtron> [-- Replace lines 5-10 with new content --]
mtron> <mfs:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "5..10"]@/sys/fail/1634
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
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "1..50"]@/sys/fail/1648
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
==>fail::[inst apply failure: 'New Title' [str::T] unable to convert uri::T]@/sys/fail/1700
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[inst apply failure: 'new-host' [str::T] unable to convert uri::T]@/sys/fail/1748
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).