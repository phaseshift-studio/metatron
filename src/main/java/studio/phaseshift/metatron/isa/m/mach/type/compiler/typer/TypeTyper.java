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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.typer;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TYPER_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.LAW_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
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

    public TypeTyper(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.jvm().put(uri("verify"), instLambda(lst(ALL_TYPE, T(LST_TID.poly(LAW_TID.maybeSome()))), (_, inst) -> {
            if (inst.arg(0).isObjInst())
                return mInstSet.AlgebraTool.analyzeTypeInst(inst.arg(0).dom(), inst.arg(0).asInst(), inst.arg(1).orElse(lst()).lstValue().stream().map(u -> Law.valueOf(u.uriValue().name())).toList()).toRec();
            return mInstSet.AlgebraTool.analyzeType(inst.arg(0).asType(), inst.arg(1).orElse(lst()).lstValue().stream().map(u -> Law.valueOf(u.uriValue().name())).toList()).toRec();
        }));
    }

    @Override
    public Code apply(final Obj code) {
        // TODO: run inst_dom/inst_rng/type_pred assertions per the rec flags
        return code.asCode();
    }
}
