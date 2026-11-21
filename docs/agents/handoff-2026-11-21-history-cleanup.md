# Handoff — history cleanup: what threads and computers leave behind

> Date: 2026-11-21. Main at `d5f005d2a`. Git is free in your own worktree and never on main: commit on your branch
> (the message ending `Co-Authored-By: @dr-stynx`), rebase on main, and hand Marko the `merge --ff-only`. Build and
> test only on ginger (`bin/metatron-remote --host ginger.local …`), one run at a time, focused classes, never the full
> suite. Nothing below is decided: start by asking Marko the open questions.

## Why this exists — read first

Two places in metatron grow without bound while a machine runs: the threads it has run and the computers it has
minted. Each execution leaves an entry that nothing removes, so a long-running machine (the live server) keeps every
one of them for as long as it runs. Marko (2026-11-21): the cleanup needs designing around its use cases, so it gets a
session of its own.

The computers, at a console, one more per expression:

```
mtron> */processor/computer/+.count()
==>28
mtron> */processor/computer/+.count()
==>29
mtron> */processor/computer/+.count()
==>30
```

The cleanup today is manual. A computer that has finished has a right-hand side, so deleting every computer with one
keeps only the evaluation in flight:

```
mtron> @/processor/computer/+.has(rhs)>>= noobj
mtron> */processor/computer/+.count()
==>1
```

## Facts in the code

Paths are under `src/main/java/studio/phaseshift/metatron/`.

### Computers

- `isa/m/mach/type/processor/BasicSwarmComputer.java:97-99` mints one computer per evaluation at
  `~/processor/computer/<n>`, with n from a JVM-wide counter (`PROCESSOR_COUNTER`). The computer is written into the
  processor of the machine it is minted in, and that write is the indexing (`BasicSwarmProcessor.java:51`).
- An evaluation's uncaught fails are filed under its computer, `~/processor/computer/<n>/fail` (`isa/m/type/impl/MFail.java:50`,
  `isa/m/mach/machInstSet.java:330`). The machine's own fail stack is `~/processor/fail` (`isa/m/mach/type/Processor.java:52`).
- A finished computer carries its result as `rhs`. Nothing deletes a computer once it has finished.

### Threads

- `ThreadExecutor` (`isa/m/sys/type/ThreadExecutor.java`) calls itself the single funnel for all metatron threading.
  Its rec, at `/sys/thread/executor`, holds two lsts, `run` and `stop` (`:89-90`). `execute(AbstractThread)` adds an
  `auto_from_(<thread vid>)` pointer to `run`, and when the task ends it moves the pointer to `stop` (`:207-219`).
  Nothing ever removes an entry from `stop`.
- The class comment describes `/sys/thread/active` and `/sys/thread/inactive`; the code keys are `run` and `stop` under
  `/sys/thread/executor`. Settle which spelling is meant.
- Every `AbstractThread` mints its vid under `~/sys/thread` (`isa/m/mach/type/thread/AbstractThread.java:134`,
  `CommonUtil.mintShortUUID(CURRENT_SYS_THREAD, true)`, which reads memory to rule out a collision), and constructing it
  files the thread rec at that vid: an obj built with a vid is written there (`Obj.Helper.objCheckAndSave`,
  `isa/m/type/Obj.java`). So a thread leaves two entries, its rec in memory and its pointer in `stop`.
- That write resolves the `~` and stamps the thread with the absolute address (`Memory.write`), so a thread's vid is
  where it is filed and its pointers read the same from any machine. Until 2026-11-21 a `VirtualThread` refused the
  stamp (its `self` override) and kept `~/sys/thread/<id>`: every reader resolved that against its own machine, so its
  doc and the executor's pointers to it missed from anywhere else.
- A `docWrap`ped thread's `Docs` rec holds the thread itself (`Docs.doc` puts `obj => <the thread>`): pruning a
  thread's rec and its `stop` pointer frees nothing while its doc still holds it.
- `run` and `stop` are `CopyOnWriteArrayList`s (`:89-90`), so every append copies the whole list: as `stop` grows, each
  new entry costs more (metatron-reviewer, 2026-11-21). A `{1..n}.timeout(...)` walk appends n alarms, O(n·|stop|).
  Whatever bounds `stop` bounds this cost too.
- `execute(Runnable)` wraps a foreign `Runnable` in a one-shot `CoreThread` (`:172-180`), so it gets the same veneer and
  leaves the same entry. `summary()` answers `[run=>n, stop=>m]`.

### Who adds entries, and how often (2026-11-21)

- Every `branch` lane at the master (`BranchInst.thread` builds a `VirtualThread`): one thread per lane per run.
- Every mtron program frame a ws server evaluates, since `d5f005d2a` (`isa/m/web/space/ws/wsServer.java:427` hands it to
  `ThreadExecutor.execute(Runnable)`): one thread per frame, whether it is a console line, a remote read, or a lane
  landing at a peer. Such a frame also mints a computer in its route's machine, so it leaves two entries.
- Since the thread-executor conversion (`refactor(thread): the raw threads run through the system executor`), six
  more sites leave entries. Five of them also write a `Docs` rec (`docWrap`, a third entry); the alarm writes none.
  - `BasicAnalyzer` publication (`VirtualThread`): at most one per step (250 ms by default) per publishing analyzer,
    whether or not anything subscribes. The network analyzer changes on every ws frame in or out, so steady traffic
    adds about 4 a second per machine. The processor's analyzer publishes while it profiles.
  - `NetworkSupervisor` redial (`VirtualThread`): per down link, one at once, then after 0.5, 1, 2, 4 and 8 s, then
    every 10 s while it stays down; none while every link is up.
  - `mathInstSet` timeout alarm (`VirtualThread`): one per `timeout()` per lhs obj, nested timeouts each their own.
  - mqtt: the handshake (`CoreThread`), one per accepted connection; the session reader (`CoreThread`), one per session.
  - `FloatingSurface` render loop (`CoreThread`): one per surface, so one per console.

## Questions to put to Marko first

1. **What is the history for?** Who reads a finished computer or thread, and how long after: the console
   (`*/processor/computer/+`), a fail investigation (`computer/<n>/fail`), `profile()` and `explain()`, `path()`, tests?
2. **What counts as finished?** For a computer, `has(rhs)`. For a thread, its move to `stop`, or its state. Do failed
   executions stay longer than successful ones?
3. **One policy or two?** Are a thread and the computer it ran one execution's history?
4. **What bounds it?** A count (the last N), an age (the last T), a size, or only an explicit sweep like today's?
5. **Where does the policy live, and who applies it?**
   - the producer, at mint or at completion (`ThreadExecutor` moving a pointer to `stop`; the processor minting
     computer n+1);
   - the collection's own address, as a query processor that holds the retention (on `~/processor/computer`, or on the
     executor's `stop`), so the policy is data an mtron program can read and change;
   - a supervisor that sweeps on a schedule.
6. **Per machine or per JVM?** `PROCESSOR_COUNTER` is JVM-wide; `~` is per machine.
7. **What may still point at a pruned entry?** `run` and `stop` hold `auto_from_` pointers to thread vids, and a
   future or a caller may hold a computer. A pruned entry should read as noobj, never as a stale value.

## Starting points

- Reproduce both counts on a fresh VM: `*/processor/computer/+.count()`, and the executor's `stop` (its rec is at
  `/sys/thread/executor`; `ThreadExecutor.instance().summary()` in Java). Run a few expressions, a sharded fold (lanes),
  and a ws round trip, and watch both grow.
- The manual sweep is the baseline any design must subsume: `@/processor/computer/+.has(rhs)>>= noobj`.
- Memories worth reading first: "Fails live in the processor" (`~/processor/fail`, per evaluation), "Metrics =
  analyzer::T", and "QProc enforcement returns fail not throw" (how a q-proc refuses a write).
