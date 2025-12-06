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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Feature {

    interface HasLogger {

        default GraphittyLogger logger() {
            return Graphitty.log(this);
        }
    }

    interface SelfClone extends HasLogger {

        default <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
            this.logger().warn("this obj doesn't support pure cloning");
            ((Obj) this).self(jvm, tid, vid);
            return (O) this;
        }

        default Obj clone() {
            this.logger().warn("this obj doesn't support pure cloning");
            return (Obj) this;
        }
    }
}
