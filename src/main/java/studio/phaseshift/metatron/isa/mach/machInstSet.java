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
import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.space.clstrSpace;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjFactory.M_FACTORY_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/mach")
public class machInstSet extends AbstractInstSet {
    public static final fURI MACH_ISA_TID = M_ISA_TID.extend("mach");
    public static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");
    public static final fURI MACH_MONAD_TID = MACH_ISA_TID.extend("monad");
    public static final fURI MACH_INST_TID = MACH_ISA_TID.extend("inst");
    public static final fURI MACH_THREAD_TID = MACH_ISA_TID.extend("thread");
    public static final fURI MACH_VIRTUAL_THREAD_TID = MACH_THREAD_TID.extend("virtual");
    public static final fURI MACH_CORE_THREAD_TID = MACH_THREAD_TID.extend("core");
    public static final fURI DROP_TID = MACH_INST_TID.extend("drop");
    public static final fURI RING_BINARY = MACH_INST_TID.extend("ring").extend("op").extend("+");
    public static final fURI CLSTR_SPACE_TID = MACH_ISA_TID.extend("clstrspace");
    public static Type CLSTR_SPACE_TYPE;
    public static final fURI FACTORY_TID = MACH_ISA_TID.extend("factory");
    public static final fURI THREAD_EXECUTOR_TID = MACH_ISA_TID.extend("thread_executor");

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
                            uri(STATE).maybe().asUri(), is_(or_(eq_(uri(STOP)), eq_(uri(RUN)), eq_(uri(PAUSE)))),
                            uri(RESULT).maybe(), T(ALL.maybeSome())))
            .create();
    public static Type MACH_CORE_THREAD_TYPE;
    public static Type MACH_MACHINE_TYPE;
    public static final fURI MACH_SWARM_MACHINE_TID = MACH_MACHINE_TID.extend("swarm");
    public static Type MACH_SWARM_MACHINE_TYPE;

    // the processor family — structural apply(code)->obj contract, then nominal monad marker, then concrete strategies
    public static final fURI MACH_PROCESSOR_TID = MACH_ISA_TID.extend("processor");
    public static final fURI MACH_MONAD_PROCESSOR_TID = MACH_PROCESSOR_TID.extend("monad");
    public static final fURI MACH_SWARM_PROCESSOR_TID = MACH_MONAD_PROCESSOR_TID.extend("swarm");
    public static Type MACH_PROCESSOR_TYPE;
    public static Type MACH_MONAD_PROCESSOR_TYPE;
    public static Type MACH_SWARM_PROCESSOR_TYPE;
    // the compiler family — structural apply(code)->code contract, then concrete strategies
    public static final fURI MACH_COMPILER_TID = MACH_ISA_TID.extend("compiler");
    public static final fURI MACH_FIXPOINT_COMPILER_TID = MACH_COMPILER_TID.extend("fixpoint");
    public static Type MACH_COMPILER_TYPE;
    public static Type MACH_FIXPOINT_COMPILER_TYPE;


    public machInstSet() {
        super(mutableMap(uri(PATTERN), uri(MACH_ISA_TID.extend(ALL))), INSTSET_TID, MACH_ISA_TID);
        // Router.global().registerPrefix(f("mach"), MACH_ISA_TID);
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
                        // the processor family — structural apply(code)->obj contract, nominal monad marker, concrete swarm strategy
                        MACH_PROCESSOR_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(MACH_PROCESSOR_TID)
                                .isaPredicate(rec(
                                        uri(STATE).maybe().asUri(), is_(or_(eq_(uri(STOP)), eq_(uri(RUN)), eq_(uri(PAUSE)))),
                                        uri(RESULT).maybe(), T(ALL.maybeSome())))
                                .create(),
                        MACH_MONAD_PROCESSOR_TYPE = Type.Builder.build()
                                .tid(MACH_PROCESSOR_TID)
                                .vid(MACH_MONAD_PROCESSOR_TID)
                                .create(),
                        MACH_SWARM_PROCESSOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MONAD_PROCESSOR_TID)
                                        .vid(MACH_SWARM_PROCESSOR_TID)
                                        .constructor(machine -> SwarmMachine.machine(machine.jvm(), machine.tid(), machine.vid()))
                                        .create(), null, null, Map.of(uri(CODE), "the code the processor will evaluate"),
                                "a swarm processor schedules independently executing monads across the code inst chain; barriers synchronize them, and the objects of the halted monads are the result"),
                        // the compiler family — structural apply(code)->code contract, concrete fixpoint strategy
                        MACH_COMPILER_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(MACH_COMPILER_TID)
                                .isaPredicate(rec(uri(INSTSET).maybe().asUri(), T(INSTSET_TID)))
                                .create(),
                        MACH_FIXPOINT_COMPILER_TYPE = Type.Builder.build()
                                .tid(MACH_COMPILER_TID)
                                .vid(MACH_FIXPOINT_COMPILER_TID)
                                .create(),
                        // machine::T — the container: an ISA + a compiler + a processor
                        MACH_MACHINE_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(MACH_MACHINE_TID)
                                .isaPredicate(rec(
                                        uri(INSTSET).maybe().asUri(), T(INSTSET_TID),
                                        uri(COMPILER).maybe().asUri(), T(MACH_COMPILER_TID),
                                        uri(PROCESSOR).maybe().asUri(), T(MACH_PROCESSOR_TID)))
                                .create(),
                        // the old swarm_machine::T — transition alias, re-parented under monad_processor
                        MACH_SWARM_MACHINE_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MONAD_PROCESSOR_TID)
                                        .vid(MACH_SWARM_MACHINE_TID)
                                        .constructor(machine -> SwarmMachine.machine(machine.jvm(), machine.tid(), machine.vid()))
                                        .create(), null, null, Map.of(uri(CODE), "the code the machine will evaluate"),
                                "a swarm machine makes use of a set of independently executing monads that move across the code inst chain. barriers serve as synchronization points where all running monads must aggregate before being released on the post-barrier segment of code. the objs referenced by the monads that halt are the result of the machine execution."),
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
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MACH_CORE_THREAD_TID), lst(T(REC_TID)), (lhs, inst) -> new CoreThread(inst.arg(0).jvm(), MACH_CORE_THREAD_TID, inst.arg(0).vid()).apply(lhs)))
                                        .create(), null, null, Map.of(),
                                "run a concurrent core thread",
                                "core::[code=>ping(<phaseshift.studio:80>),loop=>second::1.0]@/sys/thread/ping"),
                        MACH_VIRTUAL_THREAD_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_THREAD_TID)
                                        .vid(MACH_VIRTUAL_THREAD_TID)
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MACH_VIRTUAL_THREAD_TID), lst(T(REC_TID)), (lhs, inst) -> {
                                            final VirtualThread vt = new VirtualThread(inst.arg(0).jvm(), MACH_VIRTUAL_THREAD_TID, inst.arg(0).vid());
                                            vt.applyAsync(lhs);
                                            return vt;
                                        }))
                                        .create(), null, null, Map.of(),
                                "run a concurrent virtual thread",
                                "virtual::[code=>ping(<phaseshift.studio:80>),loop=>second::1.5]@/sys/thread/ping"),
                        docWrap(CLSTR_SPACE_TYPE = Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(CLSTR_SPACE_TID)
                                        .isaPredicate(rec(uri(PEER).maybe().asUri(), rec(AUTHORITY_TYPE, ALL_TYPE).maybe()))
                                        .constructor(obj -> new clstrSpace(new ConcurrentHashMap<>(), obj.asRec().jvm(), CLSTR_SPACE_TID, obj.vid())).create(),
                                null, null,
                                Map.of(uri(PEER), "known metatron instance elsewhere in ws or http space"),
                                """
                                a peer is a wsclient to a mtron_ws handler. 
                                *x and x->y are the respective read/write insts sent to the peer for evaluation.
                                """)),
                uri(INST), lst(Stream.concat(Router.RouterType.insts().stream(), Stream.of(
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
                )))));
        docWrap(this, "the reflective instruction set of metatron featuring process, monad, and code introspection");
        super.setup();

    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>();
    }
}
