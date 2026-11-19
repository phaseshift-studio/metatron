# Skill reference docs — agent reference

Moved out of [AGENTS.md](../../AGENTS.md) on 2026-10-21 so that file holds only the rules every session needs; AGENTS.md
links here where it applies. AGENTS.md's "Skills and docs" section is the summary.

## Skill Reference Docs

The skills are written under `docs/skills/`. The build renders them into `docs/website/skills/`, which is what
`.metatron/skills` links to, so never edit through that link:

| Skill        | Path                     | Purpose                                                               |
|--------------|--------------------------|-----------------------------------------------------------------------|
| **metatron** | `docs/skills/metatron/`  | VM architecture, type system, boot process, MCP, UI architecture      |
| **mtron**    | `docs/skills/mtron/`     | Language reference, examples, training pipeline, data-source adapters |

Each skill has:

- `SKILL.md` — index with a **References** section listing all `.md` docs
- `references/*.md` — detailed topic docs (architecture, patterns, language spec)
- `scripts/` — Python utilities (MCP client, training)
- `assets/` — datasets, READMEs

### When to Update

Whenever you create, rename, or significantly change a Java class that is documented in a reference `.md`, update the
corresponding file:

| You changed…                                                              | Update…                                                                                    |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| A widget or UI class (`uiInstSet`, `TreeWidget`, `PanelWidget`, any tool) | `docs/skills/metatron/references/ui-instset-java.md`                                       |
| A type system class (`Type`, `MType`, `Inst`, `Code`)                     | `docs/skills/metatron/references/type-system-java.md` or `…-mtron.md`                      |
| A rewrite class (`RewriterBuilder`, `DBRewriteBuilder`)                   | `docs/skills/metatron/references/rewrite-system-java.md`                                   |
| A space class (`tbleSpace`, `fsSpace`)                                    | `docs/skills/metatron/references/tble-space-java.md` or create a new space doc             |
| mtron language syntax or semantics                                        | `docs/skills/mtron/references/mtron-language-reference.md`                                 |
| An MCP server/client class                                                | `docs/skills/metatron/references/mcp-mtron.md` or `docs/skills/mtron/references/mcp-server-*.md` |
| A new data-source integration (`dckrSpace`, etc.)                         | `docs/skills/mtron/references/` — create a new doc                                         |

### How to Update

1. **Add new classes to the package map** — every reference doc has a tree listing of Java files in the relevant
   package. Add new files there.
2. **Add new types to relevant tables** — e.g., the Tool→Widget dependency table in
   `ui-instset-java.md`, or type registration tables elsewhere.
3. **Update counts, examples, or signatures** if the change invalidates them.
4. **Create a new `references/<topic>.md`** when adding a substantial new subsystem (a new space backend, a new
   instruction set family, etc.). Follow the `---` YAML frontmatter convention with `name` and `description` fields. Add
   the new file to the **References** list in the parent `SKILL.md`.
5. **User-directed:** only create or update `.md` files when the user explicitly asks you to, or when the change is
   clearly mechanical (adding a new class to an existing package map). For substantive documentation rewrites, confirm
   with the user first.

**IMPORTANT**: edit a skill in `docs/skills/`, never its rendered copy in `docs/website/skills/` (which is where
`.metatron/skills` points). Which other parts of `docs/website/` are build output, and the source of each, is the table
under "Skills and Docs" in [AGENTS.md](../../AGENTS.md).

### Existing Docs

- **metatron skill**: `references/` covers the type system (Java + mtron), UI architecture, rewrite system, tbleSpace,
  and MCP client/servers.
- **mtron skill**: `references/` covers the language reference, MCP server architecture, math instructions, HTTP
  fetching, dckrSpace, answer-questions patterns, and the Unsloth training pipeline.

---
