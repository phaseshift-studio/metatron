---
name: connecting-datasources
description: Connecting external data sources to metatron spaces — the pattern and route model, creating !* references, and validating connections.
---

# Connecting Data Sources

## Workflow

1. Understand source (type, format, location)
2. Study schema
3. Create `!*` reference
4. Validate connection

## Key Concept: Pattern & Route

Every space has:

- **pattern** — what uris route to this space (e.g., `local:#`, `/usr/agent/#`)
- **route** — how uris transform to the space's internal schema

```mtron
*/sys/space/${space}                     [-- view space config  --]
*/sys/space/${space}/pattern             [-- view space pattern --]
*/sys/space/${space}/route               [-- view space route   --]
*/sys/space/${space}/schema              [-- view space schema  --]
```

**IMPORTANT**: Not all spaces have a `schema`. Typically, only database oriented spaces have associated schemas which
are represented as space-specific `instsets` (instruction sets).

**Don't hardcode prefixes!** Discover them from the space config.

Example: If `fs` space has `pattern=>local:#` and `route=>[local:=>~/]`:

- URIs starting with `local:` go to the fs space
- `local:` is stripped and replaced with `~/` internally
- Query: `*<local:path/to/file>` → reads `~/path/to/file`

## Universal (all environments)

```mtron
mtron> */sys/space/+/+/                         [-- List all spaces --]
==>/sys/space/dev/metatron=>memspace::[
    pattern=>/dev/scratch/#,
    q=>[
     mintq::[
      pattern=>mintq,
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     docq::[
      pattern=>docq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>memspace::[pattern=><#>],
      inst=>instset::[pattern=><#>]],
     subq::[
      pattern=>subq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      qless_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>[sub::[
    code=>rshift(0).as(rec::T).rshift(path).select([id(),id(),id(),id(),id(),id()]).to(temp).as?rng=uri&dom=lst(uri::T).to(x).*id().update([location=>none]).as(java::T).to(**x.rshift(location).side(split([
     location=>id(),
     status=>saved,
     time=>!math:datetime_now()]).print('saved ',id(),'\n'))).map(map(/dev/scratch/src).mult(*temp.reverse().merge().take(1))),
    target=>/dev/scratch/code/#]]],
     mimeq::[
      pattern=>mimeq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T)],
     lineq::[
      pattern=>lineq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     lockq::[
      pattern=>lockq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      qless_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>[,]],
     incrq::[
      pattern=>incrq,
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)]]]@/sys/space/dev/metatron
==>/sys/space/log/scratch=>tblespace::[
    pattern=>/log/scratch/#,
    host=><sqlite:target/log_scratch.sqlite>,
    driver=><org.sqlite.JDBC>,
    table=>[,],
    q=>[
     incrq::[
      pattern=>incrq,
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     subq::[
      pattern=>subq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      qless_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>[,]],
     mimeq::[
      pattern=>mimeq,
      post_read=>inst?#{*}<=#{?}(uri::T,#::T)]],
    route=>[/log/scratch/=><>]]@/sys/space/log/scratch
mtron> */sys/space/${space}                     [-- View space config --]
mtron> !*<uri>                                  [-- Create reference (lazy) --]
mtron> *<uri>                                   [-- Dereference (evaluate) --]
```
## Connection Patterns (adapt to user's environment)

### SQL (tabledb)

```mtron
[rdbms,localhost,5512,skill,admin,admin]==[
 to(prefix),
 to(host),
 to(port),
 to(db),
 to(user),
 to(pass)]
tabledb::[/
  pattern=>${prefix}:#,/
  host=>mariadb://${host}:${port}/${db}?password=${pass}&user=${user},/
  route=>[${prefix}:=><>],/
  driver=><org.mariadb.jdbc.Driver>
]
```

### MongoDB (docdb)

```mtron
docdb::[/
  pattern=>${prefix}:#,/
  host=>mongodb://${host}:${port}/${db},/
  serializer=>!*/m/mach/io/serializer/bson,/
  route=>[${prefix}:=><>]
]
```

### Graph (graphdb)

```mtron
graphdb::[/
  pattern=>${prefix}:#,/
  route=>[${prefix}:V=>V, ${prefix}:E=>E]
]
```

### MQTT

```mtron
mqtt::[/
  pattern=>${prefix}:#,/
  host=><mqtt://${host}:${port}>,/
  serializer=>!*/m/mach/io/serializer/json/simple,/
  route=>[${prefix}:=>${topic}/]
]
```

## Schema Discovery

```mtron
[-- First, get the pattern to know how to query --]
*/sys/space/${space}.pattern             [-- e.g., "acme:#"

[-- SQL (tabledb) --]
*/sys/space/${space}.table               [-- List tables --]
*/sys/space/${space}.schema.tables       [-- Schema + types --]
*/sys/space/${space}.schema.foreign_keys [-- Foreign keys --]

[-- MongoDB (docdb) --]
*/sys/space/${space}.collection          [-- List collections --]

[-- File system (fs) --]
*<${pattern_prefix}path/#>               [-- List files recursively --]
```

## Validation

```mtron
[-- Use the pattern prefix discovered from the space --]
*<${pattern_prefix}resource>.*(_).limit(5)
```

## Common Issues

| Issue         | Solution                 |
|---------------|--------------------------|
| Timeout       | Verify host/port         |
| Type mismatch | Specify types explicitly |
| Auth error    | Check credentials        |