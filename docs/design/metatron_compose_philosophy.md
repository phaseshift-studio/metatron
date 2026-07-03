# Metatron as a Declarative Virtual System Compose

This design document explores the conceptual model of a metatron boot script (e.g., [drstynx.boot.mtron](file:///home/killswitch/software/metatron/.metatron/skills/drstynx/assets/drstynx.boot.mtron)) acting as a **virtualized "Docker Compose" environment** for data, computation, and agent runtimes.

---

## 1. The Analogy: Docker Compose vs. Metatron Boot

In modern container architecture, `docker-compose.yml` mounts volumes, exposes ports, and wires database services into a shared virtual network. In metatron, a boot script (`.mtron`) does the exact same thing, but at the **programming language and memory space layer**:

| Docker Compose Concept | Metatron Boot Equivalent | Description |
| :--- | :--- | :--- |
| **`environment:` / `args:`** | `boot/args/` | Declares runtime arguments, parameters, and credentials. |
| **`image:` / `build:`** | `import(/m/...)` | Imports native JVM instruction sets (ISA capabilities). |
| **`volumes:` (Mounts)** | `fsspace::[...]` | Mounts host directories into virtual sandbox URIs (e.g. `localfs:#`). |
| **`ports:` (Exposed)** | `httpspace::[...]` / `wsspace::[...]` | Binds external HTTP/WS network handlers to internal route tables. |
| **`depends_on:` (Services)** | Space registrations (e.g. `tbleSpace` / `grphSpace`) | Binds external data engines (Postgres, Gremlin, Ollama) to URI roots. |
| **Sandbox Network** | Global `Router` | A single unified namespace where any space can dereference any other space. |

---

## 2. Declarative Service Wiring

Using this "compose" pattern, wiring up a multi-database architecture for an agent workspace becomes completely declarative.

### The "Compose" Layout (mtron blueprint)
```mtron
[== 1. External Resources & Ports ==]
boot_args => [
  external => [
    project_root => </home/killswitch/software/metatron>,
    embeddings   => <http:// ginger.local:8025/api/v2>,
    knowgraph    => <ws://ginger.local:8182/gremlin>,
    signatures   => <mariadb://localhost:3306/codespace_db?user=mtron&password=mtron>
  ]
];

[== 2. Import Language Capabilities ==]
import(/m/mach/io);  # File system IO
import(/m/tble);     # Relational database capabilities
import(/m/grph);     # Graph DB traversing
import(/m/vec);      # Vector embeddings

[== 3. Mount Services into Sandbox Spaces ==]

# Mount A: File System Space (volumes)
fsspace::[
  pattern => localfs:#,
  route   => [localfs: => !*<boot_args/external/project_root>]
]@</sys/space/fs/project>;

# Mount B: Vector Embedding Engine (vector service)
vecspace::[
  pattern => vector:#,
  host    => !*<boot_args/external/embeddings>
]@</sys/space/vec/embeddings>;

# Mount C: Relational Signatures Cache (db service)
tblespace::[
  pattern => db:#,
  host    => !*<boot_args/external/signatures>
]@</sys/space/tble/db>;

# Mount D: Graph Call-Graph Store (graph service)
grphspace::[
  pattern => graph:#,
  host    => !*<boot_args/external/knowgraph>
]@</sys/space/grph/calls>;
```

---

## 3. CodeSpace as a Compose Service

Under this philosophy, **CodeSpace** is defined as an orchestrating virtual service that mounts these backend spaces, exposes a unified schema, and spawns indexing threads inside the sandbox:

```mtron
[== 4. Instantiate CodeSpace Service ==]
codespace::[
  pattern => <codespace:#>,
  db      => [
    source => </sys/space/fs/project>,      [-- Linked fs volume --]
    vector => </sys/space/vec/embeddings>,   [-- Linked vector DB --]
    table  => </sys/space/tble/db>,          [-- Linked SQL DB    --]
    graph  => </sys/space/grph/calls>        [-- Linked Graph DB  --]
  ]
]@</sys/space/codespace>;

[== 5. Declare active project service container ==]
project::[
  path   => <localfs:>,
  status => 'active'
]@<codespace:projects/metatron>;
```

---

## 4. Architectural Advantages

1.  **Zero-Configuration Sandbox:**
    An agent does not need to know connection strings or database driver details. It simply reads `/sys/codespace/`. The boot script handles the virtualization boundary.
2.  **Environment Swapping:**
    Swapping from SQLite to PostgreSQL, or from a local Vector DB to a cloud vector database is a single-line modification in the `external` section of the boot args. The agent's traversal code remains completely untouched.
3.  **Composable Agents:**
    Agents are just another service instantiated on top of the mounted spaces:
    ```mtron
    model::[
      name => "RefactorBot",
      tool => [!*eval, </sys/space/codespace>]
    ]@<refactor_bot>;
    ```
