---
name: metatron-ui-architecture
description: Architecture of the metatron UI subsystem (Widget, Style, FloatingSurface, JRec state bridge, uiInstSet type registration).  Reference for agents creating or modifying widgets.
---

# Metatron UI Architecture

## Package map

```
isa.mach.type.ui
  Widget.java              ← interface: run(), format(), style(), floatAt()
  Stylable.java            ← Style inner class: border, anchor, width, top, left, zIndex, floatAt(), hasFloat()
  Border.java              ← border constants (simple, continuous, rounded, none, hash, etc.)
isa.mach.type.ui.widget
  FloatingSurface.java     ← terminal-absolute rendering + Anchor enum + Slot + scroll tracking
  AccordionWidget.java     ← collapsible text panel (primary example)
  MenuBarWidget.java       ← top-pinned menu bar; renders at max z-index so it is never overlapped
  PanelWidget.java         ← simple bordered text panel
  TableWidget.java         ← tabular data
  TreeWidget.java          ← tree display
  SelectorWidget.java      ← interactive cursor-based selection
  Selector.java             ← selector with attachment
  AbstractWidget.java       ← base for interactive widgets (raw mode, key handling)
  GridWidget.java           ← widget grid layout
  CardWidget.java           ← simple card
  WidgetCanvas.java         ← pane-bounded absolute/relative render helper
  Utilities.java            ← runCursorLessWidget, key constants
isa.mach.type.ui.console
  Console.java              ← REPL, terminal, pane tree, FloatingSurface integration
  StatusLine.java           ← terminal status bar
  Highlighter.java          ← syntax highlighting + visualLength/unformat
  Hotkeys.java              ← keystrokes typed while a job holds the console (alt+b, [q], type-ahead)
  CommandPalette.java       ← see isa.mach.type.ui.console.menu
isa.mach.type.ui.console.menu
  CommandPalette.java       ← : commands, builtin shortcut keys
isa.mach.type.ui.graphitty
  Graphitty.java            ← {{macro}} DSL → ANSI escapes
isa.mach.type.ui.tmux
  Pane.java                 ← tmux-style split pane
  PaneNode.java             ← pane tree interface
  SplitContainer.java       ← pane split container
  SplitLayout.java          ← HORIZONTAL/VERTICAL split direction
isa.mach.type.ui.tool
  ExplainTool.java         ← drill-down code inspector (Tab on code in REPL)
  TraceTool.java           ← fail cause-chain explorer (:trace toggle)
  ProfileTool.java         ← instruction profiler (ExplainTool delegate)
  InstSelectorTool.java    ← instruction selector for dot-completion
  fURISelectorTool.java    ← URI/folder selector for wildcard completion
  TypeDiffTool.java        ← type diff visualizer
  TreeSelectTool.java      ← interactive tree browser with nested obj inspection
  SwipePanelWidgetTool.java ← left-right swipe panel for browsing a stream of objs
  ModalTool.java          ← modal popup panel (title + body), dismiss on space/enter/ctrl-d
isa.mach.ui
  uiInstSet.java            ← mtron type/instruction registration for all UI types
isa.m.type.reflect
  JRec.java                 ← Java-backed mtron rec: jvmRead(), jvmWrite(), extractors
```

## 1. Widget interface (`Widget.java`)

Every widget implements `Widget<W> extends Stylable<W>, AutoCloseable, Runnable`.

Key methods:

```java
// Present this widget.  Default: checks style.hasFloat() →
// floatAt() + surface.render()  OR  Graphitty.out(format()).
// Interactive widgets (Selector, ExplainTool) override with modal loop.
default void run()

// String representation (for rendering).  Subclasses MUST override.
String format()

// CSS-style float positioning on any widget.
default W floatAt(FloatingSurface surface, int row, int col)

default W floatAt(FloatingSurface surface, Anchor anchor, int width)

default W floatAt(FloatingSurface surface, Anchor anchor, int width, int top, int left)

default W unfloat(FloatingSurface surface)
```

**Consolidation note:** `display()` was removed — `run()` is the single presentation method.

**`chromeLines()`** — defaults to 1 if a border is configured, 0 otherwise. Widgets with column headers or status bars
override to add their own. Used by `FloatingSurface` to preserve structural chrome when the `height` cap clips body
lines.

**`rowCount()` / `rowString(int)` / `rowStrings()`** — defaults that split `format()` output by `\n`. Selectors iterate
over these for cursor navigation; a widget that wants column-aware selection should either override them or attach a
`TableWidget`.

## 2. JRec state bridge (`JRec.java`)

There are **two** state models in the widget tree, depending on when the widget was written:

### Model A: JVM-as-source-of-truth (AccordionWidget — preferred for new widgets)

State lives in the **persistent store** (memSpace, tbleSpace, etc.). Java fields annotated with `@JRecElement` are
**metadata for mtron introspection only** — never read by Java code. Mutators call `jvmWrite()`.  `format()` calls
`jvmRead()`
and extracts values fresh on every render.

### Model B: Java-fields-as-storage (legacy — TableWidget, PanelWidget, TreeWidget)

Java fields ARE the data store. A private `sync()` method pulls initial state from JVM when the widget was constructed
from mtron, but once Java fields are populated (by Java API or prior sync), JVM is never consulted again. Mutators use
direct field assignment or builders (`addRow()`, `addMetadata()`).

**Shared primitives** (both models):

```java
// Read latest state from persistent store (if vid is set) or local JVM merge.
jvmRead() → Router.

global().

read(vid) → freshObj.

jvm()

// Write a single field with >>=-style merge: read fresh, merge, write back.
jvmWrite(key, value)

// Static typed extractors — use with Map<Obj,Obj> from jvmRead():
jvmStr(jvm, key)     →

String
jvmBool(jvm, key)    →

boolean(defaults true if absent)

jvmInt(jvm, key, fb) →

int(with fallback)

jvmBody(jvm, key)    → List<String>  (splits \\
n and \n)
```

**Model A — JVM-as-source-of-truth** (AccordionWidget; preferred for new widgets):

```java
// Constructor — reads style from JVM so run() sees it
public MyWidget(Map<Obj, Obj> jvm, fURI tid, fURI vid) {
    super(new HashMap<>(jvm), tid, vid);
    readStyle(this.jvm());
}

// format() — one jvmRead(), all state extracted fresh
@Override
public String format() {
    Map<Obj, Obj> jvm = jvmRead();
    String title = jvmStr(jvm, keyTitle);
    // ... render ...
}

// Mutators — write through to persistent store
public void setTitle(String t) {
    jvmWrite(kTitle, str(t));
}
```

**Model B — Java-fields-as-storage** (TableWidget, PanelWidget, TreeWidget; legacy):

```java
// The fool-proof version — tracking flag prevents JVM from overwriting Java data.
private boolean javaPopulated = false;

// Every Java API mutation sets the flag.
public TableWidget addRow(final List<Object> entries) {
    this.javaPopulated = true;
    this.table.add(entries);
    return this;
}

private void sync() {
    if (this.style == null) return;    // construction guard
    if (this.javaPopulated) return;    // Java API owns the data — skip
    Map<Obj, Obj> jvm = jvmRead();     // snapshot JVM
    // Populate Java fields FROM JVM (mtron-constructed tables only):
    Obj h = jvm.get(uri("headers"));
    if (h != null && !h.isNoObj())
        h.stream().filter(Obj::isStr).forEach(o -> this.headers.add(o.strValue()));
    // ... rows, metadata ...
}
```

**⚠️ History:** Before 2026-07-26, `TableWidget.sync()` unconditionally `clear()`ed Java fields and repopulated from the
JVM snapshot. This destroyed Java-constructed tables (ProfileTool, ExplainTool, TraceTool) because the JVM serialization
round-trip through `MObjFactory.toObj()` changes container types (`Objs` vs `Lst`, `ListN` vs
`ArrayList`). The `javaPopulated` flag (shown above) fixes this. It's safer than per-field `isEmpty()` guards because a
Java-constructed table may legitimately have an empty field (e.g. headers-only table with no rows) — without the flag,
sync would pull stale rows from a prior mtron construction.

**Also: avoid `Stream.toList()` in sync ().**  `Stream.toList()` (Java 16+) returns immutable
`ImmutableCollections$ListN`. Use `Collectors.toCollection(ArrayList::new)`
instead — it keeps rows on the mutable-`ArrayList` path the rest of the codebase expects.

**When to use the legacy sync pattern vs. direct `jvmRead()`:**

- **New widgets** (AccordionWidget): read from `jvmRead()` directly in `format()`. Java fields are metadata only.
  Mutators use `jvmWrite()`.
- **Legacy widgets** (TableWidget, PanelWidget, TreeWidget): Java fields are the storage.  `sync()` populates them from
  JVM. Mutators use direct field assignment or `addRow()`/`addMetadata()` builders. The sync guard protects these from
  being overwritten.

## 3. Style system (`Stylable.Style`)

Style is a JVM-backed rec. Fields:

| Field           | Type            | Description                                                                                                                                      |
|-----------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `border`        | uri             | simple, continuous, rounded, none, thick, hash, asterisk, period                                                                                 |
| `background`    | str             | Graphitty color macro e.g. `{{[R]}}`                                                                                                             |
| `foreground`    | str             | Graphitty color macro e.g. `{{g}}`                                                                                                               |
| `divider`       | str             | Column/row divider char                                                                                                                          |
| `headerDivider` | str             | Header divider char                                                                                                                              |
| `pointer`       | str             | Selection pointer e.g. `{{r}}>`                                                                                                                  |
| `anchor`        | uri (coproduct) | top_left, top_middle, top_right, middle, bottom_left, bottom_middle, bottom_right                                                                |
| `width`         | int             | Display width in columns; 0 = natural                                                                                                            |
| `top`           | int             | Row offset from anchor edge (CSS top)                                                                                                            |
| `left`          | int             | Column offset from anchor edge (CSS left)                                                                                                        |
| `leftMargin`    | int             | Left margin                                                                                                                                      |
| `rightMargin`   | int             | Right margin                                                                                                                                     |
| `topMargin`     | int             | Top margin                                                                                                                                       |
| `bottomMargin`  | int             | Bottom margin                                                                                                                                    |
| `height`        | int             | Display height cap in rows; 0 = unbounded. Content exceeding this cap keeps header/chrome lines and discards top body lines (scroll-up behavior) |
| `zIndex`        | int             | Render order among floating widgets: higher = drawn later (on top). Default 0. Menu bars use `Integer.MAX_VALUE`. |
| `focus`         | str             | Focus highlight color (Graphitty code, e.g. `{{r}}`) applied to the focus marker of the active widget |
| `focus_token`   | str             | Marker char drawn at the focused widget's top-left corner (default `▶`) |

Float-related:

```java
style.floatAt(anchor, width, top, left)  // configure floating
style.

hasFloat()                          // true if anchor is set
style.

anchor()                            // returns Anchor enum
style.

width()                             // display width override
style.

top() /style.

left()                // offsets
style.

unfloat()                           // clear floating config
style.

zIndex(n)                           // render order: higher z = drawn on top of lower (default 0)
style.

focus(color) / focus()             // focus highlight color (Graphitty code)
style.

focusToken(token) / focusToken()  // focus marker char (default ▶)
style.

focused(bool) / focused()         // transient keyboard-focus flag
```

Text utility:

```java
Style.wrapLines(List<String> lines, int maxWidth) →List<String>
// Splits lines at word boundaries to fit maxWidth visual chars.
// Any widget with text content can call this.
```

## 4. FloatingSurface + Anchor

```java
// Terminal-absolute rendering surface.  Draw AFTER pane content.
FloatingSurface surface = new FloatingSurface(terminal);

// Pin a widget:
surface.

add(widget, row, col);                       // absolute
surface.

add(widget, anchor, width);                  // anchored
surface.

add(widget, anchor, width, top, left);       // anchored + offsets

// Anchor enum: TOP_LEFT, TOP_MIDDLE, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT
// Anchor.parse("top_middle") → TOP_MIDDLE  (also "tm", "tl", "tr", "bl", "bm", "br")

// Render cycle (automatic):
surface.

render();  // \033[s → draw all (z-sorted) → \033[u (preserves cursor)
surface.

remove(widget);  // unpin + clear area
surface.

clear();         // remove all
```

**Console integration** is automatic:

```java
Console.LOCAL_INSTANCE.getFloatingSurface()  // shared instance
// Rendered at every prepareForInput() + renderPanes()
// Widget.run() also calls render() for immediate display
```

### Rendering semantics

- **Width-scoped clearing** — each widget's previous footprint is tracked as `prevHeight` **and** `prevWidth`. Stale
  content is cleared with width-bounded spaces, never `\033[K` (erase-to-end-of-line), so a widget anchored at one
  column can never blank a widget anchored at another column on the same rows (e.g. a tall `BOTTOM_RIGHT` accordion
  under a `TOP_LEFT` widget). Shrink (a taller/wider prior render) is cleaned within the old footprint only.
- **Scroll compensation** — console output is written at the bottom and scrolls the terminal, carrying bottom-anchored
  widgets up the screen. The render thread counts newlines in every `writeToTerminal` write (all console output flows
  through the serialized bridge) and each render pass erases every widget's previous representation *at its scrolled
  position* (`oldLastRow − scroll`), width-scoped. Without this, stale copies rise row-by-row with each output line.
- **z-order** — `renderInternal` draws widgets sorted by `style.zIndex()` ascending (stable sort — equal-z widgets keep
  their current order). Higher z paints on top when regions overlap. `MenuBarWidget.run()` sets
  `zIndex(Integer.MAX_VALUE)` so the bar is never overlapped.

### Backgrounding a busy console (`<alt>+b`)

A console line runs its `SwarmMachine` **on the repl thread**: `executeMtron` starts the machine
with `applyAsync()` and then waits on its future. Forgetting to thread a long agent prompt therefore
parks the repl — the keyboard looks dead even though the machine is already on its own thread.

`Console.awaitForeground(mach, future, line)` replaces that wait. It polls the future every
`FOREGROUND_POLL_MS` (120 ms) while a **watcher thread** (`Console.watchTerminal`, a platform
`CoreThread`) reads the terminal and classifies each keystroke through `Hotkeys`:

| Key       | Effect |
|-----------|--------|
| `alt+b`   | **detach** — hand the running job to the background and return to the prompt |
| `ctrl+c`  | stop the job (raw mode can disable sigint on some terminals — this is the fallback) |
| `[q]`     | cancel the stream — only honored once the cancel offer has been printed |
| any text  | kept as the next prompt's seed buffer, so typing ahead of a long job is never lost |

**Why a watcher thread and not a poll loop.** Two terminal facts force the shape:

- Outside `readLine()` jline has restored the tty's **cooked** attributes, so keystrokes are
  line-buffered and a lone `alt+b` never arrives — `awaitForeground` therefore holds
  `terminal.enterRawMode()` for the life of the job (jline saves/restores its own attributes around
  each `readLine`, so handing the cooked attributes back afterwards is what it expects).
- `NonBlockingReader.read(timeout)`/`peek(timeout)` cannot be used for polling: with `timeout == 0`
  (`read()`) a raw-mode read never expires at all, and in cooked mode even `peek(1)` blocks in the
  pty's native read. So the blocking read lives on the watcher thread — never on the repl thread —
  and uses a **bounded** `read(WATCH_READ_MS = 100)`.

**The handoff is ordered** (`beginWatch`/`endWatch`/`releaseTerminal`, all under `watchGate`).
A read that is still pending when the cooked attributes go back becomes a *blocking cooked* read
that eats the user's next keystroke — in practice the escape byte of an arrow key, leaving its tail
in the line as literal text (the classic `OA` in the prompt). So the watcher is told to stop, the
prompt waits (bounded by `WATCH_HANDOFF_MS`) for it to leave its read, and only then are the cooked
attributes restored.

**Escape sequences are swallowed whole.** `Hotkeys` is a pure, terminal-free state machine
(`NORMAL → ESC → CSI`): the detach combo is `\e b`, arrows/function keys/unknown alt-combos are
consumed and dropped, `DEL`/backspace edit the kept text, and a keystroke is only ever *taken* when
the watcher owns the terminal. A read that expires (`READ_EXPIRED`) drops a half-seen sequence, and
a sequence already in flight is finished even after the job ended (`Hotkeys.inSequence()` keeps
`beginWatch` true) — either way no fragment can reach the next line as text. `alt+b` is deliberately
**not a keymap binding**: jline's emacs map owns `\eb` (backward-word) while the prompt is live.

Detaching does not touch the job — it keeps its thread, keeps updating widgets, and stays
addressable at its own vid (machines live under `/sys/machine/<n>`, spawned threads under
`/sys/thread/<uuid>`; the banner echoes it). What changes is the console's *attention*:
`detachForegroundJob` clears both interrupt handles (`Console.machine` and `activePane.machine()`)
so ctrl-c at the returning prompt cannot kill it, and starts a "background result collector" thread
that prints the job's result when it halts. The rest of the current input (later `;`-separated
segments) is abandoned with the backgrounded turn.

```java
console.backgroundJobs()       // detached jobs still running, in detach order
console.backgroundJobVids()    // their vids (each addressable, e.g. /sys/machine/2)
console.stopBackgroundJobs()   // stop them all — returns the count
```

**Testing it.**  `bin/metatron-console` boots a console-only VM in a pty and plays a step script at
it (`bin/metatron-docker build console --steps bin/test/console-smoke.steps`), which is the only way to
exercise the raw-mode path — see AGENTS.md → "Driving the console in a pty".

`:bg` lists them, `:bg stop` stops them.  The decision table lives in the terminal-free
`Console.foregroundStep(detach, interrupt, cancel, userMode, nowMs, offerAtMs)` → `{WAIT, DETACH,
INTERRUPT, CANCEL, OFFER_CANCEL}`: detach beats everything, ctrl+c beats cancel, and a widget on
screen (`userMode`) suppresses the cancel offer, as before.

### Widget focus & keyboard resize

Floating widgets can be cycled and resized from the keyboard, mirroring the console pane
system (`activePane` ↔ `activeWidget`).

**Keys** (bound in `CommandPalette.bindKeys()`):

| Keys                          | Action |
|-------------------------------|--------|
| `alt+w`                       | cycle focus to the next floating widget (wraps) |
| `alt+^` / `alt+v`             | grow / shrink focused widget height (±1 row) |
| `alt+>` / `alt+<`             | grow / shrink focused widget width (±4 cols); falls back to pane resize when no widget is focused |

Colon commands: `:next-widget`, `:prev-widget`, `:focus-widget [name\|off]`, `:widgets`
(lists floating widgets, active one marked — same pattern as `:panes`), and `:keymap`
(which builtin shortcut key is currently held by the console vs shadowed by a later binder).

**Key durability (reassertion)** — the five shortcut sequences above are *reasserted
builtins*: `CommandPalette.bindBuiltin` registers handler identity with the console
(`console.registerBuiltinKey(seq, handler)`), and `Console.prepareForInput` calls
`reassertBuiltinKeys()` **before every prompt** — restoring any sequence a later binder
(a menu line key, a tool) shadowed, by identity comparison on the shared `"main"` keymap
(`Console.reassertBuiltin(keyMap, registered)`).  A shadow can therefore only last until
the next read — never across turns.  This matters because the jline fork
**pre-binds `\e<` = beginning-of-history and `\e>` = end-of-history on its emacs map —
both silent**: an unowned `alt+<` / `alt+>` does nothing visible (it quietly jumps the
prompt history), which is exactly what "the key is dead" looks like.  `:keymap` shows the
current owner of each builtin sequence and the total escape-sequence binding count.

**Which edge moves is decided by the slot's anchor** — the anchor pins one edge, the free
edge does the moving: a bottom-anchored widget's **top** edge lifts on `alt+^` (grow height);
a left-anchored widget's **right** edge extends on `alt+>` (grow width), while a
right-anchored one pulls in its **left** edge; top-anchored widgets are the mirror image
(their bottom/right edges are the free ones).

**Focus registry** lives on the `Console` (analogous to `activePane`):

```java
console.hasFloatingWidgets()
console.getFloatingWidgets()           // deterministic order (FloatingSurface.widgets())
console.getActiveWidget()              // the focused floating widget (null = none)
console.focusWidget(w)                 // set focus (null clears)
console.nextWidget() / prevWidget()    // cycle focus (wraps)
console.growActiveWidgetWidth() / shrinkActiveWidgetWidth()   // ±4 cols (WIDGET_WIDTH_STEP)
console.growActiveWidgetHeight() / shrinkActiveWidgetHeight() // ±1 row (WIDGET_HEIGHT_STEP)
```

Focus is tracked by a **stable key** — `FloatingSurface.widgetKey(w)` (the widget's `vid`,
e.g. `think_widget`, with an identity fallback for vid-less widgets) — rather than the
instance: a floating widget is re-hydrated into a fresh instance on every `.display()`
update, and the key is what keeps focus and resize durable across re-floats.
`FloatingSurface.setFocusKey(key)` is the presentation hint the render pass consumes.

**Geometry durability** — the slot (not the widget instance) owns the live geometry:

- `FloatingSurface.nudge(w, widthΔ, heightΔ)` mutates `slot.targetWidth` (clamped to
  `[10, terminalWidth]`) and the slot's effective height cap (clamped to ≥3 rows), also
  refreshes the live `style.width()` for content-shaping widgets (e.g. `AccordionWidget`
  body wrap), and re-renders.
- On re-float, `add(widget, anchor, width, top, left)` carries the replaced slot's
  `targetWidth` and `heightCap` into the new slot — the rehydrated instance's
  (un-resized) `style.width()`/`style.height()` must not reset user geometry — and
  back-fills the resized width into the fresh instance's style when its own was unset.
- Rendering resolves the height cap as `slot.heightCap > 0 ? slot.heightCap : style.height()`.

**Focus marker** — `FloatingSurface.renderWidget` draws `▶` in the focused widget's own
**top-left corner cell — inside the box**.  Placing the marker inside the widget's erase
region makes ghost markers impossible: whatever owns that cell on the next pass paints over
it, so a marker can never outlive its widget's next draw (a left-of-box marker used to sit
*outside* every erase region and survived re-floats as a ghost).  The top-of-pass blank
(`markerRow`/`markerCol`) remains as a belt-and-braces sweep.  Related scroll hygiene: the
scroll-compensation erase blanks a **wrap-tolerant band** (4 rows above + 1 below the
computed stale position, scoped to the widget's own columns) — wrapped console lines add
visual rows without newlines, so the true scroll can exceed `scrollAccum` and leave a stale
box copy otherwise.

**Deterministic focus order** — `surface.widgets()` sorts slots by z-index (lowest first) →
anchor reading order (top row→bottom row, left→right) → top/left offsets → target width
(widest first). Only slot geometry — stable across re-floats — feeds the order, so cycling
never jumps around.

## 5. Instruction registration (`uiInstSet.java`)

Widgets are mtron-constructable via `uiInstSet`.

### Type registration

```java
// Each widget type needs:
public static final fURI UI_MYWIDGET_TID = UI_ISA_TID.extend("mywidget");
public static Type UI_MYWIDGET_TYPE;

// In setup():
UI_MYWIDGET_TYPE =Type.Builder.

build()
    .

tid(UI_WIDGET_TID)               // parent type
    .

vid(UI_MYWIDGET_TID)             // this type
    .

isaPredicate(rec(                // field declarations for mtron introspection
        uri("title").

maybe(),STR_TYPE,

uri("body").

maybe(),STR_TYPE
    ))
            .

constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).

rng(UI_MYWIDGET_TID),

lst(T(REC_TID)),
        (lhs,inst)->new

MyWidget(inst.arg(0).

as().

jvm(),UI_MYWIDGET_TID,inst.

arg(0).

vid())))
        .

create();
```

### Instruction registration

```java
// display — standard for all widgets
docWrap(instC(UI_INST_TID.extend("display")
        .

dom(UI_WIDGET_TID).

rng(NOOBJ_TID),

lst(),
        (lhs,inst)->{((Widget<?>)lhs).

run(); ((Widget<?>)lhs).

close(); return

noobj(); }),
        "display the widget on the terminal");

// Custom instructions (AccordionWidget example):
// Register in the widget's format() method with a one-time guard:
        if(!jvm.

containsKey(uri("toggle"))){
        this.

at(uri("toggle"),instLambda((l,i)->{
        this.

toggle();
        Graphitty.

out(Console.getTerminal().

output(), this.

format() +"\n");
        return

noobj();
    }),MUTABLE);
        }
```

### Coproduct types

```java
// For closed-set URI values (like Anchor) — derived from the enum so they stay in sync:
UI_ANCHOR_TYPE =Type.Builder.

build()
    .

tid(URI_TID)
    .

vid(UI_ANCHOR_TID)
    .

isaPredicate(inside_(lst(Arrays.stream(FloatingSurface.Anchor.values())
        .

map(a ->

uri(a.name().

toLowerCase()))
        .

toArray(Obj[]::new))))
        .

create();
```

## 6. How to create a new Widget

1. **Create the Java class** in `isa.mach.type.ui.widget`:
   ```java
   public class MyWidget extends JRec<MyWidget> implements Widget<MyWidget> {
       // @JRecElement fields for mtron introspection
       @JRecElement(key = "title", rng = "/m/str")
       private String _title = "";

       // Obj key constants (uri-wrapped once for performance)
       private static final Obj K_TITLE = uri("title");

       private Style<MyWidget> style = Style.empty();

       // JRec constructor
       public MyWidget(Map<Obj, Obj> jvm, fURI tid, fURI vid) {
           super(new HashMap<>(jvm), tid, vid);
           readStyle(this.jvm());  // pull style from JVM before run()
       }

       private void readStyle(Map<Obj, Obj> jvm) {
           Obj s = jvm.get(uri("style"));
           if (s != null && s.isRec()) {
               Style<MyWidget> st = Style.from(s.as());
               st.stylable = this;
               this.style(st);
           }
       }

       // Mutations: write through to persistent store
       public void setTitle(String t) { jvmWrite(K_TITLE, str(t)); }

       // Accessors: read from persistent store
       public String getTitle() { return jvmStr(jvmRead(), K_TITLE); }

       // format() — one jvmRead(), extract all state
       @Override public String format() {
           Map<Obj, Obj> jvm = jvmRead();
           String title = jvmStr(jvm, K_TITLE);
           // ... build and return formatted string ...
       }

       // Style
       @Override public Style<MyWidget> getStyle() { return style; }
       @Override public MyWidget style(Style<MyWidget> s) {
           this.style = s;
           if (this.style.border() == Border.none)
               this.style.border(Border.continuous);
           return this;
       }

       // Widget contract
       @Override public void close() {}
       @Override public String renderInPlace() { return format() + "\n"; }
       @Override public String renderFresh() { return format() + "\n"; }
       @Override public MyWidget cursor(Cursor c) { return this; }
   }
   ```

2. **Register the type** in `uiInstSet.java`:
    - Add `public static final fURI UI_MYWIDGET_TID` and `public static Type UI_MYWIDGET_TYPE`
    - Add type definition in `setup()` with `.isaPredicate(rec(...))` and `.constructor(...)`
    - Add to `display` instruction if needed

3. **For floating support**: nothing extra — `Widget.run()` already checks `style.hasFloat()`

4. **For word-wrap**: call `Stylable.Style.wrapLines(lines, maxWidth)` in `format()`

5. **For interactive widgets** (keyboard input): extend `AbstractWidget`, override `run()` with a modal loop using
   `BindingReader`

6. **Choose the right state model**:

   | If your widget… | Use |
                        |---|---|
   | Has simple key/value fields, built from mtron or Java | **Model A** (JVM-as-source-of-truth).  Mutators call `jvmWrite()`, `format()` calls `jvmRead()`.  See AccordionWidget. |
   | Has list/table data populated via Java builders (`addRow()`, etc.) | **Model B** (Java-fields-as-storage) with the `javaPopulated` tracking flag.  See TableWidget. |
   | Is a pure display widget with no mutable state | Either — Model A is simpler. |

## 7. Key patterns

### Read style from JVM before run ()

```java
public MyWidget(...) {
    super(...);
    readStyle(this.jvm());  // ← CRITICAL: run() checks style.hasFloat() before format()
}
```

### @JRecElement fields are metadata, NOT state

```java

@JRecElement(key = "title", rng = "/m/str")
private String _title = "";  // never read by Java code — jvmStr() is the source of truth
```

### One jvmRead () per render

```java
Map<Obj, Obj> jvm = jvmRead();  // single roundtrip
String a = jvmStr(jvm, "a");
boolean b = jvmBool(jvm, "b");
List<String> c = jvmBody(jvm, "c");
```

### Anchor naming

```java
// mtron: anchor=>top_middle  (URI, not string)
// Java:  FloatingSurface.Anchor.TOP_MIDDLE
// Full:  top_left, top_middle, top_right, bottom_left, bottom_middle, bottom_right
// Short: tl, tm, tr, bl, bm, br
```

### Border defaults

```java
if(this.style.border() ==Border.none)
        this.style.

border(Border.continuous);  // Unicode box-drawing characters
```

## 8. Tool package (`isa.mach.type.ui.tool`)

Tools are higher-level compositions of widgets for specific REPL interactions. They extend `AbstractWidget` and override
`run()` with a modal input loop. Unlike display-only widgets (which use `Widget.run()` default → float or inline
`format()`), tools own their entire render cycle via `beginRedraw()` → `WidgetCanvas`.

### Lifecycle

```java
// In Console.java — triggered by Tab, Enter on code, colon commands, etc.
ExplainTool explain = new ExplainTool(code.as());
Utilities.

runCursorLessWidget(explain, true);
```

`Utilities.runCursorLessWidget()` hides the cursor, calls `widget.run()`, then calls
`widget.close()`:

```java
public static void runCursorLessWidget(Widget<?> widget, boolean close) {
    int height = widget.height();
    Graphitty.log(Widget.class).none("{{.}}");   // hide cursor
    widget.run();
    Graphitty.log(Widget.class).none("{{*}}{{^%d}}", height); // show cursor, move up
    if (close) widget.close();
}
```

### Rendering pattern

Tools use `beginRedraw()` → `WidgetCanvas` (relative mode when no pane bounds are set):

```java
private void redrawStack() {
    WidgetCanvas canvas = beginRedraw(totalHeightUsed);
    for (String line : table.rowStrings()) {
        canvas.line(line);
    }
    canvas.statusLine("{{w}}ctrl-d{{g}}:close {{X}}");
    totalHeightUsed = canvas.finish();
}
```

`WidgetCanvas` handles two modes transparently:

- **Absolute** (pane bounds set): ANSI cursor-positioning inside the pane's content area. Lines exceeding
  `contentMaxLines` are silently dropped.
- **Relative** (no pane bounds): cursor-up to previous height → clear line → print → `\r\n`.  `previousHeight` is used
  to clear leftover lines from taller prior renders.

### Tool → Widget dependencies

| Tool                   | Widgets used                                                     |
|------------------------|------------------------------------------------------------------|
| `ExplainTool`          | `ProfileTool` → `TableWidget` (instruction table)                |
| `TraceTool`            | `TableWidget` (cause chain + stack frames)                       |
| `InstSelectorTool`     | `SelectorWidget` → `TableWidget` (instruction pairs)             |
| `fURISelectorTool`     | `SelectorWidget` → `TableWidget` (URI pairs)                     |
| `TreeSelectTool`       | `TreeWidget` (navigable tree) + `PanelWidget` (detail panel)     |
| `SwipePanelWidgetTool` | `PanelWidget` (obj display panel, docq + Highlighter formatting) |
| `ModalTool`            | `PanelWidget` (title + body popup panel)                          |

**Important:** These tools populate their `TableWidget`s via Java API (`addRow()`,
`addMetadata()`). They rely on the `sync()` guard pattern (section 2) to prevent data corruption — without it,
`TableWidget.format()` → `sync()` would clear Java-populated rows and replace them with JVM-serialized representations
that don't preserve the exact column structure.

### Selector vs SelectorWidget

Two selection widgets exist with different rendering approaches:

- **`Selector`** (older): Uses JLine's `Display.updateAnsi()` for rendering. Navigates an attached widget's
  `rowString()` output.
- **`SelectorWidget`** (newer): Uses `beginRedraw()` → `WidgetCanvas` for rendering. Manages its own `TableWidget` with
  item pairs. Preferred for new tool development.

### applyStyle () vs apply () — real-world footgun

```java
// WRONG — .apply() calls Obj.apply() (identity function — returns the Style, not the widget):
widget.style().

border(...).

apply();
// ^^ compiles, runs without error, but does NOT wire the style to the widget.
//    ProfileTool.java line 63 still uses this pattern (as of 2026-07-26).

// RIGHT — .applyStyle() calls stylable.style(this):
widget.

style().

border(...).

applyStyle();
```

The default `Obj.apply()` (no-arg) returns `this` — the `Style` object. The return value is discarded, so the chain
silently no-ops. The compiler can't catch this because `Style extends MRec extends MObj implements Obj`, and `Obj` has
`default Obj apply() { return this.apply(noobj()); }`.

## 9. Graphitty — terminal markup DSL (`Graphitty.java`)

Graphitty is a lightweight macro-to-ANSI preprocessor used throughout the UI layer. Tags are written `{{...}}` and are
stripped by `Graphitty.strip()` for visual-length calculations. The DSL supports three families of tags:

### Colour / effect tags

| Tag                                                                                           | ANSI equivalent | Description                                                       |
|-----------------------------------------------------------------------------------------------|-----------------|-------------------------------------------------------------------|
| `{{X}}`                                                                                       | `\033[m`        | reset all attributes                                              |
| `{{k}}` / `{{r}}` / `{{g}}` / `{{y}}` / `{{b}}` / `{{m}}` / `{{c}}` / `{{w}}`                 | `\033[30..37m`  | foreground: black, red, green, yellow, blue, magenta, cyan, white |
| `{{d}}`                                                                                       | `\033[39m`      | default foreground                                                |
| `{{[k]}}` / `{{[r]}}` / `{{[g]}}` / `{{[y]}}` / `{{[b]}}` / `{{[m]}}` / `{{[c]}}` / `{{[w]}}` | `\033[40..47m`  | background colours                                                |
| `{{[X]}}`                                                                                     | `\033[49m`      | default background                                                |
| `{{R}}` / `{{G}}` / `{{Y}}` / `{{B}}` / `{{M}}` / `{{C}}` / `{{W}}`                           | `\033[1;3xm`    | bold foregrounds                                                  |
| `{{~}}`                                                                                       | `\033[3m`       | italics                                                           |
| `{{_}}`                                                                                       | `\033[4m`       | underline                                                         |
| `{{-}}`                                                                                       | `\033[9m`       | strikethrough                                                     |

### Cursor / screen tags

| Tag       | ANSI equivalent | Description                                  |
|-----------|-----------------|----------------------------------------------|
| `{{@}}`   | `\033[H`        | cursor home (row 1, col 1)                   |
| `{{^N}}`  | `\033[NA`       | up N lines (e.g. `{{^5}}`; N=1 when omitted) |
| `{{vN}}`  | `\033[NB`       | down N lines                                 |
| `{{<N}}`  | `\033[ND`       | left N columns                               |
| `{{>N}}`  | `\033[NC`       | right N columns                              |
| `{{\|N}}` | `\033[NG`       | absolute column N (1-based)                  |
| `{{-N}}`  | `\033[NH`       | absolute row N (1-based)                     |
| `{{^<N}}` | `\033[NF`       | beginning of Nth previous line               |
| `{{v<N}}` | `\033[NE`       | beginning of Nth next line                   |
| `{{-X-}}` | `\033[2K`       | clear entire current line                    |
| `{{X-}}`  | `\033[0K`       | clear from cursor to end of line             |
| `{{-X}}`  | `\033[1K`       | clear from beginning of line to cursor       |
| `{{XX}}`  | `\033[2J`       | clear entire screen                          |
| `{{Xv}}`  | `\033[0J`       | clear from cursor to bottom of screen        |
| `{{X^}}`  | `\033[1J`       | clear from top of screen to cursor           |
| `{{(s)}}` | `\033[s`        | save cursor position                         |
| `{{(e)}}` | `\033[u`        | restore cursor position                      |
| `{{*}}`   | `\033[?25h`     | show cursor                                  |
| `{{.}}`   | `\033[?25l`     | hide cursor                                  |

### Stack and chaining

Every opened tag is **pushed onto a stack**. Closing with `{{/rule}}` pops the most-recently-opened matching rule and
restores the previous style. If the popped rule had a parent still on the stack, the parent's escape is re-emitted with
a
`\033[0;…` prefix so the parent style "wins" over the default reset.

Tags separated by `&` are **chained** — `{{r&_}}` emits red-foreground (`\033[31m`)
followed by underline (`\033[4m`), pushing both rules in left-to-right order.

```java
// Stack example — closing restores the previous colour:
"{{B}}blue title{{/B}} and normal text"   // bold blue → reset → normal

// Chaining example — multiple effects in one tag:
        "{{r&_}}red underlined{{/r&_}}"            // red + underline → reset both

// Combined:
        "{{b}}a {{R}}bold red{{/R}} then back to blue{{/b}}."  // bold red → blue → normal
```

### Static helpers

| Method                                  | Purpose                                                                                                                                                 |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Graphitty.string(f, args...)`          | Convert a Graphitty format string to ANSI.  Used everywhere in widget `format()` methods.                                                               |
| `Graphitty.strip(str)`                  | Remove all ANSI escapes (and Graphitty tags) to measure **visual length**.  Used by `Highlighter.visualLength()` and `WidgetCanvas` for width clipping. |
| `Graphitty.out(stream, f, args...)`     | Write a Graphitty string directly to an output stream.  Used by `WidgetCanvas.finish()` for the final flush.                                            |
| `Graphitty.writeToTerminal(f, args...)` | Write through the serialized terminal-writer bridge (FloatingSurface-safe).                                                                             |
| `Graphitty.viewLength(str)`             | Alias for `strip(str).length()`.                                                                                                                        |

### Typical widget usage

Widgets emit **Graphitty-markup strings** from `format()`. The caller is responsible for converting them to ANSI before
writing to the terminal:

```java
// In a widget format() method — return Graphitty markup:
@Override
public String format() {
    return "{{b}}Welcome{{X}} to the {{w}}Machine{{X}}";
}

// At the call site — convert to ANSI and flush:
Graphitty.

out(terminal.output(),widget.

format());
// or: terminal.writer().write(Graphitty.string(widget.format()));
```

`WidgetCanvas` handles this automatically: `canvas.line(content)` stores the raw markup, and `canvas.finish()` passes
the entire buffer through `Graphitty.out()`
in one batch.  `canvas.statusLine()` pre-converts its argument with
`Graphitty.string()` immediately (because it needs to measure the stripped length for pane-width clipping in absolute
mode).
