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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * THE /m TYPE DECLARATIONS — the sibling of {@link mInsts}, and the counterpart of {@code catTypes}: the instset's
 * file stays the instset (constants, sugar, the aggregation seam), while its TYPES are declared here, one line per
 * carrier, in the same shape {@code catTypes} uses.
 * <p>
 * A type is declared by its ADDRESSES ({@code tid} = the representation it refines, {@code vid} = its name), its
 * PREDICATE (the refinement) and — the axis this whole effort has been moving — its ALGEBRA ({@code law => ...}),
 * the theory instances the type models. The algebra is declared with {@code Type.Builder} and persisted to
 * {@code ?lawq} by the drain that runs when this set is mounted, which is why it can be written here, before any
 * space exists.
 * <p>
 * These are declared against the {@code Tokens} addresses only, so this class depends on nothing in
 * {@link mInstSet} — no class-initialisation cycle, whatever loads first. {@code mInstSet} re-exports them by name
 * for the (many) call sites that read {@code mInstSet.INT_TYPE}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mTypes {

    private mTypes() {
    }

    public static final Type BOOL_TYPE = Type.Builder.build().tid(BOOL_TID).vid(BOOL_TID).create();

    public static final Type BYTES_TYPE = Type.Builder.build().tid(BYTES_TID).vid(BYTES_TID).create();

    public static final Type INT_TYPE = Type.Builder.build().tid(INT_TID).vid(INT_TID).create();

    public static final Type REAL_TYPE = Type.Builder.build().tid(REAL_TID).vid(REAL_TID).create();

    public static final Type STR_TYPE = Type.Builder.build().tid(STR_TID).vid(STR_TID).create();

    public static final Type URI_TYPE = Type.Builder.build().tid(URI_TID).vid(URI_TID).create();

    public static final Type REL_TYPE = Type.Builder.build().tid(REL_TID).vid(REL_TID).create();

    public static final Type LST_TYPE = Type.Builder.build().tid(LST_TID).vid(LST_TID).create();

    public static final Type REC_TYPE = Type.Builder.build().tid(REC_TID).vid(REC_TID).create();

    public static final Type INST_TYPE = Type.Builder.build().tid(M_ISA_INST_TID).vid(M_ISA_INST_TID).create();

    public static final Type CODE_TYPE = Type.Builder.build().tid(CODE_TID).vid(CODE_TID).create();

    public static final Type NOOBJ_TYPE = Type.Builder.build()
            .tid(NOOBJ_TID.zero()).vid(NOOBJ_TID.zero())
            .predicate((lhs, inst) -> noobj())
            .create();
}
