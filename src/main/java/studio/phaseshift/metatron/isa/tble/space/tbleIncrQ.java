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

package studio.phaseshift.metatron.isa.tble.space;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.tble.tbleSpace;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_TID;
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
 * tbleSpace-specific {@code incrQ}.
 * <p>
 * Extends {@link BaseQ} with a reference to the space so the preWrite
 * interceptor can perform the auto-increment INSERT directly through
 * {@link ExistingTableSchema}.  Follows the same pattern as
 * {@code dcmntSpaceSubQ} and {@code MqttPubSubQ}.
 */
public class tbleIncrQ extends BaseQ {

    public tbleIncrQ(final tbleSpace space) {
        super(buildJvm(space), INCRQ_PATTERN, INCRQ_TID);
    }

    private static Map<Obj, Obj> buildJvm(final tbleSpace space) {
        return mutableMap(
                uri(PATTERN), uri(INCRQ_PATTERN),
                uri(PRE_WRITE), instC(
                        studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID
                                .dom(fURI.Singleton.ALL.maybe())
                                .rng(fURI.Singleton.ALL.maybeSome()),
                        lst(studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE,
                                T(fURI.Singleton.ALL)),
                        (lhs, inst) -> {
                            final fURI vid = inst.arg(0).uriValue();
                            final Obj  obj = inst.arg(1);
                            final fURI cleaned = vid.removeQ(INCRQ_PATTERN);
                            try {
                                final fURI aligned = studio.phaseshift.metatron.isa.Space.Helper
                                        .routeFromSpace(cleaned, space.routes());
                                final DataPath dp = DataPath.of(
                                        f(space.getDatabaseName()).extend(aligned));
                                final studio.phaseshift.metatron.isa.m.type.Rec rec =
                                        obj.isRec() ? obj.asRec()
                                                : studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(
                                                        studio.phaseshift.metatron.util.CommonUtil.mutableMap(
                                                                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("val"), obj));
                                space.existingTableSchema().ensureTableAndInsert(
                                        space.sjvm(), dp.collection(), rec);
                            } catch (final Exception e) {
                                space.logger().warn(
                                        "tbleIncrQ auto-insert failed: %s", e.getMessage());
                            }
                            return obj;
                        }),
                uri(POST_WRITE), noobj(),
                uri(PRE_READ),  noobj(),
                uri(POST_READ), noobj(),
                uri(QLESS_WRITE), noobj()
        );
    }
}
