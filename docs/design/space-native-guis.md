# Space-Native GUIs

**A GUI system where every widget is a Rec, every view is a query, and interaction is `at()` with a focus position.**

## The Core Insight

metatron already has everything needed for interactive graphical interfaces:

| Primitive | Role in GUI |
|-----------|-------------|
| `at()` | Navigate into any structure by key, index, or pattern |
| `!*` (auto_from) | Live, writable reference to another Obj in space |
| `!` (auto_eval) | Computed cell — evaluate instruction on read |
| `as(view::T, style)` | Render any Obj as a human-readable view |
| `>>=` | Write back through any reference chain |
| Style rec | Visual formatting parameters (border, color, compactness) |
| `/sys/key/+` | Keyboard event stream as a space |

A GUI is just **a Rec displayed through a Style, navigated with `at()`, with fields that `!*` reference source data and `!` auto-eval computed values.**

No widget library. No component hierarchy. No event system. Just data flowing through the existing primitives.

---

## The Rendering Pipeline

```
Raw Obj
   │
   ▼
as(view::T, Style)     ← MIME-like conversion, registered per type
   │
   ▼
ANSI string rows       ← Graphitty colors, borders, compact layouts
   │
   ▼
Space query result     ← */sys/ui/view/rows
```

`as(view::T)` is registered in the type system alongside `as(str::T)`, `as(html::T)`, `as(json::T)`, etc. Every type gets a default view (compact, max 5 fields, fallback to `as(str::T)`). Types opt in to richer views.

```mtron
# A big rec → compact view:
*/sys/machine/14.as(view::T, [style=>[pill=>true]])
  → '🧵 /sys/machine/14 [RUN] code:27 halted:0'

# A list → formatted table:
[[Name,Age],[Alice,30],[Bob,25]].as(view::T, [style=>[divider=>'|']])
  →  +-------+-----+
     | Name  | Age |
     +-------+-----+
     | Alice | 30  |
     | Bob   | 25  |
     +-------+-----+

# A space query result → scrollable viewport:
(*db:invoices/+.skip(0).take(10)).as(view::T, [style=>...])
```

---

## Style Rec Schema

A universal visual formatting contract. Every field is optional — each view type reads what it needs.

```mtron
[style => [
  border => [type => single|double|none|ascii,
             tl => '+', tr => '+', bl => '+', br => '+',
             h => '-', v => '|'],
  foreground => white|black|red|green|blue|cyan|magenta|yellow|none,
  background => white|black|red|green|blue|cyan|magenta|yellow|none,
  cursor => [foreground => _, background => _],
  header => [foreground => _, background => _],
  divider => '|',
  padding => 1,
  compact => false,
  maxFields => 5,
  maxRows => 10,
  title => none,
  width => none,
  height => none,
]]
```

---

## Selector — `at()` with a Viewport

The Selector is a Machine whose state rec combines a query, a position, key bindings, and a style.

```mtron
/selectors/invoices → [
  source => |(*db:invoices/+.skip(0).take(10)),
  view => 0,
  focus => 0,
  style => [border=>[type=>single], header=>[foreground=>white]],
  keys => [
    down => |>-.>>[focus=>+1],
    up   => |>-.>>[focus=>-1],
    pgdn => |>-.>>[view=>+10, focus=>0],
    pgup => |>-.>>[view=>-10, focus=>0],
    enter => |inst(source, view, focus){
        form(source.skip(view).plus(focus).take(1).vid())
      },
    esc => |stop(),
  ]
]
```

The Machine loop each tick:

1. **Evaluate** `source()` → pushdown-optimized query produces N rows
2. **Slice** `result.skip(view).take(viewportHeight)` → current window
3. **Render** each item via `as(view::T, style)` → ANSI row strings
4. **Read** `/sys/key/+` for the latest key event
5. **Match** key against `keys` map → execute the bound instruction
6. **Instruction** mutates state via `>>=`, loop repeats

The source expression is **barriered** (`|`). It doesn't execute until the Selector's Machine evaluates it. Changing the page is just rewriting the source query — the SQL/MQL rewrite system pushes LIMIT/OFFSET down to the backend automatically.

```mtron
# Turn the page:
/selectors/invoices >>= [source => |(*db:invoices/+.skip(10).take(10))]
```

---

## `!*` — The Live Binding Between View and Source

Every cell in a rendered view is a **reference** — not a copy. The Selector renders `!*db:invoices/4/name` as a cell. Reading it resolves through the Router to tbleSpace. Writing it via `>>=` flows through the Router to `UPDATE invoices SET name = ? WHERE id = 4`.

```mtron
# A viewport row:
/views/invoices/rows/0 → [
  id => !*db:invoices/1/id,
  name => !*db:invoices/1/name,
  total => !(*db:invoices/1/price * *db:invoices/1/qty)
]

# Read → resolves through space:
*/views/invoices/rows/0/name     → 'Alice'

# Write → writes through to source:
/views/invoices/rows/0/name >>= 'Bob'
  → Router writes db:invoices/1/name = 'Bob'
```

The `!*` convention means **no data is ever copied into the widget**. Every dereference goes through the Router to whatever space owns the data. This is what makes paging over a million-row SQL table viable — only the viewport rows are materialized.

---

## `!` Auto-Eval — Computed Cells (Excel Behavior)

Any field whose value starts with `!` is evaluated on read. This gives computed columns, conditional formatting, and action buttons — all from the data.

```mtron
/views/invoices/rows/0 → [
  name => !*db:invoices/1/name,
  price => !*db:invoices/1/price,
  qty => !*db:invoices/1/qty,
  total => !(*db:invoices/1/price * *db:invoices/1/qty),
  status => !(inst(price){
      ?>(*price, 1000) => 'HIGH',
      _ => 'low'
    }),
  action => !(/sys/actions/editInvoice(*db:invoices/1/id))
]
```

When rendered:

```
+-------+-------+-----+-------+--------+--------+
| Name  | Price | Qty | Total | Status | Action |
+-------+-------+-----+-------+--------+--------+
| Alice | 1200  | 2   | 2400  | HIGH   | [edit] |
| Bob   | 400   | 5   | 2000  | low    | [edit] |
+-------+-------+-----+-------+--------+--------+
```

Editing a cell:

```mtron
# Click/edit on Alice's price:
/views/invoices/rows/0/price >>= 1500

# Total auto-recalculates on next read:
*/views/invoices/rows/0/total     → 3000  (1500 × 2)
```

No spreadsheet engine. No formula parser. Just `!` auto-eval on read, which already exists in the language.

---

## Input as a Space Stream

Keyboard events arrive at `/sys/key/+` as a space stream. The Console (or a terminal bridge) writes them:

```mtron
# A key press produces a rec in space:
/sys/key/down     → [seq=>1423, key=>down, time=>...]
/sys/key/enter    → [seq=>1424, key=>enter]
/sys/key/65       → [seq=>1425, key=>char, value=>'A']

# The Selector reads the latest:
*/sys/key/+.take(1)    → current key
*/sys/key/+/?after=1423 → keys since sequence 1423
```

This decouples input from any specific terminal. The same widget reads keys whether the source is a local terminal, a WebSocket connection, or an MCP tool calling `keypress()`.

---

## Composing Views

Since every view is a Rec and every viewport is a query result, composition is natural:

```mtron
# Split layout: two views side by side
/views/layout → [
  type => split,
  direction => horizontal,
  left => !*/selectors/invoices,
  right => !*/selectors/products,
  ratio => 0.6,
]

# A dashboard: grid of view references
/views/dashboard → [
  type => grid,
  columns => 2,
  children => [
    !*/views/activeInvoices,
    !*/views/revenueChart,
    !*/views/recentOrders,
    !*/views/alertLog,
  ]
]
```

The split/grid renderer reads each child's `rows`, lays them out, and produces a composite ANSI frame. No widget Machine needed for static composition — just rec operations.

---

## Implementation Phases

### Phase 1: `as(view::T)` + Style (2 files)

1. Register `view::T` as a type marker
2. Default `as(view::T)` fallback → compact `as(str::T)` with max-depth clamping
3. Style rec → ANSI border/color utility (extracted from existing `Style<T>` / `Highlighter`)
4. `obj.format(style)` sugar ≡ `obj.as(view::T, style)`

### Phase 2: `/sys/key/+` input stream (1 file)

1. A thin VirtualThread that reads from the Console's JLine reader and writes key recs to `/sys/key/+`
2. Works alongside, not replacing, the existing Console input handling

### Phase 3: Selector Machine Type (2 files)

1. Register `selector::T` and `mUiInstSet` with a constructor
2. The Machine loop: evaluate source → render viewport → poll keys → dispatch
3. `keys` map binds key names to instructions (state mutations or actions)

### Phase 4: mtron-native views

- `lst.as(view::T, style)` → formatted table with borders
- `rec.as(view::T, style)` → compact key/value card
- `machine.as(view::T, style)` → status pill
- Composed layouts (split, grid) as pure rec operations

No Java needed after Phase 3. New view types are just `as(view::T)` instructions registered in the type system — exactly like MIME types already work for files.

---

## Summary

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│   Every GUI is just data:                               │
│                                                         │
│   ┌──────────┐                                          │
│   │  Style   │  ← visual formatting rec                 │
│   └────┬─────┘                                          │
│        │                                                │
│   ┌────▼─────┐   ┌──────────────────┐                   │
│   │  at()    │   │  !* auto_from    │  ← live refs      │
│   │  +       │   │  !  auto_eval    │  ← computed cells  │
│   │ viewport │   └──────────────────┘                   │
│   └────┬─────┘                                          │
│        │                                                │
│   ┌────▼─────┐                                          │
│   │ as(view  │  ← ANSI rendering (MIME pattern)         │
│   │ ::T)     │                                          │
│   └──────────┘                                          │
│                                                         │
│   Three primitives. Everything else is data in space.    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```
