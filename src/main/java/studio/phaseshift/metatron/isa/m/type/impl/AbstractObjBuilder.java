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

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.RHS;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The shared head of the two builders that create metatron objects from Java — {@link InstBuilder} for
 * instructions and {@code TypeBuilder} for types.
 *
 * <p>ONE FUNNEL, TWO SHAPES. An inst and a type are created the same way in this codebase: a builder states the
 * object's identity, its documentation and the claims declared beside it, and {@code create()} hands the finished
 * value — plus the documentation and algebra that belong beside it — to the machine as WRITES. An InstSet indexes
 * a write that lands under its pattern exactly as it indexes one made from mtron, so a Java-built instruction and
 * an mtron-defined one take the same road.
 *
 * <p>WHAT IS SHARED. Every object has a {@code tid} (the type it refines), a {@code vid} (the name it answers to),
 * a one-line {@code desc} and any {@code examples} its documentation shows, and may declare {@code law}s, an
 * {@code inverse} and a {@code derivation}. Those live here. What differs by kind stays in the subclass: an
 * instruction adds its {@code dom}/{@code rng}/{@code args} contract, a body and a form; a type adds its
 * predicate, constructor, declared instructions and algebra.
 *
 * <p>DOCUMENTATION IS INLINE. The doc string is the second argument of the method it documents — {@code
 * tid(tid, "…")}, {@code dom(dom, "…")}, {@code vid(vid, "…")} — because a doc kept in a separate call is a doc
 * that drifts from the declaration it describes and is forgotten on the way in. There is no {@code doc(...)} step
 * and no {@code docWrap} behind the scenes: the builder composes the documentation rec and {@code create()}
 * writes it at {@code <address>?docq}.
 *
 * <p>THE BUILDERS THEMSELVES WRITE NOTHING. A builder may run during an instruction set's static initialization,
 * before a machine exists to write to, so {@code create()} composes the value and queues the writes; the instset
 * that owns the address publishes them when its space is mounted. That is also why the doc strings and the claims
 * are HELD here rather than applied at chain time — there is nothing to apply them to yet.
 *
 * @param <B> the concrete builder, so a chain keeps its subclass's type
 * @param <O> the object the builder creates
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractObjBuilder<B extends AbstractObjBuilder<B, O>, O extends Obj> {

    /**
     * The type the object refines — for an instruction, its signature's base address; for a type, the type it
     * specializes.
     */
    protected fURI tid;

    /**
     * The name the object answers to — the address it is written at, and therefore the identity a reader uses.
     */
    protected fURI vid;

    /**
     * The object's one-line description, the {@code desc} slot of its {@code ?docq} rec.
     */
    protected String description;

    /**
     * The worked examples the documentation shows, the {@code example} slot of its {@code ?docq} rec.
     */
    protected final List<String> examples = new ArrayList<>();

    /**
     * The process laws the object declares it obeys — the claim half of a declaration.
     */
    protected final List<Law> laws = new ArrayList<>();

    /**
     * The opposing edge the object declares, or null when none is declared (an inverse may be derived later).
     */
    protected fURI inverse;

    /**
     * The defining equation the object declares, the {@code [lhs=>code, rhs=>code]} rec, or null when it is primitive.
     */
    protected Rec derivation;

    /**
     * The concrete builder — the cast a fluent chain needs so {@code tid(…).dom(…)} keeps the subclass's type.
     */
    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * The type this object refines — the {@code tid} the builder was built with, or null while it is unset (a
     * builder whose tid is never set is refused by {@code create()}).
     */
    public fURI tid() {
        return this.tid;
    }

    /**
     * State the type the object refines, and — because a declaration states its own documentation — the one-line
     * description that lands in the object's {@code ?docq} rec.
     */
    public B tid(final fURI tid) {
        this.tid = tid;
        return this.self();
    }

    /**
     * {@link #tid(fURI)} with the object's description inline: the doc string is the second argument of the
     * declaration it documents.
     */
    public B tid(final fURI tid, final String desc) {
        this.tid = tid;
        this.description = desc;
        return this.self();
    }

    /**
     * The object's name — the address it is written at.
     */
    public fURI vid() {
        return this.vid;
    }

    /**
     * State the object's name; the address it will be written at and read from.
     */
    public B vid(final fURI vid) {
        this.vid = vid;
        return this.self();
    }

    /**
     * {@link #vid(fURI)} with the object's description inline.
     */
    public B vid(final fURI vid, final String desc) {
        this.vid = vid;
        this.description = desc;
        return this.self();
    }

    /**
     * State the object's one-line description on its own, for a builder that named its {@code tid}/{@code vid}
     * before it had the words to describe them.
     */
    public B desc(final String desc) {
        this.description = desc;
        return this.self();
    }

    /**
     * Add worked examples to the object's documentation — the {@code example} slot of its {@code ?docq} rec.
     */
    public B example(final String... examples) {
        if (null != examples)
            this.examples.addAll(List.of(examples));
        return this.self();
    }

    /**
     * Declare the process laws the object obeys. Accumulates across calls, so a builder may state them where they
     * are discovered rather than in one long chain.
     */
    public B law(final Law... laws) {
        if (null != laws)
            this.laws.addAll(List.of(laws));
        return this.self();
    }

    /**
     * Declare the opposing edge — the instruction or name this one is the inverse of. The direction is the claim
     * ({@code f·g = ε}, never {@code g·f = ε}), so a one-way retraction declares one and only one.
     */
    public B inverse(final fURI inverse) {
        this.inverse = inverse;
        return this.self();
    }

    /**
     * Declare the DEFINING EQUATION — that this instruction equals a composition of other instructions. It is a claim,
     * not the implementation: the {@code body} still computes the instruction, and the equation tells the rewriter
     * what the instruction equals. {@link InstBuilder#create} stores it as the {@code derivation} facet of the
     * instruction's {@code ?decq} claim ({@code [lhs=>code, rhs=>code]}); a {@link TypeBuilder} stores none.
     *
     * <p>THE EQUATION. Both sides are codes over the same named HOLES, each side typed by its own ends (a code's dom is
     * its head instruction's, its rng its tail instruction's):
     * <ul>
     *   <li>the {@code lhs} is exactly this instruction with a hole for each argument — {@code div(#::T@b)}, a b that is
     *       anything. A hole names a VALUE, so its name is not all caps (an all-caps name is a generic, a type: Marko,
     *       2026-11-08). A call matches as a value tests a type ({@link Obj#test}), and every hole must bind: a code
     *       argument is typed by its range, so {@code mult(2.0)} fits {@code mult(map(#::T@b).inv())}, but binds no
     *       {@code b}, so it is no match. An argument the call omits is noobj, which only a hole admitting zero takes: {@code order(#{?}::T@b)} takes a bare {@code order()},
     *       {@code minus(#::T@b)} refuses a bare {@code minus()}, and a call passing an argument the lhs does not name
     *       matches nothing;</li>
     *   <li>the {@code rhs} is the composition, written over the same holes. A hole may sit inside an argument's code:
     *       in {@code mult(map(#::T@b).inv())}, {@code map(#::T@b)} maps the incoming obj to what {@code b} matched, so {@code mult}
     *       multiplies by that value's inverse.</li>
     * </ul>
     * The equation is an {@code update()} rule either way round
     * ({@link studio.phaseshift.metatron.isa.m.type.Call.Helper#update}): the side matched names what each hole
     * matched, and the side written reads those names back, so a hole bound to an omitted argument is noobj again
     * ({@code kmerge(noobj)}, which reads its absent selector as {@code kmerge()} does).
     *
     * <p>THE READERS. Three facts are read off the equation, and the rhs's shape decides which of them follow:
     * <ul>
     *   <li>CONTRACTION ({@code derivation_contraction}) runs it backward, rewriting every contiguous run of a chain
     *       that matches the rhs to the lhs: {@code mult(map(3).inv()) ↦ div(3)}, {@code map(1).sum() ↦ count()}. It
     *       rewrites EVERY match, so the rhs must be specific: an rhs of a lone {@code sum} would turn every
     *       {@code sum} into this instruction;</li>
     *   <li>THE COMBINER ({@link Law.Helper#combiner}) runs it forward: for a fold that does not declare the combining
     *       laws itself, the rhs's LAST instruction, its holes filled from the call, is the fold its partials
     *       recombine through. That instruction must itself be a fold over this instruction's range that declares
     *       those laws: {@code count ↦ map(1)·sum} recombines through {@code sum},
     *       {@code order(#{?}::T@b) ↦ split([_])·kmerge(#{?}::T@b)} through {@code kmerge}. This is how a sharded
     *       {@code count} or {@code order}, or a {@code group} over them, recombines;</li>
     *   <li>THE INVERSE ({@code Law.Helper.derivedInverse}): an rhs that applies a group's operation to an argument
     *       holding the group's inverse defines the other half of that group. {@code minus(#::T@b) ≡ plus(map(#::T@b).neg())}
     *       makes {@code minus} the inverse of {@code plus}, and {@code plus} the inverse of {@code minus}, over an
     *       operand whose declared algebra has the {@code plus}/{@code neg} group.</li>
     * </ul>
     * A generic's equation covers every carrier that runs its body ({@code count}, {@code order}); a specialization
     * declares its own ({@code minus} declares the same equation for {@code int}, {@code real} and {@code cmplx}).
     * {@code real}'s {@code div} and the generic {@code count}:
     * <pre>{@code
     * .derivation(div_(T(ALL, f("b"), null, null)), mult_(map_(T(ALL, f("b"), null, null)).inv_()))
     * .derivation(count_(), map_(jnt(1)).sum_())
     * }</pre>
     */
    public B derivation(final Code lhs, final Code rhs) {
        this.derivation = rec(uri(LHS), lhs, uri(RHS), rhs);
        return this.self();
    }

    /**
     * Create the object: compose the value from everything the chain stated and QUEUE the writes that publish it
     * (the object itself, its documentation at {@code ?docq}, its declaration at {@code ?decq}). Nothing is written
     * to a space here — a builder may run before one exists — so the object this answers is the value that will
     * land at {@link #vid()} once the owning instruction set drains its pending writes.
     *
     * @return the created object
     */
    public abstract O create();
}
