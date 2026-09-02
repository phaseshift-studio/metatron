# CodeSpace Functor: TreeSitter CST → codespace

The functor maps the TreeSitter CST rec::T (as produced by `ObjJavaSerializer`) into the codespace projection that agents work with. It is a many-to-one projection: codespace exposes the *editable atomic units* of Java source, discarding the full CST tree as the agent-facing surface.

> **Current direction (v2):** the parser produces the coarse schema **directly**. The full CST + redirect-index framing below is retained as the derivation story and as the raw-material option (`as(cst::T)`), but codespace consumes this section's output — there is no redirectSpace in the codespace path. The "redirect" is name-based resolution over the coarse rec itself.

> **Naming:** the coarse-schema family uses the `cs_` prefix — `cs_java::T` (vid `/m/web/mime/cs_java`), serializer tid `/m/web/serializer/cs/obj_java`. Onboarding another language is `cs_rust::T`, `cs_python::T`, etc., each with a `CS_SERIALIZER_TID.extend("obj_{lang}")` serializer and a `{LANG}_TID → CS_{LANG}_TID` as-path.

## 0. The Coarse Schema — Parser Output (current direction)

`ObjJavaSerializer` parses a Java source file into the schema below. The fine-grained TreeSitter statement/expression tree is **collapsed into complete text spans** at the declaration level; only the skeleton (package, imports, types, members) survives as structure. This is the schema agents and humans conceptualize code by.

### Rec shape

```
rec(
  package    => str("package studio.phaseshift.metatron.isa.mach.type.router;"),
  imports    => lst([str("import ...;"), ...]),           // document order
  preamble   => str("..."),                                // verbatim text before the first class
  classes    => lst([
    rec(kind       => uri("class_declaration"),
        name       => str("BasicRouter"),
        superclass => str("AbstractSpace<...>"),
        interfaces => lst([str("Router"), ...]),
        header     => str("...class BasicRouter ... {"),   // text up to the opening brace
        members    => lst([                                // document order
          rec(kind => uri("field"), name => str("PRIMARY"),
              text => str("    public static final Uri PRIMARY = uri(\"primary\");")),  // full span (incl. leading ws)
          rec(kind => uri("method"), name => str("toSkill"),
              header    => str("    public Skill toSkill() {"),   // up to the body brace
              body      => str("{ ... }"),                        // the lineq unit — directly editable
              footer    => str(""),                                // after the closing brace
              text      => str("    public Skill toSkill() { ... }"),  // derived read-only concat
              signature => str("public Skill toSkill()")),        // derived
          rec(kind => uri("constructor"), name => str("BasicRouter"),
              header => str("    public BasicRouter("), body => str("{ ... }"), footer => str("")),
          rec(kind => uri("comment"), text => str("    /* ... */")),
          ...]),
        footer     => str("}\n"))                            // text after the last member
  ]),
  postscript => str("")                                   // verbatim text after the last class
)
```

Members are stored in **document order** so serialization is a byte-exact concatenation: `preamble + classes + postscript`, where each class is `header + members + footer`, and each method/constructor member is `header + body + footer`. **Methods decompose into `header`/`body`/`footer`** so the body — the primary `lineq` edit unit — writes through directly without a stale full-text clobbering it; `text` is a derived read-only concat. Fields and comments carry a single complete `text` span. The class `header`/`footer` and member leading-gap folding account for every byte, including inter-member whitespace.

### URI paths

Name-based resolution over the rec — a trivial lookup, not a redirect:

| URI path | Resolves to | Notes |
|---|---|---|
| `cs:{file}/package` | the package declaration text | addressing view |
| `cs:{file}/imports/{n}/text` | the n-th import statement | addressing view; ordered |
| `cs:{file}/preamble` | verbatim text before the first class | write source |
| `cs:{file}/postscript` | verbatim text after the last class | write source |
| `cs:{file}/classes/{name}/name` | the type name | |
| `cs:{file}/classes/{name}/superclass` | extends clause text | |
| `cs:{file}/classes/{name}/interfaces` | lst of implements text | |
| `cs:{file}/classes/{name}/header` | text up to the opening brace | |
| `cs:{file}/classes/{name}/footer` | text after the last member | |
| `cs:{file}/classes/{name}/members/{kind}/{memberName}/text` | full member source | `kind` ∈ field, method, constructor, comment |
| `cs:{file}/classes/{name}/members/method/{memberName}/body` | the method body — **the primary lineq edit unit** | writes through directly |
| `cs:{file}/classes/{name}/members/method/{memberName}/header` | method text up to the body brace | |
| `cs:{file}/classes/{name}/members/method/{memberName}/footer` | method text after the body brace | |
| `cs:{file}/classes/{name}/members/method/{memberName}/signature` | derived, read-only | |

For the common single-public-class file, the class level is often elided in conversation (`cs:mTool/methods/toSkill/body/text`), but the canonical path includes `classes/{name}`.

**Why this needs no redirectSpace:** the coarse schema *is* the projection. `/classes/mTool/methods/toSkill/body/text` is a name-based walk over the rec (find class by name, find member by kind+name, descend into `body` then `text`). That resolution is trivial and lives in the codespace layer's `read()`; it is not a general URI↔URI mapping. `redirectSpace` remains a useful primitive for other projections (aliases, versioning, sharding) but is not in codespace's path.

**Dereference flow:** a file/URL dereference tags content via `MIME.fromExtension`/`toTid` — a `.java` file dereferences as `java::T` (a `str::T` refinement — `as?java<=str(java::T)`, parse verification in the `JAVA_TYPE` predicate). No MIME change is needed for the coarse schema.

**`cs_java::T` is a `rec::T` refinement** — `as(cs_java::T)` *is* the parse: it produces the coarse rec. Its `isaPredicate` is a **top-level shape check** — the rec must expose `classes` (a **named rec** — each class name maps to the ranked `lst` of that class's recs); `package`/`imports`/`preamble`/`postscript` are optional addressing/write views. Deeper member-level verification (method/field shapes) can be added later without touching the parse. The dereference flow is two steps:

1. `*<fs:Foo.java>` → `java::T` str (tagged by MIME)
2. `.as(cs_java::T)` → `as?cs_java<=java(cs_java::T)` runs `ObjJavaCSSerializer.parse` → the coarse `cs_java` rec

The `rec::T ↔ cs_java::T` as-paths re-tag (downgrade/upgrade the tag). Onboarding a language is `CS_{LANG}_TID` + `CS_{LANG}_TYPE` (a `rec::T` refinement) + `as?cs_{lang}<= {lang}(cs_{lang}::T)` + the `CS_{LANG}_TID ↔ REC_TID` as-paths.

### Addressing — names and ranks live in the structure

The coarse rec is **the contract every language adapter fills** (Java first, via `ObjJavaIDESerializer`; python/rust/go the same way). Named = addressable, ordered = printable — both at once: the `classes` slot is a named rec (a file's top-level names are structurally unique, each class name mapping to the ranked `lst::T` of that class's recs), and `members` is an ordered `lst::T` of **single-field wrapper recs** — `{name => memberRec}` — where **the list position is the print order** and **the wrapper key is the named slot**. Dereference walks straight through it:

```
code/0                                   file slot — the host space prepends it;
                                          the serializer never sees a filename
code/0/classes/Greeter/0                 class Greeter, rank 0 — two Greeters in different
                                         packages are different files, i.e. different code slots
code/0/classes/Greeter/0/members/1/apply the apply member at list position 1
                                         (print order is the list order)
code/0/classes/Greeter/0/members/+/apply wildcard: the set of every member named apply
code/0/classes/Greeter/0/members/1/apply/text
```

Nameless members (comments, static initializers) take their kind word as the wrapper key — `members/3/comment/text` — and stay named and addressable. `ordinal` and `path` are **not stored fields**: the key *is* the name, the list position *is* the order, and there is no parallel identifier scheme for either to drift from.

**References.** Durable `!*` pointers use the name form — `notes >>= +["I should rewrite this method", !*code/1/classes/Greeter/0/members/+/apply]` — which survives member insertion. When two members share a name, the pointer disambiguates by structural predicate — `.../members/+/apply=?=[signature=>has("int a")]` — evaluated as the native rec projection (`lhs.at(key).test(value)` per rule) on the wrapped member rec. Fixed list indices are the session-scoped form ("this one, right now") and shift on insertion — standard list semantics.

**Lossless.** `write` walks the list: classes in slot order (a non-first class's `sep` carries the inter-class gap), then within a class `header + members (wrappers in list order) + footer`, with a member's `text` folding in its leading whitespace — so `write(parse(src)) == src` byte-for-byte in **any** member order, same-named ones interleaved with others included, and the chain `'...'.as(web:java::T).as(ide:java::T).as(web:java::T).as(str::T)` returns the source intact.

**Known simplification.** Nested types currently parse as `kind=>other` members (a full `text` span); recursive expansion into their own `members` is a later refinement.

**Language onboarding** is `IDE_{LANG}_TID` + `IDE_{LANG}_TYPE` (a `rec::T` refinement — `classes` slot = named rec → ranked lists) + `as?ide_{lang}<={lang}(parse)` + `as?{lang}<=ide_{lang}(write)`, both backed by that language's serializer.

**Type-reference traversal (the source tree as a graph):** the serializer emits `superclass`/`interfaces` as **bare type names** (`AbstractSpace<Map<Obj, Obj>>`, no `extends`/`implements` keyword). The codespace layer — which has the project's file index — upgrades them into `!*` redirects to the referenced class's own cs rec:

```
superclass => !*<file://.../AbstractSpace.java>.as(java::T).as(cs_java::T)
```

So `>>superclass` on a class rec dereferences to the superclass's `cs_java` rec, and agents can walk the type tree:

- "is `Sub` a subclass of `X`?" — transitive `>>superclass` walk
- "which implementation of `method` does this class use?" — walk + signature compare
- "what are `Sub`'s effective interfaces?" — walk `>>interfaces`

Resolution of a bare name → file URI: fully-qualified names map directly (`java.util.AbstractList` → `java/util/AbstractList.java`); unqualified names resolve via the class's `package` + imports, or a project symbol index (FQN → file) built once by parsing every file (each already produces a cs rec carrying `package`/`name`).

## 1. Node Shape (verified on `BasicRouter.java`)

Every TreeSitter node becomes a rec with a `type_` key, named-field children promoted to direct keys (e.g. `name`, `type`, `parameters`, `body`), and unnamed named children collected under `out`. All nodes — leaf and compound — carry a `start`/`end` **byte range** into the original source. This is a TreeSitter invariant: `node.getContent() == source.substring(start, end)` is exact for *any* node at *any* depth.

The `text` field is **not uniform** across node types in the current serializer — a deliberate choice, not a TreeSitter limitation:

| Node type | Has `text`? | What `text` contains |
|---|---|---|
| `class_declaration` | ✅ | **Entire class source**, annotations included |
| `field_declaration` | ✅ | Full field source (e.g. `public static final Uri PRIMARY = uri("primary");`) |
| `import_declaration` | ✅ | Full import statement |
| `block` (method/constructor body) | ✅ | Full `{ ... }` source |
| `method_declaration` | ❌ | decomposed into `type`, `name`, `parameters`, `body`, `out` |
| `constructor_declaration` | ❌ | decomposed into `name`, `parameters`, `body` |

The keys present on a `class_declaration` node: `type_`, `name`, `superclass`, `interfaces`, `body`, `out`, `text`.
The keys present on a `method_declaration` node: `type_`, `type`, `name`, `parameters`, `body`, `out` — **no `text`**.

### Why `text` is missing on compound nodes

`ObjJavaSerializer.readNode()` captures `text` under this condition (line 212):

```java
final int anonymousCount = childCount - node.getNamedChildCount();
if (!hasNamedChildren || anonymousCount > 0) {
    final String content = node.getContent();
    ...
}
```

`text` is captured only when a node has **no named children** (leaf) or **has anonymous children** (keywords/punctuation mixed in: modifiers, imports, blocks, fields). A `method_declaration` has named children (`type`, `name`, `parameters`, `body`) and zero anonymous ones — its keywords live in the `modifiers` sub-node, its parens in `formal_parameters`, its braces in the `block`. So it gets no `text`.

This is a *size optimization*, not a fidelity constraint. The byte range makes every node losslessly recoverable.

## 2. The Unified Text Model: `start`/`end`/`generate`

Options A and C are the same computation, differing only in *representation*:

- **A — eager:** `text => <the source slice string>`, materialized at parse time.
- **C — lazy:** `text => rec(start => int, end => int, generate => inst)`, computed on demand.

Under C, the `generate` Inst reads the source `bytes::T` and extracts the range:

```
cs:com.Foo/methods/bar/body/text
  → !*cst:.../methods/bar/body/text                  (redirect)
  → rec(start => 1023, end => 1087, generate => inst(
       () -> *</path/to/Foo.java>.as(bytes::T).substring(1023, 1087).as(str::T)))
  → "        return addition.isNoObj() ? ..."
```

Every node's text — leaf or compound — is the same shape. The "completeness rule" collapses: there is no distinction between complete and incomplete nodes, because *every* node computes its full source span on demand. This is exactly the redirectSpace / Inst-composition model: text is a first-class computed object.

## 3. The Projection

```
CST (TreeSitter rec::T)                     codespace rec::T
────────────────────────                    ─────────────────
class_declaration.text           →  class/text            (str::T, editable)
class_declaration.name.text      →  class/name            (uri, read-only)
import_declaration.text          →  imports/{n}/text      (str::T, editable)
field_declaration.text           →  fields/{name}/text    (str::T, editable)
method_declaration.body.text     →  methods/{name}/body/text   (str::T, editable ★)
type+name+parameters text        →  methods/{name}/signature   (str::T, read-only)
method node text                 →  methods/{name}/text        (str::T, editable full method)
constructor_declaration.body.text → constructors/{name}/body/text
```

Under the unified model, `methods/{name}/text` (the full method) is as editable as the body — it is just the method node's own `start`/`end`/`generate` span. No concat view, no lossiness.

## 4. Design Consequences

### 4.1 Method body is the natural edit unit

`methods/{name}/body/text` is the `block` node's span — the fine-grained `lineq` surface for surgical edits. `methods/{name}/text` is the whole method for wholesale replacement. Both are the same shape; an agent chooses the granularity.

### 4.2 Signature is derived, read-only

`methods/{name}/signature` composes `type` + `name` + `parameters` text for navigation and annotation targeting. A signature change is a *structural* edit (replacing the method's children), not a text span. V1: read-only; structural signature rewrite is future work.

### 4.3 CST is the single source of truth

`class/text`, `fields/{name}/text`, `methods/{name}/body/text` are all projections/redirects into the CST. They overlap in the source — both are views of the same underlying tree — and `lockq` prevents an agent from holding overlapping locks. Because the URIs are coarse-grained and name-based (§4.6), they are stable under byte rearrangement; only structural changes (rename, delete, refactor) move them.

### 4.4 Canonical Representation

Under Option A, a compound node carries **both** its captured `text` and its derived children (`name`, `parameters`, `body`). These are two representations of the same source that can diverge: editing the `text` orphans the children; editing a child orphans the `text`. The serializer resolves this today by making children canonical on write (compound declarations reconstruct from children), which means wholesale `text` edits are dropped.

The full resolution is a **canonicality choice**: exactly one representation is canonical; everything else derives from it.

- **Text-canonical (the watcher model):** the method's `text` is the source of truth. Editing `methods/{name}/text` triggers a watcher that re-parses that fragment and regenerates the `name`, `parameters`, `body` children. But this is one-sided: `body/text` is a *derived* view of the method's text, so editing it directly diverges from what it derives from.

- **Source-canonical (Option C):** the file's `bytes::T` is the **only** canonical thing. Every node — method, name, parameters, body — is a `start`/`end`/`generate` view into it. Editing any text replaces a range in the source; the `subq` watch re-parses; every node's offsets and derived fields update. Nothing is stored except the source, so nothing can go stale.

Under C, the watcher model falls out automatically: editing `methods/{name}/text` replaces `source[methodStart:methodEnd]`, re-parse regenerates the whole method node. No hand-written per-node-type watcher is needed — the re-parse *is* the watcher.

### 4.5 Reparse is the Shift: the Immutable CST Rec

An edit to one component shifts the byte offsets of everything after it. The question is who computes that shift. The answer: **nobody — the reparse is the shift.**

`cst = parse(source)` is a **pure function**. The CST rec is **immutable**. An edit never mutates the CST — it mutates the source, and the CST re-derives wholesale:

```
edit → mutate source bytes → reparse → NEW immutable CST rec (replaces old atomically)
```

The overlap failure mode — "the end of one method appears inside another's byte range" — only materializes if a **stale span** is dereferenced between the source change and the reparse. The invariant that prevents it: **reparse is synchronous with the edit, before any span is read.** The space's write handler does edit + reparse + swap atomically; the old rec is garbage, and `generate` always reads spans from whatever the current rec is (always the latest parse). No deltas, no patched offsets, no spill — the moment a shift would be needed, the reparse has already produced flush, non-overlapping ranges by construction.

### 4.6 URI Stability for Concept Graphs

Agent concept graphs project onto **coarse-grained URIs** — `/class/method`, `/class/field`, `/class/import` — not byte positions. These are *structural* addresses, stable under byte rearrangement:

- Moving a method earlier in the file → its byte range shifts, but `cs:com.Foo/methods/bar/body/text` still resolves (the path is name-based, and the redirect re-resolves onto the new rec's tree).
- Inserting a field above → every subsequent span shifts, but all `/class/...` paths are unaffected.
- `class/text`, `imports/{n}/text`, `fields/{name}/text`, `methods/{name}/body/text` all survive re-parse because the tree shape is a function of the code, and the paths name their targets.

A path only breaks when the *structure* changes: a method rename or delete, a class renamed, a massive refactor. Then the redirect re-resolution returns noobj for the vanished path — the concept-graph edge dangles, and the agent learns the target moved. This is a naming concern, distinct from byte-offset correctness: offsets are always right (fresh parse); *paths* are right until the structure they name is gone.

## 5. The Inverse (write-back)

`lineq` write-back is **uniform**: every editable unit maps to a source byte range.

| codespace edit | → source mutation | → consequence |
|---|---|---|
| `fields/{name}/text = newSrc` | replace `source[start:end]` | `subq` watch re-parses → CST + offsets regenerate |
| `methods/{n}/body/text = newB` | replace `source[start:end]` | same |
| `methods/{n}/text = newMethod` | replace `source[start:end]` | same |
| `class/text = newClass` | replace `source[start:end]` | same |

An edit lands in the source `bytes::T`; the `subq` watch re-parses and regenerates all offsets. The CST is a *read-model*, always freshly derived from source + offsets. This is the design, not a bug — edits flow into the source, the CST re-derives.

## 6. Option C Write Path Sketch

Under C the write path is a single primitive: **replace a range in the canonical source.** Everything else is derived.

```
lineq write:  cs:com.Foo/methods/bar/body/text?lineq=3-7  :=  newBody
  1. deref redirect → the block node's span rec { start, end, generate }
  2. compute absolute source range:
        absStart = blockStart + lineqOffset(3)
        absEnd   = blockStart + lineqOffset(7)
  3. write to the source bytes::T:
        *<fs:.../Foo.java>.as(bytes::T)
            .replace(absStart, absEnd, newBody)
  4. re-parse synchronously → NEW immutable CST rec replaces the old
     (atomically — no stale span is readable between edit and swap; see §4.5)
  5. redirect index re-evaluates onto the new rec → codespace projections update
```

Read path mirrors it:

```
read:  *cs:com.Foo/methods/bar/body/text
  → !*<cst:.../methods/bar/body/text>
  → span rec { start, end, generate }
  → generate inst: *<fs:.../Foo.java>.as(bytes::T).substring(start, end).as(str::T)
  → "        return addition.isNoObj() ? ..."
```

Notes:

- The **`generate` Inst** is the bridge: it reads the canonical source and materializes a node's span as `str::T` on demand. It is a first-class computed object, so the rec stays compact while remaining 100% lossless.
- **Wholesale edits and sub-edits are the same operation** — both are range replacements into the source. `methods/{name}/text` = `source[mStart:mEnd]`; `methods/{name}/body/text` = `source[bStart:bEnd]`. No reconstruction switch, no stale-text clobbering, no "which representation is canonical" — the source is canonical and nothing else is stored.
- **Offset shifts are owned by re-parse.** After any replacement, offsets above the edit shift; the `subq` watch re-parses and all spans re-derive. Read-model always fresh.
- **lineq offsets are relative to the str::T**, so the codespace layer translates lineq → absolute byte range using the span's own start. Two-level addressing: codespace URI (structural) → span rec (byte range) → source.

## 7. Implementation Strategy

The two representations are nearly a **configuration parameter**: one conditional in `readNode()` chooses what occupies the `text` slot.

```java
if (CAPTURE_ALL || !hasNamedChildren || anonymousCount > 0) {
    if (CAPTURE_OFFSETS) {
        // Option C: lazy span
        r.at(uri(TEXT), rec(uri("start"), jnt(node.getStartByte()),
                            uri("end"), jnt(node.getEndByte()),
                            uri("generate"), instLambda(...)));
    } else {
        // Option A: eager slice
        r.at(uri(TEXT), str(node.getContent()));
    }
}
```

**Recommended path: implement A first.** It is a two-line change (drop the `anonymousCount > 0` guard so every node captures `getContent()`), yields a fully working codespace with every unit editable and lossless, and avoids offset-invalidation plumbing in the first iteration. When satisfied, switch to C via the flag: store offsets, materialize text lazily, and let the `subq` watch own offset refresh. The rec structure, the codespace URI scheme, and the functor projection are identical under both — only the `text` value's representation changes.

## 8. Summary

The functor is defined by one rule: **every node's source span is addressable and editable.** Under the eager model (A) that means capturing `text` everywhere; under the lazy model (C) it means `start`/`end`/`generate`. Either way, codespace exposes `class/text`, `imports/{n}/text`, `fields/{name}/text`, and `methods/{name}/text` / `methods/{name}/body/text` as lossless, surgical, concurrency-safe edit surfaces.

## 9. codeSpace, lockq, and Thread Ownership

The agent-IDE build plan: `codeSpace::T` (a standard space implementation like `fsSpace`/`mqttSpace`, tailored for agent development) equipped with `q => [lineq::[=>], subq::[=>], lockq::[=>]]` query processors.

### 9.1 lockq::T — advisory URI-pattern locks

`lockq` follows the same `QCollection` qproc shape as `subq()` — a registry lst, a `preWrite`, a `qlessWrite`, a `preRead`:

```
cs:metatron/src/.../mTool/methods/#?lockq -> lock::[usr=>/usr/agent1, expire=>datetime://2343455]
```

- **preWrite** on `?lockq=lock::[...]` → register the lock against the base pattern (`cs:.../methods/#`); `noobj` → release
- **qlessWrite** → the conflict check: on a normal write to `cs:.../methods/toSkill/body`, test the URI against each lock's pattern; a match that isn't expired **throws** (advisory — blocks the write)
- **preRead** → return the lock rec(s) matching a URI

The recursive `#` matching is the standard URI pattern test (`vid.test(pattern)`). `lockq::T` is one qproc in `QCollection`, structurally identical to `subq`.

### 9.2 Thread ownership — the "who is this?" primitive

Metatron's threads are first-class and walkable. From `*/sys/thread/+`:

```
core::    [...source absent...]@/sys/thread/main        ← root, waits for children then shutdown
virtual:: [...source=>!*/sys/thread/main...]@/sys/thread/54249889   ← 'console statusline'
virtual:: [...source=>!*/sys/thread/main...]@/sys/thread/bd8ce281   ← 'console repl'
```

Virtual threads carry `source` — a back-link to their parent thread. The identity design:

- **Add an `owner` field to thread recs** (AbstractThread / VirtualThread / CoreThread).
- **Agents**: their main execution thread carries `owner=>/usr/{agent}` (or the agent vid).
- **Humans**: their interface into metatron is thread-based too — the `console repl` / `console statusline` threads get `owner=>/usr/{name}`.
- **Child threads inherit**: resolve any thread's owner by walking `source` to the root thread and reading `owner`.

### 9.3 lockq + ownership

`lockq`'s `qlessWrite` reads the writing thread from `THREAD_STACK` (metatron keeps the current thread there), walks `source` to the root, extracts `owner`, and compares against the lock's `usr`:

- owner matches → allowed (the lock holder, re-entrant)
- no match and not expired → throw
- no `owner` found on the walk (unowned/daemon thread) → treat as foreign → throw

This is the uniform identity answer for agents, humans, and spawned children alike — one field on threads plus the `source` walk, no separate identity system.

### 9.4 codeSpace::T

A standard `AbstractSpace` subclass with:

- `q => [lineq, subq, lockq]` in its query map
- a **predefined watcher subscription** loaded on space-load — `sub::[name=>watcher, target=>cs:src/#, code=><reparse inst>]` — fires on any write to the source tree, re-parses the changed file, refreshes the cs rec index
- the **structural-URI resolver** (`cs:.../classes/{name}/members/{kind}/{memberName}/body` → name-based walk into the coarse rec)
- the **`superclass` bare-name → `!*` upgrade** (`superclass => !*<file://.../AbstractSpace.java>.as(java::T).as(cs_java::T)`) so `>>superclass` dereferences to the superclass's own cs rec, making the source tree a navigable type graph

Once `codeSpace` is running, the goal is to move the coding agent itself *into* metatron to work in the crafted environment — to feel it and to find places for high-level instructions that make life easier for coding agents.
