# Writing mtron Expressions

## Syntax

```mtron
*<uri>                    # Dereference (evaluate)
!*<uri>                   # Reference (lazy)
<source>.<op1>().<op2>()  # Chain operations
```

## Operations

| Op | Syntax | Purpose |
|----|--------|---------|
| Filter | `.filter(cond)` | Keep matching |
| Limit | `.limit(N)` | First N |
| Select | `.select(f1, f2)` | Project fields |
| Map | `.map(transform)` | Transform each |
| Count | `.count()` | Count items |
| Sum/Avg | `.sum(f)` / `.avg(f)` | Aggregate |
| Min/Max | `.min(f)` / `.max(f)` | Extremes |
| GroupBy | `.groupBy(f)` | By category |
| Distinct | `.distinct(f)` | Unique |
| Sort | `.sort(f)` | Order |

## Type Conversions

| From → To | Expression |
|-----------|------------|
| bytes → string | `.as(bytes::T).as(str::T)` |
| any → type | `.as(${type}::T)` |

## Universal (all environments)

```mtron
*/sys/space/+/                           # List spaces
*/sys/env/HOME                           # Env var
*<file:path>.as(bytes::T).as(str::T)     # Read file
<expr>.type()                            # Check type
```

## Deployment-Specific (adapt to user's environment)

**First:** Discover the pattern prefix from `*/sys/space/${space}.pattern`

```mtron
# Basic query (using discovered prefix, e.g., "acme:")
*${prefix}:${table}.*(_).limit(10)

# Filter
*${prefix}:${table}.*(_).filter(${field} == '${val}')
*${prefix}:${table}.*(_).filter(${field} > ${num})

# Project
*${prefix}:${table}.*(_).select(${f1}, ${f2}).limit(10)

# Transform
*${prefix}:${table}.*(_).map(_.${field})

# Aggregate
*${prefix}:${table}.*(_).count()
*${prefix}:${table}.*(_).groupBy(${field}).count()

# File system (using discovered prefix, e.g., "local:")
*<${prefix}path/to/file>                 # Read file
*<${prefix}path/#>                       # List recursively
```

## Build Incrementally

```mtron
*${space}:${table}.*(_).limit(5)                      # 1. Basic
*${space}:${table}.*(_).filter(${cond}).limit(5)      # 2. + Filter
*${space}:${table}.*(_).filter(${cond}).select(${f})  # 3. + Project
```

## Debug

```mtron
*${space}:${table}.*(_).limit(1)              # Test source
*${space}:${table}.*(_).limit(1).type()       # Check type
```

**Tips:** Use `.limit()` during dev. Move `.filter()` early. Break down on errors.
