---
name: http-page-fetching
description: |
  Fetching HTTP pages in mtron via httpSpace, HTML parse tree structure, and tree traversal.
  TRIGGER: When working with http:// dereferences, HTML parsing, web scraping, page fetching,
  HTML tree walking, extracting links from HTML.
---

# HTTP Page Fetching in mtron

## Architecture

`httpSpace` uses Jsoup to fetch URLs and returns **typed strings** by default (e.g., `html::"..."`). The `html::T` type
refines `str::T` — the MIME type is a **predicate** on the string content, not a structural transformation.

```
http:// url  →  httpSpace.directReader()  →  Jsoup fetch  →  html::"<html>...</html>"  (typed str)
                                                                     │
                                                         .as(rec::T) or ?mimeq=application/x-mtron
                                                                     │
                                                                     ▼
                                                              rec::T  (DOM tree)
```

The space pattern is `<http://#>`, so any `http://` URI routes through the httpSpace. The space should have `mimeq`
mounted so `?mimeq=` query parameters work:

```mtron
mtron> httpspace::[
         host    => <http://localhost:8777>,
         pattern => <http://#>,
         q       => [mimeq::[=>]],          [-- enables ?mimeq= query processor --]
         route   => [/ => <local:web>]]
```
## Basic Dereference — Typed String (default)

Default reads return a typed `html::"..."` string with `tid = /m/web/mime/html`:

```mtron
mtron> *<http://example.com>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com>][MTronException<137>:no active space supports pattern <http://example.com>]][no active space supports pattern <http://example.com>]@/sys/fail/274
mtron> *<http://example.com>.test(html::T)
==>fail::[unable to locate inst-f of test(html::T)@<1>]@/sys/fail/278
```
The string is predicate-validated: `html::T`'s predicate checks that the content is valid HTML.

## Structural Parse — rec::T DOM Tree

To get the rich `rec::T` DOM representation, use `?mimeq=application/x-mtron` or `.as(rec::T)`:

```mtron
mtron> [-- via query parameter on the URL --]
mtron> *<http://example.com?mimeq=application/x-mtron>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com?mimeq=application/x-mtron>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com?mimeq=application/x-mtron>][MTronException<137>:no active space supports pattern <http://example.com?mimeq=application/x-mtron>]][no active space supports pattern <http://example.com?mimeq=application/x-mtron>]@/sys/fail/280
mtron> [-- via .as(rec::T) on a typed string --]
mtron> *<http://example.com>.as(rec::T)
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com>][MTronException<137>:no active space supports pattern <http://example.com>]][no active space supports pattern <http://example.com>]@/sys/fail/282
```
The `?mimeq=application/x-mtron` query is handled by `QCollection.mimeQ()` postRead processor, which (1) probes the
content type from the response headers or URI extension, (2) tags the string with the correct TID (triggering predicate
validation), and (3) if `application/x-mtron` is requested, runs the content-type-specific serializer
(`ObjHTMLSerializer` for HTML) to produce the `rec::T` DOM tree.

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

Tree walking into the DOM tree requires the structural `rec::T` form. Use `.as(rec::T)` first, then walk the tree:

```mtron
mtron> [-- Cast to rec first, then walk --]
mtron> *<http://example.com>.as(rec::T)/html/body/out           [-- body's children list --]
==>fail::[parse error at line 1, col 33:
     *<http://example.com>.as(rec::T)/html/body/out           
                                     ^
     could not parse at '/']@/sys/fail/284
mtron> *<http://example.com>.as(rec::T)/html/body/out/+/out      [-- first child's children (h1, p, p) --]
==>fail::[parse error at line 1, col 33:
     *<http://example.com>.as(rec::T)/html/body/out/+/out      
                                     ^
     could not parse at '/']@/sys/fail/286
mtron> *<http://example.com>.as(rec::T)/html/body/out/+/out/+/out  [-- grandchild (a tag) --]
==>fail::[parse error at line 1, col 33:
     *<http://example.com>.as(rec::T)/html/body/out/+/out/+/out  
                                     ^
     could not parse at '/']@/sys/fail/288
```
### With `+` wildcards

The `+` wildcard enters each list element, producing a stream/set:

```mtron
mtron> *page.as(rec::T)/html/body/out/+/out        [-- all children of all body elements --]
==>fail::[parse error at line 1, col 17:
     *page.as(rec::T)/html/body/out/+/out        
                     ^
     could not parse at '/']@/sys/fail/290
mtron> *page.as(rec::T)/html/body/out/+/out/+/out  [-- all grandchildren --]
==>fail::[parse error at line 1, col 17:
     *page.as(rec::T)/html/body/out/+/out/+/out  
                     ^
     could not parse at '/']@/sys/fail/292
```
### Filtering with `where()`

```mtron
mtron> [-- Filter for elements with tag=a, then extract href --]
mtron> *page.as(rec::T)/html/body/out/+/out/+/out/+>.where([tag=>a])>>href
==>fail::[parse error at line 1, col 17:
     *page.as(rec::T)/html/body/out/+/out/+/out/+>.where([tag...
                     ^
     could not parse at '/']@/sys/fail/294
```
## Converting Back — rec::T → html::T

Serialize a `rec::T` DOM tree back to an HTML string with `.as(html::T)`:

```mtron
mtron> [-- Parse to rec, mutate, serialize back to html string --]
mtron> *<http://example.com>.as(rec::T)
         .at(html/body/out -> [/* modified children */]).as(html::T)
==>fail::[parse error at line 2, col 10:
     ...http://example.com>.as(rec::T)
            .at(html/body/out -> [/* modified childr...
                                                ^
     could not parse at '.']@/sys/fail/296
```
`ObjHTMLSerializer.write()` handles both `str::T` (pass-through via `Jsoup.parse()`) and `rec::T` (DOM rendering).

## Other MIME Types

The same pattern applies to JSON, XML, Markdown, etc.:

```mtron
mtron> *<http://example.com/data.json>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com/data.json>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com/data.json>][MTronException<137>:no active space supports pattern <http://example.com/data.json>]][no active space supports pattern <http://example.com/data.json>]@/sys/fail/298
mtron> *<http://example.com/data.json?mimeq=application/x-mtron>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com/data.json?mimeq=application/x-mtron>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com/data.json?mimeq=application/x-mtron>][MTronException<137>:no active space supports pattern <http://example.com/data.json?mimeq=application/x-mtron>]][no active space supports pattern <http://example.com/data.json?mimeq=application/x-mtron>]@/sys/fail/300
mtron> *<http://example.com/data.json>.as(rec::T)
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<http://example.com/data.json>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<http://example.com/data.json>][MTronException<137>:no active space supports pattern <http://example.com/data.json>]][no active space supports pattern <http://example.com/data.json>]@/sys/fail/302
```
## MIME Type → TID Mapping

`MIME.MIMEType.toTid()` provides the reverse mapping used by the `mimeq` query processor:

| MIME Type             | TID                                     |
|-----------------------|-----------------------------------------|
| `text/html`           | `/m/web/mime/html` (`HTML_TID`)         |
| `text/markdown`       | `/m/web/mime/markdown` (`MARKDOWN_TID`) |
| `application/json`    | `/m/web/mime/json` (`WEB_JSON_TID`)     |
| `application/xml`     | `/m/web/mime/xml` (`XML_TID`)           |
| `text/css`            | `/m/web/mime/css` (`CSS_TID`)           |
| `text/x-java`         | `/m/web/mime/java` (`JAVA_TID`)         |
| `application/x-mtron` | `null` (structural parse gate)          |

## Important Notes

- Only `http://` currently works (not `https://`). Strip the 's' from https URLs.
- Tree walking requires `.as(rec::T)` first — typed strings don't support field access.
- httpSpace host is configured in boot: `host => <http://localhost:8777>`, `pattern => <http://#>`
- `?mimeq=application/x-mtron` on httpSpace triggers structural parse via the content-type serializer.
- `?mimeq=text/html` just tags the string with `html::T` without structural conversion.