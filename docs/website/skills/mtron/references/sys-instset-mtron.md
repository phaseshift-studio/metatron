---
name: sys instruction set
description: >
  The `/m/sys` instruction set and the `fsspace::T` half of the same doc: a guarded
    `bash` whose allow/reject/env/dir policy is baked into the inst's own tid,
    `sleep`/`stdout`/`stdin`, the `sys_stat` thread summary, and the file family
    `read_file`/`edit_file`. The second half is the `fsspace::T` tour, mounted on the
    metatron project root: typed reads by MIME, `?mimeq` tagging and structural parse,
    line addressing, tree walking, and text work on a scratch mount. TRIGGER: when
    shelling out (bash guard, timeout), reading or editing file lines, mounting a
    directory as a space, or discovering what a file is before reading it.
---

# sys instruction set (`/m/sys`)

`/sys` is the required system space — created at boot, it holds the system objs
(router, typer, rewriter, environment, thread registry). The sys instset is its
instruction companion: a guarded `bash`, the blocking I/O primitives `sleep`,
`stdout`, and `stdin`, the `sys_stat` thread summary, and a file family —
`read_file`, `edit_file` — that operates on files mounted by a `fsspace::T`.

The `fsspace::T` half occupies the rest of the doc: a directory, mounted and given the
mime and line query processors, becomes a **typed** file system.

Nothing here presumes a boot-time space. Each space the examples need is loaded by the
doc itself, in the block that introduces it — read those blocks the way the examples
read them.

## instructions

| inst       | dom → rng           | args                                       | what it does                                |
|------------|---------------------|--------------------------------------------|---------------------------------------------|
| `bash`     | `#{?} → lst[str]`   | `cmd`, `timeout?`                          | guarded shell (`bash -c`), stdout as lines  |
| `sleep`    | `A{?} → A{?}`       | `time`                                     | pause the thread, pass lhs through          |
| `stdout`   | `#{?} → #{?}`       | `obj`                                      | print the arg's jvm obj, pass lhs through   |
| `stdin`    | `#{?} → str`        | —                                          | read one line from terminal input           |
| `sys_stat` | `#{?} → rec`        | —                                          | the thread executor's own summary           |
| `read_file`| `#{?} → lst`        | `file`, `min?`, `max?`                     | a file's lines, indexed, or the `min..max` slice |
| `edit_file`| `#{?} → rec`        | `file`, `text`, `min`, `max?`              | insert at `min` (or replace `min..max`), and report |

## bash (`/m/sys/inst/bash`)

The function-call primitive: reach into the shell with `bash`, shape the result with
metatron's data structures. `cmd` runs under `bash -c`, in the VM's working directory —
in the docs build that is the metatron project root — and returns a `lst[str]`, one
entry per stdout line. A non-zero exit is a `fail::T` that carries the process's stderr
in its message. `timeout` takes a time type — `second::5.0`, `millis::1000.0` — or a
bare int, which is seconds; the default is `second::20.0`.

The signature is not a memory exercise; the inst carries its own doc:

```mtron
mtron> *bash?docq
==>docs::[obj=>bash?rng=lst[str]&dom=#{?}(cmd=>str::T,{?}timeout=>union(time::T,int::T)){<j>},dom=>'maybe an obj',rng=>'a lst[str] of results',args=>[cmd=>"the terminal command to evaluate (uses bash('-c',${cmd}) behind the scenes)",{?}timeout=>"""a real number denoting timeout of the process (default: /m/math/time/second::20.0).
   """],desc=>"""evaluate bash command. *important* the timeout argument takes a real not an int -- e.g. millis::1000.0 or second::1.0.
   note that this field is optional, so when in doubt, just don't fill it out
   """,example=>["""bash('ls')                                           [-- return lst containing each file/dir as str::T --]
   {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- batch bash results indexed by cmd             --]
   ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- same as above but with lst of cmds            --]
   """]]
```
```mtron
mtron> bash('ls')
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE','metatron.ide.mtron','mvnw','mvnw.cmd','node_modules','pom.xml','README.md','RELEASE.md','REVIEW-FULL.md','REVIEW-FULL.md.bak','REVIEW-METATRON.md','src','target']
mtron> bash(cmd=>'whoami')
==>['ubuntu']
mtron> bash('df -h')
==>['Filesystem      Size  Used Avail Use% Mounted on','overlay         916G  496G  374G  58% /','tmpfs            64M     0   64M   0% /dev','shm              64M     0   64M   0% /dev/shm','/dev/nvme1n1p2  916G  496G  374G  58% /work','tmpfs            31G     0   31G   0% /proc/acpi','tmpfs            31G     0   31G   0% /proc/asound','tmpfs            31G     0   31G   0% /proc/scsi','tmpfs            31G     0   31G   0% /sys/devices/virtual/powercap','tmpfs            31G     0   31G   0% /sys/firmware']
```
A timeout and a failed exit are both fails, and both are inspectable:

```mtron
mtron> bash(cmd=>'sleep 5', timeout=>millis::500.0)  [-- the timeout kills the process --]
==>fail::[inst apply failure: org.buildobjects.process.TimeoutException: Process 'bash -c 'sleep 5'' timed out after 500ms. (at /m/sys/inst/bash@0) [Proc<155>]][Process 'bash -c 'sleep 5'' timed out after 500ms. [Proc<155>]]@/sys/fail/78
mtron> bash('ls /no/such/directory')                 [-- non-zero exit, stderr in the message --]
==>fail::[inst apply failure: org.buildobjects.process.ExternalProcessFailureException: External process `bash` terminated with unexpected exit status 2 after 3ms:
     $ bash -c 'ls /no/such/directory'
     STDERR: ls: cannot access '/no/such/directory': No such file or directory
    (at /m/sys/inst/bash@0) [ProcBuilder<228>]][External process `bash` terminated with unexpected exit status 2 after 3ms:
     $ bash -c 'ls /no/such/directory'
     STDERR: ls: cannot access '/no/such/directory': No such file or directory
    [ProcBuilder<228>]]@/sys/fail/82
```
### batch

A rec of commands maps to a rec of results, indexed by command; a lst of commands takes
the same `==` projection:

```mtron
mtron> {"ls", "whoami"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
==>['ls'=>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE','metatron.ide.mtron','mvnw','mvnw.cmd','node_modules','pom.xml','README.md','RELEASE.md','REVIEW-FULL.md','REVIEW-FULL.md.bak','REVIEW-METATRON.md','src','target']]
==>['whoami'=>['ubuntu']]
mtron> ["ls", "whoami"]==[_ => bash(_)]>>.sum()       [-- lst of cmds => one flat lst --]
==>['AGENTS.md','bin','boot','conf','CONTRIBUTING.md','dist','docs','dsh-plugins','language.properties','LICENSE','metatron.ide.mtron','mvnw','mvnw.cmd','node_modules','pom.xml','README.md','RELEASE.md','REVIEW-FULL.md','REVIEW-FULL.md.bak','REVIEW-METATRON.md','src','target','ubuntu']
```
`==` is a **select** — one branch per slot of the poly, the rec's value the projection
applied to each. The glyphs are the actions, and the sugar says so in plain sight:

```mtron
mtron> ["ls", "whoami"]==[_ => bash(_)]>>.sum().explain()
==>"""
    op      dom          rng      args                f    desc      c_dom  c_rng 
    start   noobj{0}::T  lst::T   ['ls','whoami']     <j>  initial   {0}    {1}   
    select  lst::T       lst::T   [id()=>bash(id())]  <j>  mapper    {1}    {1}   
    rshift  lst::T       #{*}::T  noobj               <j>  standard  {1}    {*}   
    sum     #{*}::T      #::T                         <?>  reducer   {*}    {1}   
   """
```
### pipe over the results

`>>` moves right along the data — one step per element; `${_}` binds the current one.
The projection can do the work: first stat line of each entry, nothing else:

```mtron
mtron> bash('ls')==[_ => bash("stat ${_}")>>0]          [-- each entry => its `File:` line --]
==>['  File: AGENTS.md','  File: bin','  File: boot','  File: conf','  File: CONTRIBUTING.md','  File: dist','  File: docs','  File: dsh-plugins','  File: language.properties','  File: LICENSE','  File: metatron.ide.mtron','  File: mvnw','  File: mvnw.cmd','  File: node_modules','  File: pom.xml','  File: README.md','  File: RELEASE.md','  File: REVIEW-FULL.md','  File: REVIEW-FULL.md.bak','  File: REVIEW-METATRON.md','  File: src','  File: target']
mtron> bash('ls').>>.bash("stat ${_}")    [-- drain: the full stat per entry --]
==>['  File: AGENTS.md','  Size: 34192     	Blocks: 72         IO Block: 4096   regular file','Device: 259,3	Inode: 26348579    Links: 1','Access: (0664/-rw-rw-r--)  Uid: ( 1000/  ubuntu)   Gid: ( 1000/  ubuntu)','Access: 2026-09-25 19:17:42.070049402 +0000','Modify: 2026-09-24 18:53:23.043502578 +0000','Change: 2026-09-24 18:53:23.044502554 +0000',' Birth: 2026-09-24 18:53:23.043502578 +0000']
==>['  File: bin','  Size: 4096      	Blocks: 8          IO Block: 4096   directory','Device: 259,3	Inode: 22826424    Links: 5','Access: (0775/drwxrwxr-x)  Uid: ( 1000/  ubuntu)   Gid: ( 1000/  ubuntu)','Access: 2026-09-25 00:07:43.360888917 +0000','Modify: 2026-09-20 22:31:57.121101695 +0000','Change: 2026-09-20 22:31:57.121101695 +0000',' Birth: 2025-11-08 21:38:11.425442859 +0000']
==>['  File: boot','  Size: 4096      	Blocks: 8          IO Block: 4096   directory','Device: 259,3	Inode: 28469828    Links: 4','Access: (0775/drwxrwxr-x)  Uid: ( 1000/  ubuntu)   Gid: ( 1000/  ubuntu)','Access: 2026-09-25 17:46:46.398082694 +0000','Modify: 2026-09-14 02:30:49.310909041 +0000','Change: 2026-09-14 02:30:49.310909041 +0000',' Birth: 2026-01-16 18:53:47.513549189 +0000']
==>['  File: conf','  Size: 4096      	Blocks: 8          IO Block: 4096   directory','Device: 259,3	Inode: 23107074    Links: 3','Access: (0775/drwxrwxr-x)  Uid: ( 1000/  ubuntu)   Gid: ( 1000/  ubuntu)','Access: 2026-09-25 17:46:46.399082679 +0000','Modify: 2026-09-14 05:55:17.159928081 +0000','Change: 2026-09-14 05:55:17.159928081 +0000',' Birth: 2026-05-04 22:27:26.799073113 +0000']
==>['  File: CONTRIBUTING.md','  Size: 6984      	Blocks: 16         IO Block: 4096   regular file','Device: 259,3	Inode: 22879108    Links: 1','Access: (0664/-rw-rw-r--)  Uid: ( 1000/  ubuntu)   Gid: ( 1000/  ubuntu)','Access: 2026-09-24 18:43:26.827307490 +0000','Modify: 2026-07-29 14:27:08.684000000 +0000','Change: 2026-08-19 19:14:16.914365198 +0000',' Birth: 2026-06-05 04:49:54.746480453 +0000']
   ...
```
### a shape of its own

`bash` supplies the functions; metatron supplies the shape. Each top-level entry's
`Size`, extracted and typed `bB::T`, then converted to `kB::T` — the unit system
converts against itself, so no `awk`, `grep`, or `du`:

```mtron
mtron> bash('ls')==[_ => bash('stat ${_} | sed -n "s/.*Size: \([0-9]*\).*/\1/p"')>>0.as?int<=str(int::T).as(bB::T)]
==>[bB::34192.0,bB::4096.0,bB::4096.0,bB::4096.0,bB::6984.0,bB::4096.0,bB::4096.0,bB::4096.0,bB::7304.0,bB::34523.0,bB::107.0,bB::11790.0,bB::8481.0,bB::4096.0,bB::50823.0,bB::150.0,bB::2624.0,bB::76171.0,bB::66247.0,bB::16576.0,bB::4096.0,bB::4096.0]
```
Unit values test against each other's units:

```mtron
mtron> bB::34192.0.gt(kB::30.0)
==>true
```
And they filter a lst by the same predicate — the branches that fail are dropped:

```mtron
mtron> [bB::34192.0, bB::100.0]==[_ => ?>kB::30.0]==[_ => else(none)]
==>[bB::34192.0]
```
### security modulators (q-params)

`bash` is hardened at the **instruction**, not the call site: `allow`, `reject`,
`env`, and `dir` attach as query parameters on the inst's own tid — a policy baked in
once rather than negotiated per call. Query parameters are metatron's way of
annotating an inst at its tid; `?*` is the door, and these are the ones agents meet
first.

| q-param  | type            | semantics                                                |
|----------|-----------------|----------------------------------------------------------|
| `allow`  | `lst[str]`      | whitelist regexes, matched whole-command (`matches()`)   |
| `reject` | `lst[str]`      | blacklist regexes, matched anywhere (`find()`)           |
| `env`    | `rec[str=>str]` | environment variables injected into the process          |
| `dir`    | `str`           | the process's working directory                           |

Each guard fails before the process spawns, and the failure names the pattern that
fired:

```mtron
mtron> bash?reject=['\brm\b']("rm -rf /tmp/never-created-here")  [-- the policy, not the file system, stops it --]
==>fail::[inst apply failure: reject patterns match command: rm -rf /tmp/never-created-here in \brm\b (at /m/sys/inst/bash@0)]@/sys/fail/910
mtron> bash?allow=['ls']("whoami")                                [-- allow is whole-command: `whoami` is not `ls` --]
==>fail::[inst apply failure: allowed patterns do not match command: whoami not in ['ls'] (at /m/sys/inst/bash@0)]@/sys/fail/914
```
The allowed form passes — the pattern must match the whole command, and may be a regex — and the env lands in the process:

```mtron
mtron> bash?allow=['ls .+']('ls AGENTS.md')
==>['AGENTS.md']
mtron> bash?env=[CI => 'docs']('echo CI=$CI')
==>['CI=docs']
```
## sleep / stdout / stdin

```mtron
mtron> sleep(second::1.0)                       [-- one second, then lhs passes through --]
mtron> stdout("the sleep above took one second")
```
`stdin()` blocks for one line of terminal input. In the headless docs build there is
no input to take, so it is shown rather than run:

```mtron
stdin()    [-- block for one line, emit it as str::T --]
```

## the registry under /sys

One read each: the environment, the thread count, and the executor's own summary —
`sys_stat` answers with running vs stopped, no introspection ceremony:

```mtron
mtron> */sys/env/HOME
==>'/work'
mtron> */sys/thread/+.count()
==>2
mtron> sys_stat()
==>[run=>1,stop=>0]
```
# file system space (`fsspace::T`)

An `fsspace::T` mounts a directory into the metatron graph. A file's uri is
`<scheme:path>`; a read returns the file's content **typed by its MIME** — the type is
a predicate on the content.

**IMPORTANT**: any uri may be wrapped in `< >`, but the brackets are *required* when
the uri carries a `.`, a space, a `~`, or a `?`. `/a/b/c` writes bare;
`<mfs:pom.xml>` and `<mfs:AGENTS.md?lineq=1-3>` must be bracketed.

## the space these examples use

The examples read the live metatron project — this very tree — and never write to it.
The route anchors on `<.>`, the cwd of the process, which in the docs build is the
project root:

```mtron
mtron> fsspace::[
         pattern => <mfs:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [mfs: => <.>]]@/sys/space/mfs
==>fsspace::[pattern=>mfs:#,q=>[mimeq::[pattern=>mimeq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}],lineq::[pattern=>lineq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}]],route=>[mfs:=><>]]@/sys/space/mfs
```
The three keys: `pattern` is the uri space this instance owns (`mfs:#`, `#` the
recursive wildcard); `route` maps the `mfs:` prefix onto the path the files live at;
and `q` is the space's **query processors** — `mimeq` tags and structurally parses
reads, `lineq` addresses lines.

Writes go to a second mount, a scratch directory the docs build owns — made by
`bash`, which is exactly the point of keeping the two halves of this doc in one
conversation:

```mtron
mtron> bash('mkdir -p /tmp/mtron-docs-scratch')
==>['']
mtron> fsspace::[
         pattern => <scratch:#>,
         q       => [lineq::[=>]],
         route   => [scratch: => /tmp/mtron-docs-scratch]]@/sys/space/scratch
==>fsspace::[pattern=>scratch:#,q=>[lineq::[pattern=>lineq,post_read=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>}]],route=>[scratch:=>/tmp/mtron-docs-scratch]]@/sys/space/scratch
```
## typed reads

```mtron
mtron> *<mfs:README.md>.tid()          [-- markdown's mime --]
==>/m/web/mime/markdown
mtron> *<mfs:pom.xml>.tid()            [-- xml's mime --]
==>/m/web/mime/xml
mtron> *<mfs:boot/docs.mtron>.tid()    [-- a .mtron file reads as the code it is --]
==>/m/rec
```
Three content types, one `*` — the tid is the referent's claim, and `.tid()` is how
it is read out. A `.mtron` file is not lines: it parses into the objs it declares, so
its referent is a `rec::T`, not a `str::T`. The probe defaults a file with no
extension to the same `application/x-mtron` type — which is a file named `lines` an
invitation to be parsed as code, and a `lines.txt` a note.

## `?mimeq` — tagging, and the structural read

`?mimeq` is a post-read query processor: it tags the string with the MIME's tid — the
tag *is* the predicate validation — and, for `application/x-mtron`, parses the content
into its structural form. Where in doubt, `?docq` and `.explain()` say what an inst
is doing — the sugar is short precisely because it is legible once:

```mtron
mtron> *<mfs:README.md?mimeq=text/markdown>                    [-- explicit tag, same referent typed --]
...
*<mfs:boot/docs.mtron>                   [-- the doc boot, read as its code --]
==>[space=>/sys/space,web=>[http/host=>http://localhost:8777,ws/host=>ws://localhost:8555],typer/stage=>[inst_dom=>true,inst_rng=>true,type_ctor=>true,obj_write=>true,code_resolve=>false],header=>"""
   _,.---._      _,.----.    ,-,--.
   _,..---._   ,-.' , -  `.  .' .' -   \ ,-.'-  _\
   /==/,   -  \ /==/_,  ,  - \/==/  ,  ,-'/==/_ ,_.'
   |==|   _   _\==|   .=.     |==|-   |  .\==\  \
   |==|  .=.   |==|_ : ;=:  - |==|_   `-' \\==\ -\
   |==|,|   | -|==| , '='     |==|   _  , |_\==\ ,\
   |==|  '='   /\==\ -    ,_ /\==\.       /==/\/ _ | .=.
   |==|-,   _`/  '.='. -   .'  `-.`.___.-'\==\ - , /:=; :
   `-.`.____.'     `--`--''                `--`---'  `=`
   ...
```
## walking the tree

Wildcards are space-side. `+` is one segment, `#` is the recursion:

```mtron
mtron> *<mfs:src/main/java/+/>                  [-- the child of src/main/java --]
==>mfs:src/main/java/studio=>mfs:src/main/java/studio
mtron> *<mfs:docs/skills/mtron/+/>  [-- this doc's siblings, with their content --]
...
```
## text work on the tree

A file is a string and a string splits: `-<` divides on the separator, and the rec
decides which pieces survive — here, everything before line 10:

```mtron
mtron> *<mfs:AGENTS.md>.-<'\n'==[?>10 => none, _ => _]
==>['# metatron — AGENTS.md','','A distributed data-oriented computing language and virtual machine built in Java. Two key terms:','','- **metatron** (lowercase): the runtime system / VM environment','- **mtron** (lowercase): the functional programming language (like Java to JVM)','','---','','## Strict Rules','']
```
## line addressing — on the scratch mount

The scratch mount is where the doc writes. `bash` authors the file in the shell's own
voice — and names it `lines.txt`, because the probe reads an extension-less file as
`application/x-mtron` code, while a `.txt` name is what makes prose prose. The space
half then addresses the same file by line: `N` or `A-B` for a slice, `N+` to insert
before line N, `+` to append; a read takes the slice, a write replaces it:

```mtron
mtron> bash('printf "the metatron docs pipeline\nevaluates this block\nand inlines the result" > /tmp/mtron-docs-scratch/lines.txt')
==>['']
mtron> *<scratch:lines.txt?lineq=1>                     [-- line two, zero-indexed --]
==>'evaluates this block'
mtron> <scratch:lines.txt?lineq=1> -> "rewrote line two"  [-- replace --]
==>'rewrote line two'
mtron> *<scratch:lines.txt>                                  [-- the file after the write --]
==>"""the metatron docs pipeline
   rewrote line two
   and inlines the result"""
```
## the file family — `read_file`, `edit_file`

The same lines, as instructions, when the shape of the answer matters. A uri with a
dot in its name takes the angle brackets, even as an argument. `read_file` indexes its
lines:

```mtron
mtron> read_file(file=><scratch:lines.txt>, min=>0, max=>2)
==>[[0,'the metatron docs pipeline'],[1,'rewrote line two']]
```
`edit_file` states the write as arguments — insert at `min`, replace the `min..max`
span when `max` is given — and answers with a status report:

```mtron
mtron> edit_file(file=><scratch:lines.txt>, text=>'added by edit_file', min=>1, max=>1)
==>[status=>success,obj=>!*<scratch:lines.txt>,start_line_count=>5,inserted_line_count=>1,end_line_count=>5]
```
A rec written as data and read back — the round trip closes the scratch story:

```mtron
mtron> scratch:meta -> [doc => 'sys-instset', revision=>2]
==>[doc=>'sys-instset',revision=>2]
mtron> *scratch:meta
==>[doc=>'sys-instset',revision=>2]
```
## a file as an instruction

A file that is executable is read back as an `inst::T` — the space exposes the call,
and the type carries where the space stood when it served it. `bin/metatron` shows
the shape; it is read here, never run:

```mtron
mtron> *<mfs:bin/metatron>
...
mtron> *<mfs:bin/metatron>.tid()
==>/sys/space/mfs/exec/metatron?rng=#{*}&dom=#{?}
```
## taking the mounts down

The doc leaves the graph as it found it — the two mounts it added are removed, and
the scratch directory goes with `bash`, the tool that made it:

```mtron
mtron> /sys/space/scratch -> noobj
mtron> /sys/space/mfs     -> noobj
mtron> bash('rm -rf /tmp/mtron-docs-scratch')
==>['']
```
## see also

* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, `.as(type::T)`.
* [mtron language reference](language-reference-mtron.md) — `>>`, the `?`-family filters, select (`==`), split (`-<`), `->` write.
* [web instruction set](web-instset-mtron.md) — the route tables an `fsspace` mount hangs behind.
* [tble instruction set](tble-instset-mtron.md) — the same space protocol, on a database.