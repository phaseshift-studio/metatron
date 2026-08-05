---
name: metatron-ui-architecture
description: Architecture of the metatron UI subsystem (Widget, Style, FloatingSurface, JRec state bridge, uiInstSet type registration).  Reference for agents creating or modifying widgets.
---

# Metatron UI Architecture

## Package map

```
isa.mach.type.ui
  Widget.java              ← interface: run(), format(), style(), floatAt()
  Stylable.java            ← Style inner class: border, anchor, width, top, left, floatAt(), hasFloat()
  Border.java              ← border constants (simple, continuous, rounded, none, hash, etc.)
isa.mach.type.ui.widget
  FloatingSurface.java     ← terminal-absolute rendering + Anchor enum + Slot
  AccordionWidget.java     ← collapsible text panel (primary example)
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
  ColonMenu.java            ← : commands including :float demo
isa.mach.type.ui.console.menu
  ColonMenu.java            ← see above
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

**`chromeLines()`** — defaults to 1 if a border is configured, 0 otherwise. Widgets with column headers or status bars override to add their own.  Used by `FloatingSurface` to preserve structural chrome when the `height` cap clips body lines.

**`rowCount()` / `rowString(int)` / `rowStrings()`** — defaults that split `format()` output by `\n`.  Selectors iterate over these for cursor navigation; a widget that wants column-aware selection should either override them or attach a `TableWidget`.

## 2. JRec state bridge (`JRec.java`)

There are **two** state models in the widget tree, depending on when the widget was
written:

### Model A: JVM-as-source-of-truth (AccordionWidget — preferred for new widgets)

State lives in the **persistent store** (memSpace, tbleSpace, etc.).  Java fields
annotated with `@JRecElement` are **metadata for mtron introspection only** —
never read by Java code.  Mutators call `jvmWrite()`.  `format()` calls `jvmRead()`
and extracts values fresh on every render.

### Model B: Java-fields-as-storage (legacy — TableWidget, PanelWidget, TreeWidget)

Java fields ARE the data store.  A private `sync()` method pulls initial state from
JVM when the widget was constructed from mtron, but once Java fields are populated
(by Java API or prior sync), JVM is never consulted again.  Mutators use direct
field assignment or builders (`addRow()`, `addMetadata()`).

**Shared primitives** (both models):

```java
// Read latest state from persistent store (if vid is set) or local JVM merge.
jvmRead() → Router.global().read(vid) → freshObj.jvm()

// Write a single field with >>=-style merge: read fresh, merge, write back.
jvmWrite(key, value)

// Static typed extractors — use with Map<Obj,Obj> from jvmRead():
jvmStr(jvm, key)     → String
jvmBool(jvm, key)    → boolean  (defaults true if absent)
jvmInt(jvm, key, fb) → int      (with fallback)
jvmBody(jvm, key)    → List<String>  (splits \\n and \n)
```

**Model A — JVM-as-source-of-truth** (AccordionWidget; preferred for new widgets):

```java
// Constructor — reads style from JVM so run() sees it
public MyWidget(Map<Obj, Obj> jvm, fURI tid, fURI vid) {
    super(new HashMap<>(jvm), tid, vid);
    readStyle(this.jvm());
}

// format() — one jvmRead(), all state extracted fresh
@Override public String format() {
    Map<Obj, Obj> jvm = jvmRead();
    String title = jvmStr(jvm, keyTitle);
    // ... render ...
}

// Mutators — write through to persistent store
public void setTitle(String t) { jvmWrite(kTitle, str(t)); }
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

**⚠️ History:** Before 2026-07-26, `TableWidget.sync()` unconditionally `clear()`ed
Java fields and repopulated from the JVM snapshot.  This destroyed Java-constructed
tables (ProfileTool, ExplainTool, TraceTool) because the JVM serialization round-trip
through `MObjFactory.toObj()` changes container types (`Objs` vs `Lst`, `ListN` vs
`ArrayList`).  The `javaPopulated` flag (shown above) fixes this.  It's safer than
per-field `isEmpty()` guards because a Java-constructed table may legitimately have
an empty field (e.g. headers-only table with no rows) — without the flag, sync would
pull stale rows from a prior mtron construction.

**Also: avoid `Stream.toList()` in sync().**  `Stream.toList()` (Java 16+) returns
immutable `ImmutableCollections$ListN`.  Use `Collectors.toCollection(ArrayList::new)`
instead — it keeps rows on the mutable-`ArrayList` path the rest of the codebase expects.

**When to use the legacy sync pattern vs. direct `jvmRead()`:**
- **New widgets** (AccordionWidget): read from `jvmRead()` directly in `format()`.  Java fields are metadata only.  Mutators use `jvmWrite()`.
- **Legacy widgets** (TableWidget, PanelWidget, TreeWidget): Java fields are the storage.  `sync()` populates them from JVM.  Mutators use direct field assignment or `addRow()`/`addMetadata()` builders.  The sync guard protects these from being overwritten.

## 3. Style system (`Stylable.Style`)

Style is a JVM-backed rec.  Fields:

| Field | Type            | Description |
|---|-----------------|---|
| `border` | uri             | simple, continuous, rounded, none, thick, hash, asterisk, period |
| `background` | str             | Graphitty color macro e.g. `{{[R]}}` |
| `foreground` | str             | Graphitty color macro e.g. `{{g}}` |
| `divider` | str             | Column/row divider char |
| `headerDivider` | str             | Header divider char |
| `pointer` | str             | Selection pointer e.g. `{{r}}>` |
| `anchor` | uri (coproduct) | top_left, top_middle, top_right, bottom_left, bottom_middle, bottom_right |
| `width` | int             | Display width in columns; 0 = natural |
| `top` | int             | Row offset from anchor edge (CSS top) |
| `left` | int             | Column offset from anchor edge (CSS left) |
| `leftMargin` | int             | Left margin |
| `rightMargin` | int             | Right margin |
| `topMargin` | int             | Top margin |
| `bottomMargin` | int             | Bottom margin |
| `height` | int             | Display height cap in rows; 0 = unbounded. Content exceeding this cap keeps header/chrome lines and discards top body lines (scroll-up behavior) |

Float-related:
```java
style.floatAt(anchor, width, top, left)  // configure floating
style.hasFloat()                          // true if anchor is set
style.anchor()                            // returns Anchor enum
style.width()                             // display width override
style.top() / style.left()                // offsets
style.unfloat()                           // clear floating config
```

Text utility:
```java
Style.wrapLines(List<String> lines, int maxWidth) → List<String>
// Splits lines at word boundaries to fit maxWidth visual chars.
// Any widget with text content can call this.
```

## 4. FloatingSurface + Anchor

```java
// Terminal-absolute rendering surface.  Draw AFTER pane content.
FloatingSurface surface = new FloatingSurface(terminal);

// Pin a widget:
surface.add(widget, row, col);                       // absolute
surface.add(widget, anchor, width);                  // anchored
surface.add(widget, anchor, width, top, left);       // anchored + offsets

// Anchor enum: TOP_LEFT, TOP_MIDDLE, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT
// Anchor.parse("top_middle") → TOP_MIDDLE  (also "tm", "tl", "tr", "bl", "bm", "br")

// Render cycle (automatic):
surface.render();  // \033[s → draw all → \033[u (preserves cursor)
surface.remove(widget);  // unpin + clear area
surface.clear();         // remove all
```

**Console integration** is automatic:
```java
Console.LOCAL_INSTANCE.getFloatingSurface()  // shared instance
// Rendered at every prepareForInput() + renderPanes()
// Widget.run() also calls render() for immediate display
```

## 5. Instruction registration (`uiInstSet.java`)

Widgets are mtron-constructable via `uiInstSet`.

### Type registration

```java
// Each widget type needs:
public static final fURI UI_MYWIDGET_TID = UI_ISA_TID.extend("mywidget");
public static Type UI_MYWIDGET_TYPE;

// In setup():
UI_MYWIDGET_TYPE = Type.Builder.build()
    .tid(UI_WIDGET_TID)               // parent type
    .vid(UI_MYWIDGET_TID)             // this type
    .isaPredicate(rec(                // field declarations for mtron introspection
        uri("title").maybe(), STR_TYPE,
        uri("body").maybe(), STR_TYPE
    ))
    .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_MYWIDGET_TID),
        lst(T(REC_TID)),
        (lhs, inst) -> new MyWidget(inst.arg(0).as().jvm(), UI_MYWIDGET_TID, inst.arg(0).vid())))
    .create();
```

### Instruction registration

```java
// display — standard for all widgets
docWrap(instC(UI_INST_TID.extend("display")
        .dom(UI_WIDGET_TID).rng(NOOBJ_TID),
        lst(),
        (lhs, inst) -> { ((Widget<?>) lhs).run(); ((Widget<?>) lhs).close(); return noobj(); }),
    "display the widget on the terminal");

// Custom instructions (AccordionWidget example):
// Register in the widget's format() method with a one-time guard:
if (!jvm.containsKey(uri("toggle"))) {
    this.at(uri("toggle"), instLambda((l, i) -> {
        this.toggle();
        Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
        return noobj();
    }), MUTABLE);
}
```

### Coproduct types

```java
// For closed-set URI values (like Anchor) — derived from the enum so they stay in sync:
UI_ANCHOR_TYPE = Type.Builder.build()
    .tid(URI_TID)
    .vid(UI_ANCHOR_TID)
    .isaPredicate(inside_(lst(Arrays.stream(FloatingSurface.Anchor.values())
        .map(a -> uri(a.name().toLowerCase()))
        .toArray(Obj[]::new))))
    .create();
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

5. **For interactive widgets** (keyboard input): extend `AbstractWidget`, override `run()` with a modal loop using `BindingReader`

6. **Choose the right state model**:

   | If your widget… | Use |
   |---|---|
   | Has simple key/value fields, built from mtron or Java | **Model A** (JVM-as-source-of-truth).  Mutators call `jvmWrite()`, `format()` calls `jvmRead()`.  See AccordionWidget. |
   | Has list/table data populated via Java builders (`addRow()`, etc.) | **Model B** (Java-fields-as-storage) with the `javaPopulated` tracking flag.  See TableWidget. |
   | Is a pure display widget with no mutable state | Either — Model A is simpler. |

## 7. Key patterns

### Read style from JVM before run()
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

### One jvmRead() per render
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
if (this.style.border() == Border.none)
    this.style.border(Border.continuous);  // Unicode box-drawing characters
```

## 8. Tool package (`isa.mach.type.ui.tool`)

Tools are higher-level compositions of widgets for specific REPL interactions.
They extend `AbstractWidget` and override `run()` with a modal input loop.  Unlike
display-only widgets (which use `Widget.run()` default → float or inline `format()`),
tools own their entire render cycle via `beginRedraw()` → `WidgetCanvas`.

### Lifecycle

```java
// In Console.java — triggered by Tab, Enter on code, colon commands, etc.
ExplainTool explain = new ExplainTool(code.as());
Utilities.runCursorLessWidget(explain, true);
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
- **Absolute** (pane bounds set): ANSI cursor-positioning inside the pane's content area. Lines exceeding `contentMaxLines` are silently dropped.
- **Relative** (no pane bounds): cursor-up to previous height → clear line → print → `\r\n`.  `previousHeight` is used to clear leftover lines from taller prior renders.

### Tool → Widget dependencies

| Tool | Widgets used |
|---|---|
| `ExplainTool` | `ProfileTool` → `TableWidget` (instruction table) |
| `TraceTool` | `TableWidget` (cause chain + stack frames) |
| `InstSelectorTool` | `SelectorWidget` → `TableWidget` (instruction pairs) |
| `fURISelectorTool` | `SelectorWidget` → `TableWidget` (URI pairs) |
| `TreeSelectTool` | `TreeWidget` (navigable tree) + `PanelWidget` (detail panel) |

**Important:** These tools populate their `TableWidget`s via Java API (`addRow()`,
`addMetadata()`).  They rely on the `sync()` guard pattern (section 2) to prevent
data corruption — without it, `TableWidget.format()` → `sync()` would clear
Java-populated rows and replace them with JVM-serialized representations that
don't preserve the exact column structure.

### Selector vs SelectorWidget

Two selection widgets exist with different rendering approaches:

- **`Selector`** (older): Uses JLine's `Display.updateAnsi()` for rendering.  Navigates an attached widget's `rowString()` output.
- **`SelectorWidget`** (newer): Uses `beginRedraw()` → `WidgetCanvas` for rendering.  Manages its own `TableWidget` with item pairs.  Preferred for new tool development.

### applyStyle() vs apply() — real-world footgun

```java
// WRONG — .apply() calls Obj.apply() (identity function — returns the Style, not the widget):
widget.style().border(...).apply();
// ^^ compiles, runs without error, but does NOT wire the style to the widget.
//    ProfileTool.java line 63 still uses this pattern (as of 2026-07-26).

// RIGHT — .applyStyle() calls stylable.style(this):
widget.style().border(...).applyStyle();
```

The default `Obj.apply()` (no-arg) returns `this` — the `Style` object.  The return
value is discarded, so the chain silently no-ops.  The compiler can't catch this
because `Style extends MRec extends MObj implements Obj`, and `Obj` has
`default Obj apply() { return this.apply(noobj()); }`.
