---
name: math instruction set
description: numeric constants, dates, time, space, and currency.
---

# math instruction set (`/m/math`)

The math instset provides numeric constants, unit-of-measurement types, and datetime handling. All types are registered
under `/m/math/+` and available via the standard type resolution system.

**************************************************************************

## types

| vid             | tid             | description                 |
|-----------------|-----------------|-----------------------------|
| `datetime::T`   | `uri::T`        | calendar datetime URI       |
| *************** | *************** | *************************** |
| `time::T`       | `real::T`       | time unit base              |
| `millis::T`     | `time::T`       | millisecond unit            |
| `second::T`     | `time::T`       | second unit (1000 millis)   |
| `minute::T`     | `time::T`       | minute unit (60 seconds)    |
| `hour::T`       | `time::T`       | hour unit (60 minutes)      |
| `day::T`        | `time::T`       | day unit (24 hours)         |
| *************** | *************** | *************************** |
| `datasize::T`   | `real::T`       | data size base              |
| `bB::T`         | `datasize::T`   | byte unit                   |
| `kB::T`         | `datasize::T`   | kilobyte (1024 bytes)       |
| `mB::T`         | `datasize::T`   | megabyte (1024 kB)          |
| `gB::T`         | `datasize::T`   | gigabyte (1024 mB)          |
| `tB::T`         | `datasize::T`   | terabyte (1024 gB)          |
| `pB::T`         | `datasize::T`   | petabyte (1024 tB)          |
| *************** | *************** | *************************** |
| `currency::T`   | `real::T`       | currency base               |
| `usd::T`        | `currency::T`   | united states currency      |
| `euro::T`       | `currency::T`   | european union currency     |

### dateTime (`/m/math/datetime`)

#### structure

`datetime::T` is a `uri::T` refinement with this layout:

```mtron
//yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm
```

| component | uri field       | range   | description     |
|-----------|-----------------|---------|-----------------|
| year      | host (pre-`.`)  | 0000+   | calendar year   |
| month     | host (post-`.`) | 01–12   | month           |
| day       | port            | 01–31   | day of month    |
| hour      | path[-4]        | 00–23   | hour            |
| minute    | path[-3]        | 00–59   | minute          |
| second    | path[-2]        | 00–59   | second          |
| millis    | path[-1]        | 000–999 | millisecond     |
| tz        | q `tz`          | ±HHMM   | timezone offset |

#### construction

```mtron_pre
[-- current system time --]
datetime_now()

[-- from record (goes through .as(uri::T) first) --]
[host=><2024.12>,port=>25,path=>[<>,<09>,<00>,<00>,<000>],/
 c=>[min=>1,max=>1],q=>[tz=>'-0500']].as(uri::T).as(datetime::T)

[-- from string-encoded URI --]
<//2024.12:25/09/00/00/000?tz=-0500>.as(datetime::T)
```

#### typed vs bare uris

Bare URIs like `<//2024.12:25/...>` work with standard URI operations (`>>host`, `>>port`, `>>path`). The datetime
vocabulary (`year`, `month`, `day`, etc.) only works on explicitly typed datetimes:

```mtron_pre
[-- rec projections of uri components --]
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)>>host
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)>>port

[-- vocabulary projections require datetime::T typing --]
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>year
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>month
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>day
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>tz
```

#### vocabulary keys

Named `>>` projections for typed datetimes:

| key      | value type | source             |
|----------|------------|--------------------|
| `year`   | `int::T`   | host.split(".")[0] |
| `month`  | `int::T`   | host.split(".")[1] |
| `day`    | `int::T`   | port               |
| `hour`   | `int::T`   | path[-4]           |
| `minute` | `int::T`   | path[-3]           |
| `second` | `int::T`   | path[-2]           |
| `millis` | `int::T`   | path[-1]           |
| `tz`     | `str::T`   | q("tz")            |

Non-vocabulary keys fall through to standard URI projections.

```mtron_pre
datetime::<//2024.12:25/09/00/00/000?tz=-0500>>>{year,month,day}
datetime::<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)>>host
```

#### predicate validation

The `datetime::T` predicate ensures base uri has:

- no scheme (or empty)
- host matches `\d{4}\.\d{2}` with month 01–12
- port in 1–31
- path has ≥4 integer segments with valid hour (0–23), minute (0–59), second (0–59)
- query contains `tz` key

```mtron_pre
<//2024.13:25/09/00/00/000?tz=-0500>.?datetime::T [-- month 13 (bad)   --]
<//2024.12:25/09/60/00/000?tz=-0500>.?datetime::T [-- second 60 (bad)  --]
<//2024.12:25/09/00/00>.?datetime::T              [-- missing tz (bad) --]
```

#### mutating and filtering

All standard uri operations apply: `==` (select), `=?=` (where), plus `>>=` (rec update) after `.as(rec::T)`.

```mtron_pre
[-- select mutation: change day --]
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)==[port=>31]>>port

[-- where filter: match day 25 --]
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)=?=[port=>25]
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)=?=[port=>26]

[-- rec update: change timezone --]
<//2024.12:25/09/00/00/000?tz=-0500>.as(rec::T)>>=[q=>[tz=>'+0000']]>>q>>tz
```

### time (`/m/math/time`)

`time::T` is a `real::T` refinement. The time unit types (`millis::T` … `day::T`) convert to each other via `.as()`; a
conversion preserves the total millis, changing only the unit label.

#### conversion

```mtron_pre
[-- upward: value shrinks, unit grows --]
millis::1000.0.as(second::T)
second::60.0.as(minute::T)
minute::60.0.as(hour::T)
hour::24.0.as(day::T)

[-- downward: value grows, unit shrinks --]
second::1.0.as(millis::T)
minute::1.0.as(second::T)
hour::1.0.as(minute::T)
day::1.0.as(hour::T)

[-- multi-step: levels can be skipped --]
millis::3600000.0.as(hour::T)
millis::86400000.0.as(day::T)

[-- fractional values are preserved --]
hour::36.0.as(day::T)
second::90.0.as(minute::T)
day::1.5.as(hour::T)

[-- bare real: re-label only, no conversion --]
30000.0.as(minute::T)
```

A converted value tests as its target unit:

```mtron_pre
millis::1000.0.as(second::T).matches(second::T)
```

Time units require a real-backed value — an int-backed time is a type violation:

```mtron_pre
day::2.as(millis::T)         [-- int-backed time (bad) --]
hour::2.as(minute::T)        [-- int-backed time (bad) --]
```

#### relational operators

`eq`, `neq`, `lt`, `gt`, `lte`, and `gte` auto-convert across units before comparing:

```mtron_pre
[-- equality across units --]
millis::1000.0.eq(second::1.0)
second::60.0.eq(minute::1.0)
minute::60.0.eq(hour::1.0)
day::1.0.eq(hour::24.0)

[-- ordering across units --]
second::60.0.gt(millis::500.0)
hour::12.0.lt(day::1.0)
minute::60.0.lte(hour::1.0)
minute::60.0.gte(hour::1.0)
```

#### normalize

`normalize()` cascades upward while the value reaches ~2× the next larger unit, until stable:

| from   | threshold | to     |
|--------|-----------|--------|
| millis | ≥ 2000    | second |
| second | ≥ 120     | minute |
| minute | ≥ 120     | hour   |
| hour   | ≥ 48      | day    |

```mtron_pre
[-- below threshold: unchanged --]
millis::1500.0.normalize()

[-- cascade until stable --]
millis::9000.0.normalize()
minute::150.0.normalize()
hour::72.0.normalize()
```

### datasize (`/m/math/datasize`)

`datasize::T` is a `real::T` refinement. The data size unit types (`bB::T` … `pB::T`) convert to each other via `.as()`
at a 1024 (binary) base; a conversion preserves the total bytes, changing only the unit label.

#### conversion

```mtron_pre
[-- upward: value shrinks, unit grows --]
bB::1024.0.as(kB::T)
kB::1024.0.as(mB::T)
mB::1024.0.as(gB::T)
gB::1024.0.as(tB::T)
tB::1024.0.as(pB::T)

[-- downward: value grows, unit shrinks --]
kB::1.0.as(bB::T)
mB::1.0.as(kB::T)
gB::1.0.as(mB::T)
tB::1.0.as(gB::T)
pB::1.0.as(tB::T)

[-- multi-step: levels can be skipped --]
bB::1073741824.0.as(gB::T)
bB::1125899906842624.0.as(pB::T)
kB::1099511627776.0.as(pB::T)

[-- larger values, both directions --]
kB::2048.0.as(mB::T)
mB::2.0.as(kB::T)

[-- bare real: re-label only, no conversion --]
1024.0.as(kB::T)
```

A converted value tests as its target unit:

```mtron_pre
bB::1024.0.as(kB::T).matches(kB::T)
```

#### relational operators

```mtron_pre
[-- equality across units --]
bB::1024.0.eq(kB::1.0)
bB::1048576.0.eq(mB::1.0)
gB::1024.0.eq(tB::1.0)
tB::1024.0.eq(pB::1.0)

[-- ordering of equal values --]
kB::1024.0.lte(mB::1.0)
kB::1024.0.gte(mB::1.0)
```

note: relational operators on non-exact unit conversions are a known bug (see the TODOs in `mathInstSetTest`).

#### normalize

`normalize()` cascades upward while the value reaches ~2× the next larger unit, until stable:

| from | threshold | to   |
|------|-----------|------|
| bB   | ≥ 2048    | kB   |
| kB   | ≥ 2048    | mB   |
| mB   | ≥ 2048    | gB   |
| gB   | ≥ 2048    | tB   |
| tB   | ≥ 2048    | pB   |

```mtron_pre
[-- below threshold: unchanged --]
bB::1500.0.normalize()
kB::1000.0.normalize()

[-- cascade until stable --]
bB::1048576.0.normalize()
kB::2048.0.normalize()
tB::2048.0.normalize()
```

**************************************************************************

## instructions

| instruction            | signature           | description             |
|------------------------|---------------------|-------------------------|
| `datetime_now()`       | `# → datetime::T`   | current system datetime |
| `normalize` (time)     | `time::T → time::T` | auto-scale time unit    |
| `normalize` (data)     | `datasize::T → datasize::T` | auto-scale data unit    |
| `cos`, `sin`, `tan`    | `real::T → real::T` | trig functions          |
| `sqrt`, `pow`, `log`   | `real::T → real::T` | math functions          |
| `abs`, `ceil`, `floor` | `real::T → real::T` | rounding functions      |

**************************************************************************

## constants

| constant | value             |
|----------|-------------------|
| `pi`     | 3.141592653589793 |
| `e`      | 2.718281828459045 |
