## Table Schema Behavior Matrix

Metatron uses structural typing: `[name=>'marko', age=>29, skill=>'x']` IS a valid
`[name=>str::T, age=>int::T]` — extra fields don't invalidate the type.  tbleSpace
follows this principle.  There is no "locked" vs "open" distinction in the database
layer; `addColumnOnTheFly` always fires for unknown columns.

The only enforcement gate is `typeAndValidate`: before INSERT/UPDATE, the rec is typed
via `rec.tid(tableType.vid())`.  If the required fields don't match (missing, wrong
type), metatron throws.  If they match, the write proceeds.

| # | Mode | Declared type | Action | Expected |
|---|---|---|---|---|
| A1 | Auto | None | Write `{name=>'a', age=>29}` | Table created. Schema auto-inferred. `URI_TYPE.maybe()` wildcard auto-added. PK `maybe()`. |
| A2 | Auto | None (after A1) | Write `{name=>'b', age=>30, skill=>'x'}` | `skill` added via ALTER TABLE. Schema refreshed with `skill=>str::T`. |
| A3 | Auto | None (after A1) | Write `{name=>'c', age=>'old'}` | `typeAndValidate` rejects — `str("old")` doesn't match `int::T`. |
| A4 | Auto | None (after A1) | Write `{name=>'d'}` | `typeAndValidate` rejects — missing required `age`. |
| D1 | Declared | `[name=>str::T, age=>int::T]` | Write `{name=>'a', age=>29}` | Table created from declared columns. Type check passes. |
| D2 | Declared | same (after D1) | Write `{name=>'b', age=>30, skill=>'x'}` | `skill` added via ALTER TABLE. Schema refreshed — the declared type grows organically with the data. |
| D3 | Declared | same (after D1) | Write `{name=>'c', age=>'old'}` | `typeAndValidate` rejects — `str("old")` doesn't match `int::T`. |
| D4 | Declared | same (after D1) | Write `{name=>'d'}` | `typeAndValidate` rejects — missing required `age`. |

### Key design decisions

- **Structural typing**: Extra fields are always accepted. Metatron says `[name=>str, age=>int]` matches `[name, age, skill]` — tbleSpace agrees.
- **No locked/open distinction in SQL**: `addColumnOnTheFly` always fires. The only gate is metatron's `rec.tid(typeVID)`.
- **Declared types protected**: `onTableChanged` skips refresh when a user-declared type exists. The user updates the type manually via collection-path declaration.
- **Auto types refreshed**: `onTableChanged` always fires for auto-generated types (they have a wildcard → `hasWildcard` = true → no skip).
