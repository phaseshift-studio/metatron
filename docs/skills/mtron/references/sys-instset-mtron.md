---
name: sys-instset
description: The /m/sys instruction set — the guarded bash() shell plus sleep/stdout/stdin process I/O.
---

# System Instruction Set (`/m/sys`)

`/sys` is metatron's required system space — created at boot, it holds the system objs (router, typer,
rewriter, env, thread). The sys instset (`/m/sys`) is its instruction companion: a guarded `bash` shell and
the `sleep`/`stdout`/`stdin` I/O primitives. Instructions live under `/m/sys/inst/...`; read any of them with
`?docq` before use.

## Instructions

| inst     | dom → rng            | arg               | description                                    |
|----------|----------------------|-------------------|------------------------------------------------|
| `bash`   | `#{?} → lst[str::T]` | `cmd`, `timeout?` | guarded shell (`bash -c`), stdout split to lst |
| `sleep`  | `A{?} → A{?}`        | `time`            | pause the current thread, pass lhs through     |
| `stdout` | `A{?} → A{?}`        | `#{?}`            | print the arg's jvm obj, pass lhs through      |
| `stdin`  | `A{?} → str`         | —                 | read one line from the terminal                |

## bash (`/m/sys/inst/bash`)

The shell instruction — the function-call primitive: reach into the shell with `bash`, shape the result with
metatron's data structures. `cmd` is the terminal command, evaluated with `bash -c`. `timeout` is an optional
`time::T` (`millis::1000.0`, `second::1.0`, ...) with a 30-second default. The result is a `lst[str::T]`, one
entry per stdout line; a non-zero exit is a `fail::T`.

```mtron
bash('ls')
bash(cmd=>'whoami', timeout=>second::5.0)
```

Batch over a rec (indexed) or a lst (flat):

```mtron
{"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- flatten to one lst --]
```

Pipe a follow-up command over each result — `>>` drains the list, `${_}` binds the current element; `.mapp`
maps explicitly (and, with a lambda, indexes by the current element):

```mtron
bash('ls').>>.bash("stat -c '%U' ${_}")            [-- drain: owners coalesce to a multiset --]
bash('ls').mapp(bash("stat -c '%U' ${_}"))         [-- map: one result lst per file --]
bash('ls').mapp(-<[_=>bash("stat -c '%U' ${_}")])  [-- indexed map: file => owner --]
```

### security modulators (q-params)

`bash` is hardened at the *instruction* level, not the call site. `allow`/`reject`/`env`/`dir` attach as query
parameters on the tid, so a tool registration bakes the policy in once — `!*bash?reject=['\brm\b']` — rather
than trusting the caller.

| q-param  | type            | semantics                                              |
|----------|-----------------|--------------------------------------------------------|
| `allow`  | `lst[str::T]`   | whitelist regexes, matched whole-command (`matches()`) |
| `reject` | `lst[str::T]`   | blacklist regexes, matched anywhere (`find()`)         |
| `env`    | `rec[uri=>str]` | environment vars injected into the process             |
| `dir`    | `str`           | working directory of the process                       |

`allow`, when non-empty, requires the command to match at least one pattern; `reject` fails when any pattern
matches anywhere. Both apply before the process spawns.

```mtron
!*bash?reject=['\brm\b']      [-- a bash that refuses rm --]
!*bash?env=[USER=>'metatron'] [-- a bash with a fixed env --]
```

### worked example — bash + the graph

The split in one line: `bash` supplies the function (`ls`, `stat`), metatron the shape — find the `Size` line,
regex out its byte count, type it `bB::T`, and keep the files over 30 kB. The unit types auto-convert, so `bB`
compares against `kB` directly (no `awk`, no `grep`, no `du`).

```mtron
bash('ls').mapp(-<[_=>bash("stat ${_}")]).>>.==[_=> >>.has("Size").regex('\s*(\d+)')>><0/0>.as?int<=str(int::T).as(bB::T)]==[_=>?>kB::30.0]
```

## sleep / stdout / stdin

```mtron
sleep(second::1.0)   [-- pause, pass lhs through --]
stdout('hello')      [-- print to the terminal, pass lhs through --]
stdin()              [-- block for one line, emit it as a str --]
```

## mounted state

Two recs are mounted under `/sys` at boot — not instructions, but read like any other space:

- **`/sys/env`** — the process environment, one `KEY => str` per variable.
- **`/sys/thread`** — the thread registry, one `id => thread` per thread, each documented via `?docq`.

```mtron
*/sys/env              [-- the environment as a rec --]
*/sys/env/HOME         [-- one variable --]
*/sys/thread/+?docq    [-- every thread's description --]
```

## see also

* [fsSpace](fsspace_mtron.md) — executable files are instructions too: reference a `chmod +x` file and append
  args to the uri to evaluate it.
