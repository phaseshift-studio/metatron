# Connecting Data Sources

## Workflow
1. Understand source (type, format, location)
2. Study schema
3. Create `!*` reference
4. Validate connection

## Key Concept: Pattern & Route

Every space has:
- **pattern** — what URIs route to this space (e.g., `local:#`, `acme:#`)
- **route** — how URIs transform for the space's internal schema

```mtron
*/sys/space/${space}                     # view space config
*/sys/space/${space}/schema              # view space schema
*/sys/space/${space}.pattern             # just the pattern
*/sys/space/${space}.route               # just the route
```

It's best to first study the data sources by reviewing their schema. However, not all data sources have an associated schema. In such cases, limit the amount of data accessed via `*{pattern}/+.take(10)` (eg. get the first 10 results).

**Don't hardcode prefixes!** Discover them from the space config.

Example: If `fs` space has `pattern=>local:#` and `route=>[local:=>~/]`:
- URIs starting with `local:` go to the fs space
- `local:` is stripped and replaced with `~/` internally
- Query: `*<local:path/to/file>` → reads `~/path/to/file`

## Universal (all environments)

```mtron
*/sys/space/+/                           # List all spaces
*/sys/space/${space}                     # View space config
!*<uri>                                  # Create reference (lazy)
*<uri>                                   # Dereference (evaluate)
```

## Connection Patterns (adapt to user's environment)

### SQL (tabledb)
```mtron
tabledb::[
  pattern=>${prefix}:#,
  host=>mariadb://${host}:${port}/${db}?password=${pass}&user=${user},
  route=>[${prefix}:=><>],
  driver=><org.mariadb.jdbc.Driver>
]
```

### MongoDB (docdb)
```mtron
docdb::[
  pattern=>${prefix}:#,
  host=>mongodb://${host}:${port}/${db},
  serializer=>!*/m/mach/io/serializer/bson,
  route=>[${prefix}:=><>]
]
```

### Graph (graphdb)
```mtron
graphdb::[
  pattern=>${prefix}:#,
  route=>[${prefix}:V=>V, ${prefix}:E=>E]
]
```

### MQTT
```mtron
mqtt::[
  pattern=>${prefix}:#,
  host=><mqtt://${host}:${port}>,
  serializer=>!*/m/mach/io/serializer/json/simple,
  route=>[${prefix}:=>${topic}/]
]
```

## Schema Discovery

```mtron
# First, get the pattern to know how to query
*/sys/space/${space}.pattern             # e.g., "acme:#" → use acme: prefix

# SQL (tabledb)
*/sys/space/${space}.table               # List tables
*/sys/space/${space}.schema.tables       # Schema + types
*/sys/space/${space}.schema.foreign_keys # Foreign keys

# MongoDB (docdb)
*/sys/space/${space}.collection          # List collections

# File system (fs)
*<${pattern_prefix}path/#>               # List files recursively
```

## Validation
```mtron
# Use the pattern prefix discovered from the space
*<${pattern_prefix}resource>.*(_).limit(5)
```

## Common Issues

| Issue | Solution |
|-------|----------|
| Timeout | Verify host/port |
| Type mismatch | Specify types explicitly |
| Auth error | Check credentials |
