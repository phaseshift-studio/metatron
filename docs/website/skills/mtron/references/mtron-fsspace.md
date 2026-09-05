---
name: mtron-fsspace
description: |
  Learn how to read and write file system files using mtron via fsSpace. Covers MIME type
  handling, file I/O, the ?mimeq query processor, and pattern-based access.
  TRIGGER: When working with file reads/writes, local file access, file type detection,
  fsSpace configuration, or MIME-based file handling.
---

# FileSystem Space (fsSpace)

An `fsspace` mounts a subset of a file system into metatron's URI address space. Files are addressed via the space's
scheme (e.g., `local:`) and path prefix.

**IMPORTANT**: Every uri can be wrapped in angle brackets `< >`, but it is only required for those uris that have `.`
(periods), ` ` (spaces), and/or special characters such as `~` (tildes) in them. For instance, `/a/b/c` can be written
as is, but
`</a/b/c.txt>` requires angle brackets.

## Configuration

A typical `fsspace` definition:

```mtron
mtron> fsspace::[
         pattern => <local:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [local: => <~/my-project>]]@/sys/space/fs/local
```
- **`pattern`** — the URI pattern this space handles (`local:#` matches `<local:file.txt>`, `<local:sub/dir/file.md>`,
  etc.)
- **`route`** — maps the pattern prefix (`local:`) to a filesystem path (`<~/my-project>`)
- **`q`** — query processors: `mimeq` for MIME type tagging/conversion, `lineq` for line-level reads/writes

## MIME Type Handling

fsSpace detects a file's MIME type from its extension (and optionally the OS content probe) and returns a **typed
string** — a refined `str::T` such as `html::T`, `json::T`, `markdown::T`, etc.

```
file.html  →  html::"<html>...</html>"     (predicate-validated HTML string)
file.json  →  json::"{\"key\":\"value\"}"  (predicate-validated JSON string)
file.txt   →  str::"plain text"            (bare string, no special type)
file.md    →  markdown::"# Title"          (predicate-validated markdown string)
```

The MIME type acts as a **predicate** on the string content. For example, `html::T`'s predicate validates that the
string is valid HTML. The structural representation (`rec::T` DOM tree) is opt-in via `?mimeq=application/x-mtron` or
`.as(rec::T)`.

### MIME-to-TID Mapping

`MIME.MIMEType.toTid()` maps file extensions to type TIDs:

| Extension       | MIME Type             | TID                    |
|-----------------|-----------------------|------------------------|
| `.html`, `.htm` | `text/html`           | `/m/web/mime/html`     |
| `.json`         | `application/json`    | `/m/web/mime/json`     |
| `.xml`          | `application/xml`     | `/m/web/mime/xml`      |
| `.md`           | `text/markdown`       | `/m/web/mime/markdown` |
| `.css`          | `text/css`            | `/m/web/mime/css`      |
| `.java`         | `text/x-java`         | `/m/web/mime/java`     |
| `.yaml`, `.yml` | `application/yaml`    | `/m/web/mime/yaml`     |
| `.mtron`        | `application/x-mtron` | `/m/rec`               |
| `.txt`          | `text/plain`          | `/m/str`               |
| _other_         | `text/plain` / probe  | `/m/str`               |

### The `mimeq` Query Processor

The `?mimeq=` query parameter on a file URI controls what the space returns:

```mtron
mtron> [-- Default: typed string (predicate-validated) --]
mtron> *<local:index.html>
mtron> [-- Explicit type tag (same as default for .html files) --]
mtron> *<local:index.html?mimeq=text/html>
mtron> [-- Structural parse via application/x-mtron --]
mtron> *<local:index.html?mimeq=application/x-mtron>
```
`mimeq` is implemented in `QCollection.mimeQ()` as a space-level `postRead` query processor. It:

1. **Probes** the content type from the object's existing TID (or falls back to URI/file extension if the TID is bare
   `STR_TID`)
2. **Tags** the string with the correct MIME TID — this triggers predicate validation (e.g., `html::T` validates the
   string is valid HTML)
3. **Structural parse** — if `?mimeq=application/x-mtron`, runs the content-type-specific serializer
   (`ObjHTMLSerializer` for HTML, `ObjJSONSerializer` for JSON, etc.) to produce the `rec::T` DOM tree

## Reading and Writing Files

### Basic Read/Write

```mtron
mtron> [-- Read a file (returns typed string by default) --]
mtron> *<local:test.md>
==>markdown::'## new content'
mtron> [-- Write a string to a file --]
mtron> <local:test.md> -> "## new content"
==>'## new content'
```
### Reading with Structural Parse

```mtron
mtron> [-- Read markdown as a rec::T structure --]
mtron> *<local:test.md?mimeq=application/x-mtron>
==>[
    type=>doc,
    out=>[[
    type=>head,
    level=>2,
    text=>'new content',
    out=>[[type=>text,content=>'new content']]]]]
mtron> [-- Read JSON, then walk into rec fields --]
mtron> *<local:config.json?mimeq=application/x-mtron>/database/host
==>fail::[parse error at line 1, col 47:
     ...l:config.json?mimeq=application/x-mtron>/database/host
                                                ^
     could not parse at '/']@/sys/fail/444
```
### Binary Files

Files without a recognized text MIME type are read as `bytes::T`. Executable files (with shebangs)
are treated as `inst::T` and can be invoked directly:

```mtron
mtron> *<local:script.sh>        [-- bytes::T if binary, str::T if text                    --]
mtron> <local:script.sh>.exec()  [-- execute (shell scripts, via application/x-mtron exec) --]
==>fail::[unable to locate inst-f of exec()@<1>]@/sys/fail/446
```
## Pattern-Based Access

fsSpace supports wildcard patterns in reads:

```mtron
mtron> [-- List all files in a directory --]
mtron> *<local:+/>
==><local:/test.md>=>markdown::'## new content'
mtron> [-- Read all .txt files --]
mtron> *<local:+/+>.where([name => where(^(>>is(hasPostfix(.txt))))])
==>fail::[parse error at line 1, col 14:
     *<local:+/+>.where([name => where(^(>>is(hasPostfix(....
                  ^
     could not parse at 'w']@/sys/fail/448
```
## Line-Level Editing with `lineq`

The `lineq` query processor enables reading and editing specific line ranges within text files, useful for targeted
edits without loading the entire file:

```mtron
mtron> [-- Read lines 10-20 of a file --]
mtron> *<local:src/main.java?lineq=10..20>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<local:src/main.java?lineq=10..20>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<local:src/main.java?lineq=10..20>][NumberFormatException<67>:For input string: "10..20"[NumberFormatException<67>:For input string: "10..20"] ← For input string: "10..20"]][For input string: "10..20"[NumberFormatException<67>:For input string: "10..20"]][For input string: "10..20"]@/sys/fail/450
mtron> [-- Replace lines 5-10 with new content --]
mtron> <local:src/main.java?lineq=5..10> -> """
         public void newMethod() {
           // new implementation
         }
       """
==>fail::[apply failure:
   	[lhs]    │ <local:src/main.java?lineq=5..10>
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ ref?rng=#{*}&dom=#("""
   public void newMethod() {
   // new implementation
   }
   """){<j>}@<1>
   	 \_dom   │ #::T
   	 \_args  │ ["""
   public void newMethod() {
   // new implementation
   }
   """][NumberFormatException<67>:For input string: "5..10"[NumberFormatException<67>:For input string: "5..10"] ← For input string: "5..10"]][For input string: "5..10"[NumberFormatException<67>:For input string: "5..10"]][For input string: "5..10"]@/sys/fail/452
```
### Boot Configuration Example

```mtron
mtron> fsspace::[
         pattern => <local:#>,
         q       => [mimeq::[=>], lineq::[=>]],
         route   => [local: => ~/src]]@/sys/space/fs/src
mtron> [-- Then use in expressions: --]
mtron> *<local:Main.java?lineq=1..50>
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ *<local:Main.java?lineq=1..50>
   	 \_dom   │ #{?}::T
   	 \_args  │ [<local:Main.java?lineq=1..50>][NumberFormatException<67>:For input string: "1..50"[NumberFormatException<67>:For input string: "1..50"] ← For input string: "1..50"]][For input string: "1..50"[NumberFormatException<67>:For input string: "1..50"]][For input string: "1..50"]@/sys/fail/454
mtron> <local:index.html?mimeq=application/x-mtron>/html/head/title
==>fail::[parse error at line 1, col 45:
     ...al:index.html?mimeq=application/x-mtron>/html/head/title
                                                ^
     could not parse at '/']@/sys/fail/456
```
## Type Round-Trip

The full read-modify-write cycle preserves types:

```mtron
mtron> [-- Read HTML, cast to rec, modify, cast back to html string, write --]
mtron> <local:page.html> -> *<local:page.html?mimeq=application/x-mtron>
         .at(html/head/title -> 'New Title')
         .as(html::T)
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ at?rng=B{*}&dom=A{?}('New Title'){<j>}@<2>
   	 \_dom   │ A{?}::T
   	 \_args  │ ['New Title'][MTronException<137>:'New Title' [str::T] unable to convert uri::T]]['New Title' [str::T] unable to convert uri::T]@/sys/fail/466
mtron> [-- Read JSON config, modify a value, write back --]
mtron> <local:config.json> -> *<local:config.json?mimeq=application/x-mtron>
         .at(database/host -> 'new-host')
         .as(json::T)
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ at?rng=B{*}&dom=A{?}('new-host'){<j>}@<2>
   	 \_dom   │ A{?}::T
   	 \_args  │ ['new-host'][MTronException<137>:'new-host' [str::T] unable to convert uri::T]]['new-host' [str::T] unable to convert uri::T]@/sys/fail/476
```
The `.as(html::T)` / `.as(json::T)` serialization passes through `ObjHTMLSerializer.write()` /
`ObjJSONSerializer.write()` which handle both `str::T` (pass-through) and `rec::T` (structural render).