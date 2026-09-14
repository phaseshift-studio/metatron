---
name: widget-jrec-review
description: Review of JRec usage in the widget library — measured failure modes, what a rewrite must preserve, and a staged recommendation (state-in-rec discipline first, then drop JRec inheritance in favour of a plain Rec).
---

# Widget library × JRec — review and recommendation

Status: **analysis, no code changed.** Every claim marked *measured* was produced by a throwaway
probe test (`JRecProbeTest`, deleted after the run; raw output reproduced in Appendix A). Claims
marked *code* are quoted from the tree with `file:line`.

## Status (updated as the work lands)

* **Phase 0, style slice — done.** `Style` is a *view over the rec*, not a copy; the cached
  `border`/`anchor` fields inside `Style` are gone; the `from(Rec)` overload was deleted (a generic
  `at(key)` result bound to it by inference and inserted a `Rec` cast that threw on `noobj`); the
  `style` field left every satellite widget.
* **Phase 0, first state slice — done.** `AccordionWidget` no longer extends `JRec`: it extends the
  new `SpaceRec`, and the rehydration machinery is gone with it (`ensureRehydrated`, `pendingBuffer`,
  `flush`, `BUFFER_FLUSH_THRESHOLD`, the `field(jvmRead(), key)` name-fallback reader,
  `jvmRead`/`jvmWrite`). One read path (`read()`), one write path (`put()`), state in the rec only;
  what remains in Java is render scratch (`lastRenderHeight`, `cursor`).
* **Append trade (deliberate).** A rec-held *list* body with `add(line, MUTABLE)` gives O(1) appends
  with no buffer, but the widget type declares `body => str{?}` and the typer's `obj_write` check
  rejects a list body — so the body stays a `str` and each append is one read-merge-write. Cost: the
  join is O(body) per append (≈0.5 GB of memcpy across a 3,200-line session) and the rec-map copy is
  shallow. If that ever matters the fix is a rec-native list **type**, never a Java buffer.
* **Field rule in force.** A widget field whose name matches a key the widget `Type` declares is a
  bug — the map is that field's home. `AccordionWidget` now has *no* state fields at all
  (`style` is declared by the widget base type; `cursor` was write-only; `lastRenderHeight` only
  served `renderInPlace()`/`renderFresh()`, which nothing calls). `PanelWidget` lost its dead
  `cursor` and its `maxWidth` field — the wrap width is the style's `width`, a declared key.
* **State slice two — `PanelWidget` and `TreeWidget` are done.** Neither has a state field left:
  a panel's body/title/`maxWidth` were already keys, and a tree's `rows`, `style` **and** `forceExpand`
  are gone — `rows` was a per-render cache of the rec (now a local, built from one `read()` per pass),
  and `forceExpand` (a `Set<fURI>` only Java could set, so a re-hydrated tree silently lost it) is now
  the declared key `expand` (`uri{*}`, one or many branch uris) written by `TreeSelectTool` into the
  rec it already builds. A tree is now a pure function of its rec: a write to the rec shows up in the
  next render, and two widgets over one rec render the same tree.
* **The tree type now declares every key the widget reads** — `root`, `max`, `code`, and the three it
  was silently reading undeclared: `flatten` (bool), `xref` (rec), `expand` (uri`{*}`). Undeclared keys
  do *not* fail (rec patterns are open — every widget writes `style` without redeclaring it), so this
  is a contract/documentation change that also puts a value type on each key, not a bug fix.
* **`body => str{*}` rejects a `lst` correctly, and the distinction matters.** A body is *one or many
  strs* — a coefficient (`str{2}::T` is two strs) — while `['one','two']` is **one value** of type
  `lst[str]::T`. They are different types, so the write fails, correctly (the renderer tolerates a
  `lst` for Java-side construction; `SpaceRec.getLines` reads all three shapes). Widening the
  declaration to `union(str{*}, lst[str])` is a one-liner if a writer ever needs it, but it would put
  two on-the-wire spellings of "lines" in the contract, so it is left as declared.
* **State slice three — `TableWidget` is done, and it lost the most.** Its three `@JRecElement`
  fields (`headers`, `table`, `metadata`) are the rec keys `header`/`row`/`metadata`; `javaPopulated`
  and `sync()` — the flag that arbitrated between "Java owns the data" and "the rec owns the data" —
  are gone with the fields they guarded; `synchronized format()` went (the widget object is
  per-render, so the lock guarded nothing); `formattedRows`/`formattedRow`/`formattedWidths` are
  private (nothing outside called them); the width pass no longer pads short rows with a hard-coded 1
  and no longer guesses the column count separately; `clear()` clears the rec (before, it emptied the
  Java lists while the rec kept its rows, so a later sync resurrected them); the `addRow(entries, key)`
  upsert is now `upsertRow(cells, keyColumn)` at its one caller; and the unused
  `TableWidget(List<String>, List<List<Object>>)` constructor is gone.
* **The cell boundary is now symmetric and single-sited.** Cells stay `Object` because both ends carry
  more than text (a row cell can be an `fURI` that `SubsWidget` casts; a metadata cell holds a whole
  `Obj`, which is how `CardUtil` and `ExplainTool` get `Type`s and `cInt`s out). `obj(Object)` is the
  only way in, `cell(Obj)` the only way out, and they are inverses — so a table built from mtron and a
  table built through the Java API are the same table, cell for cell and type for type. Under JRec they
  were not: the Java path kept raw Java objects while the rec path converted, so a uri column handed
  back an `fURI` or an `Obj` depending on the table's history.
* **`SpaceRec.forEachValue(Obj, Consumer)`** — `stream()` over a `Lst` yields the *list*, not its
  members, so the first table read saw one row where there were two and one cell where there were
  four. `getLines` had already worked around this privately; it now shares the rule.
* **State slice four — `AbstractWidget` is done, and the widget tree is off `JRec`.** The base of the
  interactive widgets and every tool moved to `SpaceRec` in one change, so 17 classes
  (`GridWidget`, `CardWidget`, `Selector`, `SubsWidget`, `SelectorWidget`, `AbstractLineWidget`,
  `Separator`, `MenuBarWidget`, `ProfileTool`, `TreeSelectTool`, `ExplainTool`, `TypeDiffTool`,
  `ModalTool`, `SwipePanelWidgetTool`, `TraceTool`, …) left the reflection bridge at once. There were
  no `@JRecElement` fields left in that tree, so the only porting was the JRec *API*: six
  `jvmWrite(key, value)` sites became `put(key, value)`, and two style reads went through `read()`.
  `JRec`'s remaining clients are exactly the frozen ones — `Console`, `Pane`, and `Rewriter`.
* **What the base swap changed underneath, for the better.** `jvmWrite` wrote straight into the map:
  no type check, and on a vid-less widget no space write either. `put` is `at(k, v, MUTABLE)`, which
  type-checks and saves — so the shapes those six sites write are now covered by
  `WidgetTypeContractTest` rather than trusted. And `JRec.jvm()` returns a **copy** (it folds
  annotated fields and methods into a temp map), so `widget.jvm().put(...)` was a silent no-op on
  every JRec subclass; `SpaceRec`/`MRec` `jvm()` is the real map. The one such call site that
  mattered — `ModalTool.syncStyle()`'s `own.jvm().putAll(panelStyle.jvm())` — is fine either way,
  because it operates on a `Style`, which is an `MRec`, not a `JRec`.
* **One latent no-op found and fixed by the inventory.** `SwipePanelWidgetTool(Lst items)` called
  `this.at(OBJ, items)`: two-arg `at` is the *immutable* form — it clones the rec with the key set and
  returns it, so the result was discarded and the Java constructor built a swipe panel with no `obj`,
  which `run()` then skipped (it bails when the rec has no `obj`). It is `put(uri(OBJ), items)` now. The
  mtron constructor was always fine, which is why nobody noticed.
* **Dead weight removed with the base:** `AbstractWidget.size` (assigned once from the terminal, read
  only by the `display.resize` two lines below it, and never again) and `eraseWidget(int)`, which had
  no callers anywhere in the repo — a leftover of the pre-`WidgetCanvas` render cycle.
* **Where it is driven end to end.** `bin/test/console-abstract-widget.steps` is new: it builds a
  `menu_bar_widget` from mtron, whose rec carries a *lst of line widgets*, and asserts the bar draws
  both — the base as state holder, as container, and as renderer in a real terminal. Its needles put a
  colour macro in the source (`menu-{{r}}File`) so the rendered text (`menu-File`) appears nowhere in
  the echoed command; a WAIT on a bare literal matches the echo and proves nothing (which is how the
  first version of that file passed while nothing rendered).
* **What drawing actually means under this base, since it cost me an hour:** `AbstractWidget.run()` is
  raw mode + attachment — it does not render — so a subclass that should appear on screen overrides
  `run()` (`MenuBarWidget`, `Selector`, `SelectorWidget`, `GridWidget`, and every tool do; the line
  widgets do not, they are drawn by the bar that holds them). That is not new with the migration, but
  it is why "display a line widget and look for its text" asserts nothing.
* **Where things stand:** every widget and every tool is on `SpaceRec`; `Console`, `Pane` and
  `Rewriter` are `JRec`'s only clients, and `JRec` is frozen.
* **`SpaceRec` is deliberately a shim, and it should dissolve.** Its two rules belong to recs in
  general, not to widgets: `put(k,v)` is already `at(k,v,MUTABLE)`, and `read()` is what `jvmRead()`
  did badly. Two ways it can end: (a) fold `read()`/`put()` into `Rec` as defaults (a platform change
  that benefits Router and Rewriter too — proposed, not snuck in), or (b) keep it as the
  widget-facing base permanently. Until then it is the thing that lets each widget move over one at
  a time instead of as a big-bang rewrite.
* What `SpaceRec` does **not** solve: it cannot stop a widget from declaring a state field (that is
  the guard test / the type-key rule), it does not fix `Obj.hashCode`/`equals` being content-derived
  (any Java map keyed by a mutable rec is still unsafe), and it does not touch `Console`/`Pane`.
* **Open, and outside the widget layer:** a widget that is *anchored* (has a `vid`) does not see its
  own post-construction writes through `read()`. Measured: with a widget built at `local:x` and then
  `at(max, 3, MUTABLE)`, the instance's own map holds `max=3` and renders 10 rows, while
  `Router.global().read(local:x)` still answers `max=1` and a second widget over that vid renders 3.
  The router is not at fault — the same write on a *plain* rec persists and reads back (`read` returns
  the stored instance, identity-equal). The difference is that a widget `Type` has a constructor, so a
  saved widget is re-instantiated from the rec; which object the space then holds — and whether a
  later write replaces it — needs its own investigation. It does not affect rendering today (a widget
  renders from its own rec) and it is not caused by `SpaceRec`, but any "write the rec from outside
  and watch the widget follow" pattern depends on the answer.
* **A state-durability footgun, measured while building the drag.** An anchored widget's style write
  *does* reach the store, and a re-hydrated instance (constructed from the stored rec, as mtron and the
  console do) reads it back — the drag's `top`/`left` survive `.display()` (verified: `top=5 left=3`
  written, 5/3 read back by a fresh instance).  But a widget constructed in Java from a **partial map**
  with a vid **clobbers** what the store held: the constructor writes the instance to the store before
  any read (`objCheckAndSave`), so `read()` then answers *the instance being constructed*, `readStyle()`
  finds no style, materializes a default and persists that — wiping the stored style.  Anything that
  constructs a widget with a vid must hand it the rec the store already holds; that is what the type
  constructor does, and what a hand-made map must not fail to do.
* Remaining: the guard test (now mechanical: parse the widget `Type` keys, fail on a matching field);
  `Console`/`Pane` stay on frozen `JRec`. The tools still hold per-session Java state
  (`TreeSelectTool.expandedNodes`, `SwipePanelWidgetTool.selectorTable`, …) — that is *interaction*
  state belonging to a live session, not widget data, but it is the next place the same question will
  be asked.
* **Tests as the guard, since the widgets had none.** `TableWidgetTest` is new (10 cases: the
  Java-built/rec-built parity property, cell types across the boundary, metadata as data, upsert,
  clear, ragged rows, style-in-rec), `WidgetTypeContractTest` flips `TypeCheck.type_pred` **on**
  (tests boot with it off) to assert each widget `Type` accepts every shape the widgets and tools
  actually write — and that a `lst[str]` body is refused; `TreeWidgetTest` grew the
  "render is a function of the rec" cases. The old fields were invisible to the suite precisely
  because there were no table tests at all.

## 0. Verdict

The complaint is correct, and it is not a widget-code discipline problem alone: **JRec has three
homes for state and no rule about which one wins.** A widget that reads `at(...)` and a widget that
reads `jvmRead()` see different values; a widget that writes the rec map and a widget that writes
the space leave different leftovers.

Recommendation, in one line: **keep Rec-ness, drop JRec-ness.**

* Phase 0 — make the widget layer obey the rule the code already states ("the rec map is the single
  source of truth for widget data" — `AbstractWidget.java:41-43`), killing the two live dualities
  (a `Style` copy in a Java field, and TableWidget's `@JRecElement` fields).
* Phase 1 — change the widget base class from `JRec<W>` to a plain rec (`MRec` + a ~100-line
  `WidgetRec`/`RecState` helper for space-aware reads/writes). This is mechanical because the
  library barely uses JRec (numbers in §2) and `MRec` already provides everything a widget needs
  (§5, Option B).
* Phase 2 (separate initiative, not part of this) — fix JRec itself for its *other* clients
  (`BasicRouter`, `Rewriter`) or freeze it; the defects are listed in §10.

Doing nothing is not the cheap option: this class of bug has produced at least eight distinct
workarounds in the tree (§3).

## 1. What JRec actually is

`JRec` presents a Java object as a metatron `Rec` by bridging fields/methods with a
`Map<Obj,Obj>`. That gives state **three** possible homes:

| Home | Written by | Read by |
|---|---|---|
| Java field (`sjvm`), opt-in via `@JRecElement` | `at(k,v)` — `JRec.java:80-91` | `at(k)` — `JRec.java:93-118`; `jvm()` — `JRec.java:126-151` |
| the rec's own map (`this.jvm`) | `at(k,v)` (same call); ephemeral `jvmWrite` — `JRec.java:182-190` | `at(k)`, `jvm()` |
| the space (vid-backed objects only) | `jvmWrite` — `JRec.java:192-200` | `jvmRead()` — `JRec.java:162-170` |

Nothing reconciles the three. In particular:

* **A read is not a lookup.** Two layers stack here. `Rec.at(key)` is itself a *path-walking,
  multi-valued* read (`Rec.java:153-159`, `Rec.Helper.atToggle` at `Rec.java:218+`) whose result can
  be an `Objs` whenever more than one thing answers the key; and `JRec.at(key)` **adds** the
  annotated field/method values on top of that union, reflecting them into the map as a side effect
  (`JRec.java:93-118`). *measured*: `table.at(uri("header"))` → `{{?}[,],['a','b']}` — an `MObjs`,
  `isObjs=true`, `isLst=false`, i.e. **not** the list the caller asked for. A caller that dispatches
  on shape (`isLst()`, `isStr()`, `isBool()`) silently takes the default branch — which is why the
  library reads bodies through `stream()` and why `AccordionWidget.isExpanded()` carries the shape
  guard `null == e || !e.isBool() || e.boolValue()` (`AccordionWidget.java:209-212`): a shape
  surprise reads as "expanded", a wrong answer with no error. Note this hazard is **Rec-level too**,
  so Option B does not remove it — it argues for a single normalising read helper in either option.
* **A write is not a write.** For a vid-backed widget `jvmWrite` updates **only the space**; the
  local map and the annotated field keep the old value.
  *measured* on `AccordionWidget` with vid `/sys/jrec_probe`:

  | read | value |
  |---|---|
  | `bodyLines()` (goes through `jvmRead()` → space) | `[written-through-jvmWrite]` ✅ |
  | `at(uri("body"))` | `'initial'` ❌ stale |
  | `jvm().get(uri("body"))` | `'initial'` ❌ stale |

  So the same widget simultaneously holds the new value and the old one, and which you get depends
  on the accessor you happened to use. For an *ephemeral* (vid-less) widget the same write updates
  the map instead — the asymmetry is invisible until something becomes store-backed.
* `JRec.clone(jvm,tid,vid)` discards the clone and returns the receiver (`JRec.java:73-77`,
  `return this;`) whereas `MRec.clone(...)` is correct (`MRec.java:108-110`). *code*.

## 2. How much JRec the widget library actually uses

*Who is a JRec*: directly `AccordionWidget`, `PanelWidget`, `TableWidget`, `TreeWidget`, plus
`Console` and `Pane`; and through `AbstractWidget.java:39` **every other widget and tool** —
`CardWidget`, `GridWidget`, `LabelLineWidget`, `MenuBarWidget`, `ProgressTableWidget`, `Selector`,
`SelectorWidget`, `Separator`, `SubsCardWidget`, `SubsWidget`, `ExplainTool`, `InstSelectorTool`,
`fURISelectorTool`, `ModalTool`, `ProfileTool`, `SwipePanelWidgetTool`, `TraceTool`,
`TreeSelectTool`. The whole library inherits JRec whether it wants it or not.

*What it uses* (grep census over `isa/mach/type/ui/**`):

| JRec-specific surface | sites | where |
|---|---|---|
| `@JRecElement` (the reflective field bridge) | **17** | `TableWidget` 3, `Console` 11, `Pane` 3 — nothing else |
| `jvmRead()` / `jvmWrite()` | 9 / 18 | `AccordionWidget` 15, `PanelWidget` 3, `MenuBarWidget` 2, `CardWidget` 2, `LabelLineWidget` 1, `AbstractWidget` 1, `TreeSelectTool` 1 |
| `this.at(...)` **data reads** (1-arg) in widget classes | ≈34 | `TreeWidget` 6, `TreeSelectTool` 5, `SwipePanelWidgetTool` 5, `MenuBarWidget` 4, `TableWidget` 3, `AbstractLineWidget` 3, `PanelWidget` 3, `CardWidget` 2, others 1 each |
| `this.at(key, value, MUTABLE)` inst latching | 45 | almost all `CommandPalette` (39) — that is config-as-rec, appropriate; `AccordionWidget` 4 |

The reflective bridge — the thing that makes fields *be* rec state — is used by **three classes**.
Everything else in the library works against the rec map or against plain Java fields.

## 3. Failure modes, and the workarounds they have already forced

All eight of these exist in the tree today; each is a symptom of the missing single source of truth.

| # | Symptom | Site |
|---|---|---|
| 1 | A widget cannot be a `HashMap` key: `Obj.hashCode() = Objects.hash(jvm())` and a widget mutates its jvm as it renders, so the slot registry silently lost widgets mid-session. Fixed this session by making it identity-keyed. | `FloatingSurface.java` (slot registry), `Obj.java:999` |
| 2 | `JRec.jvm()` synthesises new inst objects per call, so `Map.equals` on derived state is unreliable → panes had to be compared by `id()`. | `Console.java:704`, `Console.java:735-737` |
| 3 | JRec's map round-trip destroyed Java-constructed tables → a `javaPopulated` flag now arbitrates who wins. | `TableWidget.java:64-68`, `TableWidget.sync()` |
| 4 | Store-backed reads missed mtron's writes → an ad-hoc `field(jvmRead(), key)` reader with a uri-name fallback was added. | `AccordionWidget.java:173-203` |
| 5 | JRec rehydration bypasses the constructor, so transient fields are null → `ensureRehydrated()` (7 sites). | `AccordionWidget.java:82-90` |
| 6 | Appends are staged in a Java buffer so the rec lags the object, needing an explicit `flush()` discipline. | `AccordionWidget.java:64`, `flush()` |
| 7 | The widget's style is a **detached copy** of the rec's `style` key, so a Java-side style write never reaches the rec (and a re-hydration loses it). | `Stylable.java:236-251`, `readStyle()` in 5 widgets |
| 8 | `jvmWrite` + store-backed widgets: rec/field reads go stale (measured, §1). | `JRec.java:192-200` |

Counter-example worth copying: the parts of the library that are *reliable* — keyboard focus,
resize, scrolling — are reliable because their state was moved **out of the widget** into the
surface slot (`targetWidth`, `heightCap`, `scrollX/scrollY`, `follow`), which is explicitly
documented as surviving re-float precisely because widgets do not. That is the pattern to extend,
not an accident.

## 4. What any solution must preserve

1. **A widget is a Rec.** mtron holds widgets in the graph (`ui_widget::T`), merges into them
   (`@<w>>>=[body=>...]`), and renders them (`ui.display`). "Remove JRec" must not mean "stop being
   a rec".
2. **The Type-constructor wiring** in `uiInstSet` (`Type.Builder...constructor(arg -> new
   XWidget(arg.asRec().jvm(), TID, arg.vid()))`) — widgets are constructed from a rec, with a vid
   when store-backed.
3. **Space round-trip for store-backed widgets**: `@<w>` + `>>=` from mtron must be visible to the
   next render (`jvmRead()` semantics).
4. **Inst latching**: `.toggle()`, `.append(...)` etc. stay callable from mtron; they are
   rec-held insts, not Java methods.
5. **`Widget.of(rec)` / re-hydration per `.display()`**: a fresh Java view may be built at any time
   from the rec, so *nothing* may live only in a Java field.
6. **`docWrap`** registration and the existing test net (237 UI unit tests, 3 pty suites).

## 5. Options

### Option A — keep JRec, enforce state-in-map discipline
Rule set: rec map (and, for vid-backed, the space) is the only home for data; Java fields may hold
only injected collaborators, per-render scratch, and caches with explicit invalidation; no
annotated fields in widgets; reads go through one helper (space-aware, shape-normalised); writes go
through one helper that updates space **and** local map; no `Obj`-keyed Java maps without identity
keys.

* Cost: low (~300-500 LOC) and independently valuable.
* Leaves the footguns in place: `at()` still returns `Objs` and mutates on read; `jvmWrite` still
  goes stale; `clone()` is still wrong; every new widget can reintroduce the pattern; and
  `AbstractWidget extends JRec` keeps exposing all of it.

### Option B — widgets stop being JRecs (recommended)
Widgets keep being **recs**, but not *JRecs*: `extends MRec` (or a small `WidgetRec extends MRec`)
instead of `extends JRec<X>`.

* `MRec` is a concrete `Rec` with the same `(Map, tid, vid)` constructor and correct `clone`/`self`
  (`MRec.java:39-41,100-115`) and **no reflection bridge**.
* What JRec provided that must be re-provided: space-aware read/write (`jvmRead`/`jvmWrite`
  semantics), the typed extractors (`jvmStr/jvmBool/jvmInt/jvmBody` — already static helpers), and
  `ensureRehydrated`-style construction hygiene — together ~100 lines in a `WidgetRec` base.
* Inst latching (`this.at(key, inst, MUTABLE)`) and its write-through are **`Rec`-level defaults,
  not JRec's** (`Rec.java:124-149` — the MUTABLE policy is the `operation` argument; the 1-arg read
  is `Rec.java:152-160`). A plain `MRec` widget therefore keeps `.append(...)`/`.toggle()` dispatch
  and store write-through unchanged. (Cost note: `Rec.at(k,v,op)` copies the rec map on every call —
  one more reason to funnel writes through a single helper rather than sprinkle rec writes on a
  render path.)
* Genuinely *view* state (cursor, last render height, pane bounds, terminal, size, display) stays in
  Java fields — that is correct and stays unchanged; and view state that must survive re-hydration
  belongs in the **surface slot**, not the widget.
* Cost: mechanical; the JRec-only call surface is ~27 sites (§2). Risk concentrated in
  `Console`/`Pane` (11 + 3 annotated fields, 42 + 9 fields) which can be migrated last or left.

### Option C — fix JRec's semantics instead
Make `at()` non-mutating and single-valued, make `jvmWrite` refresh the local map, fix `clone`,
and give mutable recs stable `equals/hashCode`. Right long-term, but: `Obj.equals/hashCode` is used
across the whole VM (not just widgets), so this is a platform change with a much larger blast
radius, and it still does not fix "a Java field cannot hold state that must survive re-hydration".
Worth doing *for JRec's remaining clients*, separately.

### Option D — split data from view inside the rec (a modifier, not a rival)
Give widgets a rec shape of `[data=>[...], style=>...]` and keep view state out of the rec entirely
(it lives in the slot). This is what the surface already does for geometry/scroll; adopting it
explicitly prevents future "which layer owns this?" confusion. It composes with A or B.

| | A | B | C |
|---|---|---|---|
| fixes stale reads/writes | mostly (by discipline) | **yes** (one read, one write path) | yes |
| re-hydration-safe state | yes, if discipline held | **yes, structurally** | no |
| blast radius | widget layer | widget layer | whole VM |
| removes the reflective bridge | no | **yes** | no |
| effort | 300-500 LOC | 600-900 LOC + tests | large, uncapped |
| can be staged | yes | yes (after A) | no |

## 6. Recommendation

**B, prepared by A.** Concretely, four steps that each ship on their own:

1. **Phase 0 (do now, ~300-500 LOC, low risk).**
   * Style leaves the field: the widget reads/writes the rec's `style` key (a `Style` instance may
     remain as a *view* over that key, but it must not be a detached copy — `Style.from()` currently
     copies, `Stylable.java:236-251`).
   * `TableWidget`'s three `@JRecElement` fields become rec keys read through the state helper;
     delete `javaPopulated`/`sync` once round-tripping is one-directional.
   * Add `RecState` (one place to read/write; space-aware; normalises `Objs`; never mutates on read)
     and use it for every widget data access.
   * Convert the eight workarounds in §3 into either "deleted" or "explicit, documented view state".
2. **Phase 1 (the base swap, ~400-600 LOC, mechanical).**
   `AbstractWidget extends JRec<W>` → `WidgetRec<W> extends MRec`; same for `AccordionWidget`,
   `PanelWidget`, `TableWidget`, `TreeWidget`. Port the ~27 `jvmRead/jvmWrite` sites to the helper.
3. **Phase 2 (Console/Pane, decide then).** Recommended eventually: `Console` keeps a small rec for
   its 2-3 settings (`prefix`, `postfix`, `metatron_version`) and everything else is runtime state;
   `Pane` likewise. Lowest priority because they are interactive components, not data recs.
4. **Phase 3 (separate initiative).** JRec platform defects (§10) for `BasicRouter`/`Rewriter`, or
   freeze JRec and migrate those two later.

Enforcement (makes Phase 0 stick): a unit test that reflects over every class implementing
`Widget` and fails when it declares a non-final, non-transient field that is not on an explicit
allow-list (collaborators, render scratch, caches) — the rule then lives in CI instead of in a
comment.

### Suggested migration order and why
`AccordionWidget` (most JRec-touching, best understood, pty-covered) → `PanelWidget` → `TreeWidget`
→ `TableWidget` (annotated fields) → `AbstractWidget` base swap → the ~20 tools (no changes needed,
they only inherit) → `Console`/`Pane` last.

### What could go wrong, and the guards
* *A widget that mtron mutates stops seeing the mutation* — guarded by: one read path, and the pty
  suites that drive `>>=[body=>...]`, fold, scroll, and pointer gestures against a store-backed
  widget (`bin/test/console-widget-*.steps`, 26 checks).
* *A widget loses view state on re-hydration* — guarded by the rule that view state lives in the
  slot; the surfaces' existing re-float tests cover it.
* *Serialization/registration regressions* — guarded by the UI unit suite (237 tests) and
  `uiInstSetTest` (23).

## 7. Field disposition (abridged)

| Class | Rec-state in Java fields (must move) | Legitimately Java (stays) |
|---|---|---|
| `AbstractWidget` | `style` (dup of rec `style`) | `terminal`, `size`, `display`, `cursor`, `attributes`, pane bounds, `lastRenderHeight` |
| `AccordionWidget` | `style`; `pendingBuffer` (staging — keep only as an explicit cache with a flush contract) | `lastRenderHeight`, `cursor` |
| `PanelWidget` | `style`; `maxWidth` (render setting mtron cannot see) | `cursor` |
| `TableWidget` | `style`; `headers`/`table`/`metadata` (annotated, dual) ; `javaPopulated` (arbitration flag to delete) | `cursor`, `lastRenderHeight` |
| `TreeWidget` | *(done — none)* | `rows` (now a local), `forceExpand` (now `expand` in the rec) |
| `MenuBarWidget`, `AbstractLineWidget`, `ProgressTableWidget` | `style` (via `readStyle`/`Style.from`) | render scratch |
| `Console` | `prefix`, `postfix`, `metatron_version` (annotated) | ~35 runtime fields (terminal, reader, panes, watcher flags, focus/pointer state) |
| `Pane` | annotated fields (3) | `id`, `language`, `machine`, `outputBuffer`, `maxOutputLines`, … |

Field declarations were extracted mechanically (regex over `isa/mach/type/ui/**`) for this table:
`AccordionWidget` 6, `PanelWidget` 1, `TableWidget` 3, `TreeWidget` 2, `AbstractWidget` 9,
`Console` 42, `Pane` 9 declared fields besides constants — plus the `style` field every widget
inherits from `AbstractWidget.java:58`.

## 8. Open questions for the maintainer

1. Confirm "widgets stay recs, but not JRecs" is acceptable (Option B) — everything else follows.
2. Should `Style` stay a Java object that *views* the rec's `style` key (recommended), or become
   plain key access with no object at all?
3. Console/Pane: migrate in this initiative, or leave on JRec indefinitely?
4. Do we add the CI guard test (no state fields in widget classes)?
5. Do we fix JRec's platform defects for its other clients, or freeze JRec and migrate
   `BasicRouter`/`Rewriter` off it later?

## Appendix A — the probe (deleted after the run)

```java
// TableWidget (annotated fields) — what does a rec read actually return?
sayShape("table.at(header)", table.at(uri("header")));

// AccordionWidget with vid /sys/jrec_probe — is a vid-backed write visible to rec reads?
widget.body("written-through-jvmWrite");          // -> JRec.jvmWrite -> space only
say("bodyLines (jvmRead)", widget.bodyLines());
sayShape("at(body)", widget.at(uri("body")));
sayShape("jvm().get(body)", widget.jvm().get(uri("body")));
```

Raw output:

```
JRECPROBE table.at(header) = {{?}[,],['a','b']}  [class=MObjs isStr=false isBool=false isObjs=true isLst=false tid=/m/lst{1,2}]
JRECPROBE table.atDirect(header) = {{?}[,],['a','b']}  [class=MObjs … isLst=false …]
JRECPROBE table.at(header).isLst = false
JRECPROBE before format: keys = [title, body]
JRECPROBE after format: keys = [title, body, toggle, expand, collapse, append]
JRECPROBE after format: jvm() equals jvm() = true
JRECPROBE after format: hashCode stable = true
JRECPROBE fresh: bodyLines = [initial]
JRECPROBE fresh: at(body) = 'initial'
JRECPROBE fresh: jvm().get(body) = 'initial'
JRECPROBE after jvmWrite: bodyLines (jvmRead) = [written-through-jvmWrite]
JRECPROBE after jvmWrite: at(body) = 'initial'            <-- stale
JRECPROBE after jvmWrite: jvm().get(body) = 'initial'     <-- stale
```

## Appendix B — JRec platform defects (for its own clients, not widget-specific)

1. `clone(jvm,tid,vid)` returns `this` (`JRec.java:73-77`) → aliasing.
2. `at(key)` mutates the map as a side effect and may return an `Objs`
   (`JRec.java:93-118`) → shape-dependent wrong answers with defaults.
3. `jvmWrite` for vid-backed objects updates only the space (`JRec.java:192-200`) → stale rec/field
   reads (measured).
4. `jvm()` rebuilds inst objects per call (`JRec.java:144-146`) → `Map.equals`/`hashCode` on derived
   state are not stable; `Obj.hashCode` is `jvm()`-derived (`Obj.java:999`), so any Obj is unsafe as
   a `HashMap` key once it mutates (incident #1 in §3).
5. Reflection + `objCheckAndSave` run on every construction (`JRec.java:64-70`) — a fixed cost paid
   by every widget, tool, console and pane.
