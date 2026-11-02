# ideInstSet: Agent IDE Module

The agent IDE is delivered as a metatron **instset** (a "module"/"library"), not a custom space class. Storage is just
an `fsSpace` with `addQ(lineq)` `addQ(subq)` `addQ(lockq)` — all three qprocs already built. The intelligence lives in
the instset.

```
instset::[ {?}pattern=>uri, {?}const=>#{?}, {?}type=>#{*}, {?}inst=>#{*}, {?}rewrite=>#{*}, {?}sugar=>#{*} ]
```

## Architecture

**Java for the heavy lifting, mtron for composition** (metatron's core philosophy):

- **`ideInstSet`** — a Java `AbstractInstSet` subclass (like `webInstSet`) that registers types + insts
- **Java-backed watchdogs** (the primary useful objs — the brains):
    - `csDocqWatchdog` — subq handler on `cs_*` insts; on implementation write, introspects the new body and regenerates
      the docq
    - `csReparseWatchdog` — source-tree watcher; on file write, re-parses → refreshes `cs_java` recs (the codeSpace
      reparse, as a Java class)
    - `csErrorsParser` — build output lines → structured `{file, line, msg}` recs (Java regex, fast)
    - `csImportResolver` — bare type → package resolution (project index + curated JDK map)
- **Thin mtron insts** that call the Java objs — `cs_build`, `cs_test`, `cs_errors`, `cs_import`, `cs_find`, `cs_edit`,
  etc.

## Interface insts — the contract

`cs_build`/`cs_test`/`cs_status` are shipped as **interface insts** — signatures + docq + a FAIL default body, so
project owners know what to implement:

```mtron
inst?str<=#{?}()               { fail::["implement cs_build for agents..."] }@cs_build
inst?str<=#{?}(test=>uri{*}::T){ fail::["implement cs_test for agents..."] }@cs_test
inst?str<=#{?}()               { fail::["implement cs_status for agents..."] }@cs_status
```

- The FAIL body is the "abstract" marker — calling an unimplemented inst tells the agent the interface exists but needs
  wiring
- `cs_test(#)` runs all tests, `cs_test(<uri>)` runs one (the `test=>uri{*}::T` args are test-case uris)
- **Any file in an fsSpace is executable as an instruction** — so a project owner drops a `cs_build` file (an inst,
  internally a `bash` redirect or anything) into the project root, and agents call `cs_build()`. Project commands are
  *physical files in the project space*; IDE intelligence is *insts in the instset*.
- Same mechanism serves agent tools (`/home/{agent}/tool/`).

## Dual-mode docq (self-describing docs)

A `?docq` on an interface inst serves two modes, switched by implementation status (a branch in `docQ().preRead`):

- **Not implemented** → docq describes *how to build* the inst
- **Implemented** → docq describes *how to use* the inst

Storage: the docq entry carries both:

```
docs::[
  obj=>cs_test,
  desc=>"run the test suite — cs_test(#) all, cs_test(<uri>) one",   # agents, when implemented
  build=>"provide cs_test as root::bash('-c','mvn test') or your runner",  # implementers
]
```

## Self-maintaining docs via subq watchdog

Each interface inst gets a **subq watchdog** that regenerates its docq when the implementation is written — the same
pattern as the codeSpace reparse watcher:

```mtron
cs_test?subq -> sub::[name=>cs_test-docq, code=>ideInstSet.regenerateDocq(>>0)]
cs_test -> root::bash('-c','mvn test')    # owner implements → watchdog fires → docq regenerated
```

The watchdog can **introspect the actual implementation** and generate a docq describing what it really does (extract
the bash command), not a canned phrase. Docs stay truthful to the wiring; agents never see stale docs.

## The cs_* vocabulary

**Project-defined (bash redirects / file-as-instruction, user wires once):**

- `cs_build()` — build the project
- `cs_test(#)` / `cs_test(<uri>)` — run tests
- `cs_status()` — git/project status

**IDE-native (implemented in ideInstSet, work over the code graph):**

- **Understand**: `cs_project(root)`, `cs_find(project, "Class.method")`, `cs_source(rec)`, `cs_refs(rec)` (callers),
  `cs_super(rec)` (type-graph walk), `cs_impl(rec)` (effective methods)
- **Edit**: `cs_edit(rec, span, newText)` (lock → lineq → write → reparse), `cs_insert(rec, position, text)`,
  `cs_import(rec)` (resolve + insert missing imports), `cs_format(rec)`, `cs_annotate(rec, note)`
- **Verify**: `cs_errors(output)` (build output → structured error recs), `cs_jump(error)`
- **Awareness**: `cs_diff(rec)`, `cs_locks()`

Top three for v1 (highest agent value): **`cs_errors`** (collapse the verify loop), **`cs_refs`** (safe edits — who
calls this), **`cs_import`** (agents' hardest time is imports). `cs_source` close behind.

## Relation to codeSpace design

Supersedes the custom `codeSpace::T` class idea. The structural-URI approach (`cs:.../classes/{name}/...`) becomes
inst-level navigation (`cs_find(project, "Class.method")` → the rec) — arguably better for agents: they call high-level
functions rather than hand-constructing URIs. The `cs_java::T` coarse schema (ObjJavaCSSerializer), lockq, lineq
(replace/delete/append/insert), and the reparse watcher all carry over.

## The auto instruction family — `!` vs `!*`

There are two distinct auto forms, and they are not interchangeable:

- **`!`** = `auto(inst())` — auto- **applies** an **instruction**. A field declared `str::T` can hold
  `!inst?str<=(){...}` and the type holds: dereferencing executes the inst and yields a value of its rng type.
- **`!*`** = `auto(from())` — auto- **froms** a **uri**. `!*<uri>` dereferences to the value stored at that uri — used
  for lazy references where you want the uri *resolved*, not an inst *executed*.

> General rule: `!inst::T` is type-compatible with the inst's **rng** — on access, auto-resolution applies the inst and
> yields a value of its rng type. A field declared `str::T` (or `cs_result::T`) can hold `!inst?str<=(){...}` and
> dereferencing guarantees the declared type.

```
[name => str::T]                    # field declared str::T
[name => !inst?str<=(){...}]        # holds an auto-applied inst whose rng is str
*rec/name                           # deref → executes → str::T ✓
```

## The `cs_project::T` command palette — deref = execute

`build`/`test` map command-name uris to the **outcome type** (`cs_result::T`); the stored value is a `!inst` that *is*
the command run:

```
build => [
  mvn_test => !inst?cs_result<=noobj{0}(){ ... },   # ! — auto of the inst (executes on deref)
  clean    => !inst?cs_result<=noobj{0}(){ ... },
]
```

`*/usr/marko/dev/ide/build/mvn_test` resolves the uri → auto-applies the inst → the test runs → `cs_result::T`.
**Resolving the uri triggers the act of building.** The field type is what you get (`cs_result::T`); the value is the
lazy action.

## Lazy `output` — `!*` to a minted uri

The `output` field of `cs_result::T` is declared `str{*}::T` but holds a **`!*` auto-from ref** to a minted temp uri
where the line-stream is stored (see `CommandRunner`). Only on access (`>>output`) does auto-from pull the stream from
the temp uri — the result rec stays compact, and paging (`>>output.limit(10)`, `>>output.range(10,50)`) works on the
materialized stream.

```
output => str{*}::T                 # declared str{*} — the line-stream type
output => !*</sys/tmp/<id>>          # !* — auto-from of the uri, derefs to the stored stream
>>output.limit(10)                  # deref materializes; paging works on the stream
```

Two lazy forms, one rule each: **`!` executes an inst; `!*` resolves a uri.** The `cs_project` palette uses `!inst`
(each key is a runnable command); the `cs_result` output uses `!*` (a deferred uri read). This is what lets a
`cs_result` carry a potentially-large output without materializing it until an agent asks.
