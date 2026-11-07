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

package studio.phaseshift.metatron.isa.m.mach;

import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mTypes;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.MachinePerspective;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.BasicCompiler;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.parser.mtronParser;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.*;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.IdentityRewriter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.PlanRewriter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.typer.BasicTyper;
import studio.phaseshift.metatron.isa.m.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.m.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.m.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.m.mach.type.network.NetworkSupervisor;
import studio.phaseshift.metatron.isa.m.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.m.mach.type.processor.BasicSwarmComputer;
import studio.phaseshift.metatron.isa.m.mach.type.processor.BasicSwarmProcessor;
import studio.phaseshift.metatron.isa.m.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.util.MTronException;

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
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjFactory.M_FACTORY_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/mach")
public class machInstSet extends AbstractInstSet {
    public static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");
    public static final fURI ROOT_MACHINE_VID = f("/.");
    public static final fURI MACH_MONAD_TID = MACH_ISA_TID.extend("monad");
    public static final fURI MACH_INST_TID = MACH_ISA_TID.extend("inst");
    /**
     * The frame inst — {@code push(<uri>)}. push mints a MACHINE, which is a frame: it has its own vid and its own
     * space, so a relative uri descends within this JVM. A uri carrying a HOST names a PEER — a network member with
     * its own identity and lifetime, dialed rather than descended — so {@link Machine#push(fURI)} refuses it, and code
     * reaches another instance by being shipped there in a plan ({@code law_shard}).
     */
    public static final fURI PUSH_INST_TID = MACH_INST_TID.extend("push");

    /**
     * The other two frame-choosing instructions: {@code pop()} leaves the frame it is handed, {@code move(<uri>)}
     * chooses the frame the NEXT code runs in.
     * <p>
     * All three SPEAK ONE ADDRESS DIALECT, and it is the {@code ..} motif: {@code ~} is the frame you stand in,
     * {@code ~/a} (or plain {@code a}) is its child, {@code ..} is its parent, {@code ../b} is the sibling
     * {@code b}, and {@code ../../..} walks as far up as asked. The arithmetic lives in ONE place —
     * {@link Machine.Helper#machineAddress} — so the three cannot drift; they differ only in what they DO with the
     * address they are handed. {@code push} MINTS the address, {@code move} WALKS to it without closing anything,
     * {@code pop} DISCARDS it.
     * <p>
     * MINTS the address, and that is license to cross as well as descend: {@code push(<t>)} resolves {@code t} and
     * mints its LEAF from the frame at {@code t}'s PARENT — so {@code push(<../b>) == move(<..>).push(<b>)}, and a
     * sibling is minted from the shared parent rather than from beside it. An address that names a PROCESS
     * boundary is not split that way: its authority IS its identity, so it is handed to {@code push} whole, which
     * refuses it (see {@link Machine#push(fURI)}).
     */
    public static final fURI POP_INST_TID = MACH_INST_TID.extend("pop");
    public static final fURI MOVE_INST_TID = MACH_INST_TID.extend("move");
    public static final fURI STAY_INST_TID = MACH_INST_TID.extend("stay");
    public static final fURI CUT_INST_TID = MACH_INST_TID.extend("cut");
    public static final fURI SPAWN_INST_TID = MACH_INST_TID.extend("spawn");

    /**
     * The round-trip reply — {@code reply(<payload>, <tag>)}: evaluate a shipped payload and address its
     * answer by the trip the answer belongs to. It is the ROOT inst of a shipped frame, never a tail inst, because
     * a {@code noobj} kills the monad at the next inst whose domain does not admit zero ({@code SwarmProcessor}),
     * so anything appended after the payload's producer would never run. As the frame's root the reply is always
     * applied, and the payload is evaluated inside its body — where an absent answer is an ordinary return value
     * the reply can address.
     * <p>
     * The tag is an ordinary value argument (not the vid of anything), so what it addresses is the ANSWER: a value
     * leaves with the tag on it, and nothing leaves as {@link #NOOBJ_SENTINEL} — the mark that says "the answer was
     * empty, and this is the trip it belonged to".
     */
    public static final fURI REPLY_INST_TID = MACH_INST_TID.extend("reply");

    /**
     * THE MARK AN ABSENT ANSWER TRAVELS UNDER. {@code noobj} cannot be given a vid: it is the coefficient-zero
     * singleton, so {@code Obj.isNoObj()} is true for it and the serializer's root writer flattens every such obj to
     * the literal {@code noobj} before a vid could be rendered ({@code ObjSerializer.write}). A round trip whose
     * answer is nothing therefore has nothing that can carry its correlation tag, and the waiting slot is filled by
     * arrival order when it is lucky and by a deadline when it is not.
     * <p>
     * The sentinel is the smallest value that says the same thing AND can carry the tag: a uri at coefficient one,
     * which the writer renders with its vid, and whose spelling is a reserved path so no keyword, coefficient or vid
     * postfix can be mistaken for part of it. {@link #REPLY_INST_TID} mints it on the far side; the socket's
     * round-trip router ({@code WebSocketObj.router}) is the only reader, and it fills the waiting slot with
     * {@code noobj}. The spelling is reserved on the wire, so a program that deliberately answers this uri is read
     * as nothing.
     */
    public static final fURI NOOBJ_SENTINEL = f("/wire/noobj");

    /**
     * Whether an inbound obj is the absent-answer mark (see {@link #NOOBJ_SENTINEL}).
     */
    public static boolean isNoobjSentinel(final Obj obj) {
        return obj.isUri() && obj.uriValue().equals(NOOBJ_SENTINEL);
    }

    /**
     * The one spelling of "up", so {@code move()} and {@code move(<..>)} cannot come to mean different things.
     */
    private static final fURI UP = f("..");


    public static final fURI MACH_THREAD_TID = MACH_ISA_TID.extend("thread");
    public static final fURI MACH_VIRTUAL_THREAD_TID = MACH_THREAD_TID.extend("virtual");
    public static final fURI MACH_CORE_THREAD_TID = MACH_THREAD_TID.extend("core");
    public static final fURI FACTORY_TID = MACH_ISA_TID.extend("factory");
    public static final fURI THREAD_EXECUTOR_TID = MACH_ISA_TID.extend("thread_executor");
    /// /// PEERS AND THE NETWORK /// ///
    /**
     * THE URI REFINEMENTS the peer and listener types are built from. They refine {@code uri::T}, so their tids extend
     * URI_TID (a type NAME), never MACH_ISA_TID: the machine ISA owns them because only its component types use them,
     * not because they are machine TYPES.
     */
    public static final fURI AUTHORITY_TID = URI_TID.extend("authority");
    public static final fURI ORIGIN_TID = URI_TID.extend("origin");
    public static final fURI PATH_ENCODING_TID = URI_TID.extend("path_encoding");
    public static Type AUTHORITY_TYPE;
    public static Type ORIGIN_TYPE;
    public static Type PATH_ENCODING_TYPE;
    public static final fURI PEER_TID = MACH_ISA_TID.extend("peer");

    public static Type PEER_TYPE;
    public static final fURI LISTENER_TID = MACH_ISA_TID.extend(LISTENER);
    public static Type LISTENER_TYPE;
    public static final fURI NETWORK_SUPERVISOR_TID = MACH_ISA_TID.extend("network_supervisor");
    public static Type NETWORK_SUPERVISOR_TYPE;
    public static final fURI ID_CLIENT_TID = MACH_ISA_TID.extend("id_client");
    public static Type ID_CLIENT_TYPE;

    public static final Type FACTORY_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(FACTORY_TID)
            .create();

    /// //////////////////////////////////////////////////////////////////////
    public static Type THREAD_EXECUTOR_TYPE;
    public static final Type MACH_MONAD_TYPE = TypeBuilder.build().tid(LST_TID).vid(MACH_MONAD_TID).create();
    public static Type MACH_VIRTUAL_THREAD_TYPE;
    // Common thread predicate shared by core and virtual
    public static final Type MACH_THREAD_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(MACH_THREAD_TID)
            .isaPredicate(
                    rec(uri(CODE), T(ALL),
                            uri(SOURCE).maybe(), URI_TYPE,
                            uri(TIME).maybe(), DATETIME_TYPE,
                            uri(RUNTIME).maybe(), TIME_TYPE,
                            uri(YIELD).maybe(), T(MACH_THREAD_TID),
                            uri(LOOP).maybe(), TIME_TYPE,
                            uri(TIMEOUT).maybe(), TIME_TYPE,
                            uri(STATE).maybe().asUri(), union_(uri(STOP), uri(RUN), uri(PAUSE)).tryToInst(),
                            uri(RESULT).maybe(), T(ALL.maybeSome())), Map.of(
                            uri(CODE), "the thread's executing code",
                            uri(TIME).maybe(), "the datetime::T when the thread was started",
                            uri(RUNTIME).maybe(), "computes the thread's current running time::T",
                            uri(LOOP).maybe(), "delay to repeat code evaluation (default is evaluate once)",
                            uri(TIMEOUT).maybe(), "how long a read of rhs waits for the result (default is 5 seconds)",
                            uri(STATE).maybe(), "current state of the thread",
                            uri(RESULT).maybe(), "the last result produced by the thread"))
            .desc("the thread base type")
            .create();
    public static Type MACH_CORE_THREAD_TYPE;
    public static Type MACH_MACHINE_TYPE;

    public static final fURI MACH_MACHINE_COMPONENT_TID = MACH_ISA_TID.extend(COMPONENT);
    // the processor family — the architecture contract (apply(code)->computer) and the concrete swarm strategy
    public static final fURI MACH_PROCESSOR_TID = MACH_MACHINE_COMPONENT_TID.extend(PROCESSOR);
    public static final fURI MACH_SWARM_PROCESSOR_TID = MACH_PROCESSOR_TID.extend("swarm");
    public static Type MACH_PROCESSOR_TYPE;
    public static Type MACH_SWARM_PROCESSOR_TYPE;
    // the computer family — the computing instance the architecture mints (a watchdog thread + workers + a cache)
    public static final fURI MACH_COMPUTER_TID = MACH_PROCESSOR_TID.extend(COMPUTER);
    public static final fURI MACH_SWARM_COMPUTER_TID = MACH_COMPUTER_TID.extend("swarm");
    public static Type MACH_COMPUTER_TYPE;
    public static Type MACH_SWARM_COMPUTER_TYPE;
    // the compiler family — structural apply(source)->code contract, then the four stage families it composes
    public static final fURI MACH_COMPILER_TID = MACH_MACHINE_COMPONENT_TID.extend(COMPILER);
    public static Type MACH_COMPILER_TYPE;
    // the parser family — structural parse(source)->obj contract, the first stage and the one that
    // runs before there is any code: it is what source text has instead of a type
    public static final fURI MACH_PARSER_TID = MACH_MACHINE_COMPONENT_TID.extend(PARSER);
    public static final fURI MACH_MTRON_PARSER_TID = MACH_PARSER_TID.extend("mtron_parser");
    public static Type MACH_PARSER_TYPE;
    public static Type MACH_MTRON_PARSER_TYPE;
    // the rewriter family — structural rewrite(code)->code contract, concrete fixpoint strategy
    public static final fURI MACH_REWRITER_TID = MACH_MACHINE_COMPONENT_TID.extend(REWRITER);
    public static final fURI MACH_FIXPOINT_REWRITER_TID = MACH_REWRITER_TID.extend("fixpoint_rewriter");
    public static final fURI MACH_IDENTITY_REWRITER_TID = MACH_REWRITER_TID.extend("identity_rewriter");
    public static final fURI MACH_PLAN_REWRITER_TID = MACH_REWRITER_TID.extend("plan_rewriter");
    public static Type MACH_REWRITER_TYPE;
    public static Type MACH_FIXPOINT_REWRITER_TYPE;
    public static Type MACH_IDENTITY_REWRITER_TYPE;
    public static Type MACH_PLAN_REWRITER_TYPE;
    // the plan rewriter's rule format — a rewrite rule as a first-class value, the morphism path → path
    public static final fURI MACH_PLAN_TID = MACH_ISA_TID.extend("plan");
    public static Type MACH_PLAN_TYPE;
    // the resolver family — structural resolve(code)->code contract, concrete scoring + firstfind strategies
    public static final fURI MACH_RESOLVER_TID = MACH_MACHINE_COMPONENT_TID.extend(RESOLVER);
    public static final fURI MACH_IDENTITY_RESOLVER_TID = MACH_RESOLVER_TID.extend("identity_resolver");
    public static final fURI MACH_SCORING_RESOLVER_TID = MACH_RESOLVER_TID.extend("scoring_resolver");
    public static final fURI MACH_FIRSTFIND_RESOLVER_TID = MACH_RESOLVER_TID.extend("firstfind_resolver");
    public static Type MACH_RESOLVER_TYPE;
    public static Type MACH_IDENTITY_RESOLVER_TYPE;
    public static Type MACH_SCORING_RESOLVER_TYPE;
    public static Type MACH_FIRSTFIND_RESOLVER_TYPE;
    // the selector + binder families — the resolver's two collaborators: pick the candidates, then
    // make one of them concrete. Both are strategies the resolver composes, not stages of their own.
    public static final fURI MACH_SELECTOR_TID = MACH_MACHINE_COMPONENT_TID.extend(SELECTOR);
    public static final fURI MACH_SPECIFICITY_SELECTOR_TID = MACH_SELECTOR_TID.extend("specificity_selector");
    public static final fURI MACH_FIRSTFIND_SELECTOR_TID = MACH_SELECTOR_TID.extend("firstfind_selector");
    public static final fURI MACH_BINDER_TID = MACH_MACHINE_COMPONENT_TID.extend(BINDER);
    public static final fURI MACH_GENERIC_BINDER_TID = MACH_BINDER_TID.extend("generic_binder");
    public static Type MACH_SELECTOR_TYPE;
    public static Type MACH_SPECIFICITY_SELECTOR_TYPE;
    public static Type MACH_FIRSTFIND_SELECTOR_TYPE;
    public static Type MACH_BINDER_TYPE;
    public static Type MACH_GENERIC_BINDER_TYPE;
    // the typer family — structural typer
    public static final fURI MACH_TYPER_TID = MACH_MACHINE_COMPONENT_TID.extend(TYPER);
    public static Type MACH_TYPER_TYPE;
    // the tracer family — process tracer
    public static final fURI MACH_TRACER_TID = MACH_MACHINE_COMPONENT_TID.extend(TRACER);
    public static Type MACH_TRACER_TYPE;
    // memory — the machine's address space: relative bindings plus the index of absolute spaces
    public static final fURI MACH_MEMORY_TID = MACH_MACHINE_COMPONENT_TID.extend(MEMORY);
    public static Type MACH_MEMORY_TYPE;
    // network — the machines a machine knows (the roster's addresses) and the peers it has dialed (their clients)
    public static final fURI MACH_NETWORK_TID = MACH_MACHINE_COMPONENT_TID.extend(NETWORK);
    public static Type MACH_NETWORK_TYPE;
    public static Type MACH_MACHINE_COMPONENT_TYPE;
    public static final fURI MACH_DISCOVERY_TID = MACH_NETWORK_TID.extend(DISCOVERY);
    public static Type MACH_DISCOVERY_TYPE;


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
                        MACH_MACHINE_COMPONENT_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(MACH_MACHINE_COMPONENT_TID)
                                .isaPredicate(rec(uri(MACHINE).maybe().asUri(), T(MACH_MACHINE_TID)))
                                .create(),
                        // machine::T — the container: an ISA + a compiler + a processor
                        // the processor family — structural apply(code)->obj contract, nominal monad marker, concrete swarm strategy
                        MACH_PROCESSOR_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_PROCESSOR_TID)
                                .isaPredicate(rec(
                                        uri(STATE).maybe().asUri(), union_(uri(STOP), uri(RUN), uri(PAUSE)).tryToInst(),
                                        uri(RESULT).maybe(), T(ALL.maybeSome())))
                                .create(),
                        MACH_SWARM_PROCESSOR_TYPE = TypeBuilder.build()
                                .tid(MACH_PROCESSOR_TID)
                                .vid(MACH_SWARM_PROCESSOR_TID)
                                .constructor(machine -> BasicSwarmProcessor.processor(machine.jvm(), machine.tid(), machine.vid()))
                                .desc("the swarm architecture: mints a swarm computer per apply(code), which schedules independently executing monads across the code inst chain; barriers synchronize them, and the objects of the halted monads are the result")
                                .create(),
                        // the computer family — the computing instance the architecture mints
                        MACH_COMPUTER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_COMPUTER_TID)
                                .isaPredicate(rec(uri(CODE), ALL_TYPE, uri(CACHE).maybe().asUri(), REC_TYPE))
                                .create(),
                        MACH_SWARM_COMPUTER_TYPE = TypeBuilder.build()
                                .tid(MACH_COMPUTER_TID)
                                .vid(MACH_SWARM_COMPUTER_TID)
                                .isaPredicate(rec(uri(CACHE).maybe().asUri(),
                                        rec(uri(HALTED).maybe().asUri(), T(ALL.maybeSome()),
                                                uri(RUN).maybe(), T(ALL.maybeSome()),
                                                uri(BARRIER).maybe(), LST_TYPE)))
                                /* Map.of(uri(CACHE).maybe(), "three monad streams contain the current state of the computation",
                                         uri(f(CACHE).extend(RUN)).maybe(), "monads still actively computing (inst x obj)",
                                         uri(f(CACHE).extend(HALTED)).maybe(), "monads denote the result (inst0 x obj)",
                                         uri(f(CACHE).extend(BARRIER)).maybe(), "a lst[monad] of the ordered barriers in the code"))*/
                                .constructor(arg -> new BasicSwarmComputer(arg.asRec().jvm(), MACH_SWARM_COMPUTER_TID, arg.vid()))
                                .create(),
                        // the compiler family — structural apply(source)->code contract holding its four stages
                        MACH_COMPILER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_COMPILER_TID, "the compiler contract of machine::T — a machine's lowering axis, source to code::T, composed of four atomic overridable stages: the stages are the vocabulary and these rec entries are the wiring, all optional and each defaulting when absent, so a compiler is its composition rather than an algorithm. compiler.apply runs the default schedule — parse → rewrite → resolve → type — each stage handed the whole of what the last produced and returning the same, and the processor is the sibling that then runs what the compiler produced. Only the parse stage accepts anything but code — source text, or an already-parsed obj it lifts as it stands instead of re-reading; every stage returns code::T. A compiler is constructed from its stages, and a bare compiler::[=>] is the default compiler.")
                                .isaPredicate(rec(
                                        uri(PARSER).maybe().asUri(), T(MACH_PARSER_TID),
                                        uri(REWRITER).maybe().asUri(), T(MACH_REWRITER_TID),
                                        uri(RESOLVER).maybe().asUri(), T(MACH_RESOLVER_TID),
                                        uri(TYPER).maybe().asUri(), T(MACH_TYPER_TID)), Map.of(
                                        uri(PARSER).maybe(), "the first stage — source text to obj, and the identity for anything that is not a str, so a compiler accepts a program's text or its parsed obj alike: mtron_parser::T by default",
                                        uri(REWRITER).maybe(), "the second stage — the code::T to code::T lowering that runs before any type is resolved: fixpoint_rewriter::T by default (every registered rewrite rule to a stable fixpoint), identity_rewriter::T for a compiler that wants no rewrites",
                                        uri(RESOLVER).maybe(), "the third stage — the walk that threads each inst's range into the next inst's domain, delegating per inst to a selector (which candidates) and a binder (make one concrete): scoring_resolver::T by default",
                                        uri(TYPER).maybe(), "the fourth stage — the per-machine runtime type assertions (inst_dom, inst_rng, type_pred), carried in the machine's {@code /compiler/typer/check}, not a global registry"))
                                .constructor(arg -> new BasicCompiler(BasicCompiler.stages(arg.asRec().jvm()), MACH_COMPILER_TID, arg.vid()))
                                .example("compiler::[rewriter=>fixpoint_rewriter::[max=>3],resolver=>scoring_resolver::[=>]]   [-- wire the stages that matter; parser and typer default --]", "compiler::[parser=>mtron_parser::[=>]]   [-- name the read stage explicitly --]", "compiler::[=>]   [-- the default compiler: mtron parse, fixpoint, scoring, typer --]")
                                .create(),
                        // the parser family — structural contract, one concrete language (mtron itself)
                        MACH_PARSER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_PARSER_TID, "the parse contract of compiler::T — the first stage, the only one that runs before there is any code, and the seam where a language is hosted: any language can be parsed as long as it generates code::T. Because the type system supports both object-oriented and functional designs and the language is Turing-complete, most languages can map onto mtron code::T.")
                                .create(),
                        MACH_MTRON_PARSER_TYPE = TypeBuilder.build()
                                .tid(MACH_PARSER_TID)
                                .vid(MACH_MTRON_PARSER_TID, "at the parse stage of compiler::T, mtron_parser::T reads metatron's own language through the mtron serializer, so what it accepts is exactly what a console line, a source file and a test row accept — the one parser metatron ships, and the compiler's default")
                                .constructor(arg -> new mtronParser(arg.asRec().jvm(), MACH_MTRON_PARSER_TID, arg.vid()))
                                .example("mtron_parser::[=>]   [-- the default parse stage of compiler::T --]")
                                .create(),
                        // the rewriter family — structural contract, concrete fixpoint strategy
                        MACH_REWRITER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_REWRITER_TID, "the rewrite contract of compiler::T — a lowering from code::T to code::T that runs before any type is resolved, so it decides what the instruction chain is: rules see generic dom/rng, and a rule-driven collapse, a native pushdown, or a distributed reduce's gather point all enter the compiled form here.")
                                .create(),
                        MACH_IDENTITY_REWRITER_TYPE = TypeBuilder.build()
                                .tid(MACH_REWRITER_TID)
                                .vid(MACH_IDENTITY_REWRITER_TID, "at the rewrite stage of compiler::T, identity_rewriter::T is the no-op rewriter: it returns the code unchanged — for a compiler that wants no rewrites at all, since fixpoint_rewriter::T is the default")
                                .constructor(arg -> IdentityRewriter.single())
                                .example("identity_rewriter::[=>]   [-- the compiler's default rewrite stage --]")
                                .create(),
                        MACH_FIXPOINT_REWRITER_TYPE = TypeBuilder.build()
                                .tid(MACH_REWRITER_TID)
                                .vid(MACH_FIXPOINT_REWRITER_TID, "at the rewrite stage of compiler::T, fixpoint_rewriter::T applies every rewrite rule registered by every instset, in turn, over the whole code, and repeats the pass until the code hash is unchanged for max consecutive passes, so a rule re-runs on every pass and must be idempotent")
                                .isaPredicate(rec(uri(MAX).maybe().asUri(), isa_(INT_TYPE).else_(jnt(2))), Map.of(uri(MAX).maybe(), "the convergence window: the number of consecutive stable passes the code must show before the fixpoint is accepted (default 2)"))
                                .constructor(arg -> new FixPointRewriter(arg.asRec().jvm(), MACH_FIXPOINT_REWRITER_TID, arg.vid()))
                                .example("fixpoint_rewriter::[max=>3]   [-- accept the fixpoint after three stable passes --]")
                                .create(),
                        MACH_PLAN_REWRITER_TYPE = TypeBuilder.build()
                                .tid(MACH_FIXPOINT_REWRITER_TID)
                                .vid(MACH_PLAN_REWRITER_TID, "at the rewrite stage of compiler::T, plan_rewriter::T is a fixpoint_rewriter::T whose rules are the law-driven plans (plan::T) rather than the rewrites the machine's instsets register: each plan reads the algebra the instructions and types declare (?decq) — a theory role, a process law, or a form and the laws its combiner needs — so a declared algebra is optimized, and over live peers distributed (law_shard), with no rule naming an instruction. Only the plans run")
                                .constructor(arg -> new PlanRewriter(arg.asRec().jvm(), MACH_PLAN_REWRITER_TID, arg.vid()))
                                .example("plan_rewriter::[=>]   [-- rewrite with the law-driven plans alone --]")
                                .create(),
                        // the plan rewriter's rule format
                        MACH_PLAN_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(MACH_PLAN_TID, "a rewrite rule as a first-class plan — the morphism of the rewrite category (path → path)")
                                .isaPredicate(rec(
                                        uri(NAME), URI_TYPE,
                                        uri(RESULT).maybe().asUri(), ALL_TYPE, // for lambda based rewrites
                                        uri(CUT).maybe().asUri(), rec(
                                                uri(FORM).maybe().asUri(), URI_TYPE,
                                                uri(LAW).maybe().asUri(), lst(mTypes.LAW_TYPE),
                                                uri(ROLE).maybe().asUri(), URI_TYPE),
                                        uri(WHERE).maybe(), lst(rec(
                                                // the derived token is OPTIONAL now: a rec stated in the axes alone is
                                                // a plan::T (C9), and the legacy encoding is still read for one cycle
                                                uri(SHAPE).maybe().asUri(), URI_TYPE,
                                                uri(TARGET).maybe(), ALL_TYPE,
                                                // the decision's three axes (§1 of the qparam plan): the place set
                                                // (a uri, a lst of designators, or a weighted rec), the depth (a counted
                                                // int, or [height, arity] for the arities a coefficient cannot spell), and
                                                // the partition (a counted uri). ALL_TYPE because each axis decides its
                                                // value's shape; the coefficient is ANY because a COUNTED object fails a
                                                // plain <#> — the coefficient IS the axis' value (arity, part count) and
                                                // the type's own coefficient is what a counted obj is tested against.
                                                // Pinned by DecisionModelTest.testTheAxesArePlanT.
                                                uri(PLACE).maybe(), ALL_TYPE.c(cInt.ANY()),
                                                uri(HOP).maybe(), ALL_TYPE.c(cInt.ANY()),
                                                uri(SPLIT).maybe(), ALL_TYPE.c(cInt.ANY()),
                                                uri(INST).maybe(), lst(URI_TYPE),
                                                uri(SPAN), lst(rec(
                                                        uri(SPAN), URI_TYPE,
                                                        uri(FLAGS).maybe(), lst(URI_TYPE))))).maybe()), Map.of(
                                        uri(NAME), "the plan's name — the rewrite rule it embodies",
                                        uri(CUT).maybe(), "the cut selector: the morphism classes (form + laws + theory) the plan anchors on",
                                        uri(WHERE).maybe(), "the distribution: target + span regions the plan ships, and the instructions its target must serve (inst, as a client's inst names them)",
                                        uri(RESULT).maybe(), "only for a rewrite with no declarative plan: the lambda that applies the rewrite to yield the rewritten path::T"))
                                .constructor(arg -> {
                                    final Rec plan = arg.isRec() && arg.asRec().has(uri(NAME)) ? arg.asRec() : rec(uri(NAME), arg);
                                    return plan.selfTID(MACH_PLAN_TID);
                                })
                                .create(),
                        // the resolver family — structural contract, concrete scoring + firstfind strategies (empty config)
                        MACH_RESOLVER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_RESOLVER_TID, "the resolution contract of compiler::T — the stage that turns a generic instruction chain into a typed one, and the compiler's widest-reaching stage: it threads each inst's range into the next inst's domain and, per inst, delegates to a selector (fetch and order the candidates) and a binder (make the chosen candidate concrete, or reject it), so which concrete inst a call becomes — and with it whether the inst is a filter, a gather/barrier, or an initial — is settled here, per element type. What it settles is what the processor then runs (a filter's {0,1} wing dropped for compile-time typing, an initial inst seeding the element type from its own argument, a cast's range rebound to the named type), and an inst it cannot resolve is left in place for runtime resolution — a semi-resolved code, not a failed one. Structural, not nominal: its two collaborators are rec fields, both optional and both defaulting, so a resolver is its walk plus whichever strategies it names. Its members are scoring_resolver::T (the default pair), firstfind_resolver::T (first match, no scoring) and identity_resolver::T (the no-op)")
                                .isaPredicate(rec(
                                        uri(SELECTOR).maybe().asUri(), T(MACH_SELECTOR_TID),
                                        uri(BINDER).maybe().asUri(), T(MACH_BINDER_TID)), Map.of(
                                        uri(SELECTOR).maybe(), "the strategy that decides which candidates a call may resolve to, and in what order — absent, the walk uses specificity_selector::T (score by signature specificity, take the first the binder can bind)",
                                        uri(BINDER).maybe(), "the strategy each candidate is handed to — absent, the walk uses generic_binder::T (bind generics to the lhs, gate on the lhs domain, resolve the call's arguments)"))
                                .example("scoring_resolver::[selector=>firstfind_selector::[=>]]   [-- a resolver::T: the same walk, first-match selection --]", "scoring_resolver::[=>]   [-- a resolver::T: both strategies default --]")
                                .create(),
                        MACH_IDENTITY_RESOLVER_TYPE = TypeBuilder.build()
                                .tid(MACH_RESOLVER_TID)
                                .vid(MACH_IDENTITY_RESOLVER_TID, "at the resolution stage of compiler::T, identity_resolver::T is the no-op resolver: it threads no type and resolves no instruction — it returns the code unchanged — for a compiler whose resolve stage is done elsewhere")
                                .constructor(arg -> IdentityResolver.single())
                                .example("identity_resolver::[=>]   [-- a resolve stage that does nothing --]")
                                .create(),
                        MACH_SCORING_RESOLVER_TYPE = TypeBuilder.build()
                                .tid(MACH_RESOLVER_TID)
                                .vid(MACH_SCORING_RESOLVER_TID, "at the resolution stage of compiler::T, scoring_resolver::T is the walk plus the default pair of collaborators: it threads each inst's range into the next inst's domain and, per inst, hands the call to its selector (specificity_selector::T by default) with its binder (generic_binder::T by default) — so which concrete inst a call becomes is settled here, per element type; wire a selector or binder entry to compose a different resolution")
                                .constructor(arg -> new ScoringResolver(arg.asRec().jvm(), MACH_SCORING_RESOLVER_TID, arg.vid()))
                                .example("scoring_resolver::[=>]   [-- the default resolution stage of compiler::T --]")
                                .create(),
                        MACH_FIRSTFIND_RESOLVER_TYPE = TypeBuilder.build()
                                .tid(MACH_RESOLVER_TID)
                                .vid(MACH_FIRSTFIND_RESOLVER_TID, "at the resolution stage of compiler::T, firstfind_resolver::T is the same walk wired to firstfind_selector::T — a call resolves to the first viable candidate in read order, with no specificity scoring; the pre-scoring behavior, kept as the A/B counterpart of scoring_resolver::T")
                                .constructor(arg -> {
                                    final Map<Obj, Obj> stages = mutableMap(uri(SELECTOR), FirstFindSelector.single(), uri(BINDER), GenericBinder.single());
                                    stages.putAll(arg.asRec().jvm());
                                    return new ScoringResolver(stages, MACH_FIRSTFIND_RESOLVER_TID, arg.vid());
                                })
                                .example("firstfind_resolver::[=>]   [-- first match wins, no scoring --]")
                                .create(),
                        // the binder family — the resolver's "make this candidate concrete" strategy
                        MACH_BINDER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_BINDER_TID, "the binding contract of resolver::T — the collaborator that turns one selected candidate into the instruction a call becomes, or rejects it: it binds the candidate's generics to the lhs, gates it on the lhs domain, and resolves its arguments (compiling nested code args), returning null when the candidate cannot be made concrete; because a candidate that fails to bind must not be selectable, binding gates selection rather than following it, which is why a binder is a strategy inside the resolve stage and never a stage of its own. The candidate arrives already shaped with the call's dom/rng hints — that shaping is the selector's rule.")
                                .create(),
                        MACH_GENERIC_BINDER_TYPE = TypeBuilder.build()
                                .tid(MACH_BINDER_TID)
                                .vid(MACH_GENERIC_BINDER_TID, "at the resolution stage of compiler::T, generic_binder::T is the binding algorithm: it binds the candidate's generics to the lhs, gates on the lhs domain, resolves the call's arguments, seeds an initial inst's range from its own argument, and copies the user's query maps — returning null at any step that rejects the candidate; the call's dom/rng hints (including a cast's named type) are applied by the selector before the candidate reaches it")
                                .constructor(arg -> new GenericBinder(arg.asRec().jvm(), MACH_GENERIC_BINDER_TID, arg.vid()))
                                .example("generic_binder::[=>]   [-- the default binder::T --]")
                                .create(),
                        // the selector family — the resolver's "which candidate" strategy
                        MACH_SELECTOR_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_SELECTOR_TID, "the candidate contract of resolver::T — the collaborator that fetches the instructions a call may resolve to, orders them by its own rule, shapes each one with the call's dom/rng hints, and returns the first its binder can make concrete: the ordering rule is the whole strategy, and it is where resolution strategy varies — specificity_selector::T scores by signature specificity, firstfind_selector::T takes the first viable candidate in read order; a selector that cannot resolve an instruction returns null and leaves it for runtime resolution rather than failing the code")
                                .create(),
                        MACH_SPECIFICITY_SELECTOR_TYPE = TypeBuilder.build()
                                .tid(MACH_SELECTOR_TID)
                                .vid(MACH_SPECIFICITY_SELECTOR_TID, "at the resolution stage of compiler::T, specificity_selector::T orders a call's candidates by how specific their signatures are and takes the first its binder can bind: a concrete domain, an exact domain base-path match, a non-base dom/rng, matching arguments, and the large bonus that makes an as() candidate whose range names the requested type win; the from/at and as() shortcuts live here, since they return a finished contract rather than a candidate and so bypass the binder. Two legacy hint forms are preserved here and named: a single candidate replaces the dom outright, while a scored field keeps the API's declared dom and takes the call's coefficient")
                                .constructor(arg -> new SpecificitySelector(arg.asRec().jvm(), MACH_SPECIFICITY_SELECTOR_TID, arg.vid()))
                                .example("specificity_selector::[=>]   [-- the default selector::T --]")
                                .create(),
                        MACH_FIRSTFIND_SELECTOR_TYPE = TypeBuilder.build()
                                .tid(MACH_SELECTOR_TID)
                                .vid(MACH_FIRSTFIND_SELECTOR_TID, "at the resolution stage of compiler::T, firstfind_selector::T takes the first viable candidate in read order, with no specificity scoring, so selection is order-dependent — the pre-scoring strategy, kept as the A/B counterpart of specificity_selector::T")
                                .constructor(arg -> new FirstFindSelector(arg.asRec().jvm(), MACH_FIRSTFIND_SELECTOR_TID, arg.vid()))
                                .example("firstfind_selector::[=>]   [-- first match wins, no scoring --]")
                                .create(),
                        // the typer family — structural contract, runtime type assertions (identity for now)
                        MACH_TYPER_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_TYPER_TID, """
                                        the check points where the type checker should be applied.
                                        the more stages that are active, the slower instructions evaluate.
                                        however, more active stages reduces potential for data corruption.
                                        typically use many stages when designing code and once stable,
                                        remove stages accordingly for increased performance.
                                        """)
                                // every slot is optional: a BasicTyper is checked against this type while its rec is
                                // still empty (MObj's constructor), and an absent point reads the boot default
                                .isaPredicate(rec(uri(Typer.CHECK).maybe().asUri(), rec(
                                        uri(Typer.INST_DOM.name()).maybe().asUri(), BOOL_TYPE,
                                        uri(Typer.INST_RNG.name()).maybe(), BOOL_TYPE,
                                        uri(Typer.TYPE_PRED.name()).maybe(), BOOL_TYPE,
                                        uri(Typer.CODE_RESOLVE.name()).maybe(), BOOL_TYPE,
                                        uri(Typer.OBJ_WRITE.name()).maybe(), BOOL_TYPE)), mutableMap(
                                        uri(Typer.CHECK), "the type-check points: inst_dom (lhs matches the instruction domain), inst_rng (rhs matches its range), type_pred (a value matches its type predicate), obj_write (a value matches its type on a space write), code_resolve (only fully resolved code executes)"))

                                // the typer fills in its rec (check, verify), so it gets a copy, never the caller's
                                .constructor(arg -> new BasicTyper(mutableMap(arg.asRec().jvm()), MACH_TYPER_TID, arg.vid()))
                                .create(),
                        /**
                         * {@code typer::T}'s state: a {@code check} field holding one bool flag per type-check point. Grouping the points
                         * under {@code check} leaves the typer free to grow more orthogonal features beside it, and keeping each flag a
                         * {@code bool::T} leaves room for a finer realization (a level, a predicate) at the same key.
                         */
                        MACH_TRACER_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(TRACER_TYPE_TID)
                                .isaPredicate(rec(uri("stack"), rec(
                                        uri(Tracer.mtron_stack.name()).maybe().asUri(), BOOL_TYPE,
                                        uri(Tracer.java_stack.name()).maybe(), BOOL_TYPE)))
                                .constructor(stacks -> rec(uri("stack"), rec(
                                        uri(Tracer.mtron_stack.name()), stacks.asRec().at(uri(Tracer.mtron_stack.name())).orElse(BOOL_TRUE),
                                        uri(Tracer.java_stack.name()), stacks.asRec().at(uri(Tracer.java_stack.name())).orElse(BOOL_FALSE))))
                                .slots(mutableMap(
                                        uri(Tracer.mtron_stack.name()), "render mtron stack traces on uncaught fail::T",
                                        uri(Tracer.java_stack.name()), "render java stack traces on uncaught fail::T"))
                                .desc("""
                                        diagnostic stages for surfacing internal java state.
                                        useful for debugging native java issues that mtron instructions
                                        trigger but cannot introspect.
                                        """)
                                .create(),
                        // memory — a machine's address space: its relative bindings are the rec itself, and its
                        // absolute index is the `space` entry inside it, so both are reachable from mtron
                        MACH_MEMORY_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_MEMORY_TID)
                                .constructor(arg -> new BasicMemory(arg.asRec().jvm(), arg.vid()))
                                .create(),
                        // THE IDENTITY CLIENT — the identity element of the protocol-client family: its
                        // constructor is the dial (as every client's is) and its eval override evaluates the
                        // shipped code against the LOCAL machine, so a read through a loop: peer IS the
                        // local read of the localized address. It lives HERE rather than /m/web because the
                        // identity client needs no wire — it is what makes the self-similarity contract
                        // executable without a socket, and what keeps Helper.dial free of scheme branches:
                        // loop:'s default dialer is this type, resolved through the one dial path.
                        ID_CLIENT_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(ID_CLIENT_TID, """
                                        the identity client — the identity element of the protocol-client family:
                                        dialing loop://<name> constructs one, and its eval override runs the shipped code
                                        in the receiver's perspective (Machine.Helper.evalAt at the root), so the caller is
                                        left where it stood -- the loop counterpart of the ws client's ship(). The
                                        executable form of the self-similarity contract: a read through a loop peer equals
                                        the local read.
                                        """)
                                .isaPredicate(rec(
                                        uri(HOST).maybe().asUri(), URI_TYPE,
                                        uri(INST).maybe().asUri(), REC_TYPE,
                                        // optional here, installed by the constructor (mdns_discovery's rule)
                                        uri(STATUS).maybe().asUri(), BOOL_TYPE,
                                        uri(CLOSE).maybe().asUri(), INST_TYPE), Map.of(uri(HOST), "the dialed uri (identity: the local machine answers)",
                                        uri(INST), "the identity mini-instset: { eval, from, to } — the instructions a wire client serves; eval runs the shipped program at the RECEIVER's root and leaves the caller where it stood, from/to answer as the local read/write",
                                        uri(STATUS), "true: the far side is this machine, live as long as the client exists",
                                        uri(CLOSE), "nothing to hang up"))
                                .constructor(arg -> {
                                    // THE IDENTITY MINI-INSTSET — the ws client's instructions with "the far
                                    // side" being this machine, each the native with the identity body:
                                    //   code.eval(<uri>) runs the lhs code on the far side, which is HERE;
                                    //   from(<uri>) is the local read of the localized address;
                                    //   obj.to(<uri>) evaluates start(obj).to(</path>) here, so an unquoted
                                    //   program shipped to a loop peer runs, exactly as on a wire peer.
                                    // EVAL IS THE ONLY ONE THAT CAN MOVE THE CALLER, because it is the only one
                                    // that runs arbitrary code (the tail may contain `move`). It answers where
                                    // the ws client calls ship(): the program is run in the RECEIVER's
                                    // perspective (Machine.Helper.evalAt at the root) and the caller is left where
                                    // it stood -- the frame of reference is the far side's, and it goes back.
                                    final Obj client = rec(
                                            uri(HOST), arg.asRec().at(uri(HOST)),
                                            uri(INST), rec(
                                                    uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID,
                                                            (lhs, inst) -> Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()))),
                                                    uri(FROM_INST_TID), Network.Helper.override(FROM_INST_TID, (lhs, inst) -> Machine.read(inst.arg(0).uriValue().localize())),
                                                    uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> start_(lhs).to_(inst.arg(0).uriValue().localize().toUri()).tryToInst().apply(noobj()))),
                                            // the far side is this machine, so it is live as long as it exists
                                            uri(STATUS), Network.Helper.statusOf(() -> true),
                                            uri(CLOSE), instLambda((lhs, inst) -> noobj()));
                                    // indexing is a BACKGROUND behavior upon successful construction:
                                    // the client exists as an obj either way, the index merely observes
                                    Network.Helper.index(client);
                                    return client;
                                })
                                .example("id_client::[host=><loop://self>] [-- the identity peer: reads and writes answer locally --]")
                                .create(),
                        // the URI refinements the network types stand on -- a name for a service, a service, a key
                        AUTHORITY_TYPE = TypeBuilder.build()
                                .tid(URI_TID)
                                .vid(AUTHORITY_TID, "a uri containing host:port components with port being optional — a NAME for a service rather than a service")
                                .predicate((_, inst) -> {
                                    final fURI uri = inst.arg(0).uriValue();
                                    return (!uri.hasScheme() && uri.hasHost() && uri.c().isOne() && !uri.hasPoly() && uri.pathLength() == 0 && uri.qMap().isEmpty()) ?
                                            inst.arg(0) : uri().c(cInt.ZERO());
                                }).create(),
                        ORIGIN_TYPE = TypeBuilder.build()
                                .tid(URI_TID)
                                .vid(ORIGIN_TID, "a uri containing scheme://host:port components with port being optional — a SERVICE")
                                .predicate((_, inst) -> {
                                    final fURI uri = inst.arg(0).uriValue();
                                    return (uri.hasScheme() && uri.hasHost() && uri.c().isOne() && !uri.hasPoly() && uri.pathLength() == 0 && uri.qMap().isEmpty()) ?
                                            inst.arg(0) : uri().c(cInt.ZERO());
                                }).create(),
                        PATH_ENCODING_TYPE = TypeBuilder.build()
                                .tid(URI_TID)
                                .vid(PATH_ENCODING_TID, "a uri containing a dial's path encoding: scheme, host labels, port and route as path segments, with no authority of its own — the KEY of a peer or a listener")
                                .predicate((_, inst) -> {
                                    final fURI uri = inst.arg(0).uriValue();
                                    return (!uri.hasScheme() && !uri.hasHost() && uri.c().isOne() && !uri.hasPoly() && uri.pathLength() > 0 && uri.qMap().isEmpty()) ?
                                            inst.arg(0) : uri().c(cInt.ZERO());
                                }).create(),
                        // network — a machine's OWN reachable peers: never inherited, never shared with children
                        PEER_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(PEER_TID, """
                                        one metatron machine as the network records it — the value of a network peer
                                        connection. Absent `host` and `handler` is not a broken peer: it is a peer
                                        reference that crossed a wire, known here and not reachable from here.
                                        """)
                                // ONE TYPE, TWO HONEST STATES — and the difference is which fields are
                                // filled, never which type it is. An authority alone is a peer we know
                                // of but cannot reach from here, which is what a peer reference looks
                                // like after it has crossed a wire; an authority with a client is a
                                // peer the current machine talks to — with the instructions that client
                                // declares (its `inst`), never through a field of the peer's own.
                                .isaPredicate(rec(
                                        // THE ORIGIN, and the one field a peer cannot do without: a
                                        // roster lookup matches on this same service uri. The MAP KEY is
                                        // the dial's path encoding (`ws/localhost/8555/mtron`), never this
                                        // field -- a peer is one SURFACE, its origin is the service.
                                        uri(ORIGIN), ORIGIN_TYPE,
                                        // where to dial it from here, route included -- a place to reach,
                                        // not an authority: `ws://host:port/mtron` carries a path. Absent
                                        // for a peer reference that crossed a wire: known, not reachable.
                                        uri(HOST).maybe().asUri(), URI_TYPE,
                                        // the protocol CLIENT the dial constructed (a ws_client::T or kin,
                                        // typed ALL because this instruction set sits below /m/web). It IS the
                                        // connection: its `inst` is what the peer is talked to with, its
                                        // `status` whether it is live, and drop hangs it up. absent only for
                                        // a peer reference that crossed a wire.
                                        uri(CLIENT).maybe(), T(ALL)), Map.of(uri(ORIGIN), "the service this peer reaches, scheme://host:port — the map key is the dial's path encoding, this is the service, and the one field a peer cannot do without",
                                        uri(HOST), "where to dial it from here, route included: ws://host:port/mtron",
                                        uri(CLIENT), "the protocol client the dial constructed: its inst is what the peer is talked to with, its status whether it is live — held so drop can hang it up"))
                                .example("peer::[origin=><ws://192.168.1.42:8555>,host=><ws://192.168.1.42:8555/mtron>] [-- a peer the current machine can ship to --]", "peer::[origin=><ws://192.168.1.42:8555>] [-- known, and not reachable from here --]")
                                .create(),
                        // network — a machine's PRESENCE: the sockets it answers on. A port binds once per physical
                        // machine, so a socket is opened by one machine and inherited by every machine below it; each
                        // machine serving through it keeps its OWN routing table on it, in its own level.
                        LISTENER_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(LISTENER_TID, """
                                        a socket a machine answers on, and that machine's routes on it. The machine that opens
                                        a socket owns it, and its pop releases the port. Every machine below it inherits the
                                        socket and serves through it by keeping its own routing table here, which its own pop
                                        takes away. A connection is answered by the most specific route across every machine
                                        sharing the socket, and the peer it makes joins that machine's network.
                                        """)
                                .isaPredicate(rec(
                                        // THE KEY: the socket's service uri, scheme://host:port
                                        uri(ORIGIN), ORIGIN_TYPE,
                                        // THIS machine's routes on the socket, in the map idiom: path => target.
                                        // Absent rather than empty while the machine serves nothing through it.
                                        uri(ROUTE).maybe().asUri(), rec(URI_TYPE, T(ALL)),
                                        // the protocol SERVER holding the socket (a ws_server::T or kin, typed
                                        // ALL because this instruction set sits below /m/web) -- the opener's
                                        // entry and every sharer's hold the same one
                                        uri(SERVER).maybe(), T(ALL)), Map.of(uri(ORIGIN), "the socket's service uri as spelled, scheme://host:port — the map key is its path encoding (`ws/localhost/8555`): one socket per authority on a physical machine",
                                        uri(ROUTE), "this machine's routing table on the socket, <path> => target; a connection is answered by the most specific route across every machine sharing the socket",
                                        uri(SERVER), "the protocol server holding the socket — held so the owner's pop can release the port"))
                                .example("listener::[origin=><ws://0.0.0.0:8555>,route=>[/mtron=>mtron_ws,/mcp=>mcp_mtron]] [-- a machine's routes on its socket --]")
                                .create(),
                        NETWORK_SUPERVISOR_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(NETWORK_SUPERVISOR_TID, """
                                        the per-level reconciler that keeps a network's index true: it supervises the
                                        machine's dialed peers (redials a down link on capped backoff, drops a hung-up one,
                                        reports a recovered one online). One per level, minted on the first link it indexes
                                        and closed with the level — construction is connection, destruction is disconnect.
                                        """)
                                // one observable field: the liveness marker, updated on close. the
                                // supervised network and the ticking thread are the engine, not rec state.
                                .isaPredicate(rec(
                                        uri(STATUS), union_(uri("supervising"), uri("closed")).tryToInst()), Map.of(uri(STATUS), "supervising / closed — the liveness marker, updated on close"))
                                // construction is connection, destruction is disconnect: minting starts the
                                // supervisor (and files it in ~/network/supervisor); writing noobj there
                                // closes it.
                                .constructor(arg -> {
                                    final Network network = Machine.current().network();
                                    NetworkSupervisor.watch(network);
                                    return network.at(uri(SUPERVISOR));
                                })
                                .example("network_supervisor::[=>] [-- start supervising the current machine's network --]")
                                .create(),
                        MACH_DISCOVERY_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(MACH_DISCOVERY_TID, """
                                        a discovery protocol — a source of machine endpoints for the one roster/machine table. Existence is its interface: construction starts it, deletion stops it, and it publishes no verbs. It requires exactly one field, status, because liveness is the only thing a consumer cannot work out for itself. Where an endpoint was learned is irrelevant: every protocol writes into the shared table rather than exposing a listing of its own, so peers are generated from one consolidated index. Distinct from /m/web/discovery, which is the protocol surface it discovers over""")
                                // ONE REQUIREMENT, AND EXACTLY ONE. A protocol's product is ROWS in
                                // roster/machine rather than fields on itself, so nothing else need be
                                // readable off the protocol -- but liveness must be, or a dead protocol
                                // is indistinguishable from a quiet network and
                                // `discover/+.?[status=>false]` has nothing to ask.
                                //
                                // It is also what makes this type decide anything: an implementation's
                                // tid descends from its own protocol surface (/m/web/mdns), never from
                                // here, so a purely nominal discovery::T would admit nothing at all.
                                .isaPredicate(rec(uri(STATUS), BOOL_TYPE), Map.of(uri(STATUS), "the one field every discovery protocol must answer: true while it is live and finding machines. Derived on read, never stored — a responder whose interface went away does not announce itself"))
                                .create(),
                        MACH_NETWORK_TYPE = TypeBuilder.build()
                                .tid(MACH_MACHINE_COMPONENT_TID)
                                .vid(MACH_NETWORK_TID, """
                                        a machine's network: the endpoints the system knows about, the peers the
                                        current machine has dialed, and the handlers that discover them. membership is
                                        declared, never emergent — a uri must not be able to make itself a peer merely
                                        by being addressed — and it is accumulating, so a child machine unions its own
                                        level over its parent's. the authorities this machine answers to are derived
                                        from its listeners — the sockets it opened and those it inherits, up to the root\from the host every listening space
                                        declares.
                                        """)
                                // EVERY COMPONENT PREDICATE FOLLOWS ONE CONVENTION — all keys OPTIONAL
                                // (see processor::T and compiler::T beside it) — so a slot's deferred
                                // default and a partially-filled level both pass: the predicate
                                // constrains what IS present and nothing more. A required-key or
                                // map-only predicate is a different test, and on a slot that still holds
                                // its default it fails machine construction outright. Measured.
                                //
                                // THREE FIELDS, THREE QUESTIONS, deliberately not one map: `roster` is
                                // what the system KNOWS (connectionless — a name and where it is, at
                                // the ROOT so every machine reads the one listing), `peer` is what the
                                // CURRENT MACHINE has DIALED (at ~, so a child's reach is its own and a
                                // pop retracts exactly what that machine dialed), and `source` is HOW
                                // the roster is produced. Knowledge is inherited and reach is not, which
                                // is why the two are addressed at different scopes rather than told apart
                                // by the Java-side set (PeerSpawn.DECLARED) this replaces.
                                //
                                // A roster is a map, which a fixed-key rec cannot describe, so the map
                                // idiom is the positional (keyType, valueType) pair — the same one a
                                // space's `route` uses. Both fields are reached through Network: the
                                // roster at /network/roster (an address, so an entry may be written one
                                // at a time), the connections at the machine's own ~/network/peer.
                                //
                                // `source` names an implementation and stops there. Values are handler
                                // tids AS URIS (`</m/web/mdns>`), never web types: this instruction set
                                // sits below /m/web and does not import it, which is also why peer::T's
                                // `handler` is a uri. The key is a free mechanism label rather than the
                                // protocol's own tid, because discovering several segments independently
                                // is a real deployment — this host carries a physical LAN and a dozen
                                // docker bridges — so two mDNS sources with different interfaces and
                                // different trust must be able to sit side by side.
                                //
                                // CONFIGURATION IS NOT CARRIED HERE, because it is already carried
                                // elsewhere: a handler type's predicate supplies its defaults and boot
                                // args supply the deployment's values, exactly as every boot profile
                                // binds a `host` and lets `.else()` fill the rest. If a source ever does
                                // need an instance configured inline, take the route table's shape (a
                                // uri, a type reference, or an instruction) rather than widening this
                                // value to a bare rec — which reads stricter than ALL_TYPE while
                                // admitting any rec at all.
                                .isaPredicate(rec(
                                        // the component is its own address space: the branch it answers
                                        // for, <vid>/network/# once its machine mounts it (~/network/# until)
                                        uri(PATTERN).maybe().asUri(), URI_TYPE,
                                        uri(HOST).maybe().asUri(), URI_TYPE,
                                        uri(ROSTER).maybe().asUri(), rec(
                                                uri(DISCOVER).maybe().asUri(), T(MACH_DISCOVERY_TID.maybeSome()),
                                                // THE ADDRESSES PER HOST — knowledge, never reach: discovery
                                                // and a declaration both write where the machine answers
                                                // (scheme://host:port, a route where the dial needs one), and
                                                // reaching it is dialing one of them -- the client the dial
                                                // constructs brings its own instructions (its `inst`).
                                                uri(MACHINE).maybe(), rec(URI_TYPE, T(URI_TID.some()))),
                                        uri(PEER).maybe().asUri(), rec(PATH_ENCODING_TYPE, T(PEER_TID)),
                                        // PRESENCE, the third question: the sockets THIS machine serves
                                        // through, at ~ like peer -- its own and the inherited ones it keeps
                                        // routes on, so a pop takes exactly this machine's routes
                                        uri(LISTENER).maybe().asUri(), rec(PATH_ENCODING_TYPE, T(LISTENER_TID)),
                                        uri(SUPERVISOR).maybe().asUri(), T(NETWORK_SUPERVISOR_TID),
                                        // THE FOURTH QUESTION, beside known/reached/served: what you CALL a
                                        // peer. <label> => <full origin> -- a name is denotation, so this is
                                        // the one field of the four whose key is a LABEL rather than an
                                        // address, and it is read at the root for exactly that reason.
                                        uri(PREFIX).maybe().asUri(), rec(URI_TYPE, URI_TYPE).maybe(),
                                        // the AUTO STATUS INST BasicNetwork installs at construction --
                                        // a method-shaped field, not a map: the component rec is
                                        // re-typechecked on every peer write, so this slot must say what
                                        // the component actually stores.
                                        uri(STATUS).maybe().asUri(), INST_TYPE), Map.of(uri(PATTERN).maybe().asUri(), "the branch this network answers as a space: <vid>/network/#, every field at its own address and every member at its key's path",
                                        uri(ROSTER).maybe(), "known machines, connectionless: <host> => uri{+}, every address the machine answers at (scheme://host:port); knowledge, not reach — a machine is reached by dialing one of them",
                                        uri(PEER).maybe(), "machine's own connections, one per surface: <dial as a path> => peer::T (ws/localhost/8555/mtron), each addressable at ~/network/peer/<key> and hung up by writing noobj there",
                                        uri(LISTENER).maybe(), "the sockets this machine serves through, one per authority: <authority as a path> => listener::T (ws/localhost/8555) carrying this machine's own routes — a socket it opened, or one inherited from above",
                                        uri(STATUS).maybe(), "the health method — an auto inst closing over this network, one peer::T report per declared peer"))
                                .constructor(arg -> new BasicNetwork(arg.asRec().jvm()))
                                .example("network::[roster=>[b=><ws://192.168.1.42:8555>]] [-- a network that knows a peer before dialing it --]", "network::[peer=>[<ws/localhost/39111/worker>=>[origin=><ws://localhost:39111>]]] [-- a machine whose reach is the one node it holds --]")
                                .create(),
                        MACH_MACHINE_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(MACH_MACHINE_TID, """
                                        a machine integrates language, structure, and process into one context for action
                                        within metatron. machines form a nested stack: a parent machine's state is reachable
                                        from its children, and a parent defines the scope of a child machine's access to
                                        resources. machines communicate laterally through the network component, where a
                                        peer group forms a shared computing workspace that is garbage collected when the
                                        machine is popped off the parent stack. the absolute root of the metatron graph
                                        (/.) is the root machine.
                                        """)
                                .isaPredicate(
                                        TypeBuilder.DocArg.of(uri(INSTSET).maybe().asUri(), INSTSET_TYPE,
                                                "the machine's instruction set architecture"),
                                        TypeBuilder.DocArg.of(uri(COMPILER).maybe().asUri(), MACH_COMPILER_TYPE,
                                                "the lowering axis: parser, rewriter, resolver, and typer"),
                                        TypeBuilder.DocArg.of(uri(MEMORY).maybe().asUri(), MACH_MEMORY_TYPE,
                                                "the machine's address space, bounded by the parent machine"),
                                        TypeBuilder.DocArg.of(uri(NETWORK).maybe().asUri(), MACH_NETWORK_TYPE,
                                                "the known machines and this machine's peers"),
                                        TypeBuilder.DocArg.of(uri(PROCESSOR).maybe().asUri(), MACH_PROCESSOR_TYPE,
                                                "the execution axis: threads and computational state"))
                                .constructor(arg -> BasicMachine.of(MACH_MACHINE_TID, arg.vid()))
                                .create(),
                        /// /////////////////////
                        THREAD_EXECUTOR_TYPE = TypeBuilder.build()
                                .tid(REC_TID)
                                .vid(THREAD_EXECUTOR_TID, "the gateway interface for all threads in metatron")
                                .isaPredicate(rec(uri(RUN), lst(), uri(STOP), lst())).create(),
                        MACH_CORE_THREAD_TYPE = TypeBuilder.build()
                                .tid(MACH_THREAD_TID)
                                .vid(MACH_CORE_THREAD_TID, "run a concurrent core thread")
                                .constructor(cthread -> new CoreThread(cthread.jvm(), MACH_CORE_THREAD_TID, cthread.vid()))
                                .example("core::[code=>ping(<phaseshift.studio:80>),loop=>second::1.0]@~/thread/ping")
                                .create(),
                        MACH_VIRTUAL_THREAD_TYPE = TypeBuilder.build()
                                .tid(MACH_THREAD_TID)
                                .vid(MACH_VIRTUAL_THREAD_TID, "run a concurrent virtual thread")
                                .constructor(vthread -> new VirtualThread(vthread.jvm(), MACH_VIRTUAL_THREAD_TID, vthread.vid()))
                                .example("virtual::[code=>ping(<phaseshift.studio:80>),loop=>second::1.5]@~/thread/ping")
                                .create()),
                uri(INST), lst(Stream.concat(Stream.empty(), Stream.of(
                       /* instC(THREAD_INST_TID.dom(ALL.maybe()).rng(MACH_THREAD_TID), lst(T(ALL)), (lhs, inst) -> {
                            final fURI baseVID = f("~/thread");
                            final VirtualThread thread = new VirtualThread(mutableMap(uri(CODE), inst.arg(0)), MACH_VIRTUAL_THREAD_TID, CommonUtil.mintShortUUID(baseVID, true));
                            final AbstractThread parent = BootLoader.CURRENT_THREAD.get();
                            if (null != parent && null != parent.vid())
                                thread.jvm().put(uri(SOURCE), auto_from_(uri(parent.vid())).tryToInst());
                            thread.applyAsync(lhs);
                            return thread;
                        }),*/
                        // PUSH AT AN ADDRESS IS move-to-its-PARENT then push-its-NAME — the algebra Marko gave:
                        // push(<../b>) == move(<..>).push(<b>). So the frame that HOLDS the new machine is the one
                        // at the address's parent, and only the LEAF is minted: a sibling comes from the shared
                        // parent, never from beside it. Nothing in Machine.push had to change for this — it mints
                        // a child of the machine it is CALLED ON, so the memory and instset unions it builds come
                        // from that parent, which is exactly where the new frame belongs.
                        InstBuilder.build().tid(PUSH_INST_TID)
                                .dom(A.maybe(), "lhs is passed through")
                                .rng(A.maybeSome(), "the lhs side obj")
                                .argLst(T(URI_TID.maybe()), "the relative uri of the machine to spawn. if absent, a 6-char hex address is minted.")
                                .desc("""
                                        mint a machine at an address in the machine tree and make it current.
                                        constructive — it adds a machine, where move only chooses one. 
                                        because the leaf is minted from the address's parent, push can cross a level as well as descend into one, 
                                        and a sibling is therefore minted from the shared parent rather than from beside it.""")
                                .example("push(xyz)                              [-- mint /xyz --]")
                                .example("push(<../b>)                           [-- the sibling b, minted from the shared parent: = move(<..>).push(b) --]")
                                .example("push(<a/b>)                            [-- the descendant /a/b, minted from the machine at /a --]")
                                .example("push()                                 [-- an anonymous child, c = mintShortUUID(vid(here)) --]")
                                .example("push(<ws://localhost:8555/worker>)     [-- refused: a peer, not a machine in this tree --]")
                                .example("move(//remote).push(a/b)               [-- create a machine at a remote location by moving to that location first --]")
                                .body((lhs, inst) -> {
                                    final Machine here = Machine.current();
                                    final Machine pushed;
                                    if (inst.arg(0).isNoObj())
                                        pushed = here.push();
                                    else {
                                        final fURI target = Machine.Helper.machineAddress(here, inst.arg(0).uriValue());
                                        // a wire address names a PROCESS boundary, not a place in this tree: it is not
                                        // split into container + leaf, because the authority IS its identity -- it is
                                        // handed to Machine.push whole, which refuses it
                                        if (target.hasHost() || target.hasScheme())
                                            pushed = here.push(target);
                                        else {
                                            // the container is the frame the target is a CHILD of — the target's
                                            // PARENT ADDRESS. An EMPTY address here is not "nowhere": it is the
                                            // ROOT's own address, and frameAt answers the root for it.
                                            final fURI containerAddress = target.asNode().retract(1);
                                            final Machine container = containerAddress.equals(here.vid())
                                                    ? here // the common case: a plain child of where we already stand
                                                    : Helper.machineAt(here, containerAddress, "push");
                                            pushed = container.push(f(target.name()));
                                        }
                                    }
                                    MachinePerspective.publish(pushed);
                                    return lhs;
                                }).create(),
                        // POP NAMES THE FRAME YOU STAND IN. `pop()` and `pop(<~>)` are ONE instruction, and an
                        // addressed pop must still RESOLVE to where you stand: the motif is understood (`..`
                        // folds, `~` is the base) but a frame you can only reach by ADDRESS is not yours to
                        // discard from here — the stack discipline says a frame is discarded by the one that
                        // holds it, which is why `pop(<..>)` (the parent) is refused too, not just a sibling.
                        // Reaching a frame you OWN but have stepped out of needs the downward link the frame
                        // tree does not have yet (t-murdnd40-rpdahi), and closing an address-read handle would
                        // silently do nothing — so it is refused loudly rather than appearing to work.
                        InstBuilder.build().tid(POP_INST_TID)
                                .dom(A.maybe(), "the lhs is not consulted: pop acts on the machine the code is running in")
                                .rng(A.maybeSome(), "the machine that held the one discarded — \\(\\mathrm{pop}(f) \\mapsto \\mathrm{parent}(f)\\), so the current machine becomes \\(f\\)'s \\(..\\)")
                                .argLst(T(ALL.maybe()), "the machine to discard, which must resolve relative to the current machine: it is admitted only when \\(t \\sim \\mathrm{here}\\), so `pop(<../b>)` from \\(a\\) is refused. Absent, \\(t = \\mathrm{here}\\) — and `pop()` = `pop(<~>)` = `pop(_)`")
                                .desc("""
                                        destructive — the one machine instruction that closes what it leaves, where move leaves
                                        it standing and reachable by address. It discards the machine the code is running in
                                        and lands on that machine's \\(..\\). A machine is discarded by the one that holds it,
                                        so an address that does not resolve to the current machine is refused rather than
                                        silently ignored, and the root has no \\(..\\) to land on: it owns the address space,
                                        so closing it is the shutdown path and a reset is the honest instruction. Because
                                        \\(\\mathrm{move}\\) keeps no history and the containment link is what
                                        \\(\\mathrm{pop}\\) follows, a machine the code only moved into has nowhere to
                                        return to.""")
                                .example("pop()               [-- discard the current machine, land on its .. --]")
                                .example("pop(<~>)            [-- the same instruction: ~ is the current machine --]")
                                .example("pop(_)              [-- and the same again with the lhs as the argument --]")
                                .example("pop(<../b>)         [-- refused: b is not the current machine --]")
                                .example("pop()               [-- at the root: refused, reset metatron instead --]")
                                .body((lhs, inst) -> {
                                    final Machine here = Machine.current();
                                    if (here == Machine.root())
                                        throw MTronException.of("the root machine can not be closed via pop");
                                    final Machine landed;
                                    if (inst.arg(0).isNoObj())
                                        landed = here.pop();
                                    else {
                                        final Machine named = inst.arg(0).isMachine()
                                                ? inst.arg(0).asMachine()
                                                : Helper.machineAt(here, inst.arg(0).uriValue(), "pop");
                                        if (!Helper.sameFrame(named, here))
                                            throw MTronException.of("pop(%s) is not the current machine (%s): a machine is discarded by the machine that holds it — discard the current machine with pop(), or move(<%s>) there first",
                                                    inst.arg(0), here.vid(), inst.arg(0));
                                        landed = here.pop();
                                    }
                                    MachinePerspective.publish(landed);
                                    return lhs;
                                })
                                .create(),
                        // move() is move(<..>): the NON-destructive way up, and the whole point of the motif —
                        // pop() destroys the frame it leaves, move() leaves it standing and reachable by address
                        InstBuilder.build().tid(MOVE_INST_TID)
                                .dom(A.maybe(), "the lhs flows through: move re-homes the machine the next code runs in, and the value continues untouched")
                                .rng(A.maybeSome(), "the value, unchanged — \\(\\mathrm{move}(t)\\colon x \\mapsto x\\) where the machine \\(t\\) addresses becomes current")
                                .argLst(T(URI_TID.maybe()), "\\(t\\), the address to stand in: `~` is the current machine, `a` (or `~/a`) its child, `..` its parent, `../b` the sibling \\(b\\), and `../../..` walks up. Absent, \\(t = \\;..\\) — bare `move()` is `move(<..>)`")
                                .desc("""
                                        choose where the next inst will evaluate, and close nothing. The address is folded
                                        against the current machine, and an address that walks past the root saturates
                                        there rather than failing.""")
                                .example("move(<..>)          [-- up: the parent machine, without closing this one --]")
                                .example("move()              [-- the same instruction --]")
                                .example("move(<../b>)        [-- across, to the sibling b --]")
                                .example("move(<a/c>)         [-- down a composed path in one step --]")
                                .example("move(<~/..>)        [-- ~ is the current machine, consumed before the fold: the parent --]")
                                .example("move(<../../..>)    [-- past the root: saturates at the root --]")
                                .body((lhs, inst) -> {
                                    final fURI target = inst.arg(0).isNoObj() ? UP : inst.arg(0).uriValue();
                                    final Machine moved = Machine.current().move(target);
                                    MachinePerspective.publish(moved);
                                    return lhs;
                                })
                                .create(),
                        InstBuilder.build().tid(STAY_INST_TID)
                                .dom(A.maybe(), "the lhs is not consulted: stay re-stands the machine the code already runs in")
                                .rng(A.maybeSome(), "the machine the code is running in, re-bound to itself — \\(\\mathrm{stay}() \\mapsto \\mathrm{here}\\)")
                                .argLst()
                                .desc("""
                                        stand in place: the identity of move, the machine pointer re-bound to itself. The
                                        mechanical behaviors fire — the re-binding and the perspective publish — but to the
                                        machine the code is already running in, so the operation is a fixed point rather
                                        than a noop. `stay()` is `move(<~>)`.""")
                                .example("stay()              [-- stand in place: the machine pointer re-binds to itself --]")
                                .example("stay() = move(<~>)  [-- the identity move: ~ is the current machine --]")
                                .body((lhs, inst) -> {
                                    final Machine moved = Machine.current(Machine.current());
                                    MachinePerspective.publish(moved);
                                    return lhs;
                                })
                                .create(),
                        InstBuilder.build().tid(MACH_INST_TID.extend("stop"))
                                .dom(MACH_THREAD_TID, "the thread rec to halt: the lhs is the thread itself, never a value it carries")
                                .rng(MACH_THREAD_TID, "the same thread, now stopped — its state is `stop` and its carrier interrupted")
                                .argLst()
                                .desc("""
                                        halt a thread for good. The thread's `state` becomes `stop`, its carrier is
                                        interrupted and any waiter is woken: a stopped thread runs no further cycle, and
                                        `resume` refuses it — a stop ends a thread's life, where a pause holds it. The rec
                                        stays where it was, so `result` and `runtime` remain readable from the address that
                                        held it.""")
                                .example("*<~/thread/ping>.stop()    [-- halt the loop at ping, keeping its rec --]")
                                .body((lhs, inst) -> {
                                    ((AbstractThread) lhs).stop();
                                    return lhs;
                                })
                                .create(),
                        InstBuilder.build().tid(MACH_INST_TID.extend("pause"))
                                .dom(MACH_THREAD_TID, "the thread rec to hold: only a looping thread has a next cycle to hold between")
                                .rng(MACH_THREAD_TID, "the same thread, now paused after the cycle it was in — the lhs flows through")
                                .argLst()
                                .desc("""
                                        hold a looping thread between cycles. Its `state` becomes `pause` and it sleeps until
                                        `resume` wakes it, its rec and `result` untouched — a pause is a hold, where a stop
                                        is the end. A thread that does not loop, or that is already stopped, is refused with
                                        a warning and left as it was: with no next cycle there is nothing to pause
                                        between.""")
                                .example("*<~/thread/ping>.pause()   [-- hold the loop between cycles, result intact --]")
                                .body((lhs, inst) -> {
                                    ((AbstractThread) lhs).pause();
                                    return lhs;
                                })
                                .create(),
                        InstBuilder.build().tid(MACH_INST_TID.extend("resume"))
                                .dom(MACH_THREAD_TID, "the paused thread rec to wake — the lhs is the thread itself")
                                .rng(MACH_THREAD_TID, "the same thread, now running again — the lhs flows through")
                                .argLst()
                                .desc("""
                                        let a paused thread run again. Its `state` becomes `run` and its waiter is woken, so
                                        the next cycle follows immediately — the opposing edge of `pause`, and a no-op on a
                                        thread that is already running. A stopped thread is refused with a warning: `stop`
                                        ends a thread, so there is nothing left for a resume to restart.""")
                                .example("*<~/thread/ping>.resume()  [-- wake the loop: the next cycle follows --]")
                                .body((lhs, inst) -> {
                                    ((AbstractThread) lhs).resume();
                                    return lhs;
                                })
                                .create(),
                        InstBuilder.build().tid(CUT_INST_TID)
                                .dom(ALL.maybeSome(), "the value the shipped tail starts from: it is the lhs that the cut program was cut off of")
                                .rng(ALL.maybeSome(), "whatever the shipped tail answers at the peer — the one scattered lane, merged")
                                .argLst(URI_TYPE, "the peer and the place in it to resume at: `<//worker/a>`",
                                        ALL_TYPE, "the tail to run at the peer, as code: it picks up where the chain was cut")
                                .desc("""
                                        ship a chain to a peer and retrieve the result of its remote evaluation. 
                                        the move-extractor splits the chain at the
                                        first move whose target names a host, and the cut carries the resolved origin and
                                        the tail — `5.move(<//worker/a>).plus(1)` becomes
                                        `5.cut(<//worker/a>, |plus(1))` — so the shipped program is the lhs, the move
                                        to the tail's place in the peer, and the tail, evaluated through the peer's
                                        `eval` and gathered back as one lane. A cut never runs locally: an origin with no
                                        peer dialed is a failure.""")
                                .example("5.move(<//worker>).plus(1)    [-- compiles to 5.cut(<//worker>, |plus(1)) --]")
                                .body((lhs, inst) -> {
                                    // A CUT SHIPS TO A PEER, so the peer has to exist: with none dialed at the authority, the
                                    // scatter's eval would resolve to the NATIVE eval — which evaluates locally — and the
                                    // authority would vanish without a word (`1.move(//abc)` answered 1). Answering the lhs
                                    // of a move is not a result: nothing was moved to and nothing was computed.
                                    final fURI cutOrigin = inst.arg(0).uriValue().origin();
                                    // ONE DOOR, ONE REFUSAL: the same reachability question atPeer asks, and a failure that
                                    // names where the dial does live when an ancestor holds it (Network.Helper.unreachable).
                                    if (!Network.Helper.reachable(Machine.current(), cutOrigin))
                                        throw Network.Helper.unreachable(Machine.current(),
                                                inst.arg(0).uriValue(), cutOrigin);
                                    final List<Inst> insts = new ArrayList<>(start_(lhs).insts());
                                    insts.addAll(move_(uri(inst.arg(0).uriValue().path().isEmpty() ? ROOT_MACHINE_VID : f().path(inst.arg(0).uriValue().path()))).insts());
                                    insts.addAll(inst.arg(1).asCall().insts());
                                    // THE CUT ANSWERS WHAT THE PEER ANSWERS, so the scatter runs here. Handed back
                                    // unrun, the code ran only where a processor iterated it (Code.iterator() applies);
                                    // a cut applied as an inst, alone in an ordered branch's member, answered the
                                    // code itself, and the next inst failed to convert a code::T
                                    return map_(lst(code(insts))).select_(lst(eval_(inst.arg(0).uriValue().origin()).tryToInst())).merge_().apply();
                                })
                                .create(),
                        InstBuilder.build().tid(SPAWN_INST_TID)
                                .dom(ALL.maybeSome(), "the lhs is not consulted: spawn declares a process and a name, it computes nothing from a value")
                                .rng(URI_TID, "the dial uri the peer answers at, `<origin>/mtron` — an address, so reaching it is still a dial")
                                .argLst(URI_TYPE, "the authority to serve, path included because the node's store mounts there: `<//localhost/worker>` names a node on this physical machine, so a JVM is forked to serve /worker, while an authority naming another host is only declared. A port that is not named is a free one",
                                        URI_TYPE, "a name to call the spawned authority by in this machine's network table, `//worker` or `worker`. Absent, the peer is known by its address alone")
                                .desc("""
                                        mint a peer process and declare it in the roster. When the authority is this physical
                                        machine a JVM is forked, its READY marker awaited, and the node retracted again when
                                        the machine holding it is popped; a remote authority is declared and nothing is
                                        forked. Idempotent by address — asking for an authority already declared finds it
                                        rather than forking a twin — and a half-spawn that fails leaves neither an orphan
                                        process nor a roster row claiming an address nothing serves. The answer is where
                                        the node answers, and only a dial reaches it: membership is declared, never
                                        emergent.""")
                                .example("spawn(<//localhost/worker>)              [-- fork a node here serving /worker; answers ws://localhost:<port>/mtron --]")
                                .example("spawn(<//localhost/worker>, <//worker>)  [-- and call it //worker: move(<//worker/a>) now reaches that node --]")
                                .example("spawn(<ws://worker:8555/worker>)         [-- a remote authority: declared in the roster, nothing forked --]")
                                .body((_, inst) -> {
                                    final fURI spawn = PeerSpawn.spawn(inst.arg(0).uriValue());
                                    if (!inst.arg(1).isNoObj())
                                        Machine.current().network().registerPrefix(inst.arg(1).uriValue(), spawn.origin());
                                    return uri(spawn);
                                })
                                .create(),
                        // THE ROUND-TRIP REPLY. A wire client appends nothing to the program it ships: it WRAPS it,
                        // because a noobj produced inside the program kills the monad at the next inst whose domain
                        // does not admit zero, so a trailing reply would never be reached. As the frame's root this
                        // inst always runs, and the payload is evaluated in its body — where "the answer is nothing"
                        // is an ordinary return value, not a dead monad.
                        InstBuilder.build().tid(REPLY_INST_TID.addQ(BLOCK))
                                .dom(ALL.maybe(), "the frame the payload is evaluated against — a shipped program starts at noobj")
                                .rng(ALL.maybeSome(), "the payload's answer, addressed by the trip's tag; the absent-answer mark when the payload answers nothing")
                                .argLst(T(ALL.maybeSome()), "the payload to evaluate on this side",
                                        URI_TYPE, "the trip's correlation tag, a uri")
                                .desc("""
                                        evaluate a shipped payload and address the answer by the trip it belongs to. This is wire
                                        vocabulary, not a user instruction: a round-tripping client wraps the program it ships, so
                                        the frame's root is the reply and the payload is its (blocking) argument. Addressing the
                                        answer is what lets the client match a reply to its request without an envelope on the
                                        wire — the tag rides the obj it answers. An answer that is NOTHING has no address to ride
                                        (noobj is the zero-coefficient singleton, which no writer will render with a vid), so it
                                        travels as the absent-answer mark instead, and the client reads it as noobj.""")
                                .example("reply(3.plus(1), <a1f30b7c>)       [-- the far side answers 4@a1f30b7c --]")
                                .example("reply(noobj, <a1f30b7c>)           [-- the far side answers /wire/noobj@a1f30b7c: nothing, addressed --]")
                                .body((lhs, inst) -> {
                                    // the payload arrives RAW: the base path is declared in Inst.isBlocking(), so
                                    // applyArgs neither evaluates nor type-binds it before the reply can wrap it.
                                    // A CALL is evaluated; anything else IS the answer, answered as it stands — a
                                    // value's coefficient is part of it, and applying a value to the lhs would
                                    // compress that coefficient away ({3}34 came back as 34 that way)
                                    Obj payload;
                                    try {
                                        payload = inst.arg(0).apply(noobj());
                                    } catch (final Exception e) {
                                        payload = fail(e);
                                    }
                                    //final Obj answer = payload.isCall() ? payload.apply(lhs) : payload;
                                    final fURI tag = inst.arg(1).uriValue();
                                    return payload.isNoObj()
                                            // NOTHING CANNOT HOLD AN ADDRESS. noobj is the coefficient-zero singleton and
                                            // the serializer's root writer flattens it, tag and all, so the absent answer
                                            // travels as the mark instead — a value that says the same thing and can be
                                            // addressed (see NOOBJ_SENTINEL)
                                            ? uri(NOOBJ_SENTINEL).selfVID(tag)
                                            // clone().selfVID, never vid(): vid() clones AND WRITES the tagged obj at the
                                            // address (Obj.Helper.objClone -> objCheckAndSave -> Machine.write), so on the
                                            // far side every trip would leave a bookkeeping entry on its variableStack
                                            : payload.clone().selfVID(tag);
                                })
                                .create()
                ))),
                uri(REWRITE), lst(
                        docWrap(InstSet.Helper.rewriter(MACH_INST_TID.extend(REWRITE).extend("move_extract"), Helper::extractMove),
                                "the move-extractor: a move(<authority>) with a tail ships the tail, so the compiler packages it as the move's blocked-code argument — move(//host/xyz).c().d() becomes move(//host/xyz, |c().d())")
                )));
        docWrap(this, "the machine instruction set for nesting machines and their components instset, compiler, memory, network, processor");
        super.setup();

    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>();
    }

    public static class Helper {
        private Helper() {
            // do nothing
        }

        /**
         * The move-extractor: a {@code move(<authority>)} is a scatter, so this rule splits the chain at the move and
         * ships the whole thing through the peer's eval:
         * {@code a().b().move(//host/xyz).c().d()} → {@code a().b().-<[|move(xyz),_,|c().d()].as(code::T).eval(//host)}.
         * The split's lst holds the localized move, the identity (the lhs flows through here), and the tail; casting it
         * to {@code code::T} rebuilds the chain, and the peer re-compiles the shipped code, so a nested remote move is
         * extracted where it lands. A local move (no authority) is left alone.
         * <p>
         * A PREFIXED TARGET IS ALIGNED BEFORE THE DECISION, and the aligned form is what the cut carries.
         * {@code move(<self:/a>)} denotes a real authority, so the tail must be cut — and the CUT'S TARGET must be the
         * RESOLVED address, because the far side has never heard of this machine's names. The alignment goes through
         * {@link Machine.Helper#align}, the same door the runtime uses, so a spelling cannot resolve one way at compile
         * time and another when it runs.
         * <p>
         * THE TWO HALVES RESOLVE IN DIFFERENT PLACES — do not "finish" the tail here. What this machine DIALS, it
         * resolves (the cut's target, above). What the RECEIVER RUNS stays exactly as written: the tail is a program,
         * re-planned in the environment it lands in, so a nested {@code move(<//self/b>)} keeps its spelling and is
         * resolved by the destination's own tables. Aligning it here would bake THIS machine's denotation into a
         * program meant to be re-homed: a peer whose {@code self} names another place (or nothing) would then run
         * somewhere its tables never said. That is why this rule stops at the first cut and returns the rest verbatim.
         * <p>
         * THE CROSSING RULE IS HOST PRESENCE, and it is also where the STATE BOUNDARY lives: a target that names a host
         * is the only move that leaves this machine, so it is the only one whose tail runs in the receiver's frame state
         * ({@link Network.Helper} — {@code boundary} swaps the perspective, arg frames and thread away and back). A
         * host-less move changes the running thread's machine and persists; it changes state, it does not isolate it.
         */
        static Code extractMove(final Code code) {
            final List<Inst> insts = code.insts();
            for (int i = 0; i < insts.size(); i++) {
                final Inst inst = insts.get(i);
                if (!MOVE_INST_TID.equals(inst.tid().basePath()) || inst.arg(0).isNoObj())
                    continue;
                final fURI target = Machine.Helper.align(Machine.current(), inst.arg(0).uriValue());
                // A MOVE CROSSES IFF ITS TARGET NAMES A HOST. A host is the smallest thing that can name a SERVICE
                // rather than a place, so it is the one shape that can be somewhere else: a local path (`a`, `..`,
                // bare move()) stays here, and a scheme with no host (`loop:a`) names no service at all -- it fails by
                // address, and cutting for it would ship the tail to an origin nothing can dial (measured: scheme-only
                // targets crashed the origin arithmetic instead of failing the move). An ALIGNED spelling ACQUIRES its
                // host from the tables (`self:/a`, `//self/a` -> loop://self/a), so resolution -- not the page --
                // decides whether this move crosses.
                // THIS IS ALSO THE ONLY PLACE A STATE BOUNDARY EXISTS: a crossing move's tail runs in the RECEIVER's
                // frame state (Network.Helper.boundary: perspective, arg frames and thread swapped away and restored),
                // because a move that crossed to another host must neither see nor leak the caller's frames. A host-less move
                // re-homes the running thread and PERSISTS -- it changes state, it does not isolate it -- which is why
                // only this branch cuts.
                if (!target.isAddressed())
                    continue;
                final Obj authority = target.equals(inst.arg(0).uriValue()) ? inst.arg(0) : uri(target);
                final List<Inst> newCode = new ArrayList<>(insts.subList(0, i));
                newCode.add(instB(CUT_INST_TID, lst(authority, code(new ArrayList<>(insts.subList(i + 1, insts.size()))))));
                return code(newCode);
            }
            return code;
        }

        /**
         * The frame a frame instruction ADDRESSES: the {@code ..} arithmetic first (one definition, shared with
         * push), then a read. The address is resolved before it is read — never matched — because these instructions
         * name a PLACE, and a place with a {@code ..} in it is an expression rather than a key.
         * <p>
         * The root is answered directly: {@code read("/")} has no space to reach it through (the absolute marker is
         * not a mounted namespace), and {@code /.}, {@code /} and the saturated {@code ..} are one address
         * canonically, so the equality check catches every spelling of it. Anything that resolves to something other
         * than a frame is refused BY ADDRESS, so the message names what was asked for rather than what was found.
         */
        public static Machine machineAt(final Machine here, final fURI target, final String instruction) {
            final fURI address = Machine.Helper.machineAddress(here, target);
            if (null == address || address.isEmpty() || address.equals(ROOT_MACHINE_VID))
                return Machine.root();
            final Obj found = Machine.read(address);
            if (!found.isMachine())
                throw MTronException.of("%s(%s) found no machine: resolved %s is %s",
                        instruction, target, address, found.isNoObj() ? "empty" : "a " + found.tid());
            return found.asMachine();
        }

        /**
         * Whether two handles name the SAME frame. A machine's identity is its ADDRESS, not its object identity: a
         * read that answers with a machine can hand back an equal-but-distinct instance (verified — a read of a
         * frame's address is {@code .eq} to the machine {@code push(a)} returned while {@code ==} is false), so
         * comparing references makes {@code pop(<~>)} silently do nothing instead of popping.
         */
        public static boolean sameFrame(final Machine one, final Machine two) {
            if (one == two)
                return true;
            final fURI oneVID = one.vid();
            final fURI twoVID = two.vid();
            return null != oneVID && oneVID.equals(twoVID);
        }
    }
}
