# The Metatron Environment

This document explains the **metatron environment** - the complete system architecture including spaces, routers, monads, machines, serializers, and how they work together with the mtron language.

## Overview

**Metatron** is the entire system that provides:
- **Mtron**: The programming language
- **Spaces**: Adapters to different data systems
- **Router**: Pattern-based request routing
- **Instruction Sets**: Collections of operations and types
- **Monads**: Computational contexts and effects
- **Machines**: Execution engines
- **Serializers**: Data format converters

Think of it as: **Mtron is to Metatron as JavaScript is to Node.js**

## Core Components

### 1. Spaces

**Spaces** are adapters that connect Metatron to different data systems. Each space:
- Implements a common interface (`directReader`, `directWriter`)
- Handles a specific type of data system
- Has a pattern that defines what URIs it handles
- Has a route that translates external URIs to internal paths

#### Available Space Types

From `boot/boot.mtron`, here are the space types:

**`mem::`** - In-Memory Space
```metatron
mem::[pattern => /usr/#, q => [subq::[=>]]]@/sys/space/usr;
```
Stores data in memory. Fast but volatile.

**`tble::`** - SQL Database Space
```metatron
tble::[pattern    => netflix:#,
       host       => <mariadb://localhost:3306/netflix?user=mtron&password=mtron>,
       route      => [<netflix:>=><>],
       serializer => !*</m/mach/io/serializer/json/simple>,
       table      => [,],
       driver     => <org.mariadb.jdbc.Driver>]@/sys/space/netflix;
```
Connects to SQL databases (SQLite, MariaDB, PostgreSQL, etc.).

**`tp3::`** - TinkerPop3 Graph Database Space
```metatron
tp3::[pattern => grateful:#,
      host    => <conf/grateful-dead.properties>,
      route   => [<grateful:>=><>]]@/sys/space/grateful;
```
Connects to graph databases (Neo4j, JanusGraph, etc.).

**`http::`** - HTTP Server Space
```metatron
http::[host     => <http://localhost:8777>,
       pattern  => http://#,
       route    => [/ => examples/www, /docs => target/docs]]@/sys/space/www;
```
Serves HTTP requests, maps URLs to filesystem paths.

**`mqtt::`** - MQTT Broker Space
```metatron
mqtt::[pattern => mqtt://#,
       broker  => !*boot/args/mqtt/broker,
       route   => [<mqtt://>=><>]]@/sys/space/mqtt;
```
Connects to MQTT message brokers for IoT communication.

**`miot::`** - Metatron IoT Space
```metatron
miot::[pattern => miot://#,
       route   => [<miot://>=><>]]@/sys/space/miot;
```
High-level IoT abstraction layer.

**`haos::`** - Home Assistant Space
```metatron
haos::[pattern => haos://#,
       host    => <http://homeassistant.local:8123>,
       token   => *boot/args/haos/token,
       route   => [<haos://>=><>]]@/sys/space/haos;
```
Integrates with Home Assistant smart home platform.

**`fs::`** - Filesystem Space
```metatron
fs::[pattern => *fs_prefix,
     route   => [*fs_prefix=><file://>]]@/sys/space/fs;
```
Access local filesystem files and directories.

**`serial::`** - Serial Port Space
```metatron
serial::[pattern => serial://#,
         route   => [<serial://>=><>]]@/sys/space/serial;
```
Communicate with serial devices.

**`catalog::`** - LLM Catalog Space
```metatron
catalog::[pattern => catalog://#,
          host    => <http://localhost:11434>,
          route   => [<catalog://>=><>]]@/sys/space/ollama;
```
Connects to Ollama LLM catalog for AI model management.

**`meta::`** - Distributed/Cluster Space
```metatron
meta::[pattern => meta://#,
       peers   => !*boot/peers,
       route   => [<meta://>=><>]]@/sys/space/meta;
```
Distributed computing across multiple Metatron instances.

### 2. Router

The **Router** is a global singleton that:
- Maintains a registry of all mounted spaces
- Matches incoming URIs against space patterns
- Translates URIs using route mappings
- Dispatches requests to the correct space

#### Pattern Matching

Uses MQTT topic matching semantics:
- `+` - Single-level wildcard (matches one segment)
- `#` - Multi-level wildcard (matches remaining segments)

Examples:
```metatron
netflix:#        % matches netflix:movies, netflix:movies/1, netflix:movies/1/title
/users/+         % matches /users/1, /users/2, but not /users/1/name
http://#         % matches all HTTP URIs
```

#### Route Translation

Routes map external URIs to internal paths:

```metatron
route => [<netflix:>=><>]
% Strips "netflix:" prefix
% netflix:movies/1 → movies/1

route => [/ => examples/www, /docs => target/docs]
% Maps URL paths to filesystem paths
% http://localhost:8777/index.html → examples/www/index.html
% http://localhost:8777/docs/api.html → target/docs/api.html
```

#### Router API

**In Java:**
```java
Router.readFromSpace(f("netflix:movies/1"))
Router.writeToSpace(f("netflix:movies/1"), movieObject)
```

**In Mtron:**
```metatron
*netflix:movies/1              % read
netflix:movies/1 -> [title=>"Inception", year=>2010]  % write
```

### 3. Instruction Sets

**Instruction Sets** are the fundamental organizational unit in Metatron. Each instruction set contains:

1. **types** - Data types (int, str, lst, rec, etc.)
2. **constants** - Constant values/objects
3. **insts** - Operations/functions (plus, mult, from, to, etc.)
4. **rewrites** - Code transformations `f: code → code`
5. **sugars** - Syntactic conveniences for the console

#### Core Instruction Sets

**`mInstSet`** - Core/Base Instruction Set
```metatron
import(/m/mach/io);
import(/m/math);
import(/m/web);
```

Provides:
- Basic types: `int`, `real`, `str`, `bool`, `uri`
- Collections: `lst`, `rec`, `set`
- Operations: `plus`, `minus`, `mult`, `div`, `from`, `to`, `map`, `filter`
- Control flow: `if`, `else`, `reduce`, `fold`

**`tbleInstSet`** - SQL/Table Instruction Set
```metatron
import(/m/tble);
```

Provides:
- SQL query operations
- Table schema management
- Query rewrites (e.g., `[from, count]` → `SELECT COUNT(*)`)

**`tp3InstSet`** - TinkerPop3 Graph Instruction Set
```metatron
import(/m/grph/tp3);
```

Provides:
- Graph traversal operations
- Vertex/edge manipulation
- Gremlin query integration

**`llmInstSet`** - LLM Instruction Set
```metatron
import(/m/llm);
```

Provides:
- LLM model management
- Prompt execution
- Model catalog operations

#### Instruction Set Architecture

Each instruction set is a Java class implementing the instruction set interface:

```java
public class tbleInstSet extends InstSet {
    @Override
    public Obj types() { /* define types */ }

    @Override
    public Obj constants() { /* define constants */ }

    @Override
    public Obj insts() { /* define instructions */ }

    @Override
    public Obj rewrites() { /* define code transformations */ }

    @Override
    public Obj sugars() { /* define syntactic sugar */ }
}
```

### 4. Rewrites (Query Optimization)

**Rewrites** are endofunctors in the category of code: `f: code → code`

They transform instruction sequences into more efficient equivalents.

#### Example: SQL Count Optimization

**Before rewrite:**
```metatron
*netflix:movie.count()
% Fetches all 11,983 rows, counts in memory
```

**After rewrite:**
```metatron
% Transformed to: SELECT COUNT(*) FROM movie
% Executes native SQL, returns single integer
```

**Implementation in tbleInstSet.java:**
```java
public Obj rewrites() {
    return rec(
        kv("sql_count_rewrite",
            // Pattern: [from, count]
            // Rewrite to: native SQL COUNT query
        )
    );
}
```

#### Other Rewrites in mInstSet

1. **`id_removal_rewrite`** - Removes identity operations
2. **`map_nest_rewrite`** - Optimizes nested map operations

### 5. Monads

**Monads** provide computational contexts and effects. They wrap values and provide:
- Sequencing of operations
- Error handling
- State management
- Asynchronous computation

Common monads in Metatron:
- **Maybe/Option** - Handles optional values
- **Either/Result** - Handles success/failure
- **List** - Non-deterministic computation
- **IO** - Side effects and I/O operations

### 6. Machines

**Machines** are execution engines that:
- Interpret mtron code
- Execute instructions
- Manage execution context
- Handle instruction dispatch

The machine:
1. Parses mtron source code
2. Builds an instruction sequence
3. Applies rewrites for optimization
4. Executes the optimized code
5. Returns the result

### 7. Serializers

**Serializers** convert between Metatron objects and external formats:

**JSON Serializer:**
```metatron
serializer => !*</m/mach/io/serializer/json/simple>
```

**Available serializers:**
- `json/simple` - Simple JSON format
- `json/typed` - JSON with type annotations
- `binary` - Binary format for efficiency
- `xml` - XML format
- `yaml` - YAML format

Serializers are used when:
- Storing data in databases
- Sending data over network
- Reading/writing files
- Communicating with external systems

## How It All Works Together

### Example: Reading from SQL Database

**User writes:**
```metatron
*netflix:movie/1/title
```

**What happens:**

1. **Parser** parses the mtron code
2. **Machine** recognizes `*` (dereference/from operation)
3. **Router** receives request for `netflix:movie/1/title`
4. **Router** matches pattern `netflix:#` → finds `tbleSpace`
5. **Router** applies route `[<netflix:>=><>]` → translates to `movie/1/title`
6. **Router** calls `tbleSpace.directReader("movie/1/title")`
7. **tbleSpace** parses path: table=`movie`, rowId=`1`, field=`title`
8. **tbleSpace** executes SQL: `SELECT title FROM movie WHERE id=1`
9. **tbleSpace** returns result: `"Inception"`
10. **Serializer** converts to mtron object
11. **Machine** returns final result to user

### Example: Writing to SQL Database

**User writes:**
```metatron
netflix:movie/1/title -> "The Matrix"
```

**What happens:**

1. **Parser** parses the assignment
2. **Machine** recognizes `->` (assignment/to operation)
3. **Router** receives write request for `netflix:movie/1/title` with value `"The Matrix"`
4. **Router** matches pattern and translates path
5. **Router** calls `tbleSpace.directWriter("movie/1/title", "The Matrix")`
6. **tbleSpace** executes SQL: `UPDATE movie SET title='The Matrix' WHERE id=1`
7. **tbleSpace** returns success

### Example: Query Optimization with Rewrites

**User writes:**
```metatron
*netflix:movie.count()
```

**What happens:**

1. **Parser** creates instruction sequence: `[from(netflix:movie), count]`
2. **Machine** checks rewrites in `tbleInstSet`
3. **Rewrite engine** matches pattern `[from, count]`
4. **Rewrite engine** transforms to native SQL: `SELECT COUNT(*) FROM movie`
5. **Router** dispatches to `tbleSpace`
6. **tbleSpace** executes optimized SQL
7. **tbleSpace** returns `11983` (single integer, not 11,983 rows)

## The Universal Graph Vision

Metatron's ultimate goal: **Turn any data system into a graph database**

### Native References
Use each system's built-in references:
- **SQL**: Foreign keys
- **MongoDB**: DBRefs
- **Filesystem**: Symlinks
- **HTTP**: Hyperlinks

### Metatron References (`!*` system)
Universal reference system that works across all spaces:

```metatron
[user => !*/users/1,
 posts => !*/users/1/posts]
```

When you navigate with `>>`:
```metatron
*mydata.>>user.>>posts.>>0.>>title
% Traverses: mydata → users/1 → posts → first post → title
% Works across different data systems!
```

### Foreign Key Traversal (Coming Soon)

```metatron
*netflix:movie/1.>>director_id.>>name
% Follows foreign key from movie to director, gets name
% Automatic JOIN without writing SQL!
```

Multi-level patterns:
```metatron
*netflix:/+/+/+/+
% Traverse 4 levels deep, following all foreign keys
% Builds complete graph automatically!
```

## Architecture Layers

### Layer 1: Router (Pattern Matching & Routing)
- Matches URI patterns
- Translates paths using routes
- Dispatches to correct space

### Layer 2: Space.Helper (Poly Unrolling & Navigation)
- Handles field access (`/users/1/name`)
- Handles navigation (`>>` operator)
- Handles wildcards (`/users/+`)
- Handles foreign key traversal (future)

### Layer 3: Space (Raw Data Access)
- Minimal "getRaw" function
- Returns objects at exact URIs
- No translation, no unrolling
- Direct data system access

## Key Design Principles

1. **Separation of Concerns**: Each layer has a specific responsibility
2. **Spaces are Adapters**: They translate between Metatron and data systems
3. **Router Handles Translation**: Spaces receive already-translated paths
4. **Rewrites are Optimizations**: Transform code without changing semantics
5. **Everything is a URI**: Uniform addressing across all systems
6. **References are Explicit**: Use `*` to dereference, `!` to execute
7. **Pattern Matching is Universal**: Same semantics across all spaces

## Configuration and Boot Process

The `boot/boot.mtron` script:

1. **Imports instruction sets**
   ```metatron
   import(/m/mach/io);
   import(/m/math);
   import(/m/tble);
   ```

2. **Sets up global configuration**
   ```metatron
   boot/args/mqtt -> [broker => <mqtt://localhost:1883>]
   ```

3. **Mounts spaces**
   ```metatron
   tble::[pattern => netflix:#, ...]@/sys/space/netflix;
   ```

4. **Configures routing**
   ```metatron
   route => [<netflix:>=><>]
   ```

5. **Starts services**
   ```metatron
   http::[host => <http://localhost:8777>, ...]@/sys/space/www;
   ```

## Next Steps

- See [Mtron Syntax](01-mtron-syntax.md) for language details
- See [Spaces and Routing](../01-concepts/03-spaces-and-routing.md) for routing details
- See [Instruction Sets](../01-concepts/02-instruction-sets.md) for available operations
- See [Real-World Examples](../02-examples/) for practical usage
