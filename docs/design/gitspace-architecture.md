# gitSpace — a repository as a space

*Status: design sketch, 2026-10-21. Nothing is implemented. It began as a half joke while a worktree branch was
being landed on main, and the joke holds up.*

*Companion docs:*
- *`addressable-architecture.md`: the contract every space satisfies.*
- *`distributed-patterns-menu.md`: law_shard, and "construction is connection".*
- *The dckrspace skill doc: the nearest external-system space.*
- *The ide skill (`docs/skills/ide/SKILL.md`): the agent coding harness that a gitSpace would give a past.*

*Statements about today's code carry `file:line`, with paths under `src/main/java/studio/phaseshift/metatron/`.
Everything else is a proposal until Marko rules on it: the uri layout, qproc names, packaging and backend.*

---

## 0. The thesis, in one paragraph

A git repository is already the shape metatron is built around: a content-addressed graph of recs whose edges are
addresses. A commit is a rec (tree, parents, author, committer, message). A tree is a rec of names to tree or blob
addresses. A ref is the one kind of mutable address, and it points at a commit.

metatron's uri graph is that structure. `>>` walks a reference, `*` crosses to a value, and `!*` is a lazy link
resolved on demand. So a gitSpace is not an integration. It is a *reading* of the object database as uri space, plus
two disciplined kinds of write: minting an immutable object, and moving a guarded ref. What git "does" (log, ancestry,
diff, fast-forward) becomes mtron over that graph, and the rules about who may move what become qprocs on the
addresses they protect.

## 1. Where it sits: `/m/dev` (Marko, 2026-10-21)

```
/m/dev          the developer vocabulary; whatever the three share lands here
/m/dev/dckr     where a project runs      (today /m/dckr: isa/dckr/dckrInstSet.java:59,62)
/m/dev/git      a project's past          (new; this document)
/m/dev/ide      a project's present       (today /m/ide; the agent coding harness)
```

Projects already live as data under `/dev/<project>`. The ide skill's example is
`<mfs:src/test/resources/scratch>@</dev/scratch>.as(project::T)`. So `/m/dev` is the vocabulary of `/dev`, and each
child adds one dimension to a project:

- **ide** holds the source as it is now: the `code` and `idx` reprojections, edits, and a save that writes back to
  `mfs:`.
- **git** holds the source as it was, and decides which states are recorded.
- **dckr** is where the project builds and runs.

What lives at `/m/dev` itself is open (§11). `project::T`, today ide's, is the obvious first tenant.

## 2. Why: the rule should live where the writes land

Landing this branch on main took four steps on 2026-10-21:

1. A commit in the worktree. The agent's attempt was denied by the auto-mode classifier as "Git Destructive", and
   AGENTS.md bars agents from git anyway.
2. A rebase that had to run *in* the worktree, because the branch is checked out there.
3. A test run on the rebased tree.
4. `merge --ff-only` on main.

Every step is a read or a write of refs and objects. The safety rule was held in two places, neither of them the
repository: a sentence in AGENTS.md, and a classifier that pattern-matches shell commands.

In a gitSpace the rule lives where the writes land. A qproc on `git:refs/#` answers `fail::T` for a forbidden move,
which is how qprocs already block writes (§6). The rule is then inspectable (`?docq`) and enforced on every path,
whether console, MCP or a shipped workload, not only on the shell commands a classifier happens to recognize.

Reads become free. History is data, an agent can analyze a repository without being able to move it, and
`git log` is a `>>parent` walk.

## 3. The object model (proposal)

| address                       | value                                                                              | mutable |
|-------------------------------|------------------------------------------------------------------------------------|---------|
| `mgit:HEAD`                   | `!*mgit:refs/heads/<branch>` (a symbolic ref is a link to a link)                  | yes     |
| `mgit:refs/heads/<branch>`    | `!*mgit:commit/<sha>`                                                              | yes     |
| `mgit:refs/tags/<tag>`        | `!*mgit:commit/<sha>` or `!*mgit:tag/<sha>`                                        | yes     |
| `mgit:commit/<sha>`           | `[tree=>!*mgit:tree/…, parent=>{!*mgit:commit/…}, author=>[…], committer=>[…], message=>'…']` | no |
| `mgit:tree/<sha>`             | `[<name>=>!*mgit:tree/… or !*mgit:blob/…, …]`                                      | no      |
| `mgit:blob/<sha>`             | the file's bytes (or a `str` through `?mimeq`)                                     | no      |
| `mgit:tag/<sha>`              | `[object=>!*…, tagger=>[…], message=>'…']`                                         | no      |

The scheme is chosen when the space is constructed (`mgit:` here, for the metatron repository), the same way
`tblespace::[pattern=>mydb:#,…]` and `fsspace::[pattern=>mfs:#,…]` choose theirs.

- **The vid is the hash.** An object's address is the digest of its content, so the address doubles as an integrity
  check. This is the strongest form of the VID-consistency mandate: the same object has the same vid in every space
  and on every peer.
- **Refs are the only mutable addresses.** Every rule in §5 and §6 is about them.
- **DataPath fits the objects, not the refs.** `DataPath` (`furi/DataPath.java:74`) splits a uri positionally into
  db, collection, entry, field and extension (`withoutDB` at `:148-150`).
  - Objects fit it as collection/sha/field: `mgit:commit/c5328ae38/message`.
  - A tree path is the extension: `mgit:commit/c5328ae38/tree/docs/design/…`.
  - Refs have hierarchical names (`refs/heads/feature/x`), so `refs/#` is path-addressed the way fsSpace is.
    A ref resolves to a commit before any tree path applies.

## 4. Reads

The gitspace mounts like the ide skill's fsspace (`fsspace::[pattern=>mfs:#, route=>[mfs:=><.>]]`), with the route
naming the repository root. These are sketches, not run, because there is no gitSpace yet:

```mtron
gitspace::[pattern => mgit:#, route => [mgit:=><.>]]@/sys/space/git/mgit
*mgit:HEAD                                              [-- !*mgit:refs/heads/main --]
*mgit:refs/heads/+                                      [-- every branch --]
*mgit:refs/heads/main>>message                          [-- through the link to the commit's message --]
*mgit:refs/heads/main.repeat(code=>>>parent,emit=>true).take(10)>>message   [-- git log -10 --]
*<mgit:commit/c5328ae38/tree/docs/design/distributed-patterns-menu.md>      [-- a file as of a commit --]
```

- **Links are built the way dckrSpace builds them.** `refreshContainers` (`isa/dckr/dckrSpace.java:206-300`)
  emits `auto_from_(uri(...)).tryToInst()` for container→image, image→container, networks and mounts. `!*` is
  `/m/inst/auto_from` (`isa/m/mInstSet.java:158`), implemented as `Machine.read(uri).autoResolve(lhs)`
  (`isa/m/type/Obj.java:1497`). Parents and tree entries are the same kind of link, so a walk reads only what it
  touches.
- **`log` needs no new instruction.** `repeat` exists (`/m/inst/repeat`, `isa/m/mInstSet.java:172`; rec args `code`,
  `until`, `emit`, `esc` at `isa/m/type/Obj.java:1448-1495`). mInstSetTest's
  `1.repeat(code=>plus(1),until=>loop().is(eq(5)),emit=>true)` gives `{1,2,3,4,5,6}`. A log is the same shape with
  `code=>>>parent`.
- **Merges make the walk a DAG.** A commit reachable along two paths arrives twice. If equal commits bulk the way
  equal acks do in the law_shard latch (`{3}34`), a commit's coefficient in a log counts the paths that reach it, and
  `unique()` turns the walk back into a set. Unverified, but it would make "how many lines of history reach this
  commit" a free read.
- **Ancestry, diff and blame are idioms, not instructions, until proven otherwise.**
  - Ancestry is reachability over `>>parent`.
  - A diff starts as two tree recs compared key by key.
  - Blame is a log projected through a path, keeping the commits where the blob address changes.

## 5. Writes: two kinds of address, two disciplines

metatron has two write primitives, `->` (ref) and `>>=` (update). A gitSpace needs only `->`, applied two ways.

- **Objects are minted, never written to.** A write to `mgit:blob/_` stores the content and answers it at
  `mgit:blob/<sha>`. This is how `incrQ` mints a counter for a `_` segment (`furi/q/QCollection.java:634-660`) and how
  `vecIncrQ` mints a UUID (`isa/vec/space/vecIncrQ.java:73-80`), with the content's digest in place of the counter.
  Whether that is the space's own behavior or a `?hashq` is open.
  - Writing different content to an existing `<sha>` is a contradiction and answers `fail::T`.
  - Objects behave as if every address had been written `?constq`. `constQ` (`QCollection.java:366-383`) fails a
    second write with `%s is a constant`, and the gitSpace should answer with the same failure.
- **Refs are moved with compare-and-swap.** `mgit:refs/heads/main -> !*mgit:commit/<sha>` moves a branch, and `-> noobj`
  deletes one, by the general rule that a noobj write is a delete.
  - The move is atomic against the old value: JGit's `RefUpdate`, or `git update-ref <ref> <new> <old>`.
  - A ref that moved underneath the writer answers a fail, never a lost update.
- **A commit is three writes:** the tree objects (from a saved working tree or a rec), the commit object, then the
  ref. Only the third is guarded.
- **Guards run before side effects.** This is dckrSpace's lesson. Its `write` override
  (`isa/dckr/dckrSpace.java:474-545`) runs the docker command *before* any cache write, so its qprocs see the change
  only after it has happened.
  - A gitSpace must extend `AbstractSpace` and implement `directWriter` instead.
  - Then `AbstractSpace.write` (`isa/AbstractSpace.java:122-147`) runs `QProc.Layer.write` (`furi/QProc.java:415-424`):
    pre_write, then qless_write, then the space's own write.
  - If either qproc answers, the ref never moves.

## 6. Guards: the AGENTS.md rule as a lock

`lockQ` already exists (`furi/q/QCollection.java:724-765`):

- Writing `?lockq` with `lock::[usr=>…,expire=>…]` registers a lock on the address; a noobj releases it.
- Its qless_write blocks every write to a matching, unexpired lock with
  `write blocked: <uri> is locked by <usr>`.
- The lock's owner may re-enter, but only once the writer can be identified.

```mtron
<mgit:refs/heads/main?lockq> -> lock::[usr=>marko]      [-- sketch: main is Marko's --]
mgit:refs/heads/main -> !*mgit:commit/c5328ae38          [-- fail: write blocked: … is locked by marko --]
```

Today that block applies to everyone. `currentOwner()` resolves to noobj until threads carry an `owner` field (the
comment at `QCollection.java:749-750`), so the owner's own move fails too. The human releases the lock, moves the ref
and locks it again, which is close to what happened on 2026-10-21, except that it would be enforced by the repository.

Owner-aware re-entry is the missing piece, and it is not git's alone. A gitSpace would make "who is writing" urgent
for metatron as a whole: the console, an MCP session and a peer's shipped workload are different writers today only by
accident.

A second guard is new: **fast-forward only**. A ref may move from `old` to `new` only if `old` is reachable from `new`
through `>>parent`, the same walk as `log`. Its natural home is the ref's pre_write or qless_write, as a gitSpace
qproc; `?ffq` is a placeholder name.

## 7. With the ide: a project's past

The ide resolves a project's source "through its routed space" (ide skill, the pull: `src/<cls>()`). Today that
space is the fsspace (`mfs:`).

- **Reading the past.** Routing the same project through `mgit:refs/heads/main/tree`, or through a commit, gives the
  same `code` and `idx` reprojections *as of that state*, read-only. An agent can compare a class across two commits
  with the machinery it already uses for one.
- **Recording the present.** The harness's save already writes `code` back to `mfs:` through a `?subq` listener. A
  commit snapshots the saved fs subtree into tree objects, mints the commit, and asks to move the ref. The ref move is
  the step the lock governs, so an agent can propose history (objects and a commit) without being able to publish it
  (the ref).
- **Not in the first cut:** the index (staging area). It is a tree rec under construction (`mgit:index/<path>`), and
  it waits until the read path and the ref discipline are proven.

## 8. Implementation sketch (Java)

- **Package** `isa/dev/git/`, holding:
  - `gitInstSet.java`, with `@InstSet.JREService(vid = "/m/dev/git")`;
  - `gitSpace.java`;
  - `ObjGitSerializer.java`, which maps objects to recs and builds the links.
  This follows `isa/dckr/` and `isa/tble/`, under Marko's `/m/dev` hierarchy.
- **The space** extends `AbstractSpace<Repository>`. It overrides the `directReader` / `directWriter` hooks
  (`isa/Space.java:143-149`), as tbleSpace does. It does not override `read`/`write`, which dckrSpace does at the
  cost described in §5.
  - The constructor is `(sjvm, config, tid, vid)` (`isa/AbstractSpace.java:52`). It registers itself through
    `Machine.current().memory().addSpace(this)` (`:68-69`); the Router is gone (`isa/mach/type/Memory.java:122`).
- **The type** is `gitspace::T`, with vid `/m/dev/git/space/gitspace`, by analogy with `/m/tble/space/tblespace` and
  `/m/dckr/space/dckrspace`.
  - Its isaPredicate requires the `route` to a repository root.
  - It is listed in the instset's TYPE list in `setup()` and in
    `src/main/resources/META-INF/services/studio.phaseshift.metatron.isa.m.type.InstSet`.
  - Construction runs through `Obj.Helper.construct` (`isa/m/type/Obj.java:1196-1224`), as every space's does.
- **Backend: JGit or the git CLI.** Neither is decided; see §11.
  - **JGit** (`org.eclipse.jgit`) is not in the pom today. It reads loose and packed objects in-process and gives
    `RefUpdate` compare-and-swap. A log over thousands of commits would cost no subprocesses. It is licensed EDL 1.0,
    which is BSD-style.
  - **The CLI through zt-exec** is already a dependency (`pom.xml:175-179`) and is dckrSpace's precedent
    (`isa/dckr/dckrSpace.java:950-995`). It would use plumbing only: `cat-file --batch` as one long-lived reader,
    `hash-object -w`, `mktree`, `commit-tree`, and `update-ref <ref> <new> <old>` for compare-and-swap.
  - The lean is JGit for reads at scale, but adding a dependency is Marko's call.
- **Tests** are CI-agnostic, with no network.
  - `@BeforeAll` builds a small fixture repository in a temp directory: a few commits, one merge, one tag.
  - Rows are `@ParameterizedTest` + `@CsvSource` (`%` delimiter) mtron reads over the fixture.
  - Where the shared space suite applies, its write tests are skipped with `@SkipRegexTest` until phase 3.

## 9. Distribution (later)

- **Construction is connection.** A `gitspace::[…]` rec shipped in a workload constructs a gitSpace on the peer, over
  the peer's own clone, the way a shipped `tblespace::[…]` connects the peer to its database.
- **Content addressing makes peers easy.** The same sha is the same obj on every peer. "Do you have this object" is a
  read that is not noobj, and a fetch copies the reachable objects whose reads are noobj. There is no negotiation
  protocol to design: the uri is the negotiation.
- **law_shard over history.** A long history analysis shards by commit range, one range per satellite, with merge()
  as the latch, exactly as the multi-server tests shard a table by partition.

## 10. Phases

1. **Read-only object database and refs.** Commit, tree, blob and tag objects; refs and HEAD; `!*` links; path
   reads; tested over the fixture repository.
2. **History idioms.** `log`, ancestry, diff and blame as mtron. A new instruction is added only if an idiom proves
   too long to write.
3. **Writes.** Object minting (write-once), ref compare-and-swap, the `lockQ` guard and the fast-forward guard.
4. **The ide.** A project's source routed through `mgit:`, and commits from the harness.
5. **Distribution.** Gitspaces shipped to peers, fetch by reachability, and law_shard over history.

## 11. Open questions (Marko's to rule on)

- **Scheme.** One scheme per repository (`mgit:#`), or one `git:` scheme with the repository as authority
  (`git://metatron/refs/heads/main`)?
- **Short shas.** Should prefix reads be allowed (`mgit:commit/c5328ae38`), with an ambiguous prefix answering
  `fail::T`?
- **Blob values.** Bytes, a `str`, or the mime refinements (`?mimeq`, e.g. `java::T` for a `.java` blob)?
- **Who is writing.** Identity for `lockQ`'s owner check. Threads carry no `owner` yet; the gitSpace needs one, and so
  does the rest of metatron.
- **Minting.** Is content-addressing the space's own behavior, or a `?hashq` qproc, given that `?incrq` is the
  precedent?
- **Backend.** JGit or the CLI (§8)?
- **Merge and rebase.** Instructions in `/m/dev/git`, or mtron programs over the graph (replay = re-mint commits
  with new parents, then one ref move)?
- **What lives at `/m/dev` itself.** `project::T`? The guards? A `workspace::T` binding a project's fs, git and
  container together?
- **The move.** Relocating `/m/dckr` to `/m/dev/dckr` and `/m/ide` to `/m/dev/ide` renames tids that appear in
  docs, boot files and tests. The `docker:` and `mfs:` schemes are unaffected, since schemes are chosen at
  construction.
