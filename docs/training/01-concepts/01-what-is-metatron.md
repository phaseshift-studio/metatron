# What is Metatron?

## The Vision

Metatron is a **universal abstraction layer** that turns any data system into a graph database. Whether your data lives in SQL databases, document stores, filesystems, or native graph databases, Metatron provides a unified way to navigate and manipulate it.

Think of it as **the Grid from Tron** - a digital universe where all data systems are interconnected and navigable through a common interface.

## The Name

**META**tron = **META** (abstraction layer) + **TRON** (computing, 80s style)

```
         ___      ,----.  ,--.--------.   ,---.   ,--.--------.               _,.---._    .-._
  .-._ .'=.'\  ,-.--` , \/==/,  -   , -\.--.'  \ /==/,  -   , -\.-.,.---.   ,-.' , -  `. /==/ \  .-._
 /==/ \|==|  ||==|-  _.-`\==\.-.  - ,-./\==\-/\ \\==\.-.  - ,-./==/  `   \ /==/_,  ,  - \|==|, \/ /, /
 |==|,|  / - ||==|   `.-. `--`\==\- \   /==/-|_\ |`--`\==\- \ |==|-, .=., |==|   .=.     |==|-  \|  |
 |==|  \/  , /==/_ ,    /      \==\_ \  \==\,   - \    \==\_ \|==|   '='  /==|_ : ;=:  - |==| ,  | -|
 |==|- ,   _ |==|    .-' PhAsESh|==|- |iF/==/t- sT,|uDio|==|- |==|- ,   .'|==| , '='     |==| -   _ |
 |==| _ /\   |==|_  ,`-._       /==/,| /==/-  /\ - \    /==/, |==|_  . ,'. \==\ -    ,_ /|==|  /\ , |
 /==/  / / , /==/ ,     /      /==/ -/ \==\ _.\=\.-'   /==/ -//==/  /\ ,  ) '.='. -   .' /==/, | |- |
 `--`./  `--``--`-----``       `--`--`  `--`           `--`--``--`-`--`--'    `--`--''   `--`./  `--`
```

## Core Idea

### The Problem
Different data systems have different APIs:
- SQL: `SELECT * FROM users WHERE id = 1`
- MongoDB: `db.users.findOne({_id: 1})`
- Filesystem: `cat /data/users/1.json`
- Graph DB: `g.V().hasLabel('user').has('id', 1)`

This fragmentation makes it hard to:
- Work across multiple data systems
- Navigate relationships between systems
- Write portable code

### The Solution
Metatron provides a **unified URI-based interface**:
```java
// All of these work the same way, regardless of underlying storage:
Obj user = Router.readFromSpace(f("db:users/1"));        // SQL
Obj user = Router.readFromSpace(f("mongo:users/1"));     // MongoDB
Obj user = Router.readFromSpace(f("file:/data/users/1")); // Filesystem
Obj user = Router.readFromSpace(f("graph:users/1"));     // Graph DB
```

## Key Concepts

### 1. Everything is a URI
Data is addressed using URIs (Uniform Resource Identifiers):
- `db:users/1` - User with ID 1 in SQL database
- `db:users/1/name` - Just the name field
- `db:users/+` - All users (pattern matching)

### 2. Spaces Abstract Storage
A **Space** is an adapter that connects Metatron to a specific data system:
- `tbleSpace` - SQL databases (SQLite, PostgreSQL, MySQL)
- `grphSpace` - TinkerPop3 graph databases
- `fileSpace` - Filesystems
- Custom spaces for any data system

### 3. The Router Connects Everything
The **Router** maps URIs to Spaces and handles path translation:
```java
// Router sees: db:users/1
// Translates to: users/1 (strips "db:" prefix)
// Routes to: tbleSpace
// Returns: User object
```

### 4. Universal References
Any value can reference any other value using the `!*` instruction:
- `!*db:users/123` means "fetch the object at db:users/123"
- Works across any data system
- Creates a universal graph of references

### 5. Graph Traversal
Navigate relationships using:
- `>>` operator - Dereference and navigate
- `/+/+/+` patterns - Multi-level traversal
- Foreign keys automatically followed

## Example: Turning SQL into a Graph

```java
// Traditional SQL (separate queries):
User user = db.query("SELECT * FROM users WHERE id = 1");
Order order = db.query("SELECT * FROM orders WHERE user_id = 1");
Product product = db.query("SELECT * FROM products WHERE id = " + order.productId);

// Metatron (single traversal):
Obj product = Router.readFromSpace(f("db:users/1/orders/+/product"));
// Automatically follows foreign keys: users -> orders -> products
```

## The Universal Graph Vision

Metatron's goal is to make **any data system** behave like a graph database:

1. **Native References**: Use the system's built-in references (SQL foreign keys, MongoDB DBRefs, filesystem symlinks)

2. **Metatron References**: When native references don't exist, use `!*` instructions

3. **Unified Navigation**: The `>>` operator and pattern matching work everywhere

This creates a **universal graph** where:
- Every data system is a node
- Every reference (native or `!*`) is an edge
- Everything is navigable through a common interface

## Why This Matters

### For Developers
- Write once, run on any data system
- Navigate complex relationships easily
- No more N+1 query problems
- Automatic query optimization

### For Data
- Break down silos between systems
- Create relationships across databases
- Migrate between systems without rewriting code
- Treat all data as a unified graph

### For AI
- Consistent interface for training
- Easy to learn and use
- Powerful abstractions
- Extensible to new data systems

## Next Steps

- Learn about [Instruction Sets](02-instruction-sets.md) - the building blocks of Metatron
- Understand [Spaces and Routing](03-spaces-and-routing.md) - how data systems connect
- Explore [Pattern Matching](04-pattern-matching.md) - powerful query capabilities
- Discover [Universal References](05-universal-references.md) - the graph abstraction

---

**Remember**: Metatron is not just a database abstraction - it's a **meta-layer** that unifies all computing systems into a single, navigable graph. Welcome to the Grid. 🎮✨
