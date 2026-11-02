# Boundaries of Expressivity and Optimization

## The Core Constraint

Two write primitives. One interface: `Space.read(vid)` / `Space.write(vid, obj)`. Everything the system can express or optimize flows from this.

```
->   ref()     write(obj, vid)           — blind put
>>=  update()  read(vid) → mutate → write(vid, mutated)  — read-modify-write
```

---

## 1. Boundaries of Expressivity

### What the two primitives CAN express

| Operation | Expression | Mechanism |
|-----------|-----------|-----------|
| INSERT | `vid -> obj` (new vid) | write |
| UPSERT | `vid -> obj` (existing vid) | write (existence check + INSERT/UPDATE) |
| UPDATE (literal) | `*vid >>= [f=>val, g=>val]` | read → mutate → write |
| UPDATE (computed) | `*vid >>= [age=>+1, name=>-'Jr']` | read → mutate(compute) → write |
| DELETE row | `vid -> noobj` | write(noobj) → SQL DELETE |
| DELETE field | `vid/field -> noobj` or `*vid >>= [field=>none]` | write(noobj) or poly key-removal |
| BULK UPDATE | `*vid/+ >>= [f=>val]` | poly unrolling → N individual writes |
| PRECISION WRITE | `vid/a/b/c -> val` | nested write resolution |

### What the two primitives CANNOT express

**INSERT-only (fail if exists).** `->` is always upsert. No way to say "insert, but don't overwrite." Would need a separate `insert()` primitive or a flag on `write()`.

**UPDATE-only (fail if missing).** `>>=` requires an object with a VID (implies it was read, therefore exists). But the write-back through `->` is still upsert — if the row was deleted between read and write, it's silently re-inserted. No atomic "update where exists" check.

**Conditional write (compare-and-swap).** Can't express `UPDATE ... WHERE version = 5`. The read in `>>=` gets the object with its current state, but the write-back carries no condition. Two concurrent `>>=` calls race — last write wins. No optimistic locking.

**Cross-row computed updates.** `*vid/+ >>= [age=>+1]` — each row gets the SAME `+1` applied to its own value. Fine. But can't express "set rank = row_number()" or "set total = SUM(amount) from sibling rows" — computations that cross row boundaries.

**Conditional bulk delete.** Can't express `DELETE FROM users WHERE age > 30` without first reading matching rows to get their VIDs, then writing `noobj` to each. The read is mandatory.

**Atomic bulk operation.** `>>=` over a wildcard expands to N individual reads + N individual writes. No way to express "update all these rows or none." Each write is independent.

**Schema changes.** No primitive for `ALTER TABLE`, `CREATE INDEX`, etc. These remain outside the `->` / `>>=` model.

### The Expressivity Gap Summary

The two primitives cover the CRUD surface (Create, Read, Update, Delete) but cannot express:

- **Existence assertions** (insert-only, update-only)
- **Atomic conditions** (optimistic locking, compare-and-swap)
- **Cross-row computations** (aggregates, window functions)
- **Transactional batching** (all-or-nothing across multiple writes)

These are not necessarily problems — just the boundary. Adding a third primitive (e.g., a conditional write or a batch boundary) would expand expressivity but violate the "as few ways as possible" constraint.

---

## 2. Boundaries of Optimization

### What CAN be optimized

**Single-statement precision writes.** `vid/field -> val` — the space receives a write to a precise VID. For tbleSpace, `table/1/name -> 'Alice'` maps directly to `UPDATE table SET name = ? WHERE pk = ?` — one SQL statement, no read needed. Already working.

**Literal-only update → skip the read.** When `>>=` is invoked with a rec containing only literal values (no `auto_from`, no computations on `_`), the read step returns data that is never used. In theory, the system could detect this:

```
*db:users/1 >>= [name=>'Alice', age=>30]
```
Here `name=>'Alice'` and `age=>30` are literals. The read of `*db:users/1` serves no purpose — the result could be synthesized. But detecting this requires the update instruction to inspect the RHS rec for `auto_from`/computation instructions. The space-level `write()` already handles this correctly — it just gets the final rec and writes it.

**Batch writes via JDBC batching.** If the space receives multiple `write()` calls in rapid succession (e.g., from poly unrolling), it could batch them into prepared statement batches. But the space has no signal for "batch begins" / "batch ends."

**Column pruning on read.** When `>>=` only touches a subset of fields, the read could `SELECT` only those columns + PK instead of `SELECT *`. But the space doesn't know which fields the update will touch — it just gets a read request for a VID.

**KV schema upsert → true SQL upsert.** `TypedKeyValueSchema` already uses `ON CONFLICT DO UPDATE` / `REPLACE INTO` — the SQL layer handles existence checking in one statement rather than check-then-write.

### What CANNOT be optimized

**Computed updates → server-side SQL.** `*db:users/1 >>= [age=>+1]` — the `+1` is an mtron instruction evaluated at the mtron layer. The space receives `age=31` (the computed result), not `age + 1` (the computation). Can't push to SQL as `UPDATE users SET age = age + 1` unless the `+1` instruction is recognized and translated into a SQL expression rewrite.

**Bulk update → single SQL.** `*db:users/+ >>= [active=>false]` — poly unrolling produces one `write(vid, rec)` per row. The space sees N individual writes. Without a "batch update" primitive that says "apply this transformation to all rows matching this pattern," the space can't collapse this to `UPDATE users SET active = false`. The information that ALL rows get the SAME transformation exists at the poly layer but is lost by the time individual `write()` calls reach the space.

**Heterogeneous bulk updates.** Even if we had a batch primitive, `*db:users/+ >>= [age=>+1]` where each row has a different starting age produces different results per row. Each write is unique — no SQL collapsing possible.

**Transactional grouping.** Multiple `write()` calls within a thread could be wrapped in a single JDBC transaction, but the space has no way to know which writes form a logical group. Without explicit transaction boundaries, each `write()` is its own transaction (autocommit).

**Cross-space atomicity.** `>>=` reads from one space, writes to the same space. Can't express an atomic write across two spaces (e.g., decrement inventory in one DB, create order in another).

### The Optimization Ceiling (Revised)

The space sees `write(vid, obj)` / `read(pattern)`. But the **mtron layer is a compiler, not just an interpreter**. Instruction chains can be recognized and fused BEFORE reaching the space.

**Already working:** `db:person/+.where[age>?20]` compiles to `SELECT * FROM person WHERE age > 20` — a single SQL statement, not N reads + filter.

**Same fusion for update chains:**
```
*db:person/+.where[age=>?>20].update[age=>+1]
→ UPDATE person SET age = age + 1 WHERE age > 20
```
This never expands to N individual writes. The entire chain compiles to one SQL statement pushed to the database.

### Fusion Boundary

**Fusable** — every instruction in the chain has a SQL translation:
```
*db:person/+.update[active=>false]         → UPDATE person SET active = false
*db:person/+.where[age>?20].update[age=>+1] → UPDATE person SET age = age + 1 WHERE age > 20
db:person/+/age -> 0                       → UPDATE person SET age = 0
*db:person/1.update[name=>'Alice']          → UPDATE person SET name = 'Alice' WHERE id = 1
```

**Not fusable** — any instruction lacks a SQL translation:
```
*db:person/+.update[age=>foo(_)]           // foo is custom mtron, no SQL equivalent
*db:person/+.where[crossSpacePred]         // predicate involves another space
*db:person/+.where[age>?20]
   .update[age=>+(lookup(other:space))]    // cross-space value computation
```

### Where the optimization lives

The fusion happens at the **mtron algebra layer** (instruction rewrites / `CommonRewrites`), not at the space. The space still just sees `read(pattern)` and `write(vid, obj)`. But with instruction fusion, the space sees a **single write call with a wildcard pattern** instead of N individual writes. Or, for update fusion, the space could see a new kind of call — an update-with-expression — if we choose to expose it at the `TableSchema` level.

The key architectural question: does fusion produce:
- A) A single `write(pattern, obj)` where `pattern` carries the wildcard? (reuses existing interface)
- B) A new `update(pattern, expression)` where `expression` is a SQL-compilable tree? (new interface)

Option A fits the existing `Space.write()` contract but requires the space to handle wildcard writes. Option B adds API surface but gives the space maximum optimization flexibility.

---

## 3. Implications for tbleSpace Update Design

### What we MUST get right (non-negotiable)

1. **VIDs on every returned object.** Without routable VIDs, `>>=` is broken — the write-back can't find the space.

2. **`selfVID()` in the read path.** Never `vid()` — read must not trigger write.

### What falls out naturally

- **Precision writes** (`->` to nested paths) already work through the existing `directWriter` → `writeField`/`writeRow` path.
- **Field-level delete** (`field -> noobj`) already works — `writeField` with `noobj` maps to `SET col = NULL`.
- **Row-level delete** (`row -> noobj`) needs `TableSchema.delete()` to be implemented (currently throws `UnsupportedOperationException`).

### What's debatable (design choices)

- **updateRow and absent fields**: When `>>= [age=>none]` produces a rec without `age`, should `updateRow` NULL the column or leave it unchanged? The poly layer says "remove means delete." For consistency with `-> noobj`, it should NULL.

- **Bulk update optimization**: Worth adding? A `TableSchema.updateWhere(pattern, rec)` that generates `UPDATE table SET ... WHERE ...` would collapse N writes to 1 SQL statement. But it adds a method to the interface.

- **Literal-only update detection**: Worth detecting that `>>= [name=>'Alice']` doesn't need the read? Could be done at the poly layer by scanning for `auto_from`/`_` references. If none found, skip the read and go straight to write.
