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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Algebra;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ;
import static studio.phaseshift.metatron.furi.q.QCollection.DECS_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE /m TYPE DECLARATIONS — the sibling of {@link mInsts}, and the counterpart of {@code catTypes}: the instset's
 * file stays the instset (constants, sugar, the aggregation seam), while its TYPES are declared here, one line per
 * carrier, in the same shape {@code catTypes} uses.
 * <p>
 * A type is declared by its ADDRESSES ({@code tid} = the representation it refines, {@code vid} = its name), its
 * PREDICATE (the refinement) and — the axis this whole effort has been moving — its ALGEBRA ({@code law => ...}),
 * the theory instances the type models. The algebra is declared with {@code TypeBuilder} and persisted to
 * {@code ?decq} by the drain that runs when this set is mounted, which is why it can be written here, before any
 * space exists.
 * <p>
 * The ADDRESSES below are declared against the {@code Tokens} addresses only, so the fields depend on nothing in
 * {@link mInstSet} — no class-initialisation cycle, whatever loads first. The ALGEBRAS and the THEORY BATTERIES are
 * the exception and deliberately methods ({@link #declareAlgebras()}, {@link #declareAxioms()}): a rec names
 * instruction and theory addresses that only exist
 * once {@code mInstSet} has initialised, so it can be written here but not <em>initialised</em> here.
 * {@code mInstSet} re-exports the types by name for the (many) call sites that read {@code mInstSet.INT_TYPE}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mTypes {

    private mTypes() {
    }

    public static final Type BOOL_TYPE = TypeBuilder.build().tid(BOOL_TID).vid(BOOL_TID).create();

    public static final Type BYTES_TYPE = TypeBuilder.build().tid(BYTES_TID).vid(BYTES_TID).create();

    public static final Type INT_TYPE = TypeBuilder.build().tid(INT_TID).vid(INT_TID).create();

    public static final Type REAL_TYPE = TypeBuilder.build().tid(REAL_TID).vid(REAL_TID).create();

    public static final Type STR_TYPE = TypeBuilder.build().tid(STR_TID).vid(STR_TID).create();

    public static final Type URI_TYPE = TypeBuilder.build().tid(URI_TID).vid(URI_TID).create();

    /**
     * {@code law::T} — the process-law vocabulary as DATA: a {@code uri} is a law exactly when its own label's address
     * under {@link Law#LAW_TID} already holds a uri. The type is a NOMINAL refinement of {@code uri::T} at the
     * vocabulary's own address ({@code /m/law}), registered with the base set so it mounts with {@code /m} — the law
     * values live in the same jurisdiction, and the type is needed before the optional category renders it.
     * <p>
     * THE PREDICATE READS {@link Machine#current()}'s view through {@code Machine.read}: a law declared in a pushed
     * frame resolves there (the base {@code /m} is inherited), exactly as the wildcard read of {@code /m/law/+} answers
     * at the console — the read is the match, and no match is {@code noobj()}. The wildcard read is deliberate: the
     * store is the truth, so there is no second index (no set, no bloom) to keep in sync with it, and ~22 labels are
     * cheaper to scan than to hash. A predicate must never throw during boot, so a machine that is not up refuses
     * rather than reading.
     */
    final static Set<String> LAWS_SET = Stream.of(Law.values()).map(Enum::name).collect(Collectors.toSet());
    public static final Type LAW_TYPE = TypeBuilder.build()
            .tid(URI_TID)
            // NO DESCRIPTION HERE, deliberately: create() would compose a ?docq rec through QCollection.Docs, whose
            // static initialization reads mInstSet.AS_INST_TID — a field this class initializes BEFORE — so a
            // description here closes a class-initialisation cycle. The vocabulary's documentation is published by
            // Law.register() (at the type's address and at every law's address) once the machine is up instead.
            .vid(Law.LAW_TID)
            .predicate((lhs, inst) -> {
                if (!Machine.loaded() || null == lhs || !lhs.isUri())
                    return noobj();
                final String label = lhs.uriValue().name();
                // the preconstructed set answers the built-in vocabulary without a read; a law WRITTEN at
                // /m/law/<label> is admitted because law is where the uri lives, not a compiled-in list
                return LAWS_SET.contains(label) || !Machine.read(Law.LAW_TID.extend(label)).isNoObj()
                        ? lhs : noobj();
            })
            .create();

    public static final Type REL_TYPE = TypeBuilder.build().tid(REL_TID).vid(REL_TID).create();

    public static final Type LST_TYPE = TypeBuilder.build().tid(LST_TID).vid(LST_TID).create();

    public static final Type REC_TYPE = TypeBuilder.build().tid(REC_TID).vid(REC_TID).create();

    public static final Type INST_TYPE = TypeBuilder.build().tid(M_ISA_INST_TID).vid(M_ISA_INST_TID).create();

    public static final Type CODE_TYPE = TypeBuilder.build().tid(CODE_TID).vid(CODE_TID).create();

    public static final Type NOOBJ_TYPE = TypeBuilder.build()
            .tid(NOOBJ_TID.zero()).vid(NOOBJ_TID.zero())
            .predicate((lhs, inst) -> noobj())
            .create();

    /**
     * THE MACHINE'S ADDRESS — {@code machInstSet.MACH_MACHINE_TID} is exactly this expression, spelled here rather
     * than imported. It is the one address this file does not read from {@code Tokens}, and the reason is the same
     * class-initialisation cycle {@link #declareAlgebras()} exists to avoid, one instset over: reading that class's
     * constant while this set initialises observes the field at its default, because the machine's own set has not
     * reached it. The ADDRESS is the fact and it is a {@code Tokens} fact ({@code machInstSet} derives it the same
     * way), so {@code declareAlgebras()} can key the machine's type vid ({@code /m/mach/machine}) without touching
     * that class — and the machine's set still drains the declaration when it mounts.
     */
    private static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");

    /**
     * DECLARE EVERY CARRIER'S ALGEBRA — one {@code TypeBuilder.declare} per carrier, holding the theory instances
     * the carrier models, keyed by instance name ({@code ring}, {@code add_group}, …). Each role points at the LIVE
     * instruction through an {@code auto_from_} ({@code !*}) pointer, so the block names the operation rather than
     * copying it, and each instance is typed by its theory ({@code ring_theory::T}, …).
     * <p>
     * THIS IS A METHOD, NEVER STATIC FIELD INITIALIZERS: the recs name the instruction tids ({@code PLUS_INST_TID},
     * …, initialized in {@link mInstSet}) and the theory tids ({@code RING_THEORY_TID}, …). A field here would
     * close a class-initialisation cycle — this class initialises while {@code mInstSet} does, so a field reading
     * its constants can observe their defaults. {@code mInstSet}'s end-of-class static block invokes this after
     * every constant exists.
     * <p>
     * The declaration is STAGED only; {@code ?decq} is the store of record, written by the drain that runs when the
     * set is mounted, and {@code LawTable.typeLaws} serves it from there. The law table restates none of it.
     */
    public static void declareAlgebras() {
        // int: a ring — an add_group (plus/neg) and a mult_monoid (mult/1, no mult_group: integer division
        // truncates, so 5.div(2).mult(2) is 4) — with the monoid constituents spelled out
        declare(INT_TYPE, rec(mutableMap(
                uri("ring"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                uri(MUL), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                uri(Tokens.ZERO), jnt(0),
                                uri(Tokens.ONE), jnt(1)),
                        RING_THEORY_TID, null),
                uri("add_group"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                uri(ID), jnt(0),
                                uri(INV), auto_from_(NEG_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                        GROUP_THEORY_TID, null),
                uri("add_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                uri(ID), jnt(0)),
                        MONOID_THEORY_TID, null),
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                uri(ID), jnt(1)),
                        MONOID_THEORY_TID, null))));
        // real: a field — a ring where the non-zero mult elements are a group (inv = 1/x); plus the constituents
        declare(REAL_TYPE, rec(mutableMap(
                uri("field"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(MUL), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(Tokens.ZERO), real(0.0d),
                                uri(Tokens.ONE), real(1.0d),
                                uri(INV), auto_from_(INV_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                        FIELD_THEORY_TID, null),
                uri("ring"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(MUL), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(Tokens.ZERO), real(0.0d),
                                uri(Tokens.ONE), real(1.0d)),
                        RING_THEORY_TID, null),
                uri("add_group"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(ID), real(0.0d),
                                uri(INV), auto_from_(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                        GROUP_THEORY_TID, null),
                uri("mult_group"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(ID), real(1.0d),
                                uri(INV), auto_from_(INV_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                        GROUP_THEORY_TID, null),
                uri("add_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(ID), real(0.0d)),
                        MONOID_THEORY_TID, null),
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                uri(ID), real(1.0d)),
                        MONOID_THEORY_TID, null))));
        // bool: a Boolean algebra — or=join(PLUS), and=meet(MULT), not=complement — a complemented distributive
        // lattice and a rig (idempotent semiring) with bottom=false / top=true
        declare(BOOL_TYPE, rec(mutableMap(
                uri("boolean"), rec(mutableMap(
                                uri(OR), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(AND), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(NOT), auto_from_(NOT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(Tokens.ZERO), bool(false),
                                uri(Tokens.ONE), bool(true)),
                        BOOLEAN_THEORY_TID, null),
                uri("rig"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(MUL), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(Tokens.ZERO), bool(false),
                                uri(Tokens.ONE), bool(true)),
                        RIG_THEORY_TID, null),
                uri("lattice"), rec(mutableMap(
                                uri(MEET), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(JOIN), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                uri(BOTTOM), bool(false),
                                uri(TOP), bool(true)),
                        LATTICE_THEORY_TID, null))));
        // str: a monoid under concatenation (PLUS), identity ""
        declare(STR_TYPE, rec(mutableMap(
                uri("concat_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(STR_TID).rng(STR_TID)).tryToInst(),
                                uri(ID), str("")),
                        MONOID_THEORY_TID, null))));
        // uri: a monoid under extension (mult), identity the empty uri <>; branch (plus) is a commutative
        // semigroup with no identity — a/b + <> = {a/b,<>}, never a/b, so there is no zero object, only a one.
        declare(URI_TYPE, rec(mutableMap(
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                uri(ID), uri("")),
                        MONOID_THEORY_TID, null))));
        // bytes: a monoid under concatenation (PLUS), identity the empty buffer
        declare(BYTES_TYPE, rec(mutableMap(
                uri("concat_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID)).tryToInst(),
                                uri(ID), bytes(new byte[0])),
                        MONOID_THEORY_TID, null))));
        // lst: a NON-commutative monoid under concatenation (PLUS), identity the empty lst — [a]+[b] is [a,b]
        // while [b]+[a] is [b,a], which is why the theory is monoid, not commutative_monoid
        declare(LST_TYPE, rec(mutableMap(
                uri("concat_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(LST_TID).rng(LST_TID)).tryToInst(),
                                uri(ID), lst()),
                        MONOID_THEORY_TID, null))));
        // rec: a COMMUTATIVE monoid under deep merge (PLUS), identity the empty rec — merge is keyed, so the
        // order of the two operands does not change the result
        declare(REC_TYPE, rec(mutableMap(
                uri("merge_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(REC_TID).rng(REC_TID)).tryToInst(),
                                uri(ID), rec()),
                        COMMUTATIVE_MONOID_THEORY_TID, null))));
        // code: the function/stream ring — add=branch (parallel split/merge), mul=compose (serial),
        // zero=end (the 0-function, ≡ id{0}::T), one=id (the parametric identity), inv=additive inverse (coeff −1)
        declare(CODE_TYPE, rec(mutableMap(
                uri("ring"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(MUL), auto_from_(COMPOSE_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(Tokens.ZERO), auto_from_(END_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(Tokens.ONE), auto_from_(ID_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                        RING_THEORY_TID, null),
                uri("add_group"), rec(mutableMap(
                                uri(OP), auto_from_(BRANCH_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(ID), auto_from_(END_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(INV), auto_from_(NEG_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                        GROUP_THEORY_TID, null),
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(COMPOSE_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                uri(ID), auto_from_(ID_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                        MONOID_THEORY_TID, null))));
        // machine: the frame system is a GROUPOID — the objects are machines (frames), the morphisms are the
        // invertible URI extensions, composition is descent (push) and its inverse is ascent (pop). The
        // extensions are the NAMES of the morphisms, so there are infinitely many: one per address. At any
        // one object that endomorphism monoid is FREE on the extensions, and the machine stack is one
        // particular such structure — the groupoid of frames. Its identity is `here` (`.`), matching uri::T
        // below, so `push … pop` reduces to the identity exactly as `a/../` does.
        //
        // The morphisms act on the ADDRESS, which is why the operation is bound to the uri ring's `*`
        // (path composition): push is vid·e and pop is vid·e⁻¹, and the strict-descendant requirement is
        // what makes e invertible — an escaping extension is not in this groupoid at all.
        declare(carrier(MACH_MACHINE_TID), rec(mutableMap(
                uri("free_groupoid"), rec(mutableMap(
                                uri("objects"), uri(MACH_MACHINE_TID),
                                uri("morphisms"), uri(URI_TID),
                                uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                uri(ID), uri("."),
                                uri(INV), uri("..")),
                        GROUP_THEORY_TID, null),
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                uri(ID), uri(".")),
                        MONOID_THEORY_TID, null))));
    }

    /**
     * THE LAW BATTERY EVERY THEORY ENTAILS — one declaration per theory type, so a validator can take a carrier's
     * declared instances and turn them into a claim list: an instance names its theory (the instance rec's tid),
     * the theory names its laws ({@code law => [monoidic, unit, …]}), and the union is what the carrier must
     * satisfy. This is DATA — the process-law vocabulary a structural theory licenses, written once beside the
     * theory rather than restated in every carrier.
     * <p>
     * DECLARED CONSERVATIVELY, and each entry is defensible on its own terms:
     * <ul>
     *   <li>MONOID — an associative operation with an identity: {@code monoidic} (associativity) + {@code unit}.</li>
     *   <li>COMMUTATIVE_MONOID — the monoid laws plus {@code commutative}: the operation commutes. {@code lst} under
     *       concatenation is NOT one (append is ordered), {@code rec} under deep merge is (merge is keyed).</li>
     *   <li>GROUP — a monoid with inverses: those two + {@code involution} (the tool reads {@code a·a⁻¹ = id} for the
     *       operation and {@code inv(inv(a)) = a} for the inverse itself).</li>
     *   <li>RING — the additive side is an abelian group (associativity, identity, commutativity, inverse) and the
     *       multiplicative side a monoid distributing over it: {@code left_distributive} and {@code right_distributive}.
     *       NOTE the battery is FLAT — a law is not attributed to a role — so {@code commutative} is a claim about
     *       every operation of the carrier. That holds for the commutative rings this repo declares (int, real); a
     *       ring whose multiplication does not commute would be flagged, and that is the validator doing its job.</li>
     *   <li>FIELD — a commutative ring whose non-zero elements are a multiplicative group. The extra structure over a
     *       ring is the {@code inv} ROLE (not a new law), which {@code involution} exercises as {@code a·a⁻¹ = 1}, so
     *       the law list is the ring's.</li>
     *   <li>RIG — an additive and a multiplicative monoid with distributivity and NO additive inverse:
     *       {@code involution} is absent on purpose (a rig has none), {@code idempotent} is absent too (nat is a rig
     *       and is not idempotent).</li>
     *   <li>BOOLEAN — a complemented distributive lattice: commutative idempotent monoids, distributivity,
     *       {@code involution} for the complement ({@code ¬¬b = b}), and {@code complement} for the
     *       complemented-lattice law ({@code b∧¬b = ¬⊤ = ⊥}, {@code b∨¬b = ¬⊥ = ⊤}).</li>
     *   <li>LATTICE / SEMILATTICE — join and meet are each an idempotent commutative monoid bounded by bottom/top.</li>
     *   <li>NEAR_RING — one-sided distributivity only ({@code left_distributive}). Its signature is
     *       {@code {add, mul, zero}}: there is no declared identity and no declared inverse, so {@code unit} and
     *       {@code involution} would be unchecked, and {@code semi_right_distributive} is deliberately NOT here —
     *       that is an instruction claim about reducers (Thm 19), not an axiom of the theory (Thm 18).</li>
     * </ul>
     * <p>
     * STAGED ONLY, like {@link #declareAlgebras()}: this is a method for the same class-initialisation reason — the
     * recs name the theory tids and {@code TypeBuilder.declare} writes the staged map the drain reads.
     */
    public static void declareAxioms() {
        // monoid: an associative operation with an identity
        axiom(MONOID_THEORY_TID, Law.monoidic, Law.unit);
        // commutative monoid: the monoid laws plus commutativity of the operation
        axiom(COMMUTATIVE_MONOID_THEORY_TID, Law.monoidic, Law.unit, Law.commutative);
        // group: a monoid whose operation has an inverse
        axiom(GROUP_THEORY_TID, Law.monoidic, Law.unit, Law.involution);
        // ring: an abelian additive group, a multiplicative monoid, distributing — the flat battery claims
        // commutativity of every operation (true of the commutative rings declared here: int and real)
        axiom(RING_THEORY_TID, Law.monoidic, Law.unit, Law.commutative, Law.involution,
                Law.left_distributive, Law.right_distributive);
        // field: a commutative ring with a multiplicative inverse role — involution is the law that exercises it
        axiom(FIELD_THEORY_TID, Law.monoidic, Law.unit, Law.commutative, Law.involution,
                Law.left_distributive, Law.right_distributive);
        // rig: monoids + distributivity, no additive inverse (no involution) and not necessarily idempotent
        axiom(RIG_THEORY_TID, Law.monoidic, Law.unit, Law.commutative,
                Law.left_distributive, Law.right_distributive);
        // boolean: a complemented distributive lattice — involution is the complement's self-inverse law and
        // complement the bound law (a∧¬a = ¬⊤, a∨¬a = ¬⊥)
        axiom(BOOLEAN_THEORY_TID, Law.monoidic, Law.unit, Law.commutative, Law.idempotent,
                Law.involution, Law.complement, Law.left_distributive, Law.right_distributive);
        // lattice: meet and join are each an idempotent commutative monoid (bounded by top/bottom)
        axiom(LATTICE_THEORY_TID, Law.monoidic, Law.unit, Law.commutative, Law.idempotent);
        // semilattice: one side of a lattice — the same laws on a single operation
        axiom(SEMILATTICE_THEORY_TID, Law.monoidic, Law.unit, Law.commutative, Law.idempotent);
        // near-ring: associative multiplication, ONE-sided distributivity; no id and no inverse in its signature
        axiom(NEAR_RING_THEORY_TID, Law.monoidic, Law.left_distributive);
    }

    /**
     * THE LAWS A THEORY ENTAILS — the battery its {@code ?decq} declares. Empty when the theory declares none.
     */
    public static List<Law> axiomsOf(final fURI theory) {
        if (null == theory)
            return List.of();
        final fURI address = null == theory.basePath() ? theory : theory.basePath();
        final Obj declared = Machine.read(address.addQ(DECQ));
        if (declared.isRec()) {
            final Obj battery = declared.asRec().atDirect(uri(LAW));
            // A TYPE-VALUED SLOT MEANS "ANY LAW": a battery may state law => uri(Law.LAW_TID) (law::T) instead of a
            // list, so the reader expands it to the vocabulary rather than answering "no battery declared".
            if (battery.isUri() && Law.LAW_TID.equals(battery.uriValue()))
                return List.of(Law.values());
            if (battery.isLst()) {
                final List<Law> laws = battery.lstValue().stream()
                        .filter(Obj::isUri)
                        .map(obj -> law(obj.uriValue().name()))
                        .filter(Objects::nonNull)
                        .toList();
                if (!laws.isEmpty())
                    return laws;
            }
        }
        return List.of();
    }

    /**
     * The {@link Law} an axiom name denotes, or null when the name is not a law of this vocabulary.
     */
    private static Law law(final String name) {
        for (final Law law : Law.values())
            if (law.name().equals(name))
                return law;
        return null;
    }

    /**
     * DECLARE one theory's battery — {@code decs::[obj=>theory::T, law=>[…]]} at the theory's {@code ?decq}, keyed by
     * its vid: a theory type is registered by the (optional) category.
     */
    private static void axiom(final fURI theory, final Law... laws) {
        PendingWrites.write(PendingWrites.decq(theory), rec(mutableMap(uri(OBJ), T(theory),
                uri(LAW), lst(Arrays.stream(laws).map(law -> (Obj) uri(law.name())).toList())), DECS_TID, null));
    }

    /**
     * DECLARE one carrier's algebra, written as the algebra itself — the REC form of
     * {@link TypeBuilder#declare(Type, Algebra)}, because every declaration on this class is the block a reader
     * answers ({@code ring => [add => …, mul => …]}) and {@link Algebra#of(Rec)} is the identity on it.
     */
    private static void declare(final Type type, final Rec law) {
        TypeBuilder.declare(type, Algebra.of(law));
    }

    /**
     * {@link #declare(Type, Rec)} by ADDRESS — the form a THEORY type takes: it is registered by the (optional)
     * category, and the declaration is keyed by its vid either way.
     */
    private static void declare(final fURI vid, final Rec law) {
        TypeBuilder.declare(vid, Algebra.of(law));
    }

    /**
     * THE CARRIER A DECLARATION IS KEYED BY — {@link TypeBuilder#declare(Type, Algebra)} reads only the {@code vid},
     * so a carrier whose type this class does not itself declare (the machine, built in
     * {@code machInstSet.setup()}) is named by its address. A BARE type is all the declaration needs, and building it
     * here (rather than holding a field) keeps the cross-instset address the only cross-instset fact on this class's
     * initialisation path.
     */
    private static Type carrier(final fURI tid) {
        return TypeBuilder.build().tid(tid).vid(tid).create();
    }
}
