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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.function.BiConsumer;

import static studio.phaseshift.metatron.Tokens.SUBQ;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SubQ {

    private SubQ() {
        // do nothing
    }

    public static void subscribe(final fURI uri, final Obj onRecv) {
        Router.global().write(uri.addQ(SUBQ), instLambda((lhs, inst) -> onRecv.apply(lhs)));
    }

   /* public static void subscribe(final fURI uri, final BiFunction<Obj, Inst, Obj> onRecv) {
        Router.global().write(uri.addQ(SUBQ), instLambda(onRecv));
    }*/


    public static void subscribe(final fURI uri, final BiConsumer<Obj, Inst> onRecv) {
        Router.global().write(uri.addQ(SUBQ), instLambda((lhs, inst) -> {
            onRecv.accept(lhs, inst);
            return lhs;
        }));
    }

    public static void unsubscribe(final fURI uri) {
        Router.global().write(uri.addQ(SUBQ), noobj());
    }
}
