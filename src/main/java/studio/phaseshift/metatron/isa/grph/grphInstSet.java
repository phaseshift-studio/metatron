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

package studio.phaseshift.metatron.isa.grph;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;

import java.util.*;

import static studio.phaseshift.metatron.isa.grph.space.grphSpace.GRPH_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/m/grph")
public class grphInstSet extends AbstractInstSet {

    public static final fURI GRPH_ISA_TID = MTRON_TID.extend("grph");
    public static final fURI GRPH_INST_TID = GRPH_ISA_TID.extend("inst");
    public static final fURI ELMT_TID = GRPH_ISA_TID.extend("elmt");
    public static final fURI VRTX_TID = GRPH_ISA_TID.extend("vrtx");
    public static final fURI EDGE_TID = GRPH_ISA_TID.extend("edge");

    public static final fURI OUT_INST_TID = GRPH_INST_TID.extend("out");
    public static final fURI IN_INST_TID = GRPH_INST_TID.extend("in");
    public static final fURI OUTE_INST_TID = GRPH_INST_TID.extend("outE");
    public static final fURI INE_INST_TID = GRPH_INST_TID.extend("inE");
    public static final fURI BOTH_INST_TID = GRPH_INST_TID.extend("both");
    public static final fURI BOTHE_INST_TID = GRPH_INST_TID.extend("bothE");
    public static final fURI OUTV_INST_TID = GRPH_INST_TID.extend("outV");
    public static final fURI INV_INST_TID = GRPH_INST_TID.extend("inV");
    public static final fURI BOTHV_INST_TID = GRPH_INST_TID.extend("bothV");
    public static final fURI VALUES_INST_TID = GRPH_INST_TID.extend("values");
    public static final fURI LABEL_INST_TID = GRPH_INST_TID.extend("label");
    public static final fURI PROPERTIES_INST_TID = GRPH_INST_TID.extend("properties");
    //
    public static final fURI GREMLIN_INST_TID = GRPH_INST_TID.extend("gremlin");

    public static final Uri OUT = uri("OUT");
    public static final Uri IN = uri("IN");
    public static final Uri LABEL = uri("LABEL");
    public static final Uri ID = uri("ID");

    public grphInstSet() {
        super(GRPH_ISA_TID, GRPH_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return new HashSet<>(List.of(GRPH_SPACE_TYPE));
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = new ArrayList<>();
        return new LinkedHashSet<>(insts);
    }
}
