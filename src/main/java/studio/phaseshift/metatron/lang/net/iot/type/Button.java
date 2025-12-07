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

package studio.phaseshift.metatron.lang.net.iot.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Bool;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.net.iot.type.impl.MButton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Button extends Bool {
    public static final fURI BUTTON_TID = f("/iot/button");

    default Button push() {
        this.logger().info("{{b}}%s{{X}} pushed: {{y}}%s {{g}}=> {{y}}%s", this.tid(), this.boolValue(), !this.boolValue());
        return this.jvm(!this.boolValue()).as();
    }


    public static final class ButtonType {


        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(BUTTON_TID.extend("push").dom(BUTTON_TID).rng(BUTTON_TID), lst(), (lhs, inst) -> new MButton(lhs.as()).push())
            ));

        }
    }
}
