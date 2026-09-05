# metatron docs — IntelliJ plugin

Right-click a docs source file and **build it, then open the result** — the fast-turnaround loop
for writing website docs. Lives in the repo (next to the docs it builds), **outside the Maven
`src/` build**, following the same "self-contained tool in the repo" pattern as `dsh-plugins/…`.

## What it does

| You right-click… | It runs (via the pre-built uber-jar) | You get |
|---|---|---|
| `docs/skills/**/*.md` | `MarkdownRunner <file> -o .metatron/skills/<sub>` (single-file) | the **processed** markdown opened in the editor |
| `docs/website/adoc/*.adoc` | `AsciiDocRunner docs/website/adoc … --single-boot` (the adoc tree is one book) | **`docs/website/tractatus.html`** opened in the browser |

- **md** is a true single-file build (`MarkdownRunner` has a `singleFile` mode) — the fastest loop, and the
  file you clicked is the only file processed.
- **adoc** is a *book*: `tractatus.adoc` `include::`s the chapters, and the one viewable artifact is the
  single mega-page `tractatus.html`. So an adoc build rebuilds the bundle (with `--single-boot` to amortize the
  per-file VM boot) and opens that page. That is how the site is actually rendered — there is no standalone
  per-chapter HTML in the pipeline.

It uses the exact runners and JVM flags as `bin/metatron-build-docker docs`, just scoped to one file for md and
run locally (no docker). Build output streams to `target/docs-build.log` (a "Open log" action appears on the
result notification).

## Prerequisites

- **IntelliJ IDEA installed** on this machine — the build compiles against its local `lib/*.jar`. **No SDK
  download, no Gradle.**
- **metatron built once** so the uber-jar exists: `./mvnw install -DskipTests`
  (the plugin auto-finds `target/metatron-*-jar-with-dependencies.jar`).

## Build + install

```bash
docs/intellij-plugin/build-plugin.sh
# → docs/intellij-plugin/build/metatron-docs-plugin.zip
```

1. IntelliJ → **Settings → Plugins → ⚙ (gear) → Install Plugin from Disk…**
2. Pick `docs/intellij-plugin/build/metatron-docs-plugin.zip`
3. **Restart** IntelliJ
4. Right-click a `docs/skills/…/*.md` or `docs/website/adoc/*.adoc` → **Build and view (metatron docs)**

Rebuild + reinstall after changing the action (it's ~one file).

## Files

```
docs/intellij-plugin/
├── src/studio/phaseshift/metatron/intellij/DocsBuildAction.java   # the action (1 class)
├── resources/META-INF/plugin.xml                                    # register the action
├── build-plugin.sh                                                  # local compile → zip
└── README.md
```

## Design notes / extension levers

- **Kept core-API-only** (`AnAction`, `VirtualFile`, `FileEditorManager`, `BrowserUtil`, `Notifications`) so it
  compiles against any recent IDEA with no exotic module deps.
- **Progress**: v1 posts a "built/built→path" notification and opens the output. The slow part is the per-run VM
  boot, which the runners already amortize for adoc via `--single-boot`. A live streaming Run-console (instead of
  `target/docs-build.log`) and **md→HTML** rendering (via `SkillHtmlRenderer`) are the natural next levers — both
  small — if you want rendered HTML for markdown too, not just the processed source.
- **Not compiled by the metatron Maven build** — it's under `docs/`, not `src/`, so `./mvnw` never touches it.
  Its only coupling to metatron is the `java -cp <uber-jar> …Runner` call.
