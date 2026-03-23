# AI Memory - Documentation Index

This directory contains AI-generated documentation that serves as memory for work completed on the Metatron project.

## Current Documentation

### Relation Ring Implementation
- **`rel-ring-summary.md`** - Complete summary of Rel Ring implementation
  - Ring operations (mult, one, zero, neg, plus)
  - Path multiplicities and coefficient multiplication
  - Distributive multiplication over collections
  - Graph/tree exploration semantics
  - Examples and usage patterns
  - **Status**: ✅ Fully implemented and tested

- **`rel-enhancements.md`** - Future enhancement roadmap for Rel type
  - Planned features and improvements
  - Ideas for extending Rel capabilities
  - **Status**: 📋 Planning document

### Code Optimizations
- **`rewrites-summary.md`** - Rewrite optimizations in mInstSet
  - 6 rewrite rules for code optimization
  - Dead code elimination, arithmetic identities, ring-theoretic collapsing
  - Pattern matching and transformation logic
  - **Status**: ✅ Implemented

## Key Accomplishments

### 1. Relation Ring Structure (✅ Complete)
**What**: Relations now implement `Ring.O<Rel>` with full algebraic structure.

**Key Features**:
- Multiplication = relation composition
- Coefficients multiply during composition (path multiplicities)
- Distributive multiplication over Objs collections
- Active identities using `id()` instruction
- Zero relation `(noobj=>noobj)` for dead paths

**Critical Semantics**:
```mtron
{3}(1=>2) × {4}(2=>3) = {12}(1=>3)
```
"When you compose paths, you multiply coefficients. As if there are 100 paths from a to b and 10 paths from b to c, then there are 1000 paths from a to c."

**Graph Exploration**:
```mtron
(a=>b) × {(b=>c), (b=>d)} × {(d=>e), (c=>e)}
```
Enables multi-path tree/graph traversal with dead path tracking.

**Files**:
- `src/main/java/studio/phaseshift/metatron/isa/m/type/Rel.java`
- `src/test/java/studio/phaseshift/metatron/isa/m/type/RelTest.java` (157+ tests)

### 2. Test Infrastructure (✅ Complete)
**What**: Enhanced `SkipInheritedTests` annotation with `include` field.

**Purpose**: Allow specific tests to run even when their tags are filtered out.

**Example**:
```java
@SkipInheritedTests(
    tags = {"mono"},
    include = {"testMonoReadWrite"}
)
```

**Files**:
- `src/test/java/studio/phaseshift/metatron/SkipInheritedTests.java`
- `src/test/java/studio/phaseshift/metatron/SkipInheritedTestsExtension.java`

### 3. Code Rewrites (✅ Complete)
**What**: 6 rewrite optimizations in mInstSet for code optimization.

**Rewrites**:
1. `id_removal_rewrite` - Remove identity instructions
2. `map_nest_rewrite` - Flatten nested map instructions
3. `else_after_count_rewrite` - Dead code elimination
4. `plus_zero_rewrite` - Arithmetic identity (x + 0 = x)
5. `mult_one_rewrite` - Arithmetic identity (x × 1 = x)
6. `split_collapse_rewrite` - Ring-theoretic branch collapsing

**Files**:
- `src/main/java/studio/phaseshift/metatron/isa/m/mInstSet.java`
- `src/test/java/studio/phaseshift/metatron/isa/m/mInstSetTest.java`

## Known Issues

### Compiler Bug: Double Negation Timeout
**Issue**: Back-to-back identical instructions cause timeout.

**Example**: `(a=>b).neg().neg()` times out

**Workaround**: Avoid chaining identical instructions in tests.

**Status**: Known bug for ~1 month, affects testing only (functionality works).

## Future Work

### MCP Server Implementation (📋 Planned)
**Location**: `docs/todo/mcp-server.md`, `docs/todo/mcp-implementation-plan.md`

**Goal**: Build MCP server for Metatron Console access, enabling AI assistants to interact with Metatron in real-time.

**Status**: Planning phase - dependency added then reverted, waiting for implementation.

### Relation Enhancements
See `rel-enhancements.md` for detailed roadmap of future Rel improvements.

## Project Context

### Core Concepts
- **Stream Ring Theory**: Coefficients represent path multiplicities in graph traversal
- **Active Identities**: `id()` is an instruction, not a passive value
- **Verification-Forced Reification**: Proving uniqueness forces computation
- **Relations as Morphisms**: Relations form morphisms in a computational category

### Architecture
- **Instruction Sets**: Modular instruction definitions (mInstSet, etc.)
- **Spaces**: Routing and storage abstractions (httpSpace, mqttSpace, etc.)
- **Types**: Algebraic types with ring structure (Rel, Lst, Int, etc.)
- **Pattern Matching**: Rewriter system for code optimization

### Testing
- JUnit 5 with custom extensions
- Parameterized tests using mtron code strings
- Tag-based test filtering with include exceptions

## File Organization

```
docs/ai/
├── README.md                    # This file - AI memory index
├── rel-ring-summary.md          # Relation Ring implementation summary
├── rel-enhancements.md          # Future Rel enhancements roadmap
└── rewrites-summary.md          # Code rewrite optimizations

docs/todo/
├── mcp-server.md                # MCP server documentation
└── mcp-implementation-plan.md   # MCP implementation plan

docs/training/
└── [Various training materials for understanding Metatron]
```

## Quick Reference

### Running Tests
```bash
# All tests
mvn clean test

# Specific test class
mvn test -Dtest=RelTest

# Specific test method
mvn test -Dtest=RelTest#testRelMultiplication
```

### Key Files to Reference
- **Rel implementation**: `src/main/java/studio/phaseshift/metatron/isa/m/type/Rel.java`
- **Rel tests**: `src/test/java/studio/phaseshift/metatron/isa/m/type/RelTest.java`
- **Ring interface**: `src/main/java/studio/phaseshift/metatron/algebra/Ring.java`
- **Instruction sets**: `src/main/java/studio/phaseshift/metatron/isa/m/mInstSet.java`

---

**Last Updated**: 2025-01-23
**Status**: Active development on Relation Ring features complete, planning MCP server implementation
