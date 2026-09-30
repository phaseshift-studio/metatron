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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.compiler.DefaultCompiler;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SWARM_PROCESSOR_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * BasicMachine — the concrete machine: a Router that binds an ISA to its lowering and execution
 * axes. It holds its instset, compiler and processor as rec entries (the {@code machine::T} shape),
 * and its own address space through the inherited Router surface. The default machine lives at
 * {@code /sys/mach} and is bootstrapped by {@code machInstSet.setup()}; this class is the stable
 * Java instance that lives there.
 *
 * The compiler and processor slots hold <em>proto</em> constructions — Java lambdas
 * ({@code instLambda(ignore -> …)}) that mint a fresh component directly, outside the mtron type
 * system. The first fetch mints and caches the prototype in a Java field; every later fetch
 * returns a clone, so callers get a fresh, unused component without re-paying the mint. The
 * {@code processor}/{@code compiler} slots are hot, so {@code at}/{@code atDirect} short-circuit
 * straight to the jvm map for those two keys instead of walking {@code Rec.at}'s uri machinery.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicMachine extends AbstractMachine {

    private Processor cachedProcessor = null;// SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null);
    private Compiler cachedCompiler = null;//new FixPointRewriter();

    public BasicMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static BasicMachine of(final fURI tid, final fURI vid) {
        return new BasicMachine(mutableMap(
                uri(INSTSET), instLambda(ignore -> null),
                uri(COMPILER), instLambda(ignore -> DefaultCompiler.fixpointScoringCompiler()),
                uri(PROCESSOR), instLambda(ignore -> SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null))), tid, vid);
    }

    // ======================== hot slot reads ========================

    @Override
    public <O extends Obj> O at(final Obj key) {
        if (key.equals(uri(PROCESSOR)))
            return (O) this.jvm().getOrDefault(uri(PROCESSOR), noobj());
        if (key.equals(uri(COMPILER)))
            return (O) this.jvm().getOrDefault(uri(COMPILER), noobj());
        return super.at(key);
    }

    // ======================== mint + cache ========================

    @Override
    public Processor processor() {
        if (null != this.cachedProcessor)
            return this.cachedProcessor.clone().as();
        final Obj proto = this.at(PROCESSOR).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        return (this.cachedProcessor = proto.isCall() ? proto.apply().as() : proto.as()).clone().as();
    }

    @Override
    public Compiler compiler() {
        if (null != this.cachedCompiler)
            return this.cachedCompiler.clone().as();
        final Obj proto = this.at(COMPILER).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        return (this.cachedCompiler = proto.isCall() ? proto.apply().as() : proto.as()).clone().as();
    }

    // ======================== setters invalidate the cache ========================

    @Override
    public Machine processor(final Processor processor) {
        this.cachedProcessor = null;
        return super.processor(processor);
    }

    @Override
    public Machine processor(final Call templateProcessor) {
        this.cachedProcessor = null;
        return super.processor(templateProcessor);
    }

    @Override
    public Machine compiler(final Compiler compiler) {
        this.cachedCompiler = null;
        return super.compiler(compiler);
    }

    @Override
    public Machine compiler(final Call templateCompiler) {
        this.cachedCompiler = null;
        return super.compiler(templateCompiler);
    }

    @Override
    public fURI tid() {
        return MACH_MACHINE_TID;
    }
}
