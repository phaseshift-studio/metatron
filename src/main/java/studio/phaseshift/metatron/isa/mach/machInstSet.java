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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Machine;
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

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.TIME_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjFactory.M_FACTORY_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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

    /**
     * This machine's own name in a cluster roster. The home is IN the roster -- it is one of the machines -- but
     * it is not a PEER of itself, so it is filtered out of the distribution and its mailboxes are the ones the
     * peers report to.
     */
    public static final String MACH_HOME = "a";

    /**
     * WHERE DISTRIBUTED STATE LIVES. Never under /sys: that is the local instance's bookkeeping, and a mailbox or an
     * inbox is written by one machine and read by another, so it must live where a shared subspace can cover it.
     * /sys/peer stays where it is for the same reason -- the roster IS local bookkeeping, this machine's view of who
     * is in the cluster.
     */
    /**
     * WHERE DISTRIBUTED STATE LIVES, and it is a PLAIN NAMESPACE -- no scheme, no host, nothing that says what
     * answers it. A root that named its backend would put the transport into the rewrite, which is the one thing
     * this design cannot afford: the rewrite mints {@code /usr/compute/<home>/barrier/<peer>} and must not know or
     * care whether a local memory space, a syncing subspace or a remote peer answers it. What varies between
     * deployments is the SPACE MOUNTED OVER IT, never the address.
     */
    public static final fURI COMPUTE = f("/usr/compute");

    /**
     * the box a machine listens on for code to execute
     */
    public static final fURI MACH_RECV_INST_TID = MACH_INST_TID.extend("recv");

    /**
     * Machine {@code machine}'s contiguous slice of the data: {@code per} elements each, taken in order, the HOME
     * taking the first and the LAST machine taking whatever remains (so a remainder is never dropped, and a machine
     * is empty only when there are fewer values than machines).
     * <p>
     * This is what makes a peer's mailbox predictable: its value is the reducer applied to ITS slice, so
     * {@code {1,2,3}} over one peer ({@code per = ceil(3/2) = 2}) gives the home {@code {1,2}} and the peer
     * {@code {3}} -- a mailbox of 6 for {@code plus(1).plus(2).sum()}, with the home folding 9.
     */
    static List<Obj> sliceOf(final List<Obj> values, final int machine, final int machines, final int per) {
        final int from = Math.min(machine * per, values.size());
        final int to = machine == machines - 1 ? values.size() : Math.min(from + per, values.size());
        return values.subList(from, to);
    }

    /**
     * The rewrite-rule namespace of the machine ISA -- one rule per name, mirroring {@code /m/inst/rewrite}.
     * Rules registered under it are collected by {@link FixPointRewriter} and applied to every compiled code.
     */
    public static final fURI MACH_REWRITE_TID = MACH_INST_TID.extend(REWRITE);
    public static final fURI GATHER_INST_TID = MACH_INST_TID.extend("gather");

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
                        /// /// CLUSTER AWARENESS /// ///
                        docWrap(PEER_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(PEER_TID)
                                        .isaPredicate(rec(
                                                uri(AUTHORITY), URI_TYPE,
                                                // absent transport ⇒ a peer we know about but cannot reach from
                                                // here — which is what a peer reference looks like after it has
                                                // crossed a wire. One type, two honest states.
                                                //uri(TRANSPORT).maybe().asUri(), INST_TYPE,
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(STATUS).maybe().asUri(), REC_TYPE))
                                        .create(), null, null,
                                Map.of(uri(AUTHORITY), "the peer address with scheme and host:port for remote peers",
                                        uri(NAME).maybe(), "a simple name for the peer",
                                        //uri(TRANSPORT), "the inst that reaches it; absent when only known, not reachable",
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
                                "this VM's static view of its cluster (fields + methods)"),
                        MACH_NETWORK_TYPE = Type.Builder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_NETWORK_TID)
                                // .isaPredicate(rec(
                                //         uri(NAME).maybe().asUri(), STR_TYPE,
                                //         uri(PEER).maybe().asUri(), lst(PEER_TYPE),
                                //         uri(STATUS).maybe().asUri(), INST_TYPE,
                                //         uri(STATE).maybe().asUri(), rec(
                                //                 uri(LOCAL), ALL_TYPE,
                                //                 URI_TYPE, ALL_TYPE)))
                                .constructor(arg -> new BasicNetwork(arg.asRec().jvm()))
                                .create(),
                        MACH_MACHINE_TYPE = docWrap(Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(MACH_MACHINE_TID)
                                        .isaPredicate(rec(
                                                uri(INSTSET).maybe().asUri(), INSTSET_TYPE,
                                                uri(COMPILER).maybe().asUri(), MACH_COMPILER_TYPE,
                                                uri(MEMORY).maybe().asUri(), MACH_MEMORY_TYPE,
                                                uri(NETWORK).maybe().asUri(), MACH_NETWORK_TYPE,
                                                uri(PROCESSOR).maybe().asUri(), MACH_PROCESSOR_TYPE))
                                        .constructor(arg -> BasicMachine.of(MACH_MACHINE_TID, arg.vid()))
                                        .create(), Map.of(uri(INSTSET), "machine instruction set architecture",
                                        uri(COMPILER), "parser, rewriter, resolver, and typer",
                                        uri(MEMORY), "a frame of reference in space constrained by parent machine",
                                        uri(NETWORK), "peers and shared peer state",
                                        uri(PROCESSOR), "referential access to processing threads and computational state"),
                                """
                                a machine integrates language, structure, and process providing a frame of reference
                                and action within metatron. machine's form nested stack by which parent machine state
                                is accessible to children and by which parent machine's define the scope of a child
                                machine's access to resources. lateral machine communication made possible through
                                the  machine network where the peer group forms a shared computing workspace that is
                                garbage collected when machine is popped off the parent stack. the absolute local root
                                of the metatron graph (/.) is a root machine.
                                """),
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
                                "virtual::[code=>ping(<phaseshift.studio:80>),loop=>second::1.5]@/sys/thread/ping")),
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
                // uri(CONST), lst(docWrap(BasicMachine.of(MACH_MACHINE_TID, SYS.extend(MACH).extend(DEFAULT)),
                //         mutableMap(
                //                 uri(INSTSET), "machine instruction set architecture",
                //                 uri(COMPILER), "machine rewriter, resolver, and type",
                //                 uri(PROCESSOR), "machine execution engine",
                //                 uri(MEMORY), "machine spatial memory include execution frame stack",
                //                 uri(NETWORK), "machine cluster"),
                //         "the default machine template used to generate machines")),
                uri(REWRITE), lst(
                        // A monoidic reducer is ALREADY a gather: isGather() is "the dom coefficient is
                        // unbounded", and sum's dom is #{*}. resolve() therefore mints a barrier monad for it and
                        // the processor already parks and accumulates into it. So this rule does not create a
                        // rendezvous -- it marks where the rendezvous must be WIDENED to the machine's peers.
                        docWrap(InstSet.Helper.rewriter(MACH_REWRITE_TID.extend("sum_gather"),
                                        code -> {
                                            // NO PEERS, NO DISTRIBUTION. The peers are the declared roster IN SPACE
                                            // (/sys/peer): one entry per authority this machine may rely on. Space is the
                                            // mechanism already proven (the barrier mailbox), so the topology is read from
                                            // it -- the transport layer can arrive later without moving the peer source.
                                            // With an empty roster there is nothing to gather FROM, and the rule must not
                                            // fire at all: measured, an inserted gather changes WHEN a coefficient is
                                            // complete, which turns a surviving coefficient into a premature reduction.
                                            // THE PEER COUNT IS THE BARRIER COUNT. The home's branch lists one barrier per
                                            // peer, so declaring a cluster is writing a key per machine -- and it is the same
                                            // rec the run leaves its reports in, so the topology IS the mailbox set rather than
                                            // something kept beside it. Adding a peer is adding a key.
                                            final Obj roster = Machine.read(COMPUTE);
                                            final Obj home = roster.isRec() ? roster.asRec().at(uri(MACH_HOME)) : noobj();
                                            final Obj boxes = home.isRec() ? home.asRec().at(uri(BARRIER)) : noobj();
                                            if (!boxes.isRec() || boxes.asRec().jvm().isEmpty())
                                                return code;
                                            final List<Inst> insts = code.insts();
                                            if (insts.stream().noneMatch(i -> i.tid().basePath().equals(SUM_INST_TID)))
                                                return code;
                                            // A WORKER FORM IS TERMINAL. It computes locally and reports, so distributing it
                                            // again would put a gather in front of its OWN reduction and have it wait for a
                                            // report only it could send -- a deadlock, and one that only shows on the shard
                                            // (the home is immune because its compiled form already has a gather before the
                                            // reducer, which is what the idempotence check below tests). A shipped worker ends
                                            // by reporting to a mailbox, and that is what marks it.
                                            final Inst last = insts.getLast();
                                            if (last.tid().basePath().equals(TO_INST_TID)
                                                    && last.arg(0).toString().contains("/barrier/"))
                                                return code;
                                            // IDEMPOTENT, and this is load-bearing: FixPointRewriter re-runs every rule
                                            // until the code stops changing, so an unguarded insert adds a gather on each
                                            // pass. A gather already sitting immediately before the reducer means this
                                            // rule has fired.
                                            for (int i = 1; i < insts.size(); i++)
                                                if (insts.get(i).tid().basePath().equals(SUM_INST_TID)
                                                        && insts.get(i - 1).tid().basePath().equals(BARRIER_INST_TID))
                                                    return code;
                                            // the declared peers, in a stable order so the compiled form is reproducible
                                            // the barriers ARE the peers, so there is no home to filter out of them: the home
                                            // is the branch they hang under, not one of them
                                            final List<fURI> peers = boxes.asRec().jvm().keySet().stream()
                                                    .filter(Obj::isUri)
                                                    .map(Obj::uriValue)
                                                    .sorted(Comparator.comparing(fURI::toString))
                                                    .toList();
                                            // ---- THE WORKER FORM, shipped to each peer's recv box ----
                                            // The shard's code is the SAME prefix (it computes on its own data), then its own
                                            // local reduction, then a report to THE MAILBOX THE HOME WAITS ON. The mailbox is
                                            // minted from the identical expression the barriers above use, so the home's
                                            // barrier(...) and the worker's to(...) cannot drift -- hand-writing both ends is
                                            // what broke before.
                                            //
                                            // Shipping it is a SPACE WRITE, the mechanism already proven, and in a multi-JVM
                                            // deployment the same write simply lands in a tbleSpace (Postgres) instead of
                                            // memory: the mailbox stays a space, only its type changes.
                                            final int sumAt = java.util.stream.IntStream.range(0, insts.size())
                                                    .filter(i -> insts.get(i).tid().basePath().equals(SUM_INST_TID))
                                                    .findFirst().orElse(-1);
                                            if (sumAt < 0)
                                                return code;
                                            // THE DATA IS DISTRIBUTED TOO. start(...) carries it and is isInitial -- which is
                                            // what makes each machine mint its own monads from it. So the start is SLICED
                                            // across the machines and each form carries its own slice: the shard needs no
                                            // start handed to it from outside, it arrives with its own data.
                                            final int startAt = java.util.stream.IntStream.range(0, insts.size())
                                                    .filter(i -> insts.get(i).tid().basePath().equals(START_INST_TID))
                                                    .findFirst().orElse(-1);
                                            // elements() is a lst's MEMBERS; stream() is a coefficient's. The start's arg can
                                            // be either shape, and getting it wrong is silent -- stream() on a lst yields the
                                            // lst itself as one element, which would slice the data into a single piece.
                                            final List<Obj> data;
                                            if (startAt < 0)
                                                data = List.of();
                                            else {
                                                final Obj raw = insts.get(startAt).arg(0);
                                                data = raw.isLst() ? raw.asLst().elements().toList() : raw.stream().toList();
                                            }
                                            // a contiguous slice per machine, the home taking the first
                                            final int machines = peers.size() + 1;
                                            final int per = data.isEmpty() ? 0
                                                    : Math.max(1, (data.size() + machines - 1) / machines);
                                            final fURI homeBoxes = COMPUTE.extend(MACH_HOME).extend("barrier");
                                            for (int p = 0; p < peers.size(); p++) {
                                                final fURI peer = peers.get(p);
                                                final List<Inst> worker = new ArrayList<>(insts.size() + 2);
                                                if (startAt >= 0)
                                                    worker.add(instB(START_INST_TID, lst(objs(sliceOf(data, p + 1, machines, per)))));
                                                worker.addAll(insts.subList(startAt + 1, sumAt));
                                                worker.add(insts.get(sumAt));   // its own partial: for a monoid, a partial is a whole
                                                // reports to THE HOME'S MAILBOX FOR IT -- the one the home's gather waits on
                                                worker.add(instB(TO_INST_TID, lst(uri(homeBoxes.extend(peer.name())))));
                                                // and the code is shipped to the PEER'S OWN INBOX, which is named by the peer
                                                Machine.write(COMPUTE.extend(peer.name()).extend("recv"),
                                                        MCode.of(worker));
                                            }
                                            final List<Inst> out = new ArrayList<>(insts.size() + peers.size());
                                            boolean inserted = false;
                                            for (int at = 0; at < insts.size(); at++) {
                                                final Inst inst = insts.get(at);
                                                if (at == startAt) {
                                                    // the home keeps its own slice of the data, in place of the whole
                                                    out.add(instB(START_INST_TID, lst(objs(sliceOf(data, 0, machines, per)))));
                                                    continue;
                                                }
                                                if (!inserted && inst.tid().basePath().equals(SUM_INST_TID)) {
                                                    // ONE barrier(uri) PER PEER, which is what barrier(uri::T) is for: each
                                                    // waits on that peer's mailbox, and consecutive gathers chain (the loop
                                                    // appends one gather's result into the next), so the peer partials land
                                                    // in the same place the local coefficient does and the reducer sees one
                                                    // complete coefficient.
                                                    //
                                                    // The address is minted HERE, once, from the peer's roster entry and the
                                                    // reducer's own vid -- so the shard's shipped to(<same address>) cannot
                                                    // drift from what this barrier waits on. That drift is exactly the bug we
                                                    // hit writing these addresses by hand.
                                                    // THE MAILBOX IS A SPACE PATH, not the peer's transport key: a
                                                    // barrier writes and reads a SPACE address (it subscribes at the mailbox and
                                                    // reads what has accumulated), so an authority like ws://localhost:8555
                                                    // resolves to nothing and the barrier would wait on a mailbox no write can
                                                    // reach. /sys/peer is already a real space -- the roster lives in it -- so
                                                    // the mailboxes live under it, one per peer, named by the peer's place in
                                                    // the roster. Single-JVM construction: 'a' is this machine's name.
                                                    for (final fURI peer : peers)
                                                        out.add(instB(BARRIER_INST_TID, lst(uri(
                                                                COMPUTE.extend(MACH_HOME).extend("barrier").extend(peer.name())))));
                                                    inserted = true;
                                                }
                                                out.add(inst);
                                            }
                                            return inserted ? code.selfJVM(out).asCode() : code;
                                        }),
                                "gives a monoidic reducer its gather point: \\(\\mathrm{sum} \\leadsto \\mathrm{barrier} \\cdot \\mathrm{sum}\\)"))));
        docWrap(this, "the reflective instruction set of metatron featuring process, monad, and code introspection");
        super.setup();

    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>();
    }
}
