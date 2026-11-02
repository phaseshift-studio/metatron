# Metatron Language Syntax

This document provides examples of Metatron's language syntax, extracted from real test cases.

## Basic Values

### Numbers
```metatron
1                    % Integer
1.5                  % Real number
int{5}::3            % Integer with coefficient 5, value 3
int{2,4}::1          % Integer with coefficient range [2,4], value 1
```

### Strings
```metatron
"hello"              % String
'world'              % String (alternative)
```

### Collections

#### Lists
```metatron
{1,2,3}              % Set/stream of integers
[1,2,3]              % List of integers
{,}                  % Empty set
[,]                  % Empty list
```

#### Records
```metatron
[a=>1,b=>2,c=>3]     % Record (key-value pairs)
[=>]                 % Empty record
```

#### Relations
```metatron
a=>b                 % Relation (pair)
1=>2=>3              % Nested relation
```

## Operations

### Arithmetic
```metatron
1.plus(2)            % 3
{1,2,3}.plus(2)      % {3,4,5}
{1,2,3}.mult(10)     % {10,20,30}
{1,2,3}.sum()        % 6
{1,2,3}.prod()       % 6 (product)
```

### Identity and Placeholder
```metatron
_                    % Identity/placeholder (current value)
1.plus(_)            % 2 (1 + 1)
{1,2,3}.plus(_)      % {2,4,6} (each + itself)
```

### Map
```metatron
{1,2,3}.map(+2)                    % {3,4,5}
{1,2,3}.map(mult(10))              % {10,20,30}
{1,2,3}.map(map(+2))               % {3,4,5} (nested map)
```

### Filter
```metatron
{1,2,3,4}.is(gt(2))                % {3,4}
{1,2,3,4}.is(gt(2)).count()        % 2
```

### Count and Sum
```metatron
{1,2,3,4}.count()                  % 4
{1,2,3,4}.sum()                    % 10
{1,2,3,4}.sum{2}()                 % int{2}::10 (sum with coefficient 2)
```

### Take and Skip
```metatron
{1,2,3,4}.take(2)                  % {1,2}
{1,2,3,4}.skip(2)                  % {3,4}
{1,2,3,4,5}.skip(2).take(2)        % {3,4}
```

## Advanced Operations

### Merge (`>-`)
Merges a stream into a collection:

```metatron
{1,2,3}>-                          % {1,2,3} (merge into set)
{1,2,3}>-[,]                       % [1,2,3] (merge into list)
{1,2,3}>-noobj                     % {1,2,3} (merge with noobj)
{1,2}>-[3,4]                       % [1,2,3,4] (merge and append)
```

### Split (`-<`)
Splits a value into multiple streams:

```metatron
{1,2,3}-<{1,2}                     % {int{3}::1,int{3}::2}
{1,2,3}-<[_,_,_]                   % {[1,1,1],[2,2,2],[3,3,3]}
"a b c".-<' '                      % ["a", "b", "c"] (split string)
```

### Barrier
Creates a barrier that collects all values:

```metatron
{1,2,3}.barrier([,])               % Collects into list
{1,2,3,4}.barrier([,])-<[>-.count(),>-.count()]  % [4,4]
```

### Pattern Matching (`-<|`)
Pattern-based routing:

```metatron
{1,2}-<|[?>1 => +100, _=> +2]>>    % {3,102}
// If value > 1, add 100; otherwise add 2
```

### Reduce
```metatron
{1,2,3,4,5}.reduce(|plus(0))       % 15
{1,2,3,4,5}.reduce(|mult(1))       % 120
```

## Type Annotations

### Domain and Range
```metatron
map?int<=int(+2)                   % Map from int to int
sum?int<=int{3}()                  % Sum with specific quantifier
```

### Quantifiers
```metatron
int{3}                             % Exactly 3 integers
int{2,4}                           % Between 2 and 4 integers
int{*}                             % Zero or more integers
int{+}                             % One or more integers
int{?}                             % Zero or one integer
```

## Relations and Records

### Accessing Relations
```metatron
a=>b=>c.rng()                      % b=>c (range)
a=>b=>c.dom()                      % a (domain)
1=>2=>c.>>                         % 2=>c (dereference)
```

### Record Operations
```metatron
[a=>1,b=>2,c=>3]>-                 % {a=>1,b=>2,c=>3}
[a=>1,b=>2]>-.>-[=>]               % [a=>1,b=>2]
```

## String Operations

### Regex
```metatron
'123'.regex('\\d')                 % ['1','2','3']
'abcd'.regex('[a-z]{2}')           % ['ab','cd']
```

### Split and Merge
```metatron
"a b c".split(' ')                 % ["a", "b", "c"]
{"a","b","c"}.merge(' ')           % "a b c"
"a b c".split(' ').merge(' ')      % "a b c"
```

## Instructions

### Custom Instructions
```metatron
{1,2,3}.inst(_,+1,+2){ map(*0).plus(*1).plus(*2) }  % {6,9,12,15}
// Custom instruction with arguments
```

### Instruction with Quantifiers
```metatron
{1,1,2,2,3,5}.inst?int<=int{2}(){ sum() }           % {2,4,8}
// Process pairs and sum them
```

## Practical Examples

### Nested Operations
```metatron
{1,2,3}.map(+2).sum()                               % 12
{1,2,3,4}.is(gt(2)).count()                         % 2
{1,2,3,4,5}.skip(2).take(2).sum()                   % 7
```

### Complex Pipelines
```metatron
{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_.>-.sum{2}()  % int{2}::36
// Flatten lists, sum all, apply coefficient
```

### Type-Safe Operations
```metatron
{1,2,3}.map?int<=int(+2)                            % {3,4,5}
{1,2,3}.sum?int<=int{3}()                           % 6
```

### Pattern-Based Routing
```metatron
{1,2,3,4}-<|[?=1=>+10,?=2=>+20,?=3=>+30,?=4=>+40].>>  % {11,22,33,44}
// Route each value to its handler
```

## Operators Summary

| Operator | Name | Description |
|----------|------|-------------|
| `_` | Identity | Current value / placeholder |
| `>-` | Merge | Merge stream into collection |
| `-<` | Split | Split value into streams |
| `-<\|` | Pattern Split | Pattern-based routing |
| `>>` | Dereference | Navigate into relation |
| `._/...\\_` | Nest | Nest operations |
| `.` | Pipe | Chain operations |
| `?` | Type annotation | Specify types |
| `{n}` | Quantifier | Specify quantity |
| `=>` | Relation | Create key-value pair |

## Type System

### Basic Types
- `int` - Integer
- `real` - Real number
- `bool` - Boolean
- `str` - String
- `uri` - URI

### Collection Types
- `lst` - List
- `rec` - Record
- `rel` - Relation

### Meta Types
- `type` - Type
- `inst` - Instruction
- `noobj` - No object (null/empty)

## Console vs Java

### Console Syntax
```metatron
mtron> {1,2,3}.map(+2)
==> {3,4,5}

mtron> *db:users/1
==> [id=>1,name=>"Alice"]
```

### Java Equivalent
```java
// Parse and execute
Obj result = mParser.m_code().parse("{1,2,3}.map(+2)").get().apply();
// result: {3,4,5}

// Read from space
Obj user = Router.readFromSpace(f("db:users/1"));
// user: [id=>1,name=>"Alice"]
```

## Key Takeaways

1. **Functional style** - Operations chain with `.`
2. **Type annotations** - `?type<=type` for type safety
3. **Quantifiers** - `{n}`, `{*}`, `{+}`, `{?}` for cardinality
4. **Merge/Split** - `>-` and `-<` for stream manipulation
5. **Pattern matching** - `-<|` for conditional routing
6. **Identity** - `_` represents current value
7. **Coefficients** - `int{5}::3` for quantified values

## Next Steps

- See [Basic Reads](01-basic-reads.md) - Reading data with URIs
- Explore [Pattern Wildcards](02-pattern-wildcards.md) - URI patterns
- Read [Field-Level Access](03-field-access.md) - Accessing fields

---

**Note**: These examples are from `mInstSetTest.java` - real test cases that verify the language implementation. The syntax is concise, expressive, and mathematically grounded in category theory.
