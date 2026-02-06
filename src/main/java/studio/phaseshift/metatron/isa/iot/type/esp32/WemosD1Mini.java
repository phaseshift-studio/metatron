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

package studio.phaseshift.metatron.isa.iot.type.esp32;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.type.SoC;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.ESP32_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.SOC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WemosD1Mini extends MRec implements SoC {

    public WemosD1Mini(final fURI tid, final fURI vid) {
        super(CommonUtil.mutableMap(), tid, vid);
    }

    public static String render =
            """
            
            {{b}}+------------------+
            {{b}}|{{w}}1  16       23  8 {{b}}|
            {{b}}|{{w}}2  17       24  9 {{b}}|
            {{b}}|{{w}}3  18       25 10 {{b}}|
            {{b}}|{{w}}4  19       26 11 {{b}}|
            {{b}}|{{w}}5  20       27 12 {{b}}|
            {{b}}|{{w}}6  21       28 13 {{b}}|
            {{b}}|{{w}}7  22       29 14 {{b}}|
            {{b}} \\_          {{w}}30 15{{b}} |
            {{b}}  +____{{y}}USB{{b}}________+{{X}}
            
            """;

    public static class WemosD1MiniType {

        public static final fURI WEMOS_D1_MINI_TID = SOC_TID.extend("esp32/d1_mini");
        public static final Type WEMOS_D1_MINI_TYPE = Type.Builder.build()
                .tid(ESP32_TID)
                .vid(WEMOS_D1_MINI_TID)
                .constructor(instC(INST_TID.dom(ALL.maybe()).rng(WEMOS_D1_MINI_TID), lst(rec()),
                        (lhs, inst) -> {
                            Router.global().write("d1_mini_render", str(render));
                            return rec(uri("layout"), auto_from_(uri("d1_mini_render")));
                        })).create();
    }
}
