---
name: http-page-fetching
description: >
  Fetching HTTP pages in mtron via httpSpace, HTML parse tree structure, and tree traversal.
  TRIGGER: When working with http:// dereferences, HTML parsing, web scraping, page fetching,
  HTML tree walking, extracting links from HTML.
---

# HTTP Page Fetching in mtron

## Architecture

`httpSpace` uses Jsoup to fetch URLs and returns **typed strings** by default (e.g., `html::"..."`).
The `html::T` type refines `str::T` — the MIME type is a **predicate** on the string content, not a
structural transformation.

```
http:// url  →  httpSpace.directReader()  →  Jsoup fetch  →  html::"<html>...</html>"  (typed str)
                                                                     │
                                                         .as(rec::T) or ?mimeq=application/x-mtron
                                                                     │
                                                                     ▼
                                                              rec::T  (DOM tree)
```

The space pattern is `<http://#>`, so any `http://` URI routes through the httpSpace.  The space
should have `mimeq` mounted so `?mimeq=` query parameters work:

```mtron
httpspace::[
  host    => <http://localhost:8777>,
  pattern => <http://#>,
  q       => [mimeq::[=>]],          # enables ?mimeq= query processor
  route   => [/ => <local:web>]]
```

## Basic Dereference — Typed String (default)

Default reads return a typed `html::"..."` string with `tid = /m/web/mime/html`:

```mtron
*<http://example.com>
==>html::"<!DOCTYPE html><html lang=\"en\">...</html>"

*<http://example.com>.test(html::T)  
==>true
```

The string is predicate-validated: `html::T`'s predicate checks that the content is valid HTML.

## Structural Parse — rec::T DOM Tree

To get the rich `rec::T` DOM representation, use `?mimeq=application/x-mtron` or `.as(rec::T)`:

```mtron
# via query parameter on the URL
*<http://example.com?mimeq=application/x-mtron>
==>[html => [head => [...], body => [...]]]

# via .as(rec::T) on a typed string
*<http://example.com>.as(rec::T)
==>[html => [head => [...], body => [...]]]
```

The `?mimeq=application/x-mtron` query is handled by `QCollection.mimeQ()` postRead processor,
which (1) probes the content type from the response headers or URI extension, (2) tags the string
with the correct TID (triggering predicate validation), and (3) if `application/x-mtron` is requested,
runs the content-type-specific serializer (`ObjHTMLSerializer` for HTML) to produce the `rec::T` DOM tree.

## HTML Rec Structure

When converted to `rec::T`, the parsed HTML is a nested rec:

```
[html => [
  head => [
    title => '...',
    out => [
      [tag => meta, name => '...', content => '...'],
      [tag => style, data => '...']
    ]
  ],
  body => [
    out => [
      [tag => div, out => [
        [tag => h1, text => '...'],
        [tag => p, text => '...'],
        [tag => p, out => [
          [tag => a, href => <url>, text => '...']
        ]]
      ]]
    ]
  ],
  lang => 'en'
]]
```

**Key fields per element:**

- `tag` — HTML tag name (div, p, a, h1, meta, etc.)
- `text` — inner text content (for leaf elements)
- `data` — for style/script elements
- `out` — array of child elements
- `href`, `src`, `class`, `id`, etc. — HTML attributes

## Tree Walking (requires rec::T)

Tree walking into the DOM tree requires the structural `rec::T` form. Use `.as(rec::T)` first,
then walk the tree:

```mtron
# Cast to rec first, then walk
*<http://example.com>.as(rec::T)/html/body/out           # body's children list
*<http://example.com>.as(rec::T)/html/body/out/+/out      # first child's children (h1, p, p)
*<http://example.com>.as(rec::T)/html/body/out/+/out/+/out  # grandchild (a tag)
```

### With `+` wildcards

The `+` wildcard enters each list element, producing a stream/set:

```mtron
*page.as(rec::T)/html/body/out/+/out        # all children of all body elements
*page.as(rec::T)/html/body/out/+/out/+/out  # all grandchildren
```

### Filtering with `where()`

```mtron
# Filter for elements with tag=a, then extract href
*page.as(rec::T)/html/body/out/+/out/+/out/+>.where([tag=>a])>>href
```

## Converting Back — rec::T → html::T

Serialize a `rec::T` DOM tree back to an HTML string with `.as(html::T)`:

```mtron
# Parse to rec, mutate, serialize back to html string
*<http://example.com>.as(rec::T)
  .at(html/body/out -> [/* modified children */]).as(html::T)
```

`ObjHTMLSerializer.write()` handles both `str::T` (pass-through via `Jsoup.parse()`) and `rec::T` (DOM rendering).

## Other MIME Types

The same pattern applies to JSON, XML, Markdown, etc.:

```mtron
*<http://example.com/data.json>                    → json::"{...}"  (typed str)
*<http://example.com/data.json?mimeq=application/x-mtron>  → rec::T
*<http://example.com/data.json>.as(rec::T)         → rec::T
```

## MIME Type → TID Mapping

`MIME.MIMEType.toTid()` provides the reverse mapping used by the `mimeq` query processor:

| MIME Type | TID |
|---|---|
| `text/html` | `/m/web/mime/html` (`HTML_TID`) |
| `text/markdown` | `/m/web/mime/markdown` (`MARKDOWN_TID`) |
| `application/json` | `/m/web/mime/json` (`JSON_TID`) |
| `application/xml` | `/m/web/mime/xml` (`XML_TID`) |
| `text/css` | `/m/web/mime/css` (`CSS_TID`) |
| `text/x-java` | `/m/web/mime/java` (`JAVA_TID`) |
| `application/x-mtron` | `null` (structural parse gate) |

## Important Notes

- Only `http://` currently works (not `https://`). Strip the 's' from https URLs.
- Tree walking requires `.as(rec::T)` first — typed strings don't support field access.
- httpSpace host is configured in boot: `host => <http://localhost:8777>`, `pattern => <http://#>`
- `?mimeq=application/x-mtron` on httpSpace triggers structural parse via the content-type serializer.
- `?mimeq=text/html` just tags the string with `html::T` without structural conversion.
