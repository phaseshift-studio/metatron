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

import studio.phaseshift.metatron.algebra.AlgebraTool;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TYPER_TID;
import static studio.phaseshift.metatron.algebra.Law.LAW_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * TypeTyper — the concrete {@code typer::T}: the runtime type-assertion stage. Identity for now —
 * the {@code inst_dom}/{@code inst_rng}/{@code type_pred} rec flags select which assertions run, and
 * the assertions are enforced at the point that reads them (the processor, the space write) via
 * {@code Typer.Helper}, and the degree of checking is THIS typer's {@code check} state.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicTyper extends MRec implements Typer {

    /**
     * A typer whose {@code check} state is {@code check} — what the boot installs into the machine's compiler, and
     * what a test can mint directly.
     */
    public static BasicTyper of(final Rec check) {
        final BasicTyper typer = new BasicTyper(mutableMap(), MACH_TYPER_TID, null);
        typer.jvm().put(uri(Typer.CHECK), check);
        return typer;
    }

    /**
     * A typer carrying the boot default state — one instance per compiler, so the degree of checking belongs to the
     * machine that owns the compiler rather than to a JVM-wide singleton.
     */
    /**
     * A defaulting typer whose state is the boot default — one per compiler, so each machine owns its own degree
     * of checking.
     */
    public BasicTyper() {
        this(mutableMap(), MACH_TYPER_TID, null);
    }

    public BasicTyper(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        // a given check is kept (a point it leaves out reads the boot default); only a typer without one is seeded
        this.jvm().putIfAbsent(uri(Typer.CHECK), rec(new LinkedHashMap<>(Typer.defaults().jvm())));
        this.jvm().put(uri("verify"), instLambda(lst(ALL_TYPE, T(LST_TID.poly(LAW_TID.maybeSome()))), (_, inst) -> {
            if (inst.arg(0).isObjInst())
                return AlgebraTool.analyzeTypeInst(inst.arg(0).dom(), inst.arg(0).asInst(), inst.arg(1).orElse(lst()).lstValue().stream().map(u -> Law.valueOf(u.uriValue().name())).toList()).toRec();
            return AlgebraTool.analyzeType(inst.arg(0).asType(), inst.arg(1).orElse(lst()).lstValue().stream().map(u -> Law.valueOf(u.uriValue().name())).toList()).toRec();
        }));
    }

    @Override
    public Code apply(final Obj code) {
        // TODO: run inst_dom/inst_rng/type_pred assertions per the rec flags
        return code.asCode();
    }
}
