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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Typer;

import java.util.Map;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_TYPER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * TypeTyper — the concrete {@code typer::T}: the runtime type-assertion stage. Identity for now —
 * the {@code inst_dom}/{@code inst_rng}/{@code type_pred} rec flags select which assertions run, and
 * those assertions are wired up separately (per-machine) rather than read from the global TypeCheck.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeTyper extends MRec implements Typer {

    private static final TypeTyper INSTANCE = new TypeTyper(mutableMap(), MACH_TYPER_TID, null);

    public static TypeTyper single() {
        return INSTANCE;
    }

    public TypeTyper() {
        this(mutableMap(), MACH_TYPER_TID, null);
    }

    public TypeTyper(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj code) {
        // TODO: run inst_dom/inst_rng/type_pred assertions per the rec flags
        return code.asCode();
    }
}
