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
import studio.phaseshift.metatron.isa.m.space.stackSpace;
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
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
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
        memory.spaces().jvm().put(uri("+/#"), new stackSpace(f("+/#")));
        return memory;
    }

    public static BasicMachine of(final fURI tid, final fURI vid) {
        // Built once, here, and the slots close over them. Applying a slot therefore yields this instance rather
        // than constructing one: `*/memory` reads a small template, `*/memory()` is the memory, and the accessor
        // on the read path allocates nothing. A slot that constructed on apply would re-enter type resolution
        // from inside `read` — the recursion that already bit MachineFrameTest once.
        final BasicMemory memory = rootMemory();
        final BasicNetwork network = new BasicNetwork();
        return new BasicMachine(mutableMap(
                // a machine owns an address space: the pattern makes it the catch-all, and the index must be a
                // live mutable rec from the start. `Rec.orElse` eagerly evaluates its fallback, so an absent
                // index is a throwaway map and every addSpace into it would be silently lost.
                uri(PATTERN), uri(ALL),
                uri(QPROC), lst(QCollection.docQ()),
                uri(MEMORY), instLambda(ignore -> memory),
                uri(NETWORK), instLambda(ignore -> network),
                uri(INSTSET), instLambda(ignore -> null),
                uri(COMPILER), instLambda(ignore -> DefaultCompiler.fixpointScoringCompiler()),
                uri(PROCESSOR), instLambda(ignore -> SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null))), tid, vid);
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

    // No tid() override: it used to hard-return MACH_MACHINE_TID, which made the reported tid disagree with
    // the stored one for every construction path but of(). The stored tid is the truth — /sys/mach is built
    // with MACH_MACHINE_TID, and the root with the router tid, exactly as before.
}
