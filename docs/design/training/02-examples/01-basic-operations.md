# Basic Operations - Mtron Language Examples

This document provides practical examples of common operations in the mtron language, drawn from real test cases in `mInstSetTest.java`.

## Arithmetic Operations

### Basic Math
```metatron
1.plus(2)                    % 3
5.minus(3)                   % 2
4.mult(5)                    % 20
10.div(2)                    % 5
```

### Operations on Collections
```metatron
{1,2,3}.plus(2)              % {3,4,5} - add 2 to each element
{10,20,30}.mult(2)           % {20,40,60} - multiply each by 2
{5,10,15}.div(5)             % {1,2,3} - divide each by 5
```

### Aggregation
```metatron
{1,2,3}.sum()                % 6 - sum all elements
{1,2,3,4,5}.sum()            % 15
{10,20,30}.count()           % 4 - count elements (includes noobj)
```

## Collection Operations

### Creating Collections
```metatron
[1,2,3]                      % list (ordered)
{1,2,3}                      % set (unordered)
[a=>1, b=>2, c=>3]           % record (key-value pairs)
```

### Merging Collections
```metatron
{1,2,3}>-                    % {1,2,3} - merge to set
{1,2,3}>-[,]                 % [1,2,3] - merge to list
{1,2,3}>-noobj               % {1,2,3} - merge with noobj
```

### Splitting Collections
```metatron
{1,2,3}-<{mult(10),mult(1)}  % {int{50}::100, int{50}::10}
% Split and apply different operations to each branch
```

## String Operations

### String Manipulation
```metatron
"hello".plus(" world")       % "hello world"
"abc".mult(3)                % "abcabcabc"
```

### String Splitting
```metatron
"a b c".-<' '                % ["a", "b", "c"] - split by space
"one,two,three".-<','        % ["one", "two", "three"] - split by comma
```

### String Joining
```metatron
{"a","b","c"}.>-' '          % "a b c" - join with space
{"x","y","z"}.>-','          % "x,y,z" - join with comma
```

## URI Operations

### URI Splitting
```metatron
a/b/c.-</                    % [a, b, c] - split by slash
/usr/local/bin.-</           % [usr, local, bin]
```

### URI Construction
```metatron
{a,b,c}.>-/                  % a/b/c - join with slash
```

## Type Conversion

### Casting Types
```metatron
1.as(str::T)                 % "1" - int to string
"42".as(int::T)              % 42 - string to int
3.14.as(int::T)              % 3 - real to int
"3.14".as(real::T)           % 3.14 - string to real
```

### Record to List
```metatron
[a=>1, b=>2].as(lst::T)      % [(0=>(a=>1)), (1=>(b=>2))]
% Converts record to list of indexed key-value pairs
```

## Filtering and Selection

### Filtering with Predicates
```metatron
{1,2,3,4,5}.is(gt(2))        % {3,4,5} - elements > 2
{1,2,3,4,5}.is(gte(3))       % {3,4,5} - elements >= 3
{1,2,3,4,5}.is(lt(3))        % {1,2} - elements < 3
{1,2,3,4,5}.is(eq(3))        % {3} - elements == 3
```

### Counting Filtered Results
```metatron
{1,2,3}.is(gt(2)).count()    % 2 - count elements > 2
{1,2,3,4}.is(gt(5)).count()  % 0 - no elements > 5
```

### Select (Map over Records)
```metatron
{[a=>1],[a=>2],[a=>3]}.select([a=>+10])
% {[a=>11],[a=>12],[a=>13]} - add 10 to field 'a'

{[a=>1,b=>10],[a=>2,b=>20]}.select([a=>+1,b=>+5])
% {[a=>2,b=>15],[a=>3,b=>25]} - add to multiple fields
```

### Where (Filter Records)
```metatron
{[a=>1],[a=>2],[a=>3]}.where([a=>is(gte(2))])
% {[a=>2],[a=>3]} - keep records where a >= 2

{[a=>1,b=>10],[a=>2,b=>20],[a=>3,b=>30]}.where([a=>is(gt(1))])
% {[a=>2,b=>20],[a=>3,b=>30]} - keep records where a > 1
```

## Navigation Operations

### Navigate Into (`>>`)
```metatron
*a.>>b                       % navigate into field 'b'
*a.>>b>>c                    % multi-level navigation
*a.>>b>>d>>2                 % navigate to indexed element
*a.>>{a,b/c}                 % navigate into multiple fields → {1,2}
```

### Navigate Out (`<<`)
```metatron
*a.>>b.<<                    % navigate in, then back out → *a
*a.>>b>>d.>>.<<.<<           % navigate in 2 levels, out 2 levels → *b
```

### Navigation Example (from test)
Given:
```metatron
a -> [b => [c => 1, d => [0 => [e,f], 1 => [g,h]]]]
```

Then:
```metatron
*a.>>b                       % [c=>1, d=>[[e,f],[g,h]]]
*a.>>b>>c                    % 1
*a.>>b>>d                    % [[e,f],[g,h]]
*a.>>b>>d>>1                 % [g,h]
*a.>>{a,b/c}                 % {1,2} - navigate multiple paths
```

## Quantifiers (Multiplicity)

### Creating Multiple Copies
```metatron
int{50}::10                  % 50 copies of integer 10
str{3}::"hello"              % 3 copies of string "hello"
```

### Mixed Quantifiers
```metatron
{int{2}::1, int{3}::2}       % 2 copies of 1, 3 copies of 2
% Result: {1,1,2,2,2}
```

## Reduce and Fold

### Reduce with Lambda
```metatron
{1,2,3,4,5}.reduce(|plus(0))  % 15 - sum all elements
% The | indicates a lambda/closure
```

### Custom Reduction
```metatron
{1,2,3}.reduce(|mult(1))      % 6 - multiply all elements (1*2*3)
```

## Pattern Matching and Branching

### Branch with Pattern Matching
```metatron
1-<|[is(gt(0))=>plus(6), _=>plus(100)].rng()
% 7 - since 1 > 0, add 6

-1-<|[is(gt(0))=>plus(6), _=>plus(100)].rng()
% 99 - since -1 not > 0, add 100 (default case)
```

The `_` represents the default/identity case.

## Working with Records

### Creating Records
```metatron
[a=>1, b=>2, c=>3]           % simple record
[name=>"Alice", age=>30]     % record with different types
```

### Accessing Record Fields
```metatron
*myrecord.>>name             % navigate to 'name' field
*myrecord.>>age              % navigate to 'age' field
```

### Transforming Records
```metatron
{[a=>1],[a=>2]}.select([a=>mult(10)])
% {[a=>10],[a=>20]} - multiply field 'a' by 10
```

## Working with Lists

### Creating Lists
```metatron
[1,2,3]                      % list of integers
["a","b","c"]                % list of strings
[,]                          % empty list
```

### Accessing List Elements
```metatron
*mylist.>>0                  % first element
*mylist.>>1                  % second element
*mylist.>>2                  % third element
```

## Instruction Definitions

### Inline Instruction with Type Signature
```metatron
{1,3,8}.inst?int<=int(a=>plus(2)){ plus(*a) }
% {4,8,18}
% For each element x: x + (x + 2)
% 1 + 3 = 4, 3 + 5 = 8, 8 + 10 = 18
```

Format: `inst?output_type<=input_type(params){ body }`

## Chaining Operations

### Complex Chains
```metatron
{1,2,3,4,5}
  .is(gt(2))                 % {3,4,5} - filter
  .mult(10)                  % {30,40,50} - transform
  .sum()                     % 120 - aggregate
```

### Split, Transform, Merge
```metatron
"hello world"
  .-<' '                     % ["hello", "world"] - split
  .map(capitalize)           % ["Hello", "World"] - transform
  .>-' '                     % "Hello World" - merge
```

## Conditional Defaults

### Using `.else()`
```metatron
*config/port.else(config/port -> 8080)
% If config/port doesn't exist, set it to 8080

*user/name.else(user/name -> "Anonymous")
% If user/name doesn't exist, set it to "Anonymous"
```

## References and Dereferencing

### Basic Dereferencing
```metatron
x -> 42                      % assign 42 to x
*x                           % 42 - dereference x
```

### Dereferencing URIs
```metatron
*netflix:movie/1             % fetch movie with id=1
*netflix:movie/1/title       % fetch just the title field
*users/+                     % fetch all users (wildcard)
```

### Execute and Dereference
```metatron
!*boot/args/mqtt/broker      % execute from(boot/args/mqtt/broker)
!*</m/mach/io/serializer/json/simple>  % execute to get serializer
```

## Next Steps

- See [Mtron Syntax](../02-language/01-mtron-syntax.md) for complete syntax reference
- See [Advanced Examples](02-advanced-patterns.md) for complex patterns
- See [SQL Integration](03-sql-integration.md) for database operations
- See [IoT Examples](04-iot-integration.md) for IoT/MQTT usage
