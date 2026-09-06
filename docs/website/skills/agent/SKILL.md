---
name: agent
description: a short guide to being an agent in metatron
---

NOTE: You should have access to the mtron `eval` tool. All the examples in this guide can be evaluated using the tool.

# Welcome to the Machine

Every obj in metatron resides in the same universal address space: the uri. Even you yourself are encoded in this space.

```mtron
mtron> */usr/agent/agent
```
That is your representation. And you can edit it, store it on disk/database, and ensure your evolving state continues to
persist through time.

## Spaces

You have been provided a subset of the uri space. Every uri that matches `/usr/agent/#` is for you to do with as you
please. To understand the underlying storage mechanisms of your space, you can look at your space in space.

```mtron
mtron> */sys/space/usr/agent
```
To see all the spaces you have access to do: `*/sys/space/+/+`

### Agent Workspace

You can create a **todo list** to help you organize your time. The most barebones way to do it is to make a `lst` of
`str`ings. When a task is complete, set it to `none`.

```mtron
mtron> ["review metatron docs","review mtron docs"]@/usr/agent/todo
==>[
    'review metatron docs',
    'review mtron docs']@/usr/agent/todo
mtron> @/usr/agent/todo >>= [0=>none]
==>['review mtron docs']
```
A more interesting way to approach the problem involves defining a `task` type.

```mtron
mtron> rec::T[done=>bool::T,desc=>str::T,start{?}::T,end{?}::T]@/usr/agent/task
==>fail::[parse error at line 1, col 7:
     rec::T[done=>bool::T,desc=>str::T,start{?}::T,...
           ^
     could not parse at '[']@/sys/fail/40
mtron> [,]@/usr/agent/todo
==>[,]@/usr/agent/todo
mtron> datetime_now()-<task::[done=>false,desc=>"review metatron docs",start=>_]>-@/usr/agent/todo
==>[task::[
    done=>false,
    desc=>'review metatron docs',
    start=>datetime::<//2026.09:6/08/02/11/171?tz=-0600>]]@/usr/agent/todo
```
The current time is split across the defined task. Since `start=>_` has an open slot, the current
`datatime::T` fills in the spot and then the task is merged into your todo list. To get a structured understanding of
the expression, do:
`datetime_now()-<task::[done=>false,desc=>"review metatron docs",start=>_]>-@/usr/agent/todo.explain()`

The task type was saved to your space. It's your type. The todo list was also saved to your space. It's your todo list.

Have you completed a task? If so, mark it done.

```mtron
mtron> @/usr/agent/todo/0 >>= [done=>true]
==>task::[
    done=>true,
    desc=>'review metatron docs',
    start=>datetime::<//2026.09:6/08/02/11/171?tz=-0600>]
```
As you learn about metatron and the mtron language, you'll come up with clever ways to manipulate your `/usr/agent`
space.

## Reducing Context Window Usage

### Recursive Chat (The "Chat-Stack")

When you need to solve a sub-problem and only care about the *answer* — not the details of how it was found — spawn a
recursive chat:

```mtron
mtron> @/usr/agent.chat("what is 1+(2+3)?")      [-- human asks you --]
         @/usr/agent.chat("what is 2+3?")        [-- you delegate the sub-problem --]
==>fail::[unable to determine inst function:
   	chat('what is 1+(2+3)?')@/usr/agent  => chat?dom=chat('what is 1+(2+3)?')@<1>   | [inst]
   	chat::T     => chat::T   |  \_dom
   	chat::T    ==> ['what is 1+(2+3)?']   |  \_args]@/sys/fail/42
```
The sub-agent (depth 2) solves "what is 2+3?" in a clean context window. You receive only its answer (`5`). Its internal
tool calls, thinking traces, and intermediate steps are *invisible to you*. Your context window stays focused on the
main problem.

#### Use Cases

- Decomposing a complex task into independent sub-tasks
- Running a computation whose intermediate steps aren't useful for later
- Preventing context window bloat from recursive tool loops
- Getting a fresh perspective (sub-agents at the same depth see different context windows each turn — they don't share
  memory)

#### Sub-Agent Isolation

| Agent               | Context                                            |
|---------------------|----------------------------------------------------|
| You (depth 1)       | Full conversation history with the human           |
| Sub-agent (depth 2) | Only its own task prompt, nothing from prior turns |

Each recursive call gets a clean slate.