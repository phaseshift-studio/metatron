# Providing Data Statistics

## Operations

| Op | Syntax | Purpose |
|----|--------|---------|
| Count | `.count()` | Total items |
| Sum | `.sum(field)` | Sum numeric |
| Avg | `.avg(field)` | Mean |
| Min/Max | `.min(field)` / `.max(field)` | Extremes |
| GroupBy | `.groupBy(field)` | By category |
| Distinct | `.distinct(field)` | Unique values |
| Filter | `.filter(cond)` | Subset |
| Limit | `.limit(N)` | First N |

## Universal (all environments)

```mtron
*/sys/space/+/.count()                   # Count spaces
<stream>.count()                         # Count items
<stream>.filter(<cond>).count()          # Filtered count
<stream>.type()                          # Check type
```

## Deployment-Specific (adapt to user's environment)

```mtron
# Counting
*${space}:${table}.*(_).count()
*${space}:${table}.*(_).filter(${field} > ${val}).count()

# Aggregation
*${space}:${table}.*(_).sum(${field})
*${space}:${table}.*(_).avg(${field})
*${space}:${table}.*(_).min(${field})
*${space}:${table}.*(_).max(${field})

# Distribution
*${space}:${table}.*(_).groupBy(${field})
*${space}:${table}.*(_).groupBy(${field}).count()
*${space}:${table}.*(_).distinct(${field})

# Combined
*${space}:${table}.*(_).filter(${cond}).groupBy(${cat}).count()
```

## Performance
- Use `.limit()` during exploration
- Apply `.filter()` early
- Run `.count()` first to gauge size
