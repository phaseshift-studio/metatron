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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.INST;
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
     * through the fold (Thm 19), which {@code law_shard} realizes via {@link LawTable#combiner}.
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
}
