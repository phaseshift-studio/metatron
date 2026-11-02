# Driving and diagnosing the console — agent reference

Moved out of [AGENTS.md](../../AGENTS.md) on 2026-10-21 so that file holds only the rules every session needs; AGENTS.md
links here where it applies.

## Driving the console in a pty (`bin/metatron-console`)

Some behaviour only exists on a terminal: raw-mode keystrokes (`alt+b`, `ctrl-c`, arrows), the jline
keymap, history, type-ahead, prompts, widgets, and the console's input handoff. A pty is the only way
to exercise it, so `bin/metatron-console` boots a metatron VM with a console **in a pty** (python
`pty.fork` — no `docker -t` needed), plays a step script at it, asserts on what came back, and prints
a timestamped transcript.

```bash
# isolated — the agent standard, same container as the build/test loop (no ports)
bin/metatron-docker build console --steps bin/test/console-smoke.steps
bin/metatron-docker build console --run 'WAIT_CONSOLE; SEND 1; WAIT ==>1'

# on the host (needs python3)
bin/metatron-console --steps bin/test/console-smoke.steps
bin/metatron-console --help           # every flag + the full step reference
bin/metatron-console --list-keys      # KEY names: alt+b, ctrl+c, up, tab, shift+tab, ...
```

**What it gives you**

| Capability | Detail                                                                                                                                                                                                                    |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| boot       | default profile `boot/console.boot.mtron` — console-only, **no ports bound**, ~3s boot, safe beside the live server (`--boot` for any other profile)                                                                      |
| safety     | the harness *refuses* a boot file that mentions 8555/8777 unless `--allow-ports`; the VM runs in a throwaway temp cwd (`--cwd repo` to override) so the user's `.metatron.history` and other repo state are never touched |
| versions   | `--from jar` (default, the uber jar from `bin/metatron-docker build`) or `--from classes` (target/classes + `.mtron-classpath` → no jar build needed for a dev loop)                                                      |
| keys       | `KEY alt+b`, `KEY ctrl+c`, `KEY up`, `TYPE` (no Enter), `SEND` (with Enter), `RAW b"\x1bb"` escape hatch                                                                                                                  |
| assertions | `WAIT_CONSOLE`, `WAIT <text> [s]`, `WAIT_SINCE <mark> <text> [s]`, `SINCE <mark> <text>`, `SILENT <mark> <text> [s]`, `MARK`, `SLEEP`, `ECHO`, `NOTE`, `ELAPSED`                                                          |
| matching   | case- **and** ANSI-insensitive, so `WAIT background` matches a colourised banner; failures print the transcript since the nearest mark                                                                                    |
| output     | timestamped transcript, `--transcript FILE` (ANSI-stripped), `--raw FILE`, `--quiet`, `--cols/--rows`, `--timeout`; **exit code is non-zero if any check failed**                                                         |

`bin/test/console-smoke.steps` is the worked example and the console regression suite — evaluation,
history recall via `KEY up`, type-ahead kept while a job runs, `alt+b` backgrounding (asserting the
prompt returns *before* the job finishes and the job's result still lands afterwards), ctrl-c
stopping a job, and the `[q]` cancel offer. Run it after any console/UI change, and copy it as the
starting point for your own scenario (`--steps my.steps`).

**Cautions**

- An image built before python3 was added cannot drive a console — the wrapper says so; rebuild with
  `bin/metatron-docker build image`.
- `--from classes` needs `target/classes`, `src/main/resources` and `.mtron-classpath` (run
  `bin/metatron` once, or `./mvnw compile` + `./mvnw dependency:build-classpath -Dmdep.outputFile=.mtron-classpath`).
- Assert on text, never on cursor position or colours (the pty is 110x40 by default; the console
  renders differently at other sizes).
- The harness never publishes ports and boots a portless profile by default — keep it that way, and
  never point `--boot` at a profile that binds the live server's ports.

## Diagnosing a live console from an agent (file traces)

When a bug is in a UI someone else is driving — a click that does nothing, a key that is swallowed,
a read that never returns — the agent cannot see the terminal, and printing a diagnostic does not
reach the agent anyway. The technique that works, and has paid for itself repeatedly:

- **Write the trace to a file inside the repo.** An agent sandbox has its own `/tmp` and its own PID
  namespace: it cannot read the user's `/tmp` and cannot see the user's process. The repository is
  the one place both sides can see. Default the path to `System.getProperty("user.dir") +
  "/target/<name>.log"`, override with a system property, and append with
  `Files.writeString(..., CREATE, APPEND)`.
- **Never print the trace to the transcript.** Anything written at the prompt scrolls the rows under
  the pointer, which is enough to break the very gesture being examined (a double click outright, and
  a click diagnostic that prints on the first press makes the second press land on a moved row). A
  screen diagnostic cannot diagnose the screen.
- **A trace must never throw.** Wrap the whole write in a catch that swallows: a debugging aid that
  can break the console it is watching is worse than no debugging aid.
- **Log the decision, not the symptom.** The line that settles a UI bug names the values that chose
  the branch — thread name, the flag that gated it, the row and column, what was found there. "no
  output" is then a diagnosis of its own: the press never reached the console.
- **Ask for one reproduction, then read the file yourself.** One click (or one keystroke) plus a file
  the agent can open replaces a round of blind edits. Say "do it once and tell me you did", not
  "paste the log".

Two things that make the loop fast enough to be worth it:

- Compile with **`.build/jdk/bin/javac`** — it is Temurin 25, the same major the build uses. Do not rely on
  a PATH `javac`: a different major emits a class-file version the runtime rejects with
  `UnsupportedClassVersionError`. A targeted
  `javac -cp "target/classes:src/main/resources:$(cat .mtron-classpath)" -d target/classes <files>`
  has no class-file-version trap.
- A dev checkout (`bin/metatron` / `bin/drstynx`) runs `target/classes` and recompiles from source
  when it is stale — so a targeted `javac` into `target/classes` reaches the user's console at the
  next restart, with no jar build. Do not add `-sourcepath`: pulling in unrelated sources drags in
  module-flagged ones (`com.sun.net.httpserver`) and fails.

**Clean up when the bug is dead, and remove trace call sites by exact anchor.** Deleting them by
index span (from a marker comment to the next one) takes whatever sits between the two — once an
entire block of neighbouring members went with them, and only the compiler noticed. Delete the exact
strings, compile immediately, and delete the log file.
