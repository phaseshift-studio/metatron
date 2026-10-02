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

package studio.phaseshift.metatron.isa.mach;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.compiler.DefaultCompiler;
import studio.phaseshift.metatron.isa.mach.type.compiler.TypeTyper;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.FirstFindResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.ScoringResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.TIME_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjFactory.M_FACTORY_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/mach")
public class machInstSet extends AbstractInstSet {
    public static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");
    public static final fURI MACH_MONAD_TID = MACH_ISA_TID.extend("monad");
    public static final fURI MACH_INST_TID = MACH_ISA_TID.extend("inst");
    public static final fURI MACH_THREAD_TID = MACH_ISA_TID.extend("thread");
    public static final fURI MACH_VIRTUAL_THREAD_TID = MACH_THREAD_TID.extend("virtual");
    public static final fURI MACH_CORE_THREAD_TID = MACH_THREAD_TID.extend("core");
    public static final fURI FACTORY_TID = MACH_ISA_TID.extend("factory");
    public static final fURI THREAD_EXECUTOR_TID = MACH_ISA_TID.extend("thread_executor");
    /// /// CLUSTER AWARENESS /// ///
    public static final fURI PEER_TID = MACH_ISA_TID.extend("peer");
    public static final fURI CLUSTER_TID = MACH_ISA_TID.extend("cluster");
    public static final fURI CLUSTER_STATUS_INST_TID = MACH_INST_TID.extend("status");
    public static Type PEER_TYPE;
    public static Type CLUSTER_TYPE;

    public static final Type FACTORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(FACTORY_TID)
            .create();

    /// //////////////////////////////////////////////////////////////////////
    public static Type THREAD_EXECUTOR_TYPE;
    public static final Type MACH_MONAD_TYPE = Type.Builder.build().tid(LST_TID).vid(MACH_MONAD_TID).create();
    public static Type MACH_VIRTUAL_THREAD_TYPE;
    // Common thread predicate shared by core and virtual
    public static final Type MACH_THREAD_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MACH_THREAD_TID)
            .isaPredicate(
                    rec(uri(CODE), T(ALL),
                            uri(SOURCE).maybe(), URI_TYPE,
                            uri(TIME).maybe(), DATETIME_TYPE,
                            uri(RUNTIME).maybe(), TIME_TYPE,
                            uri(YIELD).maybe(), T(MACH_THREAD_TID),
                            uri(LOOP).maybe(), TIME_TYPE,
                            uri(STATE).maybe().asUri(), union_(uri(STOP), uri(RUN), uri(PAUSE)).tryToInst(),
                            uri(RESULT).maybe(), T(ALL.maybeSome())))
            .create();
    public static Type MACH_CORE_THREAD_TYPE;
    public static Type MACH_MACHINE_TYPE;

    public static final fURI MACH_MACHINE_COMPONENT_TID = MACH_ISA_TID.extend("component");
    // the processor family — structural apply(code)->obj contract, then nominal monad marker, then concrete strategies
    public static final fURI MACH_PROCESSOR_TID = MACH_MACHINE_COMPONENT_TID.extend(PROCESSOR);
    public static final fURI MACH_MONAD_PROCESSOR_TID = MACH_PROCESSOR_TID.extend(MONAD);
    public static final fURI MACH_SWARM_PROCESSOR_TID = MACH_MONAD_PROCESSOR_TID.extend("swarm");
    public static Type MACH_PROCESSOR_TYPE;
    public static Type MACH_MONAD_PROCESSOR_TYPE;
    public static Type MACH_SWARM_PROCESSOR_TYPE;
    // the compiler family — structural apply(code)->code contract, then the three stage families it composes
    public static final fURI MACH_COMPILER_TID = MACH_MACHINE_COMPONENT_TID.extend(COMPILER);
    public static final fURI MACH_DEFAULT_COMPILER_TID = MACH_COMPILER_TID.extend("default");
    public static Type MACH_COMPILER_TYPE;
    public static Type MACH_DEFAULT_COMPILER_TYPE;
    // the rewriter family — structural rewrite(code)->code contract, concrete fixpoint strategy
    public static final fURI MACH_REWRITER_TID = MACH_MACHINE_COMPONENT_TID.extend(REWRITER);
    public static final fURI MACH_FIXPOINT_REWRITER_TID = MACH_REWRITER_TID.extend("fixpoint");
    public static Type MACH_REWRITER_TYPE;
    public static Type MACH_FIXPOINT_REWRITER_TYPE;
    // the resolver family — structural resolve(code)->code contract, concrete scoring + firstfind strategies
    public static final fURI MACH_RESOLVER_TID = MACH_MACHINE_COMPONENT_TID.extend(RESOLVER);
    public static final fURI MACH_SCORING_RESOLVER_TID = MACH_RESOLVER_TID.extend("scoring");
    public static final fURI MACH_FIRSTFIND_RESOLVER_TID = MACH_RESOLVER_TID.extend("firstfind");
    public static Type MACH_RESOLVER_TYPE;
    public static Type MACH_SCORING_RESOLVER_TYPE;
    public static Type MACH_FIRSTFIND_RESOLVER_TYPE;
    // the typer family — structural type(code)->code contract
    public static final fURI MACH_TYPER_TID = MACH_MACHINE_COMPONENT_TID.extend(TYPER);
    public static Type MACH_TYPER_TYPE;
    // memory — the machine's address space: relative bindings plus the index of absolute spaces
    public static final fURI MACH_MEMORY_TID = MACH_MACHINE_COMPONENT_TID.extend(MEMORY);
    public static Type MACH_MEMORY_TYPE;
    // network — the peers a machine can reach: a roster of authority => transport
    public static final fURI MACH_NETWORK_TID = MACH_MACHINE_COMPONENT_TID.extend(NETWORK);
    public static Type MACH_NETWORK_TYPE;
    public static Type MACH_MACHINE_COMPONENT_TYPE;


    public machInstSet() {
        super(mutableMap(uri(PATTERN), uri(MACH_ISA_TID.extend(ALL))), INSTSET_TID, MACH_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(MACH_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        SPACE_TYPE,
                        FACTORY_TYPE,
                        M_FACTORY_TYPE,
                        /////////////////////////
                        MACH_MACHINE_COMPONENT_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(MACH_MACHINE_COMPONENT_TID)
                                .isaPredicate(rec(uri(MACHINE).maybe().asUri(), T(MACH_MACHINE_TID)))
                                .create(),
                        // machine::T — the container: an ISA + a compiler + a processor
                        // the processor family — structural apply(code)->obj contract, nominal monad marker, concrete swarm strategy
                        MACH_PROCESSOR_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_PROCESSOR_TID)
                                .isaPredicate(rec(
                                        uri(STATE).maybe().asUri(), union_(uri(STOP), uri(RUN), uri(PAUSE)).tryToInst(),
                                        uri(RESULT).maybe(), T(ALL.maybeSome())))
                                .create(),
                        MACH_MONAD_PROCESSOR_TYPE = Type.Builder.build()
                                .tid(MACH_PROCESSOR_TID)
                                .vid(MACH_MONAD_PROCESSOR_TID)
                                .create(),
                        MACH_SWARM_PROCESSOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_PROCESSOR_TID)
                                        .vid(MACH_SWARM_PROCESSOR_TID)
                                        .constructor(machine -> SwarmProcessor.processor(machine.jvm(), machine.tid(), machine.vid()))
                                        .create(), null, null, Map.of(uri(CODE), "the code the processor will evaluate"),
                                "a swarm processor schedules independently executing monads across the code inst chain; barriers synchronize them, and the objects of the halted monads are the result"),
                        // the compiler family — structural apply(code)->code contract holding its three stages
                        MACH_COMPILER_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_COMPILER_TID)
                                .isaPredicate(rec(
                                        uri(REWRITER).maybe().asUri(), T(MACH_REWRITER_TID),
                                        uri(RESOLVER).maybe().asUri(), T(MACH_RESOLVER_TID),
                                        uri(TYPER).maybe().asUri(), T(MACH_TYPER_TID)))
                                .create(),
                        MACH_DEFAULT_COMPILER_TYPE = Type.Builder.build()
                                .tid(MACH_COMPILER_TID)
                                .vid(MACH_DEFAULT_COMPILER_TID)
                                .constructor(arg -> new DefaultCompiler(arg.asRec().jvm(), MACH_DEFAULT_COMPILER_TID, arg.vid()))
                                .create(),
                        // the rewriter family — structural contract, concrete fixpoint strategy
                        MACH_REWRITER_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_REWRITER_TID)
                                .create(),
                        MACH_FIXPOINT_REWRITER_TYPE = Type.Builder.build()
                                .tid(MACH_REWRITER_TID)
                                .vid(MACH_FIXPOINT_REWRITER_TID)
                                .isaPredicate(rec(uri(MAX).maybe().asUri(), isa_(INT_TYPE).else_(jnt(2))))
                                .constructor(arg -> new FixPointRewriter(arg.asRec().jvm(), MACH_FIXPOINT_REWRITER_TID, arg.vid()))
                                .create(),
                        // the resolver family — structural contract, concrete scoring + firstfind strategies (empty config)
                        MACH_RESOLVER_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_RESOLVER_TID)
                                .create(),
                        MACH_SCORING_RESOLVER_TYPE = Type.Builder.build()
                                .tid(MACH_RESOLVER_TID)
                                .vid(MACH_SCORING_RESOLVER_TID)
                                .constructor(arg -> new ScoringResolver(arg.asRec().jvm(), MACH_SCORING_RESOLVER_TID, arg.vid()))
                                .create(),
                        MACH_FIRSTFIND_RESOLVER_TYPE = Type.Builder.build()
                                .tid(MACH_RESOLVER_TID)
                                .vid(MACH_FIRSTFIND_RESOLVER_TID)
                                .constructor(arg -> new FirstFindResolver(arg.asRec().jvm(), MACH_FIRSTFIND_RESOLVER_TID, arg.vid()))
                                .create(),
                        // the typer family — structural contract, runtime type assertions (identity for now)
                        MACH_TYPER_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_TYPER_TID)
                                .constructor(arg -> new TypeTyper(arg.asRec().jvm(), MACH_TYPER_TID, arg.vid()))
                                .create(),
                        // memory — a machine's address space: its relative bindings are the rec itself, and its
                        // absolute index is the `space` entry inside it, so both are reachable from mtron
                        MACH_MEMORY_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_MEMORY_TID)
                                .constructor(arg -> new BasicMemory(arg.asRec().jvm(), arg.vid()))
                                .create(),
                        // network — a frame's reachable peers, read through to the ones it inherited
                        MACH_NETWORK_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_NETWORK_TID)
                                .constructor(arg -> new BasicNetwork(arg.asRec().jvm()))
                                .create(),
                        MACH_MACHINE_TYPE = Type.Builder.build()
                                .tid(SPACE_TID)
                                .vid(MACH_MACHINE_TID)
                                .isaPredicate(rec(
                                        uri(INSTSET).maybe().asUri(), INSTSET_TYPE,
                                        uri(COMPILER).maybe().asUri(), MACH_COMPILER_TYPE,
                                        uri(MEMORY).maybe().asUri(), MACH_MEMORY_TYPE,
                                        uri(NETWORK).maybe().asUri(), MACH_NETWORK_TYPE,
                                        uri(PROCESSOR).maybe().asUri(), MACH_PROCESSOR_TYPE))
                                .create(),
                        /// /////////////////////
                        THREAD_EXECUTOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(THREAD_EXECUTOR_TID)
                                        .isaPredicate(rec(uri(RUN), lst(), uri(STOP), lst())).create(),
                                "the gateway interface for all threads in metatron"),
                        docWrap(MACH_THREAD_TYPE, null, null,
                                Map.of(
                                        uri(CODE), "the thread's executing code",
                                        uri(TIME).maybe(), "the datetime::T when the thread was started",
                                        uri(RUNTIME).maybe(), "computes the thread's current running time::T",
                                        uri(LOOP).maybe(), "delay to repeat code evaluation (default is evaluate once)",
                                        uri(STATE).maybe(), "current state of the thread",
                                        uri(RESULT).maybe(), "the last result produced by the thread"), "the thread base type"),
                        MACH_CORE_THREAD_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_THREAD_TID)
                                        .vid(MACH_CORE_THREAD_TID)
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MACH_CORE_THREAD_TID), lst(T(REC_TID)),
                                                (lhs, inst) -> new CoreThread(inst.arg(0).jvm(), MACH_CORE_THREAD_TID, inst.arg(0).vid()).applyAsync(lhs)))
                                        .create(), null, null, Map.of(),
                                "run a concurrent core thread",
                                "core::[code=>ping(<phaseshift.studio:80>),loop=>second::1.0]@/sys/thread/ping"),
                        MACH_VIRTUAL_THREAD_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_THREAD_TID)
                                        .vid(MACH_VIRTUAL_THREAD_TID)
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MACH_VIRTUAL_THREAD_TID), lst(T(REC_TID)),
                                                (lhs, inst) -> new VirtualThread(inst.arg(0).jvm(), MACH_VIRTUAL_THREAD_TID, inst.arg(0).vid()).applyAsync(lhs)))
                                        .create(), null, null, Map.of(),
                                "run a concurrent virtual thread",
                                "virtual::[code=>ping(<phaseshift.studio:80>),loop=>second::1.5]@/sys/thread/ping"),
                        /// /// CLUSTER AWARENESS /// ///
                        docWrap(PEER_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(PEER_TID)
                                        .isaPredicate(rec(
                                                uri(AUTHORITY), URI_TYPE,
                                                // absent transport ⇒ a peer we know about but cannot reach from
                                                // here — which is what a peer reference looks like after it has
                                                // crossed a wire. One type, two honest states.
                                                uri(TRANSPORT).maybe().asUri(), INST_TYPE,
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(TAG).maybe().asUri(), STR_TYPE,
                                                uri(STATUS).maybe().asUri(), REC_TYPE))
                                        .create(), null, null,
                                Map.of(uri(AUTHORITY), "the peer's address, scheme and host:port",
                                        uri(TRANSPORT), "the inst that reaches it; absent when only known, not reachable",
                                        uri(STATUS), "the last health report computed for this peer"),
                                "one metatron instance the local instance knows about"),
                        docWrap(CLUSTER_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(CLUSTER_TID)
                                        .isaPredicate(rec(
                                                uri(PEER).maybe().asUri(), ALL_TYPE,
                                                uri(STATUS).maybe().asUri(), INST_TYPE,
                                                uri(NAME).maybe().asUri(), STR_TYPE))
                                        .create(), null, null,
                                Map.of(uri(PEER), "the declared roster — an auto pointer, never a stale copy",
                                        uri(STATUS), "the health method: peer => status"),
                                "this VM's static view of its cluster (fields + methods)")),
                uri(INST), lst(Stream.concat(Stream.empty(), Stream.of(
                        instC(THREAD_INST_TID.dom(ALL.maybe()).rng(MACH_THREAD_TID), lst(T(ALL)), (lhs, inst) -> {
                            final fURI baseVID = f("/sys/thread");
                            final VirtualThread thread = new VirtualThread(mutableMap(uri(CODE), inst.arg(0)), MACH_VIRTUAL_THREAD_TID, CommonUtil.mintShortUUID(baseVID, true));
                            final AbstractThread parent = BootLoader.CURRENT_THREAD.get();
                            if (null != parent && null != parent.vid())
                                thread.jvm().put(uri(SOURCE), auto_from_(uri(parent.vid())).tryToInst());
                            thread.applyAsync(lhs);
                            return thread;
                        }),
                        instC(MACH_INST_TID.extend("stop").dom(MACH_THREAD_TID).rng(MACH_THREAD_TID), lst(), (lhs, inst) -> {
                            ((AbstractThread) lhs).stop();
                            return lhs;
                        }),
                        instC(MACH_INST_TID.extend("pause").dom(MACH_THREAD_TID).rng(MACH_THREAD_TID), lst(), (lhs, inst) -> {
                            ((AbstractThread) lhs).pause();
                            return lhs;
                        }),
                        instC(MACH_INST_TID.extend("resume").dom(MACH_THREAD_TID).rng(MACH_THREAD_TID), lst(), (lhs, inst) -> {
                            ((AbstractThread) lhs).resume();
                            return lhs;
                        })
                ))),
                uri(CONST), lst(BasicMachine.of(MACH_MACHINE_TID, SYS.extend(MACH)))));
        docWrap(this, "the reflective instruction set of metatron featuring process, monad, and code introspection");
        super.setup();

    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>();
    }
}
