---
name: ui instruction set
description: >
  The `/m/mach/ui` instruction set: a widget is a rec whose state is its own map, `as?str<=widget(str::T)`
  renders one inline anywhere a str fits, `style::T` says how it looks and where it hangs from, and the
  console floats it on a terminal where the pointer works it — click to focus, drag the chevron to move,
  drag the corner marker to reshape. TRIGGER: when rendering or floating a widget, styling or anchoring
  one, reading or writing a widget's state, moving or resizing a widget with the mouse, or asking which
  keys a widget type declares.
---

# ui instruction set (`/m/mach/ui`)

A widget is a **rec**. Its title, its body, its style, whether it is folded, where it sits — every one of
those is a key in its own map, and when the widget is anchored (`@uri`) that map is the space's. There is
no widget object holding state beside the rec: a `.display()` update re-hydrates a fresh instance from the
same rec, so the rec is the only thing that survives it.

That is why the instruction set is small — three insts and a family of types. **Rendering is a cast**:
`as?str<=widget(str::T)` turns a widget into the str you would see, so a widget can be shown inside a doc,
a chat message or a test, anywhere a str fits. **Floating** one on a live terminal, and working it with
the pointer, belongs to the console (see *anchoring, and the pointer*).

The instruction set's own subgraph — at once the map of this doc and a widget:

```mtron
mtron> tree_widget::[root=>/m/mach/ui, max=>2].as?str<=widget(str::T)
==>"""
   ui
   ├─ anchor
   ├─ console
   ├─ style
   └─ widget
       ├─ accordion_widget
       ├─ grid_widget
       ├─ label_line_widget
       ├─ menu_bar_widget
       ├─ modal_widget
       ├─ panel_widget
       ├─ progress_table_widget
       ├─ selector_widget
       ├─ stack_bar_widget
       ├─ swipe_panel_widget
       ├─ table_widget
       ├─ tree_select_widget
       └─ tree_widget
   """
```
## the space these examples use

The docs runner is **headless**: no terminal, no console. Everything below that *renders* is `mtron_pre`
— evaluated on every docs build, so it cannot drift from the code — while a block that would *float* a
widget on a terminal is shown as a plain `mtron` block, because there is nothing to float it on here. The
widget recs live at `/usr/uidoc/#`; the last block in this doc leaves the graph as it found it.

## types

| type         | vid                  | tid      | meaning                                                           |
|--------------|----------------------|----------|-------------------------------------------------------------------|
| `widget::T`  | `/m/mach/ui/widget`  | `rec::T` | the widget base — every widget is a rec, and isa this             |
| `style::T`   | `/m/mach/ui/style`   | `rec::T` | how a widget looks and where it hangs from (see *style*)          |
| `anchor::T`  | `/m/mach/ui/anchor`  | `uri::T` | `top_left`, `top_middle`, `top_right`, `middle`, `bottom_*`       |
| `console::T` | `/m/mach/ui/console` | `rec::T` | the terminal: panes, the prompt, and the surface widgets float on |

A widget type declares **the keys its widget reads** and carries the ctor that builds it, which makes the
type readable as the contract:

```mtron
mtron> */m/mach/ui/widget/panel_widget       [-- title and body, both optional; ctor?panel_widget<=#{?} --]
==>widget::T[?[{?}title=>str::T,{?}body=>str{*}::T]][ctor?rng=panel_widget&dom=#{?}(<#{*}>::T){<j>}]@panel_widget
mtron> */m/mach/ui/widget/tree_widget        [-- root and max required; code/flatten/xref/expand optional --]
==>widget::T[?[root=>uri::T,max=>int::T,{?}code=><#>::T,{?}flatten=>bool::T,{?}xref=>rec::T,{?}expand=>uri{*}::T]][ctor?rng=tree_widget&dom=#{?}(<#{*}>::T){<j>}]@tree_widget
```
`*/m/mach/ui/widget?docq` is the catalog — one entry per widget type, each with its args, its dom/rng and
its own example. It is the fastest way to answer "what can I put in this thing", and it is generated from
the same declarations the type checker uses:

```mtron
mtron> */m/mach/ui/widget?docq
==>docs::[obj=>rec::T[?[{?}style=>rec::T[?[{?}border=>str::T,{?}background=>str::T,{?}foreground=>str::T,{?}divider=>str::T,{?}headerDivider=>str::T,{?}pointer=>str::T,{?}leftMargin=>int::T,{?}rightMargin=>int::T,{?}topMargin=>int::T,{?}bottomMargin=>int::T,{?}anchor=>uri::T[?union(top_left,top_middle,top_right,middle,bottom_left,bottom_middle,bottom_right)]@anchor,{?}width=>int::T,{?}height=>int::T,{?}top=>int::T,{?}left=>int::T,{?}zIndex=>int::T,{?}scroll=><#>::T,{?}scrollX=>int::T,{?}scrollY=>int::T]][ctor?rng=style&dom=#{?}(<#{*}>::T){<j>}]@style]]@widget,args=>[{?}style=>'the style specification for the widget'],desc=>'[structural] the base widget type',accordion_widget=>docs::[obj=>widget::T[?[{?}title=>str::T,{?}body=>str{*}::T]][ctor?rng=accordion_widget&dom=#{?}(<#{*}>::T){<j>}]@accordion_widget,dom=>'maybe an obj',rng=>'an accordion obj',args=>[{?}title=>'the title of the accordion',{?}body=>'the body content of the accordion'],desc=>'[structural] an expandable/collapsible accordion widget'],progress_table_widget=>docs::[obj=>widget::T[?[{?}header=>lst::T,{?}row=>lst::T]][ctor?rng=progress_table_widget&dom=#{?}(<#{*}>::T){<j>}]@progress_table_widget,desc=>'[structural] a table of progress bars',example=>["progress_table::[row=>[[text=>'layer1',percent=>58.0],[text=>'layer2',percent=>23.0]]].run()"]],table_widget=>docs::[obj=>widget::T[?[{?}header=>lst::T,{?}row=>lst{*}::T,{?}metadata=>lst{*}::T]][ctor?rng=table_widget&dom=#{?}(<#{*}>::T){<j>}]@table_widget,dom=>'maybe an obj',rng=>'a table widget',args=>[{?}metadata=>'a lst of rows of data behind the display',{?}header=>'a lst of obj table headers',{?}row=>'a lst of poly table rows'],desc=>'[structural] a tabular data widget'],tree_widget=>docs::[obj=>widget::T[?[root=>uri::T,max=>int::T,{?}code=><#>::T,{?}flatten=>bool::T,{?}xref=>rec::T,{?}expand=>uri{*}::T]][ctor?rng=tree_widget&dom=#{?}(<#{*}>::T){<j>}]@tree_widget,dom=>'maybe an obj',rng=>'a tree widget',args=>[{?}code=>'transform obj prior to insertion into tree (default _)',{?}expand=>'branch uris whose children are read regardless of max',{?}xref=>'xref=>[max=>N, code=><call>] cross-reference decoration',max=>'the max depth to traverse',{?}flatten=>'fold single-folder chains into one path row (default false)',root=>'the root uri to traverse from'],desc=>'[structural] the root uri space is traversed to specified depth generating a tree data structure'],selector_widget=>docs::[obj=>widget::T[?[=>]][ctor?rng=selector_widget&dom=#{?}(<#{*}>::T){<j>}]@selector_widget,dom=>'maybe an obj',rng=>'a selector widget',desc=>'[structural] an interactive item selector widget'],panel_widget=>docs::[obj=>widget{*}::T[?[{?}title=>str::T,{?}body=>str{*}::T]][ctor?rng=panel_widget&dom=#{?}(<#{*}>::T){<j>}]@panel_widget,dom=>'rec',rng=>'panel',args=>[title=>'the title of the panel',body=>'the body content of the panel'],desc=>'[structural] a simple bordered UI panel widget'],grid_widget=>docs::[obj=>widget::T[?[{?}grid=>lst::T]][ctor?rng=grid_widget&dom=#{?}(<#{*}>::T){<j>}]@grid_widget,dom=>'maybe an obj',rng=>'a grid of widgets',args=>[grid=>'a lst of rows, each row a lst of cell widgets'],desc=>'[structural] a grid of rows x cols of embedded, runnable widgets'],label_line_widget=>docs::[obj=>widget::T[?[{?}body=>str::T,{?}key=>uri::T,{?}on_key=><#>::T]][ctor?rng=label_line_widget&dom=#{?}(<#{*}>::T){<j>}]@label_line_widget,dom=>'maybe an obj',rng=>'a label line widget',args=>[body=>'the text body displayed on the label'],desc=>'[structural] a single-line text label widget'],menu_bar_widget=>docs::[obj=>widget::T[?[{?}height=>int::T,{?}lines=>lst::T]][ctor?rng=menu_bar_widget&dom=#{?}(<#{*}>::T){<j>}]@menu_bar_widget,dom=>'maybe an obj',rng=>'a menu bar widget',args=>[lines=>'a lst of line widgets laid out across the bar',height=>'the interior height of the menu bar in rows'],desc=>'[structural] a menu bar pinned to the top of the terminal'],tree_select_widget=>docs::[obj=>widget::T[?[root=>uri::T,max=>int::T,{?}on_select=><#>::T,{?}label=><#>::T]][ctor?rng=tree_select_widget&dom=#{?}(<#{*}>::T){<j>}]@tree_select_widget,dom=>'maybe an obj',rng=>'a tree select tool',args=>[{?}label=>"instruction called on each tree node's obj to produce a suffix label (default: identity)",root=>'the root uri to traverse from',max=>'the max depth to traverse',{?}on_select=>"instruction called on Enter with the selected node's rel::T (default: identity)"],desc=>'[structural] an interactive tree browser: arrow keys navigate, Enter fires on_select on selected node, right/left expand/contract, label suffixes each node'],swipe_panel_widget=>docs::[obj=>widget::T[?[{?}obj=>lst::T]][ctor?rng=swipe_panel_widget&dom=#{?}(<#{*}>::T){<j>}]@swipe_panel_widget,dom=>'lst of objs',rng=>'a swipe panel widget tool',args=>[obj=>'the list of objs to swipe through'],desc=>'[structural] a left-right swipe panel: arrow keys navigate, pgup/pgdn jump ±5, ctrl-d quits; displays each obj in a PanelWidget with docq+Highlighter formatting'],modal_widget=>docs::[obj=>widget::T[?[{?}title=>str::T,{?}body=>str{*}::T]][ctor?rng=modal_widget&dom=#{?}(<#{*}>::T){<j>}]@modal_widget,dom=>'rec',rng=>'modal',args=>[title=>'the title of the modal',body=>'the body content of the modal'],desc=>'[structural] a modal popup panel: space/enter/ctrl-d dismisses'],stack_bar_widget=>docs::[obj=>widget::T[?[{?}data=>rec::T,{?}context=>int::T,{?}total=>int::T]][ctor?rng=stack_bar_widget&dom=#{?}(<#{*}>::T){<j>}]@stack_bar_widget,dom=>'maybe an obj',rng=>'a stack bar widget',args=>[{?}data=>"the sections: [key => int], each labelled by its key and drawn smallest first, largest last; the reserved key <> (the empty uri) is the what-is-left section — drawn last, wearing the unused section's style (dark by default)",{?}context=>'the denominator: the bar spans it, the rest is the unused tail, and total/context is the percent; absent = a composition (the data fills the bar, no percent, no tail)',{?}total=>'the numerator of the percent (default: the sum of the data)'],desc=>'[structural] a general single-line stacked bar; only the data is specified — the rec is open world: pre=/post= framing content (a str, or a dereference such as post=>!*/usr/dr/context_window), style.width the width of the bar, and style.section the per-section styles: [name => style::T] (body => the label painted, else the key name; foreground/background the fragments around it; width its own width) — the reserved key unused styles the unused / what-is-left section']]
```
| widget type                | keys it reads                                                 |
|----------------------------|---------------------------------------------------------------|
| `accordion_widget::T`      | `title`, `body`                                               |
| `panel_widget::T`          | `title`, `body`                                               |
| `table_widget::T`          | `header`, `row`, `metadata` (rows of data behind the display) |
| `tree_widget::T`           | `root`, `max`, `code`, `flatten`, `xref`, `expand`            |
| `progress_table_widget::T` | `header`, `row`                                               |
| `label_line_widget::T`     | `body`, `key`, `on_key`                                       |
| `menu_bar_widget::T`       | `height`, `lines`                                             |
| `modal_widget::T`          | `title`, `body`                                               |
| `swipe_panel_widget::T`    | `obj`                                                         |
| `tree_select_widget::T`    | `root`, `max`, `on_select`, `label`                           |
| `selector_widget::T`       | *(none of its own — it works the rec it is handed)*           |

A widget also answers to more than the keys its type declares: a store-backed accordion grows `expand`,
`toggle`, `append` and `collapse` — **insts latched into its rec** when it is constructed. That is its API,
and it is readable as data:

```mtron
mtron> accordion_widget::[title=>'notes',
                          body=>"l01\nl02\nl03"]@/usr/uidoc/notes
==>accordion_widget::[title=>'notes',body=>'l01
l02
l03',style=>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤',foreground=>'']]@/usr/uidoc/notes
mtron> @/usr/uidoc/notes >>= [body=>"l01\nl02\nl03\nl04"]              [-- the update carries the insts with it --]
==>accordion_widget::[style=>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤',foreground=>''],body=>'l01
l02
l03
l04',title=>'notes']
mtron> */usr/uidoc/notes                                               [-- the widget, as the rec it is --]
==>accordion_widget::[style=>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤',foreground=>''],body=>'l01
l02
l03
l04',title=>'notes']
```
## a widget is a rec — state lives in the map

Construct it, anchor it, read it back: the map *is* the widget, and the space is the map when it has a vid.
Nothing about it is Java-side, which is why an mtron write is a first-class way to change a widget the
console is showing:

```mtron
mtron> panel_widget::[title=>'note',body=>"alpha\nbeta"]@/usr/uidoc/panel
==>panel_widget::[title=>'note',body=>'alpha
beta',style=>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤']]@/usr/uidoc/panel
mtron> */usr/uidoc/panel/title
==>'note'
mtron> */usr/uidoc/panel/body
==>'alpha\nbeta'
```
The style is a key like any other, so a widget's look is set by writing one:

```mtron
mtron> @/usr/uidoc/panel >>= [style=>[anchor=>top_left,width=>20,foreground=>'']]
==>panel_widget::[title=>'note',body=>'alpha
beta',style=>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤']]
mtron> */usr/uidoc/panel/style
==>style::[border=>'┌;┐;└;┘;│;│;─;─;┬;┴;├;┤']
```
## rendering a widget inline

A widget renders wherever a `str::T` fits, by casting it through the widget's own `as?str<=widget` inst:

```mtron
mtron> panel_widget::[title=>'note',body=>"alpha\nbeta\ngamma"].as?str<=widget(str::T)
==>"""
   ┌note─┐
   │alpha│
   │beta │
   │gamma│
   └─────┘
   
   """
```
The explicit dom (`as?str<=widget(str::T)`) is what selects the widget's own rendering, and a widget read
back out of the space renders the same way — from the *anchor*, not the clone:

```mtron
mtron> @/usr/uidoc/panel.as?str<=widget(str::T)
==>"""
   ┌note─┐
   │alpha│
   │beta │
   └─────┘
   
   """
```
### the gallery

```mtron
mtron> panel_widget::[title=>'note',body=>"alpha\nbeta\ngamma"].as?str<=widget(str::T)
==>"""
   ┌note─┐
   │alpha│
   │beta │
   │gamma│
   └─────┘
   
   """
mtron> accordion_widget::[title=>'notes',body=>"l01\nl02\nl03"].as?str<=widget(str::T)
==>"""
   ┌ notes [-] ┐
   │ l01       │
   │ l02       │
   │ l03       │
   └───────────┘
   """
```
```mtron
mtron> table_widget::[header=>['name','qty'],row=>[['alpha','3'],['beta','12']]].as?str<=widget(str::T)
==>"""
               
    name  qty  
    alpha 3    
    beta  12   
               
   """
```
```mtron
mtron> tree_widget::[root=>/m/mach/ui, max=>1].as?str<=widget(str::T)
==>"""
   ui
   ├─ anchor
   ├─ console
   ├─ style
   └─ widget
   """
```
```mtron
mtron> menu_bar_widget::[height=>1,lines=>[label_line_widget::[body=>'File'],label_line_widget::[body=>'Edit']]].as?str<=widget(str::T)
==>"""
   File  Edit
   """
mtron> label_line_widget::[body=>'a label line'].as?str<=widget(str::T)
==>"""
   a label line
   """
```
## style

`style::T` is a rec of presentation keys. The ones that change what you see:

| key                            | type     | meaning                                                                                              |
|--------------------------------|----------|------------------------------------------------------------------------------------------------------|
| `border`                       | `uri::T` | `border::continuous`, `border::rounded`, `border::none`, …                                           |
| `foreground`                   | `str::T` | a graphitty color, e.g. `'{{c}}'`; `background` likewise, plus `divider`, `headerDivider`, `pointer` |
| `width`                        | `int::T` | the column width: a panel wraps its body to it                                                       |
| `height`                       | `int::T` | the row **cap**: the surface clips the viewport to it (see the caveat below)                         |
| `anchor`                       | `uri::T` | which corner/edge the widget hangs from                                                              |
| `top`, `left`                  | `int::T` | offsets **from that anchor** — what a drag writes                                                    |
| `zIndex`                       | `int::T` | paint order when floats overlap                                                                      |
| `scroll`, `scrollX`, `scrollY` |          | the viewport a widget over its own content starts at                                                 |

Width is a rendering instruction — it is the widget's own format that wraps:

```mtron
mtron> panel_widget::[title=>'note',body=>'alpha beta gamma delta epsilon zeta',style=>[width=>24]].as?str<=widget(str::T)
==>"""
   ┌note──────────────────┐
   │alpha beta gamma delta│
   │epsilon zeta          │
   └──────────────────────┘
   
   """
```
Height is a **viewport** instruction: `format()` draws the whole body and the surface shows a window of
it, so a height cap in a plain render changes nothing (the same render with the cap draws every line):

```mtron
mtron> panel_widget::[title=>'note',body=>"l01\nl02\nl03\nl04\nl05",style=>[height=>3,width=>18]].as?str<=widget(str::T)
==>"""
   ┌note┐
   │l01 │
   │l02 │
   │l03 │
   │l04 │
   │l05 │
   └────┘
   ...
```
## anchoring, and the pointer

An anchored widget (`style => [anchor=>…]`) is pinned to a corner and then offset from it. Which cell that
lands on is arithmetic the surface does on every render, from the terminal's size and the widget's own
height and width:

| anchor family | row                       | column                                                                       |
|---------------|---------------------------|------------------------------------------------------------------------------|
| `top_*`       | `2 + top`                 | `1 + left` (left), centred `+ left` (middle), `termW - w + 1 + left` (right) |
| `middle`      | `(termH - h)/2 + 1 + top` | as above                                                                     |
| `bottom_*`    | `termH - h + 1 - top`     | as above                                                                     |

The sign in the `top` row is the whole reason a drag is a translation: a **bottom** anchor measures *up*
from the bottom edge, so growing a bottom-anchored widget pushes its top edge up while a top-anchored one
pushes its bottom edge down. `left` is `+ left` for every column anchor, which is why one horizontal
offset works for all of them.

The pointer works the two corners of a focused widget:

| gesture                                                           | effect                                                                                                                           |
|-------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| click a widget                                                    | focus it — and only the focused widget shows its handles                                                                         |
| press the **chevron** (`▶`, its top-left cell) and drag           | move it: the corner you grabbed *is* the widget's origin, so it lands where the pointer is                                       |
| press the **corner marker** (`◢`, its bottom-right cell) and drag | reshape it: a width/height delta from the press, floored (10×3, so a resize cannot demolish a widget) and capped at the terminal |
| release                                                           | **park it**: the position (or the size) is written into `style`, so it survives the next `.display()`                            |
| click empty terminal                                              | drop the focus and hand the mouse back to the terminal                                                                           |

While the pointer is working, the geometry lives on the surface's slot and **nothing** is written to the
rec; the release is what persists, and it is the only thing that needs to:

```mtron
panel_widget::[title=>'note',body=>'drag me'].display()      [-- floats, if the style anchors it --]
```

```mtron
@/usr/uidoc/panel >>= [style=>[top=>3,left=>8]]              [-- what a move writes on release --]
@/usr/uidoc/panel >>= [style=>[width=>33,height=>6]]         [-- what a resize writes --]
```

A move writes only `top`/`left` and a resize only `width`/`height` (plus the position it was resized from):
a height cap nobody asked for would clip content that arrives later.

## the three insts

| inst      | dom → rng                | what it does                                                                                                             |
|-----------|--------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `display` | `widget::T` → `noobj{0}` | runs the widget: a display-only render, the interactive modal loop for a tool, or a float if the style carries an anchor |
| `nano`    | `#{?}` → `#{?}`          | opens the obj in a nano-like editor and reads the edited value back                                                      |
| `less`    | `str::T` → `noobj{0}`    | pages a str (`less(20)` for 20 lines a page)                                                                             |

```mtron
panel_widget::[title=>'note',body=>"alpha\nbeta"].display()
'one\ntwo\nthree'.less(2)
```

## not available yet

* **Floating needs a terminal.** `display`/`nano`/`less` drive the console: `display()` on a headless VM
  dies inside it, and `less` blocks on stdin. That is why the blocks above that use them are plain `mtron`:

```mtron
panel_widget::[title=>'note',body=>'alpha'].display()
    [-- fail:: inst apply failure: NullPointerException: Console.getTerminal() is null --]
```

* **`*/uri` does not render — the anchor does.** `@/usr/uidoc/panel.as?str<=widget(str::T)` renders the
  widget the store holds; the clone form (`*/usr/uidoc/panel.as?str<=widget(str::T)`) produces nothing,
  because the dereferenced rec no longer satisfies the inst's dom. Anchor the read.
* **A height cap is not visible in a plain render** — the viewport belongs to the surface (see *style*).
* **A rec-built `progress_table` draws raw recs, not bars.** The bars come from
  `addProgressRow` at runtime; a table constructed from `row => [[text=>…,percent=>…]]` renders the rows as
  they were written.
* **`tree_widget` can fail on some roots.** `tree_widget::[root=>/sys, max=>1].as?str<=widget(str::T)`
  raises a `fail::` NPE from the tree walk (`Tuple$Pair.get1()` is null for one of `/sys`'s entries);
  `/m/mach/ui`, `/usr/...` and the project subgraphs the ide skill walks are fine.
* **A box drawn one cell wide and tall shows no corner marker** — it would sit on top of the chevron.

## see also

* [ui (Java)](../metatron/references/ui-instset-java.md) — the same subsystem from the inside: `Widget`,
  `Stylable.Style`, `FloatingSurface`, `ScrollView`, the widget classes and the type registrations.
* [ide](../ide/SKILL.md) — the tree widget used as a project browser, inline, in situ.
* [tble instruction set](tble-instset-mtron.md) — the other space-as-a-type doc, and the style this one
  follows.
* `docs/design/widget-jrec-review.md` — why a widget's state is its rec and not a Java field, and what the
  migration removed.
* `bin/test/console-drag.steps`, `console-widget-scroll.steps`, `console-widget-mouse.steps` — the pty
  suites that drive the pointer and the viewport in a real console.
* `*/m/mach/ui?docq` — the instruction set's own inventory (3 insts and the type family).