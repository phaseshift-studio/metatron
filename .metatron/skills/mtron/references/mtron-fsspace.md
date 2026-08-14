---
name: mtron-fsspace
description: |
  Learn how to read and write file system files using mtron via fsSpace. Covers MIME type
  handling, file I/O, the mimeq query processor, and pattern-based access.
  TRIGGER: When working with file reads/writes, local file access, file type detection,
  fsSpace configuration, or MIME-based file handling.
---

# FileSystem Space (fsSpace)

An `fsspace` mounts a subset of a file system into metatron's URI address space. Files are
addressed via the space's scheme (e.g., `local:`) and path prefix.

## Configuration

A typical `fsspace` definition:

```mtron
fsspace::[
  pattern => <local:#>,
  q       => [mimeq::[=>], lineq::[=>]],
  route   => [local: => <~/my-project>]]@/sys/space/fs/local
```

- **`pattern`** — the URI pattern this space handles (`local:#` matches `local:file.txt`, `local:sub/dir/file.md`, etc.)
- **`route`** — maps the pattern prefix (`local:`) to a filesystem path (`~/my-project`)
- **`q`** — query processors: `mimeq` for MIME type tagging/conversion, `lineq` for line-level reads/writes

## MIME Type Handling

fsSpace detects a file's MIME type from its extension (and optionally the OS content probe) and returns
a **typed string** — a `str::T` tagged with the content-type TID (e.g., `html::T`, `json::T`).

```
file.html  →  html::"<html>...</html>"     (predicate-validated HTML string)
file.json  →  json::"{\"key\":\"value\"}"  (predicate-validated JSON string)
file.txt   →  str::"plain text"            (bare string, no special type)
file.md    →  markdown::"# Title"          (predicate-validated markdown string)
```

The MIME type acts as a **predicate** on the string content. For example, `html::T`'s predicate
validates that the string is valid HTML. The structural representation (`rec::T` DOM tree) is
opt-in via `?mimeq=application/x-mtron` or `.as(rec::T)`.

### MIME-to-TID Mapping

`MIME.MIMEType.toTid()` maps file extensions to type TIDs:

| Extension | MIME Type | TID |
|---|---|---|
| `.html`, `.htm` | `text/html` | `/m/web/mime/html` |
| `.json` | `application/json` | `/m/web/mime/json` |
| `.xml` | `application/xml` | `/m/web/mime/xml` |
| `.md` | `text/markdown` | `/m/web/mime/markdown` |
| `.css` | `text/css` | `/m/web/mime/css` |
| `.java` | `text/x-java` | `/m/web/mime/java` |
| `.yaml`, `.yml` | `application/yaml` | `/m/web/mime/yaml` |
| `.mtron` | `application/x-mtron` | `null` (structural parse) |
| `.txt` | `text/plain` | `null` (bare string) |
| _other_ | `text/plain` / probe | `null` |

### The `mimeq` Query Processor

The `?mimeq=` query parameter on a file URI controls what the space returns:

```mtron
# Default: typed string (predicate-validated)
*<local:index.html>
==>html::"<html><body><h1>Hello</h1></body></html>"

# Explicit type tag (same as default for .html files)
*<local:index.html?mimeq=text/html>
==>html::"<html><body><h1>Hello</h1></body></html>"

# Structural parse via application/x-mtron
*<local:index.html?mimeq=application/x-mtron>
==>[html => [head => [...], body => [out => [[tag => h1, text => 'Hello']]]]]
```

`mimeq` is implemented in `QCollection.mimeQ()` as a space-level `postRead` query processor. It:

1. **Probes** the content type from the object's existing TID (or falls back to URI/file extension if the TID is bare `STR_TID`)
2. **Tags** the string with the correct MIME TID — this triggers predicate validation (e.g., `html::T` validates the string is valid HTML)
3. **Structural parse** — if `?mimeq=application/x-mtron`, runs the content-type-specific serializer (`ObjHTMLSerializer` for HTML, `ObjJSONSerializer` for JSON, etc.) to produce the `rec::T` DOM tree

## Reading and Writing Files

### Basic Read/Write

```mtron
# Read a file (returns typed string by default)
*<local:test.md>

# Write a string to a file
<local:test.md> -> "## new content"
```

### Reading with Structural Parse

```mtron
# Read markdown as a rec::T structure
*<local:test.md?mimeq=application/x-mtron>

# Read JSON, then walk into rec fields
*<local:config.json?mimeq=application/x-mtron>/database/host
```

### Binary Files

Files without a recognized text MIME type are read as `bytes::T`. Executable files (with shebangs)
are treated as `inst::T` and can be invoked directly:

```mtron
*<local:script.sh>        # bytes::T if binary, str::T if text
<local:script.sh>.exec()  # execute (shell scripts, via application/x-mtron exec)
```

## Pattern-Based Access

fsSpace supports wildcard patterns in reads:

```mtron
# List all files in a directory
*<local:+/>

# Read all .txt files
*<local:+/+>.where([name => where(^(>>is(hasPostfix(.txt))))])
```

## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files,
useful for targeted edits without loading the entire file:

```mtron
# Read lines 10-20 of a file
*<local:src/main.java?lineq=10..20>

# Replace lines 5-10 with new content
<local:src/main.java?lineq=5..10> -> """
  public void newMethod() {
    // new implementation
  }
"""
```

### Boot Configuration Example

```mtron
fsspace::[
  pattern => <local:#>,
  q       => [mimeq::[=>], lineq::[=>]],
  route   => [local: => ~/src]]@/sys/space/fs/src

# Then use in expressions:
*<local:Main.java?lineq=1..50>
<local:index.html?mimeq=application/x-mtron>/html/head/title
```

## Type Round-Trip

The full read-modify-write cycle preserves types:

```mtron
# Read HTML, cast to rec, modify, cast back to html string, write
<local:page.html> -> *<local:page.html?mimeq=application/x-mtron>
  .at(html/head/title -> 'New Title')
  .as(html::T)

# Read JSON config, modify a value, write back
<local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
  .at(database/host -> 'new-host')
  .as(json::T)
```

The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).
