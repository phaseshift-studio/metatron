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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.*;
import org.bson.types.ObjectId;
import org.javatuples.Pair;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.dcmnt.schema.domain.CollectionSchemaInstSet;
import studio.phaseshift.metatron.isa.dcmnt.schema.domain.ExistingCollectionSchema;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.InstSet;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.COLLECTION_TID;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.DCMNT_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.InstSet.instset0;
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
public class dcmntSpace extends AbstractSpace<MongoClient> implements SchemaSpace {
    private static final String NATIVE_CONNACK = "native/connack";
    public static final String ID_FIELD = "_id";
    /**
     * Pre-compiled 24-char hex ObjectId pattern, shared across serializer and rewrite helpers.
     */
    public static final Pattern OBJECT_ID_REGEX = Pattern.compile("[0-9a-fA-F]{24}");
    /**
     * Internal field used to wrap non-Rec values (Lst, primitives) in a BSON document.
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

            @Override
            public ObjBSONSerializer get() {
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
        final CollectionSchemaInstSet schemaInstset =
                this.existingCollectionSchema.generateSchemaInstset(this.vid().extend(f(SCHEMA).extend(INSTSET)));
        Router.global().addSpace(schemaInstset);
        schemaInstset.setup();
        this.at(uri(f(SCHEMA).extend(INSTSET)), schemaInstset, MUTABLE);

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

    public String getDatabaseName() {
        return this.databaseName;
    }

    // =======================================================================
    // Collection stream resolution
    // =======================================================================

    // =======================================================================
    // DataPath resolution
    // =======================================================================

    /**
     * Resolve a space-relative fURI into a {@link DataPath}.
     * Delegates to {@link ExistingCollectionSchema} when the collection is known;
     * otherwise falls back to structural decomposition via {@link DataPath#ofSpaceRelative}.
     */
    private DataPath resolveDataPath(final fURI relativePath) {
        if (this.existingCollectionSchema != null) {
            final DataPath dp = this.existingCollectionSchema.resolveDataPath(relativePath);
            if (dp != null)
                return dp;
        }
        return DataPath.ofSpaceRelative(relativePath, this.database.getName());
    }

    // =======================================================================

    /**
     * Expand a collection name (possibly wildcard {@code #} or {@code +}) into
     * a stream of {@link MongoCollection} handles.
     */
    private Stream<MongoCollection<Document>> resolveCollectionStream(final String collectionName) {
        if (collectionName.equals("#") || collectionName.equals("+")) {
            return IteratorUtil.stream(this.database.listCollectionNames().iterator())
                    .map(this.database::getCollection);
        }
        return Stream.of(this.database.getCollection(collectionName));
    }

    // =======================================================================
    // directWriter / directReader
    // =======================================================================

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                // Objs (coefficient collection): zip elements to matched keys by position
                // so that bulk >>= on a wildcard URI writes each merged element to its
                // corresponding key rather than writing the entire collection to every key
                if (obj.isObjs()) {
                    final List<Obj> elems = obj.asObjs().elements().toList();
                    final Iterator<IdObj> keys = this.directReader().apply(pattern);
                    int i = 0;
                    while (keys.hasNext() && i < elems.size())
                        this.write(keys.next().furi(), elems.get(i++));
                } else {
                    this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
                }
                return noobj();
            }

            // Route from the space's external address pattern to the internal relative path
            final fURI relativePath = Space.Helper.routeFromSpace(pattern, this.routes());

            // Decompose the relative path using DataPath
            final DataPath dp = this.resolveDataPath(relativePath);

            if (dp.collection() == null)
                return noobj();

            resolveCollectionStream(dp.collection()).findFirst().ifPresent(collection -> {
                LOG.debug("WRITING: %s %s", dp.collection(), dp.entry());
                if (!dp.hasField()) {
                    writeDocument(collection, dp.entry(), obj);
                } else {
                    writeField(collection, dp.entry(), dp.fieldPathStr(), obj);
                }
            });
            return obj;
        };
    }

    /** Write (or delete) an entire document in the given collection. */
    private void writeDocument(final MongoCollection<Document> collection, final String documentId, final Obj obj) {
        if (obj.isNoObj()) {
            LOG.trace("deleting document %s", documentId);
            collection.deleteOne(Filters.eq(ID_FIELD, parseObjectId(documentId)));
        } else if (obj.isRec()) {
            final Rec newRec = obj.asRec();
            // Decompose Rec writes into per-field $set/$unset to avoid full-doc replaceOne.
            // Reads the current document, diffs field-by-field, and issues targeted operations.
            writeRecDecomposed(collection, documentId, newRec);
        } else {
            final BsonDocument bsonDoc = new BsonDocument();
            bsonDoc.put(ID_FIELD, toBsonId(documentId));
            bsonDoc.put(MTRON_VALUE_FIELD, this.getSerializer().write(obj));
            LOG.trace("upserting wrapped non-rec value for %s", documentId);
            collection.withDocumentClass(BsonDocument.class)
                    .replaceOne(Filters.eq(ID_FIELD, parseObjectId(documentId)), bsonDoc,
                            new ReplaceOptions().upsert(true));
        }
    }

    /**
     * Decompose a document-level Rec write into targeted per-field {@code $set} and
     * {@code $unset} operations.  Reads the current document, diffs against the new
     * Rec at the top level, and emits only the changed fields to MongoDB.
     * <p>
     * For nested sub-documents and arrays the entire subtree is written via a single
     * {@code $set} on the top-level key — deep leaf-level decomposition is left for
     * future optimization.
     */
    private void writeRecDecomposed(final MongoCollection<Document> collection,
                                    final String documentId,
                                    final Rec newRec) {
        final Object parsedId = parseObjectId(documentId);
        final Document current = collection.find(Filters.eq(ID_FIELD, parsedId)).first();
        final Document newDoc = new Document(this.getSerializer().writeRec(newRec).asDocument());
        newDoc.put(ID_FIELD, parsedId);

        if (current == null) {
            LOG.trace("inserting new document %s", documentId);
            collection.insertOne(newDoc);
            return;
        }

        final Document setDoc = new Document();
        final Document unsetDoc = new Document();

        for (final String field : newDoc.keySet()) {
            if (ID_FIELD.equals(field)) continue;
            final Object newVal = newDoc.get(field);
            final Object oldVal = current.get(field);
            if (!Objects.equals(newVal, oldVal))
                setDoc.put(field, newVal);
        }
        for (final String field : current.keySet()) {
            if (ID_FIELD.equals(field)) continue;
            if (!newDoc.containsKey(field))
                unsetDoc.put(field, "");
        }

        LOG.debug("decomposed write on %s/%s: %d set(s), %d unset(s)",
                collection.getNamespace().getCollectionName(), documentId,
                setDoc.size(), unsetDoc.size());

        if (!setDoc.isEmpty())
            collection.updateOne(Filters.eq(ID_FIELD, parsedId), new Document("$set", setDoc));
        if (!unsetDoc.isEmpty())
            collection.updateOne(Filters.eq(ID_FIELD, parsedId), new Document("$unset", unsetDoc));
    }

    /** Write (or unset) a single field within a document. */
    private void writeField(final MongoCollection<Document> collection, final String documentId,
                            final String fieldPathStr, final Obj obj) {
        if (obj.isNoObj()) {
            LOG.trace("unsetting field %s in document %s", fieldPathStr, documentId);
            collection.updateOne(
                    Filters.eq(ID_FIELD, parseObjectId(documentId)),
                    new Document("$unset", new Document(fieldPathStr, "")));
        } else {
            LOG.trace("updating field %s in document %s", fieldPathStr, documentId);
            collection.updateOne(
                    Filters.eq(ID_FIELD, parseObjectId(documentId)),
                    new Document("$set", new Document(fieldPathStr, this.getSerializer().write(obj))));
        }
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            final fURI alignedPattern = Space.Helper.routeFromSpace(pattern, this.routes());
            final DataPath dp = this.resolveDataPath(alignedPattern);

            // --- Field-level access (3+ segments): collection/doc/field[/deeper] ---
            if (dp.hasField()) {
                final fURI nodePattern = pattern.asNode();
                if (!dp.collectionIsWildcard() && !dp.entryIsWildcard()) {
                    // Specific collection + specific document
                    // Fast path: concrete field path → push down to MongoDB projection
                    if (!dp.fieldIsWildcard() && !dp.extensionIsWildcard()) {
                        final IdObj result = readProjectedField(dp, nodePattern);
                        if (result != null)
                            return List.of(result).iterator();
                    }
                    // Slow path: wildcard in field/extension → fetch full document, traverse in Metatron
                    final Document doc = this.database.getCollection(dp.collection())
                            .find(Filters.eq(ID_FIELD, parseObjectId(dp.entry()))).first();
                    if (doc != null) {
                        final fURI docVID = f(this.pattern.retractPattern()
                                .extend(dp.collection()).extend(dp.entry()).toString());
                        final Obj docObj = processDocument(doc);
                        final List<IdObj> results = new ArrayList<>();
                        if (docVID.test(nodePattern))
                            results.add(IdObj.of(docVID, docObj));
                        else if (docObj.isPoly())
                            results.addAll(Space.Helper.unrollPoly(docVID, docObj.as(), nodePattern));
                        return results.iterator();
                    }
                } else if (!dp.collectionIsWildcard()) {
                    // Specific collection, wildcard document
                    return IteratorUtil.stream(this.database.getCollection(dp.collection()).find()).flatMap(x -> {
                        final String idStr = idToString(x);
                        final fURI docVID = this.pattern.retractPattern().extend(dp.collection()).extend(idStr);
                        final Obj docObj = processDocument(x);
                        final List<IdObj> results = new ArrayList<>();
                        if (docVID.test(nodePattern))
                            results.add(IdObj.of(docVID, docObj));
                        else if (docObj.isPoly())
                            results.addAll(Space.Helper.unrollPoly(docVID, docObj.as(), nodePattern));
                        return results.stream();
                    }).iterator();
                } else {
                    // Wildcard collection, wildcard document
                    return IteratorUtil.stream(this.database.listCollectionNames().iterator())
                            .map(this.database::getCollection)
                            .flatMap(collection -> IteratorUtil.stream(collection.find()).map(x -> Pair.with(collection, x)))
                            .flatMap(pair -> {
                                final String idStr = idToString(pair.getValue1());
                                final fURI docVID = this.pattern.retractPattern().extend(
                                        pair.getValue0().getNamespace().getCollectionName()).extend(idStr);
                                final Obj docObj = processDocument(pair.getValue1());
                                final List<IdObj> results = new ArrayList<>();
                                if (docVID.test(nodePattern))
                                    results.add(IdObj.of(docVID, docObj));
                                else if (docObj.isPoly())
                                    results.addAll(Space.Helper.unrollPoly(docVID, docObj.as(), nodePattern));
                                return results.stream();
                            }).iterator();
                }
                return IteratorUtil.of();
            }

            // --- Branch path guard: return empty so resolveRead retries with WILD_ONE ---
            if (alignedPattern.isBranch())
                return IteratorUtil.of();

            // --- Collection / document level ---
            if (!dp.hasCollection())
                return IteratorUtil.of();

            if (!dp.hasEntry()) {
                return resolveCollectionStream(dp.collection()).map(collection -> {
                            final fURI collectionVID = Space.Helper.routeToSpace(
                                    f(collection.getNamespace().getCollectionName()), this.routes());
                            LOG.debug("collection lookup: %s", collectionVID);
                            return IdObj.of(collectionVID, uri(collectionVID, COLLECTION_TID, null)
                                    .selfVID(collectionVID));
                        }).iterator();
            }

            final List<IdObj> allResults = new ArrayList<>();
            resolveCollectionStream(dp.collection()).forEach(collection -> {
                final String collName = collection.getNamespace().getCollectionName();
                LOG.debug("READING: %s %s", collName, dp.entry());
                if (dp.entryIsWildcard()) {
                    // Wildcard document: return all documents in the collection
                    LOG.debug("reading all documents from collection %s", collName);
                    IteratorUtil.stream(collection.find()).forEach(doc -> {
                        final Object doc_id = doc.get(ID_FIELD);
                        if (doc_id == null) {
                            LOG.warn("skipping document with null _id in collection %s", collName);
                            return;
                        }
                        final String idStr = doc_id instanceof ObjectId
                                ? ((ObjectId) doc_id).toHexString()
                                : doc_id.toString();
                        final fURI docVID = f(this.pattern.retractPattern()
                                .extend(collName).extend(idStr).toString());
                        final IdObj idObj = IdObj.of(docVID, processDocument(doc));
                        allResults.add(idObj);
                        if (pattern.hasPattern() && idObj.obj().isPoly()) {
                            allResults.addAll(Space.Helper.unrollPoly(
                                    idObj.furi(), idObj.obj().as(), pattern.asNode()));
                        }
                    });
                } else {
                    LOG.debug("reading document %s from collection %s", dp.entry(), collName);
                    final Document doc = collection.find(
                            Filters.eq(ID_FIELD, parseObjectId(dp.entry()))).first();
                    if (doc != null) {
                        final fURI docVID = dp.vid(this.pattern);
                        allResults.add(IdObj.of(docVID, processDocument(doc)));
                    }
                }
            });
            return allResults.iterator();
        };
    }

    // =======================================================================
    // Lifecycle
    // =======================================================================

    @Override
    public void close() {
        try {
            // Close all change stream watchers first
            if (this.dcmntSpaceSubQ != null) {
                try {
                    this.dcmntSpaceSubQ.closeAll();
                } catch (final Exception e) {
                    LOG.error("failed to close change stream watchers", e);
                }
            }
            if (this.sjvm() != null) {
                this.sjvm().close();
                LOG.info("closed document store connection at {{b}}%s{{X}}", this.databaseName);
            }
            SchemaSpace.super.close();
        } finally {
            super.close();
        }
    }

    // =======================================================================
    // Query support
    // =======================================================================

    public Obj mql(final String collection, final Rec query) {
        try {
            return objs(IteratorUtil
                    .stream(this.database
                            .getCollection(collection)
                            .find(this.getSerializer().writeRec(query)))
                    .map(doc -> this.getSerializer().read(doc.toBsonDocument())));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    // =======================================================================
    // Static helpers
    // =======================================================================

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

    /**
     * Process a raw MongoDB document into a Metatron Obj.
     * <p>
     * Strips the {@code _id} field (already encoded in the URI path) then delegates to
     * {@link ObjBSONSerializer#readRec} which transparently handles the hidden
     * {@code __mtron_tid} field — restoring the nominal TID (e.g. {@code chicken::T})
     * that was written alongside the document fields for round-trip fidelity.
     */
    protected Obj processDocument(final Document doc) {
        final Document normalized = (Document) normalizeDBRefs(doc);
        final BsonDocument bsonDoc = normalized.toBsonDocument();
        if (bsonDoc.containsKey(MTRON_VALUE_FIELD)) {
            // Non-Rec value was wrapped in a special field — unwrap and return directly
            // (VID is tracked via IdObj.furi(); do NOT attach it to the value itself)
            return this.getSerializer().read(bsonDoc.get(MTRON_VALUE_FIELD));
        }
        // Regular document record — strip _id (already encoded in the URI)
        bsonDoc.remove(ID_FIELD);
        return this.getSerializer().readRec(bsonDoc);
    }

    /**
     * Fast-path field read: uses MongoDB projection to fetch only the target field,
     * avoiding full-document deserialization and Metatron-level Rec traversal.
     *
     * @return an {@link IdObj} with vid = {@code nodePattern} and obj = the leaf field value,
     *         or {@code null} if the document or field does not exist
     */
    private IdObj readProjectedField(final DataPath dp, final fURI nodePattern) {
        // Project only the top-level field; walk the full path locally (handles array indices)
        final Document doc = this.database.getCollection(dp.collection())
                .find(Filters.eq(ID_FIELD, parseObjectId(dp.entry())))
                .projection(new Document(dp.field(), 1))
                .first();
        if (doc == null)
            return null;

        // Walk the projected document to the leaf value, crossing array boundaries
        Object current = doc;
        for (final String segment : dp.fieldPathStr().split("\\.")) {
            if (current instanceof Document d) {
                current = d.get(segment);
            } else if (current instanceof List<?> l) {
                try {
                    final int idx = Integer.parseInt(segment);
                    current = idx >= 0 && idx < l.size() ? l.get(idx) : null;
                } catch (final NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (current == null) return null;
        }

        final BsonValue bson = toBsonValue(current);
        final Obj fieldValue = this.getSerializer().read(bson);
        return IdObj.of(nodePattern, fieldValue);
    }

    /** Convert a Java value from a BSON {@link Document} to a {@link BsonValue} for the serializer. */
    private static BsonValue toBsonValue(final Object value) {
        if (value == null) return BsonNull.VALUE;
        if (value instanceof String s) return new BsonString(s);
        if (value instanceof Integer i) return new BsonInt32(i);
        if (value instanceof Long l) return new BsonInt64(l);
        if (value instanceof Double d) return new BsonDouble(d);
        if (value instanceof Float f) return new BsonDouble(f.doubleValue());
        if (value instanceof Boolean b) return new BsonBoolean(b);
        if (value instanceof Document d) return d.toBsonDocument();
        if (value instanceof List<?> l) {
            final BsonArray arr = new BsonArray();
            for (final Object item : l) arr.add(toBsonValue(item));
            return arr;
        }
        if (value instanceof BsonValue b) return b;
        if (value instanceof ObjectId o) return new BsonObjectId(o);
        if (value instanceof org.bson.types.Binary bin) return new BsonBinary(bin.getType(), bin.getData());
        if (value instanceof java.util.Date dt) return new BsonDateTime(dt.getTime());
        return BsonNull.VALUE;
    }

    // =======================================================================
    // ID conversion helpers
    // =======================================================================

    /**
     * Convert a document-ID string to the appropriate BsonValue for use in BsonDocument writes.
     */
    private static BsonValue toBsonId(final String id) {
        if (id != null && OBJECT_ID_REGEX.matcher(id).matches())
            return new BsonObjectId(new ObjectId(id));
        return new BsonString(id != null ? id : "");
    }

    /**
     * Parse a string as an ObjectId when it matches the 24-char hex pattern,
     * otherwise return the string as-is.
     */
    private static Object parseObjectId(final String id) {
        if (id == null)
            return null;
        if (OBJECT_ID_REGEX.matcher(id).matches())
            return new ObjectId(id);
        return id;
    }

    /**
     * Safely extract a document's {@code _id} value as a string,
     * supporting both ObjectId and plain String IDs.
     */
    private static String idToString(final Document doc) {
        final Object id = doc.get(ID_FIELD);
        return id instanceof ObjectId ? ((ObjectId) id).toHexString() : id != null ? id.toString() : "";
    }
}
