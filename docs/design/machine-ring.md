# the machine ring

The **stream ring** is the algebra of *data* — the functions and instructions where `+` is parallel branching and `·`
is serial composition ([Stream Ring Theory](../website/articles/stream-ring-theory.md)). The **machine ring** is its
dual over *process* — the machine (`machine::T`) as the object, where `+` fans a computation out across machines and
`·` navigates between them. Theorem 19 of the stream ring (semi-right distributivity) is the law that glues the two:
it is what makes the machine ring's `+` *sound*.

## the object

`machine::T` = (`memory`, `network`, `instset`, `compiler`, `processor`). Each component has its own type, and the
machine ring's `+` is *component-wise* — the operation each component's own type already carries.

## the operations — `+` `·` `0` `1`

| op | name | on the machine | identity |
|----|------|----------------|----------|
| `·` | serial | `move` — point the machine pointer at an existing machine | `stay()` = `move(<~>)` = `1` |
| `+` | parallel | fan-out / union — run across machines and merge | the empty fan-out = `0` |

- `·` is a **groupoid** (every path has an inverse: `move(<a>)` ↔ `move(<..>)`), not just a monoid.
- `push`/`pop` are the *resource* (linear) pair *outside* the ring: `push` spawns, `pop` reaps. `move` only
  navigates — the target must already exist.

## the fibered `+`

`+` is component-wise, and the components split into two kinds:

| component  | kind                  | `A + B`                                                |
|------------|-----------------------|--------------------------------------------------------|
| `memory`   | collection (data)     | union the spaces / data                                |
| `network`  | collection (data)     | union the peers / roster                               |
| `instset`  | collection (data)     | union the instructions **and the rewrite rules**       |
| `compiler` | transform (compile)   | `compiler(A.instset ∪ B.instset)` — same pipe, unioned rules |
| `processor`| process (runtime)     | branch — run both in parallel, merge                   |

So `+` is *union on the data fibers, branching on the process fiber*. `law_shard` is exactly this `+`: it branches
the processor (`select([to(p₁), to(p₂)])`) and unions the memory (`merge()`, the deep merge).

## `0` and `1`

- `1` = `stay()` — the identity move (the groupoid's unit).
- `0` = the empty machine — no data, no peers, no rules, no execution. It is the additive identity (`A + 0 = A`)
  and the annihilator (`0 · move(P) = 0`).

## the structure is a semiring

The stream-ring article names the data side: reduce functions form a **near-ring** (Thm 18) — `+` a group, with
inverses via the coefficient ring's negatives — *not* a ring (Lemma 1, distributivity is one-sided). The machine
ring's `+` is weaker still: it is a **monoid** (union has no inverse — there is no negative machine), so the machine
ring is a **semiring**, not a near-ring. That asymmetry is the point: *data is Cartesian* (copyable, invertible →
coefficients), *process is linear* (monotone, non-copyable → no coefficients). Thm 19's semi-right distributivity —
`(a+b)·r = (a·r + b·r)·r` — is what glues the two halves; the extra `·r` is the barrier, the recombine `law_shard`
emits.

## practical deductions

1. **Parallelization is a declaration, not a decision.** Thm 19 turns "can I distribute this fold?" into "does the
   reducer declare `monoidic` + `commutative`?" — `law_shard` is the compiler doing it, with a proof, not a test.
2. **The barrier is derived.** Not-a-ring ⇒ `(a+b)·r ≠ a·r + b·r`; the recombine `·r` is forced. The algebra forbids
   the classic "parallel reduce drops the barrier" bug.
3. **Outage is provably safe.** Monoidic reducers are idempotent (Thm 19), so dropping offline peers (shrinking the
   fan-out) still completes correctly. Fault tolerance as a corollary of two law labels.
4. **Resources are leak-free by construction.** `push`/`pop` are allocate/deallocate; there is no un-reap. A spawned
   partition is GC'd in one call.
5. **Data co-location is a deduction.** A floatable op commutes past placement (distributivity), and the location
   coordinate says where the data lives — so "ship the code to the data" (push-down) is a theorem, not a heuristic.
6. **The negatives prevent bugs.** No split-without-recombine (not-a-ring), no un-reap (linear), no ship-to-a-place
   that can't resolve the code (the vocabulary coordinate).

## the coefficient ring is the modality

The stream ring's coefficient `C` is the one parameter that selects what a stream *means*: read as $\mathbb{N}$ it is
multiplicity ("how many copies"); read as $\mathbb{C}$ it is amplitude ("how much" — interference, the quantum case);
read as the **machine ring** it is *location* ("where it runs"). A stream $\sum_i M_i \cdot f_i$ with machine-ring
coefficients is a **located stream** — a distributed program, each function weighted by the machine it runs on. That
is what `law_shard`'s emitted layout already is: `select([to(p₁), to(p₂)])` assigns the coefficient (the machine),
`merge()` adds them (the machine union, the deep merge). So classical, quantum, and distributed are three values of
one parameter — $\mathbb{N}$, $\mathbb{C}$, Machine Ring — and their tensor ($\mathbb{C} \otimes$ Machine Ring) is
the quantum-distributed case. The `cInt` → `C<?,?>` generalization is exactly this parameter, and the machine ring is
both an algebra in its own right *and* a value of it.

## status

Encoded today: `move`/`stay`/`pop` (navigation + resource lifecycle), `law_shard` (the `+` over peers), and the local
laws (`theory_unit_removal`, …) as the `·`-side rewrites. **Not yet encoded:** the explicit `0` element, the explicit
`+` operator over nested machines, and the `place.instset` vocabulary coordinate that makes data co-location sound.
