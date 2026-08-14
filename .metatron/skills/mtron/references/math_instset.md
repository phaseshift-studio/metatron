---
name: math-instset
description: The /m/math instruction set — numeric constants, unit-of-measurement types (millis, kB, datetime), and the type registry.
---

# Math Instruction Set (`/m/math`)

The math instset provides numeric constants, unit-of-measurement types, and datetime handling. All types are registered under `/m/math/...` and available via the standard type resolution system.

## Type Registry

| Type | Base   | VID                     | Description                          |
|------|--------|-------------------------|--------------------------------------|
| `millis::T`  | `real::T` | `/m/math/time/millis`  | Millisecond unit                     |
| `second::T`  | `real::T` | `/m/math/time/second`  | Second unit (1000 millis)            |
| `minute::T`  | `real::T` | `/m/math/time/minute`  | Minute unit (60 seconds)             |
| `hour::T`    | `real::T` | `/m/math/time/hour`    | Hour unit (60 minutes)               |
| `bB::T`      | `real::T` | `/m/math/data/bB`      | Byte unit                            |
| `kB::T`      | `real::T` | `/m/math/data/kB`      | Kilobyte (1024 bytes)                |
| `mB::T`      | `real::T` | `/m/math/data/mB`      | Megabyte (1024 kB)                   |
| `gB::T`      | `real::T` | `/m/math/data/gB`      | Gigabyte (1024 mB)                   |
| `tB::T`      | `real::T` | `/m/math/data/tB`      | Terabyte (1024 gB)                   |
| `pB::T`      | `real::T` | `/m/math/data/pB`      | Petabyte (1024 tB)                   |
| `datetime::T`| `uri::T`  | `/m/math/datetime`     | Calendar datetime URI                |

## Unit Conversions

All unit types (time, data) support bidirectional conversion via `.as()`:

```mtron
millis::60000.0.as(minute::T)    → minute::1.0
hour::1.5.as(minute::T)          → minute::90.0
kB::1024.0.as(mB::T)             → mB::1.0
gB::2.0.as(mB::T)                → mB::2048.0
```

Equality comparisons auto-convert:

```mtron
millis::1000.0.eq(second::1.0)   → true
second::60.0.gt(millis::500.0)   → true
```

## DateTime (`/m/math/datetime`)

### Structure

`datetime::T` is a `uri::T` refinement with this layout:

```
//yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm
```

| Component | URI field | Range    | Description              |
|-----------|-----------|----------|--------------------------|
| year      | host (pre-`.`) | 0000+ | Calendar year            |
| month     | host (post-`.`)| 01–12  | Month                    |
| day       | port      | 01–31    | Day of month             |
| hour      | path[-4]  | 00–23    | Hour                     |
| minute    | path[-3]  | 00–59    | Minute                   |
| second    | path[-2]  | 00–59    | Second                   |
| millis    | path[-1]  | 000–999  | Millisecond              |
| tz        | q `tz`    | ±HHMM    | Timezone offset          |

### Construction

```mtron
// Current system time
datetime_now()

// From record (goes through .as(uri::T) first)
[host=><2024.12>,port=>25,path=>[<>,<09>,<00>,<00>,<000>],
 c=>[min=>1,max=>1],q=>[tz=>'-0500']].as(uri::T).as(datetime::T)

// From string-encoded URI
<//2024.12:25/09/00/00/000?tz=-0500>.as(datetime::T)
```

### Typed vs Bare URIs

Bare URIs like `<//2024.12:25/...>` work with standard URI operations (`>>host`, `>>port`, `>>path`). The datetime vocabulary (`year`, `month`, `day`, etc.) only works on explicitly typed datetimes:

```mtron
// Standard URI projections work on bare URIs
<//2024.12:25/09/00/00/000?tz=-0500>>>host       → <2024.12>
<//2024.12:25/09/00/00/000?tz=-0500>>>port       → 25

// Vocabulary projections require datetime:: prefix
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>year    → 2024
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>month   → 12
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>day     → 25
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>tz      → '-0500'
```

### Vocabulary Keys

Named `>>` projections for typed datetimes:

| Key      | Returns | Source          |
|----------|---------|-----------------|
| `year`   | `int`   | host.split(".")[0] |
| `month`  | `int`   | host.split(".")[1] |
| `day`    | `int`   | port            |
| `hour`   | `int`   | path[-4]        |
| `minute` | `int`   | path[-3]        |
| `second` | `int`   | path[-2]        |
| `millis` | `int`   | path[-1]        |
| `tz`     | `str`   | q("tz")         |

Non-vocabulary keys fall through to standard URI projections.

```mtron
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>{year,month,day}  → {2024,12,25}
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>host               → <2024.12>
```

### Predicate Validation

The Java predicate validates:
- No scheme (or empty)
- Host matches `\d{4}\.\d{2}` with month 01–12
- Port in 1–31
- Path has ≥4 integer segments with valid hour (0–23), minute (0–59), second (0–59)
- Query contains `tz` key

```mtron
<//2024.13:25/09/00/00/000?tz=-0500>.matches(datetime::T)   → false  // month 13
<//2024.12:25/09/60/00/000?tz=-0500>.matches(datetime::T)   → false  // second 60
<//2024.12:25/09/00/00>.matches(datetime::T)                → false  // missing tz
```

### Mutation & Filtering

All standard URI operations apply: `==` (select), `=?=` (where), plus `>>=` (rec update) after `.as(rec::T)`.

```mtron
// Select mutation: change day
<//2024.12:25/09/00/00/000?tz=-0500>==[port=>31]>>port      → 31

// Where filter: match day 25
<//2024.12:25/09/00/00/000?tz=-0500>=?=[port=>25]           → <//2024.12:25/...>
<//2024.12:25/09/00/00/000?tz=-0500>=?=[port=>26]           → noobj

// Rec update: change timezone
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)>>=[q=>[tz=>'+0000']]>>q>>tz   → '+0000'
```

## Instructions

| Instruction            | Signature              | Description              |
|------------------------|------------------------|--------------------------|
| `datetime_now()`       | `# → datetime::T`     | Current system datetime  |
| `normalize` (time)    | `time::T → time::T`   | Auto-scale time unit     |
| `normalize` (data)    | `data::T → data::T`   | Auto-scale data unit     |
| `cos`, `sin`, `tan`   | `real → real`         | Trig functions           |
| `sqrt`, `pow`, `log`  | `real → real`         | Math functions           |
| `abs`, `ceil`, `floor`| `real → real`         | Rounding functions       |

## Constants

| Constant | Value              |
|----------|--------------------|
| `pi`     | 3.141592653589793  |
| `e`      | 2.718281828459045  |
