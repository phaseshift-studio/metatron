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
import org.bson.Document;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;

import java.util.Arrays;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.doc.docSpace.DOC_SPACE_TYPE;
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
 * docInstSet - Instruction set for document database operations
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
@InstSet.JREService(tid = "/m/doc")
public class docInstSet extends AbstractInstSet {

    public static final fURI DOC_ISA_TID = M_ISA_TID.extend("doc");
    public static final fURI DOC_ISA_INST_TID = DOC_ISA_TID.extend("inst");
    public static final fURI DOC_ISA_REWRITE_TID = DOC_ISA_INST_TID.extend("rewrite");
    public static final fURI DOCUMENT_TID = DOC_ISA_TID.extend("document");
    public static final fURI COLLECTION_TID = DOC_ISA_TID.extend("collection");
    public static final fURI ID_FIELD = f("_id");

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

    public docInstSet() {
        super(mutableMap(uri(PATTERN), uri(DOC_ISA_TID.extend(ALL))), DOC_ISA_TID, DOC_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(DOC_ISA_TID.extend(ALL)),
                uri(CONST), lst(ObjSimpleJSONSerializer.single(), uri(ID_FIELD, URI_TID, DOC_ISA_TID.extend(ID_FIELD))),
                uri(TYPE), lst(
                        docWrap(DOCUMENT_TYPE, "a document (record) from a collection"),
                        COLLECTION_TYPE,
                        DOC_SPACE_TYPE),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(DOCUMENT_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList()))),
                uri(REWRITE), lst(
                        // Optimize: *collection.count() → MongoDB countDocuments()
                        CommonRewrites.countRewrite(
                                docSpace.class,
                                DOC_ISA_REWRITE_TID.extend("native_count"),
                                (space, furi) -> {
                                    final String collectionName = furi.segments().getFirst();
                                    final MongoCollection<Document> collection = space.database.getCollection(collectionName);
                                    return collection.countDocuments();
                                }
                        ),

                        // Optimize: *collection.sum() → MongoDB aggregation $sum
                        CommonRewrites.sumRewrite(
                                docSpace.class,
                                DOC_ISA_REWRITE_TID.extend("native_sum"),
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
                                docSpace.class,
                                DOC_ISA_REWRITE_TID.extend("native_mean"),
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
                        )
                )));
        docWrap(this,
                "collections of nested documents accessible via the metatron",
                "mongodb:people/6/address>>=[street=>Elm Street,city=>Gotham,zipcode=>90210]");
        super.setup();

    }
}
