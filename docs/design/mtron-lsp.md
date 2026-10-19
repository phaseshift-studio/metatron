# mtron IDE support — design

Language support for mtron in editors, with **IntelliJ IDEA as the primary target** and a
second, cheaper front for everything else (VS Code, Neovim, Helix) later.

The original recommendation was "LSP for semantics + TextMate grammar for highlighting", chosen
for wide coverage. That is right as a *shape* and wrong in its layering for this project: the
primary editor is IntelliJ, where a native lexer beats a TextMate bundle outright. This source
records what we build instead, and why.

## Status

| Track | State |
|---|---|
| The single door — `Obj parse(String)` returning `fail::T` | done — 156 tests green |
| M1 — position truth in parser diagnostics | done |
| Fault corpus (`mParserFaultCorpusTest`) | done — 38 rows green |
| M2 — expectation labels, delete the heuristics | not started |
| M3 — `ParseFault` as data, prose as a renderer | not started |
| Scanner (shared token model) | not started |
| `bin/metatron-lsp` (stdio JSON-RPC) | not started |
| `plugin/mtron-ide` (IntelliJ) | not started |

## 1. Goal and non-goals

**Goal.** In IntelliJ: correct syntax highlighting, folding, comments, diagnostics with quick
fixes, hover documentation, completion, and go-to-declaration — for `.mtron` files, including
files that are broken mid-edit. In other editors, the same diagnostics and navigational features
over LSP, without a second implementation of the language.

**Non-goals, deliberately.**

- **A PSI parser (grammar-kit `.bnf`).** This is where an IntelliJ language plugin gets large
  (5–15k lines). Everything in the goal above is reachable from a `Lexer` plus IntelliJ's
  text-level APIs. What it costs us is rename, Find Usages, and refactorings — deferred, not
  refused.
- **PEG error recovery** (cost-model token insertion/deletion). The scanner keeps the source
  painted and bracketed while it is broken; the parser stays authoritative for validity. One
  diagnostic for the first fault is the useful one.
- **The whole LSP spec.** A frozen subset (§5); everything else is refused rather than
  half-implemented.

## 2. Shape

Three artifacts, each in the place the repository already keeps that kind of thing:

| Path | What | Built by |
|---|---|---|
| `src/main/java/studio/phaseshift/metatron/lsp/` | scanner, token model, JSON-RPC server, handlers, vocabulary generator | `./mvnw` (same artifact as the VM) |
| `bin/metatron-lsp` | launcher — flags and classpath, mirrors `bin/metatron` | — |
| `plugin/mtron-ide/` | IntelliJ plugin: FileType, Lexer, highlighter, folding, annotator, daemon client | its own `build-plugin.sh` |
| `plugin/docs-build/` | the existing docs-build plugin, moved | its own `build-plugin.sh` |

### Why `lsp/` and not `isa/` or `util/`

`isa/` is for instruction sets: SPI-discovered, mounted in the Router, addressable by URI. The
language server is none of those — it is a **consumer** of the language with a `main()` and a
process lifecycle. Putting it there would invent a fake ISA domain.

`util/` is the wrong shelf too: eleven leaf classes (`Tuple`, `TriConsumer`, `MTronException`,
`IteratorUtil`, …), no `main()` anywhere, no lifecycle. A stdio server that owns a thread, a wire
contract, and a versioned capability set is not a leaf helper.

The shelf that already exists for this shape is **`docs/`**: a top-level package, 4,142 lines,
four classes with `main()`, launched by `bin/metatron-docker` through
`DOCS_RUNNER="studio.phaseshift.metatron.docs.MarkdownRunner"`. Same shape, same scale, same
launch convention. `lsp/` is its sibling.

**No `@InstSet.JREService`, no `META-INF/services` entry.** The package therefore cannot be
mounted into a VM or appear in `Machine.current().instset()` — "not an ISA" is structural, not a
convention someone has to remember. An agent-facing `/m/lsp` instset, if ever wanted, is a
separate annotated class and a deliberate decision.

## 3. Architecture

```
                     .mtron source
                           │
              ┌────────────▼─────────────┐
              │ scanner (lsp)            │  tolerant, never throws, yields
              │ token kinds + spans      │  tokens, bracket pairs, word-at-cursor
              └────────────┬─────────────┘
          ┌────────────────┼──────────────────┐
          ▼                ▼                  ▼
   IntelliJ Lexer   semanticTokens/full   vocab.json / tmLanguage
   + SyntaxHighlighter                  (generated from the language's own tables)

              ┌──────────────────────────────┐
              │ mParser (authoritative)      │  diagnostics, explain(), type/value
              │ + Prepared (position map)    │  for the live VM tier
              └──────────────┬───────────────┘
                             │ tier 1: attach over the existing MCP/WebSocket surface
                             ▼
                    running metatron VM
```

**One vocabulary, three renderers.** The token/operator surface is already data:
`Sugar` (`startToken`, `endToken`, `instChain`, `argCount`, `position`), registered by
`mInstSet.sugars()` and fed into the grammar at class-init via `mParser.addSugar`; instructions,
types, rewrites, constants and sugars are enumerable through `InstSet`'s sets (its javadoc calls
`Machine.current().instset()` "the enumeration"); every inst and type carries a `?docq`
documentation rec. A generator turns that into editor artifacts:

- `mtron.tmLanguage.json` — for editors that only do TextMate (not IntelliJ)
- `vocab.json` — completion items and hover text
- `conf/nanorc/mtron.nanorc` — regenerated, not hand-maintained

with a `--check` mode that exits `STALE` on drift, following `bin/build-wsplus.py --check`. One
table, N surfaces, and a test that catches drift — the alternative is the current situation, where
`conf/nanorc/mtron.nanorc` is a third hand-written approximation of the grammar whose comment
trail ("applied after generic `<>` coloring", "overrides generic `<>` cyan before `=>`") is a
record of regex ordering hacks.

**Out of process, always.** The VM requires `--enable-native-access=ALL-UNNAMED`,
`--add-modules jdk.incubator.vector`, and eight `--add-opens` flags; the IDE's JVM has none of
them. The existing docs plugin already mirrors those flags into a subprocess rather than loading
metatron in-IDE, and it is right to.

**Two tiers.** Tier 0 is the standalone server: scanner + parser + generated vocabulary, no
`BootLoader`. Tier 1 attaches to a running VM over the existing MCP/WebSocket surface for live
documentation, type inference, and evaluate-on-hover. Tier 0 bounds latency and failure; tier 1 is
the reason this can be a better language server than most.

## 4. IntelliJ integration without PSI

| Feature | API | PSI |
|---|---|---|
| `.mtron` file type, language | `FileTypeFactory`, `Language` | no |
| Highlighting | `Lexer` → `SyntaxHighlighter` | no |
| Folding, comments, brace matching | `FoldingBuilder`, `Commenter`, `PairedBraceMatcher` | no |
| Diagnostics, quick fixes | `ExternalAnnotator` → `Annotation` + `LocalQuickFix` | no |
| Hover docs (Ctrl+Q) | `DocumentationProvider` | no |
| Completion | `CompletionContributor` (token-based) | no |
| Go to declaration | `GotoDeclarationHandler` | no |
| Structure view | `StructureViewBuilder` + `StructureViewModel` | no |
| Rename, Find Usages, refactoring | reference resolution over the index | **yes** |

The plugin is a thin client: the daemon does the work, the plugin maps results onto these APIs.

## 5. Frozen LSP subset

`initialize` / `initialized` / `shutdown` / `exit`; `textDocument/didOpen`, `didChange`,
`didClose` (full sync — sources are small); `textDocument/publishDiagnostics`;
`textDocument/hover`; `textDocument/completion`; `textDocument/definition`;
`textDocument/semanticTokens/full`; `textDocument/foldingRange`; `textDocument/documentSymbol`.

Anything not on this list is out of scope until the listed set is genuinely good. JSON-RPC is
hand-rolled over `gson` (already a dependency) — **no `lsp4j`**, no new jar in the uber-jar.
`mcp_stdioHandler` is the in-tree precedent for a stdio JSON server, though it frames messages by
newline where LSP requires `Content-Length` headers.

## 6. Parser hardening

The motivating observation: the parser is the only component in the VM that renders its failures
into prose at the moment of failure. Everywhere else, failure is a `fail::T` with a cause chain
`MTronException` carries, attributed to a source uri, and rendered once at the reporting
boundary — `emitStackTrace` exists precisely so a logical failure prints once, not once per
retry. `parse()` does `throw MTronException.of(formatParseError(result))`: line and column
computed, then embedded in a sentence, with no source and no position field.

The formatter compounds it. `simplifyParseMessage` guesses the failure from the character at the
position; behind it `findUnclosedBefore` is a hand-written backward lexer tracking quotes,
triple-quotes, and four bracket depths with an angle-bracket operator heuristic. That is a second,
informal lexer whose only job is to explain the first one's failures — and it is wrong exactly
where the grammar is subtle, which is where help is needed. It exists because only three of
thirty-eight grammar rules call `labeled()`.

**M1 — position truth. Done.** `stripComments` deletes comment regions and `parse()` trimmed, so
diagnostics were reported in a coordinate space that does not exist in the file; `parseDiagnose`
did not strip comments at all, so the same text was valid or invalid depending on which entry
point was used. Now one `prepare()` produces the parsed text plus an index map
(`Prepared.toOriginal`), both entry points share it, and line, column, position, and buffer all
describe the source the caller sent. The explanation is still derived from the stripped buffer —
the heuristics only know the characters the grammar actually read.

**The door (done).** `Obj mParser.parse(String)` is the only parse. A source is one
expression or several; `;` is sugar for an `end()` **Inst** (an Obj), so a statement after it
does not restart the chain — `a.x(); y()` parses to `a.x().end().y()`, and a bare value after
`;` gets a `start(42)` because a value cannot be chained onto a call. Nothing is normalized
away: the `end()` the sugar produced stays in the program. Invalid input returns a `fail::T`
whose message is position-oriented (line, column, snippet, caret, explanation), minted through
the machine's fail space like any other fail; `faultOf(obj)` exposes the same information as
data.
<p>
The door ends at one Obj; execution is `eval`, which splits the program at `end()` and runs the
statements in order. The split is what makes `1.plus(2).plus(5).to(x); 244.plus(*x)` yield 252:
a whole-program `apply()` resolves `*x` once, before the first statement has written `x` (244),
whereas statement-by-statement execution re-resolves it after the write. A literal after a dot
stays a literal: `1.445` is a `real::T`, not `start(1).map(445)`. `parseMulti`, `parseDiagnose`/`ParseDiagnostic`, `maxParseIterations`, `cachedMainParser`
and the manual `;` loop are gone.

**M2 — expectations, then subtraction.** Add `labeled()` to the rules that lack it, in the
language's words ("a type name such as `int` or `my:type`", "`=>` or `,` after a record entry").
`choice()` already uses the `FURTHEST` strategy, so the deepest label wins and labels compose.
Then delete `simplifyParseMessage`, `findUnclosedBefore`, `hasMatchingCloser`, `lastNonSpace`.
**Acceptance criterion: those deletions happen.** If the heuristics are still needed, the labels
are not done. `labeled()` only changes messages, never acceptance, so the risk is low by
construction — and all 260 `.mtron` files must still parse.

**M3 — fault as data.** One `ParseFault(offset, endOffset, expected[], found, source)` carried by
both `ParseDiagnostic` and `MTronException`, as a `Fail` attributed to the source uri, rendered
only at the boundary. `formatted()` becomes a renderer over that data. The point is not the LSP:
it is that tests can assert on structure instead of sentence text, which is what makes messages
improvable without rewriting the suite every time they improve.

**M4 — minimal recovery.** Per-statement diagnostics from `parseMulti`, so one bad statement does
not hide the rest; and the scanner paints and brackets correctly while the source is broken. No
cost-model recovery.

## 7. Order of work

1. **M1** — position truth. *(done)*
2. **Scanner + vocabulary generator** — the shared token model, drift-checked.
3. **Fault corpus** — pathological snippets with structurally asserted faults; the spec M2 is
   measured against, written before M2. *(done — 38 rows)*
4. **M2**, then **M3**.
5. **`bin/metatron-lsp`** — the subset in §5, diagnostics first, end to end.
6. **`plugin/mtron-ide`** — FileType + Lexer + highlighter + folding + comments (a real IDE
   experience before any protocol), then the daemon client for diagnostics, hover, completion.
7. Later, by appetite: TextMate/VS Code front; go-to-declaration; PSI for rename and Find Usages.

## 8. Testing

- **Fault corpus** — pathological snippets (`12ab`, `.5`, `[a b]`, dangling `,`, unterminated
  quotes, `;;`, unclosed nesting) asserted structurally, not by message text.
- **`mInstSetTest` is THE gate for a grammar change** — 665 tests that exercise the instruction
  set through the parser. `mParserTest` plus the fault corpus are necessary but not sufficient:
  an over-broad chain rule passed both and broke 247 `mInstSetTest` cases in one run.
- **Golden corpus** — all 260 `.mtron` files must parse before and after any grammar change.
- **Differential scanner test** — every token boundary the parser depends on must be a scanner
  boundary; this is what keeps the tolerant scanner honest.
- **LSP harness** — headless stdio JSON-RPC driver asserting responses on fixture sources.
- **IntelliJ fixtures** — the plugin's own tests, per IntelliJ's test framework, if and when the
  plugin grows logic worth testing (most of it is mapping, and mapping is tested at the daemon).

## 9. Decisions

| Decision | Choice | Why |
|---|---|---|
| Primary editor | IntelliJ IDEA | stated; changes the layering |
| Highlighting in IntelliJ | native `Lexer` + `SyntaxHighlighter` | TextMate bundles paint only; a lexer also drives folding and is no more work |
| TextMate grammar | generated, deferred | needed for other editors, not for IntelliJ |
| Semantic engine | one Java server, LSP subset over stdio | single implementation of the language; serves IntelliJ now, other editors later |
| Client dependency | none (`gson` only) | `docs/intellij-plugin` is deliberately Gradle-free and SDK-download-free |
| PSI | deferred | everything in §1 is reachable without it; rename/Find Usages are not |
| Server location | `studio.phaseshift.metatron.lsp` | `docs/` precedent: top-level, `main()`-bearing, consumes the language |
| Plugin location | `plugin/` | one umbrella for IDE tooling |

## 10. Open questions

- Is the Gradle-free plugin harness a hard constraint (offline machine) or a preference? If it
  ever lifts, `gradle-intellij-plugin` + LSP4IJ would remove ~700 lines of client code and give
  `runIde`.
- Do rename and Find Usages matter enough to justify PSI later? If yes, that is a separate,
  larger project and should be planned as one.
