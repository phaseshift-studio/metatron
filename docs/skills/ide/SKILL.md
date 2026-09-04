---
name: ide
description: >
  agent ide: driving the metatron VM to work on a project's source through the
  uri graph -- self-contained setup (create the spaces, load the project,
  wire the command palette, bind the write-back subscription), pull a
  file's members, read them, edit the member fields (signature / header /
  body), and let the `code`-space subscription write back to disk.
  Only .java files for now. (living doc -- iterate freely)
---

# agent ide: source edits through the uri graph

An agent edits a project's source by **pulling a file into the code space, editing the member's *fields*,** and letting
the subscription on `code/#`
write back to disk and log a `saved` event. `.java` files only, today.

You need the mtron `eval` tool (`m_inst_eval_mtron`).

NOTE: the code blocks below are plain `mtron` (NOT `mtron_pre`) -- they are **inert text, not evaluated** by the
DocRunner. Deliberately: evaluating the setup block would overlap the current VM the docs run on. Once a toy project for
the docs to work against exists, re-tag the blocks `mtron_pre` so they execute in order, in one VM session, with state
accumulating. Nothing is pre-booted (`boot/docs.mtron` untouched) -- the setup block builds the whole environment.

## setup: build the infrastructure

Establish the two spaces, load the project's `ide.mtron` into a
`project::T` at the project root, wire the command palette, and bind the write-back subscription on the code space.

```mtron
memspace::[
  pattern => </dev/metatron/#>,
        q => [mintq::[=>],docq::[=>],subq::[=>],mimeq::[=>], lineq::[=>],lockq::[=>],incrq::[=>]]]@</sys/space/dev/metatron>;

tblespace::[pattern    => </log/metatron/#>,
            host       => <sqlite:target/metatron/log_metatron.sqlite>,
            driver     => <org.sqlite.JDBC>,
            table      => [,],
            q          => [incrq::[=>],subq::[=>],mimeq::[=>]],
            route      => [/log/metatron/ => <>]]@</sys/space/log/metatron>;

*<mfs:metatron.ide.mtron>.
  else(<mfs:.>@</dev/metatron>.
        as(project::T).
        update(+([name   =>'metatron',
                  desc   =>'metatron: a ring-based language and virtual machine'])).
        >>=[_=>dedup()].
        to(<mfs:metatron.ide.mtron>)).
  to(/dev/metatron).
  side(temp -> '.')
  -<[mvn_build => mvn_build -> ide:command('mvn -f ${*temp} compile'),
     mvn_clean => mvn_clean -> ide:command('mvn -f ${*temp} clean'),
     mvn_exec  => mvn_exec  -> ide:command('mvn -f ${*temp} compile exec:java'),
     code_tree => |inst?#{*}<=#{?}(max=>?int::T.else(2)){ *max.-<tree_select::[root=>!*/dev/metatron/root,max=>_,flatten=>true]@metatron_tree }].
  to(/dev/metatron/command);

  /dev/metatron/code/#?subq -> sub::[code=> >>0.as(rec::T)>>path==[_,_,_,_,_,_].
                                                 as?uri<=lst(uri::T).to(x).*(_).>>=[location=>none].as(web:java::T).
                                                 to(*(*x.>>location).side(-<[location=>_,user=>/usr/marko,status=>saved,time=>!math:datetime_now()].to(/log/metatron/event/_?incrq)))];
```

## encodings

A loaded Java file has two encodings:

- **rec encoding** — each member is a rec: `kind`, `name`, `signature`,
  `header` (up to `{`), `body` (`{...}`), `footer`, and `text` = **derived** (header + body + footer).
- **uri-graph (`idx`) encoding** — navigate the source with path syntax:
  `*/dev/metatron/idx/memSpace/method/close`.

The pull (`src/.../cls()`) returns the class's **rec encoding** (the
`ide:java` value) and stores it in the `code` list beside the project root.
`ide:index(root)` re-projects that code list into `root/idx`:
`class => kind => name => !@.../code/N/classes/cls/0/members/i/name` — *anchors* pointing into the code space. That
anchor is the write surface, and it also resolves back through the space to the stored member (the round trip is
asserted in the acceptance test).

## workflow

1. Pull the file (disk -> code space; fresh generation `code/N`; resets in-memory edits).
2. List the members.
3. Read a member (the rec encoding: `kind`/`name`/`signature`/`header`/`body`/`footer`/`text`).
4. Edit the member's *field* via `>>=` -- this trips the subscription and saves to disk.

```mtron
/dev/metatron/src/memSpace()
*/dev/metatron/idx/memSpace/+/+/.<<
*/dev/metatron/idx/memSpace/method/close
*/dev/metatron/idx/memSpace/method/close >>= [body=>"""{System.out.println("hello");}"""]
```

Field semantics:

| field       | edit to change                      |
|-------------|-------------------------------------|
| `signature` | the method signature                |
| `header`    | declaration/annotations (up to `{`) |
| `body`      | the method body (`{...}`)           |
| `footer`    | trailing bits                       |
| `text`      | **derived** -- do not hand-edit     |

## write-back (how it actually lands on disk)

The subscription is bound to the **code space**: `target=>/dev/metatron/code/#`. A write to a code-space member:

1. materializes the java obj into its `location` slot (the fs write), and
2. side-appends an audit record `{location, user, status:saved, time}` to
   `/log/metatron/event/_?incrq`.

Inspect the rule itself: `*/dev/metatron/code/#?subq`.

## verify

Did the write-back fire? Check the audit log (last entry is the newest save):

```mtron
*/log/metatron/event/+
```

…and read the file on disk. If the edit didn't land, the event log is the quickest tell.

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
- **The doc examples side-effect**: the `body=>` edit rewrites the project's
  `memSpace.java` on disk at build time. The mvn site build excludes this doc (toy project pending) so the examples can
  be run safely.
- Only `.java` so far.

## running as a container app

The agent ide ships as a metatron app: `boot/agent-ide.boot.mtron` builds the whole workspace (home memspace, scratch
project at `/usr/marko/scratch`, write-back subscription, command palette) and the launcher runs it in a container with
the repo mounted at `/work` (= the `mfs:` root).

```
bin/agent-ide-docker                 # up: ws://localhost:8555/mtron, http://localhost:8777/mcp
bin/agent-ide-eval '<mtron code>'    # evaluate through the mcp door (m_inst_eval_mtron)
docker stop agent-ide                # down
```

The launcher passes `--user $(id -u):$(id -g)`: the image's own uid cannot write the mounted repo and an mfs write then
**hangs** rather than failing — always run the container as the host user.

Poke surface (in the app):

```
*/usr/marko/scratch/name                    # 'scratch'
*/usr/marko/scratch/command/+               # mvn_build / mvn_clean / mvn_test / mvn_exec / code_tree
*/log/metatron/event/+                      # the save audit trail (empty until a save fires)
bin/agent-ide-eval '/usr/marko/scratch/command/mvn_build()'
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
  `memspace::[pattern => </usr/marko/#>, ...]` record instead.
- **`+` in mtron is rec-union** (update `+([...])`), not string concat.
- **tblespace hosts**: plain literal, and the parent directory must exist (sqlite JDBC will not create it).
- a `log` key in the marker block would override the CLI `log=>info`
  (header beats CLI) — keep `log` to the CLI.
