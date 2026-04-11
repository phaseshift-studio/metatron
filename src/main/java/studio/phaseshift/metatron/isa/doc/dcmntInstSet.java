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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * dcmntInstSet - Instruction set for document database operations
 *
 * <p>Defines types and instructions for working with MongoDB/DocumentDB through Metatron.
 *
 * <h2>Types</h2>
 * <ul>
 *   <li><b>DOC_TYPE</b> - A document (record) from a collection</li>
 *   <li><b>COLLECTION_TYPE</b> - A collection of documents</li>
 *   <li><b>DOC_SPACE_TYPE</b> - The document database space</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/dcmnt")
public class dcmntInstSet extends AbstractInstSet {

    public static final fURI DCMNT_ISA_TID = M_ISA_TID.extend("dcmnt");
    public static final fURI DCMNT_ISA_INST_TID = DCMNT_ISA_TID.extend("inst");
    public static final fURI DCMNT_ISA_REWRITE_TID = DCMNT_ISA_INST_TID.extend("rewrite");
    public static final fURI DOCUMENT_TID = DCMNT_ISA_TID.extend("document");
    public static final fURI COLLECTION_TID = DCMNT_ISA_TID.extend("collection");
    public static final fURI ID_FIELD = f("_id");
    public static fURI DOCDB_SPACE_TID = DCMNT_ISA_TID.extend(SPACE).extend("docdb");


    public static final Type DOCUMENT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(DOCUMENT_TID)
            .isaPredicate(rec(
                    uri(ID_FIELD), URI_TYPE,
                    URI_TYPE, T(ALL)))
            .create();

    public static final Type COLLECTION_TYPE = Type.Builder.build()
            .tid(OBJS_TID.maybeSome())
            .vid(COLLECTION_TID)
            .predicate(isa_(T(DOCUMENT_TID.maybeSome())).tryToInst())
            .create();

    public dcmntInstSet() {
        super(mutableMap(uri(PATTERN), uri(DCMNT_ISA_TID.extend(ALL))), INSTSET_TID, DCMNT_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(DCMNT_ISA_TID.extend(ALL)),
                uri(CONSTQ), lst(ObjSimpleJSONSerializer.single(), uri(ID_FIELD, URI_TID, DCMNT_ISA_TID.extend(ID_FIELD))),
                uri(TYPE), lst(
                        docWrap(DOCUMENT_TYPE, "a document (record) from a collection"),
                        COLLECTION_TYPE,
                        docWrap(Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(DOCDB_SPACE_TID)
                                        .isaPredicate(rec(
                                                uri(PATTERN), URI_TYPE,
                                                uri(HOST), URI_TYPE,
                                                uri(SERIALIZER).maybe(), URI_TYPE,
                                                uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                                                uri(COLLECTION).maybe(), LST_TYPE,
                                                uri(SCHEMA).maybe(), T(ALL)
                                        ))
                                        .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(DOCDB_SPACE_TID),
                                                lst(REC_TYPE),
                                                (lhs, inst) -> docdbSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create().asType(),
                                "a rec describing a document database connection",
                                "a rec with fields for configuring a document database connection",
                                Map.of(
                                        uri(PATTERN), "the pattern for accessing documents",
                                        uri(HOST), "connection uri (mongodb://host:port/database?options)",
                                        uri(SERIALIZER).maybe(), "the serializer for BSON documents",
                                        uri(ROUTE), "the route for accessing documents",
                                        uri(COLLECTION).maybe(), "schema discovery on collections (empty discovers all collections)"
                                ),
                                "an interface to document-oriented databases",
                                """
                                docdb::[pattern     => moviedb:#,
                                       host        => <mongodb://localhost:27017/movies>,
                                       serializer  => !*</m/mach/io/serializer/bson>,
                                       collection  => [,],
                                       route       => [moviedb:=>/moviedb/]]@/usr/entertainment/moviedb;
                                """,
                                """
                                *moviedb:schema
                                *moviedb:
                                """)),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(DOCUMENT_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList()))),
                uri(REWRITE), lst(
                        // Optimize: *collection.count() → MongoDB countDocuments()
                        CommonRewrites.countRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_count"),
                                (space, furi) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    return collection.countDocuments();
                                }
                        ),

                        // Optimize: *collection.sum() → MongoDB aggregation $sum
                        CommonRewrites.sumRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_sum"),
                                (space, furi) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    // MongoDB aggregation pipeline: [{$group: {_id: null, total: {$sum: 1}}}]
                                    final Document result = collection.aggregate(Arrays.asList(
                                            new Document("$group", new Document("_id", null)
                                                    .append("total", new Document("$sum", 1)))
                                    )).first();
                                    if (result != null && result.containsKey("total")) {
                                        return result.get("total", Number.class);
                                    }
                                    return 0;
                                }
                        ),

                        // Optimize: *collection.mean() → MongoDB aggregation $avg
                        CommonRewrites.meanRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_mean"),
                                (space, furi) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    // MongoDB aggregation pipeline: [{$group: {_id: null, average: {$avg: 1}}}]
                                    final Document result = collection.aggregate(Arrays.asList(
                                            new Document("$group", new Document("_id", null)
                                                    .append("average", new Document("$avg", 1)))
                                    )).first();
                                    if (result != null && result.containsKey("average")) {
                                        return result.getDouble("average");
                                    }
                                    return 0.0;
                                }
                        ),

                        // Optimize: *collection.take(n) → MongoDB find().limit(n)
                        CommonRewrites.limitRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_limit"),
                                (space, furi, limit) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    final fURI baseUri = furi.retract(1);
                                    return readDocumentsAsObjs(collection, baseUri, space, (int) limit);
                                }
                        ),

                        // Optimize: *collection.has() → MongoDB countDocuments() > 0
                        CommonRewrites.hasRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_has"),
                                (space, furi) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    // Use limit(1) for efficiency - we only need to know if at least one exists
                                    return collection.find().limit(1).first() != null;
                                }
                        ),

                        // Optimize: *collection.where([field=>value]) → MongoDB find(filter)
                        CommonRewrites.whereRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_where"),
                                (space, furi, predicateStr) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    final fURI baseUri = furi.retract(1);
                                    final Bson filter = parseMongoFilter(predicateStr);
                                    if (filter == null) {
                                        throw new IllegalArgumentException("Could not parse filter: " + predicateStr);
                                    }
                                    return readFilteredDocumentsAsObjs(collection, baseUri, space, filter);
                                }
                        ),

                        // Optimize: mql_where.count() → MongoDB countDocuments(filter)
                        CommonRewrites.whereCountRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_where"),
                                DCMNT_ISA_REWRITE_TID.extend("mql_where_count"),
                                (space, furi, predicateStr) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    final Bson filter = parseMongoFilter(predicateStr);
                                    if (filter == null) {
                                        throw new IllegalArgumentException("Could not parse filter: " + predicateStr);
                                    }
                                    return collection.countDocuments(filter);
                                }
                        ),

                        // Optimize: from(collection/+).>>{field1,field2} → MongoDB projection
                        CommonRewrites.selectRewrite(
                                docdbSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_select"),
                                (space, furi, columns) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);

                                    // Build MongoDB projection: {field1: 1, field2: 1, _id: 0}
                                    final Document projection = new Document();
                                    for (final String col : columns) {
                                        projection.append(col, 1);
                                    }
                                    // Exclude _id unless explicitly requested
                                    if (!columns.contains("_id")) {
                                        projection.append("_id", 0);
                                    }

                                    final List<Obj> results = new ArrayList<>();
                                    try (var cursor = collection.find().projection(projection).iterator()) {
                                        while (cursor.hasNext()) {
                                            final Document doc = cursor.next();
                                            // Convert the projected document using the space's serializer
                                            final Obj row = space.serializer.read(doc.toBsonDocument());
                                            results.add(row);
                                        }
                                    }
                                    return studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs(results.iterator());
                                }
                        )
                )));
        docWrap(this,
                """
                collections of nested documents accessible via the metatron
                <br>
                Supports:
                <ol>
                    <li> MongoDB (Community or Enterprise)
                    <li> DocumentDB (MIT licensed, PostgreSQL-based, open source)
                    <li> Amazon DocumentDB (MongoDB-compatible)
                    <li> Azure Cosmos DB for MongoDB
                    <li> Any database implementing the MongoDB wire protocol
                </ol>
                """,
                "mongodb:people/6/address>>=[street=>Elm Street,city=>Gotham,zipcode=>90210]");
        super.setup();

    }

    // ==================== Helper Methods for Rewrites ====================

    /**
     * Read documents from a collection with an optional limit, returning as Objs.
     */
    private static Obj readDocumentsAsObjs(final MongoCollection<Document> collection,
                                           final fURI baseUri,
                                           final docdbSpace space,
                                           final int limit) {
        final List<Obj> results = new ArrayList<>();
        final var cursor = collection.find().limit(limit).iterator();
        while (cursor.hasNext()) {
            final Document doc = cursor.next();
            final Object docId = doc.get("_id");
            final String idStr = docId instanceof org.bson.types.ObjectId
                    ? ((org.bson.types.ObjectId) docId).toHexString()
                    : docId.toString();
            final fURI docUri = baseUri.extend(collection.getNamespace().getCollectionName()).extend(idStr);
            results.add(space.serializer.read(doc.toBsonDocument()).selfVID(docUri));
        }
        return studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs(results.iterator());
    }

    /**
     * Read filtered documents from a collection, returning as Objs.
     */
    private static Obj readFilteredDocumentsAsObjs(final MongoCollection<Document> collection,
                                                   final fURI baseUri,
                                                   final docdbSpace space,
                                                   final Bson filter) {
        final List<Obj> results = new ArrayList<>();
        final var cursor = collection.find(filter).iterator();
        while (cursor.hasNext()) {
            final Document doc = cursor.next();
            final Object docId = doc.get("_id");
            final String idStr = docId instanceof org.bson.types.ObjectId
                    ? ((org.bson.types.ObjectId) docId).toHexString()
                    : docId.toString();
            final fURI docUri = baseUri.extend(collection.getNamespace().getCollectionName()).extend(idStr);
            results.add(space.serializer.read(doc.toBsonDocument()).selfVID(docUri));
        }
        return studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs(results.iterator());
    }

    /**
     * Parse a SQL-like WHERE clause string into a MongoDB Bson filter.
     * Handles the same format as CommonRewrites.WhereRewriteBuilder produces:
     * - "field = value"
     * - "field > value"
     * - "field < value"
     * - "field >= value"
     * - "field <= value"
     * - "field <> value"
     * - "field IS NOT NULL"
     * - Multiple conditions joined by " AND "
     *
     * @return Bson filter, or null if parsing fails
     */
    private static Bson parseMongoFilter(final String whereClause) {
        if (whereClause == null || whereClause.isBlank()) {
            return null;
        }

        // Split by AND (case insensitive)
        final String[] conditions = whereClause.split("\\s+AND\\s+", -1);
        final List<Bson> filters = new ArrayList<>();

        for (final String condition : conditions) {
            final Bson filter = parseSingleCondition(condition.trim());
            if (filter == null) {
                return null; // If any condition fails, fail the whole parse
            }
            filters.add(filter);
        }

        if (filters.isEmpty()) {
            return null;
        } else if (filters.size() == 1) {
            return filters.getFirst();
        } else {
            return Filters.and(filters);
        }
    }

    /**
     * Parse a single SQL condition into a MongoDB Bson filter.
     */
    private static Bson parseSingleCondition(final String condition) {
        // Handle IS NOT NULL
        if (condition.toUpperCase().endsWith(" IS NOT NULL")) {
            final String field = condition.substring(0, condition.length() - " IS NOT NULL".length()).trim();
            return Filters.exists(field, true);
        }

        // Handle comparison operators: >=, <=, <>, >, <, =
        final String[] operators = {">=", "<=", "<>", ">", "<", "="};
        for (final String op : operators) {
            final int idx = condition.indexOf(op);
            if (idx > 0) {
                final String field = condition.substring(0, idx).trim();
                final String valueStr = condition.substring(idx + op.length()).trim();
                final Object value = parseValue(valueStr);

                return switch (op) {
                    case "=" -> Filters.eq(field, value);
                    case ">" -> Filters.gt(field, value);
                    case "<" -> Filters.lt(field, value);
                    case ">=" -> Filters.gte(field, value);
                    case "<=" -> Filters.lte(field, value);
                    case "<>" -> Filters.ne(field, value);
                    default -> null;
                };
            }
        }

        return null;
    }

    /**
     * Parse a value string into the appropriate Java type.
     */
    private static Object parseValue(final String valueStr) {
        // Handle quoted strings
        if (valueStr.startsWith("'") && valueStr.endsWith("'")) {
            return valueStr.substring(1, valueStr.length() - 1).replace("''", "'");
        }

        // Handle booleans
        if ("TRUE".equalsIgnoreCase(valueStr)) {
            return true;
        }
        if ("FALSE".equalsIgnoreCase(valueStr)) {
            return false;
        }

        // Handle numbers
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Long.parseLong(valueStr);
            }
        } catch (final NumberFormatException e) {
            // Fall back to string
            return valueStr;
        }
    }
}
