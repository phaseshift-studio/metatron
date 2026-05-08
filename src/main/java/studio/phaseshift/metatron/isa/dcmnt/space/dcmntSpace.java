/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.dcmnt.space;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.*;
import org.bson.types.ObjectId;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.dcmnt.schema.domain.CollectionSchemaInstSet;
import studio.phaseshift.metatron.isa.dcmnt.schema.domain.ExistingCollectionSchema;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.COLLECTION_TID;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.DCMNT_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * dcmntSpace - A document database connector for Metatron supporting MongoDB-compatible databases
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
 * dcmntSpace space = dcmntSpace.of(
 *     rec(
 *         uri(PATTERN), uri("mongo:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("mongo:"), uri("/mongo/"))
 *     ).jvm(),
 *     f("/sys/space/mongo")
 * );
 * // After construction, space.at(ROOT) is a structured rec::T[?[{?}users=>users::T, ...]]
 * // and space.at(SCHEMA) is a CollectionSchemaInstSet auto-discovered from the live database.
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
public class dcmntSpace extends AbstractSpace<MongoClient> {
    private static final String NATIVE_CONNACK = "native/connack";
    public static final String ID_FIELD = "_id";
    /** 24-char hex ObjectId pattern, shared across serialiser and rewrite helpers */
    public static final String OBJECT_ID_REGEX = "[0-9a-fA-F]{24}";
    /**
     * Internal field used to wrap non-Rec values (Lst, primitives) in a BSON document
     */
    public static final String MTRON_VALUE_FIELD = "__mtron_v";

    protected MongoDatabase database;
    protected String databaseName;
    protected Supplier<ObjBSONSerializer> serializer;
    protected ExistingCollectionSchema existingCollectionSchema;
    protected dcmntSpaceSubQ dcmntSpaceSubQ;

    public static dcmntSpace of(final Map<Obj, Obj> config, final fURI vid) {
        final MongoClient client = MongoClients.create(config.get(uri(HOST)).uriValue().toString());
        return new dcmntSpace(client, config, DCMNT_SPACE_TID, vid);
    }

    protected dcmntSpace(final MongoClient sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        // Extract database name from connection string
        // Format: mongodb://host:port/database or mongodb://host:port/database?options
        final fURI connectionfURI = config.get(uri(HOST)).uriValue();
        this.databaseName = connectionfURI.segments(0, null);
        this.database = this.sjvm().getDatabase(this.databaseName);
        // Lazily resolve and configure the serializer on first use.
        // Uses a memoizing Supplier so the reference path builder is set once after the space
        // is fully constructed (when Router is ready to resolve !* auto-from instructions).
        // Each dcmntSpace needs its own serializer instance (can't mutate the shared SINGLE).
        this.serializer = new Supplier<>() {
            private ObjBSONSerializer instance;
            @Override public ObjBSONSerializer get() {
                if (instance == null) {
                    instance = dcmntSpace.this.jvm().containsKey(uri(SERIALIZER))
                            ? dcmntSpace.this.at(uri(SERIALIZER)).<ObjBSONSerializer>as()
                            : new ObjBSONSerializer();
                    instance.setLocalScheme(dcmntSpace.this.pattern().scheme());
                    instance.setReferencePathBuilder(refInfo -> {
                        final String collection = refInfo.collection();
                        if (collection.indexOf(':') >= 0) {
                            // Cross-space DBRef: $ref: "grph:V" → grph:V/1
                            return f(collection).extend(refInfo.id());
                        } else {
                            // Intra-space DBRef: $ref: "users" → mongo:users/507f...
                            return dcmntSpace.this.pattern().retractPattern()
                                    .extend(collection)
                                    .extend(refInfo.id());
                        }
                    });
                }
                return instance;
            }
        };
        final Rec conn = MObjFactory.of().toObj(this.sjvm()).asRec();
        LOG.debug("{{g}}connected{{X}} %s", conn);
        this.at(uri(NATIVE_CONNACK), conn, MUTABLE);
        // Ensure root type constraint is set if not already provided in config.
        // Open world assumption: rec::T is the floor — undeclared collections are allowed.
        // Uses jvm().putIfAbsent() (raw map access) to avoid triggering !* auto instructions
        // that at() would cause; preserves any user-provided root in the config.
        this.jvm().putIfAbsent(uri(ROOT), Rec.REC_TYPE);
        LOG.info("using document database {{b}}%s{{X}}", this.databaseName);

        // Initialize subscription query for change streams
        this.dcmntSpaceSubQ = new dcmntSpaceSubQ(this);
        this.at(uri(QPROC), this.at(uri(QPROC)).orElse(lst()).plus(lst(List.of(this.dcmntSpaceSubQ))), MUTABLE);
        LOG.debug("initialized {{g}}change stream subscription{{X}} support");

        // Schema discovery always runs at startup — root and schema are always populated.
        this.existingCollectionSchema = new ExistingCollectionSchema(this);
        this.existingCollectionSchema.initialize(this.database);

        // Build a CollectionSchemaInstSet in /m/ namespace (never routes back into dcmntSpace).
        // Type VIDs are under schemaVid/type/{collection} — within checkPattern() scope.
        // Follow grphSpace pattern: addSpace → setup() → store object directly.
        final fURI schemaVid = f("/m/dcmnt/space/schema/").extend(this.databaseName);
        final CollectionSchemaInstSet schemaInstset =
                this.existingCollectionSchema.generateSchemaInstset(schemaVid);
        Router.global().addSpace(schemaInstset);
        schemaInstset.setup();
        this.at(uri(SCHEMA), schemaInstset, MUTABLE);

        // Build a structured root type encoding the per-collection type map.
        // Each collection type is a rec::T refinement (space-agnostic, not tied to MongoDB).
        // Keys are {?}collectionName (optional = open world; unknown collections pass through).
        // Collection names come from the last segment of each type's VID (schemaVid/type/NAME).
        final LinkedHashMap<Obj, Obj> rootPredicate = new LinkedHashMap<>();
        for (final Type collectionType : schemaInstset.types()) {
            final String colName = collectionType.vid().segments().getLast();
            rootPredicate.put(uri(colName).maybe(), collectionType);
        }
        final Type rootType = Type.Builder.build()
                .tid(REC_TID)
                .isaPredicate(rec(rootPredicate))
                .create();
        this.jvm().put(uri(ROOT), rootType);

        LOG.info("initialized {{g}}collection schema{{X}} for %d collections",
                this.existingCollectionSchema.getCollectionNames().size());
    }

    public MongoDatabase getDatabase() {
        return this.database;
    }

    public ObjBSONSerializer getSerializer() {
        return this.serializer.get();
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
                    collectionStream = IteratorUtil.stream(this.getDatabase().listCollectionNames().iterator());
                } else {
                    collectionStream = Stream.of(collectionName);
                }
                return collectionStream.map(c -> this.getDatabase().getCollection(c)).flatMap(collection -> {
                    LOG.debug("WRITING: %s %s", collectionName, documentID);
                    if (fieldPath == null || fieldPath.isEmpty()) {
                        if (obj.isNoObj()) {
                            // Delete entire document
                            LOG.trace("deleting document %s from collection %s", documentID, collectionName);
                            collection.deleteOne(Filters.eq(ID_FIELD, parseObjectId(documentID)));
                        } else if (obj.isRec()) {
                            // Write entire document as BSON document fields
                            final Document doc = new Document(this.getSerializer().writeRec(obj.asRec()).asDocument());
                            doc.put(ID_FIELD, parseObjectId(documentID));
                            LOG.trace("upserting document %s in collection %s", documentID, collectionName);
                            collection.replaceOne(Filters.eq(ID_FIELD, parseObjectId(documentID)), doc, new ReplaceOptions().upsert(true));
                        } else {
                            // For non-Rec types (Lst, primitives), wrap in special __mtron_v field
                            final BsonDocument bsonDoc = new BsonDocument();
                            bsonDoc.put(ID_FIELD, toBsonId(documentID));
                            bsonDoc.put(MTRON_VALUE_FIELD, this.getSerializer().write(obj));
                            LOG.trace("upserting wrapped non-rec value for %s in collection %s", documentID, collectionName);
                            this.getDatabase().getCollection(collection.getNamespace().getCollectionName(), BsonDocument.class)
                                    .replaceOne(Filters.eq(ID_FIELD, parseObjectId(documentID)), bsonDoc, new ReplaceOptions().upsert(true));
                        }
                    } else {
                        // Write to a specific field within a document
                        final String fieldPathStr = String.join(".", fieldPath);
                        if (obj.isNoObj()) {
                            // $unset is the correct MongoDB operator for field deletion.
                            // The value ("") is ignored by MongoDB per spec.
                            LOG.trace("unsetting field %s in document %s", fieldPathStr, documentID);
                            collection.updateOne(
                                    Filters.eq(ID_FIELD, parseObjectId(documentID)),
                                    new Document("$unset", new Document(fieldPathStr, ""))
                            );
                        } else {
                            LOG.trace("updating field %s in document %s", fieldPathStr, documentID);
                            collection.updateOne(
                                    Filters.eq(ID_FIELD, parseObjectId(documentID)),
                                    new Document("$set", new Document(fieldPathStr, this.getSerializer().write(obj)))
                            );
                        }
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
            final fURI alignedPattern = Space.Helper.routeFromSpace(pattern, this.routes());
            final List<String> alignedSegments = alignedPattern.segments();

            // For 3+ segment paths the first two segments identify the document
            // (collection + docId) and the remainder is a sub-pattern within the
            // document.  Mirror memSpace.directReader by doing inline poly-parent
            // discovery: fetch the document and expand it with unrollPoly using the
            // NODE form of the pattern (trailing slash stripped) so that test() works.
            if (alignedSegments.size() > 2) {
                final String collectionName = alignedSegments.get(0);
                final String documentID = alignedSegments.get(1);
                // Only handle specific collection + document; wildcard collection/doc
                // patterns (e.g. +/+/field) are not yet supported here.
                if (!collectionName.equals("+") && !collectionName.equals("#") &&
                        !documentID.equals("+") && !documentID.equals("#")) {
                    final Document doc = this.getDatabase().getCollection(collectionName)
                            .find(Filters.eq(ID_FIELD, parseObjectId(documentID))).first();
                    if (doc != null) {
                        final fURI docVID = f(this.pattern.retractPattern()
                                .extend(collectionName).extend(documentID).toString());
                        final Obj docObj = processDocument(doc);
                        // Always use the node form so that test() can compare node to
                        // node; branch patterns (trailing slash) cause test() to fail.
                        final fURI nodePattern = pattern.asNode();
                        final List<IdObj> results = new ArrayList<>();
                        // Include the document itself if its URI matches the pattern
                        // (e.g. '#' recursive wildcard matches the containing node too).
                        if (docVID.test(nodePattern))
                            results.add(IdObj.of(docVID, docObj));
                        // Expand children if the document is a poly (Rec or Lst).
                        if (docObj.isPoly())
                            results.addAll(Space.Helper.unrollPoly(docVID, docObj.as(), nodePattern));
                        return results.iterator();
                    }
                }
                return IteratorUtil.of();
            }

            // For 2-segment BRANCH paths (trailing slash with no sub-pattern) return
            // empty so that Space.Helper.resolveRead can re-call directReader with
            // pattern.extend(WILD_ONE) and trigger the > 2 expansion above.
            if (alignedPattern.isBranch())
                return IteratorUtil.of();

            // Determine collection name - either from schema or first segment
            final String collectionName;
            final String documentID;

            if (this.existingCollectionSchema != null && this.existingCollectionSchema.isCollectionPath(alignedPattern)) {
                // Known collection from schema
                collectionName = this.existingCollectionSchema.getCollectionName(alignedPattern);
                documentID = this.existingCollectionSchema.getDocumentId(alignedPattern);
            } else {
                // Fall back to segment parsing (for wildcard or non-schema mode)
                collectionName = alignedSegments.isEmpty() ? null : alignedSegments.getFirst();
                documentID = alignedSegments.size() > 1 ? alignedSegments.get(1) : null;
            }

            LOG.debug("searching [collection: %s][document: %s]", collectionName, documentID);
            if (collectionName == null)
                return IteratorUtil.of();
            Stream<String> collectionStream;
            if (collectionName.equals("#") || collectionName.equals("+")) {
                collectionStream = IteratorUtil.stream(this.getDatabase().listCollectionNames().iterator());
            } else {
                collectionStream = Stream.of(collectionName);
            }
            if (null == documentID) {
                return collectionStream.map(c -> {
                    final fURI collectionVID = Space.Helper.routeToSpace(f(c), this.routes());
                    LOG.debug("collection lookup: %s", collectionVID);
                    return IdObj.of(collectionVID, uri(collectionVID, COLLECTION_TID, null).selfVID(collectionVID));
                }).iterator();
            }

            final List<IdObj> allResults = new ArrayList<>();
            collectionStream.map(c -> this.getDatabase().getCollection(c)).forEach(collection -> {
                final String collName = collection.getNamespace().getCollectionName();
                LOG.debug("READING: %s %s", collName, documentID);
                if (documentID.equals("+") || documentID.equals("#")) {
                    // Pattern query - return all documents in collection
                    LOG.debug("reading all documents from collection %s", collName);
                    IteratorUtil.stream(collection.find()).forEach(doc -> {
                        final Object doc_id = doc.get(ID_FIELD);
                        if (doc_id == null) {
                            LOG.warn("skipping document with null _id in collection %s", collName);
                            return;
                        }
                        final String idStr = doc_id instanceof ObjectId ? ((ObjectId) doc_id).toHexString() : doc_id.toString();
                        final fURI docVID = f(this.pattern.retractPattern().extend(collName).extend(idStr).toString());
                        final IdObj idObj = IdObj.of(docVID, processDocument(doc));
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
                        allResults.add(IdObj.of(docVID, processDocument(doc)));
                    }
                }
            });
            return allResults.iterator();
        };
    }

    @Override
    public void close() {
        // Close all change stream watchers first
        if (this.dcmntSpaceSubQ != null) {
            this.dcmntSpaceSubQ.closeAll();
        }
        if (this.sjvm() != null) {
            this.sjvm().close();
            LOG.info("closed document store connection at {{b}}%s{{X}}", this.databaseName);
        }
    }

    public Obj mql(final String collection, final Rec query) {
        try {
            return objs(IteratorUtil
                    .stream(this.getDatabase()
                            .getCollection(collection)
                            .find(this.getSerializer().writeRec(query)))
                    .map(doc -> this.getSerializer().read(doc.toBsonDocument())));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
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
     * Process a raw MongoDB document into a Metatron Obj.
     * <p>
     * Strips the {@code _id} field (already encoded in the URI path) then delegates to
     * {@link ObjBSONSerializer#readRec} which transparently handles the hidden
     * {@code __mtron_tid} field — restoring the nominal TID (e.g. {@code chicken::T})
     * that was written alongside the document fields for round-trip fidelity.
     */
    /**
     * Recursively convert {@code com.mongodb.DBRef} objects to embedded {@code {$ref, $id}}
     * Documents. In-memory MongoDB (bwaldvogel) deserialises the DBRef pattern into the
     * legacy DBRef class which lacks a BSON codec and causes {@code toBsonDocument()} to
     * throw. Real MongoDB drivers keep them as plain nested Documents, so this is a no-op
     * in production.
     */
    public static Object normalizeDBRefs(final Object value) {
        if (value instanceof com.mongodb.DBRef dbref) {
            return new Document()
                    .append("$ref", dbref.getCollectionName())
                    .append("$id", dbref.getId());
        }
        if (value instanceof Document doc) {
            final Document out = new Document();
            for (final String key : doc.keySet()) {
                out.put(key, normalizeDBRefs(doc.get(key)));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(dcmntSpace::normalizeDBRefs).toList();
        }
        return value;
    }

    private Obj processDocument(final Document doc) {
        final Document normalized = (Document) normalizeDBRefs(doc);
        final BsonDocument bsonDoc = normalized.toBsonDocument();
        if (bsonDoc.containsKey(MTRON_VALUE_FIELD)) {
            // Non-Rec value was wrapped in a special field — unwrap and return directly
            // (VID is tracked via IdObj.furi(); do NOT attach it to the value itself)
            return this.getSerializer().read(bsonDoc.get(MTRON_VALUE_FIELD));
        } else {
            // Regular document record — strip _id (already encoded in the URI)
            bsonDoc.remove(ID_FIELD);
            return this.getSerializer().readRec(bsonDoc);
        }
    }

    /**
     * Convert a document-ID string to the appropriate BsonValue for use in BsonDocument writes.
     */
    private BsonValue toBsonId(final String id) {
        if (id != null && id.matches(OBJECT_ID_REGEX))
            return new BsonObjectId(new ObjectId(id));
        return new BsonString(id != null ? id : "");
    }

    /**
     * Parse a string as an ObjectId, handling both hex strings and other formats
     */
    private Object parseObjectId(final String id) {
        if (id == null) {
            return null;
        }
        if (id.matches(OBJECT_ID_REGEX)) {
            return new ObjectId(id);
        }
        return id;
    }
}
