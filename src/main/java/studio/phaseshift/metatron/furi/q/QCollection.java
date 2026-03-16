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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashSet;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.CONST;
import static studio.phaseshift.metatron.furi.Q.Q_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class QCollection {

    public static final fURI CONSTQ_TID = Q_TID.extend("constq");
    public static final Type CONSTQ_TYPE = Type.Builder.build().tid(Q_TID).vid(CONSTQ_TID).constructor(QCollection::constQ).create();

    private QCollection() {
        // do nothing 
    }


    public static Q constQ() {
        final Set<fURI> CONSTQ_FURIS = new HashSet<>();
        return BaseQ.create(CONSTQ_TID, f(CONST),
                furi -> bool(CONSTQ_FURIS.contains(furi.noQ())),         // pre-read
                null,                                                    // post-read
                (furi, obj) -> {                                         // pre-write
                    if (obj.isNoObj())
                        CONSTQ_FURIS.remove(furi.noQ());
                    else
                        CONSTQ_FURIS.add(furi.noQ());
                    return noobj();
                },
                null,                                                    // post-write
                (furi, _) -> {                                           // qless-write
                    if (!furi.hasQ(CONST) && CONSTQ_FURIS.contains(furi.noQ()))
                        return fail(MTronException.of("%s is a constant", furi.noQ()));
                    return noobj();
                });
    }
}
