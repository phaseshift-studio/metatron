w# Metatron Training Documentation

This directory contains structured training materials for understanding and working with Metatron.

## What is Metatron?

**Metatron** is a meta-layer over computing systems that provides a unified abstraction for working with any data system (SQL databases, document databases, graph databases, filesystems, etc.) as if they were all part of a single, navigable graph.

The name comes from **META** (abstraction layer) + **TRON** (computing, 80s style) - it's the "Grid" that unifies all data systems.

## Learning Path

### 1. Core Concepts
Start here to understand the fundamental ideas:
- [What is Metatron?](01-concepts/01-what-is-metatron.md)
- [Instruction Sets](01-concepts/02-instruction-sets.md)
- [Spaces and Routing](01-concepts/03-spaces-and-routing.md)
- [Pattern Matching](01-concepts/04-pattern-matching.md)
- [The Universal Reference System](01-concepts/05-universal-references.md)

### 2. Language and Environment
Learn the mtron language and metatron environment:
- [Mtron Language Syntax](02-language/01-mtron-syntax.md) - Complete language reference
- [Metatron Environment](02-language/02-metatron-environment.md) - Spaces, routers, monads, machines, serializers

### 3. Practical Examples
Learn by doing:
- [Basic Operations](02-examples/01-basic-operations.md) - Examples from mInstSetTest.java
- [Advanced Patterns](02-examples/02-advanced-patterns.md) - Real-world usage from boot.mtron
- [Basic Data Reading](02-examples/01-basic-reads.md)
- [Pattern Wildcards](02-examples/02-pattern-wildcards.md)
- [Field-Level Access](02-examples/03-field-access.md)
- [Writing Data](02-examples/04-writing-data.md)

### 4. Advanced Topics
Deep dives into specific features:
- [Query Optimization with Rewrites](03-advanced/01-rewrites.md)
- [Foreign Key Traversal](03-advanced/02-foreign-key-traversal.md)
- [Creating Custom Spaces](03-advanced/03-custom-spaces.md)

### 5. Design Principles
Understanding the architecture:
- [Separation of Concerns](04-architecture/01-separation-of-concerns.md)
- [Router vs Space vs Helper](04-architecture/02-router-space-helper.md)
- [The Universal Graph Vision](04-architecture/03-universal-graph.md)

### 6. Common Patterns and Pitfalls
Learn from real debugging sessions:
- [Common Mistakes](05-patterns/01-common-mistakes.md)
- [Best Practices](05-patterns/02-best-practices.md)
- [Debugging Guide](05-patterns/03-debugging-guide.md)

## Quick Reference

### Basic Operations
```java
// Read a single object
Obj user = Router.readFromSpace(f("db:users/1"));

// Read with pattern wildcard
Obj allUsers = Router.readFromSpace(f("db:users/+"));

// Read a specific field
Obj userName = Router.readFromSpace(f("db:users/1/name"));

// Write data
Router.writeToSpace(f("db:users/1"), rec(uri("name"), str("Alice")));
```

### Pattern Syntax
- `+` - Single-level wildcard (matches one segment)
- `#` - Multi-level wildcard (matches remaining segments)
- Pattern must be in URI: `db:users/+` not just `users/+`

### Key Principles
1. **Use Router, not directReader()** - Router handles path translation
2. **Spaces are minimal** - They just return raw data
3. **`*` is console syntax** - Don't use in Java code
4. **Patterns need wildcards** - `db:#` not `db:`

## Contributing

This training documentation is a living resource. As Metatron evolves, these docs should be updated to reflect:
- New features and capabilities
- Lessons learned from debugging
- Common questions and answers
- Real-world usage patterns

## For AI Training

These documents are structured to be used for fine-tuning AI models on Metatron. Each document includes:
- **Concept explanations** - What and why
- **Code examples** - Concrete usage
- **Common mistakes** - What not to do
- **Design rationale** - The thinking behind decisions

The goal is to create an AI that understands not just the syntax, but the philosophy and architecture of Metatron.
