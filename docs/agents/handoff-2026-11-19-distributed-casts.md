# Handoff — the distributed asciinema casts don't distribute

> Date: 2026-11-19. Main at `ea9e34099`. First proposed on 2026-11-18 as a session card ("Make the distributed asciinema
> casts distribute"); this file keeps that write-up after the card expires. Git is free in your own worktree and never
> on main: hand Marko the commit (message file written first, in `.docker-target/`) and the `merge --ff-only`. Build and
> probe only on ginger (`bin/metatron-remote --host ginger.local …`), one run at a time, focused classes, never the full
> suite.

## Why this exists — read first

Marko wants this done, later. The casts in `docs/asciinema/mini-tutorials.md` are the play links of the menu rows in
the distributed-computing skill reference (`docs/skills/mtron/references/distributed-computing-mtron.md` embeds seven
`dist-*` casts). Their captions promise distribution, and their recordings show none, so each one undercuts the row it
illustrates.

## The problem

The distributed casts (`mini-distributed`, `dist-data-local`, `dist-keyed`, `dist-master`, `dist-move`, `dist-const`,
`dist-tree`, `dist-run`) import `/m/math` and `/m/math/cat` and dial their two peers with

```
spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
```

The rendered `docs/website/asciinema/mini-distributed.cast` (rebuilt 2026-11-18 06:57) shows both peers answering
their `ws_client` records, and then `{1,2,3,4,5,6,7,8}.plus(1).sum().explain()>>format` prints the serial plan
(`start`, `plus`, `sum`) under the caption "branch, keyed by the two peers; each folds its own part".

## What works

In a MarkdownRunner VM, `law_shard` fires once `/m/math/cat` is imported and the peers are dialed the way `harvest` in
`docs/website/skills/drstynx/assets/drstynx.boot.mtron` dials them. The Ring Theoretic Distributed Computing article
(`docs/articles/ring-theoretic-distributed-computing.md`) carries this block, and its render was checked on ginger:

```
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_OUTPUT] harvest?rec<=int{*}(uri{?}::T){ /
  spawn?uri<=int(<//localhost:${_ + 1111}/${_}>,<//${_}>,*0). /
    -<[host=>_].as(ws_client::T).barrier()>>host.>-[,].as(rec::T)}@harvest
[NO_OUTPUT] {0,1}.harvest()
```

`{0,1}.harvest()` answers `[0=>ws://localhost:1111/mtron, 1=>ws://localhost:1112/mtron]`, after which
`{1,2,3,4}.plus(1).sum().explain()>>format` prints a `branch` keyed by `ws://localhost:1111` and
`ws://localhost:1112` (lanes `start?hop=0({1,3}).plus(1).sum()` and `start?hop=0({2,4}).plus(1).sum()`), then `sum`.
Without the cat import the plan stays serial even with the peers dialed.

## The task

1. Find which difference matters: the dialing form (`ws_client::[pattern=>…]` vs `[host=>_].as(ws_client::T)`), the
   spawn's arity (two arguments vs `harvest`'s three), or the `barrier()`. A `ws_client` built with `host=>`
   self-registers into `/network/peer`, which is the set `law_shard`'s guard reads (see the class comment of
   `DistributedComputingTest`); that is the likeliest cause.
2. Switch the casts' setup accordingly. Defining `harvest` (or calling it, if the docs boot gains it) is the likely
   choice; keep each cast's own caption lines.
3. Re-render the casts and confirm that every cast's `explain()` output shows what its caption promises.

Re-render the recordings with `bin/metatron-site docs/asciinema/mini-tutorials.md` (casts only, no page; it writes
`docs/website/asciinema/*.cast`). Check how `bin/metatron-site` routes its build before running it, so it runs on
ginger.

## How to probe one page first

Stage the script and any scratch `.md` in `src/test/java/.probe/`, which is gitignored but rsynced to the build host,
then run `bin/metatron-remote --host ginger.local sh src/test/java/.probe/doc-probe.sh <file.md>`. The rendered page
lands in `.docker-target/doc-probe/`, and docs/website is never written. Move the files back out afterwards, because a
`.md` left in `src/test/java/.probe/` is newer than the jar and forces a rebuild. The script:

```sh
#!/bin/sh
# Probe one page through MarkdownRunner without touching docs/website: output goes to target/doc-probe
# (target/ in the build container is the worktree's ignored .docker-target/).
set -eu
JAR=$(ls -1 target/metatron-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ] || [ -n "$(find src pom.xml -type f -newer "$JAR" 2>/dev/null | head -n 1)" ]; then
    echo "==> jar stale or missing — mvn package (skip tests)"
    mvn -B -Dmaven.repo.local=/work/.build/m2/repository -DskipTests package -q
    JAR=$(ls -1 target/metatron-*-jar-with-dependencies.jar | head -1)
fi
rm -rf target/doc-probe; mkdir -p target/doc-probe
java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
  --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.base/sun.nio.cs=ALL-UNNAMED \
  -cp "$JAR" studio.phaseshift.metatron.docs.MarkdownRunner "$1" -o target/doc-probe > target/doc-probe.log 2>&1 || echo "==> runner exit $?"
echo "==> output files:"; find target/doc-probe -type f | head
echo "==> fail lines:"; grep -rn "fail::" target/doc-probe | head -40 || true
```

After a render, count `fail::` and `==>ERROR` lines yourself: an un-`[ERROR]`'d failure only logs, and the build goes
on. Only a fence at column 0 is evaluated.

## Traps

- **An instruction's point is lost in code shipped to a peer (open bug, 2026-11-18).**
  `start(20).id{5}().plus(10).prod()` answers 24300000 serially but 900 over two peers, and `explain()` prints nested
  code without points (`id()` for `id{3}()`). Spans survive (`{0,2}plus(1)`). Keep point examples out of the casts until
  that is fixed.
- **The suites compile differently from the docs.** Production `Compiler.apply` is parse → rewrite → resolve → type;
  the suites' `CatTheoryCompiler` (test-only) resolves before it rewrites. A green `DistributedComputingTest` row does
  not prove the cast; render it.
- **`profile()>>format` after a seed partition** at the end of a chain answers a raw rec of profilings keyed by machine,
  which `>>format` does not lay out; after a span-run partition or a chain of cuts it formats one table.

## Done when

Every `dist-*` cast's `explain()` output matches its caption, the re-rendered casts and the edited
`docs/asciinema/mini-tutorials.md` are ready for Marko to commit, and the seven play links in the skill reference show
distribution.
