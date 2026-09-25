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
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE','metatron.ide.mtron','mvnw','mvnw.cmd','node_modules','pom.xml','README.md','RELEASE.md','REVIEW-FULL.md','REVIEW-FULL.md.bak','REVIEW-METATRON.md','src','target']
mtron> bash(cmd=>'whoami', timeout=>second::5.0)
==>['killswitch']
```
Batch over a rec (indexed) or a lst (flat):

```mtron
mtron> {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
==>['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE','metatron.ide.mtron','mvnw','mvnw.cmd','node_modules','pom.xml','README.md','RELEASE.md','REVIEW-FULL.md','REVIEW-FULL.md.bak','REVIEW-METATRON.md','src','target']]
==>['whoami'=>['killswitch']]
==>['df -h'=>['Filesystem             Size  Used Avail Use% Mounted on','tmpfs                  6.1G  6.3M  6.1G   1% /run','efivarfs               128K   42K   82K  34% /sys/firmware/efi/efivars','/dev/nvme1n1p2         916G  501G  369G  58% /','tmpfs                   31G  222M   31G   1% /dev/shm','tmpfs                  5.0M   20K  5.0M   1% /run/lock','tmpfs                   31G     0   31G   0% /run/qemu','/dev/nvme1n1p1         511M  6.2M  505M   2% /boot/efi','tmpfs                  6.1G  252K  6.1G   1% /run/user/1000','/dev/nvme0n1p2         932G  240G  692G  26% /media/hdd0','//192.168.1.72/beast1  1.8T  1.7T  145G  93% /srv/beast/hdd1']]
mtron> ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
==>fail::[lhs range does not match inst domain: fail::T => lst{*}::T [sum?rng=lst&dom=lst{*}(){<j>}@<2>]]@/sys/fail/1862
```
Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
mtron> bash('ls').>>.bash("stat -c 'U' ${_}")            [-- drain: owners coalesce to a multiset --]
==>{22}['U']
mtron> bash('ls').mapp(bash("stat -c 'U' ${_}"))         [-- map: one result lst per file --]
==>fail::[unable to locate inst-f of mapp(bash("stat -c 'U' ${_}"))@<1>]@/sys/fail/1890
mtron> bash('ls').mapp(-<[_=>bash("stat -c 'U' ${_}")])  [-- indexed map: file => owner --]
==>fail::[unable to locate inst-f of mapp(split([id()=>bash("stat -c 'U' ${_}")]))@<1>]@/sys/fail/1894
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
==>14
mtron> */sys/thread/+.=?=[state=>run]       [-- number of active threads --]
==>ERROR: infinite recursion detected in parser: parser consumed 0 characters at '=?=[state=>run]'
mtron> */sys/thread/+?docq                  [-- thread documentation     --]
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},source=>!*/sys/thread/main,state=>stop,time=>datetime::<//2026.09:25/06/48/15/688?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/e6c46929,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},start=>noobj,state=>stop,time=>datetime::<//2026.09:25/06/47/48/261?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/main,desc=>'this root thread waits till all child threads are complete and then releases a latch to initiate metatron shutdown procedure']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/679?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/95f054eb,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/699?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/1f48b4e0,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/693?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/9d8e346b,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/668?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/62eeaa16,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/691?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/a61703a2,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},source=>!*/sys/thread/main,state=>stop,time=>datetime::<//2026.09:25/06/48/15/695?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/208f7b1c,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/697?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/1e6fa8e6,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},source=>!*/sys/thread/main,state=>stop,time=>datetime::<//2026.09:25/06/48/15/677?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/a8668665,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},source=>!*/sys/thread/main,state=>stop,time=>datetime::<//2026.09:25/06/48/15/650?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/d1b68533,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/681?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/f978cc81,desc=>'metatron-thread']
==>docs::[obj=>core::[code=>inst?rng=#{*}&dom=#{?}(){<j>},state=>stop,time=>datetime::<//2026.09:25/06/48/15/670?tz=-0600>,runtime=>!inst?rng=#{*}&dom=#{?}(){<j>},result=>noobj]@/sys/thread/a37def8e,desc=>'metatron-thread']
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
==>fsspace::[pattern=>mfs:#,q=>[mimeq::[pattern=>mimeq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}],lineq::[pattern=>lineq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}]],route=>[mfs:=>/home/killswitch/software/metatron]]@/sys/space/fs/mfs
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
==>[html=>[head=>[title=>'metatron',out=>[[tag=>meta,charset=>'utf-8'],[tag=>meta,name=>'viewport',content=>'width=device-width, initial-scale=1.0'],[tag=>meta,name=>'keywords',content=>'metatron programming data graph database llm'],[tag=>meta,name=>'description',content=>'Next Generation Data Technologies'],[tag=>meta,name=>'theme-color',content=>'#ffffff'],[tag=>link,rel=>'apple-touch-icon',sizes=>'114x114',href=><images/apple-touch-icon.png>],[tag=>link,rel=>'icon',type=>'image/png',sizes=>'32x32',href=><images/favicon-32x32.png>],[tag=>link,rel=>'icon',type=>'image/png',sizes=>'16x16',href=><images/favicon-16x16.png>],[tag=>link,rel=>'mask-icon',href=><images/safari-pinned-tab.svg>,color=>'#5bbad5'],[tag=>link,rel=>'preconnect',href=><https://fonts.googleapis.com>],[tag=>link,rel=>'preconnect',href=><https://fonts.gstatic.com>,crossorigin=>''],[tag=>link,href=><https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:ital,wght@0,400;0,500;0,600;1,400;JetBrains+Mono:ital,wght@0,400;0,500;0,700;1,400;Oswald:wght@400;500;600;700&display=swap>,rel=>'stylesheet'],[tag=>link,href=><https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.css>,rel=>'stylesheet'],[tag=>link,href=><https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.10.0/css/all.min.css>,rel=>'stylesheet'],[tag=>link,href=><https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css>,rel=>'stylesheet'],[tag=>link,href=><css/bootstrap.min.css>,rel=>'stylesheet'],[tag=>link,href=><https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.5.0/styles/night-owl.min.css>,rel=>'stylesheet'],[tag=>script,src=><https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/highlight.min.js>,type=>'text/javascript'],[tag=>script,src=><https://unpkg.com/highlightjs-copy/dist/highlightjs-copy.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/languages/mtron.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/languages/java.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/languages/bash.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/languages/sql.min.js>,type=>'text/javascript'],[tag=>script,src=><highlight/languages/json.min.js>,type=>'text/javascript'],[tag=>script,src=><https://code.jquery.com/jquery-3.4.1.min.js>,type=>'text/javascript'],[tag=>script,async=>'',src=><https://cdn.jsdelivr.net/npm/mathjax@4/tex-mml-chtml.js>,type=>'text/javascript'],[tag=>link,href=><css/metatron.css>,rel=>'stylesheet'],[tag=>style,data=>"""
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
==>[type=>doc,out=>[[type=>head,level=>1,text=>'metatron',out=>[[type=>text,content=>'metatron']]],[type=>p,text=>"""<a href="http://metatron.phaseshift.studio"><img src="http://metatron.phaseshift.studio/images/metatron-character.png" width="200px"></a>
   """,out=>[[type=>html_inline,html=>'<a href="http://metatron.phaseshift.studio">'],[type=>html_inline,html=>'<img src="http://metatron.phaseshift.studio/images/metatron-character.png" width="200px">'],[type=>html_inline,html=>'</a>']]]]]
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
==>fail::[unable to locate inst-f of mfs:script.sh()@<0>]@/sys/fail/1902
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
   			<attribute name="maven.pomderived" value="true"/>
   		</attributes>
   	</classpathentry>
   	<classpathentry kind="src" path="target/generated-sources/annotations">
   ...
mtron> [-- Read all .txt files --]
mtron> *<mfs:+/+>.where([name => -<'.'>>1.is('txt')])
==>fail::[inst apply failure: java.io.UncheckedIOException: java.nio.file.AccessDeniedException: /backends (at /m/inst/from@0) [UnixException<90>]][java.nio.file.AccessDeniedException: /backends [UnixException<90>]]@/sys/fail/2378
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<mfs:src/main.java?lineq=10..20>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "10..20" (at /m/inst/from@0) [NumberFormatException<67>]][For input string: "10..20" [NumberFormatException<67>]]@/sys/fail/2382
mtron> [-- Replace lines 5-10 with new content --]
mtron> <mfs:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "5..10" (at /m/inst/ref@1) [NumberFormatException<67>]][For input string: "5..10" [NumberFormatException<67>]]@/sys/fail/2386
```
### boot configuration example

```mtron
mtron> fsspace::[
         pattern => <local:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [local: => ~/src]]@/sys/space/fs/src
==>fsspace::[pattern=>local:#,q=>[mimeq::[pattern=>mimeq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}],lineq::[pattern=>lineq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}]],route=>[local:=>/m/inst/thread(/src)]]@/sys/space/fs/src
mtron> [-- Then use in expressions: --]
mtron> *<local:Main.java?lineq=1..50>
==>fail::[inst apply failure: java.lang.NumberFormatException: For input string: "1..50" (at /m/inst/from@0) [NumberFormatException<67>]][For input string: "1..50" [NumberFormatException<67>]]@/sys/fail/2390
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
==>fail::[inst apply failure: 'New Title' [str::T] unable to convert uri::T (at /m/inst/at@2)]@/sys/fail/2428
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[inst apply failure: 'new-host' [str::T] unable to convert uri::T (at /m/inst/at@2)]@/sys/fail/2464
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).