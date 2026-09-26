---
name: sys instruction set
description: >
  The `/m/sys` instruction set and the `fsspace::T` half of the same doc: a guarded
    `bash` whose allow/reject/env/dir policy is baked into the inst's own tid,
    `sleep`/`stdout`/`stdin`, the `sys_stat` thread summary, and the file family
    `read_file`/`edit_file`. The second half is the `fsspace::T` tour, mounted on the
    metatron project root: typed reads by MIME, `?mimeq` tagging and structural parse,
    line addressing, tree walking, and text work on a scratch mount. TRIGGER: when
    shelling out (bash guard, timeout), reading or editing file lines, mounting a
    directory as a space, or discovering what a file is before reading it.
---

# sys instruction set (`/m/sys`)

`/sys` is the required system space — created at boot, it holds the system objs
(router, typer, rewriter, environment, thread registry). The sys instset is its
instruction companion: a guarded `bash`, the blocking I/O primitives `sleep`,
`stdout`, and `stdin`, the `sys_stat` thread summary, and a file family —
`read_file`, `edit_file` — that operates on files mounted by a `fsspace::T`.

The `fsspace::T` half occupies the rest of the doc: a directory, mounted and given the
mime and line query processors, becomes a **typed** file system.

Nothing here presumes a boot-time space. Each space the examples need is loaded by the
doc itself, in the block that introduces it — read those blocks the way the examples
read them.

## instructions

| inst       | dom → rng           | args                                       | what it does                                |
|------------|---------------------|--------------------------------------------|---------------------------------------------|
| `bash`     | `#{?} → lst[str]`   | `cmd`, `timeout?`                          | guarded shell (`bash -c`), stdout as lines  |
| `sleep`    | `A{?} → A{?}`       | `time`                                     | pause the thread, pass lhs through          |
| `stdout`   | `#{?} → #{?}`       | `obj`                                      | print the arg's jvm obj, pass lhs through   |
| `stdin`    | `#{?} → str`        | —                                          | read one line from terminal input           |
| `sys_stat` | `#{?} → rec`        | —                                          | the thread executor's own summary           |
| `read_file`| `#{?} → lst`        | `file`, `min?`, `max?`                     | a file's lines, indexed, or the `min..max` slice |
| `edit_file`| `#{?} → rec`        | `file`, `text`, `min`, `max?`              | insert at `min` (or replace `min..max`), and report |

## bash (`/m/sys/inst/bash`)

The function-call primitive: reach into the shell with `bash`, shape the result with
metatron's data structures. `cmd` runs under `bash -c`, in the VM's working directory —
in the docs build that is the metatron project root — and returns a `lst[str]`, one
entry per stdout line. A non-zero exit is a `fail::T` that carries the process's stderr
in its message. `timeout` takes a time type — `second::5.0`, `millis::1000.0` — or a
bare int, which is seconds; the default is `second::20.0`.

The signature is not a memory exercise; the inst carries its own doc:

```mtron_pre
*bash?docq
```

```mtron_pre
bash('ls')
bash(cmd=>'whoami')
bash('df -h')
```

A timeout and a failed exit are both fails, and both are inspectable:

```mtron_pre
[ERROR] bash(cmd=>'sleep 5', timeout=>millis::500.0)  [-- the timeout kills the process --]
[ERROR] bash('ls /no/such/directory')                 [-- non-zero exit, stderr in the message --]
```

### batch

A rec of commands maps to a rec of results, indexed by command; a lst of commands takes
the same `==` projection:

```mtron_pre
{"ls", "whoami"}.-<[_ => _]==[_ => bash(_)]   [-- rec of cmds => rec of result lsts --]
["ls", "whoami"]==[_ => bash(_)]>>.sum()       [-- lst of cmds => one flat lst --]
```

`==` is a **select** — one branch per slot of the poly, the rec's value the projection
applied to each. The glyphs are the actions, and the sugar says so in plain sight:

```mtron_pre
["ls", "whoami"]==[_ => bash(_)]>>.sum().explain()
```

### pipe over the results

`>>` moves right along the data — one step per element; `${_}` binds the current one.
The projection can do the work: first stat line of each entry, nothing else:

```mtron_pre
bash('ls')==[_ => bash("stat ${_}")>>0]          [-- each entry => its `File:` line --]
[MAXOUTPUT 5] bash('ls').>>.bash("stat ${_}")    [-- drain: the full stat per entry --]
```

### a shape of its own

`bash` supplies the functions; metatron supplies the shape. Each top-level entry's
`Size`, extracted and typed `bB::T`, then converted to `kB::T` — the unit system
converts against itself, so no `awk`, `grep`, or `du`:

```mtron_pre
bash('ls')==[_ => bash('stat ${_} | sed -n "s/.*Size: \([0-9]*\).*/\1/p"')>>0.as?int<=str(int::T).as(bB::T)]
```

Unit values test against each other's units:

```mtron_pre
bB::34192.0.gt(kB::30.0)
```

And they filter a lst by the same predicate — the branches that fail are dropped:

```mtron_pre
[bB::34192.0, bB::100.0]==[_ => ?>kB::30.0]==[_ => else(none)]
```

### security modulators (q-params)

`bash` is hardened at the **instruction**, not the call site: `allow`, `reject`,
`env`, and `dir` attach as query parameters on the inst's own tid — a policy baked in
once rather than negotiated per call. Query parameters are metatron's way of
annotating an inst at its tid; `?*` is the door, and these are the ones agents meet
first.

| q-param  | type            | semantics                                                |
|----------|-----------------|----------------------------------------------------------|
| `allow`  | `lst[str]`      | whitelist regexes, matched whole-command (`matches()`)   |
| `reject` | `lst[str]`      | blacklist regexes, matched anywhere (`find()`)           |
| `env`    | `rec[str=>str]` | environment variables injected into the process          |
| `dir`    | `str`           | the process's working directory                           |

Each guard fails before the process spawns, and the failure names the pattern that
fired:

```mtron_pre
[ERROR] bash?reject=['\brm\b']("rm -rf /tmp/never-created-here")  [-- the policy, not the file system, stops it --]
[ERROR] bash?allow=['ls']("whoami")                                [-- allow is whole-command: `whoami` is not `ls` --]
```

The allowed form passes — the pattern must match the whole command, and may be a regex — and the env lands in the process:

```mtron_pre
bash?allow=['ls .+']('ls AGENTS.md')
bash?env=[CI => 'docs']('echo CI=$CI')
```

## sleep / stdout / stdin

```mtron_pre
sleep(second::1.0)                       [-- one second, then lhs passes through --]
stdout("the sleep above took one second")
```

`stdin()` blocks for one line of terminal input. In the headless docs build there is
no input to take, so it is shown rather than run:

```mtron
stdin()    [-- block for one line, emit it as str::T --]
```

## the registry under /sys

One read each: the environment, the thread count, and the executor's own summary —
`sys_stat` answers with running vs stopped, no introspection ceremony:

```mtron_pre
*/sys/env/HOME
*/sys/thread/+.count()
sys_stat()
```

# file system space (`fsspace::T`)

An `fsspace::T` mounts a directory into the metatron graph. A file's uri is
`<scheme:path>`; a read returns the file's content **typed by its MIME** — the type is
a predicate on the content.

**IMPORTANT**: any uri may be wrapped in `< >`, but the brackets are *required* when
the uri carries a `.`, a space, a `~`, or a `?`. `/a/b/c` writes bare;
`<mfs:pom.xml>` and `<mfs:AGENTS.md?lineq=1-3>` must be bracketed.

## the space these examples use

The examples read the live metatron project — this very tree — and never write to it.
The route anchors on `<.>`, the cwd of the process, which in the docs build is the
project root:

```mtron_pre
fsspace::[/
  pattern => <mfs:#>, /
  q       => [mimeq::[=>], lineq::[=>]], /
  route   => [mfs: => <.>]]@/sys/space/mfs
```

The three keys: `pattern` is the uri space this instance owns (`mfs:#`, `#` the
recursive wildcard); `route` maps the `mfs:` prefix onto the path the files live at;
and `q` is the space's **query processors** — `mimeq` tags and structurally parses
reads, `lineq` addresses lines.

Writes go to a second mount, a scratch directory the docs build owns — made by
`bash`, which is exactly the point of keeping the two halves of this doc in one
conversation:

```mtron_pre
bash('mkdir -p /tmp/mtron-docs-scratch')
fsspace::[/
  pattern => <scratch:#>, /
  q       => [lineq::[=>]], /
  route   => [scratch: => /tmp/mtron-docs-scratch]]@/sys/space/scratch
```

## typed reads

```mtron_pre
*<mfs:README.md>.tid()          [-- markdown's mime --]
*<mfs:pom.xml>.tid()            [-- xml's mime --]
*<mfs:boot/docs.mtron>.tid()    [-- a .mtron file reads as the code it is --]
```

Three content types, one `*` — the tid is the referent's claim, and `.tid()` is how
it is read out. A `.mtron` file is not lines: it parses into the objs it declares, so
its referent is a `rec::T`, not a `str::T`. The probe defaults a file with no
extension to the same `application/x-mtron` type — which is a file named `lines` an
invitation to be parsed as code, and a `lines.txt` a note.

## `?mimeq` — tagging, and the structural read

`?mimeq` is a post-read query processor: it tags the string with the MIME's tid — the
tag *is* the predicate validation — and, for `application/x-mtron`, parses the content
into its structural form. Where in doubt, `?docq` and `.explain()` say what an inst
is doing — the sugar is short precisely because it is legible once:

```mtron_pre
[NO_OUTPUT] *<mfs:README.md?mimeq=text/markdown>                    [-- explicit tag, same referent typed --]
[NO_PROMPT] [MAXOUTPUT 10] *<mfs:boot/docs.mtron>                   [-- the doc boot, read as its code --]
```

## walking the tree

Wildcards are space-side. `+` is one segment, `#` is the recursion:

```mtron_pre
*<mfs:src/main/java/+/>                  [-- the child of src/main/java --]
[NO_OUTPUT] *<mfs:docs/skills/mtron/+/>  [-- this doc's siblings, with their content --]
```

## text work on the tree

A file is a string and a string splits: `-<` divides on the separator, and the rec
decides which pieces survive — here, everything before line 10:

```mtron_pre
*<mfs:AGENTS.md>.-<'\n'==[?>10 => none, _ => _]
```

## line addressing — on the scratch mount

The scratch mount is where the doc writes. `bash` authors the file in the shell's own
voice — and names it `lines.txt`, because the probe reads an extension-less file as
`application/x-mtron` code, while a `.txt` name is what makes prose prose. The space
half then addresses the same file by line: `N` or `A-B` for a slice, `N+` to insert
before line N, `+` to append; a read takes the slice, a write replaces it:

```mtron_pre
bash('printf "the metatron docs pipeline\nevaluates this block\nand inlines the result" > /tmp/mtron-docs-scratch/lines.txt')
*<scratch:lines.txt?lineq=1>                     [-- line two, zero-indexed --]
<scratch:lines.txt?lineq=1> -> "rewrote line two"  [-- replace --]
*<scratch:lines.txt>                                  [-- the file after the write --]
```

## the file family — `read_file`, `edit_file`

The same lines, as instructions, when the shape of the answer matters. A uri with a
dot in its name takes the angle brackets, even as an argument. `read_file` indexes its
lines:

```mtron_pre
read_file(file=><scratch:lines.txt>, min=>0, max=>2)
```

`edit_file` states the write as arguments — insert at `min`, replace the `min..max`
span when `max` is given — and answers with a status report:

```mtron_pre
edit_file(file=><scratch:lines.txt>, text=>'added by edit_file', min=>1, max=>1)
```

A rec written as data and read back — the round trip closes the scratch story:

```mtron_pre
scratch:meta -> [doc => 'sys-instset', revision=>2]
*scratch:meta
```

## a file as an instruction

A file that is executable is read back as an `inst::T` — the space exposes the call,
and the type carries where the space stood when it served it. `bin/metatron` shows
the shape; it is read here, never run:

```mtron_pre
[NO_OUTPUT] *<mfs:bin/metatron>
*<mfs:bin/metatron>.tid()
```

## taking the mounts down

The doc leaves the graph as it found it — the two mounts it added are removed, and
the scratch directory goes with `bash`, the tool that made it:

```mtron_pre
/sys/space/scratch -> noobj
/sys/space/mfs     -> noobj
bash('rm -rf /tmp/mtron-docs-scratch')
```

## see also

* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, `.as(type::T)`.
* [mtron language reference](language-reference-mtron.md) — `>>`, the `?`-family filters, select (`==`), split (`-<`), `->` write.
* [web instruction set](web-instset-mtron.md) — the route tables an `fsspace` mount hangs behind.
* [tble instruction set](tble-instset-mtron.md) — the same space protocol, on a database.
