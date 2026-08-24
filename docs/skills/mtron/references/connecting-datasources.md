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

```mtron_pre
*/sys/space/+/+/                         [-- List all spaces --]
*/sys/space/${space}                     [-- View space config --]
!*<uri>                                  [-- Create reference (lazy) --]
*<uri>                                   [-- Dereference (evaluate) --]
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
