/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.doc;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.doc.schema.BsonTypeMapper;
import studio.phaseshift.metatron.isa.doc.schema.domain.ExistingCollectionSchema;
import studio.phaseshift.metatron.isa.doc.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.doc.dcmntInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * docdbSpace - A document database connector for Metatron supporting MongoDB-compatible databases
 *
 * <p>Provides access to MongoDB-compatible document databases through Metatron's unified type system.
 * Compatible with:
 * <ul>
 *   <li>MongoDB (Community or Enterprise)</li>
 *   <li>DocumentDB (MIT licensed, PostgreSQL-based, open source)</li>
 *   <li>Amazon DocumentDB (MongoDB-compatible)</li>
 *   <li>Azure Cosmos DB for MongoDB</li>
 *   <li>Any database implementing the MongoDB wire protocol</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>CRUD operations on documents</li>
 *   <li>Automatic collection discovery</li>
 *   <li>Lazy reference resolution (DBRef and manual references)</li>
 *   <li>Schema inference and type generation</li>
 *   <li>Cross-database references via fURIs</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * // MongoDB with schema discovery enabled
 * docdbSpace space = docdbSpace.of(
 *     rec(
 *         uri(PATTERN), uri("mongo:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("mongo:"), uri("/mongo/")),
 *         uri(COLLECTION), lst()  // Include COLLECTION to enable schema discovery
 *     ).jvm(),
 *     f("/sys/space/mongo")
 * );
 *
 * // DocumentDB without schema discovery (faster startup)
 * docdbSpace space = docdbSpace.of(
 *     rec(
 *         uri(PATTERN), uri("doc:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("doc:"), uri("/doc/"))
 *         // Omit COLLECTION to disable schema discovery
 *     ).jvm(),
 *     f("/sys/space/doc")
 * );
 * }</pre>
 *
 * <h2>Document Access</h2>
 * <pre>{@code
 * // Read a document by ID
 * Obj user = space.read(f("mongo:users/507f1f77bcf86cd799439011"));
 * // Returns: [_id=>'507f1f77...', name=>'John', email=>'john@example.com']
 *
 * // Access with lazy reference resolution
 * Obj order = space.read(f("mongo:orders/123"));
 * // Returns: [_id=>'123', customerId=>!*mongo:customers/456, items=>[...]]
 *
 * // Traverse reference
 * Obj customer = order.asRec().at(uri("customerId"));
 * // Resolves to: [_id=>'456', name=>'Acme Corp', ...]
 * }</pre>
 *
 * <h2>Schema Access</h2>
 * <pre>{@code
 * // Access schema information
 * Obj schema = space.read(f("mongo:schema/mydb"));
 * // Returns: [pattern=>mongo:schema/mydb/#, collections=>[...], references=>[...]]
 * }</pre>
 *
 * <h2>Type Mapping</h2>
 * <ul>
 *   <li>ObjectId → uri (enables cross-database references)</li>
 *   <li>Document → rec (nested records)</li>
 *   <li>Array → lst</li>
 *   <li>String → str, Number → int/real, Boolean → bool</li>
 *   <li>DBRef → auto_from (lazy reference resolution)</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class docdbSpace extends AbstractSpace<MongoClient> {
    private static final String NATIVE_CONNACK = "native/connack";
    public static final String ID_FIELD = "_id";

    protected MongoDatabase database;
    protected String databaseName;
    protected ObjSerializer<BsonValue> serializer;
    protected ExistingCollectionSchema existingCollectionSchema;

    public static docdbSpace of(final Map<Obj, Obj> config, final fURI vid) {
        final MongoClient client = MongoClients.create(config.get(uri(HOST)).uriValue().toString());
        return new docdbSpace(client, config, DOCDB_SPACE_TID, vid);
    }

    protected docdbSpace(final MongoClient sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        // Extract database name from connection string
        // Format: mongodb://host:port/database or mongodb://host:port/database?options
        final fURI connectionfURI = config.get(uri(HOST)).uriValue();
        this.databaseName = connectionfURI.segments(0, null);
        this.database = this.sjvm().getDatabase(this.databaseName);
        this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjBSONSerializer());

        // Configure serializer with reference path builder for lazy resolution
        if (this.serializer instanceof ObjBSONSerializer) {
            ((ObjBSONSerializer) this.serializer).setReferencePathBuilder(refInfo ->
                    this.pattern.retractPattern()
                            .extend(refInfo.collection())
                            .extend(refInfo.id())
            );
        }
        final Rec conn = MObjFactory.of().toObj(this.sjvm()).asRec();
        LOG.debug("{{g}}connected{{X}} %s", conn);
        this.at(uri(NATIVE_CONNACK), conn, MUTABLE);
        LOG.info("using document database {{b}}%s{{X}}", this.databaseName);

        // Initialize collection schema discovery (optional - enabled if COLLECTION is in config)
        final boolean enableSchemaDiscovery = config.getOrDefault(uri(COLLECTION), null) != null;

        if (enableSchemaDiscovery) {
            this.existingCollectionSchema = new ExistingCollectionSchema(this);
            this.existingCollectionSchema.initialize(this.database);

            // Store collection metadata in space config
            this.at(uri(COLLECTION), lst(this.existingCollectionSchema.getCollectionNames().stream()
                    .map(c -> (Obj) uri(c)).toList()), MUTABLE);

            // Store schema in configuration so it doesn't interfere with pattern queries on data
            this.at(uri(SCHEMA), auto_(instC(DCMNT_ISA_INST_TID.dom(ALL.maybe()).rng(REC_TID),lst(),(lhs,inst)-> generateSchema())).tryToInst(), MUTABLE);
            this.generateSchema();
            LOG.info("initialized {{g}}collection schema{{X}} in config for %d collections",
                    this.existingCollectionSchema.getCollectionNames().size());
        } else {
            this.existingCollectionSchema = null;
            // Log available collections without schema discovery
            final List<String> collections = IteratorUtil.list(this.database.listCollectionNames().iterator());
            LOG.info("schema discovery {{y}}disabled{{X}} - discovered {{y}}%d{{X}} collections: %s",
                    collections.size(), collections);
        }
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                // pattern write - write to all matching fURIs
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                // Strip the space's route prefix to get the relative path
                final fURI relativePath = stripPatternPrefix(pattern);
                
                // Determine collection name - either from schema or first segment
                final String collectionName;
                final String documentID;
                final List<String> fieldPath;
                
                if (this.existingCollectionSchema != null && this.existingCollectionSchema.isCollectionPath(relativePath)) {
                    // Known collection from schema
                    collectionName = this.existingCollectionSchema.getCollectionName(relativePath);
                    documentID = this.existingCollectionSchema.getDocumentId(relativePath);
                    fieldPath = this.existingCollectionSchema.getFieldPath(relativePath);
                } else {
                    // Fall back to segment parsing (for wildcard or non-schema mode)
                    final List<String> segments = relativePath.segments();
                    collectionName = segments.isEmpty() ? null : segments.getFirst();
                    documentID = segments.size() > 1 ? segments.get(1) : null;
                    fieldPath = segments.size() > 2 ? segments.subList(2, segments.size()) : null;
                }
                
                if (collectionName == null)
                    return noobj();
                Stream<String> collectionStream;
                if (collectionName.equals("#") || collectionName.equals("+")) {
                    collectionStream = IteratorUtil.stream(this.database.listCollectionNames().iterator());
                } else {
                    collectionStream = Stream.of(collectionName);
                }
                return collectionStream.map(c -> this.database.getCollection(c)).flatMap(collection -> {
                    LOG.debug("WRITING: %s %s", collectionName, documentID);
                    if (obj.isNoObj()) {
                        // Delete document
                        LOG.trace("deleting document %s from collection %s", documentID, collectionName);
                        collection.deleteOne(Filters.eq(ID_FIELD, parseObjectId(documentID)));
                    } else if (fieldPath == null || fieldPath.isEmpty()) {
                        // Write entire document
                        final Document doc = new Document(this.serializer.writeRec(obj.asRec()).asDocument());
                        doc.put(ID_FIELD, parseObjectId(documentID));
                        LOG.trace("upserting document %s in collection %s", documentID, collectionName);
                        collection.replaceOne(Filters.eq(ID_FIELD, parseObjectId(documentID)), doc, new ReplaceOptions().upsert(true));
                    } else {
                        // Write to a specific field within a document
                        final String fieldPathStr = String.join(".", fieldPath);
                        LOG.trace("updating field %s in document %s", fieldPathStr, documentID);
                        collection.updateOne(
                                Filters.eq(ID_FIELD, parseObjectId(documentID)),
                                new Document("$set", new Document(fieldPathStr, this.serializer.write(obj)))
                        );
                    }
                    return Stream.of(obj);
                }).iterator().next();
            }
            return noobj();
        };
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            final fURI alignedPattern = Space.Helper.routeFromSpace(pattern,this.routes());

            // Determine collection name - either from schema or first segment
            final String collectionName;
            final String documentID;
            
            if (this.existingCollectionSchema != null && this.existingCollectionSchema.isCollectionPath(alignedPattern)) {
                // Known collection from schema
                collectionName = this.existingCollectionSchema.getCollectionName(alignedPattern);
                documentID = this.existingCollectionSchema.getDocumentId(alignedPattern);
            } else {
                // Fall back to segment parsing (for wildcard or non-schema mode)
                final List<String> segments = alignedPattern.segments();
                collectionName = segments.isEmpty() ? null : segments.getFirst();
                documentID = segments.size() > 1 ? segments.get(1) : null;
            }
            
            LOG.debug("searching: %s", collectionName);
            if (collectionName == null)
                return IteratorUtil.of();
            Stream<String> collectionStream;
            if (collectionName.equals("#") || collectionName.equals("+")) {
                collectionStream = IteratorUtil.stream(this.database.listCollectionNames().iterator());
            } else {
                collectionStream = Stream.of(collectionName);
            }
            
            final List<IdObj> allResults = new ArrayList<>();
            collectionStream.map(c -> this.database.getCollection(c)).forEach(collection -> {
                final String collName = collection.getNamespace().getCollectionName();
                LOG.debug("READING: %s %s", collName, documentID);
                if (documentID == null || documentID.equals("+") || documentID.equals("#")) {
                    // Pattern query - return all documents in collection
                    LOG.debug("reading all documents from collection %s", collName);
                    IteratorUtil.stream(collection.find()).forEach(doc -> {
                        final Object doc_id = doc.get(ID_FIELD);
                        final String idStr = doc_id instanceof ObjectId ? ((ObjectId) doc_id).toHexString() : doc_id.toString();
                        final fURI docVID = f(this.pattern.retractPattern().extend(collName).extend(idStr).toString());
                        final IdObj idObj = IdObj.of(this.serializer.read(doc.toBsonDocument()).selfVID(docVID));
                        allResults.add(idObj);
                        // Add poly unrolling for pattern queries
                        if (pattern.hasPattern() && idObj.obj().isPoly()) {
                            allResults.addAll(Space.Helper.unrollPoly(idObj.furi(), idObj.obj().as(), pattern.asNode()));
                        }
                    });
                } else {
                    // Specific document ID
                    LOG.debug("reading document %s from collection %s", documentID, collName);
                    final Document doc = collection.find(Filters.eq(ID_FIELD, parseObjectId(documentID))).first();

                    if (doc != null) {
                        final fURI docVID = f(this.pattern.retractPattern().extend(collName).extend(documentID).toString());
                        allResults.add(IdObj.of(docVID, this.serializer.readRec(doc.toBsonDocument()).selfVID(docVID)));
                    }
                }
            });
            return allResults.iterator();
        };
    }

    @Override
    public void close() {
        if (this.sjvm() != null) {
            this.sjvm().close();
            LOG.info("closed document store connection at {{b}}%s{{X}}", this.databaseName);
        }
    }

    /**
     * Generate a schema object from the discovered collection metadata.
     * Returns a rec with:
     * - pattern: base pattern for schema access
     * - collections: list of collection metadata with nested type/probability recs
     * - references: list of detected references
     */
    private Obj generateSchema() {
        this.existingCollectionSchema.initialize(this.database);
        final List<Obj> collections = this.existingCollectionSchema.getCollectionMetadata().stream()
                .map(collection -> (Obj) rec(
                        uri(NAME), str(collection.collectionName()),
                        uri(SCHEMA), buildNestedTypeRec(collection.fields()),
                        uri(PROBABILITY), buildNestedProbabilityRec(collection.fields())))
                .toList();

        final List<Obj> references = this.existingCollectionSchema.getCollectionMetadata().stream()
                .flatMap(collection -> collection.references().stream())
                .map(ref -> (Obj) rec(
                        uri(FROM), str(ref.fromCollection()),
                        uri(FIELD), str(ref.fromField()),
                        uri(TO), str(ref.toCollection()),
                        uri(TYPE), str(ref.type().name())))
                .toList();

        return rec(
                uri(NAME), str(this.databaseName),
                uri(COLLECTION), lst(collections),
                uri(REFERENCE), lst(references));
    }

    /**
     * Build a nested rec structure for types from flat field paths.
     * e.g., "location.address.city" → [location => [address => [city => str::T]]]
     */
    private Obj buildNestedTypeRec(final List<ExistingCollectionSchema.FieldMetadata> fields) {
        final Map<String, Object> tree = new LinkedHashMap<>();

        for (final ExistingCollectionSchema.FieldMetadata field : fields) {
            final String[] parts = field.path().split("\\.");
            insertIntoTree(tree, parts, 0, BsonTypeMapper.toMtronType(field.bsonType()));
        }

        return treeToTypeRec(tree);
    }

    /**
     * Build a nested rec structure for probabilities from flat field paths.
     * e.g., "location.address.city" with prob 0.95 → [location => [address => [city => <0.95>]]]
     */
    private Obj buildNestedProbabilityRec(final List<ExistingCollectionSchema.FieldMetadata> fields) {
        final Map<String, Object> tree = new LinkedHashMap<>();

        for (final ExistingCollectionSchema.FieldMetadata field : fields) {
            final String[] parts = field.path().split("\\.");
            insertIntoTree(tree, parts, 0, field.probability());
        }

        return treeToProbabilityRec(tree);
    }

    /**
     * Insert a value into a nested tree structure at the given path.
     */
    @SuppressWarnings("unchecked")
    private void insertIntoTree(final Map<String, Object> tree, final String[] parts,
                                final int index, final Object value) {
        if (index >= parts.length) return;

        final String key = parts[index];

        if (index == parts.length - 1) {
            // Leaf node - store the value
            tree.put(key, value);
        } else {
            // Intermediate node - get or create nested map
            final Object existing = tree.get(key);
            final Map<String, Object> nested;
            if (existing instanceof Map) {
                nested = (Map<String, Object>) existing;
            } else {
                nested = new LinkedHashMap<>();
                tree.put(key, nested);
            }
            insertIntoTree(nested, parts, index + 1, value);
        }
    }

    /**
     * Convert a tree structure to a nested rec with Type values.
     */
    @SuppressWarnings("unchecked")
    private Obj treeToTypeRec(final Map<String, Object> tree) {
        final Map<Obj, Obj> recMap = new LinkedHashMap<>();

        for (final Map.Entry<String, Object> entry : tree.entrySet()) {
            final Obj key = uri(entry.getKey());
            final Object value = entry.getValue();

            if (value instanceof Map) {
                // Nested structure
                recMap.put(key, treeToTypeRec((Map<String, Object>) value));
            } else if (value instanceof Type type) {
                // Leaf type value - Type implements Obj
                recMap.put(key, type);
            }
        }

        return rec(recMap);
    }

    /**
     * Convert a tree structure to a nested rec with probability (real) values.
     */
    @SuppressWarnings("unchecked")
    private Obj treeToProbabilityRec(final Map<String, Object> tree) {
        final Map<Obj, Obj> recMap = new LinkedHashMap<>();

        for (final Map.Entry<String, Object> entry : tree.entrySet()) {
            final Obj key = uri(entry.getKey());
            final Object value = entry.getValue();

            if (value instanceof Map) {
                // Nested structure
                recMap.put(key, treeToProbabilityRec((Map<String, Object>) value));
            } else if (value instanceof Double) {
                // Leaf probability value
                recMap.put(key, real((Double) value));
            }
        }

        return rec(recMap);
    }

    /**
     * Strip the space's route prefix from a fURI to get the relative path.
     * For example, if route maps mongo: to /mongo/ and fURI is /mongo/users/1, returns /users/1
     */
    private fURI stripPatternPrefix(final fURI furi) {
        // If there are routes, use the route target as the prefix to strip
        if (!this.routes().isEmpty()) {
            // Get the first route's target (e.g., /mongo/)
            final studio.phaseshift.metatron.isa.m.type.Uri routeTarget = this.routes().values().iterator().next();
            final fURI prefix = routeTarget.asUri().uriValue().asNode();
            // Only use the route if it's not empty (has actual path segments)
            if (!prefix.path().isEmpty() && prefix.path().stream().anyMatch(s -> !s.isEmpty())) {
                return furi.removePrefix(prefix);
            }
        }
        // Fallback to using the pattern if no routes or route is empty
        final fURI patternBase = this.pattern().asNode();
        return furi.removePrefix(patternBase);
    }

    /**
     * Parse a string as an ObjectId, handling both hex strings and other formats
     */
    private Object parseObjectId(final String id) {
        if (id == null) {
            return null;
        }
        // Try to parse as ObjectId (24-character hex string)
        if (id.matches("[0-9a-fA-F]{24}")) {
            return new ObjectId(id);
        }
        // Otherwise use as-is (could be a string ID or other format)
        return id;
    }
}
