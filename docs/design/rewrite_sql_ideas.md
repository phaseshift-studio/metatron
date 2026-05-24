# SQL Query Rewrite Optimization Ideas

This document contains potential query rewrites for `tbleInstSet` that push computation down to the SQL database instead of performing it in-memory.

## Implemented Rewrites

### 1. Native Count
**Pattern**: `[from, count]`
**Rewrite**: `SELECT COUNT(*) FROM table`
**Benefit**: Avoids fetching all rows just to count them
**Example**:
```
*netflix:movie.count()
=> SELECT COUNT(*) FROM movie
=> 11983
```

---

## Proposed Rewrites

### 2. Filter + Count
**Pattern**: `[from, filter(predicate), count]`
**Rewrite**: `SELECT COUNT(*) FROM table WHERE predicate`
**Benefit**: Pushes filter to database, counts only matching rows
**Example**:
```
*netflix:movie[?year > 2020].count()
=> SELECT COUNT(*) FROM movie WHERE year > 2020
```

**Complexity**: Requires translating Metatron predicates to SQL WHERE clauses

---

### 3. Map (Projection)
**Pattern**: `[from, map(field)]`
**Rewrite**: `SELECT field FROM table`
**Benefit**: Fetches only needed columns instead of all columns
**Example**:
```
*netflix:movie.map(title)
=> SELECT title FROM movie
```

**Note**: Already partially implemented in `ExistingTableSchema.read()` for field-level access

---

### 4. Filter + Map
**Pattern**: `[from, filter(predicate), map(field)]`
**Rewrite**: `SELECT field FROM table WHERE predicate`
**Benefit**: Combines projection and filtering in single query
**Example**:
```
*netflix:movie[?year > 2020].map(title)
=> SELECT title FROM movie WHERE year > 2020
```

---

### 5. Sort (Order By)
**Pattern**: `[from, sort(field)]` or `[from, sort(comparator)]`
**Rewrite**: `SELECT * FROM table ORDER BY field [ASC|DESC]`
**Benefit**: Uses database sorting instead of in-memory sorting
**Example**:
```
*netflix:movie.sort(year)
=> SELECT * FROM movie ORDER BY year ASC
```

**Complexity**: Need to detect sort direction and handle custom comparators

---

### 6. Limit (Take)
**Pattern**: `[from, limit(n)]` or `[from, take(n)]`
**Rewrite**: `SELECT * FROM table LIMIT n`
**Benefit**: Database stops after n rows instead of fetching all then limiting
**Example**:
```
*netflix:movie.limit(10)
=> SELECT * FROM movie LIMIT 10
```

---

### 7. Filter + Sort + Limit
**Pattern**: `[from, filter(predicate), sort(field), limit(n)]`
**Rewrite**: `SELECT * FROM table WHERE predicate ORDER BY field LIMIT n`
**Benefit**: Complete query pushdown - very common pattern
**Example**:
```
*netflix:movie[?year > 2020].sort(rating).limit(10)
=> SELECT * FROM movie WHERE year > 2020 ORDER BY rating DESC LIMIT 10
```

---

### 8. Aggregations (Sum, Avg, Max, Min)
**Pattern**: `[from, map(field), sum]`
**Rewrite**: `SELECT SUM(field) FROM table`
**Benefit**: Database computes aggregate instead of fetching all values
**Examples**:
```
*netflix:movie.map(budget).sum()
=> SELECT SUM(budget) FROM movie

*netflix:movie.map(rating).avg()
=> SELECT AVG(rating) FROM movie

*netflix:movie.map(year).max()
=> SELECT MAX(year) FROM movie
```

---

### 9. Filter + Aggregation
**Pattern**: `[from, filter(predicate), map(field), sum]`
**Rewrite**: `SELECT SUM(field) FROM table WHERE predicate`
**Benefit**: Combines filtering and aggregation
**Example**:
```
*netflix:movie[?year > 2020].map(budget).sum()
=> SELECT SUM(budget) FROM movie WHERE year > 2020
```

---

### 10. Group By + Aggregation
**Pattern**: `[from, group(field), map(aggregate)]`
**Rewrite**: `SELECT field, AGG(value) FROM table GROUP BY field`
**Benefit**: Database performs grouping and aggregation
**Example**:
```
*netflix:movie.group(year).map(count)
=> SELECT year, COUNT(*) FROM movie GROUP BY year
```

**Complexity**: Requires understanding Metatron's group semantics

---

### 11. Distinct
**Pattern**: `[from, map(field), distinct]` or `[from, unique]`
**Rewrite**: `SELECT DISTINCT field FROM table`
**Benefit**: Database eliminates duplicates
**Example**:
```
*netflix:movie.map(director).distinct()
=> SELECT DISTINCT director FROM movie
```

---

### 12. Exists (Any)
**Pattern**: `[from, filter(predicate), any]` or `[from, filter(predicate), isEmpty, not]`
**Rewrite**: `SELECT EXISTS(SELECT 1 FROM table WHERE predicate)`
**Benefit**: Database stops at first match instead of fetching all
**Example**:
```
*netflix:movie[?year > 2025].any()
=> SELECT EXISTS(SELECT 1 FROM movie WHERE year > 2025)
```

---

## Implementation Considerations

### Pattern Matching Strategy
- **Regex/FSM Approach**: Walk instruction sequences like strings with a finite state machine (transducer)
- **Match => Action Model**: Fluent API for pattern matching and rewriting
  ```java
  rewrite(pattern("[from, count]"))
    .when(space -> space instanceof tbleSpace)
    .to(code -> nativeSqlCount(code))
  ```

### Challenges
1. **Predicate Translation**: Converting Metatron predicates/filters to SQL WHERE clauses
2. **Type Safety**: Ensuring rewrites preserve semantics
3. **Coefficient Preservation**: Maintaining coefficient multiplication through rewrites
4. **Composability**: Handling combinations of patterns (filter + sort + limit)
5. **SQL Dialect Differences**: SQLite vs PostgreSQL vs MySQL syntax variations

### Testing Strategy
- Unit tests for each rewrite pattern
- Performance benchmarks comparing rewritten vs non-rewritten queries
- Semantic equivalence tests (rewritten query produces same results)

---

## Future: Cross-Table Rewrites

Once foreign key traversal is implemented, we can optimize cross-table queries:

### Join Optimization
**Pattern**: `[from(table1), map(fk_field), from, map(field)]`
**Rewrite**: `SELECT t2.field FROM table1 t1 JOIN table2 t2 ON t1.fk = t2.id`
**Benefit**: Single JOIN query instead of N+1 queries

This will be crucial for the "universal graph database" vision where `>>` and `/+/+/+` traverse foreign keys.

---

## Notes
- Start with simple, high-value rewrites (count, limit, basic aggregations)
- Build pattern matching infrastructure that scales to 10+ rewrites
- Consider creating a rewrite testing framework
- Document performance improvements for each rewrite
