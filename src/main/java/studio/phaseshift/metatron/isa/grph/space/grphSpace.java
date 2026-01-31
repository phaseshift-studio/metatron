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

package studio.phaseshift.metatron.isa.grph.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.VRTX_TID;
import static studio.phaseshift.metatron.isa.grph.type.Edge.EdgeType.EDGE_TYPE;
import static studio.phaseshift.metatron.isa.grph.type.Vrtx.VrtxType.VRTX_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class grphSpace<S extends Space> extends MSpace<S> {

    public static final fURI GRPH_SPACE_TID = MTRON_TID.extend("grph").extend("space/grph");

    public static final Type GRPH_SPACE_TYPE = T(GRPH_SPACE_TID, null,
            instC(GRPH_SPACE_TID.extend("con").dom(ALL.maybe()).rng(GRPH_SPACE_TID),
                    lst(rec(
                            uri(SPACE), uri(ALL),
                            uri(PATTERN), URI_TYPE,
                            uri(SCHEME).maybe(), rec(
                                    uri(VRTX_TID), VRTX_TYPE,
                                    uri(EDGE_TID), EDGE_TYPE))),
                    (lhs, inst) -> null));// grphSpace.of(inst.arg(0).asRec(), inst.arg(0).vid())));


    protected grphSpace(final Rec config, final fURI vid) {
        super(config.at(SPACE), config.jvm(), GRPH_SPACE_TID, vid);
    }

    /*public static <S extends Space> grphSpace<S> of(final Rec config, final fURI vid) {
        return new grphSpace<>(config, vid);
    }*/
}
