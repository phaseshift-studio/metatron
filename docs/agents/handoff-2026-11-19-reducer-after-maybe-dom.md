# Handoff — a fold after a `{?}`-dom generic instruction folds each obj alone

> Date: 2026-11-19. Main at `ea9e34099`. First proposed on 2026-11-18 as a session card ("Fix folds after a {?}-dom
> generic instruction"); this file keeps that write-up after the card expires, re-checked on main the next day. Git is
> free in your own worktree and never on main: hand Marko the commit (message file written first, in `.docker-target/`,
> ending `Co-Authored-By: @dr-stynx`) and the `merge --ff-only`. Build and test only on ginger
> (`METATRON_REMOTE_ENV=/home/killswitch/software/metatron/bin/test/metatron-remote.env bin/metatron-remote --host ginger.local build test -Dtest=…`),
> one run at a time; read the `Tests run:` lines, not the exit code. Run focused classes and never the full suite:
> Marko names the gate. Typer checks stay at the boot default; never flip `PeerNode`'s typer or the boot profiles.
> Write tests for your own work.

## Why this exists — read first

`{1,2,3,4}.is(gt(0)).sum()` answers `{1,2,3,4}` instead of 10. Any fold after an instruction whose generic dom is
`A{?}` (declared `.dom(A.maybe(), …)`) folds each obj alone. Marko wants it fixed, later.

## It still reproduces (2026-11-19, main `ea9e34099`)

Probed through MarkdownRunner on ginger with no peers and no cat import. The probe script is in
`docs/agents/handoff-2026-11-19-distributed-casts.md`.

- `{1,2,3,4}.is(gt(0)).sum()` answers `1, 2, 3, 4`; it should answer 10.
- `explain()>>format` prints `is  A{?}::T → A{?}::T` (a filter), then `sum  #::T → #::T` at `c_dom {1}` with no `f`
  (`<?>`).
- `{1,2,3}.side(plus(1)).sum()` answers 6, so the one-obj dom case, fixed earlier, holds.
- **Distribution masks it.** With `/m/math/cat` imported and two peers dialed, the same expression answers 10, because
  law_shard ships the filter and each lane folds its own share. Probe without peers.

## The cause (traced 2026-11-18, re-read 2026-11-19)

- `Inst.Helper.bindGenerics` (`Inst.java:1330`, the condition at `:1336`) binds a generic dom against a multi-obj
  stream only when `lhs.type().c().within(dom.c())` holds or `appliesPerObject(lhs.type(), dom)` holds.
- `appliesPerObject` (`Inst.java:861`) is `dom.c().isOne() && lhs.c().gt(cInt.ONE())`. A `{?}` dom passes neither
  test, so `A` stays unbound and the instruction's rng stays `A{?}`.
- The reducer after it then compiles unbound and applies per obj at runtime.
- The javadoc on `appliesPerObject` records the same bug, already fixed for one-obj doms (`{1,2,3}.side(plus(1)).sum()`).
  Its regression rows are in `mInstSetTest` (lines 209–213) and `InstTest#testBindGenericsBulkLhs` (`InstTest.java:410`).
- Every `.dom(A.maybe(), …)` declaration is affected. Today there are 8 in `Obj.java` and 2 in `mInsts.java`; the card
  counted 18 in `Obj.java` before the instructions moved to `mInsts`. They include `is`, `to`, `map`, `at`, `move`,
  `stay`, `push`, `pop`, `block`, `native` and `source`; grep both files for the current list. `to` escapes only
  because its rng is `A{*}`, and `print`/`println` now declare `A{*}<=A{?}`, the same as `to`.

## Candidate fix

Extend `appliesPerObject` to a `{?}` dom (`isOne() || isMaybe()`). Keep the `gt(1)` lhs condition and respect the
`{*}`-deferral caution in its javadoc. The domain gate shares this helper and must agree with the binder: the call at
`Inst.java:844`, and `filterOnDomainAllowUnique` in `Type.java`, `Inst.java` and
`mach/type/compiler/resolver/GenericBinder.java`.

## Done when

- `{1,2,3,4}.is(gt(0)).sum()` answers 10 with no peers.
- Regression rows sit beside `mInstSetTest`'s `side` rows, plus an `InstTest#testBindGenericsBulkLhs` row for
  `test?A{?}<=A{?}`.
- The change reaches every `{?}`-dom instruction, so propose the focused classes and let Marko choose: `InstTest`,
  `mInstSetTest`, `catInstSetTest`, `TypeTest`, `DistributedComputingTest`, `PlaceLaneTest`, and `GraphittyLoggerTest`
  for `print`. Run the full suite only if he asks for it.

## Related

- `docs/agents/handoff-2026-11-19-reducer-after-span-range.md` has the same signature: a reducer left unbound after an
  instruction whose rng coefficient understates the stream (`{?}` here, `{1}` there). One fix may cover both; check
  before fixing either.
- Surfaced while fixing law_shard (`Shard.java`), whose run after `print(_)` stayed home. That fix changed `print`'s
  contract and deliberately did not touch the binder, because a binder change reaches every `{?}`-dom instruction.
