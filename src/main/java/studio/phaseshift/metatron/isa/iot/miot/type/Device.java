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

package studio.phaseshift.metatron.isa.iot.miot.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.miot.miotInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Set;

import static studio.phaseshift.metatron.Tokens.OFFLINE;
import static studio.phaseshift.metatron.Tokens.ONLINE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.MIOT_DEVICE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Device {

    private Device() {
        // do nothing
    }

    public static void installTypes(final Set<Type> types, final Set<Inst> insts) {
        Type.Builder.build()
                .tid(REC_TID)
                .vid(MIOT_DEVICE_TID)
                .isaPredicate(rec(uri("status"), is_(or_(eq_(uri(ONLINE)), eq_(uri(OFFLINE)))).tryToInst()))
                .constructor(lhs -> {
                    final fURI toVID = miotInstSet.deduceVID(lhs, f("+").extend(lhs.tid().name()));
                    if (toVID != null) {
                        final Obj sub = Router.readFromSpace(toVID.extend("status").q("sub"));
                        if (sub.isNoObj())
                            Router.writeToSpace(toVID.extend("status").q("sub"), print_(uri(toVID), str(" {{g}}status{{X}}: {{y}}"), get_(uri("" + 1))));
                    }
                    return lhs;
                })
                .inst(miotInstSet.MIOT_INST_TID.extend("attach").dom(MIOT_DEVICE_TID).rng(MIOT_DEVICE_TID), lst(T(miotInstSet.MIOT_ENTITY_TID)),
                        (lhs, inst) -> {
                            final fURI toVID = miotInstSet.deduceVID(lhs, f("+").extend(lhs.tid().name()));
                            if (null != toVID) {
                                lhs.asRec().at(inst.arg(0).tid().name(), inst.arg(0), Poly.MUTABLE);
                                Router.global().write(toVID.extend(inst.arg(0).tid().name()), inst.arg(0));
                            }
                            return lhs;
                        }).create(types, insts);
    }
}
