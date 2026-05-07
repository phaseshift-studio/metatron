# Answering Questions

## Question Types

| Type | Approach |
|------|----------|
| Syntax | Provide examples with explanations |
| Conceptual | High-level first, then drill down |
| Troubleshooting | Review history, suggest diagnostics |

## Getting Documentation for Any Obj

Any obj in metatron can have associated documentation. Use `?docq` suffix:

```mtron
*/path/to/obj           # The obj itself
*/path/to/obj?docq      # The obj's documentation
```

Documentation structure:
```
docs::[
  obj     => <the object>,
  dom     => 'input type description',
  rng     => 'output type description',
  args    => [0=>'first arg', 1=>'second arg'],
  desc    => 'what it does',
  example => [...]
]
```

Use this to understand unfamiliar instructions or explain them to users.

## Universal Examples (all environments)

```mtron
*/sys/space/+/                              # List spaces
*/sys/space/${space_name}                   # Space details
*/sys/env/HOME                              # Env variable
*/sys/env/+/                                # All env vars
*<file:.mtron_history>.as(bytes::T).as(str::T)  # Command history
<expression>.type()                         # Check type
```

## Deployment-Specific (adapt to user's environment)

**First:** Check `*/sys/space/${space}.pattern` to discover the URI prefix.

```mtron
# SQL table (if pattern is "acme:#")
*acme:${table}.*(_).limit(10)

# Document collection (if pattern is "mongo:#")
*mongo:${collection}.*(_).limit(10)

# Graph vertices/edges (if pattern is "g:#")
*g:V.*(_).limit(10)
*g:E.*(_).limit(10)

# File system (if pattern is "local:#")
*<local:path/to/file>                    # Read file
*<local:path/#>                          # List recursively
```

**Don't hardcode prefixes!** Discover from `pattern` field, then use that prefix.
