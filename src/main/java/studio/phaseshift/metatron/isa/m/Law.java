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

import studio.phaseshift.metatron.furi.fURI;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The law axis: the process laws a morphism obeys — a property of the operation itself, never the whole
 * algebra (those are the theory types on {@code object::T}).
 *
 * <p>NOTES: the structural laws — semilattice, boolean, near_ring, field, rig, lattice, ring, group, … — are not
 * process laws: they name an algebraic <em>structure</em>, so they are theory types on {@code object::T}
 * ({@code monoid_theory::T}, {@code group_theory::T}, {@code ring_theory::T}, {@code field_theory::T},
 * {@code rig_theory::T}, {@code boolean_theory::T}, {@code lattice_theory::T},
 * {@code semilattice_theory::T}, {@code near_ring_theory::T}). The former provenance axis
 * (syntactic/declared/semantic) is dropped too: an instruction set serves the declared process laws; the
 * rest are derived (syntactic) or computed (semantic) when wired.
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
     * element types in order otherwise ({@code inst?dom=A&rng=A&args=[A]}).
     * <p>
     * THE {@code args} COMPONENT IS ARITY, AND ITS ABSENCE IS A CLAIM: {@code args=[A]} says the operation takes
     * exactly one argument beyond its lhs, while no {@code args} at all says the law is arity-agnostic — an element
     * law, or one an operation may obey at any arity. That is why a law whose declarations span an idempotent
     * {@code zero} and an idempotent {@code or} must be spelled {@code inst?dom=A&rng=A}: the binary spelling would
     * make the nullary claim a category error.
     *
     * @return the fURI signature of the law's subject
     */
    public fURI wrt() {
        return switch (this) {
            // a binary operation on the carrier (A × A → A): the laws that read an OPERAND equation
            case complement, cyclic, left_distributive, magmadic, nilpotent, right_distributive, zero_divisor,
                 action -> BINARY_ENDO;
            // the carrier to itself, the arity stated only by `args`: the ELEMENT laws (an identity, an
            // annihilator, an atomic element) and the process laws whose claims today span arities -- an
            // idempotent nullary zero/one AND an idempotent binary join, a commutative stream reduction AND a
            // commutative binary op, a self-inverse unary op AND the group form of the same law
            case absorbing, commutative, idempotent, involution, monoidic, prime, unit -> ENDO;
            // the comparisons of a partially ordered carrier (A × A → bool)
            case poset -> RELATION;
            // the reducer's distributivity, and the closure it iterates: both are laws of an unbounded iterate
            case semi_right_distributive, kleene -> FOLD;
            // coefficient/form laws: read off the instruction's form, no operation on a carrier to state
            case floatable, blocked, partial -> FORM;
            // the effect axis: there is no carrier
            case pure -> EFFECT;
        };
    }
}
