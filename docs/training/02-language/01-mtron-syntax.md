# Mtron Language Syntax

This document covers the core syntax of the **mtron language** - the textual programming language used to interact with the metatron environment.

## Comments

Comments use square brackets with double equals:

```metatron
[== This is a comment ==]
[== mount instruction sets ==]
[== global settings store in boot ==]
```

## Literals

### Numbers
```metatron
1                    % integer
3.14                 % real/float
```

### Strings
```metatron
"hello world"        % string literal
"a b c"              % string with spaces
```

### URIs
URIs use angle brackets `< >`:

```metatron
<http://localhost:8080>
<mqtt://broker.local:1883>
<mariadb://localhost:3306/database?user=admin&password=secret>
<ws://127.0.0.1:7777>
<file:///path/to/file>
```

### Paths
Simple paths without angle brackets:

```metatron
/usr/local/bin
a/b/c
boot/args/mqtt
```

## Collections

### Lists
Ordered collections with square brackets:

```metatron
[1,2,3]              % list of integers
["a","b","c"]        % list of strings
[,]                  % empty list
```

### Sets
Unordered collections with curly braces:

```metatron
{1,2,3}              % set of integers
{"a","b","c"}        % set of strings
```

### Records
Key-value pairs (relations):

```metatron
[a=>1, b=>2, c=>3]   % record with three fields
[name=>"Alice", age=>30]
[=>]                 % empty record
```

Single relation:
```metatron
a=>1                 % single key-value pair
name=>"Bob"
```

## Operators

### Navigation Operators

**`>>` - Navigate Into (Move Right)**
```metatron
*a.>>b               % navigate into field 'b' of object at 'a'
*a.>>b>>c            % multi-level navigation
*a.>>b>>d>>2         % navigate into indexed element
*a.>>{a,b/c}         % navigate into multiple fields
```

**`<<` - Navigate Out (Move Left)**
```metatron
*a.>>b.<<            % navigate in, then back out
*a.>>b>>d.>>.<<.<<   % navigate in multiple levels, then back out
```

### Split and Merge Operators

**`-<` - Split**
```metatron
"a b c".-<' '        % split string by space → ["a", "b", "c"]
a/b/c.-</            % split URI by slash → [a, b, c]
{1,2,3}-<{mult(10),mult(1)}  % branch/split computation
```

**`>-` - Merge**
```metatron
{1,2,3}>-            % merge to set → {1,2,3}
{1,2,3}>-[,]         % merge to list → [1,2,3]
{"a","b","c"}.>-' '  % merge/join with space → "a b c"
```

**`-<|` - Branch with Pattern Matching**
```metatron
1-<|[is(gt(0))=>plus(6), _=>plus(100)].rng()  % if >0 then +6 else +100
```

### Arithmetic Operators
```metatron
1.plus(2)            % 3
5.minus(3)           % 2
4.mult(5)            % 20
10.div(2)            % 5
```

### Comparison Operators
```metatron
is(gt(2))            % greater than 2
is(gte(5))           % greater than or equal to 5
is(lt(10))           % less than 10
is(eq(3))            % equal to 3
```

## Method Chaining

Mtron uses fluent method chaining with dot notation:

```metatron
{1,2,3}.plus(2)                    % {3,4,5}
{1,2,3}.sum()                      % 6
{1,2,3}.count()                    % 4
{1,2,3}.is(gt(2)).count()          % 2 (count elements > 2)
```

## Quantifiers (Multiplicity)

Use `{n}::value` to create multiple copies:

```metatron
int{50}::10          % 50 copies of integer 10
{int{2}::1, int{3}::2}  % 2 copies of 1, 3 copies of 2
```

## Type Annotations

### Type Casting with `as()`
```metatron
1.as(str::T)         % convert 1 to string "1"
"1".as(int::T)       % convert "1" to integer 1
[a=>1,b=>2].as(lst::T)  % convert record to list
```

### Instruction Type Signatures
```metatron
inst?int<=int(a=>plus(2)){ plus(*a) }
% instruction that takes int, returns int, with parameter 'a'
```

Format: `inst?output_type<=input_type(params){ body }`

## References and Dereferencing

### The `*` Operator (Dereference/From)
```metatron
*a                   % dereference 'a' - fetch object at URI 'a'
*boot/args           % fetch object at path boot/args
*netflix:movie/1     % fetch movie with id=1 from netflix space
```

### The `!` Operator (Execute)
```metatron
!*a                  % execute from(a) - don't return literally
!*boot/args/mqtt/broker  % execute and fetch the broker config
!*</m/mach/io/serializer/json/simple>  % execute to get serializer
```

The `!*` combination means "execute the instruction that fetches this value".

## Variable Assignment

Use `->` for assignment:

```metatron
x -> 42              % assign 42 to x
name -> "Alice"      % assign string to name
fs_prefix -> *boot/args/local.as(str::T).plus(':#').as(uri::T)
```

## Conditional Defaults

Use `.else()` to provide default values:

```metatron
<boot/args/mqtt>.else(boot/args/mqtt -> [broker => <mqtt://localhost:1883>])
*<boot/peers>.else(boot/peers -> [ws://localhost:6666])
```

If the value doesn't exist or is empty, execute the else clause.

## Select and Where (SQL-like)

```metatron
{[a=>1],[a=>2],[a=>3]}.select([a=>+10])
% {[a=>11],[a=>12],[a=>13]}

{[a=>1],[a=>2],[a=>3]}.where([a=>is(gte(2))])
% {[a=>2],[a=>3]}
```

## Lambdas and Inline Instructions

### Reduce with Lambda
```metatron
{1,2,3,4,5}.reduce(|plus(0))  % 15 (sum all elements)
```

### Inline Instruction Definition
```metatron
inst?uri<=rec(){ to(a).map(z2m:).mult(*a../friendly_name).mult(set/state) }
```

## Import Statements

Import instruction sets:

```metatron
import(/m/mach/io);
import(/m/math);
import(/m/web);
import(/m/iot);
import(/m/grph/tp3);
import(/m/llm);
import(/m/tble);
```

## Print Statements

### Basic Print
```metatron
print("Hello, World!\n");
print("Value: ", *some/value, "\n");
```

### Color Codes
```metatron
print("{{y}}yellow text{{X}}");
print("{{r}}red{{X}} {{g}}green{{X}} {{b}}blue{{X}}");
```

Color codes:
- `{{y}}` - yellow
- `{{r}}` - red
- `{{g}}` - green
- `{{b}}` - blue
- `{{c}}` - cyan
- `{{m}}` - magenta
- `{{X}}` - reset/clear

## Pattern Matching

### Wildcards
- `+` - Single-level wildcard (matches one segment)
- `#` - Multi-level wildcard (matches remaining segments, must be at end)

```metatron
/users/+             % matches /users/1, /users/2, etc.
/users/#             % matches /users/1, /users/1/name, /users/1/posts/5, etc.
db:#                 % matches db:users, db:users/1, db:users/1/name, etc.
```

### Pattern in Branching
```metatron
value-<|[
  is(gt(0)) => plus(10),
  is(lt(0)) => minus(10),
  _ => identity
].rng()
```

The `_` matches anything (identity/default case).

## Space Mounting Syntax

Mount a space with configuration:

```metatron
type::[config_record]@mount_point
```

Examples:

```metatron
mem::[pattern => /usr/#, q => [subq::[=>]]]@/sys/space/usr;

tble::[pattern    => netflix:#,
       host       => <mariadb://localhost:3306/netflix>,
       route      => [<netflix:>=><>],
       table      => [,]]@/sys/space/netflix;

http::[host     => <http://localhost:8777>,
       pattern  => http://#,
       route    => [/ => examples/www]]@/sys/space/www;
```

## Multi-line Expressions

Records and other structures can span multiple lines:

```metatron
*<boot/script>.else(boot/script ->
 [sh     => /bin/sh,
  bash   => /bin/bash,
  zsh    => /bin/zsh,
  python => /usr/bin/python3,
  perl   => /usr/bin/perl,
  mtron  => /bin/mtron])
```

## Common Patterns

### Fetch and Transform
```metatron
*netflix:movie/1.>>title         % get movie title
*users/+.>>name                  % get all user names
```

### Conditional Assignment with Default
```metatron
*config/port.else(config/port -> 8080)
```

### Chain Operations
```metatron
{1,2,3,4,5}
  .is(gt(2))        % filter > 2
  .mult(10)         % multiply by 10
  .sum()            % sum result
```

### Split, Transform, Merge
```metatron
"hello world".-<' '.map(capitalize).>-' '
% "Hello World"
```

## Key Differences from Other Languages

1. **No semicolons required** (except to separate statements on same line)
2. **Method chaining is primary style** (not nested function calls)
3. **URIs are first-class** (use angle brackets)
4. **Paths are first-class** (no quotes needed for simple paths)
5. **Pattern matching built-in** (with `+` and `#` wildcards)
6. **References are explicit** (use `*` to dereference)
7. **Execution is explicit** (use `!` to execute instructions)

## Next Steps

- See [Language Examples](../02-examples/01-basic-operations.md) for practical examples
- See [Instruction Sets](../01-concepts/02-instruction-sets.md) to understand available operations
- See [Spaces and Routing](../01-concepts/03-spaces-and-routing.md) to understand the environment
