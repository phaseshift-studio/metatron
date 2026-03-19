/**
 * Document database space implementation for metatron.
 * <p>
 * This package provides access to document databases (MongoDB, DocumentDB) through metatron's
 * unified type system and routing infrastructure.
 * </p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * /isa/doc/
 * ├── docSpace.java                          // Main space implementation
 * ├── schema/
 * │   ├── storage/
 * │   │   └── DocumentSchema.java            // How mtron stores data in MongoDB
 * │   └── domain/
 * │       ├── ExistingCollectionSchema.java  // Discover collections & references
 * │       └── DocumentSchemaGenerator.java   // Generate mtron types from collections
 * </pre>
 *
 * <h2>Key Concepts</h2>
 * <ul>
 *   <li><b>Storage Schema</b> - How metatron encodes its own data in MongoDB (key-value, indices)</li>
 *   <li><b>Domain Schema</b> - User-facing collection structures and document types</li>
 *   <li><b>Lazy References</b> - DBRef and manual references resolved via auto_from instructions</li>
 *   <li><b>Type Mapping</b> - MongoDB types mapped to mtron types (ObjectId → uri, Document → rec, etc.)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Create a document space
 * docSpace space = docSpace.of(
 *     rec(
 *         uri(PATTERN), uri("mongo:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("mongo:"), uri("/mongo/"))
 *     ).jvm(),
 *     f("/sys/space/mongo")
 * );
 *
 * // Read a document
 * Obj user = Router.global().read(f("mongo:users/507f1f77bcf86cd799439011"));
 *
 * // Access schema
 * Obj schema = Router.global().read(f("mongo:schema/mydb"));
 * </pre>
 *
 * <h2>Reference Resolution</h2>
 * <p>
 * Document references are resolved lazily using auto_from instructions:
 * </p>
 * <pre>
 * *mongo:orders/123
 * ==> [
 *   _id => '123',
 *   customerId => !*mongo:customers/456,  // auto_from instruction
 *   items => [...]
 * ]
 *
 * *mongo:orders/123>>customerId  // Resolves the reference
 * ==> [
 *   _id => '456',
 *   name => 'Acme Corp',
 *   ...
 * ]
 * </pre>
 *
 * @see studio.phaseshift.metatron.isa.tble.tbleSpace SQL database space
 * @see studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space Graph database space
 */
package studio.phaseshift.metatron.isa.doc;
