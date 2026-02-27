# Metatron Project - Developer Guide

This document contains essential information about the Metatron distributed computing language, codebase structure, and development practices.

---

## Table of Contents
1. [Metatron Language Reference](#metatron-language-reference)
2. [Type System](#type-system)
3. [Syntax Patterns](#syntax-patterns)
4. [Common Operations](#common-operations)
5. [Testing Patterns](#testing-patterns)
6. [Codebase Structure](#codebase-structure)
7. [Build & Test Commands](#build--test-commands)
8. [Code Style & Conventions](#code-style--conventions)

---

## Metatron Language Reference

Metatron (mtron) is a distributed computing language with a rich type system and functional programming capabilities.

### Core Concepts

- **Objects (Obj)**: Everything in Metatron is an object
- **Coefficients**: Objects can have coefficients (e.g., `int{5}::1`) for performance optimization of repeated computations
- **Pattern Matching**: Uses `_` as wildcard for matching and operations
- **Instruction Chaining**: Operations can be chained (e.g., `.reverse().count()`)
- **Branched Structures**: Polys (Lst, Rec) are branched structures - each element/entry is a separate branch
- **Merge Operator (`>-`)**: Merges branches into a single stream
- **Split Operator (`-<`)**: Splits a stream into branches (opposite of merge)

---

## Type System

### 1. **Int (Integer)**
- Whole numbers: `1`, `42`, `-5`, `0`
- Operations: `plus()`, `mult()`, `minus()`, `div()`, `mod()`, `pow()`
- Comparisons: `gt()`, `gte()`, `lt()`, `lte()`, `eq()`
- Other: `sum()`, `neg()`, `abs()`, `as()`

**Examples:**
```metatron
5.plus(3)                    % 8
10.mult(2)                   % 20
5.gt(3)                      % true
5.gte(5)                     % true
```

### 2. **Real (Floating Point)**
- Decimal numbers: `1.5`, `3.14`, `-2.7`, `0.0`
- Same operations as Int
- Type conversion: `.as(int::T)` to convert to integer

**Examples:**
```metatron
1.5.plus(2.5)                % 4.0
3.14.mult(2.0)               % 6.28
1.5.gt(1.0)                  % true
```

### 3. **Str (String)**
- Text enclosed in double quotes: `"hello"`, `"world"`, `""`
- Operations: `plus()` (concatenation), `reverse()`, `eq()`
- Note: `.count()` was removed from strings (use `>-.count()` for stream-based counting)

**Examples:**
```metatron
"hello".plus(" world")       % "hello world"
"abc".reverse()              % "cba"
"hello".eq("hello")          % true
```

### 4. **Bool (Boolean)**
- Values: `true`, `false`
- Operations: `plus()` (OR), `mult()` (AND), `not()`, `eq()`

**Examples:**
```metatron
true.plus(false)             % true
true.mult(false)             % false
true.not()                   % false
```

### 5. **Uri (Uniform Resource Identifier)**
- Path-like structures: `a/b/c`, `/a/b`, `<http://example.com/path>`
- Operations: `reverse()`, `count()`, `eq()`, `as()`
- Note: `.count()` returns 1 for the URI object itself (not path segments)

**Examples:**
```metatron
a/b/c.reverse()              % c/b/a
a/b/c.count()                % 1
a.eq(a)                      % true
```

### 6. **Lst (List)** - Poly/Branched Structure
- Ordered collections: `[a,b,c]`, `[1,2,3]`, `[,]` (empty list)
- Syntax: Elements separated by commas
- **Branched Structure**: Each element is a separate branch
- Operations: `reverse()`, `merge()`, `plus()`, `count()`
- **Important**: `.count()` returns 1 (the list object itself), use `>-.count()` to count elements

**Examples:**
```metatron
[a,b,c].reverse()            % [c,b,a]
[1,2,3].merge()              % {1,2,3}
[1,2,3].plus([4,5])          % [1,2,3,4,5]
[1,2,3].count()              % 1 (the list object)
[1,2,3]>-.count()            % 3 (merges branches, counts elements)
[,].reverse()                % [,]
```

**Pattern Matching in Lists:**
```metatron
[a,b,c].reverse()==[reverse(),reverse(),reverse()]     % [c,b,a]
```

**Branched Structure in Conditionals:**
```metatron
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']    % [10=>'greater than 9']
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']>>  % 'greater than 9'
```

### 7. **Rec (Record)** - Poly/Branched Structure
- Key-value pairs: `[a=>1,b=>2,c=>3]`, `[=>]` (empty record)
- Syntax: `key=>value` pairs separated by commas
- **Branched Structure**: Each key-value pair is a separate branch
- Operations: `reverse()`, `merge()`, `plus()`, `count()`, `select()`, `dom()`, `rng()`, `has()`
- **Important**: `.count()` returns 1 (the record object itself), use `>-.count()` to count entries

**Examples:**
```metatron
[a=>1,b=>2].reverse()                    % [b=>2,a=>1]
[a=>1,b=>2].merge()                      % {a=>1,b=>2}
[a=>1].plus([b=>2])                      % [a=>1,b=>2]
[a=>1,b=>2,c=>3].count()                 % 1 (the record object)
[a=>1,b=>2,c=>3]>-.count()               % 3 (merges branches, counts entries)
[a=>1,b=>2].dom()                        % {a,b}
[a=>1,b=>2].rng()                        % {1,2}
[a=>1,b=>2].has(a)                       % true
```

**Pattern Matching in Records:**
```metatron
[a=>1,b=>2,c=>3].select([a=>_,b=>_])     % [a=>1,b=>2]
[a=>1,b=>2].reverse()==[_=>reverse()]    % [b=>2,a=>1]
```

**Branched Structure in Conditionals:**
```metatron
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']    % [10=>'greater than 9']
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']>>  % 'greater than 9'
```

### 8. **Objs (Set/Collection)**
- Unordered collections: `{1,2,3}`, `{a,b,c}`, `{,}` (empty set)
- Syntax: Elements separated by commas in curly braces
- Operations: `merge()`, `count()`, `plus()`
- Note: `.count()` returns total element count (including duplicates with coefficients)

**Examples:**
```metatron
{1,2,3}.merge()              % {1,2,3}
{1,2,3}.count()              % 3
{1,1,1}.count()              % 3 (counts all instances)
{1,2}.plus({3})              % {1,2,3}
```

### 9. **Rel (Relation)**
- Binary relations: `1->2`, `a->b`
- Syntax: `first->second`
- Operations: `first()`, `second()`, `eq()`, `plus()`

**Examples:**
```metatron
(1->2).first()               % 1
(1->2).second()              % 2
(1->2).eq(1->2)              % true
```

### 10. **NoObj (No Object)**
- Represents absence of value: `noobj`
- Used for error handling and missing values
- Operations: `isNoObj()` predicate
- Zero coefficient: `int{0}::1.isNoObj()` returns `true`

**Examples:**
```metatron
noobj.isNoObj()              % true
1.isNoObj()                  % false
int{0}::1.isNoObj()          % true
```

### 11. **Byte Unit Types**
Metatron provides byte unit types for working with data sizes using **binary (base-2) units**.

**Available Types:**
- `bB` - Byte (1 byte)
- `kB` - Kilobyte (1,024 bytes = 2^10)
- `mB` - Megabyte (1,048,576 bytes = 2^20)
- `gB` - Gigabyte (1,073,741,824 bytes = 2^30)
- `tB` - Terabyte (1,099,511,627,776 bytes = 2^40)
- `pB` - Petabyte (1,125,899,906,842,624 bytes = 2^50)

**Operations:**
- `.as(type)` - Convert between byte units
- `.eq(other)` - Compare byte units for equality

**Note:** Type names use lowercase letters followed by uppercase `B` (e.g., `kB`, `mB`) because uppercase `B` alone is a generic type parameter in Metatron.

**Examples:**
```metatron
kB::1024.as(mB::T)           % 1 (convert 1024 kB to mB)
mB::1.as(kB::T)              % 1024 (convert 1 mB to kB)
gB::5.as(mB::T)              % 5120 (convert 5 gB to mB)
pB::1.as(tB::T)              % 1024 (convert 1 pB to tB)

mB::1024.eq(gB::1)           % true (1024 mB equals 1 gB)
kB::1024.eq(mB::1)           % true (1024 kB equals 1 mB)
gB::2.eq(mB::2048)           % true (2 gB equals 2048 mB)
tB::1.eq(gB::1024)           % true (1 tB equals 1024 gB)
```

**Binary Convention:**
- 1 kB = 1024 bytes (not 1000)
- 1 mB = 1024 kB = 1,048,576 bytes (not 1,000,000)
- 1 gB = 1024 mB = 1,073,741,824 bytes (not 1,000,000,000)

This follows the binary (base-2) convention used by computer systems for memory and storage.

---

## Syntax Patterns

### Coefficients
Objects can have coefficients indicating multiplicity or other metadata. Coefficients are used to **bulk repeated computations** into a single operation for efficiency.

**Syntax**: `type{coefficient}::value`

```metatron
int{5}::1                    % Integer 1 with coefficient 5
noobj{5}                     % NoObj with coefficient 5
int{7}::3                    % Integer 3 with coefficient 7
```

**Performance Optimization**: When you have repeated identical values, Metatron uses coefficients to avoid redundant computation:
```metatron
{1,1,1,1,1,1,1}.plus(2)      % Doesn't execute 7 times!
                             % Executes once on int{7}::1, then applies .plus(2)
                             % Result: int{7}::3
```

**Coefficients in Merged Streams**: When branches are merged, duplicate values get coefficients:
```metatron
{1,2,3}-<[+1,+2,+3]>-        % Split then merge:
                             % ==>2           (appears once)
                             % ==>int{2}::3   (appears twice, with coefficient)
                             % ==>int{3}::4   (appears three times, with coefficient)
                             % ==>int{2}::5   (appears twice, with coefficient)
                             % ==>6           (appears once)
```

**Negative Coefficients & Interference**: Coefficients can be negative, enabling constructive and destructive interference:
```metatron
[1,2,3]                      % ==>[1,2,3]

[1,2,3]+[int{-1}::1]         % Add element 1 with coefficient -1
                             % ==>[1,2,3,int{-1}::1]

[1,2,3]+[int{-1}::1]>-       % Merge: 1 appears with coefficient 1 and -1
                             % Destructive interference: 1 + (-1) = 0, so 1 disappears
                             % ==>2
                             % ==>3

[1,int{100}::2,3]+[int{-75}::2]>-   % Partial destructive interference
                                     % 2 appears with coefficient 100 and -75
                                     % 100 + (-75) = 25
                                     % ==>1
                                     % ==>int{25}::2
                                     % ==>3
```

**Coefficient Ranges & Pattern Matching**: Coefficients support range specifications for pattern matching (regex-style):
```metatron
[int{25}::3]                         % List with element 3 having coefficient 25
                                     % (using [ ] prevents int from unrolling)

[int{25}::3].matches([int{25}::T])   % Exact match: coefficient must be 25
                                     % ==>true

[int{25}::3].matches([int{24}::T])   % Exact match fails
                                     % ==>false

[int{25}::3].matches([int{1,26}::T]) % Range match: coefficient between 1 and 26
                                     % ==>true

[int{25}::3].matches([int{10,26}::T])% Range match: coefficient between 10 and 26
                                     % ==>true

[int{25}::3].matches([int{10,}::T])  % Open-ended range: coefficient >= 10
                                     % ==>true

[int{25}::3].matches([int{,20}::T])  % Open-ended range: coefficient <= 20
                                     % ==>false

[int{25}::3].matches([int{*}::T])    % Wildcard: any coefficient (0 or more)
                                     % ==>true

[int{25}::3].matches([int{+}::T])    % One or more: coefficient >= 1
                                     % ==>true

[int{25}::3].matches([int{?}::T])    % Zero or one: coefficient 0 or 1
                                     % ==>false
```

**Coefficient Range Syntax:**
- `{n}` - Exact coefficient (e.g., `{25}`)
- `{min,max}` - Range from min to max (e.g., `{1,26}`)
- `{min,}` - Open-ended minimum (e.g., `{10,}` means >= 10)
- `{,max}` - Open-ended maximum (e.g., `{,20}` means <= 20)
- `{*}` - Zero or more (any coefficient)
- `{+}` - One or more (coefficient >= 1)
- `{?}` - Zero or one (coefficient 0 or 1)

### Type Casting
Use `.as()` to convert between types:
```metatron
1.5.as(int::T)               % Convert real to int
"123".as(int::T)             % Convert string to int
```

### Wildcards and Pattern Matching
The `_` symbol is used as a wildcard in pattern matching:
```metatron
[a=>1,b=>2].select([a=>_])              % Select key 'a' with any value
[a=>1,b=>2].reverse()==[_=>reverse()]   % Apply reverse to all values
```

Coefficients also support pattern matching with range syntax:
```metatron
int{25}::3.matches(int{25}::T)          % Exact coefficient match
int{25}::3.matches(int{10,30}::T)       % Range match (10 to 30)
int{25}::3.matches(int{10,}::T)         % Minimum match (>= 10)
int{25}::3.matches(int{*}::T)           % Any coefficient
int{25}::3.matches(int{+}::T)           % One or more (>= 1)
int{25}::3.matches(int{?}::T)           % Zero or one (0 or 1)
```

### Merge Operator (`>-`) and Split Operator (`-<`)

**Merge (`>-`)**: Merges branches into a single stream
**Split (`-<`)**: Splits a stream into branches (opposite of merge)

**Key Concept**: Polys are **branched structures**. Each element in a list or entry in a record is a separate branch.

#### Merge Examples:
```metatron
[1,2,3].count()              % 1 (the list object itself)
[1,2,3]>-.count()            % 3 (merges branches into stream, counts elements)

[a=>1,b=>2].count()          % 1 (the record object itself)
[a=>1,b=>2]>-.count()        % 2 (merges branches into stream, counts entries)
```

#### Split Examples:
```metatron
{1,2,3}                      % Displays as 3 separate branches:
                             % ==>1
                             % ==>2
                             % ==>3

{1,2,3}-<[+1,+2,+3]          % Split each element into a list of operations:
                             % ==>[2,3,4]
                             % ==>[3,4,5]
                             % ==>[4,5,6]

{1,2,3}-<[+1,+2,+3]>-        % Split then merge back:
                             % ==>2
                             % ==>int{2}::3
                             % ==>int{3}::4
                             % ==>int{2}::5
                             % ==>6
```

**Branched Structures in Conditionals:**
The branched nature becomes apparent in conditional expressions:
```metatron
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']    % [10=>'greater than 9']
10-<[?<10 => 'less than 10', ?>=9 => 'greater than 9']>>  % 'greater than 9'
```
Each condition is evaluated as a separate branch, and matching branches are returned.

### Instruction Chaining
Operations can be chained together:
```metatron
[a,b,c].reverse().merge()    % Reverse then merge
5.plus(3).mult(2)            % Add then multiply
```

### Sugar Syntax

Metatron provides syntactic sugar to make code more readable. The WebSocket server returns explicit (non-sugar) syntax with full type URIs.

**Common Sugar Mappings** (defined in `mInstSet.sugars()`):

| Sugar | Explicit | Description |
|-------|----------|-------------|
| `+` | `plus()` | Addition |
| `-` | `minus()` | Subtraction |
| `*` | `from()` | From/multiplication context |
| `>-` | `merge()` | Merge branches into stream |
| `-<` | `split()` | Split stream into branches |
| `>>` | `rshift()` | Right shift/extract |
| `<<` | `lshift()` | Left shift |
| `==` | `select()` | Selection |
| `->` | `ref()` | Reference/relation |
| `@` | `at()` | Access element at key |
| `_` | `id()` | Identity/wildcard |
| `?` | `isa()` | Type check |

**Conditional Sugars:**

| Sugar | Explicit | Description |
|-------|----------|-------------|
| `?==` | `where()` | Filter where condition |
| `?=` | `is().eq()` | Is equal to |
| `?>` | `is().gt()` | Is greater than |
| `?>=` | `is().gte()` | Is greater than or equal |
| `?<` | `is().lt()` | Is less than |
| `?<=` | `is().lte()` | Is less than or equal |
| `?!=` | `is().neq()` | Is not equal to |
| `?=~` | `is().matches()` | Matches pattern |

**Type URIs in Output:**

WebSocket responses include full type URIs:
```metatron
% Input (sugar):
1+2

% Output (explicit):
</m/int>::3

% Input (sugar):
[a=>1,b=>2]

% Output (explicit):
</m/rec>::[</m/uri>::<a> => </m/int>::1,</m/uri>::<b> => </m/int>::2]
```

**Examples:**
```metatron
1+2                          % Sugar for 1.plus(2)
{1,2,3}>-                    % Sugar for {1,2,3}.merge()
[1,2,3]-<[+1]                % Sugar for [1,2,3].split([plus(1)])
a->b                         % Sugar for a.ref(b) (creates relation)
```

---

## Common Operations

### Universal Operations (available on most types)
- `.eq(other)` - Equality comparison
- `.as(type)` - Type conversion
- `.count()` - Count (behavior varies by type)
- `.reverse()` - Reverse operation (where applicable)

### Arithmetic Operations (Int, Real)
- `.plus(n)` - Addition
- `.minus(n)` - Subtraction
- `.mult(n)` - Multiplication
- `.div(n)` - Division
- `.mod(n)` - Modulo
- `.pow(n)` - Power
- `.neg()` - Negation
- `.abs()` - Absolute value
- `.sum()` - Sum (for collections)

### Comparison Operations (Int, Real)
- `.gt(n)` - Greater than
- `.gte(n)` - Greater than or equal (note: `gte` not `geq`)
- `.lt(n)` - Less than
- `.lte(n)` - Less than or equal (note: `lte` not `leq`)
- `.eq(n)` - Equal to

### Collection Operations (Lst, Rec, Objs)
- `.merge()` - Merge into a set/collection
- `.plus(other)` - Concatenate/combine
- `>-.count()` - Count elements (stream-based)

### Record-Specific Operations
- `.dom()` - Get domain (keys)
- `.rng()` - Get range (values)
- `.has(key)` - Check if key exists
- `.select(pattern)` - Select entries matching pattern

### Boolean Operations
- `.plus(bool)` - Logical OR
- `.mult(bool)` - Logical AND
- `.not()` - Logical NOT

---

## Testing Patterns

### Test File Structure
All test files extend `AbstractMetatronTest` or `AbstractObjTest` and use JUnit 5.

**Location:** `/home/killswitch/software/metatron/src/test/java/studio/phaseshift/metatron/isa/m/type/`

### Parameterized Tests
Use `@ParameterizedTest` with `@CsvSource` for data-driven tests:

```java
@ParameterizedTest
@CsvSource(value = {
        "[a=>1,b=>2].reverse()                    % [b=>2,a=>1]",
        "[a=>1].reverse()                         % [a=>1]",
        "[=>].reverse()                           % [=>]",
}, delimiter = '%')
public void testReverse(final String code, final String expected) {
    AbstractMetatronTest.testCode(LOG, code, expected);
}
```

### Test Method Pattern
```java
AbstractMetatronTest.testCode(LOG, code, expected);
```
- `LOG` - Logger instance (usually `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`)
- `code` - Metatron code to execute
- `expected` - Expected result as string

### Boolean Assertion Pattern
For boolean predicates, use direct assertion:
```java
@ParameterizedTest
@CsvSource(value = {
        "noobj.isNoObj()     | true",
        "1.isNoObj()         | false",
}, delimiter = '|')
public void testIsNoObj(final String code, final boolean expected) {
    final Obj obj = mParser.parse(code);
    LOG.debug("testing %s.isNoObj() [expected:%s]", obj, expected);
    assertEquals(expected, obj.isNoObj());
}
```

### Test Coverage Best Practices
When writing tests, ensure coverage of:
1. **Edge cases**: Empty collections, single elements, zero values
2. **Negative numbers**: For numeric operations
3. **Nested structures**: Lists of lists, records of records
4. **Type conversions**: Using `.as()` operations
5. **Chained operations**: Multiple operations in sequence
6. **Pattern matching**: Using wildcards and patterns

---

## Codebase Structure

### Main Source Structure
```
/home/killswitch/software/metatron/src/main/java/studio/phaseshift/metatron/
├── isa/                          # Instruction Set Architecture
│   ├── m/                        # Metatron-specific ISA
│   │   ├── type/                 # Type implementations
│   │   │   ├── Rec.java          # Record interface
│   │   │   ├── Lst.java          # List interface
│   │   │   ├── Int.java          # Integer interface
│   │   │   ├── Str.java          # String interface
│   │   │   ├── Uri.java          # URI interface
│   │   │   ├── Real.java         # Real number interface
│   │   │   ├── Bool.java         # Boolean interface
│   │   │   ├── Rel.java          # Relation interface
│   │   │   ├── Objs.java         # Set/Collection interface
│   │   │   └── ...
│   │   └── Poly.java             # Polymorphic type interface
│   └── ...
└── ...
```

### Test Structure
```
/home/killswitch/software/metatron/src/test/java/studio/phaseshift/metatron/
├── AbstractMetatronTest.java           # Base test class with utility methods
├── AbstractObjTest.java                # Abstract object test base class
├── AbstractSerializerTest.java         # Base class for serializer tests
└── isa/
    └── m/
        └── type/
            ├── RecTest.java            # Record type tests
            ├── LstTest.java            # List type tests
            ├── IntTest.java            # Integer type tests
            ├── StrTest.java            # String type tests
            ├── UriTest.java            # URI type tests
            ├── RealTest.java           # Real number type tests
            ├── BoolTest.java           # Boolean type tests
            ├── RelTest.java            # Relation type tests
            ├── NoObjTest.java          # NoObj type tests
            └── ObjsTest.java           # Set/Collection type tests
```

### Key Classes
- **AbstractMetatronTest**: Provides `testCode(LOG, code, expected)` utility method
- **AbstractObjTest**: Base class for object-specific tests
- **AbstractSerializerTest**: Generic base class for testing `ObjSerializer<T>` implementations

---

## Build & Test Commands

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RecTest

# Run specific test method
mvn test -Dtest=RecTest#testReverse
```

### Building
```bash
# Build the project
mvn install

# Clean build
mvn clean install

# Compile only
mvn compile
```

### Linting/Code Quality
```bash
# Verify project
mvn verify

# Check for errors
mvn validate
```

### Interactive Metatron REPL

Metatron has a built-in WebSocket server that allows interactive execution of mtron code.

#### Starting the WebSocket Server
```bash
# Build the project first
mvn clean install

# Start Metatron with WebSocket server on port 8999
java -jar target/metatron-*.jar '[host=><ws://0.0.0.0:8999>]'

# Or with a boot configuration
java -jar target/metatron-*.jar '[boot=><conf/boot.mtron>,host=><ws://0.0.0.0:8999>]'
```

#### Executing mtron Code via WebSocket

**Using the helper script:**
```bash
# Execute a single mtron expression
./bin/mtron-exec.py "1.plus(2)"

# More complex examples
./bin/mtron-exec.py "[1,2,3].reverse()"
./bin/mtron-exec.py "{1,2,3}-<[+1,+2,+3]>-"
```

**Using websocat (if installed):**
```bash
echo "1.plus(2)" | websocat ws://localhost:8999
```

**Using Python directly:**
```python
import asyncio
import websockets

async def execute(code):
    async with websockets.connect('ws://localhost:8999') as ws:
        await ws.send(code)
        result = await ws.recv()
        print(result)

asyncio.run(execute("1.plus(2)"))
```

---

## Documentation System

Metatron uses **AsciiDoc** for comprehensive user documentation with an automated code execution system that keeps examples in sync with the language.

### Documentation Structure

**Location**: `/home/killswitch/software/metatron/docs/`

**Main Files**:
- `index.adoc` - Main documentation entry point, includes all sections
- `intro.adoc` - Introduction and core concepts
- `m.adoc` - Core instruction set documentation
- `space.adoc` - Space architecture
- `mkv.adoc`, `mweb.adoc`, `mgrph.adoc`, `mvec.adoc`, `mllm.adoc`, `mach.adoc` - Instruction set modules

**Supporting Files**:
- `css/metatron.css` - Custom styling
- `highlight/languages/mtron.min.js` - Syntax highlighting for mtron
- `images/` - Documentation images and diagrams
- `javascript/metatron-docs.js` - Interactive documentation features

### Automated Code Execution System

The documentation includes a **Python-based code runner** that executes mtron code blocks and injects the output back into the documentation. This ensures examples are always accurate and up-to-date.

**How It Works**:
1. Mark code blocks with the magic marker `<!-- 🐖`
2. The parser (`docs/python/docs-code-parser.py`) finds these blocks
3. Code is sent to a running Metatron instance via WebSocket (port 8999)
4. Output is captured and injected into the generated documentation
5. Documentation stays in sync with language changes automatically!

**Code Block Format**:
```asciidoc
++++
<!-- 🐖
[HIDDEN] a -> 'abc'              [-- Hidden setup code
int                               [-- <1> Visible code with callout
int + int                         [-- <2>
int + int{-1}                     [-- <3>
-->
++++
```

**Special Markers**:
- `[HIDDEN]` - Execute code but don't show it in output (for setup)
- `[HEADER]` - Add header text to output section
- `[-- <n>` - Callout numbers for annotations
- `%` - Line continuation character

### Running the Documentation System

**Prerequisites**:
1. Start Metatron with WebSocket server on port 8999
2. Python 3.8+ with required dependencies

**Generate Documentation**:
```bash
# Navigate to docs directory
cd /home/killswitch/software/metatron/docs

# Run the doc parser
python python/docs-code-parser.py . -o . -d

# This processes all .adoc files and updates them with live code output
```

**Python Dependencies**:
- `websockets` - For WebSocket communication with Metatron
- `asyncio` - For async WebSocket handling

### Documentation Development Workflow

1. **Edit AsciiDoc files** in `/home/killswitch/software/metatron/docs/`
2. **Add executable code blocks** using the `🐖` marker
3. **Start Metatron** with WebSocket server enabled
4. **Run the doc parser** to execute code and update outputs
5. **Build HTML documentation** (using AsciiDoctor)
6. **Review and iterate**

### AsciiDoc Features Used

- **LaTeX Math**: For algebraic notation (Stream Ring Theory)
  ```asciidoc
  [stem]
  ++++
  \langle CF, +, \cdot \rangle
  ++++
  ```

- **Syntax Highlighting**: Custom mtron language support
  ```asciidoc
  [source,mtron]
  ----
  {1,2,3}-<[+1,+2,+3]>-
  ----
  ```

- **Collapsible Sections**: For long code outputs
  ```asciidoc
  .Section Title
  [%collapsible]
  ====
  Content here
  ====
  ```

- **Tables**: For type references and comparisons
  ```asciidoc
  [cols="1,2,6",options=header]
  |===
  | Type | Example | Description
  | int  | 123     | 64-bit integer
  |===
  ```

### Contributing to Documentation

When adding new documentation:

1. **Use executable examples** wherever possible (with `🐖` marker)
2. **Include edge cases** in examples (empty collections, negative numbers, etc.)
3. **Add callouts** to explain complex code: `[-- <1>`
4. **Test examples** by running the doc parser before committing
5. **Follow existing style** for consistency
6. **Add images** to `docs/images/` for visual explanations
7. **Update index.adoc** if adding new sections

### Documentation vs SWEEP.md

**SWEEP.md** (this file):
- Quick reference for developers and AI assistants
- Language syntax, patterns, and conventions
- Testing patterns and build commands
- Stream Ring Theory foundation
- Fast to read and search

**AsciiDoc Documentation** (`/docs/*.adoc`):
- Comprehensive user documentation
- Tutorials and examples
- Architecture diagrams and visual aids
- Executable code examples
- Published HTML documentation

Both serve different purposes and should be maintained in parallel.

---

## Code Style & Conventions

### Java Conventions
1. **Imports**: Organize imports, remove unused imports
2. **Generics**: Use generic type parameters where appropriate (e.g., `AbstractSerializerTest<T>`)
3. **Final**: Use `final` for parameters and local variables where appropriate
4. **Logging**: Use SLF4J logger: `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`

### Test Naming
- Test methods: `testOperationName()` (e.g., `testReverse()`, `testPlus()`, `testCount()`)
- Use descriptive names that indicate what operation is being tested

### Documentation
- Use JavaDoc for public classes and methods
- Include `@param`, `@return`, and class-level descriptions
- For Java 23+, Markdown-style `///` comments are supported (JEP 467)
- For earlier versions, use standard HTML in JavaDoc

### Metatron Code in Tests
- Align test cases for readability
- Use consistent spacing around `%` delimiter
- Group related test cases together
- Comment out failing/WIP tests with `//` rather than removing them

### Example Test Format
```java
@ParameterizedTest
@CsvSource(value = {
        "[a=>1,b=>2,c=>3].reverse()                                  % [c=>3,b=>2,a=>1]",
        "[a=>1,b=>2].reverse()                                       % [b=>2,a=>1]",
        "[a=>1].reverse()                                            % [a=>1]",
        "[=>].reverse()                                              % [=>]",
}, delimiter = '%')
public void testReverse(final String code, final String expected) {
    AbstractMetatronTest.testCode(LOG, code, expected);
}
```

---

## Important Notes

### Recent Changes & Patterns
1. **Branched structures**: Polys (Lst, Rec) are branched structures - each element/entry is a branch
2. **Merge operator (`>-`)**: Merges branches into a single stream for operations
3. **Split operator (`-<`)**: Splits a stream into branches (opposite of merge)
4. **Coefficient interference**: Coefficients can be negative; when merged, they add (enabling constructive/destructive interference)
5. **Coefficient ranges**: Support regex-style range patterns (`{*}`, `{+}`, `{?}`, `{min,max}`, `{min,}`, `{,max}`)
6. **Count behavior**: `.count()` returns 1 for the poly object itself; use `>-.count()` to count elements/entries
7. **Comparison operators**: Use `gte`/`lte` (not `geq`/`leq`) for greater/less than or equal
8. **Record selection**: Use pattern syntax `[a=>_,b=>_]` instead of `[a,b]` for selecting keys
9. **String count removed**: `.count()` operation was removed from strings
10. **URI count behavior**: `.count()` returns 1 for the URI object itself, not path segment count
11. **Objs count behavior**: `.count()` returns total element count including duplicates

### Common Pitfalls
- Don't confuse list syntax `[a,b,c]` with record syntax `[a=>1,b=>2,c=>3]`
- **Critical**: `.count()` on polys returns 1 (the object itself), use `>-.count()` to count elements/entries
- Remember that `>-` is the **merge operator** (merges branches into stream) and `-<` is the **split operator** (splits stream into branches)
- Polys (Lst, Rec) are **branched structures** - each element/entry is a separate branch
- Use `_` for wildcards in pattern matching
- Empty collections: `[,]` for lists, `[=>]` for records, `{,}` for sets
- Coefficient syntax: `type{coefficient}::value`
- Coefficients optimize performance by bulking repeated computations (e.g., `{1,1,1,1,1,1,1}.plus(2)` executes once on `int{7}::1`)
- Coefficients can be **negative** and add when merged, enabling interference (e.g., `int{100}::2 + int{-75}::2 = int{25}::2`)
- When coefficients sum to zero during merge, the element disappears (destructive interference)
- Coefficient ranges use regex-style syntax: `{*}` (any), `{+}` (one or more), `{?}` (zero or one), `{min,max}`, `{min,}`, `{,max}`
- Use `[ ]` brackets to prevent integers from unrolling when you want to preserve coefficients

---

## Future Reference

### When Adding New Tests
1. Read existing test files for the type you're testing
2. Follow the parameterized test pattern with `@CsvSource`
3. Test edge cases (empty, single element, negative values)
4. Test chained operations
5. Test pattern matching where applicable
6. Run tests to verify they pass
7. Check for linting errors with `get_errors` tool

### When Learning New Operations
1. Check the corresponding test file (e.g., `RecTest.java` for record operations)
2. Look at the interface definition in `src/main/java/.../type/`
3. Examine existing test cases for usage patterns
4. Add new test cases to improve coverage

---

## Quick Reference Card

| Type | Syntax Example | Common Ops |
|------|----------------|------------|
| Int | `42`, `-5` | `plus`, `mult`, `gt`, `gte`, `sum` |
| Real | `3.14`, `-2.7` | `plus`, `mult`, `gt`, `gte`, `sum` |
| Str | `"hello"` | `plus`, `reverse`, `eq` |
| Bool | `true`, `false` | `plus` (OR), `mult` (AND), `not` |
| Uri | `a/b/c`, `/path` | `reverse`, `count`, `eq` |
| Lst | `[a,b,c]`, `[,]` | `reverse`, `merge`, `plus`, `>-.count()` |
| Rec | `[a=>1,b=>2]`, `[=>]` | `reverse`, `dom`, `rng`, `select`, `>-.count()` |
| Objs | `{1,2,3}`, `{,}` | `merge`, `count`, `plus` |
| Rel | `1->2`, `a->b` | `first`, `second`, `eq`, `plus` |
| NoObj | `noobj` | `isNoObj()` |
| Byte Units | `kB::1024`, `mB::1`, `gB::5` | `as`, `eq` (binary: 1 kB = 1024 bytes) |

---

## Theoretical Foundation: Stream Ring Theory

Metatron is built on **Stream Ring Theory**, an algebraic framework that enables the composition of functional structures respecting the axioms and theorems of algebraic ring theory. This foundation makes Metatron Turing Complete.

### Core Mathematical Concepts

#### 1. **Streams**
- A stream is an **unordered list of objects**: `x = ⟨x₁, x₂, ..., xₙ⟩`
- Streams are **directed**: objects flow from tail to head
- Streams are **atemporal**: no required order for function application, merging, or bulking

#### 2. **Stream Objects**
- Every object `x ∈ X` has a coefficient `c ∈ C`
- Denoted as `cx ∈ CX` (coefficient-prefixed object)
- When coefficient is 1, we write `x` instead of `1x`

#### 3. **Stream Functions**
- Signature: `a : X → Y*` (consumes from incoming stream, produces to outgoing stream)
- Every function has a coefficient `c ∈ C`
- Denoted as `ca ∈ CF` (coefficient-prefixed function)

#### 4. **The Stream Ring ⟨CF, +, ·⟩**
The stream ring is the product of:
- **Coefficient Ring** `⟨C, +, ·⟩` - typically integers ℤ, but can be any ring with unity
- **Function Ring** `⟨F, +, ·⟩` - all stream functions

**Stream Ring Operators:**
- **Addition (`+`)**: Creates parallel branches (split)
  - `ca + db` creates two parallel functions sharing same incoming/outgoing streams
  - When `a = b`: `ca + db = (c + d)a` (bulk coefficients)
- **Multiplication (`·`)**: Creates serial composition (pipe)
  - `ca · db = (c · d)(a · b)` (compose functions, multiply coefficients)

### Stream Ring Axioms

If `x, y, z ∈ X` are objects, `c, d, e ∈ C` are coefficients, and `a, b ∈ F` are functions:

1. **Equivalence**: `x ∼ y ⟹ ⟨cx⟩ = ⟨cy⟩`
2. **Unordered**: `⟨cx, dy⟩ = ⟨dy, cx⟩` (streams are unordered)
3. **Bulk**: `⟨cx, dx⟩ = ⟨(c + d)x⟩` (merge equivalent objects by summing coefficients)
4. **Apply**: `⟨cx⟩da = ⟨(c · d)a(x)⟩` (multiply coefficients when applying functions)
5. **Split**: `⟨cx⟩(da + eb) = (⟨cx⟩da) + (⟨cx⟩eb)` (copy object to parallel branches)
6. **Merge**: `⟨cx⟩ + ⟨dy⟩ = ⟨cx, dy⟩` (merge parallel streams)
7. **Zero**: `⟨0x⟩ = ⟨c∅⟩ = ⟨⟩` (zero coefficient or empty object disappears)

### Function Subrings

The function ring `F` contains four types of functions:

#### 1. **Map Functions** `Fₘ` - One-to-One
- Signature: `a : X → Y`
- Maps each incoming object to exactly one outgoing object
- **Bijective maps** have inverses: `a·a⁻¹ = 1`
- Multiplicative monoid `⟨Fₘ, ·⟩` is functionally closed

#### 2. **Filter Functions** `Fₓ` - One-to-(One or None)
- Signature: `a : X → X ∪ ∅`
- Based on predicate `p : X → {true, false}`
- **Idempotent**: `aⁿ = a` (applying filter multiple times = applying once)
- **Commutative**: `a·b = b·a`
- **Annihilator**: Every filter `a` has `ā = 1 - a` where:
  - `a·ā = 0` (filter and its negation produce nothing)
  - `a + ā = 1` (filter or its negation passes everything)

#### 3. **FlatMap Functions** `Fₓₘ` - One-to-Many
- Signature: `a : X → Y*`
- Maps one incoming object to zero or more outgoing objects
- Both `Fₘ ⊂ Fₓₘ` and `Fₓ ⊂ Fₓₘ`
- Both additive and multiplicative groups are functionally closed

#### 4. **Reduce Functions** `Fᵣ` - Many-to-One
- Signature: `a : X* → Y`
- Consumes entire incoming stream, produces single object
- **Temporal**: All previous functions must execute before reducer
- **Coefficient-aware**: Operates on `CX*` (stream objects with coefficients)
- **Not right distributive**: `(a + b)c ≠ ac + bc` (forms a near-ring)
- **Left distributive**: `c(a + b) = ca + cb`
- All reduce functions have coefficient 1: `ca ∈ CFᵣ ⟹ c = 1`

**Monoidic Reduce Functions** `Fₘᵣ ⊂ Fᵣ`:
- Based on commutative monoid `⟨X, ⊕⟩`
- **Idempotent**: `cⁿ = c`
- **Semi-right distributive**: `(a + b)c = (ac + bc)c`

### Key Theorems

**Universal Functional Commutativity of Coefficients:**
- `ca = a·c` (coefficient can move through expression)
- `ca + cb = c(a + b) = (a + b)c`
- `(ca)ⁿ = cⁿaⁿ = aⁿ·cⁿ`
- **Exception**: Does NOT apply to reduce functions: `c1·1a ≠ 1a·c1`

**Distributivity:**
- In commutative coefficient ring: `ca + (cd)b = c(a + db) = (a + db)c`
- Greatest common factor is both left and right distributive

**Atemporality:**
- No required order for function application, stream merging, or object bulking
- Enables lazy evaluation: depth-first (save space) or breadth-first (save time)

### Metatron's Implementation

**Merge Operator (`>-`)**: Implements the merge axiom
- Merges branches into single stream
- `[1,2,3]>-.count()` merges list branches and counts elements

**Split Operator (`-<`)**: Implements the split axiom
- Splits stream into branches
- `{1,2,3}-<[+1,+2,+3]` splits each element into operations

**Coefficients**: Implement the bulk axiom
- `int{7}::1` represents integer 1 with coefficient 7
- Performance optimization: `{1,1,1,1,1,1,1}.plus(2)` executes once on `int{7}::1`
- Negative coefficients enable interference: `int{100}::2 + int{-75}::2 = int{25}::2`
- Zero coefficient causes annihilation: `int{1}::1 + int{-1}::1 = 0` (element disappears)

**Branched Structures (Polys)**: Lists and Records are branched
- Each element/entry is a separate branch
- `.count()` returns 1 (the poly object itself)
- `>-.count()` merges branches and counts elements

### Turing Completeness

Stream Ring Theory is **Turing Complete** - any computation expressible by a Turing machine can be written as a stream ring expression. The algebra provides:
- Conditional branching (via filters and pattern matching)
- Iteration (via coefficients and reduce functions)
- State management (via stream objects and coefficients)
- Function composition (via ring multiplication)

### References

Rodriguez, M.A., "Stream Ring Theory," S/V Red Herring's Ship's Log: Chronicles in the Sea of Cortez, pages 10–40, Mulegé, Baja California Sur, México, February 2019.

---

*Last Updated: 2025 - This document will be continuously updated as we learn more about Metatron.*
