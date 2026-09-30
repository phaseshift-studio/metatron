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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.Rewriter;
import studio.phaseshift.metatron.isa.mach.type.Typer;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_COMPILER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * DefaultCompiler — the concrete {@code compiler::T}: a rec holding its three atomic stages as rec
 * entries ({@code rewriter}, {@code resolver}, {@code typer}). The default schedule is the
 * three-stage composition {@code typer(resolver(rewriter(code)))}; generic binding runs inside the
 * resolver's per-candidate selection, so there is no separate binder stage.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DefaultCompiler extends AbstractCompiler {

    public DefaultCompiler() {
        this(mutableMap(
                uri(REWRITER), FixPointRewriter.single(),
                uri(RESOLVER), ScoringResolver.single(),
                uri(TYPER), TypeTyper.single()), MACH_COMPILER_TID, null);
    }

    public DefaultCompiler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The default compiler: fixpoint rewrite, scoring resolution, type assertions.
     *
     * @return a fresh {@code DefaultCompiler} wired to the default stages
     */
    public static DefaultCompiler fixpointScoringCompiler() {
        return new DefaultCompiler();
    }

    @Override
    public Rewriter rewrite() {
        return this.at(uri(REWRITER)).orElse(FixPointRewriter.single()).as();
    }

    @Override
    public Resolver resolver() {
        return this.at(uri(RESOLVER)).orElse(ScoringResolver.single()).as();
    }

    @Override
    public Typer typer() {
        return this.at(uri(TYPER)).orElse(TypeTyper.single()).as();
    }
}
