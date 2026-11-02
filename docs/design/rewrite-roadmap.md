# Rewrite Roadmap

Planned SQL rewrite optimizations for `CommonRewrites.java`.

Extracted from the source file on 2026-05-24.

| # | Rewrite | Pattern | SQL Equivalent | Status |
|---|---------|---------|---------------|--------|
| 1 | `skipRewrite` | `from(table).skip(n)` | `SELECT * FROM table OFFSET n` | planned |
| 2 | `dedupRewrite` | `from(table).dedup()` | `SELECT DISTINCT * FROM table` | planned |
| 3 | `paginationRewrite` | `from(table).skip(m).take(n)` | `SELECT * FROM table LIMIT n OFFSET m` | planned |
| 4 | `orderRewrite` | `from(table).order(column)` | `SELECT * FROM table ORDER BY column [ASC\|DESC]` | planned |
| 5 | `order+take` | `from(table).order(column).take(n)` | `SELECT * FROM table ORDER BY column LIMIT n` | planned |

## Notes

- `skipRewrite`: Similar to `limitRewrite` but extracts offset from `skip()` instruction.
- `paginationRewrite`: Requires matching a 3-instruction sequence and extracting both values.
- `orderRewrite`: Needs to extract column name from `order()` argument and map to SQL column.
- `order+take`: Very common pattern for "top N" queries; significant optimization potential.
