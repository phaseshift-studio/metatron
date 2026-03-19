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
import studio.phaseshift.metatron.isa.doc.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * docSpace - A document database connector for Metatron supporting MongoDB-compatible databases
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
 * // MongoDB
 * docSpace space = docSpace.of(
 *     rec(
 *         uri(PATTERN), uri("mongo:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("mongo:"), uri("/mongo/")),
 *         uri(COLLECTION), lst()  // Empty = discover all collections
 *     ).jvm(),
 *     f("/sys/space/mongo")
 * );
 *
 * // DocumentDB (open source, MIT licensed)
 * docSpace space = docSpace.of(
 *     rec(
 *         uri(PATTERN), uri("doc:#"),
 *         uri(HOST), uri("mongodb://localhost:27017/mydb"),
 *         uri(ROUTE), rec(uri("doc:"), uri("/doc/"))
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
public class docSpace extends AbstractSpace<MongoClient> {

    public static final String ID_FIELD = "_id";
    public static fURI DOC_SPACE_TID = docInstSet.DOC_ISA_TID.extend(SPACE).extend("doc");
    public static final Type DOC_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(DOC_SPACE_TID)
                    .isaPredicate(rec(
                            uri(PATTERN), URI_TYPE,
                            uri(HOST), URI_TYPE,
                            uri(SERIALIZER).maybe(), URI_TYPE,
                            uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                            uri(COLLECTION).maybe(), LST_TYPE))
                    .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(DOC_SPACE_TID),
                            lst(REC_TYPE),
                            (_, inst) -> docSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    protected MongoDatabase database;
    protected String databaseName;
    protected String schemaPrefix;
    protected ObjSerializer<BsonValue> serializer;

    /**
     * Create a new docSpace instance
     *
     * @param config Configuration map with PATTERN, HOST, ROUTE, and optional COLLECTION
     * @param vid    Virtual ID for this space
     * @return A new docSpace instance
     */
    public static docSpace of(final Map<Obj, Obj> config, final fURI vid) {
        try {
            final String connectionString = config.get(uri(HOST)).uriValue().toString();
            final MongoClient client = MongoClients.create(connectionString);
            return new docSpace(client, config, DOC_SPACE_TID, vid);
        } catch (final Exception ex) {
            throw MTronException.of(ex);
        }
    }

    protected docSpace(final MongoClient sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
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

        LOG.info("connected {{b}}%s{{X}}", connectionfURI);
        LOG.info("using database {{b}}%s{{X}}", this.databaseName);
        // Initialize schema prefix for schema access
        this.schemaPrefix = this.pattern.retractPattern().extend("schema").toString();
        // Log available collections
        final List<String> collections = StreamSupport.stream(
                        this.database.listCollectionNames().spliterator(), false)
                .collect(Collectors.toList());
        LOG.info("discovered {{g}}%d collections{{X}}: %s", collections.size(), collections);
    }


    @Override
    public Obj read(final fURI vid) {
        LOG.warn("reading %s => %s", vid, Space.Helper.routeFromSpace(vid, this.routes()));
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.warn("writing %s => %s", vid, vid);
        return studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(LOG, this, vid.basePath(), obj, this.directWriter(), this.directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(obj);
        });
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                // Pattern write - write to all matching fURIs
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                final String collectionName = Space.Helper.routeFromSpace(pattern, this.routes()).segments(0, null);
                if (collectionName == null)
                    return noobj();
                Stream<String> collectionStream;
                if (collectionName.equals("#") || collectionName.equals("+")) {
                    collectionStream = IteratorUtil.stream(this.database.listCollectionNames().iterator());
                } else {
                    collectionStream = Stream.of(collectionName);
                }
                return collectionStream.map(c -> this.database.getCollection(c)).flatMap(collection -> {
                    final String documentID = Space.Helper.routeFromSpace(pattern, this.routes()).segments(1, null);
                    LOG.debug("WRITING: %s %s", collectionName, documentID);
                    if (obj.isNoObj()) {
                        // Delete document
                        LOG.trace("deleting document %s from collection %s", documentID, collectionName);
                        collection.deleteOne(Filters.eq(ID_FIELD, parseObjectId(documentID)));
                    } else if (pattern.segmentLength() == 2) {
                        // Write entire document
                        final Document doc = new Document(this.serializer.writeRec(obj.asRec()).asDocument());
                        doc.put(ID_FIELD, parseObjectId(documentID));
                        LOG.trace("upserting document %s in collection %s", documentID, collectionName);
                        collection.replaceOne(Filters.eq(ID_FIELD, parseObjectId(documentID)), doc, new ReplaceOptions().upsert(true));
                    } else {
                        // Write to a specific field within a document
                        final String fieldPath = pattern.segments().subList(1, pattern.segmentLength()).stream().collect(Collectors.joining("."));
                        LOG.trace("updating field %s in document %s", fieldPath, documentID);
                        collection.updateOne(
                                Filters.eq(ID_FIELD, parseObjectId(documentID)),
                                new Document("$set", new Document(fieldPath, this.serializer.write(obj)))
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
            final String collectionName = Space.Helper.routeFromSpace(pattern, this.routes()).segments(0, null);
            if (collectionName == null)
                return IteratorUtil.of();
            Stream<String> collectionStream;
            if (collectionName.equals("#") || collectionName.equals("+")) {
                collectionStream = IteratorUtil.stream(this.database.listCollectionNames().iterator());
            } else {
                collectionStream = Stream.of(collectionName);
            }
            return collectionStream.map(c -> this.database.getCollection(c)).flatMap(collection -> {
                final String documentID = Space.Helper.routeFromSpace(pattern, this.routes()).segments(1, null);
                LOG.debug("READING: %s %s", collectionName, documentID);
                if (documentID == null || documentID.equals("+") || documentID.equals("#")) {
                    // Pattern query - return all documents in collection
                    LOG.debug("reading all documents from collection %s", collectionName);
                    final List<IdObj> results = new ArrayList<>();
                    for (final Document doc : collection.find()) {
                        final Object id = doc.get(ID_FIELD);
                        final String idStr = id instanceof ObjectId ? ((ObjectId) id).toHexString() : id.toString();
                        final fURI docUri = f(this.pattern.retractPattern().extend(collectionName).extend(idStr).toString());
                        results.add(IdObj.of(docUri, this.serializer.read(doc.toBsonDocument())));
                    }
                    return results.stream();
                } else {
                    // Specific document ID
                    LOG.debug("reading document %s from collection %s", documentID, collectionName);
                    final Document doc = collection.find(Filters.eq(ID_FIELD, parseObjectId(documentID))).first();

                    if (doc == null) {
                        return Stream.empty();
                    }

                    final fURI docUri = f(this.pattern.retractPattern().extend(collectionName).extend(documentID).toString());
                    return Stream.of(IdObj.of(docUri, this.serializer.readRec(doc.toBsonDocument())));
                }
            }).iterator();
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
     * Parse a string as an ObjectId, handling both hex strings and other formats
     */
    private Object parseObjectId(final String id) {
        // Try to parse as ObjectId (24-character hex string)
        if (id.matches("[0-9a-fA-F]{24}")) {
            return new ObjectId(id);
        }
        // Otherwise use as-is (could be a string ID or other format)
        return id;
    }
}
