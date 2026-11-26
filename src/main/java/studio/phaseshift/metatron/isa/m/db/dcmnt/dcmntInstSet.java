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

package studio.phaseshift.metatron.isa.m.db.dcmnt;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import studio.phaseshift.metatron.algebra.rewrite.db.CommonRewrites;
import studio.phaseshift.metatron.algebra.rewrite.db.DBRewriteBuilder;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.m.db.dcmnt.schema.MQLRewriteUtils;
import studio.phaseshift.metatron.isa.m.db.dcmnt.space.dcmntSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.isa.m.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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
@InstSet.JREService(vid = "/m/db/dcmnt")
public class dcmntInstSet extends AbstractInstSet {

    public static final fURI DCMNT_ISA_TID = DB_ISA_TID.extend("dcmnt");
    public static final fURI DCMNT_ISA_INST_TID = DCMNT_ISA_TID.extend(INST);
    public static final fURI DCMNT_ISA_REWRITE_TID = DCMNT_ISA_INST_TID.extend(REWRITE);
    public static final fURI MQL_INST_TID = DCMNT_ISA_INST_TID.extend(MQL);
    public static final fURI COLLECTION_TID = DCMNT_ISA_TID.extend(COLLECTION);
    public static final String ID_FIELD_STRING = "_id";
    public static final fURI ID_FIELD = f(ID_FIELD_STRING);
    public static fURI DCMNT_SPACE_TID = DCMNT_ISA_TID.extend(SPACE).extend("dcmntspace");
    public static Type DCMNT_SPACE_TYPE;

    public static final Type COLLECTION_TYPE = TypeBuilder.build()
            .tid(URI_TID)
            .vid(COLLECTION_TID)
            //.isaPredicate(rec(uri(NAME),URI_TYPE))
            .create();

    public dcmntInstSet() {
        super(mutableMap(uri(PATTERN), uri(DCMNT_ISA_TID.extend(ALL))), INSTSET_TID, DCMNT_ISA_TID);
    }

    @Override
    public void setup() {
        // THE SUM OF THE FIELD THE PATH NAMES, for mql_sum and mql_sum_column alike, its extension as Mongo's dotted path
        // (summablePath declines a path with no field): "$" + field alone summed value for */+/value/x. A $group over no
        // document is the sum's seed, 0
        final BiFunction<dcmntSpace, DataPath, Number> fieldSum = (space, dp) -> {
            final Number sum = fieldAggregate(space, dp, "$sum");
            return null == sum ? 0 : sum;
        };
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(DCMNT_ISA_TID.extend(ALL)),
                uri(CONSTQ), lst(ObjJSONSerializer.single(), uri(ID_FIELD, URI_TID, DCMNT_ISA_TID.extend(ID_FIELD))),
                uri(TYPE), lst(
                        COLLECTION_TYPE,
                        DCMNT_SPACE_TYPE = TypeBuilder.build()
                                .tid(SPACE_TID)
                                .vid(DCMNT_SPACE_TID)
                                .isaPredicate(rec(
                                        uri(PATTERN), URI_TYPE,
                                        uri(HOST), URI_TYPE,
                                        uri(SERIALIZER).maybe(), URI_TYPE,
                                        uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                                        uri(ROOT).maybe(), ALL_TYPE,
                                        uri(SCHEMA).maybe(), T(INSTSET_TID)
                                ))
                                .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(DCMNT_SPACE_TID),
                                        lst(REC_TYPE),
                                        (lhs, inst) -> dcmntSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid())))
                                .refinement("a rec describing a document database connection", "a rec with fields for configuring a document database connection", Map.of(
                                        uri(PATTERN), "the pattern for accessing documents",
                                        uri(HOST), "connection uri (mongodb://host:port/database?options)",
                                        uri(SERIALIZER).maybe(), "the serializer for BSON documents",
                                        uri(ROUTE), "the route for accessing documents",
                                        uri(ROOT).maybe(), "the root type constraint — writes at document root must satisfy this type; defaults to rec::T",
                                        uri(SCHEMA).maybe(), "an instset of collection types auto-discovered from the live database"
                                ))
                                .desc("an interface to document-oriented databases (MongoDB wire protocol)")
                                .example("""
                                        dcmntspace::[pattern => moviedb:#,
                                                     host    => <mongodb://localhost:27017/movies>,
                                                     root    => rec::T,
                                                     route   => [moviedb:=>/moviedb/]]@/usr/entertainment/moviedb;
                                        """)
                                .example("""
                                        *moviedb:schema
                                        *moviedb:movie/
                                        """)
                                .create().asType()),
                uri(INST), lst(
                        instC(MQL_INST_TID.dom(DCMNT_SPACE_TID).rng(REC_TID.maybeSome()), lst(URI_TYPE, REC_TYPE), (lhs, inst) -> lhs.<dcmntSpace>as().mql(inst.arg(0).uriValue().toString(), inst.arg(1).as())),
                        docWrap(instC(MQL_INST_TID.dom(COLLECTION_TID).rng(REC_TID.maybeSome()), lst(REC_TYPE), (lhs, inst) -> Machine.current().memory().<dcmntSpace>getSpaceFor(lhs.uriValue()).mql(lhs.uriValue().name(), inst.arg(0).as())),
                                "a document collection",
                                "the result of the mql query",
                                Map.of(jnt(0), "an mql query represented as a rec"),
                                "the provided rec is translated to an bson query document and evaluated against the domain collection",
                                """
                                        *moviedb:movies.mql([title => 'The Matrix'])  [-- movies.find({"title":"The Matrix"}) --]
                                        """)),
                uri(REWRITE), lst(
                        // Optimize: *collection.count() → MongoDB countDocuments()
                        CommonRewrites.countRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_count"),
                                (space, dp) -> {
                                    final String collectionName = dp.collection();
                                    final MongoCollection<Document> collection = space.getDatabase().getCollection(collectionName);
                                    return collection.countDocuments();
                                },
                                dcmntInstSet::readsDocuments
                        ),

                        // Optimize: *collection.sum() → MongoDB aggregation $sum
                        CommonRewrites.sumRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_sum"),
                                fieldSum,
                                dcmntInstSet::summablePath
                        ),

                        // Optimize: *collection.>>field.sum() → MongoDB aggregation $sum on that field
                        CommonRewrites.sumColumnRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_sum_column"),
                                fieldSum,
                                dcmntInstSet::summablePath
                        ),

                        // Optimize: *collection.mean() → MongoDB aggregation $avg
                        CommonRewrites.meanRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_mean"),
                                (space, dp) -> {
                                    // THE MEAN OF THE FIELD THE PATH NAMES (2026-11-25): {$avg: 1} averaged the literal 1, so
                                    // *mongo:rewrite_test/+/value.mean() answered 1.0 for 5.5. A field no document holds, and a
                                    // collection with no document, give $avg null: the mean of nothing, noobj, as the walk answers.
                                    // MongoDB averages decimals to a Decimal128, which getDouble refused; mongo-java-server
                                    // averages them to a double, so no row of the tests can pin it
                                    final Number average = fieldAggregate(space, dp, "$avg");
                                    return null == average ? null : average.doubleValue();
                                },
                                dcmntInstSet::summablePath
                        ),

                        // Optimize: *collection.take(n) → MongoDB find().limit(n)
                        CommonRewrites.limitRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_limit"),
                                // MongoDB's find().limit(0) means "no limit" (returns everything), unlike SQL LIMIT 0
                                // (returns nothing), so the zero case is guarded
                                (space, dp, limit) -> 0 == limit ? noobj() : readDocuments(space, dp.collection(),
                                        space.getDatabase().getCollection(dp.collection()).find().limit((int) limit)),
                                dcmntInstSet::readsDocuments
                        ),

                        // Optimize: *collection.isa([field=>value]) → MongoDB find(filter)
                        CommonRewrites.whereRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_where"),
                                (space, dp, predicateStr) -> {
                                    final Bson filter = parseMongoFilter(predicateStr);
                                    if (filter == null) {
                                        throw new IllegalArgumentException("Could not parse filter: " + predicateStr);
                                    }
                                    return readDocuments(space, dp.collection(),
                                            space.getDatabase().getCollection(dp.collection()).find(filter));
                                },
                                MQLRewriteUtils.PREDICATE_JOINER,
                                MQLRewriteUtils.CONDITION_FORMATTER,
                                dcmntInstSet::readsDocuments
                        ),

                        // Optimize: mql_where.count() → MongoDB countDocuments(filter)
                        CommonRewrites.whereCountRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_where"),
                                DCMNT_ISA_REWRITE_TID.extend("mql_where_count"),
                                (space, dp, predicateStr) -> {
                                    final String collectionName = dp.collection();
                                    final MongoCollection<Document> collection = space.getDatabase().getCollection(collectionName);
                                    final Bson filter = parseMongoFilter(predicateStr);
                                    if (filter == null) {
                                        throw new IllegalArgumentException("Could not parse filter: " + predicateStr);
                                    }
                                    return collection.countDocuments(filter);
                                }
                        ),

                        // Optimize: mql_where.take(n) → MongoDB find(filter).limit(n)
                        CommonRewrites.whereLimitRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_where"),
                                DCMNT_ISA_REWRITE_TID.extend("mql_where_limit"),
                                (space, dp, predicateStr, limit) -> {
                                    final Bson filter = parseMongoFilter(predicateStr);
                                    if (filter == null) {
                                        throw new IllegalArgumentException("Could not parse filter: " + predicateStr);
                                    }
                                    // MongoDB's limit(0) means "no limit", as mql_limit guards for the unfiltered
                                    // take(0): the filtered one returned every match
                                    return 0 == limit ? noobj() : readDocuments(space, dp.collection(),
                                            space.getDatabase().getCollection(dp.collection()).find(filter).limit((int) limit));
                                }
                        ),

                        // Optimize: from(collection/+).>>{field1,field2} → a MongoDB projection, and the >>{field1,field2} stays to project each document
                        CommonRewrites.selectRewrite(
                                dcmntSpace.class,
                                DCMNT_ISA_REWRITE_TID.extend("mql_select"),
                                (space, dp, columns) -> {
                                    final String collectionName = dp.collection();
                                    final MongoCollection<Document> collection = space.getDatabase().getCollection(collectionName);

                                    // Build MongoDB projection: {field1: 1, field2: 1, _id: 0}
                                    final Document projection = new Document();
                                    for (final String col : columns) {
                                        projection.append(col, 1);
                                    }
                                    // Exclude _id unless requested: a requested _id is read only to be stripped, as
                                    // the walk strips it from every document it reads
                                    if (!columns.contains(ID_FIELD_STRING)) {
                                        projection.append(ID_FIELD_STRING, 0);
                                    }

                                    return objs(IteratorUtil.stream(collection.find().projection(projection).iterator())
                                            .map(space::processDocument));
                                },
                                dcmntInstSet::readsDocuments
                        )
                )));
        docWrap(this,
                "nested documents, typed collections, and schema-enforced writes — all within metatron",
                "@mongodb:people/6/address    >>=  [street=>Elm Street,city=>Gotham]",
                "@mongodb:people/6/address    >>= +[zipcode=>90210]",
                "*mongodb:people/6/address         [-- [street=>Elm Street,city=>Gotham,zipcode=>90210] --]",
                "*mongodb:people/6/address/zipcode [-- 90210 --]");
        super.setup();

    }

    // ==================== Helper Methods for Rewrites ====================

    /**
     * Stamp a document with its collection's Type.  A bare
     * {@code rec::T} is not a refinement of the collection's type, so a
     * document read by a native rewrite must be typed to its collection to
     * remain a valid instance of the collection schema.  The type is read with
     * {@link SchemaSpace.Helper#type}, as the SQL plans read theirs: the declared
     * type, else the schema instset's own read, which infers it.  A scan of
     * {@code space.schema().types()} found only what a read had inferred before,
     * so a first query's documents went untyped (2026-11-25).  No-op when the
     * collection has no type.
     */
    private static Obj typeDocument(final Obj document, final Type collectionType) {
        // Only type a document the collection type actually accepts: a value stored
        // at an entry (dcmntSpace wraps it) is no document, and a document another
        // client wrote need not fit the type.  Stamping a non-instance would make
        // the native rewrites fail their result-type check.
        return collectionType == null || !document.test(collectionType)
                ? document : document.tid(collectionType.vid());
    }

    /**
     * THE DOCUMENTS A FIND ANSWERS, EACH READ AS THE WALK READS IT (2026-11-26): through
     * {@link dcmntSpace#processDocument}, so its {@code _id} is stripped, a value stored at an entry is unwrapped and a
     * reference is read as one, at the vid the walk gives it, typed to its collection. The plans read the raw document:
     * their documents kept the {@code _id} the walk strips, a stored value read as the document that wraps it, and a
     * document holding a reference failed, as the driver decodes one as a {@code DBRef} that {@code toBsonDocument}
     * has no codec for.
     */
    private static Obj readDocuments(final dcmntSpace space, final String collectionName,
                                     final FindIterable<Document> found) {
        final Type collectionType = SchemaSpace.Helper.type(space, collectionName);
        return objs(IteratorUtil.stream(found.iterator()).map(doc -> {
            final Object docId = doc.get(ID_FIELD_STRING);
            final String idStr = docId instanceof org.bson.types.ObjectId oid
                    ? oid.toHexString() : docId.toString();
            return typeDocument(space.processDocument(doc).selfVID(documentVID(space, collectionName, idStr)),
                    collectionType);
        }));
    }

    /**
     * The external VID of a document, built exactly the way the generic dcmnt
     * read path builds it (space pattern, then collection, then id).  Using the
     * scheme-less {@code DataPath.spaceURI()} instead produced a VID like
     * {@code users/user1}, which routes to the root memSpace rather than back to
     * this space.
     */
    private static fURI documentVID(final dcmntSpace space, final String collectionName, final String id) {
        return space.pattern().retractPattern().extend(collectionName).extend(id);
    }

    /**
     * A SUM OR A MEAN OF A FIELD THAT HOLDS WHAT IS NO NUMBER FAILS LOUDLY (2026-11-26, Marko): {@code $sum} and
     * {@code $avg} skip it, where the walk concatenates strs, merges sub-documents, folds lsts or fails, so the plan
     * answered a number the walk does not. The same {@code $group} counts those values: a value at or past {@code ""} in
     * BSON's order and before a timestamp (a str, a sub-document, an array, a binary, an ObjectId, a bool, a date), since
     * every number, null and a missing field sort before {@code ""}. A timestamp, a regex, a DBPointer, JavaScript and
     * MaxKey sort from the timestamp on, and the walk reads them as noobj and drops them as {@code $sum} skips them, so
     * they are not counted. A symbol sorts with the strs and a binary without mtron's magic byte with the binaries, so
     * those two are refused though the walk drops them. mongo-java-server orders values so too, and has no {@code $type}
     * or {@code $isNumber}. A count above 0 fails the plan. A date is refused as well, which the walk reads as its
     * milliseconds and adds. Null when no document is grouped.
     */
    private static Number fieldAggregate(final dcmntSpace space, final DataPath dp, final String accumulator) {
        final String field = "$" + dp.fieldPathStr();
        final Document result = space.getDatabase().getCollection(dp.collection()).aggregate(List.of(
                new Document("$group", new Document(ID_FIELD_STRING, null)
                        .append("value", new Document(accumulator, field))
                        .append("others", new Document("$sum", new Document("$cond", List.of(new Document("$and", List.of(
                                new Document("$gte", List.of(field, "")), new Document("$lt", List.of(field, new BsonTimestamp(0, 0))))), 1, 0))))))).first();
        if (null == result)
            return null;
        final long others = result.get("others", Number.class).longValue();
        if (others > 0)
            throw MTronException.of("%s holds %d values that are no number, which %s skips where the walk does not", dp.fieldPathStr(), others, accumulator);
        return result.get("value", Number.class);
    }

    /**
     * Guard for the native {@code sum} and {@code mean} aggregations: MongoDB aggregates one concrete field of the
     * documents the source reads ({@link DBRewriteBuilder#fieldPath}), a path below it as its dotted path. A source
     * with no field reads whole documents, which MongoDB does not add as the walk does (the walk's sum of documents is
     * their join, and their mean fails): {@code $sum: 1} counted them, and {@code $avg: 1} answered 1.0 for any
     * collection with a document. A wildcard field, a wildcard shift or a wildcard below the field (a walk over a
     * sub-document's values) is not representable either: {@code $sum: "$+"} and {@code $sum: "$points.+"} silently
     * aggregated to 0. Nor is an index below the field ({@code tags/0}, {@code tags/-1}, the walk's own index test,
     * CommonUtil.isInt): an aggregation path reads it as a field named 0, where the walk takes a lst's element.
     * Declining lets the walk answer those paths.
     */
    private static boolean summablePath(final dcmntSpace space, final List<Inst> matches) {
        if (!readsDocuments(space, matches))
            return false;
        final DataPath dp = DBRewriteBuilder.fieldPath(space, matches);
        return null != dp && (!dp.hasExtension() || dp.extension().segments().stream().noneMatch(CommonUtil::isInt));
    }

    /**
     * A PLAN READS DOCUMENTS (2026-11-25), so it declines a source the space reads flat (its isFlatRead): the kv_store
     * collection, or a collection the database does not hold and no schema declares. Under an absolute route
     * ({@code amongo:} to {@code /amongo/}), {@code *amongo:+} after a flat write reads that entry, and mql_count
     * counted the absent collection amongo: 0.
     */
    private static boolean readsDocuments(final dcmntSpace space, final List<Inst> matches) {
        final Obj ref = matches.getFirst().arg(0);
        return !ref.isUri() || !space.isFlatRead(space.redirect(ref.uriValue(), true));
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
