# Agent Code Harness — Specification

**Status:** draft working spec, distilled from the 2026-08-19 session brainstorm.
Items marked **OPEN** are unresolved decisions.

---

## 0. Purpose

Bring metatron to the point of being an **agent code harness**: a coding agent
works *inside* metatron — reading, editing, building, and verifying Java code —
with the `ideInstSet` supplying the agent-facing concepts and tools, on top of
the `webInstSet` file-type conversions.

**Guiding principle: disk is the single source of truth.**
Every read (and `as()`-map) re-derives from disk. Every edit flushes back to
disk. The in-space structures are *views* and *working copies*, never a second
source of truth.

The larger shape: the coding agent is an `agent::T` (spaces-as-data, features
as capability recs) whose `tool_feature` derefs the `ide:` insts — each
self-describing by docq — over a shadow project tree like the one specified
here.

---

## 1. Building blocks (existing, verified live)

| Piece | Where | Role |
|---|---|---|
| `web:java::T` | `webInstSet` | MIME-validated `str::T` refinement — `*<file>.as(web:java::T)` derefs and tags |
| `ide:java::T` | `ideInstSet` | the coarse Java schema as a `rec::T` refinement (classes/members/header/body/footer) |
| `ide:project::T` | `ideInstSet` | project descriptor (root, name, build/test palettes, `code`) |
| `ide:result::T` + `cs_command` + `CommandRunner` | `ideInstSet` | run a command → standardized `status`/`runtime`/`output` outcome |
| `ObjJavaIDESerializer` | `ide/parser` | lossless TreeSitter coarse parse (read) and byte-exact write |
| as-paths (bidirectional entry) | `ideInstSet` | `as?ide:java<=web:java` (parse) and `as?ide:java<=rec` (re-tag) |
| project walk | `ideInstSet` (~line 148) | `as?ide:project<=uri` — semi-implemented; `<src/>.as(project::T)` builds the project clone |
| `lineq` / `subq` / `lockq` | `QCollection` | edit granularity / observation / advisory concurrency control |

---

## 2. The core model

```
PAIR TREE (immutable, project space)              DISK (single source of truth)
  /usr/dev/src/...  =>  [loc, materializer]  ◄────┼────────────────────┐
                                                  │ read                │ flush
                             materializer         ▼                     │
        agent: @pair.>>1           READ: disk → as(ide:java::T) → stamp location
                                                  │                     │
                            ▼                     │                     │
                 BUFFER (workspace, mutable)       │                     │
                 /usr/dev/<loc-path> = rec(location, package, classes…) │
                                                  │ agent edits the working copy
                                                  ▼
                      AUTOSAVE SUB (Java, key type of ideInstSet)
                                                  │ rec.at(location) → file uri
                                                  │ ObjJavaIDESerializer.write(rec) → str
                                                  ▼
                                   Router.writeToSpace(location, str) ───► disk

  revert = re-deref the pair (fresh parse from disk) — the pair tree never mutates
```

Five components: **pair tree** (immutable view definitions), **materializer**
(lazy disk→rec→buffer), **`location`** (self-provenance stamped onto the rec),
**buffer** (per-file working copy), **autosave sub** (Java, the single
write-through choke point).

---

## 3. Component specifications

### 3.1 The pair node (immutable)

One node per project file, under the agent home (e.g. `/usr/dev/src/…`):

```mtron
[ <loc>,  <loc>.- <[ _, as(ide:java::T) ]>.map( [ >>0, >>1 >>= [location => >>0] ] ).to(/usr/dev * >>0) ]
```

- Element 0: the file's URI (`loc`).
- Element 1: the **materializer** — an auto-applied inst, decoded:
  1. `loc .- <[_, as(ide:java::T)]>` — thread the file through a two-way view: raw file and coarse rec;
  2. `.map([>>0, >>1 >>= [location => >>0]])` — stamp `location` (the file uri) onto the rec;
  3. `.to(/usr/dev * >>0)` — persist the result at the buffer path derived from the file (`/usr/dev` + path).

Rules:

- **The pair tree never mutates** — except when a file is *added* or *removed* from the project.
- Language-agnostic: in theory the pair may be as bare as `[<src/…>, !*</src/…]`, and the reader
  appends the encoding they want: `pair.>>1.as(ide:java::T)`.
- The pair node is **readable mtron sitting in the tree — the mechanism is self-documenting**.

### 3.2 The `location` field (self-provenance)

- Stamped by the materializer; carries the file's identity *inside the content*.
- This is what lets the autosave sub be lookup-free: `rec.at(location)` is the flush target.
- Semantics: transport metadata, **not** source. It must be **inert on write-back** —
  see OPEN (B).
- Semantics pinning — OPEN (A): canonical form of `location` (full fs-space uri vs
  relative path) and the buffer-path derivation (`/usr/dev` + non-scheme path).

### 3.3 The buffer (workspace)

- Mutable working copy per file at `/usr/dev/<loc-path>`.
- Shape: the coarse rec **with `location`** — same shape as a pair node, eager instead of lazy.
- The agent edits the buffer; the buffer's host space carries the subscription.
- Edit granularity (whole rec vs member-surgical `buffer/classes/…/body` + `lineq`)
  depends on nested-addressing behavior of the host space — OPEN (D).

### 3.4 The autosave subscription (Java — key type of `ideInstSet`)

- **Written in Java, made fast** — no mtron re-evaluation on the hot path.
- Registered over the buffer root (pattern `/usr/dev/#`) at project build time.
- Fires on any buffer write (contract from `QCollection.subQ().qlessWrite`):
  `code` receives `lst([written_uri, obj])`; fires via `applyAsync`.
- Body, in effect: `file = rec.at(location)` → `str = ObjJavaIDESerializer.write(rec)`
  → `Router.writeToSpace(file, str)` → disk.
- Single choke point: this is also the future home for the audit trail, reparse hooks,
  and error surfacing (`cs_errors`-era work).
- The subscription must register with an **explicit `target` carrying `#`** (the
  `subq_sub` rec form) — a bare inst written at `?subq` would bind an exact uri and
  miss the subtree.

### 3.5 Project build

```mtron
<src/>.as(project::T)        [-- walks the source tree, builds the pair clone, registers the sub --]
```

Lands on the semi-implemented `as?ide:project<=uri` (`ideInstSet` ~line 148).
Java performs the tree walk; mtron does the rest. The project rec's `code`
references the shadow root.

---

## 4. The agent-facing loop

1. **Explore** — `>>` over the pair tree (the project, as data).
2. **Materialize** — `@pair.>>1` → fresh parse from disk → buffer → rec (disk is always re-read).
3. **Edit** — mutate the buffer rec (whole or member-level, pending OPEN (D)).
4. **Autosave** — the Java sub flushes to disk. The pair tree is untouched.
5. **Verify** — `cs_command`-style build/test over the project → `ide:result::T`.
6. **Revert** — re-deref the pair. New fresh parse overwrites the buffer.

---

## 5. Design decisions (and why)

| Decision | Why |
|---|---|
| Disk = source of truth; reads always re-derive | Single source; no stale shadow; revert is one deref |
| Pair tree immutable (add/remove only) | The tree is a *view definition*, not a copy — cannot drift |
| `location` stamped onto the rec | Self-provenant: identity travels with content; the sub needs zero lookup, zero config |
| Autosave sub in Java, key type of ideInstSet | Fast hot path; one typed, docq-able registration; one choke point for audit/reparse later |
| Materializer is readable mtron (pair element) | The mechanism self-documents; an agent reading a node sees exactly what deref does |
| `to()` (detached) not `at()` (anchored) in the materializer | Deliberate reference semantics for the buffer persist step |
| Per-file buffers (path derived from `location`) | No "current file" register; deterministic, guessable, sub covers the tree in one registration |
| Sub on the buffer, **not** the pair tree | Simpler than watching the view layer; no re-link/no-op dance |
| Bidirectional types exist; write direction is the sub's job | `as?ide:java<=web:java` and `as?ide:java<=rec` are in place; the Java sub does the coarse→source conversion directly |

---

## 6. Edge cases

- **Create file:** agent writes a *new* pair node (tree mutation: add) → flush creates the file.
- **Delete file:** `noobj` on the pair node (tree mutation: remove) → file removed.
- **Revert:** re-deref the pair — fresh parse from disk.
- **External edit** (human/tool touched the file): buffer is stale until re-deref.
  Convention: **agents read through the shadow tree, never direct `*<file>`** —
  this also avoids racing the async flush.
- **Async flush window:** the sub runs via `applyAsync` — disk lags the buffer by a tick.
- **Concurrency:** `lockq` over the buffer root; per-agent buffer homes
  (`/usr/{agent}/dev`) when agents run in parallel.
- **Non-Java files:** same pair shape; element 1 carries another (or no) `as()` encoding;
  the reader appends the encoding at read time.

---

## 7. Open questions

- **(A) Canonical `location`.** Must be the full fs-space file URI (it is both the flush
  target and the buffer-path seed). Buffer rule: `/usr/dev` + non-scheme path. **TBC.**
- **(B) Writer inertness.** `ObjJavaIDESerializer.write()` must **explicitly strip**
  `location` rather than rely on the unknown-field-skip; document it as transport metadata.
- **(C) Type predicate.** Put `location` in `IDE_JAVA_TYPE`'s `isaPredicate` with
  `.maybe()` so `test(ide:java::T)` holds for both raw-parse and buffer forms — **TBC.**
- **(D) Edit granularity.** Probe (live VM) whether the buffer's host space resolves nested
  member URIs (`buffer/classes/…/members/method/main/body`) so `lineq` can hit a body `str`
  directly; otherwise whole-rec edits (still byte-exact).
- **(E) Rendering quirk.** `ide:java` recs render with a `java::` prefix — ambiguous next to
  `web:java::T` str values; check tid rendering.

---

## 8. Test fixture

A disposable Maven project in the repo — mess with it freely:

```
src/test/resources/scratch/
├── pom.xml
├── README.md
├── src/main/java/com/example/scratch/{Greeter,Calculator,Operation}.java
└── src/test/java/com/example/scratch/GreeterTest.java
```

```bash
./mvnw -f src/test/resources/scratch/pom.xml test        # verified BUILD SUCCESS, 2/2
```

Live-VM probe chain (endpoint `ws://localhost:8555/mtron`):

```mtron
*<src:…/Greeter.java>.as(web:java::T).as(ide:java::T)
```

---

## 9. Known debts (carry-forward)

- `CommandRunner` splits on whitespace — quoted args, pipes, env, cwd, timeout all unhandled;
  upgrade before wrapping richer build commands.
- The `cs_*` vocabulary (`cs_errors`, `cs_refs`, `cs_import`, `cs_build`, …) and the
  watchdogs from `ide-instset.md` remain design-only — the surface to build *after* this spec lands.
- Doc drift: the codespace docs still describe `cs_*` naming under `/m/web`; code has moved to
  `ide:*` under `/m/ide`.

---

## 10. Related

- `docs/design/codespaces/ide-instset.md` — agent IDE module design (cs_* contract)
- `docs/design/codespaces/codespace-functor.md` — coarse schema (parser output, §0)
- `docs/design/codespaces/codespace-v2.md` — redirect-index model (precursor of the pair/sub approach)
- `src/main/java/studio/phaseshift/metatron/isa/ide/ideInstSet.java`
- `src/main/java/studio/phaseshift/metatron/isa/ide/parser/ObjJavaIDESerializer.java`
- `src/main/java/studio/phaseshift/metatron/isa/ide/CommandRunner.java`
- `src/main/java/studio/phaseshift/metatron/furi/q/QCollection.java` (`subQ()` contract)
