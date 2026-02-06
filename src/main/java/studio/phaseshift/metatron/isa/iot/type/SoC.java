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

package studio.phaseshift.metatron.isa.iot.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Int;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.iot.iotInstSet.SOC_TID;
import static studio.phaseshift.metatron.isa.iot.type.Device.DEVICE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface SoC extends Rec {

    default void gpio(final Int pin, final Int value) {
        Router.global().write(this.tid().extend(pin.intValue().toString()), value);
    }

    default Int gpio(final Int pin) {
        return Router.global().read(this.tid().extend(pin.intValue().toString())).as();
    }

    final class SoCType {

        public static final fURI PIN_TID = SOC_TID.extend("pin");
        public static final Type PIN_TYPE = Type.Builder.build().tid(REC_TID).vid(PIN_TID).predicate(isa_(rec(
                uri("pin"), INT_TYPE,
                uri("usage").maybe(), DEVICE_TYPE))).create();

        public static final Type SOC_TYPE = Type.Builder.build().tid(REC_TID).vid(SOC_TID).predicate(isa_(rec(
                uri("boot").maybe().asUri(), URI_TYPE,
                uri("pin"), lst(PIN_TYPE),
                uri("stat").maybe(), rec(
                        uri("uptime").maybe().asUri(), INT_TYPE)))).create();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of());
        }
    }
}
