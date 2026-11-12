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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.TriFunction;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.COMPOSE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MONOID_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.mInstSet.LAW_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.THEORY_FORM_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.sum_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.carriers;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.contracts;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
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
 * The type {@code law::T} lives beside it ({@code mInstSet.LAW_TYPE}, at {@link #LAW_TID}): a union of the constants'
 * labels, {@code uri::T[?union(absorbing, …, cyclic)]} (Marko, 2026-11-08). The vocabulary is closed: users assert these
 * labels and declare no new logics (ruling 2026-10-21).
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
     * effectful, so a rule that moves, drops or replicates code (the absorbing row's swallowed prefix,
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
     * address ({@code mInstSet.LAW_TYPE}), and every constant's value is written at {@link #address()} beneath it: one
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
                    + "declare pure is effectful, so a rule that moves, drops or replicates code (the absorbing row's swallowed "
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
                    + "through the fold (Thm 19), which law_shard realizes through the fold's computed combiner";
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
        // the vocabulary itself, documented once at the type's address beside mInstSet's docWrap of the type
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
         * THE CLAIMS SNAPSHOT — the equations the registered instructions' {@code ?decq} claims state: each defining
         * equation keyed by the address it is declared at, each law's claimants ({@link #claimants}) and each
         * theory's rules as its carriers' instances bind them ({@link #rules}), with each instruction's claim as the walk read it
         * ({@link Claim}, keyed by its tid), and the staging revision and the claim writes it was taken at. Retaken when
         * a builder stages anything (the revision moves), when a {@code ?decq} write lands ({@link #evict}), or while
         * the machine was not yet up; a retake re-reads only the claims a change can have reached ({@link #claims}).
         */
        private record Claims(Map<fURI, Rec> derivations, Map<Law, List<Inst>> claimants, Map<fURI, List<Rec>> rules,
                              Map<fURI, Claim> read) {
        }

        /**
         * AN INSTRUCTION'S CLAIM AS A WALK READ IT — whether the store answered with the instruction's own claim (an
         * ancestor's, which a read falls back to by subsumption, is not its own), and what the answer states for it: its
         * defining equation (noobj when it states none), and its term ({@link #op}) under every law it claims.
         */
        private record Claim(Inst inst, boolean own, Rec derivation, Map<Law, Inst> claimed) {
        }

        private static volatile Claims CLAIMS = new Claims(Map.of(), Map.of(), Map.of(), Map.of());
        private static volatile List<PendingWrites.Write> CLAIMS_AT = null;
        private static volatile long CLAIMS_SEEN = -1;
        private static volatile boolean CLAIMS_LOADED = false;
        private static final AtomicLong CLAIM_WRITES = new AtomicLong();

        /**
         * WHERE A RETAKE MUST READ AGAIN — the base paths a {@code ?decq} write landed under since the last retake
         * ({@link #evict}), and every base path a claim was ever staged or written under ({@code CLAIMED}, guarded by
         * itself, the retake's lock). The store answers an instruction whose own claim it does not hold by subsumption
         * over the claims under its base path, and type definitions decide that, so such an instruction is read again on
         * every retake.
         */
        private static final Set<fURI> EVICTED = ConcurrentHashMap.newKeySet();
        private static final Set<fURI> CLAIMED = new HashSet<>();

        /**
         * A BOOT IS A FRESH WORLD — forget the claims snapshot ({@code BootLoader.resetMemos}, before any set mounts).
         */
        public static void reset() {
            synchronized (CLAIMED) {
                CLAIMS = new Claims(Map.of(), Map.of(), Map.of(), Map.of());
                CLAIMS_AT = null;
                CLAIMS_SEEN = -1;
                CLAIMS_LOADED = false;
                EVICTED.clear();
                CLAIMED.clear();
            }
        }

        /**
         * A {@code ?decq} DECLARATION LANDED at {@code address} ({@code QCollection.EVICT}, which a claim the store already
         * held never reaches): the snapshot is retaken on its next read, and every instruction under the address's base
         * path is read again, since a changed claim can change an equation, a law or the instructions that state one.
         */
        public static void evict(final fURI address) {
            EVICTED.add(address.basePath());
            CLAIM_WRITES.incrementAndGet();
        }

        /**
         * The {@code decs::T} an inst declares beside itself ({@code ?decq}) — its process laws, inverse and derivation —
         * the inverse replaced when its carrier's algebra derives one. {@code noobjRec} when the inst declares nothing.
         */
        public static Rec lawsOf(final fURI instTid) {
            final Rec declared = declaredRow(instTid);
            return declared.isNoObj() ? declared : algebraic(instTid, declared, algebraOf(instTid.dom()));
        }

        /**
         * THE DECLARATION OF THE OBJ DISPATCH SELECTS ({@link Typer.Helper#deduceTypeInst}): a generic's laws hold for every
         * instantiation that runs its body, a specialization answers with its own, and a refined operand gets the laws of
         * the body it runs. The answer's {@code obj} is that contract, so its tid is the address a rewrite retypes an
         * unresolved chain instruction with (its coefficient shape made legible). The INVERSE is the OPERAND's algebra and
         * never inherits ({@link #algebraFor}): nat's rig subtracts int's add_group. When dispatch names no contract, the
         * op's generic claim answers; {@code noobjRec} when nothing declares the op.
         */
        public static Rec declared(final fURI op, final Type operand) {
            if (null == op || null == op.basePath())
                return noobjRec();
            final fURI base = op.basePath();
            if (null == operand || null == operand.vid() || DERIVING.get())
                return universalDeclared(base);
            final Inst contract = deduce(base, operand);
            final Rec row = null == contract ? noobjRec() : declaredRow(contract.tid());
            return row.isNoObj() ? universalDeclared(base) : algebraic(contract.tid(), row.at(OBJ, contract), operand);
        }

        /**
         * EVERY EQUATION THE CLAIMS STATE — a walk of the registered instructions through {@code Machine.read}, kept as the
         * {@link #CLAIMS} snapshot: a rewrite pass asks per evaluation, and a walk per pass is a regression. Every law an
         * instruction claims records its term ({@link #op}), which a law's rule binds ({@link #claimed}); instructions
         * sharing an op share the term. The theories' rules are bound per carrier instance ({@link #rules}).
         * <p>
         * A RETAKE READS AGAIN ONLY WHERE A CHANGE CAN REACH: an instruction under a base path a builder staged anything
         * at, or a {@code ?decq} write landed under, since the last walk, and one the store answers without its own claim
         * under a base path that holds claims ({@code CLAIMED}). Every other instruction keeps its {@link Claim} — a
         * builder stages per test, and a walk of every claim per retake cost about half a second (2026-11-08). One
         * retake at a time, so each change is taken once; the tables are rebuilt from the claims in the instset's order.
         */
        private static Claims claims() {
            if (CLAIMS_LOADED && PendingWrites.writes() == CLAIMS_AT && CLAIM_WRITES.get() == CLAIMS_SEEN)
                return CLAIMS;
            synchronized (CLAIMED) {
                final List<PendingWrites.Write> revision = PendingWrites.writes();
                final long written = CLAIM_WRITES.get();
                if (CLAIMS_LOADED && revision == CLAIMS_AT && written == CLAIMS_SEEN)
                    return CLAIMS;
                final Set<fURI> touched = new HashSet<>();
                for (final Iterator<fURI> evicted = EVICTED.iterator(); evicted.hasNext(); ) {
                    touched.add(evicted.next());
                    evicted.remove();
                }
                CLAIMED.addAll(touched);
                // THE LOG ONLY APPENDS, so a write at an index the last revision held is the same address, re-staged
                // when it is another write -- unless the log was cleared, and then no claim is kept
                final List<PendingWrites.Write> was = null == CLAIMS_AT ? List.of() : CLAIMS_AT;
                boolean cleared = revision.size() < was.size();
                for (int i = 0; i < revision.size(); i++) {
                    final PendingWrites.Write write = revision.get(i);
                    if (i < was.size() && write == was.get(i))
                        continue;
                    cleared |= i < was.size() && !write.address().equals(was.get(i).address());
                    touched.add(write.address().basePath());
                    if (write.address().hasQ(QCollection.DECQ))
                        CLAIMED.add(write.address().basePath());
                }
                final Map<fURI, Claim> held = cleared ? Map.of() : CLAIMS.read();
                final boolean loaded = Machine.loaded();
                final Map<fURI, Claim> read = new HashMap<>();
                final Map<fURI, Rec> derivations = new LinkedHashMap<>();
                final Map<Law, Set<Inst>> claimants = new EnumMap<>(Law.class);
                final Set<fURI> carriers = new LinkedHashSet<>();
                if (loaded)
                    for (final Inst inst : Machine.current().instset().insts()) {
                        if (null == inst.tid())
                            continue;
                        if (null != inst.tid().dom() && null != algebraOf(inst.tid().dom()))
                            carriers.add(inst.tid().dom().basePath());
                        final fURI operation = Inst.Helper.operationTid(inst.tid());
                        final fURI base = operation.basePath();
                        Claim claim = held.get(inst.tid());
                        if (null == claim || claim.inst() != inst || touched.contains(base) || !claim.own() && CLAIMED.contains(base)) {
                            final Obj stored = Machine.read(PendingWrites.decq(operation));
                            final Obj claimant = stored.isRec() ? stored.asRec().atDirect(uri(OBJ)) : noobj();
                            final Rec declared = declaredEntry(stored);
                            final Inst op = stored.isRec() ? op(inst) : null;
                            final Map<Law, Inst> stated = new EnumMap<>(Law.class);
                            for (final Law law : Law.values())
                                if (declares(declared, law))
                                    stated.put(law, op);
                            claim = new Claim(inst, claimant.isInst() && Inst.Helper.operationTid(claimant.tid()).equals(operation),
                                    stored.isRec() ? declaredDerivation(stored.asRec()) : noobjRec(), stated);
                        }
                        read.put(inst.tid(), claim);
                        if (!claim.derivation().isNoObj())
                            derivations.putIfAbsent(operation, claim.derivation());
                        claim.claimed().forEach((law, term) -> claimants.computeIfAbsent(law, key -> new LinkedHashSet<>()).add(term));
                    }
                final Map<Law, List<Inst>> terms = new EnumMap<>(Law.class);
                claimants.forEach((law, term) -> terms.put(law, List.copyOf(term)));
                // THE THEORIES' RULES, FILLED IN BY EVERY INSTANCE A CARRIER DECLARES: a theory states its rules in its own
                // role names (monoid_theory's op(id) ⇝ ε) and an instance of that theory binds them (int's add_monoid
                // gives plus(0) ⇝ ε). A theory refines no other, so an instance answers only its own theory's rules, kept
                // by theory: a rewrite reads one theory's table and gates it its own way
                final Map<fURI, List<Obj>> stated = new HashMap<>();
                final Map<fURI, Set<Rec>> bound = new LinkedHashMap<>();
                for (final fURI carrier : carriers)
                    for (final Obj block : TheoryHelper.instances(T(carrier)).jvm().values()) {
                        if (!block.isRec() || null == block.asRec().tid())
                            continue;
                        for (final Obj rule : stated.computeIfAbsent(block.asRec().tid().basePath(), theory -> {
                            final Obj rewrite = TheoryHelper.stated(theory, REWRITE);
                            return rewrite.isLst() ? rewrite.lstValue() : List.of();
                        })) {
                            final Rel equation = rule.isRec() ? rule.asRec().<Rel>elements().findFirst().orElse(null) : null;
                            final List<Inst> lhs = null == equation ? null : bind(equation.first().asCall().insts(), block.asRec(), derivations);
                            final List<Inst> rhs = null == equation ? null : bind(equation.second().asCall().insts(), block.asRec(), derivations);
                            if (null != lhs && null != rhs && !lhs.isEmpty())
                                bound.computeIfAbsent(block.asRec().tid().basePath(), theory -> new LinkedHashSet<>()).add(rec(code(lhs), code(rhs)));
                        }
                    }
                final Map<fURI, List<Rec>> theories = new LinkedHashMap<>();
                bound.forEach((theory, rule) -> theories.put(theory, List.copyOf(rule)));
                final Claims snapshot = new Claims(derivations, terms, theories, read);
                CLAIMS = snapshot;
                CLAIMS_AT = revision;
                CLAIMS_SEEN = written;
                CLAIMS_LOADED = loaded;
                return snapshot;
            }
        }

        /**
         * ONE SIDE OF A THEORY'S RULE AS AN INSTANCE BINDS IT — the rule is stated in the theory's role names and the
         * instance fills them in: an instruction a role names is that role's operation, by its base path (a unit drops by
         * its literal), and an argument naming a role is that role's value, so monoid's {@code op(id)}, bound by int's
         * {@code add_monoid}, is {@code plus(0)}. COMPOSITION IS ADJACENCY in a chain ({@code .} is compose), so an
         * operation bound to {@code compose} hands on its operations in place: code's {@code mul_monoid} gives
         * {@code id()}. AN OPERATION APPLIED TO ANOTHER ROLE'S OPERATION, {@code op(inv(a))}, is the instruction DEFINED
         * that way, read off the derivations over the operation's carrier: group's {@code op(a)·op(inv(a))}, bound by int's
         * {@code add_group}, is {@code plus(a)·minus(a)}, since {@code minus ≡ plus∘neg}. Null when the instance leaves a
         * role the rule names unbound (a pointer whose target is not registered, an inverse that is no operation), when
         * nothing defines the composite, or when a role binds {@code branch}, whose operands are lanes rather than a chain:
         * code's {@code add_monoid} means {@code branch(a, end) = a}, which no chain rule spells yet.
         */
        private static List<Inst> bind(final List<Inst> insts, final Rec instance, final Map<fURI, Rec> derivations) {
            final List<Inst> out = new ArrayList<>();
            for (final Inst inst : insts) {
                final Obj op = instance.jvm().containsKey(uri(inst.tid().name())) ? instance.at(f(inst.tid().name())) : inst;
                if (!op.isInst() || BRANCH_INST_TID.basePath().equals(op.asInst().tid().basePath()))
                    return null;
                final Obj nested = 1 == inst.args().count() && inst.arg(0).isInst() ? inst.arg(0) : noobj();
                if (nested.isInst() && instance.jvm().containsKey(uri(nested.asInst().tid().name()))) {
                    final Obj inner = instance.at(f(nested.asInst().tid().name()));
                    if (!inner.isInst())
                        return null;
                    final fURI form = derivations.keySet().stream()
                            .filter(tid -> sameCarrier(tid, op.asInst().tid()) && defined(derivations.get(tid), op.asInst().tid(), inner.asInst().tid()))
                            .findFirst()
                            .orElse(null);
                    if (null == form)
                        return null;
                    out.add(instB(form.basePath(), nested.asInst().args()));
                    continue;
                }
                final List<Obj> args = new ArrayList<>();
                for (final Obj arg : inst.args().lstValue()) {
                    final Obj value = arg.isUri() && instance.jvm().containsKey(arg) ? instance.at(arg.uriValue()) : arg;
                    if (value.isNoObj())
                        return null;
                    args.add(value);
                }
                if (!COMPOSE_INST_TID.basePath().equals(op.asInst().tid().basePath()))
                    out.add(instB(op.asInst().tid().basePath(), lst(args)));
                else
                    for (final Obj arg : args) {
                        if (!arg.isInst())
                            return null;
                        out.add(instB(arg.asInst().tid().basePath(), arg.asInst().args()));
                    }
            }
            return out;
        }

        /**
         * An instruction's op as a rule term: its address with one named hole per argument slot, so a pair of the op over
         * the same operands binds each name once ({@code plus(#::T@a0).minus(#::T@a0)}).
         */
        private static Inst op(final Inst inst) {
            return instB(inst.tid().basePath(), lst(IntStream.range(0, (int) inst.args().count())
                    .mapToObj(slot -> (Obj) T(ALL, f("a" + slot), null, null)).toList()));
        }

        /**
         * Whether a derivation is in flight on this thread — the guard {@link #declared} answers the generic claim under.
         */
        private static final ThreadLocal<Boolean> DERIVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

        /**
         * THE CLAIM AN INSTRUCTION DECLARED BESIDE ITSELF — one {@code Machine.read} of its {@code ?decq} address. A
         * claim {@link InstBuilder#create()} staged and nothing has published yet is published by that read (the
         * q-proc's lazy hook), so the store is the only source.
         */
        private static Rec declaredRow(final fURI instTid) {
            return null == instTid ? noobjRec() : declaredRow(instTid, Machine.read(stagingAddress(instTid)));
        }

        /**
         * The claim the store answered at an instruction's staging address, as {@link #declaredRow(fURI)} reads it.
         * THE STORE ANSWERS FOR THE QUERIED CONTRACT ONLY. A read of an address it holds no claim for falls back to
         * subsumption, so plus?imperial answers real's plus claim (an imperial is a real) -- but a claim is inherited
         * through the contract deduce() lands the operand on, never through the store: an ancestor's claim is refused.
         */
        private static Rec declaredRow(final fURI instTid, final Obj stored) {
            final Obj claimant = stored.isRec() ? stored.asRec().atDirect(uri(OBJ)) : noobj();
            return claimant.isInst() && !Inst.Helper.operationTid(claimant.tid()).equals(Inst.Helper.operationTid(instTid))
                    ? noobjRec() : declaredEntry(stored);
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
         * THE CLAIM AN INSTRUCTION DECLARED BESIDE ITSELF AS THE STORE HOLDS IT ({@code ?decq}, staged by
         * {@code InstBuilder.create}): the {@code decs::T} itself, answered only when it declares a process law, an
         * inverse or a defining equation — a claim of nothing else (a form alone) is {@code noobjRec}.
         */
        private static Rec declaredEntry(final Obj fromDecq) {
            if (!fromDecq.isRec())
                return noobjRec();
            final Obj declaredLaw = fromDecq.asRec().atDirect(uri(LAW));
            // A TYPE-VALUED SLOT MEANS "ANY LAW": a declaration may state law => uri(LAW_TID) (law::T) instead of a list,
            // so expand it to the vocabulary rather than letting a non-lst silently answer "no laws declared".
            final Rec declaration = declaredLaw.isUri() && Law.LAW_TID.equals(declaredLaw.uriValue())
                    ? fromDecq.asRec().at(LAW, lst(Arrays.stream(Law.values()).map(law -> (Obj) uri(law.name())).toList()))
                    : fromDecq.asRec();
            // the inverse is read as an ADDRESS, never as a value: at(...) auto-resolves and would follow the inverse
            // edge into the instruction it names instead of answering which instruction it is
            return declaration.atDirect(uri(LAW)).isLst() || null != TheoryHelper.roleAddress(declaration, INVERSE)
                    || !declaredDerivation(declaration).isNoObj() ? declaration : noobjRec();
        }

        /**
         * The defining equation a declaration carries — its {@code [lhs=>code, rhs=>code]} rec — or {@code noobjRec} when
         * the rec holds none. A half-written derivation answers no derivation: it states no equation, and a rewrite run
         * off half of one would be a guess.
         */
        private static Rec declaredDerivation(final Rec declaration) {
            final Obj derivation = declaration.atDirect(uri(DERIVATION));
            return derivation.isRec() && derivation.asRec().atDirect(uri(LHS)).isCode() && derivation.asRec().atDirect(uri(RHS)).isCode()
                    ? derivation.asRec() : noobjRec();
        }

        /**
         * WHAT A DECLARATION MAY STATE — the refusal a {@code ?decq} write answers, or noobj when the declaration is sound
         * (installed as {@code QCollection.VALIDATE}, so a refused claim never reaches the store):
         * <ul>
         *   <li>every law it claims is a {@code law::T}, the union of the vocabulary's labels (Marko, 2026-11-08), or the
         *       law slot is {@code law::T}'s own address, any law;</li>
         *   <li>its theories are a rec keyed by {@code theory_form::T}, the union of the theory forms (Marko, 2026-11-08):
         *       {@code ring}, {@code add_monoid}, {@code mul_monoid}, …, never what an instruction does on the carrier;</li>
         *   <li>a derivation is two codes, its lhs ends in the instruction it defines, and its sides declare the same
         *       value names, so the equation runs both ways: the write-side twin of every name binding
         *       ({@link Code.Helper#bind}).</li>
         * </ul>
         * Whether a law's shape names the instruction's contract is the census's question (LawCensusTest): a claim no
         * shape names is a category error it reports, and some built-in claims await a shape of their own (mean's
         * reduction), so refusing them here would drop declarations the census still allows.
         */
        public static Obj validate(final Rec declaration) {
            final Obj laws = declaration.atDirect(uri(LAW));
            if (!laws.isNoObj() && !(laws.isUri() && LAW_TID.equals(laws.uriValue()))) {
                if (!laws.isLst())
                    return fail(MTronException.of("the laws a declaration claims are a lst of law::T, not %s", laws));
                for (final Obj law : laws.lstValue())
                    if (!law.test(LAW_TYPE))
                        return fail(MTronException.of("%s is no law::T, the union of the declared labels", law));
            }
            final Obj theories = declaration.atDirect(uri(THEORY));
            if (!theories.isNoObj()) {
                if (!theories.isRec())
                    return fail(MTronException.of("the theories a declaration models are a rec keyed by theory_form::T, not %s", theories));
                for (final Obj form : theories.asRec().jvm().keySet())
                    if (!form.test(THEORY_FORM_TYPE))
                        return fail(MTronException.of("%s is no theory_form::T, the union of the theory forms", form));
            }
            final Obj derivation = declaration.atDirect(uri(DERIVATION));
            if (derivation.isNoObj())
                return noobj();
            final Obj lhs = derivation.isRec() ? derivation.asRec().atDirect(uri(LHS)) : noobj();
            final Obj rhs = derivation.isRec() ? derivation.asRec().atDirect(uri(RHS)) : noobj();
            if (!lhs.isCode() || !rhs.isCode() || lhs.asCode().insts().isEmpty())
                return fail(MTronException.of("a derivation is [lhs=>code, rhs=>code], not %s", derivation));
            final Obj obj = declaration.atDirect(uri(OBJ));
            final Inst derived = lhs.asCode().insts().getLast();
            // by NAME: the declaration's address states the namespace, and an lhs parsed before its instruction is
            // registered carries the bare name
            if (obj.isObjInst() && !derived.tid().name().equals(obj.tid().name()))
                return fail(MTronException.of("a derivation's lhs ends in the instruction it defines: %s is not %s", derived, obj));
            final Set<fURI> lhsNames = new HashSet<>();
            final Set<fURI> rhsNames = new HashSet<>();
            if (null == Inst.Helper.declarations(lhs.asCode().insts(), lhsNames) || null == Inst.Helper.declarations(rhs.asCode().insts(), rhsNames))
                return fail(MTronException.of("a derivation declares one generic as two types: %s", derivation));
            return lhsNames.equals(rhsNames) ? noobj() :
                    fail(MTronException.of("a derivation's sides declare the same names, so it runs both ways: %s against %s", lhsNames, rhsNames));
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
         * This is SOURCE 1 of the three {@link #algebraic} names — the algebra, and the only one that can answer for an
         * endomorphism. Source 2 (the graph's symmetric {@code coupling}, which proposes a partner but not a direction)
         * and source 3 (the declared field) are noted there.
         */
        private static fURI derivedInverse(final fURI tid, final Rec entry, final Type algebra) {
            if (null == tid)
                return null;
            if (declares(entry, Law.involution))
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
                } else if (defined(declaredDerivation(entry), op, inv)) {
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
        private static boolean defined(final Rec derivation, final fURI op, final fURI inv) {
            if (derivation.isNoObj())
                return false;
            final List<Inst> steps = derivation.atDirect(uri(RHS)).asCode().insts();
            for (int i = 0; i < steps.size(); i++) {
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
         * THE DEFINITION IS READ WHEREVER IT IS DECLARED — among the equations every registered instruction declares
         * beside itself in {@code ?decq} ({@link #claims}). The equation lives with the SIBLING it
         * defines, not with {@code tid}: {@code mult}→{@code div} is a consequence of {@code mul_group} plus
         * {@code div ≡ mult ∘ inv}, and the equation is {@code div}'s claim, not {@code mult}'s.
         */
        private static fURI inverseForm(final fURI tid, final fURI op, final fURI inv) {
            for (final Map.Entry<fURI, Rec> entry : claims().derivations().entrySet())
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
         * The registered contract this type lands on, or null when the machine registers none (an unbooted machine
         * included) — see {@link Typer.Helper#deduceTypeInst}.
         */
        private static Inst deduce(final fURI base, final Type operand) {
            return Machine.loaded() ? Typer.Helper.deduceTypeInst(Machine.current().instset(), operand, base) : null;
        }

        /**
         * The declaration as the OPERAND's algebra answers it: its inverse replaced when that algebra derives one, the
         * declared retraction kept when it does not, and the declaration itself when the two agree. Strictly the
         * operand's — see {@link #declared(fURI, Type)}.
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
        private static Rec algebraic(final fURI tid, final Rec declared, final Type operand) {
            DERIVING.set(Boolean.TRUE);
            try {
                final fURI inverse = derivedInverse(tid, declared, algebraFor(operand, tid));
                return null == inverse || inverse.equals(TheoryHelper.roleAddress(declared, INVERSE)) ?
                        declared : declared.at(INVERSE, uri(inverse));
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
         * {@code strict} and {@code inherit} were identical (162 tests green either way), because a type that declares
         * a block ({@code nat}'s {@code rig}) is answered by its own block in both policies, and a type that declares
         * none ({@code imperial}) is stopped earlier, at the CLAIM — its {@code plus?imperial} contract declares no
         * {@code ?decq} claim, so {@link #declared(fURI, Type)} answers noobjRec before an algebra is ever read. The
         * policy becomes load-bearing when a type has claims but no block: once delegation ({@code law => int}) lands,
         * or the claim read learns to fall back to an ancestor's claim. {@link #byContract} is the loosest body and the
         * only one that differs today: it hands {@code nat} int's ring, which
         * {@code catInstSetTest#testStrictLawResolution} flags.
         *
         * @param operand  the type being operated on
         * @param contract the n-tid of the claim being answered, whose dom is the implementation's carrier
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
         * PRE-SWITCH (non-strict) — exactly the read the retired LawTable made before the strict switch: the
         * CONTRACT's own carrier answers, so {@code plus?dom=/m/int} is read out of int's algebra whatever the
         * operand is and a nat operand inherits int's ring. Kept for comparison; it is the one body that changes
         * today's answers.
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
        private static Rec universalDeclared(final fURI base) {
            for (final Obj claim : Machine.read(base.addQ(QCollection.DECQ)).stream().toList()) {
                final Obj obj = claim.isRec() ? claim.asRec().atDirect(uri(OBJ)) : noobj();
                final Rec entry = obj.isInst() && isUniversal(obj.tid().dom()) ? declaredEntry(claim) : noobjRec();
                if (!entry.isNoObj())
                    return entry;
            }
            return noobjRec();
        }

        /**
         * A chain instruction keyed with its declared n-tid for the carrier it folds — the form {@link #combiner},
         * {@code Form.of} and every declaration lookup read before resolution. A fold whose argument holds folds,
         * {@code group([k⇒h])}, has those keyed too, since its combiner is computed from theirs. The instruction itself
         * when nothing declares it: an undeclared op matches no law-bearing slot, fail closed.
         */
        public static Inst keyed(final Inst inst, final Type carrier) {
            final Rec declared = declared(inst.tid().basePath(), carrier);
            if (declared.isNoObj())
                return inst;
            final Inst typed = inst.tid(declared.atDirect(OBJ).tid());
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
         *   <li>a fold DEFINED as a pointwise map into one — its declared derivation, {@code r ↦ map(g)·f} —
         *       recombines through {@code f}, read off {@code r}'s target object (the carrier {@code f} folds): the
         *       equation run forward as an {@code update()} rule, its holes filled from {@code keyed}'s own arguments —
         *       {@code count ↦ map(1)·sum}, {@code order(#{?}::T@b) ↦ split([_])·kmerge(#{?}::T@b)}, which a bare
         *       {@code order()} matches too;</li>
         *   <li>a fold whose argument holds folds recombines POINTWISE through theirs — {@code group([k⇒h])}, see
         *       {@link #pointwise} — and its combiner is a chain rather than one instruction.</li>
         * </ul>
         * {@code keyed} carries its declared n-tid ({@link #keyed}). Null when the fold has none: {@code mean}
         * (magmadic), {@code str} {@code sum} (not commutative), an undeclared or non-reducing instruction.
         */
        public static Call combiner(final Inst keyed, final Law... laws) {
            final Rec entry = lawsOf(keyed.tid());
            if (entry.isNoObj() || Inst.Form.reducer != Inst.Form.of(keyed))
                return null;
            if (Arrays.stream(laws).allMatch(law -> declares(entry, law)))
                return keyed;
            if (GROUP_INST_TID.equals(keyed.tid().basePath()))
                return pointwise(keyed, laws);
            final Rec derivation = declaredDerivation(entry);
            if (derivation.isNoObj())
                return null;
            // the equation run forward: keyed binds the lhs's last instruction as update() binds a run (every name bound),
            // and the names its holes matched fill the rhs's last instruction -- the fold the partials recombine through
            final Inst derived = derivation.atDirect(uri(LHS)).asCode().insts().getLast();
            if (null == Code.Helper.bind(List.of(keyed), List.of(derived)))
                return null;
            final Inst fold = Call.Helper.update(List.of(keyed),
                    rec(code(List.of(derived)), code(List.of(derivation.atDirect(uri(RHS)).asCode().insts().getLast())))).getFirst();
            // the definition's last step must itself be a FOLD of the target, declaring the laws -- a mapper that
            // happens to be monoidic and commutative (bool plus) recombines nothing
            final Rec declared = declared(fold.tid().basePath(), keyed.rng());
            return !declared.isNoObj() && Inst.Form.reducer == Inst.Form.of(fold.tid(declared.atDirect(OBJ).tid()))
                    && Arrays.stream(laws).allMatch(law -> declares(declared, law)) ? fold : null;
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
            if (null == perKey)
                return null;
            // THE PARTIALS ARE RECS, one per part, so the merge is rec's sum, keyed at rec as a chain instruction is
            // before resolution (keyed). Left bare behind a branch that hands on #{*}, the merge kept no range, and
            // the select after it bound the first contract of its family, uri's projection, which a rec cannot enter
            final Call merge = sum_().select_(rec(id_().tryToInst(), perKey));
            return code(List.of(keyed(merge.insts().getFirst(), T(REC_TID)), merge.insts().getLast()));
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
            final Rec declared = declared(fold.asInst().tid().basePath(), keyed.rng());
            return declared.isNoObj() ? null : declared.atDirect(OBJ).tid();
        }

        /**
         * Whether the inst declares the given process law — the guard a law-driven rewrite tests before firing.
         */
        public static boolean declares(final fURI instTid, final Law law) {
            return declares(lawsOf(instTid), law);
        }

        /**
         * Whether an instruction declares a process law for the carrier's type. The chain instruction carries no
         * endpoints (the rewriter runs before resolution), so the declaration is resolved by op address,
         * disambiguated by the operand type — never guessed across types.
         */
        public static boolean declares(final Inst inst, final Type carrier, final Law law) {
            return declares(declared(inst.tid().basePath(), carrier), law);
        }

        /**
         * Whether a declaration ({@link #lawsOf}, {@link #declared}) states the given process law in its {@code law} lst.
         */
        public static boolean declares(final Rec declared, final Law law) {
            final Obj laws = declared.atDirect(uri(LAW));
            return laws.isLst() && laws.asLst().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name()));
        }

        /**
         * ALL DERIVATIONS A REWRITE CAN APPLY — the equations instructions declare beside themselves ({@code ?decq}),
         * each keyed by the contract that declares it, in the order the machine's instset lists them ({@link #claims}).
         * The key is where the equation speaks ({@link #selects}).
         */
        public static Map<fURI, Rec> derivations() {
            return Collections.unmodifiableMap(claims().derivations());
        }

        /**
         * A LAW'S CLAIMANTS — the term ({@link #op}: its address with one named hole per argument slot) of every
         * registered instruction whose claim states {@code law} ({@link #claims}). Instructions sharing an op share the
         * term.
         */
        public static List<Inst> claimants(final Law law) {
            return claims().claimants().getOrDefault(law, List.of());
        }

        /**
         * A LAW'S RULES AS ITS CLAIMANTS BIND THEM (Marko, 2026-11-09) — a law's rules are stated in the name {@code op},
         * the instruction whose claim states the law (the law's rewrite, one of /m's), and every claimant fills {@code op} wherever it
         * stands. An {@code op} that states no argument is the claimant's term, holes and all, so a pair binds its operands
         * equal: involution's {@code op·op ⇝ ε}, bound by {@code neg}, is {@code neg()·neg() ⇝ ε}, and idempotent's
         * {@code op·op ⇝ op}, bound by bool's {@code plus}, is {@code plus(a0)·plus(a0) ⇝ plus(a0)}. An {@code op} that
         * states its own arguments is the claimant's operation over them: poset's {@code is(op(#::T@a))}, bound by
         * {@code gt}, is {@code is(gt(#::T@a))}. A rewrite matches and applies them in Java ({@code Call.Helper.update}),
         * where the law holds at the carrier.
         */
        public static List<Rec> claimed(final Rec rule, final Law law) {
            final List<Rec> bound = new ArrayList<>();
            for (final Rel equation : rule.<Rel>elements().toList())
                if (equation.first().isCall() && equation.second().isCall())
                    for (final Inst term : claimants(law))
                        bound.add(rec(fill(equation.first(), term), fill(equation.second(), term)));
            return bound;
        }

        /**
         * One side of a law's rule with {@code op} filled by a claimant's term, at any depth ({@link #claimed}).
         */
        private static Obj fill(final Obj side, final Inst term) {
            if (side.isInst()) {
                final Inst inst = side.asInst();
                if (OP.equals(inst.tid().name()))
                    return inst.args().isEmpty() ? term : instB(term.tid(), inst.args());
                return inst.args().isLst() ? inst.args(lst(inst.args().<Obj>elements().map(arg -> fill(arg, term)).toList())) : inst;
            }
            return side.isCall() ? code(side.asCall().insts().stream().map(inst -> fill(inst, term).asInst()).toList()) : side;
        }

        /**
         * A LAW'S LOCAL EQUATIONS, APPLIED IN JAVA (Marko, 2026-11-08): each {@code [lhs => rhs]} rule is a
         * {@link Call.Helper#update} over the chain's Code objects (matched and changed there, nothing evaluated), and a
         * matched run changes only where {@code admits} the code at the run's start, the law read at the carrier there. A
         * run whose instructions carry a coefficient stays: the rule's terms are unscaled, and {@code f{2}·f{2}} is no
         * {@code f·f}.
         */
        public static Code rewrite(final Code code, final List<Rec> rules, final TriFunction<Code, Integer, Integer, Boolean> admits) {
            Code out = code;
            for (final Rec rule : rules) {
                final Code scanned = out;
                final List<Inst> pattern = rule.elements().findFirst().map(lhs -> lhs.first().asCall().insts()).orElse(List.of());
                // a rule naming an op the chain does not hold cannot match it, so it is not scanned at all: every element a
                // run must take ({+}# takes one at least, {*}# may take none) tests an instruction of the chain
                if (pattern.isEmpty() || !pattern.stream().allMatch(element -> element.tid().b().isZeroable()
                        || scanned.insts().stream().anyMatch(inst -> inst.tid().basePath().test(element.tid().basePath()))))
                    continue;
                final List<Inst> next = Call.Helper.update(scanned.insts(), rule, (start, length) ->
                        scanned.insts().subList(start, start + length).stream().allMatch(inst -> cInt.ONE().equals(inst.c()))
                                && admits.apply(scanned, start, length));
                out = next.equals(scanned.insts()) ? scanned : code(next);
            }
            return out;
        }

        /**
         * A REWRITE'S RULES (Marko, 2026-11-10: one rewrite per rule) — each of /m's rule rewrites carries its rules as its
         * declared argument, a rec of ROWS, a key and its rules: a value, not a type, so the rewrite resolves with its rules
         * already bound. The rows apply in order ({@code form_map_unwrap} has two). A row keyed by a LAW states its rule
         * in the name {@code op}, bound to every instruction whose claim states the law ({@link #claimed}) and applied under
         * that law's gate at the chain's carrier:
         * <ul>
         *   <li>{@code involution} {@code op·op ⇝ ε}, where the operation is an involution there, or the carrier's
         *       additive-group inverse ({@link #isInvolution});</li>
         *   <li>{@code idempotent} {@code op·op ⇝ op}, where it is declared idempotent there;</li>
         *   <li>{@code monoidic} {@code op·op ⇝ op}, on a fold ({@link #isFold});</li>
         *   <li>{@code poset} {@code is(op(a))·is(op(b)) ⇝ is(op(b))}, where {@code b} is the stricter bound
         *       ({@link #isStricter});</li>
         *   <li>{@code absorbing} {@code {+}#·op ⇝ op}, a run of one or more instructions of any name and arguments before
         *       the op, where the op absorbs it there ({@link #isAbsorbed}).</li>
         * </ul>
         * A law a row names but no gate knows is not applied. A row keyed by a FORM ({@link Inst.Form}) states its rules
         * over named holes, and its holes range over the form: a matched run changes only where the operation of everything
         * a hole captured, an instruction or the op symbol a chain carries before resolution, declares that form. A form is
         * declared per operation ({@code InstBuilder.declaredForm}), so no carrier is read. The {@code mapper} row's
         * {@code map(#::T@f) ⇝ #::T@f} unwraps {@code map(neg)}, never {@code map(sum())}. A row keyed by anything else is
         * PLAIN ({@code map_nest}, {@code else_after_count}, {@code range_skip_take}): its rule is stated in the instructions
         * themselves, so it applies as it stands, with no {@code op} to bind and no gate; a {@code ${...}} template in its
         * change computes an argument from the names the run bound.
         */
        public static Code lawRemoval(final Code code, final Rec table) {
            Code out = code;
            for (final Rel entry : table.<Rel>elements().toList()) {
                if (!entry.second().isRec())
                    continue;
                final String key = entry.first().isUri() ? entry.first().uriValue().name() : null;
                final Law law = Arrays.stream(Law.values()).filter(candidate -> candidate.name().equals(key)).findFirst().orElse(null);
                final Inst.Form form = Arrays.stream(Inst.Form.values()).filter(candidate -> candidate.name().equals(key)).findFirst().orElse(null);
                if (null != law) {
                    // a gate per bound rule, built from the rule's own pattern: the poset gate reads the bounds its run bound
                    final Function<List<Inst>, TriFunction<Code, Integer, Integer, Boolean>> gate = switch (law) {
                        case involution -> pattern -> (scanned, start, length) -> isInvolution(scanned.insts().get(start), carriers(scanned).get(start));
                        case idempotent -> pattern -> (scanned, start, length) -> declares(scanned.insts().get(start), carriers(scanned).get(start), Law.idempotent);
                        case monoidic -> pattern -> (scanned, start, length) -> isFold(scanned.insts().get(start), carriers(scanned).get(start));
                        case poset -> pattern -> (scanned, start, length) -> isStricter(Code.Helper.bind(scanned.insts().subList(start, start + length), pattern),
                                scanned.insts().get(start), carriers(scanned).get(start));
                        case absorbing -> pattern -> Helper::isAbsorbed;
                        default -> null;
                    };
                    if (null != gate)
                        for (final Rec bound : claimed(entry.second().asRec(), law))
                            out = rewrite(out, List.of(bound), gate.apply(bound.<Rel>elements().findFirst().orElseThrow().first().asCall().insts()));
                } else if (null != form)
                    for (final Rel rule : entry.second().asRec().<Rel>elements().toList()) {
                        final List<Inst> pattern = rule.first().asCall().insts();
                        out = rewrite(out, List.of(rec(rule.first(), rule.second())), (scanned, start, length) -> {
                            final Map<fURI, Obj> captured = Code.Helper.bind(scanned.insts().subList(start, start + length), pattern);
                            return null != captured && !captured.isEmpty() && captured.values().stream().allMatch(hole -> form == InstBuilder.declaredForm(
                                    hole.isInst() ? hole.asInst().tid() : hole.isUri() ? Machine.current().memory().alignPrefix(hole.uriValue()) : null));
                        });
                    }
                else
                    out = rewrite(out, List.of(entry.second().asRec()), (scanned, start, length) -> true);
            }
            return out;
        }

        /**
         * THE POSET GATE — the bound a rule keeps, {@code b}, is the stricter of the pair: the comparison itself orders it
         * past {@code a} ({@code b > a} under {@code gt}, {@code b < a} under {@code lt}), or the two are equal. The
         * comparison is evaluated, so it must be declared {@code pure} as well as {@code poset} at the carrier, and both
         * bounds must be values. A pair the order relates neither way (a partial order need not) stays.
         */
        private static boolean isStricter(final Map<fURI, Obj> names, final Inst filter, final Type carrier) {
            final Obj a = null == names ? null : names.get(f("a"));
            final Obj b = null == names ? null : names.get(f("b"));
            if (null == a || null == b || a.isCall() || b.isCall())
                return false;
            final Inst comparison = filter.arg(0).asCall().insts().getFirst();
            return declares(comparison, carrier, Law.poset) && declares(comparison, carrier, Law.pure)
                    && (a.equals(b) || Processor.Helper.apply(b, comparison.args(lst(a))).booleanCheck());
        }

        /**
         * The involution test: the operation's own declared {@code involution} law, or the carrier's declared
         * additive-group inverse.
         */
        private static boolean isInvolution(final Inst inst, final Type carrier) {
            if (declares(inst, carrier, Law.involution))
                return true;
            final Inst inv = TheoryHelper.invInst(carrier);
            return !inv.isNoObj() && inv.test(inst);
        }

        /**
         * A fold: the declared coefficient shape gathers many to one ({@code reducer} or {@code join}) and the
         * operation is declared {@code monoidic} — the shape a repeated fold is idempotent on.
         */
        private static boolean isFold(final Inst inst, final Type carrier) {
            final Rec declared = declared(inst.tid().basePath(), carrier);
            if (declared.isNoObj())
                return false;
            final Inst.Form form = Inst.Form.of(inst.tid(declared.atDirect(OBJ).tid()));
            return (Inst.Form.reducer == form || Inst.Form.join == form) && declares(inst, carrier, Law.monoidic);
        }

        /**
         * THE ABSORBING GATE — an absorbing op is a constant of the chain, so the run before it that cannot change what it
         * answers is dead code, {@code …·g·zero ⇝ …·zero}. The run is the rule's {@code {+}#}, which a rewrite takes as long
         * as it can and shortens where this refuses it ({@link Call.Helper#update}), and never holds the seed: the chain's
         * initial form, read off the contract its typing resolved it to. The op must be absorbing at the carrier flowing
         * into it, and dropping each instruction of the run must not change what the op answers, read off declarations
         * alone, so an undeclared op (no legible form) is never dropped:
         * <ul>
         *   <li><b>pure</b> — the instruction declares {@link Law#pure}. One that does not is effectful, so an undeclared
         *       op (side, print, to) stops the run.</li>
         *   <li><b>multiplicity</b> — a terminal op (rng {@code {0}}) answers noobj whatever reaches it, so any pure
         *       instruction goes. Any other answers a constant PER OBJ, so the instruction must hand it one obj per obj: a
         *       {@code mapper}, or a {@code filter} when the op is non-strict — its {@code {?}} dom fires on the noobj the
         *       filter leaves, while a strict op ({@code {1}}, {@code {+}} dom) never sees it
         *       ({@code {1,2,3}.is(gt(1)).zero()} is {@code {2}0}, and {@code {1,2,3}.sum().zero()} is {@code 0}).</li>
         *   <li><b>carrier</b> — the op's law was read for the chain's carrier, so the instruction must map its own carrier
         *       to itself: dom and rng both the carrier's base type, or both the one generic ({@code A ⇒ A}). Both ends,
         *       because a declaration is found by EITHER end matching the carrier ({@code as?int<=str} answers an int
         *       carrier too), and {@code 5.as(str::T).zero()} is str's zero, {@code ''}, not int's.</li>
         * </ul>
         * A scaled instruction is no term of a rule ({@link #rewrite}), so a run holding one, or a scaled op, stays.
         */
        private static boolean isAbsorbed(final Code scanned, final int start, final int length) {
            final List<Type> carriers = carriers(scanned);
            final int last = start + length - 1;
            final Inst seed = contracts(scanned).getFirst();
            if ((0 == start && null != seed && seed.isInitial()) || !declares(scanned.insts().get(last), carriers.get(last), Law.absorbing))
                return false;
            final fURI op = declared(scanned.insts().get(last).tid().basePath(), carriers.get(last)).atDirect(OBJ).tid();
            final Long least = op.dom().c().min();
            return IntStream.range(start, last).allMatch(i -> {
                final Rec prior = declared(scanned.insts().get(i).tid().basePath(), carriers.get(i));
                if (!declares(prior, Law.pure))
                    return false;
                if (op.rng().c().isZero())
                    return true;
                final fURI tid = prior.atDirect(OBJ).tid();
                final fURI dom = tid.dom().basePath();
                if (!dom.equals(tid.rng().basePath()) || !(dom.isGeneric() || (null != carriers.get(i).vid() && dom.equals(carriers.get(i).vid().basePath()))))
                    return false;
                final Inst.Form form = Inst.Form.of(scanned.insts().get(i).tid(tid));
                return Inst.Form.mapper == form || (Inst.Form.filter == form && (null == least || 0L >= least));
            });
        }

        /**
         * THE UNIT LAW, /m's {@code theory_unit_removal}: an operation applied to its own theory identity drops out, and a
         * bare identity operation is the identity morphism — monoid_theory's rule as every monoid instance binds it
         * ({@link #rules}), so the one rule covers every declared monoid.
         * <p>
         * THE IDENTITY IS A VALUE, SO THE OPERAND IS COMPARED, one rule at a time: a match is a type test, under which a
         * computed operand passes by its range ({@code plus(map(3).neg())} would pass as {@code plus(0)}, and
         * {@code derivation_contraction} would never see it), so the run's operands must equal the unit's. Only
         * composition's identity is one: the additive group's {@code id} is end, composition's zero, and taking it for the
         * identity turned {@code 5.end()} into {@code 5}. A lone instruction is a selector (a payload), not a morphism to
         * collapse, so the bare identity stays alone; a sole unit such as {@code [plus(0)]} still drops.
         */
        public static Code unitRemoval(final Code code) {
            Code out = code;
            for (final Rec unit : rules(MONOID_THEORY_TID)) {
                final Inst term = unit.elements().findFirst().orElseThrow().first().asCall().insts().getFirst();
                out = rewrite(out, List.of(unit), (scanned, start, length) -> term.args().equals(scanned.insts().get(start).args())
                        && (!term.args().isEmpty() || 1 < scanned.insts().size()));
            }
            return out;
        }

        /**
         * THE INVERSE LAW, /m's {@code inverse_cancellation}: an adjacent declared inverse pair with equal operands cancels
         * to the identity — {@code f·f⁻¹ ↦ ε} and {@code f⁻¹·f ↦ ε}, group_theory's two rules as every group instance binds
         * them ({@link #rules}), cancelled only where the second instruction is the inverse the first declares at the
         * chain's carrier, in that direction. A declared inverse is one-sided: {@code inverse(f) = g} claims
         * {@code x.f().g() = x} (f a section, g its retraction), never {@code x.g().f() = x} — on int, {@code x.mult(2).div(2)}
         * is {@code x} but {@code 5.div(2).mult(2)} is 4. Endpoints are unavailable before resolution, so the operand type
         * that selected the declaration is the type guard. An operand that is its type's declared {@code zero} never
         * cancels: the zero annihilates under {@code mult} ({@code mult(0)} has no inverse), and under {@code plus} it is
         * the unit law's anyway. An involution's pair is the involution law's ({@code law_involution}). Its terms are
         * unscaled as every local equation's are ({@link #rewrite}).
         */
        public static Code inverseCancellation(final Code code) {
            return rewrite(code, rules(GROUP_THEORY_TID), (scanned, start, length) -> {
                final Inst a = scanned.insts().get(start);
                final Inst b = scanned.insts().get(start + 1);
                final fURI inverse = TheoryHelper.roleAddress(declared(a.tid().basePath(), carriers(scanned).get(start)), INVERSE);
                return null != inverse && b.tid().basePath().equals(inverse.basePath())
                        && a.args().equals(b.args())
                        && a.args().elements().noneMatch(arg -> TheoryHelper.instances(arg.type()).jvm().values().stream()
                        .anyMatch(block -> block.isRec() && arg.equals(block.asRec().at(ZERO))));
            });
        }

        /**
         * DERIVATION CONTRACTION, /m's {@code derivation_contraction}: a derived instruction's primitive composition folds
         * back to the derived operation — {@code plus·neg ↦ minus}, {@code mult·inv ↦ div}, from the instruction's declared
         * {@code derivation} ({@link #derivations}). Each equation runs backward as an {@code update()} rule
         * ({@link Call.Helper#update}): every contiguous run of the chain that tests the rhs becomes the lhs, its holes
         * reading what the rhs's holes matched. The rhs is matched as a whole WINDOW: a one-instruction rhs nests its
         * composition in that instruction's args ({@code plus(map(#::T@b).neg())}), a longer one spans several, and
         * matching only the rhs's last instruction would contract every {@code sum} to {@code count}.
         * <p>
         * AN EQUATION SPEAKS WHERE ITS CONTRACT RUNS (carrier selection, Marko 2026-11-08): a run is contracted only where
         * the carrier flowing into it dispatches the derived op to the contract that declares the equation
         * ({@link #selects}). Real's {@code div ≡ mult·inv} once contracted an int's {@code 7.mult(2.inv())} to a truncating
         * {@code div}, and int's {@code minus ≡ plus·neg} a lst's {@code plus} into the lst {@code minus} that removes
         * slots: two chains that fail as written (int has no {@code inv}, lst no {@code neg}) answered another op.
         */
        public static Code derivationContraction(final Code code) {
            Code out = code;
            for (final Map.Entry<fURI, Rec> d : derivations().entrySet())
                out = rewrite(out, List.of(rec(d.getValue().atDirect(uri(RHS)).asCode(), d.getValue().atDirect(uri(LHS)).asCode())),
                        (scanned, start, length) -> selects(carriers(scanned).get(start), d.getKey()));
            return out;
        }

        /**
         * THE ADDITIVE LIKE-TERM LAW, /m's {@code branch_collapse}: equal members of any occurrence of the code ring's
         * additive op collapse to the member that first appeared, carrying the summed multiplicities,
         * {@code S(x,x,…,x) ↦ S(x{n})}. The op is read off the code ring's declared theory at rewrite time (branch), so the
         * body knows nothing of branch, and a code ring that declares no additive op is the identity. The op is matched
         * by its base path: it is registered generic, so a space read's admitted() would drop it, and the bare address is
         * unique to the code ring.
         * <p>
         * A REC ARGUMENT LIST IS A ROUTE TABLE: its members are SCHEDULED (each runs at its key's place), not free additive
         * terms. Equal lanes under ONE key are already summed by the rec itself ({@code a => x, a => x} is {@code a => x{2}}),
         * and lanes under different keys run at different places, so they never collapse. A member's multiplicity is the
         * point of its output ({@link #multiplicity}), and the summed point is written back there, its span untouched.
         */
        public static Code branchCollapse(final Code code) {
            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
            if (null == op)
                return code;
            boolean changed = false;
            final List<Inst> out = new ArrayList<>(code.insts().size());
            for (final Inst inst : code.insts()) {
                if (!op.basePath().equals(inst.tid().basePath()) || inst.args().isRec() || 2 > inst.args().count()) {
                    out.add(inst);
                    continue;
                }
                final List<Obj> reps = new ArrayList<>();
                final List<cInt> sums = new ArrayList<>();
                for (final Obj arg : inst.args().elements().toList()) {
                    int slot = -1;
                    for (int j = 0; j < reps.size(); j++) {
                        if (sameMember(reps.get(j), arg)) {
                            slot = j;
                            break;
                        }
                    }
                    if (-1 == slot) {
                        reps.add(arg);
                        sums.add(multiplicity(arg));
                    } else
                        sums.set(slot, sums.get(slot).plus(multiplicity(arg)));
                }
                if (reps.size() < inst.args().count()) {
                    changed = true;
                    final List<Obj> merged = new ArrayList<>(reps.size());
                    // a representative is kept as written only when it already carries the sum: the first member may be
                    // zero-pointed (branch(id{0}(),id()) sums to 1 on id{0}()), so a sum of 1 is no proof of an unpointed rep
                    for (int i = 0; i < reps.size(); i++) {
                        final Obj rep = reps.get(i);
                        final cInt sum = sums.get(i);
                        if (multiplicity(rep).equals(sum))
                            merged.add(rep);
                        else if (rep.isCode() && !rep.asCode().insts().isEmpty()) {
                            final List<Inst> insts = new ArrayList<>(rep.asCode().insts());
                            insts.set(insts.size() - 1, insts.getLast().d(sum).asInst());
                            merged.add(code(insts));
                        } else
                            merged.add(rep.isInst() ? rep.d(sum) : rep.c(sum));
                    }
                    out.add(inst.args(lst(merged)));
                } else
                    out.add(inst);
            }
            return changed ? code(out) : code;
        }

        /**
         * THE ONE-TERM SUM LAW, /m's {@code branch_inline}: an occurrence of the code ring's additive op whose members have
         * come to one is that member, added to nothing, {@code S(x) ↦ x}. The frame unwraps and its own coefficient folds
         * into the member, {@code branch{c}(b) ↦ b{c}}: a code member takes it on its last inst, where its multiplicity is
         * read, and any other member takes the multiplication itself. A call member splices in by its insts, a non-call
         * member lifts to the map that emits it, stream-transparent either way.
         * <p>
         * A REMOTE LANE IS NOT A ONE-TERM SUM: inlining a lane keyed to another host would drop the place and run the
         * program here. A lone addressless lane runs here anyway, so it inlines as a positional one does.
         */
        public static Code branchInline(final Code code) {
            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
            if (null == op)
                return code;
            boolean changed = false;
            final List<Inst> out = new ArrayList<>(code.insts().size());
            for (final Inst inst : code.insts()) {
                if (op.basePath().equals(inst.tid().basePath()) && 1 == inst.args().count() && !Inst.Helper.hasRemoteLane(inst)) {
                    final Obj lone = inst.args().isRec() ? inst.args().asRec().recValue().values().iterator().next() : inst.arg(0);
                    final cInt c = inst.c();
                    final Obj member;
                    if (cInt.ONE().equals(c))
                        member = lone;
                    else if (lone.isCode()) {
                        final List<Inst> insts = new ArrayList<>(lone.asCode().insts());
                        insts.set(insts.size() - 1, (Inst) insts.getLast().c(o -> o.mult(c)));
                        member = code(insts);
                    } else
                        member = lone.c(o -> o.mult(c));
                    changed = true;
                    if (member.isCall())
                        out.addAll(member.asCall().insts());
                    else
                        out.add((Inst) map_(member).tryToInst());
                } else
                    out.add(inst);
            }
            return changed ? code(out) : code;
        }

        /**
         * THE LEFT-DISTRIBUTIVE NORMAL FORM, /m's {@code branch_factor}: an occurrence of the code ring's additive op whose
         * members all start with the same pure instruction shares it ahead of the op, {@code branch(a.b, a.c) ↦
         * a.branch(b, c)}, the ring's a·b + a·c = a·(b + c) (Marko, 2026-11-10: two parallel expressions run as one,
         * branched where their instructions differ). It goes as far as the like-term normal form and no further: one shared
         * instruction a pass (the fixpoint reaches a longer prefix); every member alike in its head, span and point
         * included, so no point moves; every member keeping a remainder, so no unit is introduced; and the head pure, as it
         * then runs once instead of once per member. A branch feeds its members one obj at a time, so the head must take
         * one obj at a time too (a mapper, filter or flatmapper): a shared reducer would gather the whole stream ahead of
         * the branch, and {@code {1,2}.branch(count().plus(1),count().plus(2))} would count 2 where each member counts 1.
         */
        public static Code branchFactor(final Code code) {
            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
            if (null == op)
                return code;
            final Predicate<Obj> factorable = m -> m.isCode() && m.c().isOne() && 1 < m.asCode().insts().size();
            // the rewriter runs before resolution, so a head's form and purity are read for the carrier flowing into its branch
            final List<Type> carriers = carriers(code);
            boolean changed = false;
            final List<Inst> out = new ArrayList<>(code.insts().size() + 1);
            for (int i = 0; i < code.insts().size(); i++) {
                final Inst inst = code.insts().get(i);
                final List<Obj> members = op.basePath().equals(inst.tid().basePath()) && !inst.args().isRec() ? inst.args().elements().toList() : List.of();
                final Inst head = 2 > members.size() || !factorable.test(members.getFirst()) ? null : members.getFirst().asCode().insts().getFirst();
                final Rec declared = null == head ? noobjRec() : declared(head.tid().basePath(), carriers.get(i));
                if (declared.isNoObj()
                        || !EnumSet.of(Inst.Form.mapper, Inst.Form.filter, Inst.Form.flatmapper).contains(Inst.Form.of(head.tid(declared.atDirect(OBJ).tid())).subring())
                        || !declares(head, carriers.get(i), Law.pure) || !members.stream().allMatch(m -> factorable.test(m)
                        && sameMember(m.asCode().insts().getFirst(), head) && m.asCode().insts().getFirst().tid().d().equals(head.tid().d()))) {
                    out.add(inst);
                    continue;
                }
                changed = true;
                out.add(head);
                out.add(inst.args(lst(members.stream()
                        .map(m -> (Obj) code(new ArrayList<>(m.asCode().insts().subList(1, m.asCode().insts().size()))).tryToInst())
                        .toList())));
            }
            return changed ? code(out) : code;
        }

        /**
         * Two additive members being the same element — {@link Inst.Helper#same} with the refined endpoints compared too,
         * minus the positional vid. A code member is the same when its instruction list is pairwise the same, the points
         * included up to the last inst: only the last inst's point is the member's multiplicity, as a point ahead of a
         * reducer is what the reducer reads ({@code 5.plus(1).count()} is 1 and {@code 5.plus{2}(1).count()} is 2). A
         * non-call member is the same when it is coefficient-less equal (the coefficient is the multiplicity the law sums,
         * so it is deliberately OUT of the test).
         */
        private static boolean sameMember(final Obj a, final Obj b) {
            if (a.isInst() && b.isInst()) {
                final Inst ai = a.asInst();
                final Inst bi = b.asInst();
                return Inst.Helper.same(ai, bi)
                        && ai.dom().equals(bi.dom())
                        && ai.rng().equals(bi.rng())
                        && Objects.equals(ai.f(), bi.f());
            }
            if (a.isCode() && b.isCode()) {
                final List<Inst> ai = a.asCode().insts();
                final List<Inst> bi = b.asCode().insts();
                if (ai.size() != bi.size())
                    return false;
                for (int i = 0; i < ai.size(); i++)
                    if (!sameMember(ai.get(i), bi.get(i)) || (i < ai.size() - 1 && !ai.get(i).tid().d().equals(bi.get(i).tid().d())))
                        return false;
                return true;
            }
            if (a instanceof Inst || b instanceof Inst || a instanceof Code || b instanceof Code)
                return false;
            return a.clessEquals(b);
        }

        /**
         * The multiplicity an additive member contributes — the coefficient the law sums. For an inst member it is the
         * inst's own point; for a code member it is the point of the chain's last inst, the one on the member's output. The
         * head is no anchor: a point there floats past a reducer, so {@code 5.branch(plus(1).count(),plus(1).count())}
         * collapsed to {@code plus{2}(1).count()} and answered 2, not {@code {2}1}. An inst's multiplicity is its POINT d,
         * never its whole coefficient b·d: a span is part of the function (like terms share it), so summing it as a point
         * read {@code {?}plus(1) + plus(1)} as {@code plus{1,2}(1)}.
         */
        private static cInt multiplicity(final Obj member) {
            if (member.isCode() && !member.asCode().insts().isEmpty())
                return member.asCode().insts().getLast().tid().d();
            return member.isInst() ? member.asInst().tid().d() : member.c();
        }

        /**
         * A THEORY'S RULES, BOUND — the rules the theory states in its own type ({@link TheoryHelper#stated}), filled in by
         * every instance of it a carrier declares ({@link #bind}). monoid_theory's {@code op(id) ⇝ ε} gives {@code plus(0)},
         * {@code mult(1)}, {@code plus("")}, …, and code's {@code id()}, since composition is adjacency; group_theory's
         * {@code op(a)·op(inv(a)) ⇝ ε} and {@code op(inv(a))·op(a) ⇝ ε} give int's {@code plus·minus} and {@code minus·plus},
         * and real's {@code mult·div} and {@code div·mult}. The rules are op-level, so a rewrite gates each at the chain's
         * carrier: a unit compares its operand, which a match only type-tests, and an inverse pair is the inverse the
         * carrier derives. Read off the carriers of the registered instructions ({@link #claims}).
         */
        public static List<Rec> rules(final fURI theory) {
            return claims().rules().getOrDefault(theory.basePath(), List.of());
        }

        /**
         * WHERE A CONTRACT'S CLAIM SPEAKS FOR A CHAIN — whether dispatch of {@code contract}'s op on {@code carrier} runs
         * that very contract ({@link #deduce}). A claim about an op the chain does not hold yet, a derivation's lhs,
         * holds only there: real's {@code div ≡ mult·inv} contracts a real's {@code mult(map(2.0).inv())}, never an
         * int's, whose truncating div declares no such equation. A contract whose dom ranges over every carrier speaks
         * at each; an unknown carrier ({@code #}) selects no other.
         */
        public static boolean selects(final Type carrier, final fURI contract) {
            if (isUniversal(contract.dom()))
                return true;
            final Inst selected = deduce(contract.basePath(), carrier);
            return null != selected && contract.equals(Inst.Helper.operationTid(selected.tid()));
        }
    }
}
