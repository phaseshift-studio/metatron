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

package studio.phaseshift.metatron.isa.vec.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;
import java.util.UUID;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * vctrSpace-specific {@code incrQ}.
 * <p>
 * Extends {@link BaseQ} with a reference to the space so the preWrite
 * interceptor can generate a UUID document ID when the caller writes
 * without specifying one.  Follows the same pattern as {@code tbleIncrQ}
 * and {@code grphIncrQ}.
 */
public class vecIncrQ extends BaseQ {

    public vecIncrQ(final vecSpace space) {
        super(buildJvm(space), INCRQ_PATTERN, INCRQ_TID);
    }

    private static Map<Obj, Obj> buildJvm(final vecSpace space) {
        return mutableMap(
                uri(PATTERN), uri(INCRQ_PATTERN),
                uri(PRE_WRITE), instC(
                        M_ISA_INST_TID
                                .dom(ALL.maybe())
                                .rng(ALL.maybeSome()),
                        lst(URI_TYPE, T(ALL)),
                        (lhs, inst) -> {
                            final fURI vid = inst.arg(0).uriValue();
                            final Obj obj = inst.arg(1);
                            // When the write target is a wildcard or collection-level
                            // (no entry), generate a UUID ID.  The directWriter will
                            // pick it up on the actual write path.
                            final fURI cleaned = vid.removeQ(INCRQ_PATTERN);
                            if (cleaned.segmentLength() <= 1) {
                                // Collection-level write: inject a UUID segment
                                final String newId = UUID.randomUUID().toString();
                                final fURI newVid = cleaned.extend(newId);
                                space.write(newVid, obj);
                                return obj;
                            }
                            return obj;
                        }),
                uri(POST_WRITE), noobj(),
                uri(PRE_READ), noobj(),
                uri(POST_READ), noobj(),
                uri(QLESS_WRITE), noobj()
        );
    }
}
