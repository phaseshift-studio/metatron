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
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.parser.Parser;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.compiler.parser.mtronParser;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.ScoringResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_COMPILER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicCompiler — the concrete {@code compiler::T}: a thin wrapper over the rec. It holds no Java
 * fields; its four stages ARE its rec entries, so a compiler is always introspectable — read
 * {@code .>>parser}, {@code .>>rewriter}, {@code .>>resolver}, {@code .>>typer} straight off the obj.
 * <p>
 * The defaults are data, not Java fallbacks: {@link #defaultStages()} supplies the four default
 * entries and {@link #stages(Map)} merges a caller's entries over them, so
 * {@code compiler::[=>]} mints the default compiler WITH its stages in the rec and
 * {@code compiler::[rewriter=>fixpoint_rewriter::[max=>3]]} overrides just one of them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicCompiler extends MRec implements Compiler {

    public BasicCompiler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * A compiler carrying the default stages — the constructor a subclass reaches for, so it cannot
     * accidentally be handed a bare map and then read {@code noobj} for a stage.
     */
    protected BasicCompiler(final fURI tid, final fURI vid) {
        this(defaultStages(), tid, vid);
    }

    /**
     * The default stage entries — mtron parse, fixpoint rewrite, scoring resolution, type assertions.
     * Every defaulting path goes through here, so a compiler's stages live in its rec rather than in
     * a Java-side {@code orElse}.
     */
    public static Map<Obj, Obj> defaultStages() {
        return mutableMap(
                uri(PARSER), mtronParser.single(),
                uri(REWRITER), FixPointRewriter.single(),
                uri(RESOLVER), ScoringResolver.single(),
                uri(TYPER), TypeTyper.single());
    }

    /**
     * The default stages with {@code overrides} merged over them (an override wins).
     */
    public static Map<Obj, Obj> stages(final Map<Obj, Obj> overrides) {
        final Map<Obj, Obj> stages = defaultStages();
        stages.putAll(overrides);
        return stages;
    }

    /**
     * The default compiler — the four default stages as rec entries.
     */
    public static BasicCompiler defaults() {
        return new BasicCompiler(defaultStages(), MACH_COMPILER_TID, null);
    }

    // the entries are read into an Obj before they are narrowed: at() is generic and unchecked, so
    // naming a concrete type at the read site makes the retrieval itself cast to that type.

    @Override
    public Parser parser() {
        final Obj entry = this.at(uri(PARSER));
        return entry.as();
    }

    @Override
    public Rewriter rewrite() {
        final Obj entry = this.at(uri(REWRITER));
        return entry.as();
    }

    @Override
    public Resolver resolver() {
        final Obj entry = this.at(uri(RESOLVER));
        return entry.as();
    }

    @Override
    public Typer typer() {
        final Obj entry = this.at(uri(TYPER));
        return entry.as();
    }
}
