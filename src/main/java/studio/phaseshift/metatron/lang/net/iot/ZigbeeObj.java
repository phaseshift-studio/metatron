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

package studio.phaseshift.metatron.lang.net.iot;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ZigbeeObj extends MRec {

    public static final fURI ZIGBEE_TID = f("/web/zigbee");
    public static Type ZIGBEE_TYPE = docWrap(T(ZIGBEE_TID, null, instC(INST_TID.dom(REC_TID).rng(ZIGBEE_TID), lst(), (lhs, inst) -> {
        fURI newvid = lhs.<Rec>as().at(":id").uriValue();
        newvid = newvid.retract().extend("mtron").extend(newvid.tail(1));
        Router.global().logger().info("creating zigbee device: %s", newvid);
        return new ZigbeeObj(
                mParser.parse("-<?<=[name=>(device/friendlyName),device=>-<[(:id)].lift(|from(_))-<[_].lift(|auto(_))]")
                        .apply(lhs).jvm(), ZIGBEE_TID, fnull).vid(newvid);
    })), null, null, Map.of(), """
            */mqtt/zigbee2mqtt/+/
              .where(>>.?[device=>_])-<?<=[<<,>>]
              .temp(a=>(0),b=>(1)){*b.plus([:id=>auto(*a)])}
              .as(zigbee::T)""");

    public ZigbeeObj(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

}
