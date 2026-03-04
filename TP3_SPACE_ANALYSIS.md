# tp3Space Performance Analysis & Optimization Plan

## Overview
This document provides an analysis of the `tp3Space` implementation and identifies potential optimization opportunities to achieve performance comparable to native TinkerPop Gremlin.

---

## Architecture Summary

### Core Components

1. **tp3Space** (`/src/main/java/studio/phaseshift/metatron/isa/grph/tp3/space/tp3Space.java`)
   - Extends `grphSpace<Graph>`
   - Wraps TinkerPop `Graph` instances (currently TinkerGraph)
   - Provides Metatron Space interface over TinkerPop graphs
   - Uses `directReader()` and `directWriter()` for graph operations

2. **mGraph** (`/src/main/java/studio/phaseshift/metatron/isa/grph/tp3/graph/mGraph.java`)
   - Implements TinkerPop `Graph` interface
   - Wraps `tp3Space` to provide TinkerPop API
   - Bidirectional bridge: Metatron ↔ TinkerPop

3. **Element Wrappers**
   - **VertexMap**: Wraps TinkerPop `Vertex` as Metatron `Rec`
   - **EdgeMap**: Wraps TinkerPop `Edge` as Metatron `Rec`
   - **ElementMap**: Base class providing property access via Map interface

4. **Graph Elements**
   - **mVertex**: TinkerPop `Vertex` implementation backed by Metatron `Rec`
   - **mEdge**: TinkerPop `Edge` implementation backed by Metatron `Rec`
   - **mProperty/mVertexProperty**: Property implementations

---

## Current Implementation Analysis

### Data Flow

#### Reading from tp3Space
```
User Request → tp3Space.read(fURI)
  → Space.Helper.resolveRead()
  → directReader()
  → TinkerGraph.vertices()/edges()
  → VertexMap/EdgeMap wrapping
  → Metatron Rec objects
```

#### Writing to tp3Space
```
User Request → tp3Space.write(fURI, Obj)
  → Space.Helper.resolveWrite()
  → directWriter()
  → TinkerGraph vertex/edge creation
  → Property updates
  → VertexMap/EdgeMap wrapping
```

### Key Observations

#### Strengths
1. **Clean abstraction**: Separates Metatron and TinkerPop concerns
2. **Lazy evaluation**: Uses `LazyAutoElmnt` for deferred element conversion
3. **Bidirectional**: Can use both Metatron and Gremlin APIs
4. **Type safety**: Strong typing through fURI patterns

#### Potential Performance Bottlenecks

1. **Object Wrapping Overhead** (Lines 125-126, 185-195 in tp3Space.java)
   ```java
   // Every vertex/edge read creates new VertexMap/EdgeMap wrapper
   VertexMap.vertexToRec(v, this)
   EdgeMap.edgeToRec(e, this)
   ```
   - **Impact**: Memory allocation + GC pressure
   - **Frequency**: Every graph traversal operation

2. **Iterator Conversions** (Lines 185-195 in tp3Space.java)
   ```java
   IteratorUtil.stream(this.sjvm.vertices())
     .map(v -> Tuple.Pair.with(..., VertexMap.vertexToRec(v, this)))
     .iterator()
   ```
   - **Impact**: Stream overhead, intermediate collections
   - **Frequency**: Every pattern-based read

3. **Property Access** (ElementMap.java lines 72-79, 112-125)
   ```java
   // Property reads check multiple locations
   Property<?> property = this.base.property(key);
   if (!property.isPresent()) {
       property = this.base.property(":" + key); // Second lookup
       return property.isPresent() ?
         mParser.m_obj().parse(property.value().toString()).get() : noobj();
   }
   ```
   - **Impact**: Double property lookups + parsing overhead
   - **Frequency**: Every property access

4. **Serialization** (ElementMap.java lines 118-123)
   ```java
   // Non-mono objects serialized to strings
   this.base.property(":" + keyString, SERIALIZER.write(value));
   ```
   - **Impact**: String serialization/deserialization overhead
   - **Frequency**: Every complex property write/read

5. **Pattern Matching** (tp3Space.java lines 176-201)
   ```java
   // Multiple string operations and prefix checks
   if (pattern.hasPrefix(f(this.schemaPrefix))) { ... }
   else if (pattern.pathLength() < 3) { ... }
   else if (pattern.equals(f(this.vertexPrefix).extend("#"))) { ... }
   ```
   - **Impact**: String allocations, multiple comparisons
   - **Frequency**: Every read operation

6. **Edge Navigation** (VertexMap.java lines 63-66)
   ```java
   // Streams edges and creates lazy instances
   IteratorUtil.stream(this.getBase().edges(Direction.IN))
     .map(e -> rel(uri(e.label()),
       auto_from_(uri(this.space.elementVID(e)), lazyEdgeToRec(e, this.space)).tryToInst()))
     .collect(new CommonUtil.RecCollector());
   ```
   - **Impact**: Stream overhead, multiple object creations
   - **Frequency**: Every edge traversal

---

## Optimization Opportunities

### High Impact (Likely 2-5x speedup)

#### 1. **Object Pooling for Wrappers**
- **Problem**: New VertexMap/EdgeMap created for every access
- **Solution**: Implement object pool or cache
- **Implementation**:
  ```java
  // In tp3Space
  private final Map<Object, VertexMap> vertexCache = new ConcurrentHashMap<>();

  private VertexMap getCachedVertex(Vertex v) {
      return vertexCache.computeIfAbsent(v.id(),
          id -> new VertexMap(v, this));
  }
  ```
- **Expected gain**: 30-50% reduction in allocations

#### 2. **Lazy Property Loading**
- **Problem**: All properties loaded eagerly via entrySet()
- **Solution**: Load properties on-demand
- **Implementation**:
  ```java
  // In ElementMap
  private Map<String, Object> propertyCache = null;

  @Override
  public Obj get(Object key) {
      if (propertyCache == null) {
          propertyCache = new HashMap<>();
      }
      return propertyCache.computeIfAbsent(key.toString(),
          k -> loadProperty(k));
  }
  ```
- **Expected gain**: 20-40% for property-light traversals

#### 3. **Optimize Pattern Matching**
- **Problem**: Multiple string operations per read
- **Solution**: Pre-compile patterns, use switch on enum
- **Implementation**:
  ```java
  enum PatternType { SCHEMA, VERTEX_ALL, VERTEX_PATTERN, EDGE_PATTERN, UNKNOWN }

  private PatternType classifyPattern(fURI pattern) {
      // Cache classification results
      // Use integer comparisons instead of string operations
  }
  ```
- **Expected gain**: 10-20% for read-heavy workloads

### Medium Impact (Likely 1.5-2x speedup)

#### 4. **Batch Operations**
- **Problem**: Individual vertex/edge operations
- **Solution**: Batch reads/writes when possible
- **Implementation**: Detect bulk patterns like `V/+` and use bulk APIs

#### 5. **Property Serialization Optimization**
- **Problem**: String serialization for complex objects
- **Solution**: Use binary format or custom encoding
- **Expected gain**: 15-30% for property-heavy graphs

#### 6. **Iterator Optimization**
- **Problem**: Stream overhead in hot paths
- **Solution**: Use direct iterators where possible
- **Implementation**: Replace `IteratorUtil.stream().map().iterator()` with custom iterators

### Low Impact (Likely 1.1-1.3x speedup)

#### 7. **String Interning**
- **Problem**: Repeated string allocations for labels/keys
- **Solution**: Intern common strings

#### 8. **Reduce Logging**
- **Problem**: LOG.info() calls in hot paths
- **Solution**: Use LOG.debug() or conditional logging

---

## Benchmarking Strategy

### Test Cases
1. **Vertex Creation**: Bulk vertex insertion
2. **Edge Creation**: Bulk edge insertion
3. **Property Access**: Read/write properties
4. **Traversal**: Simple traversals (out(), in(), both())
5. **Pattern Matching**: Various fURI patterns
6. **Complex Queries**: Multi-hop traversals

### Comparison Baseline
- Native TinkerGraph operations
- Current tp3Space implementation
- Optimized tp3Space implementation

### Metrics
- Operations per second
- Memory allocation rate
- GC pressure
- Latency percentiles (p50, p95, p99)

---

## Implementation Plan

### Phase 1: Profiling (Week 1)
1. Set up JMH benchmarks
2. Profile with JProfiler/YourKit
3. Identify actual hotspots
4. Validate assumptions above

### Phase 2: Quick Wins (Week 2)
1. Implement object pooling
2. Optimize pattern matching
3. Reduce logging overhead
4. Measure improvements

### Phase 3: Deep Optimizations (Week 3-4)
1. Lazy property loading
2. Custom iterators
3. Batch operations
4. Property serialization optimization

### Phase 4: Validation (Week 5)
1. Run TinkerPop test suite
2. Performance regression tests
3. Memory profiling
4. Documentation

---

## Questions for Discussion

1. **Target Performance**: What's the acceptable performance gap vs native TinkerPop?
2. **Memory vs Speed**: Are we willing to trade memory for speed (caching)?
3. **API Compatibility**: Can we change internal APIs or must maintain exact compatibility?
4. **Graph Backends**: Should we optimize for TinkerGraph specifically or all backends?
5. **Use Cases**: What are the most common query patterns in production?

---

## Next Steps

When you return, we can:
1. Set up performance benchmarks
2. Profile the current implementation
3. Implement the highest-impact optimizations
4. Measure and iterate

Looking forward to optimizing tp3Space with you! 🚀
