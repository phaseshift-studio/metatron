# Environment Type (`env::T`) — Design

**Date:** 2026-09-06 **Status:** design draft — simmering, **not approved, not implemented**
**Owner:** the user (direction); the ideas below are captured verbatim-in-substance from that discussion.
**Scope:** elevate *environment* from a `BashFeature`-local bag of fields to a first-class type `env::T`,
consumed by every feature that runs commands (bash, dckr, build, docs, iot) — the way the LLM family
consumes `model::T`.

---

## 1. TL;DR

- **Today:** "environment" exists only inside `BashFeature` — `dir`, `timeout`, `env`, `allow`, `reject` are
  feature-local fields defined in the `bash_feature` type (`llmInstSet.java:647-662`) and read via
  `this.at(...)`. It is the *only* process-spawning feature in the repo.
- **The bet:** the execution family is coming (dckr‑exec, build, docs‑runner, iot‑shell, mcp‑exec). Without a
  shared noun, each one will mint its own `DIR`/`TIMEOUT`/`ENV`. `model::T` is the precedent for what a single
  rich rec type prevents within a feature family.
- **The proposal:** `env::T` — a rec‑based, `docWrap`‑ed, *constructed* type (value = a small `Env` object with
  accessors, exactly the `model::T` → `mModel` shape) holding `envvars`, `dir`, `timeout`, and an isolation
  `policy`. Features consume it via `this.at(ENV_TID)`.
- **The key reframe (contract → enforcer):** `model::T` is a *contract*; `mModel`/provider is the
  *implementation*. Likewise `env::T` is the contract (what the agent is *allowed*: vars, cwd, budget,
  isolation), and the **enforcer is pluggable** — JProc today (env + dir + timeout; weak), `dckr`/OS namespaces
  later (fs, netns, cgroups; strong). Same type, different backend. *That* is what makes it an "OS partition"
  instead of a config map.
- **Isolation is a policy on the type, not a boolean on a feature** (see §5).

---

## 2. The state of the world (grounded)

What exists and works today (all tested — `BashFeatureTest`, 43 green, `BUILD SUCCESS`):

| concern | where | behavior |
|---|---|---|
| `dir` | `bash_feature` type → `BashFeature` | `this.at(DIR).orElse(uri(user.dir))` → `ProcBuilder.withWorkingDirectory` |
| `timeout` | `bash_feature` type → `BashFeature` | precedence **agent arg → feature config → `DEFAULT_TIMEOUT` (30 s)**; `.tid(MATH_MILLIS_TID)` unit conversion |
| `env` | `bash_feature` type → `BashFeature` | `rec[uri, union(uri, str)]`; applied via `ProcBuilder.withVars(...)` — **verified in bytecode as `Map.putAll` = additive/override** |
| `allow` / `reject` | `bash_feature` → `BashFeature` | `allow` = `matches()` (whole command); `reject` = `find()` (anywhere); applied **before** spawn |
| execution | `BashFeature` | `bash -c "<cmd>"` (let a real shell tokenize); failure = non‑zero exit → `MTronException` with exit code + normalized execution time |

Facts that shape the design (verified, not assumed):

- **JProc env mechanics —** `ProcBuilder.withVars(map)` = `putAll` (additive/override). `clearEnvironment()`
  flips a flag; in `Proc`, the bytecode runs `environment().clear()` **iff** that flag is set, *then* always
  `putAll(vars)`. So: default = *inherit host env + override*; `clearEnvironment()` = *only the declared vars*.
- **Inheritance is the default, and it is the exposure:** as shipped, the agent's bash inherits the whole JVM
  environment (PATH, HOME, and any secret that lives there) and could `printenv` it. `env => [...]` today is a
  *context layer*, not a sandbox.
- **`PATH` is the dividing line between a complete env and a partial overlay** — a partial overlay never needs
  to declare `PATH` (it inherits it); a complete one must (or nothing resolves).
- **The `ENV` token already exists** (`Tokens.java:317`, `"env"`); so do `PATH` (`"path"`) and `USER`
  (`"user"`). There is **no** `ENVIRONMENT`, `PARTITION`, or `CONTEXT` type in use — the noun is free to claim.
- **The `model::T` template** (`llmInstSet.java:216-230`): `docWrap(Type.Builder … .vid(LLM_MODEL_TID)
  .isaPredicate(rec(…)) .constructor(arg -> LLMFactory.createModel(arg.asRec())) …)` — a rec type whose value
  is a constructed object (`mModel`), consumed by ~7 features (`Embed`, `Concept`, `Chat`, `Compaction`,
  `Agent`, `LLMFactory`, …).

---

## 3. Proposal: `env::T`

A first-class type, mirroring `LLM_MODEL_TYPE`:

```java
ENV_TYPE = docWrap(Type.Builder.build()
        .tid(REC_TID)
        .vid(ENV_TID)                                    // e.g. <domain>.extend(ENV) — the "env::T"
        .isaPredicate(rec(
                uri(ENVVARS).maybe(), rec(URI_TYPE, union_(URI_TYPE, STR_TYPE).tryToInst()),
                uri(DIR).maybe().asUri(), URI_TYPE,
                uri(TIMEOUT).maybe(), TIME_TYPE,
                uri(POLICY).maybe(), union_(uri(shared), uri(isolated)) ))
        .constructor(arg -> new Env(arg.asRec()))        // tiny Env object: envs(), dir(), timeout(), isIsolated() — the mModel analog
        .create(), …,
        "the execution environment shared by any feature that runs a command")
```

Consequences that fall out of making it a *type* (not a feature config):

1. **One source of truth** for env/dir/timeout semantics — documented once, defaulted once, tested once.
2. **Flow-through** — a partition value can be *assigned to an agent* and inherited by every executor it
   touches: "run this agent in partition P" where P carries env + dir + budget + isolation. That is the
   multi‑feature win.
3. **`Env` value object** gives typed accessors and is the future home of the convention that *env values are
   never logged* (mask-once on the type, not re-decided per feature).

**Naming (preference, not settled):** the *type* is `env::T` (the `ENV` token exists — no new term to mint);
**"partition" stays as the English** for an `env::T` whose policy is `isolated`. `partition::T` as a type name
bakes a policy into the noun and collides with "filesystem partition".

**Namespacing (decision needed):** LLM-scoped (`LLM_ISA_TID.extend(ENV)`) or a small new domain (e.g. an
`os`/`m-os` instSet)? dckr/iot/build are *not* LLM features, which leans **new domain**; if the type ever moves,
it's one `TID` line.

---

## 4. Contract → enforcer (the "OS partition" made honest)

| layer | `model` family | `env` family |
|---|---|---|
| **The type (contract)** | `model::T` — what the model must provide | `env::T` — what the agent is allowed: vars, cwd, budget, isolation |
| **The value (adapter)** | `mModel` (provider/host/key/size/quant/…) | `Env` (envvars/dir/timeout/policy) |
| **Weak enforcer** | an HTTP/Ollama client | **JProc** — env + cwd + timeout. Isolation here = *env scrub* (`clearEnvironment`) + path + clock. **No fs/net isolation.** |
| **Strong enforcer** | (a hosted provider sandbox, if any) | **`dckr` / OS namespaces** — own fs, netns, cgroups |

Honesty bound: JProc+`clearEnvironment` stops the agent *reading* the host env; it does **not** stop the agent
from writing files or touching the network. Strong isolation is an **enforcer** capability (`dckr` already is
one in this repo); `env::T` merely *carries the intent* and names the enforcer. Claiming "sandbox" for
`clearEnvironment` alone would be a stretch — keep those words distinct in code and docs.

---

## 5. Isolation policy (the `clean_env` question, settled as a *type* concept)

Two modes, one type field:

- **`shared` (additive, = today's shipped behavior):** inherit the host environment, overlay `envvars`
  (`withVars` = `putAll`). Low burden, weak isolation.
- **`isolated` (clean):** `clearEnvironment()` then `withVars(envvars)`. The shell sees **exactly** the declared
  vars — *nothing* inherited. The environment must be **self‑sufficient**: it must declare `PATH` (or nothing
  resolves), plus `HOME`/`USER`/`TERM` as needed.

Three candidate encodings, in ascending explicitness:

1. **Explicit union:** `policy => shared | isolated` — a typed pair of uris, not a boolean. Clean, discoverable.
2. **Inferred from data:** the *presence of `PATH` in `envvars`* declares a complete environment → isolated;
   absence → additive. No flag at all, self‑documenting ("a complete env names its own PATH").
   *Known edge:* additively *overriding* `PATH` (e.g. prepending `~/.local/bin`) would flip the mode.
3. **Both:** explicit `policy` when present; the `PATH` heuristic as the default when absent.

**Leaning:** (1) or (3) — once this is a first‑class type, an explicit typed policy is *not* the cheesy boolean
it was as a feature flag; the boolean was cheesy because it was a *modifier bolted on bash*.

---

## 6. What is deliberately **not** `env::T`

- **`allow` / `reject`** — that's *command governance* (what may run), a different concern from *environment*
  (in what context). Keep it on bash, or give it its own policy type later.
- **Per‑call environments** — settled: feature/agent-level only; the agent's *model* passes call‑level args
  (like `timeout`), but environment context stays above the call.
- **fs/net/resource limits** — enforcer's job (`dckr`/cgroups). At most *advisory* fields on the type until
  there's an enforcer that reads them.

---

## 7. Phases (each gated on the one before)

1. **P1 — the type.** Define `ENV_TID` + `ENV_TYPE` + a small `Env` value object. Migrate `bash_feature`'s
   `dir`/`timeout`/`env` onto it (keep the precedence: call‑arg `>` `env::T` `>` built‑in default). Behavior and
   the 43 tests stay green. Cheap enough to do *before* a second consumer exists — seed the noun, keep it small.
2. **P2 — the payoff.** A **second consumer** (dckr‑exec or the docs build runner) consumes the same `env::T`.
   An abstraction without two users is a guess; with two, it's a fact. If no second consumer materializes,
   reassess whether P1's generality was premature.
3. **P3 — enforcers.** `policy => isolated` → `clearEnvironment()` under JProc; then a dckr enforcer for
   fs/net/resources reading the *same* `env::T`. The contract never changes; the backend does.

---

## 8. Open questions

1. **Name** — `env::T` (type) + "partition" (English for `policy=>isolated`)? or `partition::T` outright?
2. **Namespace** — LLM domain (near the consumers) vs a new `os` domain (near the concept)?
3. **Policy encoding** — explicit union, `PATH`-heuristic, or both (see §5).
4. **Default homes** — today `timeout` has three layers (type default 10 s, code `DEFAULT_TIMEOUT` 30 s, call
   arg). When `env::T` absorbs `dir`/`timeout`, decide where defaults live so they can't disagree.
5. **Secrets convention** — state it once on the type: env *values* are never logged; names may be. (Current
   tests already pin "no leakage" for bash; the convention should outlive bash.)

---

## 9. Appendix — the implementation this builds on (stable, tested)

So a future agent doesn't re-litigate settled ground:

- `bash -c "<command>"` — the spawn idiom (a process `exec` does no shell tokenizing; let a real shell parse the
  agent's command line).
- Guards are lexical, applied pre-spawn: `allow` = whole-command `matches()`, `reject` = anywhere `find()`;
  `reject` is the real fence, `allow` is shape (an `echo .*` allow does **not** prevent `echo x; rm …`).
- The shipped boot config uses `reject => ["\brm\b"]` (word-boundary), not `rm .*`.
- Timeouts: `.tid(MATH_MILLIS_TID)` converts unit→millis via the type constructor (value conversion, not
  relabeling); failure reporting = non‑zero exit with normalized execution time.
- `env => [a => b, user => marko]` is `rec[uri, union(uri, str)]`; values become `withVars` entries via
  `toCleanString()`.
- Tokens in play: `ENV` ("env"), `PATH` ("path"), `USER` ("user") exist; `ENVIRONMENT`/`PARTITION` do not.
