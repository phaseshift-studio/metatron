---
name: ide
description: metatron agent coding harness ide
---

# agent ide: source edits through the uri graph

**IMPORTANT**: If you are not encoded in metatron and thus, can't speak executable mtron code, you will need to use the
mcp server tool `eval` (`m_inst_eval_mtron`).

An agent edits a project's source by **pulling** a file into the code space, **editing** the rec-encoded member's, and
letting a **subscription** on `code/#` write back to disk and **log** a `saved` event.

## setup: build the infrastructure

First, load necessary instruction sets. Second, open three spaces:

1. a location to store metatron encoding the source code (`memspace`).
2. access to the file system where source code is stored (`fsspace`).
3. an optional space for storing mutation event logs (`tblespace`).

```mtron
mtron> import(/m/ide,ide)
mtron> import(/m/web,web)
mtron> import(/m/math,math)
mtron> memspace::[
         pattern => </dev/scratch/#>,
               q => [mintq::[=>],docq::[=>],subq::[=>],
                     mimeq::[=>], lineq::[=>],lockq::[=>],
                     incrq::[=>]]]@</sys/space/dev/metatron>
mtron> fsspace::[pattern      => mfs:#,
                  route       => [mfs:=><.>]]@/sys/space/fs/mfs
mtron> tblespace::[pattern    => </log/scratch/#>,
                   host       => <sqlite:target/log_scratch.sqlite>,
                   driver     => <org.sqlite.JDBC>,
                   table      => [,],
                   q          => [incrq::[=>],subq::[=>],mimeq::[=>]],
                   route      => [/log/scratch/ => <>]]@</sys/space/log/scratch>
```
## encodings

A loaded Java file has two encodings:

- **rec encoding** — each member is a rec: `kind`, `name`, `signature`,
  `header` (up to `{`), `body` (`{...}`), `footer`, and `text` = **derived** (header + body + footer).
- **uri-graph (`idx`) encoding** — navigate the source with path syntax:
  `*/dev/metatron/idx/memSpace/method/close`.

The pull (`src/.../${class}()`) returns the class's **rec encoding** (the
`ide:java::T` value) and stores it in the `code` list beside the project root.
`ide:index(root)` re-projects that code list into `root/idx`:
`class => kind => name => !@.../code/N/classes/cls/0/members/i/name` — *anchors* pointing into the code space. That
anchor is the write surface, and it also resolves back through the space to the stored member (the round trip is
asserted in the acceptance test).

| field       | edit to change                      |
|-------------|-------------------------------------|
| `signature` | the method signature                |
| `header`    | declaration/annotations (up to `{`) |
| `body`      | the method body (`{...}`)           |
| `footer`    | trailing bits                       |
| `text`      | **derived** -- do not hand-edit     |

## writing code in mtron

An example Java/Maven3 project is provided with metatron. This project is used for the following examples.

```mtron
mtron> *<mfs:src/test/resources/scratch/pom.xml>.
         as(rec::T).
         repeat(code=>>>,until=><<.-<[has(Id),has(version)]>-,emit=>false).take(3)
==>groupId=>[node=>[,],text=>'com.example.scratch']
==>artifactId=>[node=>[,],text=>'scratch']
==>version=>[node=>[,],text=>'0.1.0-SNAPSHOT']
```
A `project::T` consolidates source code, various space embeddings, and build commands under one `rec::T`. There exists
an `as()`-mapping from `uri::T` to `project::T`. The resultant `project::T` is saved to the user home graph. Finally.
maven build commands are attached to the `project::T` for each of access.

```mtron
mtron> <mfs:src/test/resources/scratch>@</dev/scratch>.as(project::T).to(/dev/scratch)
       @/dev/scratch >>= +[command => [mvn_build => !ide:command('mvn -f src/test/resources/scratch compile'),
                                       mvn_clean => !ide:command('mvn -f src/test/resources/scratch clean'),
                                       mvn_exec  => !ide:command('mvn -f src/test/resources/scratch compile exec:java')]]
==>project::[
    root=>mfs:src/test/resources/scratch,
    src=>[
     Operation=>inst?#{*}<=#{?}(#{*}::T),
     Echo=>inst?#{*}<=#{?}(#{*}::T),
     Calculator=>inst?#{*}<=#{?}(#{*}::T),
     EchoTest=>inst?#{*}<=#{?}(#{*}::T)],
    code=>[,],
    idx=>[=>],
    command=>[
     mvn_build=>!ide:command('mvn -f src/test/resources/scratch compile'),
     mvn_clean=>!ide:command('mvn -f src/test/resources/scratch clean'),
     mvn_exec=>!ide:command('mvn -f src/test/resources/scratch compile exec:java')]]
```
Now that the project is stored in space, a quick build to ensure a clean slate to work from.

```mtron
mtron> */dev/scratch/command/mvn_build
==>result::[
    status=>success,
    runtime=>millis::895.0000,
    command=>'mvn -f src/test/resources/scratch compile',
    output=>!*/sys/tmp/e52e924c]
```
The Java source files have a `str::T > web:java::T` encoding accessible via `src`.

```mtron
```
The file name serves as the key and an lambda `inst` serves as a lazy constructor of an `ide:java::T`. Calling the file
name pulls the raw
`src` into both `code` and `idx`.

```mtron
mtron> /dev/scratch/src/Echo()
==>[Echo=>[
    field=>[
     PREFIX=>!@/dev/scratch/code/0/classes/Echo/0/members/0/PREFIX,
     name=>!@/dev/scratch/code/0/classes/Echo/0/members/1/name],
    constructor=>[Echo=>!@/dev/scratch/code/0/classes/Echo/0/members/2/Echo],
    comment=>[=>],
    method=>[
     speak=>!@/dev/scratch/code/0/classes/Echo/0/members/4/speak,
     name=>!@/dev/scratch/code/0/classes/Echo/0/members/6/name]]]
mtron> */dev/scratch/code/0
==>java::[
    package=>'package com.example.scratch;',
    preamble=>"""package com.example.scratch;
   
   /**
    * A simple greeter used ...""",
    classes=>[Echo=>[[
    kind=>class_declaration,
    name=>'Echo',
    header=>'public class Echo {',
    members=>[
     [PREFIX=>[
    kind=>field,
    text=>"""
   
       public static final String PREFIX = "...thus spoke";""",
    name=>'PREFIX']],
     [name=>[
    kind=>field,
    text=>"""
   
       private final String name;""",
    name=>'name']],
     [Echo=>[
    kind=>constructor,
    name=>'Echo',
    signature=>'Echo(String name)',
    header=>"""
   
       public Echo(String name) """,
    body=>"""{
           this.name = name;
       }""",
    footer=>'',
    text=>"""
   
       public Echo(String name) {
           this.name = name;
   ..."""]],
     [comment=>[
    kind=>comment,
    text=>"""
   
       /**
        * Speak to a person.
        *
        * @param wh..."""]],
     [speak=>[
    kind=>method,
    name=>'speak',
    signature=>'String speak(String who)',
    header=>"""
       public String speak(String who) """,
    body=>"""{
           return who;
       }""",
    footer=>'',
    text=>"""
       public String speak(String who) {
           return who;
   ..."""]],
     [comment=>[
    kind=>comment,
    text=>"""
   
       /**
        * The name this speaker was built with.
       ..."""]],
     [name=>[
    kind=>method,
    name=>'name',
    signature=>'String name()',
    header=>"""
       public String name() """,
    body=>"""{
           return this.name;
       }""",
    footer=>'',
    text=>"""
       public String name() {
           return this.name;
       }"""]]],
    footer=>"""
   }"""]]],
    postscript=>"""
   """,
    location=><mfs:src/test/resources/scratch/src/main/java/com/example/scratch/Echo.java>]
mtron> */dev/scratch/idx/Echo
==>[
    field=>[
     PREFIX=>!@/dev/scratch/code/0/classes/Echo/0/members/0/PREFIX,
     name=>!@/dev/scratch/code/0/classes/Echo/0/members/1/name],
    constructor=>[Echo=>!@/dev/scratch/code/0/classes/Echo/0/members/2/Echo],
    comment=>[=>],
    method=>[
     speak=>!@/dev/scratch/code/0/classes/Echo/0/members/4/speak,
     name=>!@/dev/scratch/code/0/classes/Echo/0/members/6/name]]
```
`idx` offers a human-readable path scheme that projects to the `code` uri subgraph. Due to the `!*` nature of the `idx`
objs, any updates to
`idx` redirect to `code`. When `code` is **re-saved**, a `?subq` listener fires, mapping the `ide:java::T` to
`web:java::T`
and then to disk. The subscription then pulls the file from disk to a `web:java::T` and then a `ide:java::T` in `code`
and `idx`. In this way,
`code` serves as a metatron encoded proxy to the file system representation of the project's source code.

```
         ┌─────── idx 
         │         │
   src ──┤         │
    ▲    │         ▼
    │    └─────── code 
    │            ⋰
    └───── sub:[...]   
```

**edit, then save — two steps** (verified against a live VM, 2026-09-04): the `>>=` edit lands in the `code` space
immediately, but the **disk write-back fires on the class-level save** (`code/N.to(...)`) — the subscription
serializes the class rec (header + body + footer of each member) and writes the file. An edit that is never saved is
space-only.

```mtron
mtron> */dev/scratch/idx/Echo/method/speak
==>[
    kind=>method,
    name=>'speak',
    signature=>'String speak(String who)',
    header=>"""
       public String speak(String who) """,
    body=>"""{
           return who;
       }""",
    footer=>'',
    text=>"""
       public String speak(String who) {
           return who;
   ..."""]@/dev/scratch/code/0/classes/Echo/0/members/4/speak
mtron> */dev/scratch/idx/Echo/method/speak/body.-<'\n'.as(rec::T)
==>[
    0=>'{',
    1=>'        return who;',
    2=>'    }']
mtron> */dev/scratch/idx/Echo/method/speak/body.-<'\n'.as(rec::T) >>= [1 => "return who;"]
==>[0=>'{',1=>'return who;',2=>'    }']
mtron> @/dev/scratch/idx/Echo/method/speak >>= [body=> '{ return "marko"; }']
==>[
    body=>'{ return "marko"; }',
    kind=>method,
    name=>'speak',
    signature=>'String speak(String who)',
    header=>"""
       public String speak(String who) """,
    footer=>'',
    text=>"""
       public String speak(String who) {
           return who;
   ..."""]
mtron> *<mfs:src/test/resources/scratch/src/main/java/com/example/scratch/Echo.java>
==>java::"""package com.example.scratch;
   
   /**
    * A simple greeter used as a scratch fixture for the agent IDE.
    */
   public class Echo {
   
       public static final String PREFIX = "...thus spoke";
   
       private final String name;
   
       public Echo(String name) {
           this.name = name;
       }
   
       /**
        * Speak to a person.
        *
        * @param who the person to speak with
        * @return the spoken words
        */
       public String speak(String who) {
           return who;
       }
   
       /**
        * The name this speaker was built with.
        *
        * @return the speekers name
        */
       public String name() {
           return this.name;
       }
   }
   """
```
Finally, to check if the update to `Echo::speak` made it to disk, dereference the uri disk pointer.

```mtron
mtron> *<mfs:src/test/resources/scratch/src/main/java/com/example/scratch/Echo.java>
==>java::"""package com.example.scratch;
   
   /**
    * A simple greeter used as a scratch fixture for the agent IDE.
    */
   public class Echo {
   
       public static final String PREFIX = "...thus spoke";
   
       private final String name;
   
       public Echo(String name) {
           this.name = name;
       }
   
       /**
        * Speak to a person.
        *
        * @param who the person to speak with
        * @return the spoken words
        */
       public String speak(String who) {
           return who;
       }
   
       /**
        * The name this speaker was built with.
        *
        * @return the speekers name
        */
       public String name() {
           return this.name;
       }
   }
   """
```
The standard template for selective editing of code is provided below where `[X=>Y]` is a placeholder for patterns
itemized in the subsequent table.

> **known limitation (verified 2026-09-04, live VM):** the nested `>>=[...]>...join` *inside* `body=>` is
> **silently dropped** by the current VM build — the line echoes back as accepted, but nothing changes in `code`,
> in `idx`, or on disk (and no error is raised). The same update works when the new body is a **concrete string** —
> which is what an agent composes anyway (it has the lines and the edit). So: build the full new `body` text, update
> with it, then save (`code/N.to`).

```mtron
@../idx/${class}/method/${method} >>= [body => -<'\n'.as(rec::T)>>=([X=>Y]>>.>-?str<=str{*}('\n'))]
└───────────────┬───────────────┘     └───┬──┘└──┬──┘ └────┬───┘└────┬───┘└┬┘└─────────┬─────────┘
                │                         │      │         │         │     │           └ join them by a newline
     anchor the method to edit            │      │         │         │     └ get lines  
                                          │      │         │         └ update template **important**
                                          │      │         └ turn the lst of lines to a int indexed rec
                                          │      └ split the current body by newlines
                                      │   └ update the method body                                │
                                      └────────────┬───────────┘ └─────────────┬──────────────────┘ 
                                                   │                           └ src lines to single src string 
                                                   └ single src string to src lines
```

| pattern           | example                                | discussion               |
|-------------------|----------------------------------------|--------------------------|
| single replace    | `[4 => 'a']`                           | replace line 4           |
| single insert     | `[4 => +'a']`                          | insert at line 4         |
| multi-line insert | `[3 => +'a\nb']`                       | insert 2 lines at line 3 |
| single delete     | `[1 => none]`                          | remove first line        |
| batch delete      | `[?>2.?<5 => none ]`                   | remove lines 3 and 4     |
| batch delete      | `[_ => has('ex[1-9]{2}.*').map(none)]` | remove lines by regex    |

### subscriptions

A `?subq` subscription handles transforming updated `code` structures into language compliant source text on disk.
Different subscriptions can be defined to provide any number of useful reactions to code edits. Examples include:

1. triggering an agent to review the code.
2. sending a log event to the log portion of space.
3. compile the code and altering should it fail.
4. record stats (points to sections of the code).
5. ...

The current `sub::T` is:

```mtron
mtron> */dev/scratch/code/#?subq
==>[sub::[
    code=>rshift(0).as(rec::T).rshift(path).select([id(),id(),id(),id(),id(),id()]).to(temp).as?rng=uri&dom=lst(uri::T).to(x).*id().update([location=>none]).as(java::T).to(**x.rshift(location).side(split([
     location=>id(),
     status=>saved,
     time=>!math:datetime_now()]).print('saved ',id(),'\n'))).map(map(/dev/scratch/src).mult(*temp.reverse().merge().take(1))),
    target=>/dev/scratch/code/#]]
```
## gotchas (learned the hard way)

- **Edit fields, not `text`.** `text` is derived; writing it (e.g. via
  `?lineq=`) edits a detached projection under `idx`, never satisfies the subscription, detaches the member anchor, and
  accumulates stale slots across generations.
- **Re-pulling resets.** `src/...()` bumps the generation (`code/0 -> 1 -> 2…`)
  from disk; in-memory edits die with the generation. Disk is authoritative.
- **Multi-line strings**: use `""" ... """`; send *real* tabs/newlines in the value -- a literal backslash-`\t`/`\n`
  lands as text, not control chars.
- **`.vid()`** on a member yields a wrapped expr (`<...>`), not a plain uri;
  `* <text?lineq=N>` on it flattens to a uri. `?lineq` ranges (`3-6`)
  dereference fine; single-line `?lineq=N` with `*(_)` returns `this`
  (self), not the line.
- **`>>=` on a derived/clone path** silently does nothing useful.
  Verified repro (2026-09-04):
  `@…/idx/Echo/method/speak >>= [body=> -<'\n'.as(rec::T)>>=([1=>'x']>>.>-?str<=str{*}('\n'))]`
  echoes as accepted; `code`, `idx`, and disk are all unchanged, no error. Plain-string RHS works in the same one line:
  `>>= [body=> '{
        return who;
    }']` lands in `code` immediately.
- **Save to make it stick.** The `code/N.to(code/N)` re-save is what fires the write-back subscription — after the edit,
  run it, then confirm on the `mfs:` file. Until then the edit is space-only.
- **`text` stays stale after a body edit — by design.** the serializer composes `header + body + footer` on write, so
  an outdated `text` field is harmless; do not try to keep it in sync by hand.
- **rec key order (since the int-key sorting, 2026-09-04):** `as(rec::T)` from a lst, `>>=` result recs, and `==`
  select result recs all render **int keys ascending**; recs with any non-int key keep their original order. Both are
  stable within a run and safe to rely on for line numbers.
- **join (`>>.>-?str<=str{*}(...)`) accepts a rec or a lst of strings** — joining a `rec` after `.as(lst::T)` fails
  (`Tuple$Pair cannot be cast to String`); the pair-list is not a string list.
- **the mtron MCP eval has a single-obj echo bug:** bare single-obj expressions can return `noobj` even when server
  side they are fine — wrap for a liveness check with `1-<[expr]` (returns the address/uri when live; note `1-<[a,b]`
  *splits* lists, so use it for single values). List-valued expressions render normally.
- **The doc examples side-effect**: the `body=>` edit rewrites the project's
  `memSpace.java` on disk at build time. The mvn site build excludes this doc (toy project pending) so the examples can
  be run safely.
- Only `.java` so far.

## running as a container app

The agent ide ships as a metatron app: `boot/agent-ide.boot.mtron` builds the whole workspace (home memspace, scratch
project at `/dev/scratch`, write-back subscription, command palette) and the launcher runs it in a container with the
repo mounted at `/work` (= the `mfs:` root).

```
bin/agent-ide-docker                 # up: ws://localhost:8555/mtron, http://localhost:8777/mcp
bin/agent-ide-eval '<mtron code>'    # evaluate through the mcp door (m_inst_eval_mtron)
docker stop agent-ide                # down
```

The launcher passes `--user $(id -u):$(id -g)`: the image's own uid cannot write the mounted repo and an mfs write then
**hangs** rather than failing — always run the container as the host user.

Poke surface (in the app):

```
*/dev/scratch/name                    # 'scratch'
*/dev/scratch/command/+               # mvn_build / mvn_clean / mvn_test / mvn_exec / code_tree
*/log/metatron/event/+                      # the save audit trail (empty until a save fires)
bin/agent-ide-eval '/dev/scratch/command/mvn_build()'
```

No maven in the runtime image: `mvn_build()` fails in-container by design right now (documented gap; the docker socket
is mounted for the dckr-space route as an option).

## the pull: code and idx are separate reprojections

The push goal was decoupling: `code` is the reprojection of the source text, and `idx` is a *reprojection of the code
list* — multiple embeddings of one structure in the uri graph, like database indices. Delivered:

- **the pull owns the code list only.** `src/<cls>()` (the class inst under the project) resolves the source through its
  routed space,
  `ide:java`-transforms it, appends the class value into `base/code` (a lst that grows over repeated pulls), writes
  `base/code` beside the project root, and **returns the flow of the class rec** — the pulled value is the rec encoding,
  not a fail and not a list of anchors.
- **`idx` is the separate inst `ide:index`.** On a project-root uri it reads
  `base/code` and re-slices every element: `class => kind => name` => the
  `!@base/code/N/classes/<C>/<ci>/members/<mc>/<name>` anchors — one anchor per code element `N` holding the member —
  and returns that reprojection as its value. It best-effort publishes `base/idx` beside the root. Adding another
  embedding of the code list (a different keying, a different slice) is a new small inst that reads `base/code` — the
  pull itself never changes.
- **`code` base = the root URI itself** (`ideInstSet` pull lambda:
  `codeBase = lhs.isUri() ? lhs.uriValue() : lhs.vid()`). The as (project)
  lhs's dom is `uri`, so the uri is its own space location — `code` (and, on publish, `idx`) land beside the project in
  the SAME space (mfs/fs) the root came from. A root with neither uri nor vid fails with a clear contract message
  instead of the old
  `Cannot invoke "fURI.extend(String)" because Obj.vid() is null` NPE.
- **deref walk:** `*<mfs:root>` re-shapes the root to `/src/...`; the walk strips the leading slash
  (`replaceFirst("^/+","")`) to stay CWD-relative per its existing `startsWith("src")` filter. Without that, the project
  builds **empty** — the 257-byte `project.ide.mtron` artifact on boot and its `#{*}` passthrough class insts are that
  empty shape.

Acceptance — `ideInstSetTest#testPullAfterSpaceRoundTrip` (green, JDK 24):
routes a `probe:` space against the repo root, builds the project from
`*<probe:root>`, calls the stored class inst, and asserts (1) the pull returns the class rec, (2) the `code` list landed
beside the root with the parsed class, (3) `ide:index` re-projects it with `code/0/.../members/...`
anchors, and (4) **idx -> anchor -> value**: the first anchor uri resolves through the space back to the stored member
rec.

**Known gap (engine, not ide):** *publishing* `base/idx` on an fs/mfs space fails — the idx value carries `!@...`
auto-at refs and the fs write throws `[rec::T] unable to convert uri::T`. That only affects the side effect: `ide:index`
returns the reprojection regardless, and for a memspace-rooted project the write works (no serialization). Auto-at ref
serialization is an fs-space / mtron-serializer matter — the domain owner's call.

### the list-element truth (memspace)

- **`[expr]` stores the CODE, not the value.** `*wl/0` renders
  `map('...').as(java::T)...` — an unevaluated instruction. When another obj touches it the code applies; when it
  doesn't, deep reads **under** a code element (`code/0/classes/...`) are noobj by design (`Space.Helper.unrollPoly`
  only descends into poly rec/lst elements).
- **`block` / `|` is the explicit "don't evaluate" tool** (marko, 2026-09-03):
  `block(b)` returns `b` when applied (`Obj.java` `BLOCK_INST_TID`
  body `(lhs, inst) -> inst.arg(0)`); `|` is its sugar (`mInstSet` `Sugar.prefix("|", BLOCK_INST_TID)`). Per marko, in
  `1-<[+2,|+2]` the bare `+2` evaluates on touch (→ 3) while the `|+2`
  stays inert (→ `+2`). So to hold an unevaluated value use `|`/`block`, not a bare code element.
- **The value shape is the clean one:** `expr.to(slot)` evaluates before the write; deep reads under the stored rec
  resolve (`ObjJavaIDESerializerTest#testIdeJavaStoredValueReads`). The pull does this in-java
  (`codeLst.add(ideJava, MUTABLE)`).
- **`Lst.at` append (fixed here):** writing an element at `index == size`
  used to throw `lst index out of bounds: 0 > 0` instead of appending (the pull grows its code list element by element).
  `Lst.at(key,value,op)` now appends at `index == size` — the rec.at upsert stance for lists — and keeps throwing past
  `size`. Covered by
  `memSpaceTest#testRecListFieldAppendWrite`; LstTest/RecTest/memSpaceTest all green.

## boot-file footguns (agent-ide.boot.mtron)

Learned the hard way, in this order of cost:

- **chain lines end with `.`** — a continuation line starting with `.` after a `<uri>` literal line does not parse: `could not parse at 'e' — unclosed
  '<' — missing '>'?`. Dot-at-EOL everywhere (drstynx style) parses clean.
- **`.else()` is an instruction** — call it at a use site. As a rec field value (tblespace host, `side(temp -> ...)`
  argument) it stays a code obj and dies with `[code::T] unable to convert uri::T`. The house pattern is the
  `[== boot args ==]` marker block (BootLoader parses it and overrides the CLI for same-named keys) — no `.else()`
  needed.
- **`<uri>` literals take no splices** — `<mfs:${...}>` fails to parse. Use literal paths, or the string-with-splice
  form:
  `side(temp -> "${*boot/args/build/file}")`.
- **`#` is not a comment** — only `[-- .. --]` and `[== .. ==]` are.
- **quote chars inside `[==..==]` bodies matter** — unbalanced quotes in a comment body silently kill the web binding
  (door never opens, zero error lines). Same for the `"""` banner.
- **don't nest `[== boot args ==]` text inside another comment** — it terminates that comment.
- **boot-args values**: a bare word (`user => marko`) is stored differently than a quoted str or a `<...>` uri; the home
  module's `_`
  binding was unreliable, so the boot builds the home space as an explicit
  `memspace::[pattern => </dev/#>, ...]` record instead.
- **`+` in mtron is rec-union** (update `+([...])`), not string concat.
- **tblespace hosts**: plain literal, and the parent directory must exist (sqlite JDBC will not create it).
- a `log` key in the marker block would override the CLI `log=>info`
  (header beats CLI) — keep `log` to the CLI.