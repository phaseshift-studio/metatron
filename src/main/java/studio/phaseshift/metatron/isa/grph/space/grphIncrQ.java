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

package studio.phaseshift.metatron.isa.grph.space;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TYPE;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.VRTX_TYPE;
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
 * grphSpace-specific {@code incrQ}.
 * <p>
 * Extends {@link BaseQ} with a reference to the space so the preWrite
 * interceptor can create a vertex or edge without an explicit element ID,
 * letting JanusGraph (or the underlying TinkerPop graph) auto-assign it.
 * Follows the same pattern as {@code tbleIncrQ} and {@code dcmntIncrQ}.
 */
public class grphIncrQ extends BaseQ {

    public grphIncrQ(final grphSpace space) {
        super(buildJvm(space), INCRQ_PATTERN, INCRQ_TID);
    }

    private static Map<Obj, Obj> buildJvm(final grphSpace space) {
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
                                final DataPath dp = DataPath.withoutDB(aligned);
                                final String collection = dp.collection(); // "V" or "E"
                                final Rec rec = obj.isRec() ? obj.asRec()
                                        : studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(
                                        mutableMap(uri("val"), obj));

                                // Determine element type: V, E, or label-level collection
                                final boolean isV = "V".equals(collection);
                                final boolean isE = "E".equals(collection);
                                final boolean isVertex = isV
                                        || (!isE && space.schema().types().stream()
                                        .anyMatch(t -> t.vid().name()
                                                .equalsIgnoreCase(collection)
                                                && t.isRefinementOf(VRTX_TYPE)));
                                final boolean isEdge = isE
                                        || (!isV && !isVertex && space.schema().types().stream()
                                        .anyMatch(t -> t.vid().name()
                                                .equalsIgnoreCase(collection)
                                                && t.isRefinementOf(EDGE_TYPE)));
                                // Label unknown: default to vertex (e.g. bootstrap write)
                                final boolean isLabelVertex = !isV && !isE
                                        && !isVertex && !isEdge;

                                if (isVertex || isLabelVertex) {
                                    // ── vertex: auto-generated ID ──
                                    final String label = isV ? extractLabel(rec) : collection;
                                    final Vertex vertex = space.sjvm().addV(label).next();
                                    space.logger().debug("created vertex with auto-id {{b}}%s{{X}} label {{b}}%s{{X}}",
                                            vertex.id(), label);
                                    for (final Map.Entry<Obj, Obj> e : rec.jvm().entrySet()) {
                                        if (e.getKey().equals(grphInstSet.LABEL)) continue;
                                        final Obj value = e.getValue();
                                        if (value.isNoObj() || value.isNone()
                                                || value.isAuto())
                                            continue;
                                        final String propName = e.getKey().isUri()
                                                ? e.getKey().asUri().uriValue().name()
                                                : e.getKey().toString();
                                        space.sjvm().V(vertex.id())
                                                .property(propName,
                                                        ObjTP3Serializer.tp3Value(value))
                                                .next();
                                    }
                                    // Track field types for schema update
                                    final Map<String, Obj> existing =
                                            space.existingGraphSchema.getLogicalTypes()
                                                    .get(label.toLowerCase());
                                    final boolean isNewLabel = existing == null;
                                    boolean hasNew = isNewLabel;
                                    for (final Map.Entry<Obj, Obj> e : rec.jvm().entrySet()) {
                                        if (e.getKey().equals(grphInstSet.LABEL)) continue;
                                        final Obj value = e.getValue();
                                        if (value.isNoObj() || value.isNone()
                                                || value.isAuto())
                                            continue;
                                        final String propName = e.getKey().isUri()
                                                ? e.getKey().asUri().uriValue().name()
                                                : e.getKey().toString();
                                        space.existingGraphSchema.trackPropertyType(
                                                label, propName, value);
                                        if (!isNewLabel && !existing.containsKey(
                                                propName.toLowerCase()))
                                            hasNew = true;
                                    }
                                    if (hasNew)
                                        space.existingGraphSchema.onLabelChanged(
                                                label, isNewLabel);
                                    // Optional auto-commit
                                    space.at(uri("auto_tx")).ifPresent(x -> {
                                        if (x.boolValue())
                                            space.sjvm().tx().commit();
                                    });
                                } else if (isEdge) {
                                    // ── edge: auto-generated ID ──
                                    // Edges need OUT-V and IN-V references in the Rec.
                                    final String label = isE ? extractLabel(rec) : collection;
                                    final Obj outV = rec.at(uri("outV"));
                                    final Obj inV = rec.at(uri("inV"));
                                    if (outV.isNoObj() || inV.isNoObj()) {
                                        space.logger().warn(
                                                "edge write via incrQ requires outV and inV fields");
                                        return obj;
                                    }
                                    final Vertex outVertex = space.sjvm().V(
                                            Long.parseLong(outV.toString())).next();
                                    final Vertex inVertex = space.sjvm().V(
                                            Long.parseLong(inV.toString())).next();
                                    final org.apache.tinkerpop.gremlin.structure.Edge edge =
                                            space.sjvm().addE(label)
                                                    .from(outVertex).to(inVertex).next();
                                    space.logger().debug("created edge with auto-id {{b}}%s{{X}} label {{b}}%s{{X}}",
                                            edge.id(), label);
                                    // Optional auto-commit
                                    space.at(uri("auto_tx")).ifPresent(x -> {
                                        if (x.boolValue())
                                            space.sjvm().tx().commit();
                                    });
                                }
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
     * Extract the element label from a Rec — either from the explicit
     * {@code label} field or from the object's type TID.
     */
    private static String extractLabel(final Rec rec) {
        if (rec.jvm().containsKey(grphInstSet.LABEL))
            return rec.jvm().get(grphInstSet.LABEL).uriValue().toString();
        return rec.tid().basePath().toString();
    }
}
