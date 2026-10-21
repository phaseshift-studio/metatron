# Distributed Computing Patterns through Algebra

**Date:** 2026-10-18
**Context:** The foundation is *Stream Ring Theory* (Rodriguez 2019); the substrate is `/m/math/cat`
and the declarative `plan::T` DSL (cut/route/span, driven by the theory's roles/laws). This doc is
the companion to `cat-rewriter-distribution.md` (the first distribution rewrite) and
`db-pushdown-plan-architecture.md` (the database push-downs): it states the general principle that
**the patterns are the algebra's laws, not the ops.**

---

## 1. The premise: distribution is a theorem, not a feature

Distribution is what you get for free when the chain's structure obeys a law. The implementation
sites are the *theorem bindings* of the stream ring:

| theorem | statement | meaning for distribution |
|---|---|---|
| Thm 5 | streams are atemporal | no required order of apply/merge/bulk — remote placement is always safe |
| Thm 6 | coefficients float through non-reduce chains: `ca · 1b = 1a · 1b · c1` | elementwise work is independent — replicate, don't coordinate |
| Thm 16 | reducers are temporal | the fold is the one synchronization point |
| Thm 17 | floating stops at a reducer | the fold is the cut |
| Thm 19 | monoidic reducers are semi-right-distributive: `(a+b)c = (ac+bc)c` | a commutative monoid fold splits, merges, and re-applies |

So "what can be distributed" is decided by *which law the anchor obeys*, never by hand.

## 2. The laws are the patterns

Each law admits exactly one distribution shape. The few shapes:

| law | pattern | distribution shape | coordination |
|---|---|---|---|
| floatable (Thm 6) | map / filter / flatmap | **free** — replicate the run | none |
| commutative monoid (Thm 19) | reduce / fold | **star** — scatter, partial reduce, gather | one barrier |
| associative, not commutative | scan / prefix | **pipeline** — carry the running total | neighbor-to-neighbor |
| keyed product | join / group-by | **shuffle** — hash-partition, join locally, gather | key co-location |

There are only these few, and they are distinguished by the *law* (floatable / commutative /
associative / keyed), not by the op.

## 3. One pattern, many instances

`sum`, `count`, `min`, `max`, `merge`, `union` are all the **one** "commutative monoid fold" pattern.
The shard plan already realizes this: it keys on `reducer + monoidic + commutative`, so it fires for
every commutative reducer with no per-op work. The op is the *instance*; the law is the *pattern*.

The same holds one level up, at the backend: `native_count` is the same pattern as
`native_where`/`native_limit`/`native_select` — a **delegation** — and the space resolves the
instance (`SELECT` / `db.collection` / `g.V`). See `db-pushdown-plan-architecture.md`.

## 4. The route vocabulary is the distribution shape

The plan's `route` names the shape, and the shape is the law's consequence:

```
local     — transform in place            (the algebraic folds)
peers     — star / scatter-gather         (law_shard)
pipeline  — carry, neighbor-to-neighbor   (the scan)
shuffle   — keyed partition               (the join)
native    — delegate to a backend         (the push-downs)
none      — delete                        (the unit law)
```

`local`, `peers`, and `none` exist today; `pipeline`, `shuffle`, and `native` are the three shapes
the algebra still implies and the DSL does not yet speak. (`SPACE`/`TREE` in `Route.Target` are the
unused seeds of `pipeline`/`shuffle`.)

## 5. The plan::T connection

A distributed plan is two declarations over the same structure the folds already use:

```
cut   — the anchor's law        (commutative / associative / floatable / keyed)
route  — the law's shape        (star / pipeline / shuffle / free / native)
span   — the region that ships  (seed / pre / at / post)
```

`cut` answers *which law*, `route` answers *which shape*, and the resolver — the theory for ops, the
space for backends — renders the instance. That is the "algebraically driven" claim made literal:
the pattern is a reference into the algebra, and the op is what the law resolves to.

## 6. What's next

1. **Scan (pipeline)** — the associative, non-commutative fold. Still the additive monoid, so it is
   the algebraically-pure next step; it forces the `pipeline` shape.
2. **Join (shuffle)** — the keyed product. This is the bridge to the database engine's push-downs,
   and its `native` route target.
3. **The real span** — `pipeline` and `shuffle` need the span as a concrete region
   (`[(lo, hi, role)]`), not a scan over the whole chain; this is the gating piece for both.

The working list — every pattern's law, shape, plumbing and status, including what has shipped — is
`distributed-patterns-menu.md`. A stream is a bag (bulked by coefficient), so item 1's scan applies to `lst` values,
not to streams.
