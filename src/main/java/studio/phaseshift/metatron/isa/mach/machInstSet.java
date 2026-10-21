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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.MachinePerspective;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.compiler.BasicCompiler;
import studio.phaseshift.metatron.isa.mach.type.compiler.TypeTyper;
import studio.phaseshift.metatron.isa.mach.type.compiler.parser.mtronParser;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.*;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.IdentityRewriter;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

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
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
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
     * The frame inst — {@code push(<uri>)}. The uri decides the boundary: a relative one mints a child frame in
     * this JVM, while one carrying an authority is a <em>process</em> boundary, which a frame does not cross — code
     * reaches another process by being shipped there in a plan ({@code law_shard}), so {@link Machine#push(fURI)}
     * refuses it.
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
     * MINTS the address, and that is licence to cross as well as descend: {@code push(<t>)} resolves {@code t} and
     * mints its LEAF from the frame at {@code t}'s PARENT — so {@code push(<../b>) == move(<..>).push(<b>)}, and a
     * sibling is minted from the shared parent rather than from beside it. An address that names a PROCESS
     * boundary is not split that way: its authority IS its identity, so it is handed to {@code push} whole, which
     * refuses it (see {@link Machine#push(fURI)}).
     */
    public static final fURI POP_INST_TID = MACH_INST_TID.extend("pop");
    public static final fURI MOVE_INST_TID = MACH_INST_TID.extend("move");

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
    public static final fURI PEER_TID = MACH_ISA_TID.extend("peer");

    public static Type PEER_TYPE;
    public static final fURI ID_CLIENT_TID = MACH_ISA_TID.extend("id_client");
    public static Type ID_CLIENT_TYPE;

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
    public static Type MACH_REWRITER_TYPE;
    public static Type MACH_FIXPOINT_REWRITER_TYPE;
    public static Type MACH_IDENTITY_REWRITER_TYPE;
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
    public static final fURI MACH_DISCOVERY_TID = MACH_NETWORK_TID.extend("discovery");
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
                        // the compiler family — structural apply(source)->code contract holding its four stages
                        MACH_COMPILER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_COMPILER_TID)
                                        .isaPredicate(rec(
                                                uri(PARSER).maybe().asUri(), T(MACH_PARSER_TID),
                                                uri(REWRITER).maybe().asUri(), T(MACH_REWRITER_TID),
                                                uri(RESOLVER).maybe().asUri(), T(MACH_RESOLVER_TID),
                                                uri(TYPER).maybe().asUri(), T(MACH_TYPER_TID)))
                                        .constructor(arg -> new BasicCompiler(BasicCompiler.stages(arg.asRec().jvm()), MACH_COMPILER_TID, arg.vid()))
                                        .create(),
                                Map.of(
                                        uri(PARSER).maybe(), "the first stage — source text to obj, and the identity for anything that is not a str, so a compiler accepts a program's text or its parsed obj alike: mtron_parser::T by default",
                                        uri(REWRITER).maybe(), "the second stage — the code::T to code::T lowering that runs before any type is resolved: fixpoint_rewriter::T by default (every registered rewrite rule to a stable fixpoint), identity_rewriter::T for a compiler that wants no rewrites",
                                        uri(RESOLVER).maybe(), "the third stage — the walk that threads each inst's range into the next inst's domain, delegating per inst to a selector (which candidates) and a binder (make one concrete): scoring_resolver::T by default",
                                        uri(TYPER).maybe(), "the fourth stage — the per-machine runtime type assertions (inst_dom, inst_rng, type_pred), carried in the machine instead of a global TypeCheck registry"),
                                "the compiler contract of machine::T — a machine's lowering axis, source to code::T, composed of four atomic overridable stages: the stages are the vocabulary and these rec entries are the wiring, all optional and each defaulting when absent, so a compiler is its composition rather than an algorithm. compiler.apply runs the default schedule — parse → rewrite → resolve → type — each stage handed the whole of what the last produced and returning the same, and the processor is the sibling that then runs what the compiler produced. Only the parse stage accepts anything but code — source text, or an already-parsed obj it lifts as it stands instead of re-reading; every stage returns code::T. A compiler is constructed from its stages, and a bare compiler::[=>] is the default compiler.",
                                "compiler::[rewriter=>fixpoint_rewriter::[max=>3],resolver=>scoring_resolver::[=>]]   [-- wire the stages that matter; parser and typer default --]",
                                "compiler::[parser=>mtron_parser::[=>]]   [-- name the read stage explicitly --]",
                                "compiler::[=>]   [-- the default compiler: mtron parse, fixpoint, scoring, typer --]"),
                        // the parser family — structural contract, one concrete language (mtron itself)
                        MACH_PARSER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_PARSER_TID)
                                        .create(),
                                "the parse contract of compiler::T — the first stage, the only one that runs before there is any code, and the seam where a language is hosted: any language can be parsed as long as it generates code::T. Because the type system supports both object-oriented and functional designs and the language is Turing-complete, most languages can map onto mtron code::T."),
                        MACH_MTRON_PARSER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_PARSER_TID)
                                        .vid(MACH_MTRON_PARSER_TID)
                                        .constructor(arg -> new mtronParser(arg.asRec().jvm(), MACH_MTRON_PARSER_TID, arg.vid()))
                                        .create(),
                                "at the parse stage of compiler::T, mtron_parser::T reads metatron's own language through the mtron serializer, so what it accepts is exactly what a console line, a source file and a test row accept — the one parser metatron ships, and the compiler's default",
                                "mtron_parser::[=>]   [-- the default parse stage of compiler::T --]"),
                        // the rewriter family — structural contract, concrete fixpoint strategy
                        MACH_REWRITER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_REWRITER_TID)
                                        .create(),
                                "the rewrite contract of compiler::T — a lowering from code::T to code::T that runs before any type is resolved, so it decides what the instruction chain is: rules see generic dom/rng, and a rule-driven collapse, a native pushdown, or a distributed reduce's gather point all enter the compiled form here."),
                        MACH_IDENTITY_REWRITER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_REWRITER_TID)
                                        .vid(MACH_IDENTITY_REWRITER_TID)
                                        .constructor(arg -> IdentityRewriter.single())
                                        .create(),
                                "at the rewrite stage of compiler::T, identity_rewriter::T is the no-op rewriter: it returns the code unchanged — for a compiler that wants no rewrites at all, since fixpoint_rewriter::T is the default",
                                "identity_rewriter::[=>]   [-- the compiler's default rewrite stage --]"),
                        MACH_FIXPOINT_REWRITER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_REWRITER_TID)
                                        .vid(MACH_FIXPOINT_REWRITER_TID)
                                        .isaPredicate(rec(uri(MAX).maybe().asUri(), isa_(INT_TYPE).else_(jnt(2))))
                                        .constructor(arg -> new FixPointRewriter(arg.asRec().jvm(), MACH_FIXPOINT_REWRITER_TID, arg.vid()))
                                        .create(),
                                Map.of(uri(MAX).maybe(), "the convergence window: the number of consecutive stable passes the code must show before the fixpoint is accepted (default 2)"),
                                "at the rewrite stage of compiler::T, fixpoint_rewriter::T applies every rewrite rule registered by every instset, in turn, over the whole code, and repeats the pass until the code hash is unchanged for max consecutive passes, so a rule re-runs on every pass and must be idempotent",
                                "fixpoint_rewriter::[max=>3]   [-- accept the fixpoint after three stable passes --]"),
                        // the resolver family — structural contract, concrete scoring + firstfind strategies (empty config)
                        MACH_RESOLVER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_RESOLVER_TID)
                                        .isaPredicate(rec(
                                                uri(SELECTOR).maybe().asUri(), T(MACH_SELECTOR_TID),
                                                uri(BINDER).maybe().asUri(), T(MACH_BINDER_TID)))
                                        .create(),
                                Map.of(
                                        uri(SELECTOR).maybe(), "the strategy that decides which candidates a call may resolve to, and in what order — absent, the walk uses specificity_selector::T (score by signature specificity, take the first the binder can bind)",
                                        uri(BINDER).maybe(), "the strategy each candidate is handed to — absent, the walk uses generic_binder::T (bind generics to the lhs, gate on the lhs domain, resolve the call's arguments)"),
                                "the resolution contract of compiler::T — the stage that turns a generic instruction chain into a typed one, and the compiler's widest-reaching stage: it threads each inst's range into the next inst's domain and, per inst, delegates to a selector (fetch and order the candidates) and a binder (make the chosen candidate concrete, or reject it), so which concrete inst a call becomes — and with it whether the inst is a filter, a gather/barrier, or an initial — is settled here, per element type. What it settles is what the processor then runs (a filter's {0,1} wing dropped for compile-time typing, an initial inst seeding the element type from its own argument, a cast's range rebound to the named type), and an inst it cannot resolve is left in place for runtime resolution — a semi-resolved code, not a failed one. Structural, not nominal: its two collaborators are rec fields, both optional and both defaulting, so a resolver is its walk plus whichever strategies it names. Its members are scoring_resolver::T (the default pair), firstfind_resolver::T (first match, no scoring) and identity_resolver::T (the no-op)",
                                "scoring_resolver::[selector=>firstfind_selector::[=>]]   [-- a resolver::T: the same walk, first-match selection --]",
                                "scoring_resolver::[=>]   [-- a resolver::T: both strategies default --]"),
                        MACH_IDENTITY_RESOLVER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_RESOLVER_TID)
                                        .vid(MACH_IDENTITY_RESOLVER_TID)
                                        .constructor(arg -> IdentityResolver.single())
                                        .create(),
                                "at the resolution stage of compiler::T, identity_resolver::T is the no-op resolver: it threads no type and resolves no instruction — it returns the code unchanged — for a compiler whose resolve stage is done elsewhere",
                                "identity_resolver::[=>]   [-- a resolve stage that does nothing --]"),
                        MACH_SCORING_RESOLVER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_RESOLVER_TID)
                                        .vid(MACH_SCORING_RESOLVER_TID)
                                        .constructor(arg -> new ScoringResolver(arg.asRec().jvm(), MACH_SCORING_RESOLVER_TID, arg.vid()))
                                        .create(),
                                "at the resolution stage of compiler::T, scoring_resolver::T is the walk plus the default pair of collaborators: it threads each inst's range into the next inst's domain and, per inst, hands the call to its selector (specificity_selector::T by default) with its binder (generic_binder::T by default) — so which concrete inst a call becomes is settled here, per element type; wire a selector or binder entry to compose a different resolution",
                                "scoring_resolver::[=>]   [-- the default resolution stage of compiler::T --]"),
                        MACH_FIRSTFIND_RESOLVER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_RESOLVER_TID)
                                        .vid(MACH_FIRSTFIND_RESOLVER_TID)
                                        .constructor(arg -> {
                                            final Map<Obj, Obj> stages = mutableMap(uri(SELECTOR), FirstFindSelector.single(), uri(BINDER), GenericBinder.single());
                                            stages.putAll(arg.asRec().jvm());
                                            return new ScoringResolver(stages, MACH_FIRSTFIND_RESOLVER_TID, arg.vid());
                                        })
                                        .create(),
                                "at the resolution stage of compiler::T, firstfind_resolver::T is the same walk wired to firstfind_selector::T — a call resolves to the first viable candidate in read order, with no specificity scoring; the pre-scoring behavior, kept as the A/B counterpart of scoring_resolver::T",
                                "firstfind_resolver::[=>]   [-- first match wins, no scoring --]"),
                        // the binder family — the resolver's "make this candidate concrete" strategy
                        MACH_BINDER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_BINDER_TID)
                                        .create(),
                                "the binding contract of resolver::T — the collaborator that turns one selected candidate into the instruction a call becomes, or rejects it: it binds the candidate's generics to the lhs, gates it on the lhs domain, and resolves its arguments (compiling nested code args), returning null when the candidate cannot be made concrete; because a candidate that fails to bind must not be selectable, binding gates selection rather than following it, which is why a binder is a strategy inside the resolve stage and never a stage of its own. The candidate arrives already shaped with the call's dom/rng hints — that shaping is the selector's rule."),
                        MACH_GENERIC_BINDER_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_BINDER_TID)
                                        .vid(MACH_GENERIC_BINDER_TID)
                                        .constructor(arg -> new GenericBinder(arg.asRec().jvm(), MACH_GENERIC_BINDER_TID, arg.vid()))
                                        .create(),
                                "at the resolution stage of compiler::T, generic_binder::T is the binding algorithm: it binds the candidate's generics to the lhs, gates on the lhs domain, resolves the call's arguments, seeds an initial inst's range from its own argument, and copies the user's query maps — returning null at any step that rejects the candidate; the call's dom/rng hints (including a cast's named type) are applied by the selector before the candidate reaches it",
                                "generic_binder::[=>]   [-- the default binder::T --]"),
                        // the selector family — the resolver's "which candidate" strategy
                        MACH_SELECTOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_SELECTOR_TID)
                                        .create(),
                                "the candidate contract of resolver::T — the collaborator that fetches the instructions a call may resolve to, orders them by its own rule, shapes each one with the call's dom/rng hints, and returns the first its binder can make concrete: the ordering rule is the whole strategy, and it is where resolution strategy varies — specificity_selector::T scores by signature specificity, firstfind_selector::T takes the first viable candidate in read order; a selector that cannot resolve an instruction returns null and leaves it for runtime resolution rather than failing the code"),
                        MACH_SPECIFICITY_SELECTOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_SELECTOR_TID)
                                        .vid(MACH_SPECIFICITY_SELECTOR_TID)
                                        .constructor(arg -> new SpecificitySelector(arg.asRec().jvm(), MACH_SPECIFICITY_SELECTOR_TID, arg.vid()))
                                        .create(),
                                "at the resolution stage of compiler::T, specificity_selector::T orders a call's candidates by how specific their signatures are and takes the first its binder can bind: a concrete domain, an exact domain base-path match, a non-base dom/rng, matching arguments, and the large bonus that makes an as() candidate whose range names the requested type win; the from/at and as() shortcuts live here, since they return a finished contract rather than a candidate and so bypass the binder. Two legacy hint forms are preserved here and named: a single candidate replaces the dom outright, while a scored field keeps the API's declared dom and takes the call's coefficient",
                                "specificity_selector::[=>]   [-- the default selector::T --]"),
                        MACH_FIRSTFIND_SELECTOR_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_SELECTOR_TID)
                                        .vid(MACH_FIRSTFIND_SELECTOR_TID)
                                        .constructor(arg -> new FirstFindSelector(arg.asRec().jvm(), MACH_FIRSTFIND_SELECTOR_TID, arg.vid()))
                                        .create(),
                                "at the resolution stage of compiler::T, firstfind_selector::T takes the first viable candidate in read order, with no specificity scoring, so selection is order-dependent — the pre-scoring strategy, kept as the A/B counterpart of specificity_selector::T",
                                "firstfind_selector::[=>]   [-- first match wins, no scoring --]"),
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
                        // THE IDENTITY CLIENT — the identity element of the protocol-client family: its
                        // constructor is the dial (as every client's is) and its send_recv applies the
                        // shipped message against the LOCAL machine, so a read through a loop: peer IS the
                        // local read of the localized address. It lives HERE rather than /m/web because the
                        // identity transport needs no wire — it is what makes the self-similarity contract
                        // executable without a socket, and what keeps Helper.dial free of scheme branches:
                        // loop:'s default dialer is this type, resolved through the one dial path.
                        docWrap(ID_CLIENT_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(ID_CLIENT_TID)
                                        .isaPredicate(rec(
                                                uri(HOST).maybe().asUri(), URI_TYPE,
                                                uri(SEND_RECV).maybe().asUri(), INST_TYPE,
                                                uri(CLOSE).maybe().asUri(), INST_TYPE))
                                        .constructor(arg -> {
                                            final Obj client = rec(
                                                    uri(HOST), arg.asRec().at(uri(HOST)),
                                                    uri(SEND_RECV), instLambda((lhs, inst) -> inst.arg(0).apply(lhs)),
                                                    uri(CLOSE), instLambda((lhs, inst) -> noobj()));
                                            // indexing is a BACKGROUND behavior upon successful construction:
                                            // the client exists as an obj either way, the index merely observes
                                            Network.Helper.index(client);
                                            return client;
                                        })
                                        .create(), null, null,
                                Map.of(uri(HOST), "the dialed uri (identity: the local machine answers)",
                                        uri(SEND_RECV), "applies the shipped message against the local machine — the identity transport",
                                        uri(CLOSE), "nothing to hang up"),
                                """
                                the identity client — the identity element of the protocol-client family:
                                dialing loop://<name> constructs one, and its send_recv evaluates the shipped
                                message against the local machine. The executable form of the self-similarity
                                contract: a read through a loop peer equals the local read.
                                """,
                                "*~/network.connect(<loop://self>) [-- the identity peer: reads and writes answer locally --]"),
                        // network — a machine's OWN reachable peers: never inherited, never shared with children
                        docWrap(PEER_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(PEER_TID)
                                        // ONE TYPE, TWO HONEST STATES — and the difference is which fields are
                                        // filled, never which type it is. An authority alone is a peer we know
                                        // of but cannot reach from here, which is what a peer reference looks
                                        // like after it has crossed a wire; an authority with a transport is a
                                        // peer the current machine can ship to.
                                        .isaPredicate(rec(
                                                // THE KEY, and the one field a peer cannot do without: a roster
                                                // lookup matches on this same service uri.
                                                uri(AUTHORITY), URI_TYPE,
                                                // the endpoint and the SURFACE, in webSpace's own vocabulary: a
                                                // space's `host` names where to dial and the handler refines the
                                                // scheme into a protocol (mtron_ws, mcp_ws, …). Named as a uri
                                                // rather than a web type because this instruction set sits below
                                                // /m/web and does not import it — the same reason `source` on the
                                                // network is a uri. Both absent ⇒ known, not reachable from here.
                                                uri(HOST).maybe().asUri(), URI_TYPE,
                                                uri(HANDLER).maybe().asUri(), URI_TYPE,
                                                // a name is how a distributed expression says WHICH machine it
                                                // means: the rewrite mints one per peer as a path segment
                                                // (/usr/compute/a/barrier/b), so this is the field mapping such a
                                                // name onto a discovered authority. Not decoration.
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(STATUS).maybe().asUri(), REC_TYPE,
                                                // THE REACH: the inst that ships a message to this peer and answers
                                                // with what it computed. It is what makes a connection a connection
                                                // rather than a known address.
                                                uri(TRANSPORT).maybe().asUri(), INST_TYPE,
                                                // the protocol CLIENT the dial constructed (a ws_client::T or kin,
                                                // typed ALL because this instruction set sits below /m/web) -- held
                                                // so disconnect can hang up. absent for a connectionless transport
                                                // (loop, http) and for a peer reference that crossed a wire.
                                                uri(CLIENT).maybe(), T(ALL)))
                                        .create(), null, null,
                                Map.of(uri(AUTHORITY), "the peer's service uri, scheme://host:port — the connection key, and the one field a peer cannot do without",
                                        uri(HOST), "where to dial it from here, route included: ws://host:port/mtron",
                                        uri(HANDLER), "the protocol surface at that endpoint, named as a uri: </m/web/ws/mtron_ws>, </m/web/http/mtron_http>, </m/web/mcp/mcp_ws>",
                                        uri(NAME), "the machine's name in the distributed expression that means it — a bare name, never an address",
                                        uri(STATUS), "the health report computed for this peer — computed on call, never stored",
                                        uri(TRANSPORT), "the reach: an inst that ships a message to this peer and answers with the peer's result",
                                        uri(CLIENT), "the protocol client the dial constructed — held so disconnect can hang up; absent for connectionless transports"),
                                """
                                one metatron machine as the network records it — the value of a network peer
                                connection. Absent `host` and `handler` is not a broken peer: it is a peer
                                reference that crossed a wire, known here and not reachable from here.
                                """,
                                "peer::[authority=><ws://192.168.1.42:8555>,host=><ws://192.168.1.42:8555/mtron>,handler=></m/web/ws/mtron_ws>,name=>'builder-2'] [-- a peer the current machine can ship to --]",
                                "peer::[authority=><ws://192.168.1.42:8555>,name=>'builder-2'] [-- known, and not reachable from here --]"),
                        MACH_DISCOVERY_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(MACH_DISCOVERY_TID)
                                        // ONE REQUIREMENT, AND EXACTLY ONE. A protocol's product is ROWS in
                                        // roster/machine rather than fields on itself, so nothing else need be
                                        // readable off the protocol -- but liveness must be, or a dead protocol
                                        // is indistinguishable from a quiet network and
                                        // `discover/+.?[status=>false]` has nothing to ask.
                                        //
                                        // It is also what makes this type decide anything: an implementation's
                                        // tid descends from its own protocol surface (/m/web/mdns), never from
                                        // here, so a purely nominal discovery::T would admit nothing at all.
                                        .isaPredicate(rec(uri(STATUS), BOOL_TYPE))
                                        .create(),
                                Map.of(uri(STATUS), "the one field every discovery protocol must answer: true while it is live and finding machines. Derived on read, never stored — a responder whose interface went away does not announce itself"),
                                """
                                a discovery protocol — a source of machine endpoints for the one roster/machine table. Existence is its interface: construction starts it, deletion stops it, and it publishes no verbs. It requires exactly one field, status, because liveness is the only thing a consumer cannot work out for itself. Where an endpoint was learned is irrelevant: every protocol writes into the shared table rather than exposing a listing of its own, so peers are generated from one consolidated index. Distinct from /m/web/discovery, which is the protocol surface it discovers over"""),
                        MACH_NETWORK_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_MACHINE_COMPONENT_TID)
                                        .vid(MACH_NETWORK_TID)
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
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(HOST).maybe().asUri(), URI_TYPE,
                                                uri(ROSTER).maybe().asUri(), rec(
                                                        uri(DISCOVER).maybe().asUri(), T(MACH_DISCOVERY_TID.maybeSome()),
                                                        // A REACH PER HOST, not only an endpoint: discovery writes
                                                        // addresses, a declaration writes the transport that reaches
                                                        // the machine, and transportOf() already reads both out of
                                                        // this one table -- an inst to apply, or an address to match.
                                                        uri(MACHINE).maybe(), rec(URI_TYPE, T(ALL.some()))),
                                                uri(PEER).maybe().asUri(), rec(URI_TYPE, T(PEER_TID)),
                                                uri(SOURCE).maybe().asUri(), rec(URI_TYPE, URI_TYPE),
                                                // the AUTO STATUS INST BasicNetwork installs at construction --
                                                // a method-shaped field, not a map: the component rec is
                                                // re-typechecked on every peer write, so this slot must say what
                                                // the component actually stores.
                                                uri(STATUS).maybe().asUri(), INST_TYPE))
                                        .constructor(arg -> new BasicNetwork(arg.asRec().jvm()))
                                        .create(),
                                Map.of(uri(NAME).maybe().asUri(), "a human friendly name for the nested network",
                                        uri(ROSTER).maybe(), "known machines, connectionless: a declared peer is <authority> => transport, a discovered machine is <host> => uri{+} (every address it answers at); transportOf reads both",
                                        uri(PEER).maybe(), "machine's own connections: <service-uri> => peer::T",
                                        uri(SOURCE).maybe(), "the roster generation mechanism: <mechanism> => <handler tid as a uri>, one entry per mechanism",
                                        uri(STATUS).maybe(), "the health method — an auto inst closing over this network, one peer::T report per declared peer"),
                                """
                                a machine's network: the endpoints the system knows about, the peers the
                                current machine has dialed, and the handlers that discover them. Membership is
                                declared, never emergent — a uri must not be able to make itself a peer merely
                                by being addressed — and it is accumulating, so a child machine unions its own
                                level over its parent's. The authorities this machine answers to are derived
                                from the host every mounted space declares, so identity has exactly one
                                source and cannot drift.
                                """,
                                "network::[name=>'local',roster=>[b=><ws://192.168.1.42:8555>],source=>[mdns=></m/web/mdns>]] [-- a network that knows a peer before dialing it --]",
                                "network::[peer=>[<ws://localhost:39111>=>[authority=><ws://localhost:39111/worker>]]] [-- a machine whose reach is the one node it holds --]"),
                        MACH_MACHINE_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(MACH_MACHINE_TID)
                                        .isaPredicate(rec(
                                                uri(INSTSET).maybe().asUri(), INSTSET_TYPE,
                                                uri(COMPILER).maybe().asUri(), MACH_COMPILER_TYPE,
                                                uri(MEMORY).maybe().asUri(), MACH_MEMORY_TYPE,
                                                uri(NETWORK).maybe().asUri(), MACH_NETWORK_TYPE,
                                                uri(PROCESSOR).maybe().asUri(), MACH_PROCESSOR_TYPE))
                                        .constructor(arg -> BasicMachine.of(MACH_MACHINE_TID, arg.vid()))
                                        .create(), Map.of(uri(INSTSET), "the machine's instruction set architecture",
                                        uri(COMPILER), "the lowering axis: parser, rewriter, resolver, and typer",
                                        uri(MEMORY), "the machine's address space, bounded by the parent machine",
                                        uri(NETWORK), "the known machines and this machine's peers",
                                        uri(PROCESSOR), "the execution axis: threads and computational state"),
                                """
                                a machine integrates language, structure, and process into one context for action
                                within metatron. machines form a nested stack: a parent machine's state is reachable
                                from its children, and a parent defines the scope of a child machine's access to
                                resources. machines communicate laterally through the network component, where a
                                peer group forms a shared computing workspace that is garbage collected when the
                                machine is popped off the parent stack. the absolute root of the metatron graph
                                (/.) is the root machine.
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
                                "core::[code=>ping(<phaseshift.studio:80>),loop=>second::1.0]@~/thread/ping"),
                        MACH_VIRTUAL_THREAD_TYPE = docWrap(Type.Builder.build()
                                        .tid(MACH_THREAD_TID)
                                        .vid(MACH_VIRTUAL_THREAD_TID)
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MACH_VIRTUAL_THREAD_TID), lst(T(REC_TID)),
                                                (lhs, inst) -> new VirtualThread(inst.arg(0).jvm(), MACH_VIRTUAL_THREAD_TID, inst.arg(0).vid()).applyAsync(lhs)))
                                        .create(), null, null, Map.of(),
                                "run a concurrent virtual thread",
                                "virtual::[code=>ping(<phaseshift.studio:80>),loop=>second::1.5]@~/thread/ping")),
                uri(INST), lst(Stream.concat(Stream.empty(), Stream.of(
                        instC(THREAD_INST_TID.dom(ALL.maybe()).rng(MACH_THREAD_TID), lst(T(ALL)), (lhs, inst) -> {
                            final fURI baseVID = f("~/thread");
                            final VirtualThread thread = new VirtualThread(mutableMap(uri(CODE), inst.arg(0)), MACH_VIRTUAL_THREAD_TID, CommonUtil.mintShortUUID(baseVID, true));
                            final AbstractThread parent = BootLoader.CURRENT_THREAD.get();
                            if (null != parent && null != parent.vid())
                                thread.jvm().put(uri(SOURCE), auto_from_(uri(parent.vid())).tryToInst());
                            thread.applyAsync(lhs);
                            return thread;
                        }),
                        // PUSH AT AN ADDRESS IS move-to-its-PARENT then push-its-NAME — the algebra Marko gave:
                        // push(<../b>) == move(<..>).push(<b>). So the frame that HOLDS the new machine is the one
                        // at the address's parent, and only the LEAF is minted: a sibling comes from the shared
                        // parent, never from beside it. Nothing in Machine.push had to change for this — it mints
                        // a child of the machine it is CALLED ON, so the memory and instset unions it builds come
                        // from that parent, which is exactly where the new frame belongs.
                        docWrap(instC(PUSH_INST_TID.dom(ALL.maybe()).rng(MACH_MACHINE_TID), lst(T(URI_TID.maybe())), (lhs, inst) -> {
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
                                    return pushed;
                                }),
                                "the lhs is not consulted: push acts on the machine the code is running in, and carries the value flowing through untouched",
                                "the machine just minted and entered \\(c\\)",
                                Map.of(jnt(0), "\\(t\\), the address to mint. The machine at that address's parent holds the new machine and only \\(\\mathrm{name}(t)\\) is newly made, so \\(\\mathrm{push}(t) = \\mathrm{move}(\\mathrm{parent}(t)) \\cdot \\mathrm{push}(\\mathrm{name}(t))\\); absent, an anonymous child is minted instead"),
                                "mint a machine at an address in the machine tree and make it current. Constructive — it adds a machine, where move only chooses one. Because the leaf is minted from the address's parent, push can cross a level as well as descend into one, and a sibling is therefore minted from the shared parent rather than from beside it. A wire address carrying an authority is a different boundary, one a machine does not cross: code reaches another process by being shipped there in a plan, so the address is refused.",
                                "push(xyz)                              [-- mint /xyz --]",
                                "push(<../b>)                           [-- the sibling b, minted from the shared parent: = move(<..>).push(b) --]",
                                "push(<a/b>)                            [-- the descendant /a/b, minted from the machine at /a --]",
                                "push()                                 [-- an anonymous child, \\(c = \\mathrm{mintShortUUID}(\\mathrm{vid}(\\mathrm{here}))\\) --]",
                                "push(<ws://localhost:8555/worker>)     [-- refused: a process boundary, not a place in this tree --]"),
                        // POP NAMES THE FRAME YOU STAND IN. `pop()` and `pop(<~>)` are ONE instruction, and an
                        // addressed pop must still RESOLVE to where you stand: the motif is understood (`..`
                        // folds, `~` is the base) but a frame you can only reach by ADDRESS is not yours to
                        // discard from here — the stack discipline says a frame is discarded by the one that
                        // holds it, which is why `pop(<..>)` (the parent) is refused too, not just a sibling.
                        // Reaching a frame you OWN but have stepped out of needs the downward link the frame
                        // tree does not have yet (t-murdnd40-rpdahi), and closing an address-read handle would
                        // silently do nothing — so it is refused loudly rather than appearing to work.
                        docWrap(instC(POP_INST_TID.dom(ALL.maybe()).rng(MACH_MACHINE_TID), lst(T(ALL.maybe())), (lhs, inst) -> {
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
                                    return landed;
                                }),
                                "the lhs is not consulted: pop acts on the machine the code is running in",
                                "the machine that held the one discarded — \\(\\mathrm{pop}(f) \\mapsto \\mathrm{parent}(f)\\), so the current machine becomes \\(f\\)'s \\(..\\)",
                                Map.of(jnt(0), "the machine to discard, which must resolve relative to the current machine: it is admitted only when \\(t \\sim \\mathrm{here}\\), so `pop(<../b>)` from \\(a\\) is refused. Absent, \\(t = \\mathrm{here}\\) — and `pop()` = `pop(<~>)` = `pop(_)`"),
                                "destructive — the one machine instruction that closes what it leaves, where move leaves it standing and reachable by address. It discards the machine the code is running in and lands on that machine's \\(..\\). A machine is discarded by the one that holds it, so an address that does not resolve to the current machine is refused rather than silently ignored, and the root has no \\(..\\) to land on: it owns the address space, so closing it is the shutdown path and a reset is the honest instruction. Because \\(\\mathrm{move}\\) keeps no history and the containment link is what \\(\\mathrm{pop}\\) follows, a machine the code only moved into has nowhere to return to.",
                                "pop()               [-- discard the current machine, land on its .. --]",
                                "pop(<~>)            [-- the same instruction: ~ is the current machine --]",
                                "pop(_)              [-- and the same again with the lhs as the argument --]",
                                "pop(<../b>)         [-- refused: b is not the current machine --]",
                                "pop()               [-- at the root: refused, reset metatron instead --]"),
                        // move() is move(<..>): the NON-destructive way up, and the whole point of the motif —
                        // pop() destroys the frame it leaves, move() leaves it standing and reachable by address
                        docWrap(instC(MOVE_INST_TID.dom(ALL.maybe()).rng(MACH_MACHINE_TID), lst(T(URI_TID.maybe())), (lhs, inst) -> {
                                    final Machine moved = Machine.current(Helper.machineAt(Machine.current(),
                                            inst.arg(0).isNoObj() ? UP : inst.arg(0).uriValue(), "move"));
                                    MachinePerspective.publish(moved);
                                    return moved;
                                }),
                                "the lhs is not consulted: move chooses the machine the next code runs in",
                                "the machine moved to — \\(\\mathrm{move}(t) \\mapsto f\\) where \\(f\\) is the machine \\(t\\) addresses",
                                Map.of(jnt(0), "\\(t\\), the address to stand in: `~` is the current machine, `a` (or `~/a`) its child, `..` its parent, `../b` the sibling \\(b\\), and `../../..` walks up. Absent, \\(t = \\;..\\) — bare `move()` is `move(<..>)`"),
                                "choose where the next inst will evaluate, and close nothing. The address is folded against the current machine, and an address that walks past the root saturates there rather than failing.",
                                "move(<..>)          [-- up: the parent machine, without closing this one --]",
                                "move()              [-- the same instruction --]",
                                "move(<../b>)        [-- across, to the sibling b --]",
                                "move(<a/c>)         [-- down a composed path in one step --]",
                                "move(<~/..>)        [-- ~ is the current machine, consumed before the fold: the parent --]",
                                "move(<../../..>)    [-- past the root: saturates at the root --]"),
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
