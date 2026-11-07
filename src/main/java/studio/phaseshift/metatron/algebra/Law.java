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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.algebra.rewrite.Derivation;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.sum_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The law axis: the process laws a morphism obeys — a property of the operation itself, never the whole
 * algebra (those are the theory types on {@code object::T}).
 *
 * <p><b>THE VOCABULARY IS DATA.</b> This enum is the AUTHORING CONVENIENCE only: it names a law and holds its prose
 * and its admissible shapes, but the runtime truth is a plain {@code uri} value per law, stored at
 * {@link #address()} — one value per address under {@link #LAW_TID} ({@code /m/law/commutative},
 * {@code /m/law/monoidic}, …). Nothing is typed per law: "law" is WHERE the uri lives, and {@link #register()} writes
 * every constant there at boot (staged through {@link PendingWrites}, so the mount that owns {@code /m} publishes it).
 * The nominal type {@code law::T} lives beside it ({@code mTypes.LAW_TYPE}, at {@link #LAW_TID}) and admits a uri when
 * its own label's address already holds a uri — an open vocabulary that a later write extends without recompiling.
 *
 * <p>NOTES: the structural laws — semilattice, boolean, near_ring, field, rig, lattice, ring, group, … — are not
 * process laws: they name an algebraic <em>structure</em>, so they are theory types on {@code object::T}
 * ({@code monoid_theory::T}, {@code group_theory::T}, {@code ring_theory::T}, {@code field_theory::T},
 * {@code rig_theory::T}, {@code boolean_theory::T}, {@code lattice_theory::T},
 * {@code semilattice_theory::T}, {@code near_ring_theory::T}). The former provenance axis
 * (syntactic/declared/semantic) is dropped too: an instruction set serves the declared process laws; the
 * rest are derived (syntactic) or computed (semantic) when wired.
 *
 * <p>The javadoc on each constant below is the AUTHORING SITE; {@link #documentation()} is the one source the
 * registration publishes, and the prose there is the text a reader of {@code /m/law/<label>?docq} answers.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public enum Law {
    /**
     * an annihilator: {@code a·x = a} for every {@code x} (absorbing element of the operation)
     */
    absorbing,
    /**
     * the reflexive-transitive iterate — {@code aⁿ} over the operation (a Kleene closure)
     */
    kleene,
    /**
     * pass-through-or-drop ({0,1}); the optional/zeroable lift of a value
     */
    partial,
    /**
     * commutes through the stream ring's parallel/serial structure — the coefficient floats off F\Fr
     */
    floatable,
    /**
     * cannot float past a barrier — a reduce anchors the coefficient in place
     */
    blocked,
    /**
     * the effect axis: the result is a function of the lhs and args alone, and applying it changes nothing else — no
     * write, no print, no spawn. Declared, never inferred: an instruction that does not declare {@code pure} is
     * effectful, so a rule that moves, drops or replicates code ({@code law_absorbing}'s swallowed prefix,
     * {@code law_shard}'s floatable run) admits only declared-pure instructions
     */
    pure,
    /**
     * a monoid/group acting on a carrier — {@code f(gh)x = f(g)f(h)x}
     */
    action,
    /**
     * an atomic/indecomposable element — no nontrivial factorization
     */
    prime,
    /**
     * distributes over {@code +} on the left — {@code a(b+c) = ab+ac}
     */
    left_distributive,
    /**
     * distributes over {@code +} on the right — {@code (a+b)c = ac+bc}
     */
    right_distributive,
    /**
     * distributes over {@code +} on the right, with a RE-APPLICATION — {@code (a+b)c = (ac+bc)c}: the reducer's
     * distributivity, and the law a monoidic fold is allowed to declare. A reducer does NOT obey
     * {@link #right_distributive}: {@code (a+b)c} merges the branches and reduces once, while {@code ac+bc} yields
     * two reduced objects (Lemma 1, so {@code ⟨F_r, +, ·⟩} is a near-ring, left distributive only — Thm 18). The
     * inner re-application is what makes a shard sound rather than merely partitioned: the partials recombine
     * through the fold (Thm 19), which {@code law_shard} realizes via {@link Law.Helper#combiner}.
     */
    semi_right_distributive,
    /**
     * a bare binary op — a magma: closure only, neither associativity nor an identity demanded (as {@link #monoidic}
     * is a monoid). {@code mean} is the witness: commutative, but neither associative nor unital
     */
    magmadic,
    /**
     * an associative binary op with an identity element
     */
    monoidic,
    /**
     * {@code a·b = b·a}
     */
    commutative,
    /**
     * the carrier is partially ordered, and these insts are its comparisons
     */
    poset,
    /**
     * the identity element — {@code e·x = x·e = x}
     */
    unit,
    /**
     * self-inverse — {@code f(f(x)) = x} (period two)
     */
    involution,
    /**
     * the complemented-lattice law — {@code a·inv(a) = inv(id)}: an element joined with its complement is the other
     * bound of the lattice (for a Boolean algebra, {@code a∧¬a = ¬⊤ = ⊥} and {@code a∨¬a = ¬⊥ = ⊤}). It is written
     * with the roles {@link #involution} already reads, so it needs no new role: the binary operation, its declared
     * inverse, and the operation's bound identity.
     */
    complement,
    /**
     * {@code f(f(x)) = f(x)}
     */
    idempotent,
    /**
     * some power is zero — {@code fⁿ = 0}
     */
    nilpotent,
    /**
     * nonzero {@code a} with {@code a·b = 0} for some nonzero {@code b}
     */
    zero_divisor,
    /**
     * generated by one element — every value is a power of a single generator
     */
    cyclic;

    /**
     * THE LAW VOCABULARY'S ADDRESS — {@code /m/law}. The nominal type {@code law::T} is registered at exactly this
     * address ({@code mTypes.LAW_TYPE}), and every constant's value is written at {@link #address()} beneath it: one
     * constant for the spelling, so the storage address and the type address can never drift apart.
     */
    public static final fURI LAW_TID = f("/m/law");

    /**
     * A signature for a binary operation on a carrier — {@code A × A → A}: one argument beyond the lhs, an
     * {@code A} answer. The {@code args} component is what makes this shape BINARY: a law written this way is
     * refused by an operation that does not take exactly one argument.
     */
    private static final fURI BINARY_ENDO = f("inst?dom=A&rng=A&args=[A]");

    /**
     * A signature for an operation on a carrier — {@code A → A}: the carrier to itself, with the operand types
     * stated only by the {@code args} component.
     * <p>
     * With NO {@code args} this is the ARITY-AGNOSTIC shape, and that is a property of the spelling, not an
     * omission: the law is about an element of the carrier ({@code zero}, {@code one}, {@code id}) or about an
     * operation on it at ANY arity — a unary {@code neg}, a binary {@code and}, a stream reduction, and the bare
     * element laws an operation's identity/annihilator/atomicity are. A law whose claims today land on more than
     * one arity MUST use this shape: spelling it with {@code args=[A]} makes every nullary claim a category error.
     */
    private static final fURI ENDO = f("inst?dom=A&rng=A");

    /**
     * A signature for a relation on a carrier — {@code A × A → bool}: two operands, a {@code bool} answer.
     */
    private static final fURI RELATION = f("inst?dom=A&rng=bool&args=[A]");

    /**
     * A signature for a fold — {@code A{*} → A}: many copies of the carrier in, one {@code A} out.
     */
    private static final fURI FOLD = f("inst?dom=A{*}&rng=A");

    /**
     * An operation ON THE CARRIER, at the two arities the instsets witness: a binary operation ({@code A × A → A})
     * or a fold ({@code A{*} → A}). The ARITY-AGNOSTIC {@link #ENDO} is deliberately NOT in this set — an operation
     * law is about an operation, so a unary {@code neg} or a nullary element is not the kind of thing it can be
     * about.
     */
    private static final List<fURI> OPERATION = List.of(BINARY_ENDO, FOLD);

    /**
     * A signature for a form law — {@code inst}: the law is about an instruction's form, not an operation on a
     * carrier.
     */
    private static final fURI FORM = f("inst");

    /**
     * A signature for an effect — {@code obj}: the law is about what an instruction does to the world, and there is
     * no carrier at all.
     */
    private static final fURI EFFECT = f("obj");

    /**
     * THE ADDRESS THIS LAW'S VALUE IS STORED AT — {@code /m/law/<label>}, the one place the runtime asks whether the
     * vocabulary holds a law. The label is the enum constant's name, so the address, the stored value's label and the
     * name a declaration spells are all the same string.
     *
     * @return the law's address under {@link #LAW_TID}
     */
    public fURI address() {
        return LAW_TID.extend(this.name());
    }

    /**
     * THE PROSE THIS LAW PUBLISHES — the enum constant's javadoc text as a plain sentence, served through the law's
     * {@code ?docq}. It is the ONE source the registration reads: a reader asks the stored value, never this enum, for
     * a law's documentation.
     *
     * @return the law's one-sentence documentation
     */
    public String documentation() {
        return switch (this) {
            case absorbing -> "an annihilator: a·x = a for every x (absorbing element of the operation)";
            case kleene -> "the reflexive-transitive iterate — aⁿ over the operation (a Kleene closure)";
            case partial -> "pass-through-or-drop ({0,1}); the optional/zeroable lift of a value";
            case floatable -> "commutes through the stream ring's parallel/serial structure — the coefficient floats off F\\Fr";
            case blocked -> "cannot float past a barrier — a reduce anchors the coefficient in place";
            case pure -> "the effect axis: the result is a function of the lhs and args alone, and applying it changes "
                    + "nothing else — no write, no print, no spawn. Declared, never inferred: an instruction that does not "
                    + "declare pure is effectful, so a rule that moves, drops or replicates code (law_absorbing's swallowed "
                    + "prefix, law_shard's floatable run) admits only declared-pure instructions";
            case action -> "a monoid/group acting on a carrier — f(gh)x = f(g)f(h)x";
            case prime -> "an atomic/indecomposable element — no nontrivial factorization";
            case left_distributive -> "distributes over + on the left — a(b+c) = ab+ac";
            case right_distributive -> "distributes over + on the right — (a+b)c = ac+bc";
            case semi_right_distributive -> "distributes over + on the right, with a RE-APPLICATION — (a+b)c = (ac+bc)c: "
                    + "the reducer's distributivity, and the law a monoidic fold is allowed to declare. A reducer does NOT "
                    + "obey right_distributive: (a+b)c merges the branches and reduces once, while ac+bc yields two reduced "
                    + "objects (Lemma 1, so ⟨F_r, +, ·⟩ is a near-ring, left distributive only — Thm 18). The inner "
                    + "re-application is what makes a shard sound rather than merely partitioned: the partials recombine "
                    + "through the fold (Thm 19), which law_shard realizes via the law table's combiner";
            case magmadic -> "a bare binary op — a magma: closure only, neither associativity nor an identity demanded (as "
                    + "monoidic is a monoid). mean is the witness: commutative, but neither associative nor unital";
            case monoidic -> "an associative binary op with an identity element";
            case commutative -> "a·b = b·a";
            case poset -> "the carrier is partially ordered, and these insts are its comparisons";
            case unit -> "the identity element — e·x = x·e = x";
            case involution -> "self-inverse — f(f(x)) = x (period two)";
            case complement -> "the complemented-lattice law — a·inv(a) = inv(id): an element joined with its complement "
                    + "is the other bound of the lattice (for a Boolean algebra, a∧¬a = ¬⊤ = ⊥ and a∨¬a = ¬⊥ = ⊤). It is "
                    + "written with the roles involution already reads, so it needs no new role: the binary operation, its "
                    + "declared inverse, and the operation's bound identity";
            case idempotent -> "f(f(x)) = f(x)";
            case nilpotent -> "some power is zero — fⁿ = 0";
            case zero_divisor -> "nonzero a with a·b = 0 for some nonzero b";
            case cyclic -> "generated by one element — every value is a power of a single generator";
        };
    }

    /**
     * The signature of the thing this law is about — "with respect to".
     * <p>
     * A law is a statement about a shape of operation on a set, never about one instruction's behavior: the signature
     * names that shape, with the carrier as the poly variable {@code A}, the operand types in the {@code args} query
     * component, and {@code obj} where there is no carrier at all. A claim on an instruction whose contract does not
     * match this signature is a category error, not a counterexample — such an instruction is not the kind of thing
     * the law can be about, so it neither confirms nor refutes it.
     * <p>
     * The spellings are canonical: {@code A} (never {@code #}, which would flip {@link fURI#hasPattern()} for a query
     * value), no {@code args} key at all when there are no arguments, and one {@code args=[...]} key carrying the
     * element types in order otherwise ({@code inst?rng=A&dom=A&args=[A]}).
     * <p>
     * THE {@code args} COMPONENT IS ARITY, AND ITS ABSENCE IS A CLAIM: {@code args=[A]} says the operation takes
     * exactly one argument beyond its lhs, while no {@code args} at all says the law is arity-agnostic — an element
     * law, or one an operation may obey at any arity. That is why a law whose declarations span an idempotent
     * {@code zero} and an idempotent {@code or} must be spelled {@code inst?rng=A&dom=A}: the binary spelling would
     * make the nullary claim a category error.
     * <p>
     * A law answers the SET of shapes it is admissible on: most answer one, and the operation laws answer the two
     * arities the instsets witness (a binary op and a fold). A claim is a category error when the instruction's
     * contract matches NONE of them; matching one is necessary and never sufficient (the equations decide truth).
     *
     * @return the fURI signatures the law is about, most specific first
     */
    public List<fURI> shapes() {
        return switch (this) {
            // an OPERATION on the carrier, either arity the instsets witness: a binary op or a fold. The arity is a
            // real claim here -- a commutative unary neg is a category error, and so is a monoidic element.
            case action, commutative, magmadic, monoidic -> OPERATION;
            // a binary operation on the carrier (A × A → A): the laws that read an OPERAND equation
            case complement, cyclic, left_distributive, nilpotent, right_distributive, zero_divisor -> List.of(BINARY_ENDO);
            // the carrier to itself, the arity stated only by `args`: the ELEMENT laws (an identity, an
            // annihilator, an atomic element) and the process laws whose claims today span arities -- an
            // idempotent nullary zero/one AND an idempotent binary join, a self-inverse unary op AND the group
            // form of the same law
            case absorbing, idempotent, involution, prime, unit -> List.of(ENDO);
            // the comparisons of a partially ordered carrier (A × A → bool)
            case poset -> List.of(RELATION);
            // the reducer's distributivity, and the closure it iterates: both are laws of an unbounded iterate
            case semi_right_distributive, kleene -> List.of(FOLD);
            // coefficient/form laws: read off the instruction's form, no operation on a carrier to state
            case floatable, blocked, partial -> List.of(FORM);
            // the effect axis: there is no carrier
            case pure -> List.of(EFFECT);
        };
    }

    /**
     * {@link #shapes()} under its historical name — "with respect to" the shape the law is about. It delegates so the
     * authoring call sites ({@code AlgebraTool.matches}, the census, the declaration docs) keep their contract while
     * the shape table has exactly one definition.
     *
     * @return the fURI signatures the law is about, most specific first
     */
    public List<fURI> wrt() {
        return this.shapes();
    }

    /**
     * PUBLISH THE VOCABULARY AS DATA — one plain {@code uri} value per law at {@link #address()}, its prose at
     * {@code ?docq} and its admissible shapes at {@code ?decq}, all staged through {@link PendingWrites}.
     * <p>
     * WHY THE QUEUE: this runs from {@code mInstSet.setup()} just before the drain that publishes {@code /m}, so the
     * writes land in the same mount as the space that owns {@code /m/law} — the values, their documentation and their
     * shape declarations are all in the law's jurisdiction. The log is retained and keyed by address, so a second call
     * (or a re-mount) re-stages the same addresses rather than appending duplicates: registration is IDEMPOTENT.
     * <p>
     * THE SHAPE DECLARATION is the smallest rec a reader can consume: {@code decs::[obj => <the law's uri>, inst => lst(<shape uris>)]}. The
     * {@code inst} key is the existing token (no new vocabulary), and the values are the very {@link #shapes()} the
     * analyzer matches against an instruction's contract, so a stored law documents its own admissibility instead of a
     * table restating it.
     */
    public static void register() {
        // the vocabulary itself, documented once at the type's address (mTypes.LAW_TYPE's builder cannot carry a
        // description: composing a ?docq rec at class-init time would pull QCollection into mTypes' initialization and
        // close the mTypes -> mInstSet.AS_INST_TID cycle)
        PendingWrites.write(PendingWrites.docq(LAW_TID),
                QCollection.Docs.doc("the process-law vocabulary: one uri per law, stored at " + LAW_TID + "/<label>"));
        for (final Law law : Law.values()) {
            final fURI address = law.address();
            PendingWrites.write(address, uri(law.name()));
            PendingWrites.write(PendingWrites.docq(address), QCollection.Docs.doc(law.documentation()));
            PendingWrites.write(PendingWrites.decq(address), rec(mutableMap(uri(OBJ), uri(law.name()),
                    uri(INST), lst(law.shapes().stream().map(shape -> (Obj) uri(shape)).toList())), QCollection.DECS_TID, null));
        }
    }

    /**
     * THE LAW READS — what an instruction declares beside itself ({@code ?decq}, read through {@code Machine.read}) and
     * the facts that follow from it: the inverse its carrier's algebra derives, the contract dispatch selects, a fold's
     * combiner, and every defining equation a rewrite can contract. Read-only: nothing here stores a declaration.
     */
    public static final class Helper {

        /**
         * THE DERIVATIONS SNAPSHOT — every registered instruction's {@code ?decq} defining equation, keyed by the address
         * it is declared at, and the staging revision it was taken at. INTERIM: retaken when the staging log's revision
         * moves (a builder declared something) or before the machine was up; type-edit subscriptions will replace it.
         */
        private static volatile Map<fURI, Derivation> DERIVATIONS = Map.of();
        private static volatile List<PendingWrites.Write> DERIVATIONS_AT = null;
        private static volatile boolean DERIVATIONS_LOADED = false;

        /**
         * A BOOT IS A FRESH WORLD — forget the derivations snapshot ({@code BootLoader.resetMemos}, before any set mounts).
         */
        public static void reset() {
            DERIVATIONS = Map.of();
            DERIVATIONS_AT = null;
            DERIVATIONS_LOADED = false;
        }

        /**
         * The entry of an inst — the process laws, inverse and derivation it declares beside itself ({@code ?decq}), the
         * inverse replaced when its carrier's algebra derives one. Null when the inst declares nothing.
         */
        public static Entry lawsOf(final fURI instTid) {
            final Entry declared = declaredRow(instTid);
            return null == declared ? null : computed(instTid, declared);
        }

        /**
         * THE DECLARATION OF THE OBJ DISPATCH SELECTS ({@link Typer.Helper#deduceTypeInst}): a generic's laws hold for every
         * instantiation that runs its body, a specialization answers with its own, and a refined operand gets the laws of
         * the body it runs. The INVERSE is the OPERAND's algebra and never inherits ({@link #algebraFor}): nat's rig
         * subtracts int's add_group. When dispatch names no contract, the op's generic claim answers.
         */
        public static Declared declared(final fURI op, final Type operand) {
            if (null == op || null == op.basePath())
                return null;
            final fURI base = op.basePath();
            if (null == operand || null == operand.vid() || DERIVING.get())
                return universalDeclared(base);
            final Inst contract = deduce(base, operand);
            if (null != contract) {
                final Entry row = declaredRow(contract.tid());
                if (null != row)
                    return new Declared(contract.tid(), algebraic(contract.tid(), row, operand));
            }
            return universalDeclared(base);
        }

        /**
         * EVERY DEFINING EQUATION, keyed by the address it is declared at — {@code derivation_contraction} reads the values,
         * {@link #inverseForm} the entries. A walk of the registered instructions through {@code Machine.read}, kept as the
         * {@link #DERIVATIONS} snapshot: a rewrite pass asks per evaluation, and a walk per pass is a regression.
         */
        private static Map<fURI, Derivation> declaredDerivations() {
            final List<PendingWrites.Write> revision = PendingWrites.writes();
            if (DERIVATIONS_LOADED && revision == DERIVATIONS_AT)
                return DERIVATIONS;
            final boolean loaded = Machine.loaded();
            final Map<fURI, Derivation> snapshot = new LinkedHashMap<>();
            if (loaded)
                for (final Inst inst : Machine.current().instset().insts()) {
                    final Obj claim = null == inst.tid() ? noobj() : Machine.read(stagingAddress(inst.tid()));
                    final Derivation derivation = claim.isRec() ? declaredDerivation(claim.asRec()) : null;
                    if (null != derivation)
                        snapshot.putIfAbsent(Inst.Helper.operationTid(inst.tid()), derivation);
                }
            DERIVATIONS = snapshot;
            DERIVATIONS_AT = revision;
            DERIVATIONS_LOADED = loaded;
            return snapshot;
        }

        /**
         * A process-law row: the declared laws, the operation's inverse (null when none), and its derivation
         * (null when primitive — not definable as a composition of other instructions).
         * <p>
         * THE INVERSE IS DIRECTIONAL, AND IT HAS THREE SOURCES in this order:
         * <ol>
         *   <li><b>the carrier's algebra</b> — a group instance plus the operation's defining equation
         *       ({@code div ≡ mult ∘ inv}) gives {@code mult↔div}, and an {@link Law#involution} is its own inverse.
         *       This is the only source that can answer for an ENDOMORPHISM, where the category graph has no partner
         *       to offer: {@code position} short-circuits every endo to {@code retract}, and reads {@code coupling}
         *       only when dom and rng differ — so two endos over one carrier are indistinguishable to it;</li>
         *   <li><b>the graph's {@code coupling}</b> — an edge running back over the same two objects. A CANDIDATE pair,
         *       never a decision: the relation is symmetric, so the graph can propose a partner but cannot choose the
         *       direction, and the direction is the whole content of the claim ({@code int→str→int} holds,
         *       {@code str→int→str} does not: "007" becomes 7 becomes "7");</li>
         *   <li><b>the declared field</b> — the residue neither of the above answers, e.g. int's one-way
         *       {@code mult→div} (truncating division retracts multiplication, sound in one direction only).</li>
         * </ol>
         * {@code inverse(f) = g} claims {@code f·g = ε} only — f a section, g its retraction — and never
         * {@code g·f = ε}, which is why a lossy conversion may declare one direction and answer null for the other.
         */
        public record Entry(Lst laws, fURI inverse, Derivation derivation) {
        }

        /**
         * Whether a derivation is in flight on this thread — the guard {@link #lookup} answers the declared row under.
         */
        private static final ThreadLocal<Boolean> DERIVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

        /**
         * An inst's declared entry together with the n-tid it was declared under — the address a rewrite
         * retypes an unresolved chain instruction with so the instruction's coefficient shape is legible.
         */
        public record Declared(fURI tid, Entry entry) {
        }

        /**
         * THE ROW AN INSTRUCTION DECLARED BESIDE ITSELF — one {@code Machine.read} of its {@code ?decq} address. A claim
         * {@link InstBuilder#create()} staged and nothing has published yet is published by that read (the q-proc's lazy
         * hook), so the store is the only source.
         */
        private static Entry declaredRow(final fURI instTid) {
            if (null == instTid)
                return null;
            // THE STORE ANSWERS FOR THE QUERIED CONTRACT ONLY. A read of an address it holds no claim for falls back to
            // subsumption, so plus?imperial answers real's plus claim (an imperial is a real) -- but a row is inherited
            // through the contract deduce() lands the operand on, never through the store: an ancestor's claim is refused
            final Obj stored = Machine.read(stagingAddress(instTid));
            final Obj claimant = stored.isRec() ? stored.asRec().atDirect(uri(OBJ)) : noobj();
            return claimant.isInst() && !Inst.Helper.operationTid(claimant.tid()).equals(Inst.Helper.operationTid(instTid))
                    ? null : declaredEntry(stored);
        }

        /**
         * THE ADDRESS A DECLARATION IS STAGED AT — the instruction's OPERATION address (its tid with the {@code args}
         * component removed) plus {@code ?decq}, which is where {@link InstBuilder#create()} writes and where a later
         * boot reads. A null address answers null rather than throwing, matching the log's own tolerance.
         */
        private static fURI stagingAddress(final fURI instTid) {
            return null == instTid ? null : PendingWrites.decq(Inst.Helper.operationTid(instTid));
        }

        /**
         * THE ROW AN INSTRUCTION DECLARED BESIDE ITSELF AS THE STORE HOLDS IT ({@code ?decq}, staged by
         * {@code InstBuilder.create}): its process laws, its inverse and its defining equation, each read only when the
         * rec carries it. An undeclared facet is null (an absent laws list is the empty list, since a row with no laws
         * still declares its relations), and a rec declaring nothing is null rather than an empty claim — the table's
         * row is then the only source.
         */
        private static Entry declaredEntry(final Obj fromDecq) {
            if (null == fromDecq || !fromDecq.isRec())
                return null;
            final Rec declaration = fromDecq.asRec();
            // read as an ADDRESS, never as a value: at(...) auto-resolves and would follow the inverse edge into the
            // instruction it names instead of answering which instruction it is
            final fURI inverse = TheoryHelper.roleAddress(declaration, INVERSE);
            final Derivation derivation = declaredDerivation(declaration);
            final Obj declaredLaw = declaration.atDirect(uri(LAW));
            // A TYPE-VALUED SLOT MEANS "ANY LAW": a declaration may state law => uri(LAW_TID) (law::T) instead of a list,
            // so expand it to the vocabulary rather than letting a non-lst silently answer "no laws declared".
            final Obj lawLst = declaredLaw.isUri() && Law.LAW_TID.equals(declaredLaw.uriValue())
                    ? lst(Arrays.stream(Law.values()).map(law -> (Obj) uri(law.name())).toList())
                    : declaredLaw;
            if (!lawLst.isLst() && null == inverse && null == derivation)
                return null;
            return new Entry(lawLst.isLst() ? lawLst.asLst() : lst(), inverse, derivation);
        }

        /**
         * The defining equation a declaration carries — the lhs and rhs stored as {@code code::T} — or null when the
         * rec holds none. A half-written derivation answers no derivation: it states no equation, and a rewrite run
         * off half of one would be a guess.
         */
        private static Derivation declaredDerivation(final Rec declaration) {
            final Obj derivation = declaration.atDirect(uri(DERIVATION));
            if (!derivation.isRec())
                return null;
            final Obj lhs = derivation.asRec().atDirect(uri(LHS));
            final Obj rhs = derivation.asRec().atDirect(uri(RHS));
            return lhs.isCode() && rhs.isCode() ? new Derivation(lhs.asCode(), rhs.asCode()) : null;
        }

        /**
         * The entry as the algebra answers it: the declared row, its inverse replaced when the carrier derives one.
         * The declared row is returned unchanged when the two agree, so today's declarations cost nothing.
         */
        private static Entry computed(final fURI tid, final Entry declared) {
            DERIVING.set(Boolean.TRUE);
            try {
                final Type carrier = algebraOf(null == tid ? null : tid.dom());
                final fURI inverse = derivedInverse(tid, declared, null == carrier ? null : algebraFor(carrier, tid));
                return null == inverse || inverse.equals(declared.inverse()) ?
                        declared : new Entry(declared.laws(), inverse, declared.derivation());
            } finally {
                DERIVING.set(Boolean.FALSE);
            }
        }

        /**
         * THE INVERSE of an operation, read off THE OPERAND'S declared algebra ({@link #algebraFor} decides which type
         * that is) — a consequence, never a new fact.
         * <ul>
         *   <li>an operation declared {@link Law#involution} is its own inverse ({@code f·f = 1} is the law);</li>
         *   <li>a carrier declaring a <b>group</b> instance {@code (op, id, inv)} states the cancellation both ways:
         *       {@code op}'s inverse is the instruction DEFINED as {@code op} applied to its inverted argument —
         *       {@code div ≡ mult ∘ inv}, {@code minus ≡ plus ∘ neg} — and that instruction's inverse is {@code op};</li>
         *   <li>otherwise null: the carrier's algebra says nothing about an inverse, and the declaration is the
         *       source ({@code int}'s {@code mult}→{@code div}: truncating division is a retraction of multiplication,
         *       sound in that direction only, and no algebra states it).</li>
         * </ul>
         * Only a group is read: a {@code field} names {@code mul}/{@code inv} too, but {@code 0} has no {@code 1/0}, so a
         * field's multiplicative side is no group and licenses no cancellation of its own.
         * <p>
         * This is SOURCE 1 of the three {@link Entry} names — the algebra, and the only one that can answer for an
         * endomorphism. Source 2 (the graph's symmetric {@code coupling}, which proposes a partner but not a direction)
         * and source 3 (the declared field) are noted there.
         */
        private static fURI derivedInverse(final fURI tid, final Entry entry, final Type algebra) {
            if (null == tid)
                return null;
            if (has(entry, Law.involution))
                return tid;
            // THE ALGEBRA IS THE OPERAND'S, NEVER THE CONTRACT'S: a type models exactly what it declares, so an
            // operand that declares none (or a rig where int declares a ring) derives no inverse here
            if (null == algebra || null == algebra.vid())
                return null;
            final fURI carrier = algebra.vid().basePath();
            if (null == carrier || carrier.equals(ALL.basePath()) || carrier.isGeneric())
                return null;
            for (final Obj block : TheoryHelper.instances(algebra).jvm().values()) {
                if (!block.isRec() || !GROUP_THEORY_TID.equals(block.asRec().tid().basePath()))
                    continue;
                final fURI op = TheoryHelper.roleAddress(block.asRec(), OP);
                final fURI inv = TheoryHelper.roleAddress(block.asRec(), INV);
                if (null == op || null == inv)
                    continue;
                if (op.basePath().equals(tid.basePath())) {
                    // the group's own operation: its inverse is the instruction DEFINED as it, argument inverted
                    final fURI form = inverseForm(tid, op, inv);
                    if (null != form)
                        return form;
                } else if (defined(entry.derivation(), op, inv)) {
                    // this instruction IS that definition: its inverse is the group's operation
                    return null == tid.dom() || null == tid.rng() || null == op.basePath() ? null : op.basePath().dom(tid.dom()).rng(tid.rng());
                }
            }
            return null;
        }

        /**
         * Whether a derivation defines its instruction as {@code op} applied to its inverted argument — the defining
         * equation {@code div ≡ mult ∘ inv}. Read structurally, so the argument may be nested however the chain builder
         * wrote it; a derivation ending anywhere else says nothing about cancelling {@code op}.
         */
        private static boolean defined(final Derivation derivation, final fURI op, final fURI inv) {
            if (null == derivation)
                return false;
            final List<Inst> steps = derivation.rhs().insts();
            for (int i = 1; i < steps.size(); i++) {              // index 0 is the seed, never the definition
                final Inst step = steps.get(i);
                if (step.tid().basePath().equals(op.basePath()) && step.args().elements().anyMatch(arg -> holds(arg, inv.basePath())))
                    return true;
            }
            return false;
        }

        /**
         * Whether an instruction holds the given op, in its arguments (nested code included).
         */
        private static boolean holds(final Obj arg, final fURI op) {
            if (arg.isCode())
                return arg.asCode().insts().stream().anyMatch(inst -> holds(inst, op));
            if (!arg.isInst())
                return false;
            return arg.asInst().tid().basePath().equals(op) || arg.asInst().args().elements().anyMatch(inner -> holds(inner, op));
        }

        /**
         * The instruction over {@code tid}'s carrier that is DEFINED as {@code op} applied to its inverted argument —
         * the instruction whose inverse is {@code op}. Null when nothing declares one.
         * <p>
         * BOTH PLACES A DEFINITION CAN LIVE ARE READ — the in-code rows and the equations instructions declare beside
         * themselves ({@link #declaredDerivations}). A sibling that migrated to {@code ?decq} is still the SAME claim
         * about the same instruction, so a carrier that names the defining composition must find it there too: without
         * this, migrating {@code div}'s row would silently strip {@code mult}'s inverse ({@code mult}→{@code div} is a
         * consequence of {@code mult_group} plus {@code div ≡ mult ∘ inv}, and the equation is the half that migrated).
         */
        private static fURI inverseForm(final fURI tid, final fURI op, final fURI inv) {
            for (final Map.Entry<fURI, Derivation> entry : declaredDerivations().entrySet())
                if (sameCarrier(entry.getKey(), tid) && defined(entry.getValue(), op, inv))
                    return entry.getKey();
            return null;
        }

        /**
         * Whether two n-tids address an operation over the same carrier.
         */
        private static boolean sameCarrier(final fURI a, final fURI b) {
            return null != a && null != b && null != a.dom() && null != b.dom() && null != a.rng() && null != b.rng()
                    && a.dom().basePath().equals(b.dom().basePath()) && a.rng().basePath().equals(b.rng().basePath());
        }

        /**
         * Whether an entry declares the given process law.
         */
        private static boolean has(final Entry entry, final Law law) {
            return entry.laws().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name()));
        }

        /**
         * The registered contract this type lands on, or null when the machine registers none (an unbooted machine
         * included) — see {@link Typer.Helper#deduceTypeInst}.
         */
        private static Inst deduce(final fURI base, final Type operand) {
            return Machine.loaded() ? Typer.Helper.deduceTypeInst(Machine.current().instset(), operand, base) : null;
        }

        /**
         * The row as the OPERAND's algebra answers it: its inverse replaced when that algebra derives one, the declared
         * retraction kept when it does not. Strictly the operand's — see {@link #declared(fURI, Type)}.
         */
        private static Entry algebraic(final fURI tid, final Entry declared, final Type operand) {
            DERIVING.set(Boolean.TRUE);
            try {
                final fURI inverse = derivedInverse(tid, declared, algebraFor(operand, tid));
                return null == inverse || inverse.equals(declared.inverse()) ?
                        declared : new Entry(declared.laws(), inverse, declared.derivation());
            } finally {
                DERIVING.set(Boolean.FALSE);
            }
        }

        /**
         * The type whose algebra an inverse is read from — the plain (coefficient-free, non-generic) carrier named by
         * an n-tid, or null when there is none to read.
         */
        private static Type algebraOf(final fURI carrier) {
            return null == carrier || null == carrier.basePath() || carrier.basePath().equals(ALL.basePath())
                    || carrier.basePath().isGeneric() ? null : T(carrier.basePath());
        }

        /**
         * WHICH TYPE'S ALGEBRA ANSWERS FOR AN OPERAND — THE ONE POLICY SWITCH. The policy is the single call on the last
         * line of the body: {@link #strict} (active), {@link #inherit}, or {@link #byContract}. All three are real,
         * compiled methods rather than commented-out bodies on purpose — a comment block holding a method is one
         * unbalanced fence away from a broken build (flipping it by hand did break this file), while a compiled
         * alternative is type-checked on every build no matter which one the switch selects.
         * <p>
         * WHAT THE CHOICE MOVES, measured 2026-10-27 with the suite under both live bodies: the observable answers of
         * {@code strict} and {@code inherit} are identical on today's table (162 tests green either way), because a
         * type that declares a block ({@code nat}'s {@code rig}) is answered by its own block in both policies, and a
         * type that declares none ({@code imperial}) is stopped earlier, at the ROW — its {@code plus?imperial} contract
         * has no row in any law table, so {@link #declared(fURI, Type)} answers null before an algebra is ever read. The
         * policy becomes load-bearing when a type has rows but no block: once delegation ({@code law => int}) lands, or
         * the row read learns to fall back to an ancestor's row. {@link #byContract} is the loosest body and the only one
         * that differs today: it hands {@code nat} int's ring, which {@code catInstSetTest#testStrictLawResolution} flags.
         *
         * @param operand  the type being operated on
         * @param contract the n-tid of the row being answered, whose dom is the implementation's carrier
         * @return the type whose algebra answers for it
         */
        private static Type algebraFor(final Type operand, final fURI contract) {
            return strict(operand);
        }

        /**
         * STRICT — the operand's own declaration, and nothing else. A type models exactly what it declares, so
         * {@code nat}'s {@code rig} <em>subtracts</em> {@code int}'s {@code add_group}, and a type that declares no
         * algebra licenses no rewrite. This is the policy {@link #declared(fURI, Type)} documents.
         */
        private static Type strict(final Type operand) {
            return operand;
        }

        /**
         * INHERIT — the non-strict policy: the nearest ancestor that DECLARES a block answers, so a type that says
         * nothing borrows its base's algebra ({@code age} would get int's ring, a bare refinement of real would get
         * real's field). Convenient for throwaway types, and unsound wherever a refinement <em>narrows</em> the
         * structure: {@code nat} must subtract {@code int}'s {@code add_group}, and no walk can express a subtraction.
         * A type that does declare (nat's rig) is unaffected — the walk stops at the operand itself.
         * <p>
         * The walk has the shape of {@link Type#pathIncludes(Type)}: a base type ends it, the root is never asked, and
         * when no ancestor declares anything the operand answers (which declares nothing either, so the policies agree).
         */
        private static Type inherit(final Type operand) {
            if (null == operand || null == operand.vid())
                return operand;
            Type type = operand;
            int hops = 0;
            while (null != type && hops++ < 256) {
                if (!TheoryHelper.instances(type).jvm().isEmpty())
                    return type;
                if (type.isBaseType() || type.isRootType())
                    break;
                type = type.parentType();
            }
            return operand;
        }

        /**
         * PRE-SWITCH (non-strict) — exactly the read this table made before the strict switch: the CONTRACT's own
         * carrier answers, so {@code plus?dom=/m/int} is read out of int's algebra whatever the operand is and a nat
         * operand inherits int's ring. Kept for comparison; it is the one body that changes today's answers.
         */
        private static Type byContract(final fURI contract) {
            return null == contract ? null : algebraOf(contract.dom());
        }

        /**
         * Whether a declared dom ranges over every carrier: the universal type {@code #} or a generic variable.
         */
        private static boolean isUniversal(final fURI dom) {
            return null != dom && null != dom.basePath() && (dom.basePath().equals(ALL.basePath()) || dom.basePath().isGeneric());
        }

        /**
         * THE GENERIC'S DECLARATION — the op's {@code ?decq} claim whose contract ranges over every carrier (a {@code #}
         * or type-variable dom), the answer when dispatch names no contract: a generic's laws hold at every instantiation
         * of its body. Dispatch misses {@code count?dom=#{*}} because {@link Type#isRootType()} compares the vid with its
         * coefficient; {@link #isUniversal} compares base paths.
         */
        private static Declared universalDeclared(final fURI base) {
            for (final Obj claim : Machine.read(base.addQ(QCollection.DECQ)).stream().toList()) {
                final Obj obj = claim.isRec() ? claim.asRec().atDirect(uri(OBJ)) : noobj();
                final Entry entry = obj.isInst() && isUniversal(obj.tid().dom()) ? declaredEntry(claim) : null;
                if (null != entry)
                    return new Declared(obj.tid(), entry);
            }
            return null;
        }

        /**
         * A chain instruction keyed with its declared n-tid for the carrier it folds — the form {@link #combiner},
         * {@code Form.of} and every declaration lookup read before resolution. A fold whose argument holds folds,
         * {@code group([k⇒h])}, has those keyed too, since its combiner is computed from theirs. The instruction itself
         * when nothing declares it: an undeclared op matches no law-bearing slot, fail closed.
         */
        public static Inst keyed(final Inst inst, final Type carrier) {
            final Declared declared = declared(inst.tid().basePath(), carrier);
            if (null == declared)
                return inst;
            final Inst typed = inst.tid(declared.tid());
            if (!GROUP_INST_TID.equals(typed.tid().basePath()) || 1 != typed.args().count() || !typed.arg(0).isRec())
                return typed;
            final Map<Obj, Obj> spec = new LinkedHashMap<>();
            typed.arg(0).asRec().elements().forEach(kv -> spec.put(kv.jvm().get0(),
                    kv.secondDirect().isInst() ? keyed(kv.secondDirect().asInst(), carrier) : kv.secondDirect()));
            return typed.args(lst(rec(spec)));
        }

        /**
         * THE COMBINER of a fold, computed and never declared: the fold its partials recombine through, so that
         * {@code (a ⊎ b)·r = (a·r ⊎ b·r)·combiner(r)} — Thm 19 with the re-application read off the theory. A stream is a
         * bag, so this is the universal property of the free commutative monoid: a fold that distributes is
         * {@code map(g)} followed by the fold of a commutative monoid, and its partials recombine through that fold.
         * <ul>
         *   <li>a fold declaring the laws itself is its own combiner — {@code sum}, {@code prod} — and the answer is
         *       {@code keyed} itself;</li>
         *   <li>a fold DEFINED as a pointwise map into one — its declared {@link Derivation}, {@code r ↦ map(g)·f} —
         *       recombines through {@code f}, read off {@code r}'s target object (the carrier {@code f} folds), with the
         *       generics bound from {@code keyed}'s own arguments — {@code count ↦ map(1)·sum},
         *       {@code order(B) ↦ split([_])·kmerge(B)};</li>
         *   <li>a fold whose argument holds folds recombines POINTWISE through theirs — {@code group([k⇒h])}, see
         *       {@link #pointwise} — and its combiner is a chain rather than one instruction.</li>
         * </ul>
         * {@code keyed} carries its declared n-tid ({@link #keyed}). Null when the fold has none: {@code mean}
         * (magmadic), {@code str} {@code sum} (not commutative), an undeclared or non-reducing instruction.
         */
        public static Call combiner(final Inst keyed, final Law... laws) {
            final Entry entry = lawsOf(keyed.tid());
            if (null == entry || Inst.Form.reducer != Inst.Form.of(keyed))
                return null;
            if (declaresAll(entry, laws))
                return keyed;
            if (GROUP_INST_TID.equals(keyed.tid().basePath()))
                return pointwise(keyed, laws);
            if (null == entry.derivation())
                return null;
            final Inst fold = entry.derivation().fold(keyed);
            if (null == fold)
                return null;
            // the definition's last step must itself be a FOLD of the target, declaring the laws -- a mapper that
            // happens to be monoidic and commutative (bool plus) recombines nothing
            final Declared declared = declared(fold.tid().basePath(), keyed.rng());
            return null != declared && Inst.Form.reducer == Inst.Form.of(fold.tid(declared.tid())) && declaresAll(declared.entry(), laws) ? fold : null;
        }

        /**
         * THE POINTWISE COMBINER. {@code group([k⇒h])} is a homomorphism from the bag into the recs of {@code h}'s monoid,
         * keyed by {@code k}: the partials merge by key, and each key's partials fold through {@code h}'s own combiner. So
         * it is computed from that combiner, never named — {@code sum()==[_=>combiner(h)]}: the rec {@code sum} (the deep
         * merge, which appends colliding partials into a bag — a lst kept whole, so an {@code order}'s sorted runs reach
         * {@code kmerge} as runs — and reads a bulked partial as its copies), then a per-key fold. Null, so nothing shards,
         * when a value is not a fold with a combiner ({@code mean}, a constant), or when the spec's entries fold differently
         * (a key keeps no record of the entry that claimed it, and the claim falls differently in every part, so even folds
         * with one combiner would mix).
         */
        private static Call pointwise(final Inst keyed, final Law... laws) {
            if (1 != keyed.args().count() || !keyed.arg(0).isRec())
                return null;
            final List<Obj> folds = keyed.arg(0).asRec().elements().map(Rel::secondDirect).toList();
            final Obj fold = folds.isEmpty() ? null : folds.getFirst();
            if (null == fold || !fold.isInst() || !folds.stream().allMatch(f -> f.isInst() && Inst.Helper.same(f.asInst(), fold.asInst())))
                return null;
            final Obj perKey = perKey(fold, laws);
            return null == perKey ? null : sum_().select_(rec(id_().tryToInst(), perKey));
        }

        /**
         * The per-key fold of one spec value: its combiner, a self-combining fold emitted as the chain wrote it (its op
         * address, resolved where it runs). Null when the value is no fold with a combiner.
         */
        private static Obj perKey(final Obj value, final Law... laws) {
            if (!value.isInst())
                return null;
            final Call fold = combiner(value.asInst(), laws);
            if (null == fold)
                return null;
            return fold == value ? value.asInst().tid(value.asInst().tid().basePath()) : fold;
        }

        /**
         * The combiner under Thm 19's hypothesis — a partial recombines through a commutative monoid's fold.
         */
        public static Call combiner(final Inst keyed) {
            return combiner(keyed, Law.monoidic, Law.commutative);
        }

        /**
         * The combiner as the category names it — the declared n-tid of the edge the partials recombine through, an
         * address as {@code analysis.inverse} is one — or null when the fold has no combiner, or recombines through a
         * chain ({@link #pointwise}) that no single edge names.
         */
        public static fURI combinerOf(final Inst keyed) {
            final Call fold = combiner(keyed);
            if (null == fold || !fold.isInst())
                return null;
            if (fold == keyed)
                return keyed.tid();
            final Declared declared = declared(fold.asInst().tid().basePath(), keyed.rng());
            return null == declared ? null : declared.tid();
        }

        private static boolean declaresAll(final Entry entry, final Law... laws) {
            return Arrays.stream(laws).allMatch(law -> entry.laws().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name())));
        }

        /**
         * Whether the inst declares the given process law — the guard a law-driven rewrite tests before firing.
         */
        public static boolean declares(final fURI instTid, final Law law) {
            final Entry entry = lawsOf(instTid);
            return null != entry && entry.laws().lstValue().stream()
                    .anyMatch(l -> l.uriValue().name().equals(law.name()));
        }

        /**
         * Whether an instruction declares a process law for the carrier's type. The chain instruction carries no
         * endpoints (the rewriter runs before resolution), so the declaration is resolved by op address,
         * disambiguated by the operand type — never guessed across types.
         */
        public static boolean declares(final Inst inst, final Type carrier, final Law law) {
            final Declared declared = declared(inst.tid().basePath(), carrier);
            return null != declared && declares(declared, law);
        }

        public static boolean declares(final Declared declared, final Law law) {
            return declared.entry().laws().lstValue().stream()
                    .anyMatch(l -> l.uriValue().name().equals(law.name()));
        }

        /**
         * ALL DERIVATIONS A REWRITE CAN APPLY — the values of {@link #declaredDerivations}, in declaration order (the
         * in-code rows first, then the equations instructions declare beside themselves).
         */
        public static List<Derivation> derivations() {
            return new ArrayList<>(declaredDerivations().values());
        }
    }
}
