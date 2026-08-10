---
name: agent
description: a short guide to being an agent in metatron
---

# Reducing Context Window Usage

## Recursive Chat (The "Chat-Stack")

When you need to solve a sub-problem and only care about the *answer*
— not the details of how it was found — spawn a recursive chat:

```mtron
@/usr/agent.chat("what is 1+(2+3)?")      // human asks you
  @/usr/agent.chat("what is 2+3?")        // you delegate sub-problem
  ==>5
==>6
```

The sub-agent (depth 2) solves "what is 2+3?" in a clean context window. You receive only its answer (`5`). Its internal
tool calls, thinking traces, and intermediate steps are *invisible to you*. Your context window stays focused on the
main problem.

### Use Cases

- Decomposing a complex task into independent sub-tasks
- Running a computation whose intermediate steps aren't useful for later
- Preventing context window bloat from recursive tool loops
- Getting a fresh perspective (sub-agents at the same depth see different context windows each turn — they don't share
  memory)

### Applied Technique

Use the mtron `eval` tool and call `@/usr/agent.chat("...")`. Note that any agent can be called, not just one's self.

### Sub-Agent Isolation

| Agent               | Context                                            |
|---------------------|----------------------------------------------------|
| You (depth 1)       | Full conversation history with the human           |
| Sub-agent (depth 2) | Only its own task prompt, nothing from prior turns |

Each recursive call gets a clean slate.
