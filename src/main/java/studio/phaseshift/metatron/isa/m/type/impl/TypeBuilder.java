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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Algebra;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BUILD A TYPE IN ONE CHAIN — the friendly surface over {@code MType.T}, the sibling of {@link InstBuilder}.
 *
 * <p>A type is DECLARED by its two addresses ({@code tid} = the representation it refines, {@code vid} = the name it
 * answers to), its refinement (a predicate and/or a constructor), the instructions it introduces, its ALGEBRA (the
 * theory instances it models) and its documentation. One chain states all of it:
 *
 * <pre>{@code
 * TypeBuilder.build().tid(INT_TID).vid(INT_TID, "a 64-bit signed integer")
 *         .example("123.plus(456) [-- 579 --]")
 *         .add(plus).mul(mult).zero(jnt(0)).one(jnt(1))
 *         .create();
 * }</pre>
 *
 * <p>A TYPE IS A WRITE, NOT A REGISTRATION. Creating one means writing {@code (vid, type)} — plus the declaration
 * that sits beside it, its algebra at {@code vid?decq} and its documentation at {@code vid?docq} — because the
 * instruction set that OWNS the address is what indexes it. That is how mtron creates a type (a write through
 * {@code Machine}), and Java takes the same road here rather than keeping a private registry beside it.
 *
 * <p>AND THE WRITE IS QUEUED. A type is created during its instruction set's static initialization, before a machine
 * exists and before that set's space is mounted, so {@link #create()} composes the value and hands the writes to
 * {@link PendingWrites}; the owning set publishes them when it mounts ({@code PendingWrites.drainUnder} in
 * {@code AbstractInstSet.setup}). NOTHING in this class touches a machine, a space or a q-proc.
 *
 * <p>DOCUMENTATION IS INLINE, exactly as in {@link AbstractObjBuilder}: the doc string is the second argument of the
 * declaration it documents ({@code vid(vid, "…")}, {@code predicate(call, "…")}, {@code inst(tid, args, func, "…")}).
 * The composed doc rec is the same shape {@code QCollection.Docs} writes — {@code obj}, {@code desc}, {@code example},
 * and the instruction slots {@code dom}/{@code rng}/{@code args} where the object is an instruction. A TYPE states
 * those same slots from its own refinement: {@link #refinement(String, String)} is the prose of its predicate and
 * constructor sides, and {@link #slots(Map)} (or {@link #isaPredicate(Obj, Map)}) says what each slot of its shape is.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class TypeBuilder extends AbstractObjBuilder<TypeBuilder, Type> {

    /**
     * WHAT EACH SLOT OF THE TYPE IS, keyed by the slot's own key — stated through {@link #isaPredicate(Obj, Map)},
     * {@link #isaPredicate(DocArg...)}, {@link #slots(Map)} or {@link #constructor(Call, Map)}, and published with the
     * type's documentation at {@code ?docq} (the {@code args} slot), so the doc describes the shape the type claims.
     */
    private final Map<Obj, String> slotDocs = new LinkedHashMap<>();

    /**
     * THE PROSE OF THE TYPE'S TWO REFINEMENT SIDES — what its predicate accepts and what its constructor produces.
     * A type has no dom/rng SLOTS, so these are not a contract: they are the two lines the documentation renders as
     * the type's signature, carried in the doc rec's {@code dom}/{@code rng} slots.
     */
    private String predicateDoc;
    private String constructorDoc;

    /**
     * THE LAW-BLOCK NAMES of the conventional theory instances the role sugar composes — the same spellings
     * {@code mTypes} writes in its own declarations ({@code ring}, {@code add_group}, …), never new vocabulary.
     */
    private static final String RING = "ring";
    private static final String ADD_GROUP = "add_group";
    private static final String ADD_MONOID = "add_monoid";
    private static final String MULT_MONOID = "mult_monoid";

    /**
     * THE INSTRUCTIONS A TYPE DECLARED — the {@code .inst(...)} rows of every builder, keyed by the declaring type's
     * vid. The rows are staged here because a type is built before the instruction set that registers it is mounted,
     * and the {@link #drain(Type, InstSet)} of that set is what appends them to its OWN {@code inst} declaration list
     * (the list that answers "what did this instruction set provide", and the one a reader walks to attribute a
     * contract to its author). That question is not "whose address is it at": memory routes a write to the most
     * specific instruction set, so a set that declares {@code as?point<=str} writes that contract into /m's table.
     * <p>
     * Staging only — the instruction itself is already queued by {@link #create()}, and each VM boot re-records the
     * declaration, so the map is never drained.
     */
    private static final Map<fURI, Set<Inst>> INSTS = new ConcurrentHashMap<>();

    /**
     * FORGET THE PER-ADDRESS ATTRIBUTION of the instructions a type declared — the build-time record
     * {@code drain(Type, InstSet)} replays into a set's own {@code inst} list. Part of
     * {@code BootLoader.resetCaches()}.
     */
    public static void reset() {
        INSTS.clear();
    }

    /**
     * The type's refinement: what an obj must satisfy to BE an instance of this type.
     */
    private Call predicate;

    /**
     * The type's constructor: how a raw obj is lifted into an instance of this type.
     */
    private Call constructor;

    /**
     * The algebra an {@link #algebra(Algebra)} call stated, or null when the role sugar ({@link #add}/{@link #mul}/…)
     * is what composes one.
     */
    private Algebra algebra;

    /**
     * The instructions the type declares, in declaration order, each with the documentation it stated.
     */
    private final List<Declared> insts = new ArrayList<>();

    /**
     * The ROLE BINDINGS of the algebra vocabulary — {@code add}, {@code mul}, {@code zero}, {@code one}, {@code inv}
     * as the type declares them. They are bound by the role methods and composed into a conventional theory instance
     * by {@link #composedAlgebra()}; a declaration that names them instead of writing the block is the same algebra.
     */
    private Inst add;
    private Inst mul;
    private Inst inv;
    private Obj zero;
    private Obj one;

    private TypeBuilder() {
    }

    /**
     * A new, empty builder — the entry point the fluent chain starts from.
     */
    public static TypeBuilder build() {
        return new TypeBuilder();
    }

    /**
     * ONE DECLARED INSTRUCTION: the instruction itself (which is written at its own contract address) and the doc
     * string it stated, if any. A record rather than a map entry because an instruction's EQUALITY is its operation
     * ({@code equals}/{@code hashCode} compare through the tid minus the {@code args} component), so two contracts of
     * one operation would collide as map keys.
     */
    private record Declared(Inst inst, String desc, Map<Obj, String> argDocs) {
    }

    // ============================ the refinement ============================

    /**
     * State the type's predicate — the {@code dom}/{@code rng}-shaped call an obj is held to.
     */
    public TypeBuilder predicate(final Call predicate) {
        this.predicate = predicate;
        return this;
    }

    /**
     * {@link #predicate(Call)} with the type's description inline.
     */
    public TypeBuilder predicate(final Call predicate, final String desc) {
        this.predicate = predicate;
        this.description = desc;
        return this;
    }

    /**
     * State the type's predicate as the JVM function that decides it. The type must already be NAMED ({@code vid})
     * and REFINED ({@code tid}): the predicate's contract is minted against both of them.
     */
    public TypeBuilder predicate(final BiFunction<Obj, Inst, Obj> predicate) {
        if (null == this.vid)
            throw MTronException.of("vid must be set prior to specifying predicate");
        if (null == this.tid)
            throw MTronException.of("tid must be set prior to specifying predicate");
        return this.predicate(instC(Tokens.INST_PRED_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(Tokens.ALL_STAR)), predicate));
    }

    /**
     * State the type's predicate as a structural {@code isa} claim — the rec/obj-shape refinement a structural type
     * carries ({@code rec(uri("role"), URI_TYPE)}). Its contract is minted here so the caller states the shape alone.
     */
    public TypeBuilder isaPredicate(final Obj predicate) {
        this.slotDocs.clear();
        return this.predicate(isa_(predicate).tryToInst());
    }

    /**
     * State the type's two REFINEMENT SIDES as prose — what its predicate accepts and what its constructor produces.
     * A type has no dom/rng SLOTS (those belong to an instruction's contract); its two sides are its predicate and its
     * constructor, and the doc rec's {@code dom}/{@code rng} slots are where their prose lives, so the documentation
     * renders the type as the signature {@code predicate => constructor}.
     * <p>
     * The strings are prose only: null or blank omits that side, exactly as {@code QCollection.Docs.doc} composes it,
     * and the authored prose is preserved verbatim (no prefix is added here).
     */
    public TypeBuilder refinement(final String predicateDoc, final String constructorDoc) {
        this.predicateDoc = predicateDoc;
        this.constructorDoc = constructorDoc;
        return this;
    }

    /**
     * {@link #refinement(String, String)} together with the docs of the type's SLOTS — the whole shape a
     * {@code docWrap(type, predicateProse, constructorProse, slots, description, examples…)} call stated in one
     * breath, stated by the builder that owns the declaration instead.
     */
    public TypeBuilder refinement(final String predicateDoc, final String constructorDoc,
                                  final Map<Obj, String> slotDocs) {
        return this.refinement(predicateDoc, constructorDoc).slots(slotDocs);
    }

    /**
     * State WHAT EACH SLOT OF THE TYPE IS — one line per slot, keyed by the slot's own key and published in the doc
     * rec's {@code args} slot. A key addresses the type's shape: a predicate's top-level key, a key nested below one,
     * or a position when the shape is a declared list. Accumulates across calls, so a chain may state the slots where
     * they are declared.
     * <p>
     * An {@code isa} type that states its predicate AND its slot docs in one call uses
     * {@link #isaPredicate(Obj, Map)}; this is the form for a shape whose keys are not the predicate's own top-level
     * keys, and for the constructor's arguments ({@link #constructor(Call, Map)}).
     */
    public TypeBuilder slots(final Map<Obj, String> slotDocs) {
        if (null != slotDocs)
            this.slotDocs.putAll(slotDocs);
        return this;
    }

    /**
     * State the type's {@code isa} claim and say WHAT EACH PREDICATE SLOT IS — the arg docs travel with the
     * declaration and are published with the type at {@code ?docq} (the {@code args} slot). The map is keyed by the
     * predicate's own keys, so a doc can never describe a slot the predicate does not have.
     */
    public TypeBuilder isaPredicate(final Obj predicate, final Map<Obj, String> argDocs) {
        this.slotDocs.clear();
        this.slots(argDocs);
        return this.predicate(isa_(predicate).tryToInst());
    }

    /**
     * ONE PREDICATE SLOT AND ITS DOCUMENTATION — the key the claim is made at, the type the value must satisfy, and
     * the one-line description of what that slot IS. A triple, not three parallel lists, so the three cannot drift.
     */
    public record DocArg(Obj key, Obj type, String doc) {
        public DocArg {
            if (null == key)
                throw MTronException.of("a predicate slot must be named");
        }

        public static DocArg of(final Obj key, final Obj type) {
            return new DocArg(key, type, null);
        }

        public static DocArg of(final Obj key, final Obj type, final String doc) {
            return new DocArg(key, type, doc);
        }
    }

    /**
     * State the type's {@code isa} claim as a TABLE OF SLOTS, each slot carrying its own description — the form
     * {@code machInstSet} uses for {@code machine::T}:
     * <pre>{@code
     * .isaPredicate(
     *     DocArg.of(uri(INSTSET).maybe().asUri(),  INSTSET_TYPE,       "the machine's instruction set architecture"),
     *     DocArg.of(uri(COMPILER).maybe().asUri(), MACH_COMPILER_TYPE, "the lowering axis: parser, rewriter, resolver, typer"))
     * }</pre>
     * A record per slot rather than a positional {@code Object...}: the arity and the types are checked by the
     * compiler, a missing doc is a null field rather than a shifted triple, and the call reads as the table it is.
     */
    public TypeBuilder isaPredicate(final DocArg... args) {
        final Map<Obj, Obj> predicate = new LinkedHashMap<>();
        this.slotDocs.clear();
        if (null != args)
            for (final DocArg arg : args) {
                if (null == arg)
                    continue;
                predicate.put(arg.key(), arg.type());
                if (null != arg.doc())
                    this.slotDocs.put(arg.key(), arg.doc());
            }
        return this.predicate(isa_(rec(predicate)).tryToInst());
    }


    /**
     * State the type's constructor — the call that lifts a raw obj into an instance of this type.
     */
    public TypeBuilder constructor(final Call constructor) {
        this.constructor = constructor;
        return this;
    }

    /**
     * {@link #constructor(Call)} with the type's description inline.
     */
    public TypeBuilder constructor(final Call constructor, final String desc) {
        this.constructor = constructor;
        this.description = desc;
        return this;
    }

    /**
     * {@link #constructor(Call)} with the docs of its ARGUMENTS. A constructor IS an instruction
     * ({@code INST_CTOR_TID}), so what it reads is the doc rec's {@code args} slot — stated at the constructor the docs
     * belong to, rather than at the type whose name happens to carry them.
     */
    public TypeBuilder constructor(final Call constructor, final Map<Obj, String> argDocs) {
        this.constructor = constructor;
        return this.slots(argDocs);
    }

    /**
     * State the constructor as the JVM function that lifts one obj. The type must already be NAMED ({@code vid}): the
     * constructor's range is the type's own address.
     */
    public TypeBuilder constructor(final Function<Obj, Obj> function) {
        return this.constructor(function, null);
    }

    /**
     * {@link #constructor(Function)} with the docs of its ARGUMENTS — the JVM-function form of
     * {@link #constructor(Call, Map)}, for a constructor minted here rather than stated as an instruction.
     */
    public TypeBuilder constructor(final Function<Obj, Obj> function, final Map<Obj, String> argDocs) {
        if (null == this.vid)
            throw MTronException.of("vid must be set prior to specifying constructor");
        return this.constructor(instC(Tokens.INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(T(Tokens.ALL_STAR)), (lhs, inst) -> function.apply(inst.arg(0))), argDocs);
    }

    /**
     * State a CONSTANT constructor — the call that answers the same value every time. The type must already be NAMED
     * ({@code vid}), and the constructor takes no arguments (it has none to read).
     */
    public TypeBuilder constructor(final Supplier<Obj> supplier) {
        if (null == this.vid)
            throw MTronException.of("vid must be set prior to specifying constructor");
        return this.constructor(instC(Tokens.INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(), (lhs, inst) -> supplier.get()));
    }

    // ============================ the algebra ============================

    /**
     * DECLARE the type's algebra — the theory instances it models, written as the algebra itself ({@code
     * Algebra.of(rec(uri("ring"), rec(…)))}). It is persisted beside the type at {@code vid?decq}, which is the store
     * of record a reader ({@code Machine.read}, {@code TheoryHelper.instances}) answers from.
     */
    public TypeBuilder algebra(final Algebra algebra) {
        this.algebra = algebra;
        return this;
    }

    /**
     * {@link #algebra(Algebra)} with the type's description inline.
     */
    public TypeBuilder algebra(final Algebra algebra, final String desc) {
        this.algebra = algebra;
        this.description = desc;
        return this;
    }

    /**
     * {@link #algebra(Algebra)} for a caller holding the algebra as the rec it already is — the shape
     * {@code mTypes} and the law tables spell ({@code Algebra.of} is the identity on an algebra).
     */
    public TypeBuilder law(final Rec law) {
        return this.algebra(Algebra.of(law));
    }

    /**
     * Bind the ADDITIVE operation of the type's algebra ({@code plus} for a numeric carrier, {@code branch} for
     * code). Composes with {@link #mul}/{@link #zero}/{@link #one}/{@link #inv} into the conventional theory
     * instance the roles describe — see {@link #composedAlgebra()}.
     */
    public TypeBuilder add(final Inst op) {
        this.add = op;
        return this;
    }

    /**
     * {@link #add(Inst)} with the type's description inline.
     */
    public TypeBuilder add(final Inst op, final String desc) {
        this.add = op;
        this.description = desc;
        return this;
    }

    /**
     * Bind the MULTIPLICATIVE operation of the type's algebra ({@code mult} for a numeric carrier, {@code compose}
     * for code).
     */
    public TypeBuilder mul(final Inst op) {
        this.mul = op;
        return this;
    }

    /**
     * {@link #mul(Inst)} with the type's description inline.
     */
    public TypeBuilder mul(final Inst op, final String desc) {
        this.mul = op;
        this.description = desc;
        return this;
    }

    /**
     * Bind the ADDITIVE IDENTITY of the type's algebra ({@code 0} for a numeric carrier, {@code end} for code).
     */
    public TypeBuilder zero(final Obj zero) {
        this.zero = zero;
        return this;
    }

    /**
     * {@link #zero(Obj)} with the type's description inline.
     */
    public TypeBuilder zero(final Obj zero, final String desc) {
        this.zero = zero;
        this.description = desc;
        return this;
    }

    /**
     * Bind the MULTIPLICATIVE IDENTITY of the type's algebra ({@code 1} for a numeric carrier, {@code id} for code).
     */
    public TypeBuilder one(final Obj one) {
        this.one = one;
        return this;
    }

    /**
     * {@link #one(Obj)} with the type's description inline.
     */
    public TypeBuilder one(final Obj one, final String desc) {
        this.one = one;
        this.description = desc;
        return this;
    }

    /**
     * Bind the ADDITIVE INVERSE of the type's algebra ({@code neg} for int/real, {@code inv} for real's
     * multiplicative side, the coefficient complement for code).
     */
    public TypeBuilder inv(final Inst op) {
        this.inv = op;
        return this;
    }

    /**
     * {@link #inv(Inst)} with the type's description inline.
     */
    public TypeBuilder inv(final Inst op, final String desc) {
        this.inv = op;
        this.description = desc;
        return this;
    }

    // ============================ the declared instructions ============================

    /**
     * DECLARE an instruction the type introduces — {@code as?point<=str} is declared by {@code point::T} although the
     * contract lives in /m's table. The instruction is written at its own contract address and its declaration is
     * recorded against the registering set (see {@link #drain(Type, InstSet)}).
     */
    public TypeBuilder inst(final fURI tid, final Poly<?, ?> args, final BiFunction<Obj, Inst, Obj> func) {
        return this.inst(tid, args, func, null);
    }

    /**
     * {@link #inst(fURI, Poly, BiFunction)} with the instruction's documentation inline — its {@code desc} and the
     * {@code dom}/{@code rng}/{@code args} slots read off the contract it was minted with.
     */
    public TypeBuilder inst(final fURI tid, final Poly<?, ?> args, final BiFunction<Obj, Inst, Obj> func, final String desc) {
        return this.inst(instC(tid, args, func), desc, argDocs(args));
    }

    /**
     * DECLARE an already-built instruction — the form for an instruction minted elsewhere (by {@link InstBuilder} or
     * by mtron), which carries a contract this builder must not restate.
     */
    public TypeBuilder inst(final Inst inst) {
        return this.inst(inst, null, Map.of());
    }

    /**
     * The one routing point for a declared instruction: remember it with the documentation it stated.
     */
    private TypeBuilder inst(final Inst inst, final String desc, final Map<Obj, String> argDocs) {
        if (null != inst)
            this.insts.add(new Declared(inst, desc, argDocs));
        return this;
    }

    // ============================ creation ============================

    /**
     * CREATE the type: compose it from everything the chain stated and QUEUE the writes that publish it — the type at
     * its {@code vid}, its documentation at {@code vid?docq}, its algebra at {@code vid?decq} — plus, for every
     * instruction the type declared, the instruction itself at its contract address, its claim at {@code ?decq} and
     * its documentation at {@code ?docq}. Nothing is written through a machine here: the owning instruction set
     * publishes the queue when its space is mounted.
     *
     * @return the type, exactly the value that will land at {@link #vid()}
     */
    @Override
    public Type create() {
        if (null == this.tid)
            throw MTronException.of("tid must be set before creating a type");
        final Type type = T(Tuple.Pair.with(this.predicate, this.constructor), this.tid, this.vid);
        // A WRITE OF NOTHING IS NOT A WRITE: a {0}-coefficient address names the empty multiplicity (noobj::T's own
        // address is one), so there is no location to publish a type at and the write is skipped rather than staged.
        if (null != this.vid && !this.vid.c().isZero()) {
            PendingWrites.write(this.vid, type);
            if (null != this.description || !this.examples.isEmpty() || !this.slotDocs.isEmpty()
                    || null != this.predicateDoc || null != this.constructorDoc) {
                final fURI docAddress = PendingWrites.docq(this.vid);
                final Rec doc = docRec(type, this.predicateDoc, this.constructorDoc,
                        this.slotDocs, this.description, this.examples.toArray(String[]::new));
                PendingWrites.write(docAddress, doc);
            }
            final Algebra declared = null == this.algebra ? this.composedAlgebra() : this.algebra;
            if (null != declared)
                PendingWrites.write(PendingWrites.decq(this.vid),
                        rec(mutableMap(uri(Tokens.OBJ), type, uri(Tokens.THEORY), declared), QCollection.DECS_TID, null));
            // ... AND STAGE WHO DECLARED THE INSTRUCTIONS, for the registering set to record in its own `inst` list
            if (!this.insts.isEmpty())
                INSTS.computeIfAbsent(this.vid.basePath(), _key -> ConcurrentHashMap.newKeySet())
                        .addAll(this.insts.stream().map(Declared::inst).toList());
        }
        for (final Declared declared : this.insts) {
            final Inst inst = declared.inst();
            PendingWrites.write(inst.tid(), inst);
            if (null != declared.desc())
                PendingWrites.write(PendingWrites.docq(inst.tid()),
                        docRec(inst, inst.tid().dom().toString(), inst.tid().rng().toString(), declared.argDocs(), declared.desc()));
        }
        return type;
    }

    /**
     * CREATE the type AND REGISTER IT — the form an instruction set uses for the types it declares up front: the
     * type is added to the caller's {@code type} collection and the instructions it declared to its {@code inst}
     * collection, which are the collections {@code InstSet.types()} and {@code InstSet.insts()} answer with.
     * <p>
     * The declaration itself is still the one {@link #create()} queues: the collected type and insts are the level's
     * DECLARED list, and the queued writes are what publish them when the level mounts, so the two agree rather than
     * duplicate.
     *
     * @param typeSet the collection the created type is registered in
     * @param instSet the collection the instructions the type declared are registered in
     * @return the created type
     */
    public Type create(final Set<Type> typeSet, final Set<Inst> instSet) {
        final Type type = this.create();
        if (null != typeSet)
            typeSet.add(type);
        if (null != instSet)
            instSet.addAll(this.insts.stream().map(Declared::inst).toList());
        return type;
    }

    // ============================ declaration of an already-built type ============================

    /**
     * DECLARE a type's algebra AFTER the type was built — the form for a type whose declaration must sit below the
     * addresses it points at (a field initializer may not read a field declared later, and a theory block names
     * instruction addresses that only exist once their set has initialized). The write is queued at
     * {@code <vid>?decq} exactly as {@link #algebra(Algebra)} would have queued it.
     *
     * @param type    the already-built type
     * @param algebra the theory instances it models
     */
    public static void declare(final Type type, final Algebra algebra) {
        if (null == type)
            return;
        declare(type.vid(), algebra);
    }

    /**
     * {@link #declare(Type, Algebra)} by ADDRESS — the form for a type this class does not hold as a {@link Type}
     * object, e.g. a THEORY type, whose only fact a declaration keys on is its vid.
     *
     * @param vid     the type's address
     * @param algebra the theory instances it models
     */
    public static void declare(final fURI vid, final Algebra algebra) {
        if (null == vid || null == algebra)
            return;
        PendingWrites.write(PendingWrites.decq(vid),
                rec(mutableMap(uri(Tokens.OBJ), T(vid), uri(Tokens.THEORY), algebra), QCollection.DECS_TID, null));
    }

    /**
     * PUBLISH a type's standing declaration at THIS mount — its algebra ({@code ?decq}) and its documentation
     * ({@code ?docq}) — the call the registering set makes for every type it holds, beside the instruction
     * attribution below. A type that declared neither is untouched.
     * <p>
     * THE RETAINED LOG IS WHAT REPLAYS IT: {@code PendingWrites} keeps every staged write, so the set's own
     * {@code drainUnder} at mount publishes the type, its algebra and its documentation again, into whatever fresh
     * space that mount built. The one thing published here is the INSTRUCTIONS the type declared, because a contract
     * may live at another set's address, beyond that drain.
     *
     * @param type    the type whose declaration is republished by the mount's drain
     * @param instSet the instruction set that registers it (null replays the declaration alone)
     */
    public static void drain(final Type type, final InstSet instSet) {
        if (null == type || null == type.vid())
            return;
        if (null == instSet)
            return;
        final Set<Inst> insts = INSTS.get(type.vid().basePath());
        if (null == insts || insts.isEmpty())
            return;
        // KEYED BY FULL TID: one operation name is many contracts, and they are distinguished by their
        // dom/rng/args — the same identity the instset's own tables use.
        final Map<String, Inst> rows = new LinkedHashMap<>();
        final Obj existing = instSet.jvm().get(uri(Tokens.INST));
        if (null != existing && existing.isLst())
            for (final Obj row : existing.lstValue())
                if (row.isInst())
                    rows.putIfAbsent(row.tid().toString(), row.as());
        insts.forEach(inst -> rows.putIfAbsent(inst.tid().toString(), inst));
        // A DIRECT PUT, NEVER `at(..., MUTABLE)`: Rec.at APPENDS when the entry already holds an Objs,
        // which doubles the coefficients on every registration (the trap BasicInstSet.refer documents).
        instSet.jvm().put(uri(Tokens.INST), lst(new ArrayList<>(rows.values())));
        // ... AND THE CONTRACTS THEMSELVES. A type may declare a contract at ANOTHER set's address (geo:point's
        // as(json::T) lives at /m/inst/as?...), outside this set's drainUnder, and the set that owns that address
        // mounted before this type was built, so nothing else publishes it in this boot. The staged write is
        // published unless the owning table already holds that exact contract (a re-mount): an address read is no
        // test of that, since a contract's address also answers the contracts that subsume it.
        insts.stream().map(Inst::tid).distinct()
                .filter(tid -> Machine.read(tid.basePath()).stream().noneMatch(live -> tid.equals(live.tid())))
                .forEach(PendingWrites::publish);
    }

    // ============================ the composition rules ============================

    /**
     * THE CONVENTIONAL THEORY INSTANCE THE ROLES DESCRIBE — one instance, chosen by the ROLES PRESENT, in this
     * precedence:
     * <ul>
     *   <li>{@code add + mul + zero + one} → {@code ring} (with {@code inv} when the type also declares an additive
     *       inverse), the two-operation carrier: the block names its operations by the {@code add}/{@code mul}/
     *       {@code zero}/{@code one} roles, as {@code mTypes} declares int's and real's rings;</li>
     *   <li>{@code add + zero + inv} (no multiplicative side) → {@code add_group}, spelled with the group's
     *       {@code op}/{@code id}/{@code inv} roles ({@code TheoryHelper} reads a group's operation from {@code op});</li>
     *   <li>{@code add + zero} → {@code add_monoid}, spelled {@code op}/{@code id};</li>
     *   <li>{@code mul + one} (no additive side) → {@code mult_monoid}, spelled {@code op}/{@code id}.</li>
     * </ul>
     * An explicit {@link #algebra(Algebra)} OVERRIDES all of it: a caller who writes the block means exactly that
     * block, and the roles are then only documentation. Roles that describe none of these shapes compose NO algebra,
     * rather than a half-declared theory the validator would have to guess at.
     * <p>
     * The operations are held as {@code !*} AUTO pointers ({@code auto_from_}), not copied instructions: the block
     * names the operation the type binds, and the pointer is transparent to a reader
     * ({@code TheoryHelper.roleAddress}), so a declaration cannot go stale when the instruction is re-registered.
     */
    private Algebra composedAlgebra() {
        final boolean hasAdd = null != this.add;
        final boolean hasMul = null != this.mul;
        final boolean hasZero = null != this.zero;
        final boolean hasOne = null != this.one;
        final boolean hasInv = null != this.inv;
        if (!hasAdd && !hasMul)
            return null;
        if (hasAdd && hasMul && hasZero && hasOne) {
            final Map<Obj, Obj> roles = new LinkedHashMap<>();
            roles.put(uri(Tokens.ADD), pointer(this.add));
            roles.put(uri(Tokens.MUL), pointer(this.mul));
            roles.put(uri(Tokens.ZERO), this.zero);
            roles.put(uri(Tokens.ONE), this.one);
            if (hasInv)
                roles.put(uri(Tokens.INV), pointer(this.inv));
            return algebraOf(RING, roles, RING_THEORY_TID);
        }
        if (hasAdd && hasZero && hasInv && !hasMul && !hasOne) {
            final Map<Obj, Obj> roles = new LinkedHashMap<>();
            roles.put(uri(Tokens.OP), pointer(this.add));
            roles.put(uri(Tokens.ID), this.zero);
            roles.put(uri(Tokens.INV), pointer(this.inv));
            return algebraOf(ADD_GROUP, roles, GROUP_THEORY_TID);
        }
        if (hasAdd && hasZero && !hasMul && !hasOne) {
            final Map<Obj, Obj> roles = new LinkedHashMap<>();
            roles.put(uri(Tokens.OP), pointer(this.add));
            roles.put(uri(Tokens.ID), this.zero);
            return algebraOf(ADD_MONOID, roles, MONOID_THEORY_TID);
        }
        if (hasMul && hasOne && !hasAdd) {
            final Map<Obj, Obj> roles = new LinkedHashMap<>();
            roles.put(uri(Tokens.OP), pointer(this.mul));
            roles.put(uri(Tokens.ID), this.one);
            return algebraOf(MULT_MONOID, roles, MONOID_THEORY_TID);
        }
        return null;
    }

    /**
     * One theory instance, as the algebra declares it: the instance NAME keys a rec of its roles, and the rec is
     * typed by the theory it instantiates ({@code ring_theory::T}) — the shape {@code mTypes} writes and the shape
     * {@code TheoryHelper} reads.
     */
    private static Algebra algebraOf(final String name, final Map<Obj, Obj> roles, final fURI theory) {
        return Algebra.of(rec(mutableMap(uri(name), rec(mutableMap(roles), theory, null))));
    }

    /**
     * The {@code !*} AUTO pointer a role binds — the operation's own contract address, held as a pointer so the block
     * names the operation rather than copying it (the form {@code mTypes} uses).
     */
    private static Obj pointer(final Inst op) {
        return instB(AUTO_FROM_INST_TID, lst(uri(op.tid())));
    }

    // ============================ the recs a builder writes ============================

    /**
     * The documentation rec written at {@code ?docq} — the same shape {@code QCollection.Docs} stores
     * ({@code obj}, {@code desc}, {@code example}, and the {@code dom}/{@code rng}/{@code args} slots an instruction
     * fills). Composed here rather than through {@code docWrap} because a builder runs before any space exists.
     */
    private static Rec docRec(final Obj obj, final String dom, final String rng,
                              final Map<Obj, String> argDocs, final String desc, final String... examples) {
        return QCollection.Docs.doc(obj, dom, rng, argDocs, desc, examples);
    }

    /**
     * The {@code args} slot of an instruction's documentation — each declared argument type rendered by the type it
     * states, keyed by its position, the same map {@code QCollection.docWrap(Inst, …)} composes.
     */
    private static Map<Obj, String> argDocs(final Poly<?, ?> args) {
        final Map<Obj, String> docs = new LinkedHashMap<>();
        if (null == args || args.isNoObj())
            return docs;
        if (args.isRec())
            args.asRec().elements().forEach(rel -> docs.put(rel.first(), rel.second().tid().toString()));
        else
            args.asLst().indexedStream().forEach(entry -> docs.put(entry.first(), entry.second().tid().toString()));
        return docs;
    }
}
