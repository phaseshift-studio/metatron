---
name: answer-questions
description: How to answer questions about mtron/metatron — syntax, conceptual, and troubleshooting approaches, plus retrieving documentation via ?docq.
---

# Answering Questions

## Question Types

| Type            | Approach                            |
|-----------------|-------------------------------------|
| Syntax          | Provide examples with explanations  |
| Conceptual      | High-level first, then drill down   |
| Troubleshooting | Review history, suggest diagnostics |

## Getting Documentation for Any Obj

Any obj in metatron can have associated documentation. Use `?docq` suffix:

```mtron
*/path/to/obj           [-- the obj itself --]
*/path/to/obj?docq      [-- the obj's documentation --]
```

Documentation structure:

```mtron_pre
*/m/space/qproc/docq/docs
```

Use this to understand unfamiliar instructions or explain them to users.

## Universal Examples (all environments)

```mtron_pre
*/sys/space/+                                  [-- list spaces                --]
*/sys/space/sys                                [-- sys space details          --]
*/sys/env/HOME                                 [-- environmental variable     --]
*/sys/env/+/.filter(not(<<.has(API))).take(5)  [-- first 5 environmental vars --]
```

## Deployment-Specific (adapt to user's environment)

**First:** Check `*/sys/space/${space}.pattern` to discover the URI prefix.

```mtron_pre
[-- SQL table (if pattern is "acme:#") --]
*acme:${table}.*(_).limit(10)

[-- Document collection (if pattern is "mongo:#") --]
*mongo:${collection}.*(_).limit(10)

[-- Graph vertices/edges (if pattern is "g:#") --]
*g:V.*(_).limit(10)
*g:E.*(_).limit(10)

[-- File system (if pattern is "local:#") --]
*<local:path/to/file>                    [-- Read file --]
*<local:path/#>                          [-- List recursively --]
```

**Don't hardcode prefixes!** Discover from `pattern` field, then use that prefix.
