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

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * dcmntSpace-specific {@code incrQ}.
 * <p>
 * Extends {@link BaseQ} with a reference to the space so the preWrite
 * interceptor can insert a new document without an explicit {@code _id},
 * letting MongoDB auto-generate the {@link org.bson.types.ObjectId}.
 * Follows the same pattern as {@code tbleIncrQ}.
 */
public class dcmntIncrQ extends BaseQ {

    public dcmntIncrQ(final dcmntSpace space) {
        super(buildJvm(space), INCRQ_PATTERN, INCRQ_TID);
    }

    private static Map<Obj, Obj> buildJvm(final dcmntSpace space) {
        return mutableMap(
                uri(PATTERN), uri(INCRQ_PATTERN),
                uri(PRE_WRITE), instC(
                        M_ISA_INST_TID
                                .dom(ALL.maybe())
                                .rng(ALL.maybeSome()),
                        lst(studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE,
                                T(ALL)),
                        (lhs, inst) -> {
                            final fURI vid = inst.arg(0).uriValue();
                            final Obj obj = inst.arg(1);
                            final fURI cleaned = vid.removeQ(INCRQ_PATTERN);
                            try {
                                final fURI aligned = Space.Helper
                                        .routeFromSpace(cleaned, space.routes());
                                final DataPath dp = DataPath.of(
                                        f(space.getDatabaseName()).extend(aligned));
                                final Rec rec = obj.isRec() ? obj.asRec()
                                        : studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(
                                        mutableMap(uri("val"), obj));

                                final MongoCollection<Document> collection =
                                        space.getDatabase().getCollection(dp.collection());

                                // Build a BSON Document from the Rec, omitting auto/
                                // noobj values.  No _id is set so MongoDB generates
                                // an ObjectId automatically.
                                final Document doc = new Document();
                                rec.jvm().forEach((key, value) -> {
                                    if (value.isAuto() || value.isNoObj() || value.isNone())
                                        return;
                                    final String fieldName = key.isUri()
                                            ? key.asUri().uriValue().name()
                                            : key.toString();
                                    doc.put(fieldName, unwrapValue(value));
                                });
                                collection.insertOne(doc);

                                final org.bson.types.ObjectId generatedId =
                                        doc.getObjectId("_id");
                                space.logger().debug("inserted document with auto-id {{b}}%s{{X}} in collection {{b}}%s{{X}}",
                                        generatedId.toHexString(), dp.collection());

                                // Track field types for incremental schema update
                                final String collName = dp.collection();
                                final Map<String, Obj> existing = space.existingCollectionSchema
                                        .getLogicalTypes().get(collName.toLowerCase());
                                final boolean isNewCollection = existing == null;
                                boolean hasNewFields = isNewCollection;
                                for (final Map.Entry<Obj, Obj> entry : rec.jvm().entrySet()) {
                                    if (entry.getValue().isAuto() || entry.getValue().isNoObj()
                                            || entry.getValue().isNone())
                                        continue;
                                    final String fieldName = entry.getKey().isUri()
                                            ? entry.getKey().asUri().uriValue().name()
                                            : entry.getKey().toString();
                                    space.existingCollectionSchema.trackFieldType(
                                            collName, fieldName, entry.getValue());
                                    if (!isNewCollection && !existing.containsKey(
                                            fieldName.toLowerCase()))
                                        hasNewFields = true;
                                }
                                if (hasNewFields)
                                    space.existingCollectionSchema.onCollectionChanged(
                                            collName, isNewCollection);
                            } catch (final Exception e) {
                                throw MTronException.of(e);
                            }
                            return obj;
                        }),
                uri(POST_WRITE), noobj(),
                uri(PRE_READ), noobj(),
                uri(POST_READ), noobj(),
                uri(QLESS_WRITE), noobj()
        );
    }

    /**
     * Unwrap a metatron value to its JVM counterpart suitable for BSON
     * serialization.  Strings, numbers, booleans pass through directly;
     * everything else falls back to {@code toString()}.
     */
    private static Object unwrapValue(final Obj value) {
        if (value.isStr()) return value.asStr().jvm();
        if (value.isInt()) return value.asInt().jvm();
        if (value.isReal()) return value.asReal().jvm();
        if (value.isBool()) return value.asBool().jvm();
        return value.jvm();
    }
}
