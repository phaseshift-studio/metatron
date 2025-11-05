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

package studio.phaseshift.metatron.lang.mkv;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;

import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mkv.mkvSpace.KVSPACE_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mkvInstSet extends MInstSet {

    public static final fURI MKV_TID = f("/mkv");
    public static final fURI INST_TID = MKV_TID.extend("inst");

    public mkvInstSet(final fURI vid) {
        super(MKV_TID, vid);
    }

    public static mkvInstSet create() {
        return new mkvInstSet(fURI.NULL);
    }

    @Override
    public Set<Inst> insts() {
        return Set.of(
                instC(INST_TID.extend("mkv").dom(ALL.maybe()).rng(KVSPACE_TID), rec(uri("pattern"), T(URI_TID)), (lhs, inst) -> mkvSpace.of(inst.arg("pattern").uriValue(), fURI.NULL)));
    }

   // @Override
//    public Set<Type> types() {
  //      return Set.of(T(KVSPACE_TID));
   // }
}