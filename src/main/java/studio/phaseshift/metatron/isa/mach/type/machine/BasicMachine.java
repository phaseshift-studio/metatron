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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.compiler.BasicCompiler;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_COMPILER_TID;
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

    private Processor cachedProcessor = null;
    private Compiler cachedCompiler = null;

    public BasicMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The outermost machine — pattern {@code ALL}, an index seeded with the {@code +/#} stack space, and no
     * components of its own. This is what {@code BootLoader} instantiates as the root: every address resolves
     * through it, and it delegates execution only if something is actually asked of it, which for the root
     * never happens.
     */
    public BasicMachine(final fURI vid) {
        super(vid);
    }

    public BasicMachine(final fURI pattern, final fURI vid) {
        super(pattern, vid);
    }

    /**
     * A machine's memory, seeded with the {@code +/#} stack space. The root is now minted from the template
     * rather than built by the pattern/vid constructor, so this seed has to live here or the root loses the
     * stack that the old constructor gave it.
     */
    private static BasicMemory rootMemory() {
        final BasicMemory memory = new BasicMemory();
        return memory;
    }

    public static BasicMachine of(final fURI tid, final fURI vid) {
        // EVERY COMPONENT SLOT IS BOUND AS ITSELF — not as a template (an instLambda the accessor must apply).
        // This is what lets them stop being deferred, and it needs nothing from the compiler: the accessor hands
        // back a slot that already holds its component, so AbstractMachine's constructor can parent it without
        // applying anything. The deferral was only ever about ORDER — this method mints the root machine during
        // BootLoader.load, before machInstSet.setup() assigns the component types AND before there is a machine to
        // compile with (a Call-valued slot died with "machine has no compiler: /m/mach/machine{0}"). Handing over
        // an instance needs neither a type nor a compiler.
        //
        // The processor keeps its CLONE-PER-FETCH semantic either way: processor() clones anything that is not a
        // call, and SwarmProcessor.clone() is the override that exists precisely to hand each run a fresh
        // processor rather than one sharing the running/barrier/halted queues.
        final BasicMemory memory = rootMemory();
        final BasicNetwork network = new BasicNetwork();
        final BasicInstSet instset = new BasicInstSet();
        return new BasicMachine(mutableMap(
                // a machine owns an address space: the pattern makes it the catch-all, and the index must be a
                // live mutable rec from the start. `Rec.orElse` eagerly evaluates its fallback, so an absent
                // index is a throwaway map and every addSpace into it would be silently lost.
                uri(PATTERN), uri(ALL),
                uri(QPROC), lst(QCollection.docQ()),
                uri(MEMORY), memory,
                uri(NETWORK), network,
                uri(INSTSET), instLambda(ignore -> instset),
                uri(COMPILER), BasicCompiler.defaults(),
                uri(PROCESSOR), SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null)), tid, vid);
        // uri("+").c(cInt.of(-1, 1)), instC(f("+").c(cInt.of(-1, 1)).dom(MACH_MACHINE_TID).rng(MACH_MACHINE_TID), lst(),
        //       (lhs, inst) -> lhs.asMachine().move(f(inst.tid().name()).c(inst.tid().c())))), tid, vid);
    }

    // ======================== hot slot reads ========================

    @Override
    public <OBJ extends Obj> OBJ at(final Obj key) {
        if (key.equals(uri(PROCESSOR)))
            return (OBJ) this.jvm().getOrDefault(uri(PROCESSOR), noobj());
        if (key.equals(uri(COMPILER)))
            return (OBJ) this.jvm().getOrDefault(uri(COMPILER), noobj());
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
}
