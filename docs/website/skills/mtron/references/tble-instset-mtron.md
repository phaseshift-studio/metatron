---
name: tble instruction set
description:
  The `/m/tble` instruction set and the `tblespace::T` it belongs to:
    a JDBC relational database mounted as a metatron space — tables that appear from the first rec write, typed rows, the rewrite family that pushes reads down into SQL, the key/value fall-through, `auto_from` foreign keys, and native `sql()`. TRIGGER: When connecting a database (SQLite, PostgreSQL, MariaDB, MySQL), writing or reading table rows in mtron, wondering whether a read was pushed down to SQL, mapping a row cell to a `!*` pointer, or asking what a table's schema is.
---

# tble instruction set (`/m/tble`)

A relational database is a space, a table is a collection, and a row is a `rec::T` whose vid **is** its primary key.
That is the whole idea, and it is why `/m/tble` is small: *one instruction* — `sql` — and a family of rewrites that
turn a mtron read into the SQL the backend is already good at. Everything else is the space protocol you already know:
write with `->`, read with `*`, walk the graph.

`tblespace::T` is a JDBC-backed `space::T` (SQLite, PostgreSQL, MariaDB and MySQL are the tested backends). It is
**dual-path**: a collection that names a table routes to a SQL table; every other collection falls through to a typed
key/value store in the same database.

## the space these examples use

Every block below runs against one sqlite database mounted as `tbledoc:#`. (`mtron_pre` blocks are executed by the
docs pipeline and inlined with their results; a plain `mtron` block is only shown.)

```mtron
mtron> import(/m/tble)
==>instset::[
    pattern=>/m/tble/#,
    q=>[docq::[
    pattern=>docq,
    pre_read=>inst?#{*}<=#{?}(uri::T),
    pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
    obj=>memspace::[pattern=><#>],
    inst=>instset::[pattern=><#>]]],
    space=>[super=>!*/m],
    const=>[[llm_chat_schema=>inst?#{*}<=#{*}()]@helper],
    type=>[tblespace::T],
    inst=>[sql?rec{*}<=tblespace(str::T)],
    rewrite=>[/m/tble/inst/rewrite/sql_count?code{?}<=code(),/m/tble/inst/rewrite/sql_sum?code{?}<=code(),/m/tble/inst/rewrite/sql_mean?code{?}<=code(),/m/tble/inst/rewrite/sql_limit?code{?}<=code(),/m/tble/inst/rewrite/sql_offset?code{?}<=code(),/m/tble/inst/rewrite/sql_offset_limit?code{?}<=code(),/m/tble/inst/rewrite/sql_where?code{?}<=code(),/m/tble/inst/rewrite/sql_where_count?code{?}<=code(),/m/tble/inst/rewrite/sql_where_order?code{?}<=code(),/m/tble/inst/rewrite/sql_where_order_offset?code{?}<=code(),...(8 more)]]@/m/tble
mtron> tblespace::[pattern => tbledoc:#,
                   host    => <sqlite:/tmp/mtron-tbledoc.db>,
                   driver  => <org.sqlite.JDBC>,
                   q       => [incrq::[=>],
                               docq::[tbledoc:person => docs::[
                                 desc    => "a person row: name, age, and a skill once one is written",
                                 example => ["*tbledoc:person/+.count()"]]]],
                   route   => [tbledoc: => <>]]@/sys/space/tbledoc
==>tblespace::[
    pattern=>tbledoc:#,
    host=><sqlite:/tmp/mtron-tbledoc.db>,
    driver=><org.sqlite.JDBC>,
    q=>[
     incrq::[
      pattern=>incrq,
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     docq::[
      pattern=>docq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>memspace::[pattern=><#>],
      inst=>instset::[pattern=><#>]]],
    route=>[tbledoc:=><>]]@/sys/space/tbledoc
```
The two `q` entries are the space's **query processors**, and both earn their place: `docq` is what lets *a space
document its own tables* — so `?docq` on a table tells an agent what it is looking at before it queries it — and `incrq`
is what makes a `_?incrq` write assign its own key (see *keys the database assigns* below).

## types

| type           | vid                                  | tid        | meaning                                                             |
|----------------|--------------------------------------|------------|---------------------------------------------------------------------|
| `tblespace::T` | `/m/tble/space/tblespace`            | `space::T` | a JDBC database as a space; isa `rec[host=>uri::T,driver=>uri::T]`  |
| table types    | `/sys/space/<space>/instset/<table>` | `rec::T`   | one per discovered table, published into the space's schema instset |

```mtron
mtron> *tblespace?docq      [-- the type, its ctor, and the space's own summary --]
==>docs::[
    obj=>tblespace::T,
    desc=>'[structural] a metatron realization of a relational database',
    example=>['*<http://metatron.phaseshift.studio/skills/mtron/references...']]
mtron> *</m/tble/helper>    [-- the instset's helper rec: the llm chat schema, as a str --]
==>[llm_chat_schema=>inst?#{*}<=#{*}()]
```
## configuration

| key          | type     | meaning                                                                                                              |
|--------------|----------|----------------------------------------------------------------------------------------------------------------------|
| `pattern`    | `uri::T` | the uri subtree this space owns — `tbledoc:#`                                                                        |
| `host`       | `uri::T` | the JDBC url **without** its `jdbc:` prefix: `<sqlite:/tmp/x.db>`, `<postgresql://host:5432/db?user=..&password=..>` |
| `driver`     | `uri::T` | the JDBC driver class — `<org.sqlite.JDBC>`, `<org.postgresql.Driver>`, `<org.mariadb.jdbc.Driver>`                  |
| `route`      | `rec::T` | scheme → path inside the space: `[tbledoc: => <>]` strips the scheme; a value can point a subtree elsewhere          |
| `q`          | `rec::T` | query processors — `incrq::[=>]` for `_?incrq` key assignment, `docq` to document tables (above)                     |
| `serializer` | `uri::T` | the serializer the key/value side uses; defaults to mtron's own                                                      |
| `table`      | `lst::T` | **deprecated and ignored** — table mapping is always on (older boot files still carry `table => [,]`)                |

Two practical notes: sqlite will not create the parent directory of the database file, so it must already exist; and
mariadb/mysql hosts carry their credentials in the uri. A space may also be built by the ctor alone —
`tblespace::[host=>..,driver=>..]` — and mounted at any vid.

## writing rows — the table appears from the first write

There is no DDL to write. The first `rec` written to a collection creates the table from the rec's fields; the vid's
last segment becomes the primary key; and a field the table has never seen widens the table (`ALTER TABLE`) and the
collection's type with it.

```mtron
mtron> tbledoc:person/1 -> [name=>'marko',age=>29]
==>[name=>'marko',age=>29]
mtron> tbledoc:person/2 -> [name=>'grant',age=>25]
==>[name=>'grant',age=>25]
mtron> tbledoc:person/3 -> [name=>'metis',age=>41]
==>[name=>'metis',age=>41]
mtron> tbledoc:person/4 -> [name=>'xilo',age=>33,skill=>'graph']    [-- a field the table has not seen --]
==>[name=>'xilo',age=>33,skill=>'graph']
```
SQL column types follow the obj types, and the column catalog is readable as data — `_mtron_meta` is where metatron
keeps what SQL cannot say:

```mtron
mtron> */sys/space/tbledoc.sql('SELECT table_name, column_name, base_vid, obj_tid, ref_table FROM _mtron_meta')
==>[
    table_name=>'person',
    column_name=>'$table',
    base_vid=>'/m/rec',
    obj_tid=>'/m/rec']
==>[
    table_name=>'person',
    column_name=>'skill',
    base_vid=>'/m/str',
    obj_tid=>'/m/str']
==>[
    table_name=>'award',
    column_name=>'$table',
    base_vid=>'/m/rec',
    obj_tid=>'/m/rec']
==>[
    table_name=>'award',
    column_name=>'trophy',
    base_vid=>'/m/str',
    obj_tid=>'/m/str']
==>[
    table_name=>'award',
    column_name=>'recipient',
    base_vid=>'/m/inst',
    obj_tid=>'/m/inst/auto_from',
    ref_table=>'person']
==>[
    table_name=>'note',
    column_name=>'$table',
    base_vid=>'/m/rec',
    obj_tid=>'/m/rec']
==>[
    table_name=>'person',
    column_name=>'age',
    base_vid=>'/m/int',
    obj_tid=>'/m/int']
==>[
    table_name=>'person',
    column_name=>'name',
    base_vid=>'/m/str',
    obj_tid=>'/m/str{2}']
==>[
    table_name=>'note',
    column_name=>'body',
    base_vid=>'/m/str',
    obj_tid=>'/m/str']
```
A structural obj (`tags=>['a','b']`) rides in a `TEXT` column with its type preserved in `obj_tid`, so a read
reconstructs the lst rather than a string that looks like one.

## reading rows

```mtron
mtron> *tbledoc:person/+              [-- every row as a rec --]
==>[name=>'xilo',age=>33,skill=>'graph']
==>[name=>'marko',age=>29]
==>[name=>'metis',age=>41]
==>[name=>'grant',age=>25]
==>[name=>'vela',age=>53]
mtron> *tbledoc:person/1              [-- one row --]
==>[name=>'marko',age=>29]
mtron> *tbledoc:person/+/             [-- vid => row --]
==>tbledoc:person/4=>[name=>'xilo',age=>33,skill=>'graph']
==>tbledoc:person/1=>[name=>'marko',age=>29]
==>tbledoc:person/3=>[name=>'metis',age=>41]
==>tbledoc:person/2=>[name=>'grant',age=>25]
==>tbledoc:person/5=>[name=>'vela',age=>53]
mtron> *tbledoc:person/+/name         [-- one column, across rows --]
==>'metis'
==>'grant'
==>'vela'
==>'marko'
==>'xilo'
mtron> *tbledoc:person/1/name         [-- one field: the row is unrolled --]
==>'marko'
mtron> *tbledoc:person/+/id           [-- the keys: they live in the vid, not in the body --]
==>1
==>5
==>2
==>4
==>3
```
Rows come back in whatever order the backend chose — say so in the expression (`.order(select(col))`) when position
matters.

### anchor vs clone: who keeps the address

`*` is a **clone** and `@` is an **anchor**, and for a table row that is the difference between a value and an
*addressable* value. Take the fields out of the rows and the clone loses the row it came from:

```mtron
mtron> *tbledoc:person/+/+      [-- clone: fields detached from their rows --]
==>'metis'
==>41
==>'grant'
==>25
==>53
==>'vela'
==>33
==>29
==>'graph'
==>'marko'
==>'xilo'
mtron> @tbledoc:person/+/+      [-- anchor: the same fields, each still addressed to its row --]
==>'metis'@tbledoc:person/3/name
==>41@tbledoc:person/3/age
==>'grant'@tbledoc:person/2/name
==>25@tbledoc:person/2/age
==>53@tbledoc:person/5/age
==>'vela'@tbledoc:person/5/name
==>33@tbledoc:person/4/age
==>29@tbledoc:person/1/age
==>'graph'@tbledoc:person/4/skill
==>'marko'@tbledoc:person/1/name
==>'xilo'@tbledoc:person/4/name
```
The same holds for a rewrite's result — it is the clone path that hands rows back with a placeholder vid, which is why
the anchored form is the one to keep when the rows might be written back:

```mtron
mtron> *tbledoc:person/+.take(2)     [-- clone path --]
==>{2}[name=>'marko',age=>29]@tbledoc:person/noobj
==>{2}[name=>'grant',age=>25]@tbledoc:person/noobj
mtron> @tbledoc:person/+.take(2)     [-- anchor path --]
==>{2}[name=>'marko',age=>29]@tbledoc:person/noobj
==>{2}[name=>'grant',age=>25]@tbledoc:person/noobj
```
## the rewrites — reads that become SQL

The interesting half of `/m/tble`: a read that matches a table is rewritten to SQL before it runs, so `count()` is
`SELECT COUNT(*)`, `=?=` is `WHERE`, and a projection is a column list. The match is on the *code*, not the data — the
same expression over a `memspace` stays in mtron.

```mtron
mtron> *tbledoc:person/+.=?=[age=>?<30]        [-- SELECT * FROM person WHERE age < 30 --]
==>[name=>'marko',age=>29]@tbledoc:person/noobj
==>[name=>'grant',age=>25]@tbledoc:person/noobj
mtron> *tbledoc:person/+.count()               [-- SELECT COUNT(*) FROM person --]
==>5
mtron> *tbledoc:person/+/age.sum()             [-- SELECT SUM(age) FROM person --]
==>181.0000
mtron> *tbledoc:person/+/age.mean()            [-- SELECT AVG(age) FROM person --]
==>36.2000
mtron> *tbledoc:person/+.==[name=>_]           [-- SELECT name FROM person --]
==>[name=>'xilo']
==>[name=>'marko']
==>[name=>'metis']
==>[name=>'grant']
==>[name=>'vela']
mtron> *tbledoc:person/+.order(select(age))    [-- ... ORDER BY age --]
==>{2}[name=>'grant',age=>25]@tbledoc:person/noobj
==>{2}[name=>'marko',age=>29]@tbledoc:person/noobj
==>{2}[name=>'xilo',age=>33,skill=>'graph']@tbledoc:person/noobj
==>{2}[name=>'metis',age=>41]@tbledoc:person/noobj
==>{2}[name=>'vela',age=>53]@tbledoc:person/noobj
mtron> *tbledoc:person/+.dedup(select(name))   [-- SELECT DISTINCT name FROM person --]
==>{2}'marko'
==>{2}'grant'
==>{2}'metis'
==>{2}'xilo'
==>{2}'vela'
mtron> *tbledoc:person/+.take(2)               [-- ... LIMIT 2 --]
==>{2}[name=>'marko',age=>29]@tbledoc:person/noobj
==>{2}[name=>'grant',age=>25]@tbledoc:person/noobj
mtron> *tbledoc:person/+.skip(1)               [-- ... OFFSET 1 --]
==>{2}[name=>'grant',age=>25]@tbledoc:person/noobj
==>{2}[name=>'metis',age=>41]@tbledoc:person/noobj
==>{2}[name=>'xilo',age=>33,skill=>'graph']@tbledoc:person/noobj
==>{2}[name=>'vela',age=>53]@tbledoc:person/noobj
```
| rewrite                  | mtron                                   | sql                                                 |
|--------------------------|-----------------------------------------|-----------------------------------------------------|
| `sql_where`              | `*db:table/+/+.=?=[col=>pred]`          | `SELECT * ... WHERE`                                |
| `sql_where_count`        | `*db:table/+/+.=?=[col=>pred].count()`  | `SELECT COUNT(*) ... WHERE`                         |
| `sql_where_order`        | `...=?=[..].order(select(col))`         | `... WHERE ... ORDER BY`                            |
| `sql_where_order_offset` | `...=?=[..].order(select(col)).skip(n)` | `... ORDER BY ... OFFSET n`                         |
| `sql_where_limit`        | `...=?=[..].take(n)`                    | `... WHERE ... LIMIT n`                             |
| `sql_where_offset`       | `...=?=[..].skip(n)`                    | `... WHERE ... OFFSET n`                            |
| `sql_where_offset_limit` | `...=?=[..].skip(m).take(n)`            | `... WHERE ... LIMIT n OFFSET m`                    |
| `sql_select`             | `*db:table/+/+.==[col=>_]`              | `SELECT col ...`                                    |
| `sql_order`              | `*db:table/+/+.order(select(col))`      | `... ORDER BY col`                                  |
| `sql_distinct`           | `*db:table/+/+.dedup(select(col))`      | `SELECT DISTINCT col ...`                           |
| `sql_count`              | `*db:table/+/+.count()`                 | `SELECT COUNT(*)`                                   |
| `sql_sum`                | `*db:table/+/col.sum()`                 | `SELECT SUM(col)`                                   |
| `sql_mean`               | `*db:table/+/col.mean()`                | `SELECT AVG(col)`                                   |
| `sql_limit`              | `*db:table/+/+.take(n)`                 | `... LIMIT n`                                       |
| `sql_offset`             | `*db:table/+/+.skip(n)`                 | `... OFFSET n`                                      |
| `sql_offset_limit`       | `*db:table/+/+.skip(m).take(n)`         | `... LIMIT n OFFSET m`                              |
| `kv_count`               | `*db:kv/+.count()`                      | `SELECT COUNT(*) FROM kv_store WHERE furi LIKE ...` |
| `kv_limit`               | `*db:kv/+.take(n)`                      | `... LIMIT n` (broken on the typed schema — below)  |

The instset prints that list itself, and each rewrite documents its own before/after code:

```mtron
mtron> */m/tble?docq
==>docs::[
    obj=>instset::[
     pattern=>/m/tble/#,
     q=>[docq::[
    pattern=>docq,
    pre_read=>inst?#{*}<=#{?}(uri::T),
    pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
    obj=>memspace::[pattern=><#>],
    inst=>instset::[pattern=><#>]]],
     space=>[super=>!*/m],
     const=>[[llm_chat_schema=>inst?#{*}<=#{*}()]@helper],
     type=>[tblespace::T],
     inst=>[sql?rec{*}<=tblespace(str::T)],
     rewrite=>[/m/tble/inst/rewrite/sql_count?code{?}<=code(),/m/tble/inst/rewrite/sql_sum?code{?}<=code(),/m/tble/inst/rewrite/sql_mean?code{?}<=code(),/m/tble/inst/rewrite/sql_limit?code{?}<=code(),/m/tble/inst/rewrite/sql_offset?code{?}<=code(),/m/tble/inst/rewrite/sql_offset_limit?code{?}<=code(),/m/tble/inst/rewrite/sql_where?code{?}<=code(),/m/tble/inst/rewrite/sql_where_count?code{?}<=code(),/m/tble/inst/rewrite/sql_where_order?code{?}<=code(),/m/tble/inst/rewrite/sql_where_order_offset?code{?}<=code(),...(8 more)]]@/m/tble,
    desc=>'relational tables, typed rows, and SQL rewrites within the ...',
    example=>['*acme:customer.where[person=>[name=>_=>age=>?>29]]']]
```
## native sql

When the vocabulary is not enough, go to the backend. `sql` takes a `str` — templates included — and yields `rec{*}`,
which mtron then treats like any other objs.

```mtron
mtron> */sys/space/tbledoc.sql('SELECT name, age FROM person WHERE age < 30')
==>[name=>'marko',age=>29]
==>[name=>'grant',age=>25]
mtron> */sys/space/tbledoc.sql('SELECT name, age FROM person WHERE age < 30')>>age.sum()
==>54
```
A `sql` str may carry several statements: all but the last run as updates, the last is the query whose result set comes
back. Templates reach the graph, so a query can be parameterized by live state — the instruction's own example is
`sql('SELECT * FROM movie WHERE runtime < ${*next_event} - time(now)')`.

## foreign keys: a `!*` in the cell

`auto_from` is metatron's **structural** foreign key: the pointer is part of the row value, and it stays lazy until a
traversal forces the read. Where a `where`-join computes a relationship at query time, a `!*` cell *is* the
relationship.

```mtron
mtron> tbledoc:award/1 -> [trophy=>'gold',recipient=>!*tbledoc:person/1]
==>[
    trophy=>'gold',
    recipient=>!*tbledoc:person/1]
mtron> tbledoc:award/2 -> [trophy=>'silver',recipient=>!*tbledoc:person/2]
==>[
    trophy=>'silver',
    recipient=>!*tbledoc:person/2]
mtron> *tbledoc:award/+                [-- the cell renders as an unresolved pointer --]
==>[
    trophy=>'silver',
    recipient=>!*tbledoc:person/2]
==>[
    trophy=>'gold',
    recipient=>!*tbledoc:person/1]
mtron> *tbledoc:award/1/recipient      [-- traversal resolves it to the row --]
==>[name=>'marko',age=>29]
```
The SQL footprint is deliberately plain: the column is a bare `INTEGER` (`TEXT` for string keys), there is no SQL
`FOREIGN KEY` constraint, and the pointer metadata — including `ref_table` — lives in `_mtron_meta`. An intra-space
pointer stores the bare table name; an inter-space pointer (`!*mem:venue/1` into a `memspace`, a `grphspace`, an
fsSpace…) keeps its scheme and routes through the router, so a row can point anywhere in the metatron graph. Both
kinds may sit in one row.

## the schema of what you are querying

```mtron
mtron> */sys/space/tbledoc/schema/pattern      [-- where the discovered types are published --]
==>/sys/space/tbledoc/instset/#
mtron> */sys/space/tbledoc/instset/+/          [-- the types as addressed objs --]
==>/sys/space/tbledoc/instset/award=>rec::T[?[{?}trophy=>str::T,recipient=>isa(person/+/id).!*id(),uri{?}::T=>#::T]]@/sys/space/tbledoc/instset/award
==>/sys/space/tbledoc/instset/note=>rec::T[?[{?}body=>str::T,uri{?}::T=>#::T]]@/sys/space/tbledoc/instset/note
==>/sys/space/tbledoc/instset/person=>rec::T[?[{?}name=>str::T,{?}age=>int::T,{?}skill=>str::T,uri{?}::T=>#::T]]@/sys/space/tbledoc/instset/person
```
Reading the schema is how you meet a table you have never seen: each entry is an `isa([{?}name=>str::T,…])` refinement
of `rec::T`, and a foreign-key column appears as `recipient=>isa(person/+/id).!*id()` — the column *is* the pointer.
A table's type is replaced when the table widens.

Discovery is **lazy**, and it is worth knowing exactly what fires it: the catalog walk runs on the first *write*, or on
a read of the space's instset (`*/sys/space/<space>/instset/+/`). Until it has run, the space does not know which
collections are tables, so a table read falls through to the key/value side and comes back empty — a freshly mounted
space answers `0` to a count that the database file can prove is not zero (see *taking the space down* below).

## the key/value fall-through — and documents

`kv`, `kv_store`, `msg` and `_mtron_meta` are the reserved collections. A write to a reserved path is stored as a
typed key/value entry — same database, no table — and a nested `rec` turns that corner into a document store:

```mtron
mtron> tbledoc:kv/greeting -> 'salve, metatron'
==>'salve, metatron'
mtron> *tbledoc:kv/greeting
==>'salve, metatron'
mtron> tbledoc:kv/session/1 -> [user=>'marko',cart=>['fig','olive']]
==>[user=>'marko',cart=>['fig','olive']]
mtron> *tbledoc:kv/session/1/cart      [-- a field of the stored document --]
==>['fig','olive']
mtron> *tbledoc:kv/+/                  [-- vid => value --]
==>tbledoc:kv/greeting=>'salve, metatron'
```
## patching a row (a write replaces it)

A write to an existing key **replaces the row body** — fields left out of the rec are cleared:

```mtron
mtron> tbledoc:person/5 -> [name=>'vela',age=>52]
==>[name=>'vela',age=>52]
mtron> tbledoc:person/5 -> [age=>53]        [-- a partial write: `name` is cleared --]
==>[age=>53]
mtron> *tbledoc:person/5
==>[age=>53]
```
To patch, anchor the row and update it in place. `>>= [k=>v]` overlays fields the row already has; `+[k=>v]` promotes a
field it does not (creating the column, if the table has never had one):

```mtron
mtron> @tbledoc:person/5 >>= +[name=>'vela']     [-- set promotion --]
==>[name=>{2}'vela',age=>53]
mtron> *tbledoc:person/5
==>[name=>'vela',age=>53]
```
## keys the database assigns

`_` as the last path segment asks the backend for the key — the uri says *where the row lives in the table*, the
database says *what its key is*. Two things have to be true for it to work: the space must declare the `incrq` query
processor (`q => [incrq::[=>]]`, in the setup block above), and the write must ask for it with `?incrq`:

```mtron
mtron> tbledoc:note/_?incrq -> [body=>'a note with a database-assigned key']
==>[body=>'a note with a database-assigned key']@tbledoc:note/10
mtron> tbledoc:note/_?incrq -> [body=>'another one']
==>[body=>'another one']@tbledoc:note/12
mtron> *tbledoc:note/+/id                                    [-- the keys the backend picked --]
==>1
==>12
==>3
==>10
==>11
==>2
==>4
==>5
==>6
==>7
==>8
==>9
```
## taking the space down

A space is a **mount**, not a copy. Writing `noobj` to its vid takes it down: the pattern stops routing, the JDBC
connection closes, and the schema instset the space published goes with it. The rows do not — they live in the database
file, so mounting a `tblespace` over the same file brings them back. The block below is also the shortest proof of how
lazy that mount is: the rows are there from the first statement, but the space only learns they are *tables* when
something walks the catalog:

```mtron
mtron> /sys/space/tbledoc -> noobj                 [-- unmount --]
mtron> tblespace::[pattern => tbledoc:#,
                   host    => <sqlite:/tmp/mtron-tbledoc.db>,
                   driver  => <org.sqlite.JDBC>,
                   route   => [tbledoc: => <>]]@/sys/space/tbledoc
==>tblespace::[
    pattern=>tbledoc:#,
    host=><sqlite:/tmp/mtron-tbledoc.db>,
    driver=><org.sqlite.JDBC>,
    route=>[tbledoc:=><>]]@/sys/space/tbledoc
mtron> *tbledoc:person/+.count()                   [-- 0: a fresh mount has not discovered its tables yet --]
==>0
mtron> */sys/space/tbledoc/instset/+/              [-- reading the instset is what walks the catalog --]
==>/sys/space/tbledoc/instset/award=>rec::T[?[{?}trophy=>str::T,recipient=>isa(person/+/id).!*id()]]@/sys/space/tbledoc/instset/award
==>/sys/space/tbledoc/instset/note=>rec::T[?[{?}body=>str::T]]@/sys/space/tbledoc/instset/note
==>/sys/space/tbledoc/instset/person=>rec::T[?[{?}name=>str::T,{?}age=>int::T,{?}skill=>str::T]]@/sys/space/tbledoc/instset/person
mtron> *tbledoc:person/+.count()                   [-- the rows were in the file all along --]
==>5
mtron> /sys/space/tbledoc -> noobj                 [-- leave the graph as we found it --]
```
Unmount before re-mounting the same scheme under a different configuration: while a space is mounted it owns its
pattern, and reads of an unmounted scheme fall through to the router's catch-all rather than failing loudly.

## not available yet

* **`.explain()` on a rewritten read fails.** The rewrite replaces the code with a rewrite inst that is not itself
  callable, so a `count()` read explains as
  `fail::[args do not match inst args: [X=>/m/tble/inst/rewrite/sql_count?int<=#{0}(person/+)]]`.
  Ask the rewrite instead — `*/m/tble?docq` prints the family, and each entry documents its own before/after code:

```mtron
*tbledoc:person/+.count().explain()    [-- fail:: args do not match inst args --]
```

* **`kv_limit` is broken on the typed key/value schema.** `*tbledoc:kv/+.take(n)` compiles to
  `SELECT furi, obj FROM kv_store WHERE ...` and sqlite answers *no such column: obj*. `kv_count`
  (`*tbledoc:kv/+.count()`)
  is fine.

```mtron
*tbledoc:kv/+.take(1)                  [-- fail:: SQLITE_ERROR ... no such column: obj --]
```

* **`>>{col}` is a drain, not a projection.** `*tbledoc:person/+>>{name,age}` yields the field *values* as a flat
  stream, not recs. The projection rewrite is `==[col=>_]` (`sql_select`).

* **the docs runner doubles a `_?incrq` row.** In a live VM session one `_?incrq` write inserts one row, and the
  assigned
  key stamps onto the vid. Evaluated by the docs runner — which evaluates with type checking disabled — the same write
  lands *two* rows, and the vid it returns is the second key, so the key list above reads `1, 3, 2, 4` rather than
  `1, 2`. The idiom is the same either way; the doubling is a runner-context artifact, not a misconfiguration.

## see also

* [tbleSpace (Java)](../metatron/references/tble-space-java.md) — the same space from the inside: dual-path reads, VID
  stamping, `ExistingTableSchema`, schema generation, dialect handling.
* [Connecting Data Sources](connecting-datasources.md) — the pattern + route model every space shares, and `!*`.
* [dckrSpace](dckrspace-mtron.md) — an end-to-end SQLite container + tbleSpace walkthrough.
* the design records at `docs/design/tbleSpace-update-design.md` and `docs/design/sql_mqtt_schema_design.md`.
* `*/m/tble?docq` — the instset's own inventory (1 inst, 18 rewrites, 1 const) read straight from the VM.