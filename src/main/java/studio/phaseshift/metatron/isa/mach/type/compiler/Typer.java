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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Typer — the final stage of {@code compiler::T}: the runtime type-assertion pass over
 * {@code code::T}. A typer is a machine component ({@code typer::T}) whose rec flags
 * ({@code inst_dom}, {@code inst_rng}, {@code type_pred}) select which assertions run per machine,
 * replacing the global {@code TypeCheck} registry.
 *
 * <p>{@link Helper} holds the stage's <b>deduction rule</b> — which instruction <em>implementation</em> a type
 * lands on. That rule is the contract between a family read and the resolver: what
 * {@link Helper#admitted} admits, the resolver may use, and vice versa.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Typer extends Machine.Component {

    /**
     * Type-check {@code code} according to this typer's enabled assertions.
     *
     * @param code the code to type-check
     * @return the type-checked code
     */
    Code apply(Obj code);

    /**
     * the deduction rule: for a type, which instruction implementation it reaches, given the instruction sets
     * mounted on the machine.
     */
    public static final class Helper {

        private Helper() {
        }

        /**
         * THE INSTRUCTION A TYPE LANDS ON — the most specific registered contract of {@code genericInst}'s family
         * whose {@code dom} lies on {@code type}'s single path to the root ({@code nat -> int -> #}), ranked
         * closest-to-the-token first. {@code nat::T}'s {@code plus} is the {@code int}-dom contract; an
         * {@code imperial} {@code plus} is its own, {@code real}'s is next, and its sibling {@code metric}'s is
         * never admitted. So a type with no implementation of its own continues up its path until it lands on a
         * base type's — which is what makes {@code ?dom=nat} resolve at all.
         * <p>
         * The returned instruction is the registered contract itself: its {@code tid} names the endpoints it was
         * registered over (the implementation's, not the query's) and its function is the implementation that
         * runs. A caller keying on the address reads {@code returned.tid()}; one re-stamping a chain instruction
         * writes {@code genericInst.tid(returned.tid())}.
         * <p>
         * {@code null} when nothing is admitted — never a guess: a family read and a resolver must both decline,
         * so that no law-driven rewrite fires on the wrong type. {@code type} decides the dom axis; a dom/rng
         * already on {@code genericInst} decides the rng axis (an exact rng match ranks first).
         *
         * @param instSet     the instruction set to search — the machine's union ({@link Machine#instset()}) in
         *                    production, where one family may be registered across several sets
         * @param type        the type being typed (the operand, e.g. {@code nat::T})
         * @param genericInst the underspecified instruction ({@code |plus}); its family name and any rng it
         *                    already carries are the query
         * @return the most specific admitted contract, or null when none is admitted
         */
        public static Inst deduceTypeInst(final InstSet instSet, final Type type, final Inst genericInst) {
            return null == genericInst ? null : deduceTypeInst(instSet, type, genericInst.tid());
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} from an op address alone — what a law table has before
         * resolution, when it knows the family ({@code /m/inst/plus}) and the operand but holds no instruction.
         */
        public static Inst deduceTypeInst(final InstSet instSet, final Type type, final fURI op) {
            if (null == instSet || null == op || null == op.basePath())
                return null;
            final Type dom = null != type ? type : (op.hasDom() ? T(op.dom()) : null);
            if (null == dom)                              // nothing to deduce from: fail closed
                return null;
            final Type rng = op.hasRng() ? T(op.rng()) : null;
            return admitted(instSet.insts().stream(), op, dom, rng).findFirst().orElse(null);
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} over the current machine's instset — the union of every
         * mounted set, which is where a family's contracts actually live together.
         */
        public static Inst deduceTypeInst(final Type type, final Inst genericInst) {
            return null == genericInst ? null :
                    deduceTypeInst(Machine.current().instset(), type, genericInst.tid());
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} over the current machine's instset, from an op address.
         */
        public static Inst deduceTypeInst(final Type type, final fURI op) {
            return deduceTypeInst(Machine.current().instset(), type, op);
        }

        /**
         * THE ADMISSION RULE — the contracts of one family that may legally run for {@code queriedDom}, ranked.
         * <ul>
         *   <li><b>dom</b> is a PATH relation, not a name match: a contract's dom is admitted iff it lies on
         *       {@code queriedDom}'s single path to the root ({@link Type#pathIncludes}), so {@code nat} admits
         *       the {@code int} contract and {@code imperial} admits {@code real}'s — while siblings never admit
         *       each other ({@code metric} is off {@code imperial}'s path);</li>
         *   <li><b>rng</b> is admitted by the same rule, and a root or generic rng ({@code #}, {@code A}) admits
         *       nothing specific — it is the weakest claim, not a match for everything;</li>
         *   <li><b>ranking</b>: an exact rng match first (that is what {@code ?rng=} asks for), then
         *       closest-to-the-token on the dom axis ({@link Type#pathTo}), then on the rng axis. Deterministic
         *       on purpose — a family read, a resolver and the law table must agree on the winner.</li>
         * </ul>
         * A null {@code queriedDom} admits the whole family, unranked (a bare family read).
         *
         * @param insts      the candidate instructions — a set's whole table, as the stream a family is cut from
         * @param pattern    the query instruction, read for its base path and any rng it carries
         * @param queriedDom the type being typed, or null for an unranked family read
         * @param queriedRng the type the result must land in, or null when the rng is unconstrained
         * @return the admitted contracts, ranked when a dom is queried
         */
        public static Stream<Inst> admitted(final Stream<Inst> insts, final fURI pattern, final Type queriedDom, final Type queriedRng) {
            Objects.requireNonNull(insts, "insts");
            Objects.requireNonNull(pattern, "pattern");
            final Stream<Inst> candidates = family(insts, pattern)
                    .filter(i -> null == queriedDom || queriedDom.pathIncludes(i.dom()))
                    .filter(i -> null == queriedRng ?
                            (!pattern.hasRng() || i.rng().vidOrTid().test(pattern.rng())) :
                            (queriedRng.pathIncludes(i.rng()) && !i.rng().isRootType() && !i.rng().isGeneric()));
            return null == queriedDom ?
                    candidates :
                    candidates.sorted(Comparator
                            .comparingInt((Inst i) -> pattern.hasRng() && i.rng().vidOrTid().test(pattern.rng()) ? 0 : 1)
                            .thenComparingInt(i -> queriedDom.pathTo(i.dom()))
                            .thenComparingInt(i -> null == queriedRng ? 0 : queriedRng.pathTo(i.rng())));
        }

        /**
         * The instructions of one family — every registration sharing the family name of {@code pattern}'s base
         * path (bidirectional names included, {@link fURI#bimatches}). An instset's table is keyed by exactly this
         * name, so a set may answer from its own index instead of scanning; both forms are the same family.
         */
        public static Stream<Inst> family(final Stream<Inst> insts, final fURI pattern) {
            return insts
                    .filter(i -> null != i && null != i.tid() && null != i.tid().basePath())
                    .filter(i -> i.tid().basePath().bimatches(pattern.basePath().asNode()));
        }

        // ======================== delegation decisions ========================

        /**
         * THE CANDIDATE SET of a delegation — the wrapper itself plus the values of the fields its type
         * declaration exposes; this is what an instruction fans out over.
         * <pre>
         * person[ninja]::[…].xyz()   ==   { person::[…], ninja::[…] }.xyz()
         * </pre>
         * A wrapper is a {@code rec} whose type INSTANTIATES a declaration whose tid poly names the
         * exposed fields ({@code rec[role]::T[?[…,role=>rec::T]]@person}), and an instantiation
         * ({@code person[ninja]}) names their types. The candidate set is the wrapper PLUS every declared
         * slot that is PRESENT and holds a {@code rec} — one level deep, so a candidate's own slots never
         * join and the walk never recurses. Every other value (a mono, a plain rec with no such
         * declaration, a slot holding a {@code str}) has nothing to fan out over and yields the wrapper
         * alone.
         * <p>
         * A {@code Inst.Form.typer} instruction retypes its subject, so it is OPAQUE: it applies to the
         * wrapper alone and never distributes over the components.
         *
         * @param lhs  the value the instruction applies to
         * @param inst the instruction about to be applied
         * @return the wrapper first, then the exposed component values; a one-element list when there is
         *         nothing to fan out over
         */
        public static List<Obj> candidates(final Obj lhs, final Inst inst) {
            if (Inst.Form.of(inst) == Inst.Form.typer)
                return List.of(lhs);
            final List<Obj> parts = delegationParts(lhs);
            if (parts.isEmpty())
                return List.of(lhs);
            final List<Obj> candidates = new ArrayList<>(parts.size() + 1);
            candidates.add(lhs);
            candidates.addAll(parts);
            return candidates;
        }

        /**
         * The components a wrapper exposes, as declared by its type's tid poly and carried by the value.
         * Empty unless the value is a {@code rec} whose type is an INSTANTIATION (its tid carries the
         * arguments) of a DECLARATION whose tid poly names the exposed fields — and only a slot that is
         * present AND holds a {@code rec} is a part. Every other value (a mono, a plain rec with no such
         * declaration, a slot holding a {@code str}) has nothing to delegate to and takes the ordinary
         * single-application path.
         */
        private static List<Obj> delegationParts(final Obj lhs) {
            if (!lhs.isRec())
                return List.of();
            final fURI tid = lhs.tid();
            // the value's OWN tid carries the type arguments (person[ninja]), the declaration's carries the fields
            if (null == tid || !tid.hasPoly())
                return List.of();
            final Type type = lhs.type();
            final fURI declared = null == type ? null : type.tid();
            if (null == declared || !declared.hasPoly())
                return List.of();
            // AN INSTANTIATION, NOT A SLOT DECLARATION. {@code person[ninja]} instantiates a declaration whose own
            // tid is {@code rec[role]}, so the value's type family (person) differs from its base (rec). A plain
            // {@code rec[k1,k2]} literal carries the same base, and its bracket is a slot list, not a set of
            // traits to delegate to -- it is not a wrapper.
            if (null == type.vid() || type.vid().basePath().equals(declared.basePath()))
                return List.of();
            final Rec rec = lhs.asRec();
            final List<Obj> parts = new ArrayList<>(declared.poly().size());
            for (final String slot : declared.poly()) {
                final Obj part = rec.at(f(slot.trim()));
                if (part.isRec())
                    parts.add(part);
            }
            return parts;
        }

        /**
         * IS THE RESOLVED INSTRUCTION ABOUT THIS CANDIDATE? The fan-out is not "apply it to everything": each
         * candidate is offered the instruction, and the type system says whether it is that candidate's.
         * <ol>
         *   <li>The instruction did not resolve to a callable instruction (noobj, or an unresolved fallback with
         *       no function) — the candidate simply does not receive it. This is the resolution miss that
         *       replaced the old name-level selection: nothing has to know which components implement a trait,
         *       the resolver already refuses the ones that do not.</li>
         *   <li>A {@code typer} retypes its subject, so it is opaque and never distributed.</li>
         *   <li>A GENERIC/PATTERN domain ("any obj") is about every candidate — the multiplicity reads.</li>
         *   <li>A BASE-typed domain ({@code rec}, {@code str}, ...) is about the wrapper AS that form: its
         *       components are not a different subject for it. This is what keeps a type predicate, a rec-typed
         *       instruction, or {@code count()} from summing over the parts.</li>
         *   <li>Any other domain NAMES a type, and it reaches the candidate whose own type identity is that name:
         *       the field's value for a typed call ({@code fight?<=ninja()}), the wrapper for its own type
         *       ({@code ponder?<=person()}). The wrapper only BORROWS a component's type by covariance, which is
         *       TYPE ACCEPTANCE (passing a wrapper where a trait is expected, {@link Type.Helper#recPolySupertype}),
         *       NOT dispatch identity — and dispatch is the identity question, so that rule is not consulted here.</li>
         * </ol>
         * WHEN THE CALLER NAMES A DOMAIN ({@code ?<=ninja()}), that name is the subject, not the overload the
         * resolver happened to bind: {@code describe?<=duo_position()} addresses positions even though
         * {@code describe} also has a {@code duo_ninja} body. An untyped call has nothing but the resolved
         * instruction's own domain to go on.
         *
         * @param wrapper  whether {@code candidate} is the wrapper itself (not one of its components)
         * @param candidate the value the instruction was resolved against
         * @param cinst    the instruction resolved against {@code candidate}
         * @param original the instruction before resolution (its named domain, when any, is the subject)
         * @return true when the resolved instruction is about this candidate, so it must be invoked
         */
        public static boolean applicable(final boolean wrapper, final Obj candidate, final Inst cinst, final Inst original) {
            if (cinst.isNoObj())
                return false;
            // an as() inst is completed by the runtime when it carries no function: every other instance of the
            // no-function fallback is a resolution that could not determine a body
            if (!cinst.hasf() && !cinst.tid().basePath().equals(AS_INST_TID))
                return false;
            if (cinst.form() == Inst.Form.typer)
                return wrapper;
            final fURI requestedID = original.hasDom() ? Obj.Helper.specificTypeId(original.dom()) : null;
            final Type dom = null != requestedID && !requestedID.isGeneric() && !requestedID.hasPattern()
                    ? original.dom() : cinst.dom();
            final fURI domID = Obj.Helper.specificTypeId(dom);
            if (domID.isGeneric() || domID.hasPattern())
                return true;
            if (dom.isBaseType())
                return wrapper;
            return candidate.testByID(dom);
        }
    }
}
