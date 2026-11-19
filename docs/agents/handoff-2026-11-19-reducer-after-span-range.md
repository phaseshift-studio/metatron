# Handoff — a reducer after a span range held home folds each obj alone

> Date: 2026-11-19. Main at `ea9e34099`. First proposed on 2026-11-18 as a session card ("Fix reducer folding per obj
> after a span range"); this file keeps that write-up after the card expires, re-checked on main the next day. Git is
> free in your own worktree and never on main: hand Marko the commit (message file written first, in `.docker-target/`,
> ending `Co-Authored-By: @dr-stynx`) and the `merge --ff-only`. Build and test only on ginger
> (`METATRON_REMOTE_ENV=/home/killswitch/software/metatron/bin/test/metatron-remote.env bin/metatron-remote --host ginger.local build test -Dtest=…`),
> one run at a time, focused classes, and never the full suite: Marko names the gate. Typer checks stay at the boot
> default (everything but `code_resolve`).

## Why this exists — read first

Marko confirmed the bug on 2026-11-18 and wants it fixed, later: "its a bug..you shouldn't have to do
sum?int<=int{*}() ... just a workaround for now until the bug is solved." A reducer that follows a span-ranged
instruction, in a chain whose head is a place-keyed branch, compiles with no dom and applies once per obj instead of
folding the stream.

## It still reproduces (2026-11-19, main `ea9e34099`)

Probed through MarkdownRunner on ginger, which compiles in production order, with `/m/math` and `/m/math/cat` imported
and two peers dialed by `harvest`. The setup block and the probe script are in
`docs/agents/handoff-2026-11-19-distributed-casts.md`.

| expression                                                     | answer           | correct |
|----------------------------------------------------------------|------------------|---------|
| `{1,2,3,4,5}.plus(1).sum().{0,3}plus(1).sum?hop=0()`            | `20, 21, 22, 23` | 86      |
| `{1,2,3,4,5}.plus(1).sum().{0,3}plus?place=[local](1).sum()`    | `20, 21, 22, 23` | 86      |

`explain()>>format` on either prints the cut's `branch` and `sum` typed correctly, then `{0,3}plus  #::T → #::T` at
`c_rng {1}`, then `sum  #::T → #::T` at `c_dom {1}` with no `f` (`<?>`). The reducer is unbound.

## How it was characterized (2026-11-18)

An in-process probe: `CatTheoryCompiler.single()`, `/m/math` and `/m/math/cat` imported, two `loop://` peers dialed,
the chain run through `Machine.current().compiler().rewriter()` and then `TransportRewriteHarness.evaluate`.

- `{1,2,3,4,5}.plus(1).sum().{0,3}plus?place=[local](1).sum()` (the ranged run held home) compiles to
  `branch?rng=#{*}&dom=noobj{0}…`, `sum?dom=#{*}&args`, `{0,3}plus?rng=/m/int&dom=#&args=[/m/int]`, then **`sum?args`:
  no dom, unbound** (`hasf=false`), and answers `{20,21,22,23}`. Holding the run with `sum?hop=0()` instead gives the
  same, so a q on the reducer is not the cause.
- It is identical with typer checks on (the boot default) or off, so it is not a typer-off bug, whatever earlier notes
  say.
- The `sum` right after the branch gets `dom=#{*}` from the branch's rng and folds correctly. When the ranged run ships
  (law_shard plans every cut now), the answer is 86 either way.
- Marko's console compiled the same one-cut tail to `sum int{*}::T → int::T <j>` and answered 86. The 2026-11-19 docs
  probe, which compiles in production order too, still answered per obj, so compiler order alone does not explain the
  console's 86: find what else differs in his setup.
- Live repro (from booger: `timeout 60 bin/wsplus -e '<expr>' 'ws://192.168.1.61:8555/mtron?out=application/x-mtron-ui'`,
  with peers dialed): `….{0,3}plus(1).sum?hop=0()` folds per obj, and `explain()>>format` shows `{0,3}plus` at
  `c_rng {1}`.

## Likely causes (check them)

1. A span-ranged instruction's rng coefficient does not count its unrolls: `{m,n}a` maps one obj to `n-m+1`, but is
   typed `{1}`, so the next reducer is never given a `{*}` dom.
2. A reducer whose dom the typer leaves unbound applies per obj rather than gathering.

Read `Rewriter.Helper` typing (`carriers`/`contracts`), the typer stage, and where a spanned instruction's coefficients
are computed.

## The workaround to remove

`src/test/java/studio/phaseshift/metatron/isa/m/mach/network/PlaceLaneTest.java`,
`testARunStatesItsOwnAxesOrKeepsThoseBeforeIt`: the first row (line 572 on `ea9e34099`) is
`start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum?int<=int{*}&hop=0()  % 1 % 86`, and the comment at line 565 names
this bug.

## Done when

- That row passes as plain `sum?hop=0()`, with the barrier and its comment dropped.
- A typing-level regression test pins the reducer's dom after a span range.
- Both expressions in the table answer 86 in a docs probe.
- The focused classes Marko names are green. `PlaceLaneTest`, `DistributedComputingTest` and `mInstSetTest` are the
  obvious candidates.

## Related

- `docs/agents/handoff-2026-11-19-reducer-after-maybe-dom.md` has the same signature: a reducer left unbound after an
  instruction whose rng coefficient understates the stream (`{1}` here, `{?}` there). One fix may cover both; check
  before fixing either.
- Found while generalizing law_shard to plan every fold of a chain. The bug predates that work: the old one-cut layout
  kept any tail behind the branch, so a ranged tail with a fold after it answered per obj. The walk now ships such runs,
  which sidesteps the bug, but a ranged run held home (`?hop=0`, a single place, or a computed prefix) still hits it.
- The suites compile with `CatTheoryCompiler` (resolve, then rewrite); production rewrites first. This bug shows under
  both.
