---
name: http-page-fetching
description: >
  Fetching HTTP pages in mtron via httpSpace, HTML parse tree structure, and tree traversal.
  TRIGGER: When working with http:// dereferences, HTML parsing, web scraping, page fetching,
  HTML tree walking, extracting links from HTML.
---

# HTTP Page Fetching in mtron

## Architecture

`httpSpace` uses Jsoup to fetch URLs and `ObjHTMLSerializer` to convert HTML into mtron's nested rec structure. The space pattern is `<http://#>`, so any `http://` URI routes through it.

## Basic Dereference

```mtron
*<http://example.com>               # fetches and parses entire page
*<http://example.com/html/head/title>  # direct element access
```

## HTML Rec Structure

The parsed HTML is a nested rec with this pattern:

```
html::[html => [
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

## Tree Walking

### Direct path access (depth known)

```mtron
*<http://example.com>/html/body/out           # body's children list
*<http://example.com>/html/body/out/+/out      # first child's children (h1, p, p)
*<http://example.com>/html/body/out/+/out/+/out  # grandchild (a tag)
```

### With `+` wildcards

The `+` wildcard enters each list element, producing a stream/set:
```
*page/html/body/out/+/out        # all children of all body elements
*page/html/body/out/+/out/+/out  # all grandchildren
```

### Filtering with `where()`

```mtron
# Filter for elements with tag=a, then extract href
*page/html/body/out/+/out/+/out/+>.where([tag=>a])>>href
```

### Planned: `repeat()` for deep traversal (NOT YET IMPLEMENTED)

```mtron
*page/html/body.repeat([
  path  => out/+,
  emit  => where([tag=>a])>>href,
  until => ^(>>loops.is(gt(5)))
])
```

The `^` lifts computation into the traversal monad to inspect traversal state (e.g., loop count).
`>>` extracts a field from each element.

## Important Notes

- Only `http://` currently works (not `https://`). Strip the 's' from https URLs.
- The `+` wildcard can cause "rhs does not match inst range" errors when the result type doesn't match — the data is present in the error output.
- httpSpace host is configured in boot.mtron: `host => <http://localhost:8777>`, `pattern => <http://#>`
