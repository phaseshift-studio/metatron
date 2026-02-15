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

package studio.phaseshift.metatron.isa.iot.miot.type.soc;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.MIOT_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.miot.type.Entity.MIOT_ENTITY_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.FAIL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface SoC extends Rec {

    fURI MIOT_SOC_TID = MIOT_ISA_TID.extend("soc");
    fURI MIOT_SOC_INST_TID = MIOT_SOC_TID.extend("inst");
    Type MIOT_SOC_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MIOT_SOC_TID)
            .predicate(isa_(rec(
                    uri("code").<Uri>maybe(), LST_TYPE,
                    uri("arch"), is_(or_(eq_(uri("esp32")), eq_(uri("esp8266")))),
                    URI_TYPE.<Type>maybe(), MIOT_ENTITY_TYPE))).create();

    fURI MIOT_SOC_INST_RESET_TID = MIOT_SOC_INST_TID.extend("reset");


    static Obj sendCode(final Obj soc, final Call code) {
        if (soc.vid() == null) return fail("vid required for %s", soc);
        Router.global().write(soc.vid().extend(CODE), code);
        return noobj();
    }

    static Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(MIOT_SOC_INST_RESET_TID.dom(MIOT_SOC_TID).rng(FAIL_TID.maybe()), lst(), (lhs, inst) -> SoC.sendCode(lhs, instB(MIOT_SOC_INST_RESET_TID, lst())))
        ));
    }

}
