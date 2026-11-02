# Universal References: The `!*` System

## The Vision: A Universal Graph

Metatron's ultimate goal is to turn **any data system** into a graph database. The key to achieving this is the **universal reference system** - a way for any value in any system to reference any other value in any system.

This creates a **universal graph** where:
- Every piece of data is a node
- Every reference is an edge
- Everything is navigable

## Two Types of References

### 1. Native References
Use the data system's built-in reference mechanisms:
- **SQL**: Foreign keys (`orders.customer_id → customers.id`)
- **MongoDB**: DBRefs
- **Filesystems**: Symlinks
- **Graph DBs**: Native edges

### 2. Metatron References (`!*`)
When native references don't exist or aren't sufficient, use Metatron's instruction-based reference system.

## The `!*` Reference System

### What is `!*`?

`!*` is a **reference instruction** that means "fetch the object at this URI":

- **`!`** = Execute instruction (don't return literally)
- **`*`** = Dereference/from operator (fetch from URI)
- **`!*abc`** = "Execute from(abc)" = fetch the object at URI `abc`

### How It Works

When Metatron encounters a value like `!*db:users/123`, it:

1. **Recognizes** it as an instruction (the `!` prefix)
2. **Parses** the instruction (`*db:users/123` = from(db:users/123))
3. **Executes** the instruction (fetches the object)
4. **Returns** the referenced object (not the instruction itself)

### Example: Document Database

```javascript
// MongoDB document with Metatron reference
{
  "order_id": 1,
  "customer": "!*db:customers/123",  // Metatron reference
  "product": "!*db:products/456",    // Metatron reference
  "quantity": 2
}

// When you read this order:
Obj order = Router.readFromSpace(f("mongo:orders/1"));

// Accessing the customer field automatically dereferences:
Obj customer = order.at(uri("customer"));
// Returns the actual customer object, not the string "!*db:customers/123"
```

### Example: Filesystem

```
# File: /data/orders/1.json
{
  "order_id": 1,
  "customer": "!*db:customers/123",
  "notes": "!*file:/data/notes/order-1.txt"
}

# File: /data/notes/order-1.txt
"Customer requested express shipping"
```

When you read the order and access the notes:
```java
Obj order = Router.readFromSpace(f("file:/data/orders/1.json"));
Obj notes = order.at(uri("notes"));
// Returns: "Customer requested express shipping"
// Not: "!*file:/data/notes/order-1.txt"
```

## The `>>` Operator: Graph Traversal

The `>>` operator is the **navigation primitive** for traversing the graph:

### Basic Traversal
```java
// Start with an order
Obj order = Router.readFromSpace(f("db:orders/1"));

// Navigate to customer (follows reference)
Obj customer = order >> uri("customer");

// Navigate to customer's address
Obj address = customer >> uri("address");

// Chain them together
Obj address = order >> uri("customer") >> uri("address");
```

### Multi-Level Patterns

You can also use URI patterns for multi-level traversal:

```java
// All products in all orders
Obj products = Router.readFromSpace(f("db:orders/+/product/+"));

// Automatically follows:
// orders/1 → product reference → products/123
// orders/2 → product reference → products/456
// etc.
```

## Cross-System References

The power of `!*` is that it works **across any data system**:

### SQL → MongoDB
```sql
-- SQL table: orders
CREATE TABLE orders (
  id INT PRIMARY KEY,
  customer_ref TEXT  -- Contains: "!*mongo:customers/abc123"
);
```

```java
// Read order from SQL
Obj order = Router.readFromSpace(f("db:orders/1"));

// Navigate to customer in MongoDB
Obj customer = order >> uri("customer_ref");
// Automatically fetches from MongoDB!
```

### Filesystem → SQL
```
# File: /data/config.json
{
  "database": "!*db:config/1",
  "cache": "!*redis:config/main"
}
```

```java
// Read config from filesystem
Obj config = Router.readFromSpace(f("file:/data/config.json"));

// Navigate to database config in SQL
Obj dbConfig = config >> uri("database");
// Fetches from SQL database
```

### Graph → REST API
```java
// Graph node with API reference
{
  "id": "user-123",
  "profile": "!*api:users/123/profile",
  "friends": "!*graph:user-123/friends"
}
```

## Foreign Key Traversal (Future)

Once foreign key detection is implemented, Metatron will automatically follow SQL foreign keys:

### Automatic FK Detection
```sql
CREATE TABLE orders (
  id INT PRIMARY KEY,
  customer_id INT,
  FOREIGN KEY (customer_id) REFERENCES customers(id)
);
```

### Automatic Traversal
```java
// Navigate through foreign key
Obj customer = Router.readFromSpace(f("db:orders/1/customer_id"));
// Automatically detects FK and fetches customers/123

// Multi-level traversal
Obj street = Router.readFromSpace(f("db:orders/1/customer_id/address_id/street"));
// Follows: orders → customers → addresses → street field
```

### Pattern-Based FK Traversal
```java
// All customer names for all orders
Obj names = Router.readFromSpace(f("db:orders/+/customer_id/+/name"));

// Executes efficient JOIN:
// SELECT c.name FROM orders o JOIN customers c ON o.customer_id = c.id
```

## Creating References

### In SQL
Store Metatron references as text:
```sql
INSERT INTO orders (id, customer_ref, product_ref)
VALUES (1, '!*db:customers/123', '!*db:products/456');
```

### In Documents
Use string values with `!*` prefix:
```javascript
{
  "order_id": 1,
  "customer": "!*db:customers/123"
}
```

### In Code
Create references programmatically:
```java
// Create a reference value
Obj customerRef = str("!*db:customers/123");

// Store it
Router.writeToSpace(
  f("db:orders/1"),
  rec(uri("customer_ref"), customerRef)
);

// Later, dereference it
Obj customer = Router.readFromSpace(f("db:orders/1/customer_ref"));
// Returns the actual customer object
```

## Reference Resolution

### Automatic Resolution
Metatron automatically resolves references when:
1. **Field access** - `order.at(uri("customer"))`
2. **`>>` operator** - `order >> uri("customer")`
3. **Pattern matching** - `db:orders/+/customer/+`

### Manual Resolution
You can also manually resolve references:
```java
// Get the reference string
Obj refString = order.at(uri("customer_ref"));
// Returns: "!*db:customers/123"

// Parse and execute
fURI customerUri = f(refString.strValue().substring(2)); // Remove "!*"
Obj customer = Router.readFromSpace(customerUri);
```

## The Universal Graph in Action

### Example: E-Commerce System

```
SQL Database (db:):
  - customers table
  - orders table
  - products table

MongoDB (mongo:):
  - product_reviews collection
  - user_preferences collection

Filesystem (file:):
  - product_images/
  - order_receipts/

Graph DB (graph:):
  - social_network
  - recommendation_graph
```

### Cross-System Navigation

```java
// Start with an order in SQL
Obj order = Router.readFromSpace(f("db:orders/1"));

// Navigate to customer (SQL FK)
Obj customer = order >> uri("customer_id");

// Navigate to preferences (Metatron ref to MongoDB)
Obj prefs = customer >> uri("preferences");
// customer.preferences = "!*mongo:preferences/abc123"

// Navigate to product (SQL FK)
Obj product = order >> uri("product_id");

// Navigate to reviews (Metatron ref to MongoDB)
Obj reviews = product >> uri("reviews");
// product.reviews = "!*mongo:reviews/product-456"

// Navigate to image (Metatron ref to filesystem)
Obj image = product >> uri("image");
// product.image = "!*file:/images/product-456.jpg"

// All in one traversal:
Obj image = order >> uri("product_id") >> uri("image");
```

## Benefits of Universal References

### 1. System Independence
References work regardless of where data lives:
```java
// Same syntax, different systems
Obj data1 = Router.readFromSpace(f("db:users/1"));      // SQL
Obj data2 = Router.readFromSpace(f("mongo:users/1"));   // MongoDB
Obj data3 = Router.readFromSpace(f("file:/users/1"));   // Filesystem
```

### 2. Flexible Data Modeling
Move data between systems without breaking references:
```java
// Reference stays the same
"!*db:customers/123"

// Even if you migrate customers to MongoDB:
"!*mongo:customers/123"

// Just update the reference, code doesn't change
```

### 3. Unified Navigation
One navigation model for all data:
```java
// Same >> operator everywhere
Obj result = start >> uri("field1") >> uri("field2") >> uri("field3");
// Works whether fields are in SQL, MongoDB, filesystem, etc.
```

### 4. Lazy Loading
References are resolved on-demand:
```java
// Fetch order (doesn't fetch customer yet)
Obj order = Router.readFromSpace(f("db:orders/1"));

// Only fetch customer when accessed
Obj customer = order >> uri("customer");
// Now customer is fetched
```

## Implementation Details

### Reference Detection
Metatron detects references by:
1. **Prefix check** - Does value start with `!*`?
2. **Type check** - Is it a string or instruction?
3. **URI validation** - Is the rest a valid URI?

### Reference Execution
When a reference is detected:
1. **Parse** - Extract the URI from `!*uri`
2. **Route** - Find the appropriate Space
3. **Fetch** - Execute `Router.readFromSpace(uri)`
4. **Return** - Return the referenced object

### Caching (Future)
References could be cached to avoid repeated fetches:
```java
// First access - fetches from database
Obj customer1 = order >> uri("customer");

// Second access - returns cached value
Obj customer2 = order >> uri("customer");
```

## Key Takeaways

1. **`!*` creates universal references** - Any value can reference any other value
2. **Works across systems** - SQL, MongoDB, filesystems, graph DBs, etc.
3. **`>>` operator navigates** - Traverse the graph naturally
4. **Automatic resolution** - References are dereferenced transparently
5. **Native + Metatron refs** - Use both for maximum flexibility
6. **Lazy loading** - References fetched on-demand
7. **System independence** - References work regardless of storage

## Next Steps

- See [Basic Reads](../02-examples/01-basic-reads.md) - practical examples
- Explore [Foreign Key Traversal](../03-advanced/02-foreign-key-traversal.md) - deep dive
- Read [The Universal Graph Vision](../04-architecture/03-universal-graph.md) - big picture

---

**Remember**: The `!*` reference system is what makes Metatron truly **universal**. It turns any data system into a graph database by creating a unified reference mechanism that works everywhere. Welcome to the Grid! 🎮✨
