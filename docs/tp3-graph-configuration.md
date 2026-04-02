# TinkerPop3 Graph Configuration Guide

## Overview

The `tp3Space` implementation uses Apache TinkerPop's standard `GraphFactory` mechanism, making it compatible with **any TinkerPop3-compliant graph database**. This includes 20+ graph databases from in-memory solutions to distributed, cloud-hosted systems.

## Configuration Format

Configurations use the `GRAPH` key with Apache Commons Configuration properties:

```java
rec(
    PATTERN, uri("/pattern/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("fully.qualified.GraphClassName"),
        // ... additional graph-specific properties
    )
)
```

## Supported Graph Databases

### 1. TinkerGraph (In-Memory, Reference Implementation)

**Basic in-memory graph:**
```java
rec(
    PATTERN, uri("/g/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")
    )
)
```

**With persistence:**
```java
rec(
    PATTERN, uri("/g/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph"),
        uri("gremlin.tinkergraph.graphLocation"), str("/tmp/mygraph.kryo"),
        uri("gremlin.tinkergraph.graphFormat"), str("gryo")
    )
)
```

**With sample dataset (legacy format):**
```java
rec(
    PATTERN, uri("/g/#"),
    NATIVE, rec(uri(LOAD), uri("modern"))  // or "grateful", "air_routes"
)
```

---

### 2. JanusGraph (Distributed, Scalable)

**With BerkeleyDB backend:**
```java
rec(
    PATTERN, uri("/janus/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.janusgraph.core.JanusGraphFactory"),
        uri("storage.backend"), str("berkeleyje"),
        uri("storage.directory"), str("/var/lib/janusgraph"),
        uri("cache.db-cache"), bool(true),
        uri("cache.db-cache-size"), real(0.5)
    )
)
```

**With Cassandra backend:**
```java
rec(
    PATTERN, uri("/janus/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.janusgraph.core.JanusGraphFactory"),
        uri("storage.backend"), str("cql"),
        uri("storage.hostname"), str("localhost"),
        uri("storage.cql.keyspace"), str("janusgraph")
    )
)
```

**With Cassandra + Elasticsearch:**
```java
rec(
    PATTERN, uri("/janus/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.janusgraph.core.JanusGraphFactory"),
        uri("storage.backend"), str("cql"),
        uri("storage.hostname"), str("cassandra-host"),
        uri("index.search.backend"), str("elasticsearch"),
        uri("index.search.hostname"), str("elasticsearch-host"),
        uri("index.search.elasticsearch.client-only"), bool(true)
    )
)
```

---

### 3. Neo4j (OLTP Graph Database)

**Embedded Neo4j:**
```java
rec(
    PATTERN, uri("/neo4j/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph"),
        uri("gremlin.neo4j.directory"), str("/var/lib/neo4j/data"),
        uri("gremlin.neo4j.conf.dbms.security.auth_enabled"), bool(false)
    )
)
```

**With high availability:**
```java
rec(
    PATTERN, uri("/neo4j/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph"),
        uri("gremlin.neo4j.directory"), str("/var/lib/neo4j/data"),
        uri("gremlin.neo4j.conf.dbms.mode"), str("HA"),
        uri("gremlin.neo4j.conf.ha.server_id"), jnt(1),
        uri("gremlin.neo4j.conf.ha.initial_hosts"), str("neo4j1:5001,neo4j2:5001")
    )
)
```

---

### 4. Amazon Neptune (Cloud Graph Service)

**Remote connection to Neptune:**
```java
rec(
    PATTERN, uri("/neptune/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph"),
        uri("gremlin.remote.remoteConnectionClass"),
            str("org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection"),
        uri("gremlin.remote.driver.clusterFile"), str("conf/neptune-remote.yaml")
    )
)
```

**Neptune remote configuration file (neptune-remote.yaml):**
```yaml
hosts: [your-neptune-endpoint.amazonaws.com]
port: 8182
serializer: {
  className: org.apache.tinkerpop.gremlin.driver.ser.GryoMessageSerializerV3d0,
  config: { ioRegistries: [org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry] }
}
connectionPool: { enableSsl: true }
```

---

### 5. Azure Cosmos DB (Gremlin API)

**Cosmos DB connection:**
```java
rec(
    PATTERN, uri("/cosmos/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph"),
        uri("gremlin.remote.remoteConnectionClass"),
            str("org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection"),
        uri("gremlin.remote.driver.clusterFile"), str("conf/cosmos-remote.yaml")
    )
)
```

**Cosmos DB remote configuration (cosmos-remote.yaml):**
```yaml
hosts: [your-account.gremlin.cosmos.azure.com]
port: 443
username: /dbs/your-database/colls/your-collection
password: your-primary-key
connectionPool: { enableSsl: true }
serializer: { className: org.apache.tinkerpop.gremlin.driver.ser.GraphSONMessageSerializerV3d0 }
```

---

### 6. OrientDB

**Embedded OrientDB:**
```java
rec(
    PATTERN, uri("/orient/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.orientdb.OrientGraph"),
        uri("orient-url"), str("plocal:/var/lib/orientdb/databases/mydb"),
        uri("orient-user"), str("admin"),
        uri("orient-pass"), str("admin")
    )
)
```

---

### 7. ArangoDB

**ArangoDB connection:**
```java
rec(
    PATTERN, uri("/arango/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("com.arangodb.tinkerpop.gremlin.structure.ArangoDBGraph"),
        uri("arangodb.hosts"), str("127.0.0.1:8529"),
        uri("arangodb.user"), str("root"),
        uri("arangodb.password"), str("password"),
        uri("arangodb.graph.name"), str("mygraph")
    )
)
```

---

### 8. Generic Gremlin Server (Remote)

**Connection to any Gremlin Server:**
```java
rec(
    PATTERN, uri("/remote/#"),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph"),
        uri("gremlin.remote.remoteConnectionClass"),
            str("org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection"),
        uri("gremlin.remote.driver.sourceName"), str("g"),
        uri("gremlin.remote.driver.clusterFile"), str("conf/remote-server.yaml")
    )
)
```

**Generic remote configuration (remote-server.yaml):**
```yaml
hosts: [localhost]
port: 8182
serializer: { className: org.apache.tinkerpop.gremlin.driver.ser.GryoMessageSerializerV3d0 }
```

---

## Backward Compatibility

### Legacy Format (Pre-GraphFactory)

The old configuration format still works and defaults to TinkerGraph:

```java
rec(
    PATTERN, uri("/g/#"),
    ROUTE, rec(uri("/g/V"), uri("V"), uri("/g/E"), uri("E")),
    NATIVE, rec(
        uri("factory"), MObjFactory.single(),
        uri(LOAD), uri("modern")  // Loads TinkerGraph modern dataset
    )
)
```

This is equivalent to:

```java
rec(
    PATTERN, uri("/g/#"),
    ROUTE, rec(uri("/g/V"), uri("V"), uri("/g/E"), uri("E")),
    GRAPH, rec(
        uri("gremlin.graph"), str("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")
    ),
    NATIVE, rec(
        uri("factory"), MObjFactory.single(),
        uri(LOAD), uri("modern")
    )
)
```

---

## Sample Datasets

TinkerGraph supports three built-in datasets via the `NATIVE.LOAD` configuration:

- **modern**: Small graph with 6 vertices, 6 edges (people and software)
- **grateful**: Grateful Dead concert data (~800 vertices, ~8000 edges)
- **air_routes**: Airport route data (~3500 vertices, ~50000 edges)

These only work with TinkerGraph. Other graph databases will ignore the dataset loading.

---

## Configuration Properties Reference

### Common Properties (All Graphs)

| Property | Type | Description |
|----------|------|-------------|
| `gremlin.graph` | String | **Required**. Fully qualified class name of the Graph implementation |

### TinkerGraph Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gremlin.tinkergraph.graphLocation` | String | - | File path to persist graph |
| `gremlin.tinkergraph.graphFormat` | String | `gryo` | Format: `gryo`, `graphson`, `graphml` |
| `gremlin.tinkergraph.vertexIdManager` | String | `ANY` | ID manager: `ANY`, `LONG`, `INTEGER`, `UUID`, `CUSTOM` |
| `gremlin.tinkergraph.edgeIdManager` | String | `ANY` | ID manager: `ANY`, `LONG`, `INTEGER`, `UUID`, `CUSTOM` |

### JanusGraph Properties

| Property | Type | Description |
|----------|------|-------------|
| `storage.backend` | String | Backend: `inmemory`, `berkeleyje`, `cql`, `hbase` |
| `storage.directory` | String | Directory for BerkeleyDB backend |
| `storage.hostname` | String | Cassandra/HBase hostname |
| `index.search.backend` | String | Index backend: `elasticsearch`, `solr`, `lucene` |
| `cache.db-cache` | Boolean | Enable database-level cache |

### Remote Connection Properties

| Property | Type | Description |
|----------|------|-------------|
| `gremlin.remote.remoteConnectionClass` | String | Remote connection class (usually `DriverRemoteConnection`) |
| `gremlin.remote.driver.clusterFile` | String | Path to cluster configuration YAML |
| `gremlin.remote.driver.sourceName` | String | Traversal source name (default: `g`) |

---

## Testing Your Configuration

After configuring your graph, test the connection:

```java
// Create tp3Space instance
tp3Space space = tp3Space.of(yourConfig, f("/sys/space/test"));

// Test basic operations
Obj vertices = space.read(f("/g/V/+"));
LOG.info("Found %d vertices", vertices.asObjs().count());

// Close when done
space.close();
```

---

## Dependencies

Add the appropriate graph database dependency to your `pom.xml`:

```xml
<!-- TinkerGraph (included with TinkerPop) -->
<dependency>
    <groupId>org.apache.tinkerpop</groupId>
    <artifactId>tinkergraph-gremlin</artifactId>
    <version>3.7.0</version>
</dependency>

<!-- JanusGraph -->
<dependency>
    <groupId>org.janusgraph</groupId>
    <artifactId>janusgraph-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Neo4j -->
<dependency>
    <groupId>org.apache.tinkerpop</groupId>
    <artifactId>neo4j-gremlin</artifactId>
    <version>3.7.0</version>
</dependency>

<!-- Remote connections (Neptune, Cosmos DB, Gremlin Server) -->
<dependency>
    <groupId>org.apache.tinkerpop</groupId>
    <artifactId>gremlin-driver</artifactId>
    <version>3.7.0</version>
</dependency>
```

---

## Resources

- [Apache TinkerPop Documentation](https://tinkerpop.apache.org/docs/current/)
- [TinkerPop Providers](https://tinkerpop.apache.org/providers.html)
- [GraphFactory JavaDoc](https://tinkerpop.apache.org/javadocs/current/full/)
- [JanusGraph Documentation](https://docs.janusgraph.org/)
- [Amazon Neptune Gremlin](https://docs.aws.amazon.com/neptune/latest/userguide/access-graph-gremlin.html)
- [Azure Cosmos DB Gremlin](https://learn.microsoft.com/en-us/azure/cosmos-db/gremlin/)
