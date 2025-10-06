/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj.mext;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.space.Router;

import java.util.Set;

import static studio.phaseshift.metatron.lang.fURI.ALL;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/
public class mextInstSet extends MInstSet {


    public static final fURI MEXT_TID = fURI.of("/mext");
    public static final fURI COMPLEX_TID = MEXT_TID.extend("cmplx");

    private static Set<fURI> MEXT_TYPES = Set.of(COMPLEX_TID);

    public static final fURI MEXT_INST_TID = MEXT_TID.extend("inst");
    public static final fURI ID_TID = MEXT_INST_TID.extend("id");


    protected void load() {
        MEXT_TYPES.forEach(t -> Router.global().registerRewrite(f(t.name()), t));
        this.write(ID_TID, instC(ID_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(), (lhs, inst) -> lhs));

        //TODO: convert below to the pure write() model above
        // this.define(NOOBJ_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(), (lhs, inst) -> lhs); // noobj is also an inst (no inst)
    }

    public mextInstSet(final fURI vid) {
        super(MEXT_TID, vid);
        this.load();
    }

    public static mextInstSet of(final fURI vid) {
        return new mextInstSet(vid);
    }
}