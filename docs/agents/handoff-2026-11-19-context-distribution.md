# Handoff — context distribution: getting a lane's definitions to the peer that runs it

> Date: 2026-11-19. Main at `649256191`. Git is free in your own worktree and never on main: hand Marko the commit
> (message file written first, in `.docker-target/`, ending `Co-Authored-By: @dr-stynx`) and the `merge --ff-only`.
> Build and test only on ginger (`bin/metatron-remote --host ginger.local …`), one run at a time, focused classes,
> never the full suite. Nothing below is decided: start by asking Marko the open questions.

## Why this exists — read first

Marko's next focus, after menu row W (embarrassingly parallel) and `Inst.isPure` landed. A lane that calls a
user-defined instruction cannot ship today. With `complex_computation?int<=int(){plus(1).mult(2)}@complex_computation`,
`{1,…,8}.complex_computation().sum()` answers 88 but keeps `complex_computation` home and ships only `sum?hop=0()`.
There are two reasons:
1. Under the production compile order (parse → rewrite → resolve) the user instruction is still bare when `law_shard`
   runs. `Law.Helper.keyed` returns it unchanged (it has no claim), and `isPure` fails closed: no claim, no body. A body
   that calls another user instruction fails closed too, since that inner reference is bare.
2. Even if it floated, the peer does not know the definition.

Context distribution means getting the definitions (and any other context a lane needs) to the peers that run it.

## Facts in the code

- `Inst.isPure` reads the named contract's claim, else the mtron body, else every contract of a bare op; it walks
  arguments, including code nested in lsts and recs. `InstPattern` (floatable, pure), `law_absorbing` and
  `branch_factor` use it.
- `path()` records the program as it runs: `StatefulMonad.java:114` appends each applied instruction and its output.
  `BasicSwarmComputer.java:253` tags code that reads `state/path`. So `law_shard` already changes what `path()` reports:
  the home side records `branch` and the fold, while the lanes' steps stay on the peers.
- `PlaceResolver.serves` admits a peer whose client serves the placement's transport instructions (`to`, `eval`) and is
  online. It never checks the lane's own instructions.
- There is no cost model. `Shard.emit`'s only size gate is "at least two members".
- The T pattern (`move`, `extractMove`) already ships a tail as source, which the receiver re-compiles.

## The design space (discussed 2026-11-19)

Marko: inlining a body into the lane changes what `path()` records. A context-distribution phase could use
`move(//remote)` to install the definitions on the peers before evaluation.

| strategy                                        | `path()` sees the same instructions? | paid when                 | main risk                                       |
|-------------------------------------------------|--------------------------------------|---------------------------|-------------------------------------------------|
| inline the body into the lane                   | no                                   | never shipped             | reflective chains change; peer overrides bypassed |
| context phase: `move` installs definitions first | yes                                  | once per peer per version | stale copies; installs leak to other users        |
| definitions travel with each lane               | yes                                  | every lane, every run     | repeated bytes; peer must scope them              |
| peer fetches a definition from home on first use | yes                                  | first call + a round trip | home must be reachable from every peer            |
| ship only to peers that already serve the lane  | yes                                  | nothing                   | may leave no candidates                           |

For the context phase: key each definition by a hash of its body and ship it only if missing; install under the
sender's namespace or a pushed frame; ship the transitive closure. The `isPure` walk visits exactly that set, so one
walk can prove purity and list what to ship. Widening `serves` to the lane's own instructions gives "never ship to a
peer that cannot run this" under any strategy.

Purity bounds the context: a pure computation cannot read changing addresses, so its context is code and types
(definitions, types, instsets such as math and cat). That holds only if plain reads (`*x`, `from`) never declare `pure`,
which is unchecked.

Cost: local time n×c; distributed time = a round trip + context (spread over runs if installed ahead) + data out +
(n÷k)×c + results back. W returns all n results, a fold returns k partials. `{1,2,2,3}.plus(2)` never pays; eight
100 ms elements on two peers do. c can be declared (a `cost` law beside `pure`), derived through a body (it adds along a
chain the way purity composes), or measured (`profile`). When n is unknown, plan statically and let the branch run its
lanes locally when its bag is small. `?place` and `?hop=0` stay as overrides.

A chain that reads its own path either stays home, or its lanes ship their paths home to be collated, like profile
collation (menu row U).

## Open questions for Marko

1. Which context strategy, or which combination?
2. Does `path()` report the program as written or as run? That decides whether inlining is allowed.
3. Should a cost model gate W (and A), and where does c come from?
4. Do plain reads declare `pure`? They must not, for purity to bound the context.

## Where to start

`Shard.java` (`apply`, `emit`), `InstPattern.satisfies`, `Inst.isPure` and `Inst.Helper.isPure`, `Law.Helper.keyed`
and `declaresAtEveryContract`, `PlaceResolver.serves`, the T pattern's `extractMove`. DistributedComputingTest's
`test_W_an_instruction_whose_body_holds_an_effect_stays_home` holds only the negative case: a positive row for a
pure user instruction waits on this work.
