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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Selector;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.ALL_STAR;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFiberedRec.fiberedRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * fibered rec — a {@code rec::T} of a named FIBERED declaration, realized as a BUNDLE (the outer rec) and the FIBERS
 * its declaration's bracket lifts out of it: {@code rec[role]::T[?[…,role=>rec::T]]@person} names the fiber
 * {@code role}, and the instance {@code person[ninja]} gives it a type. A <i>field</i> is any key→value pair of the
 * rec; a <i>fiber</i> is a field (or a path, {@code rec[geo/0]}) that the bracket names.
 * <p>
 * THE LIFT (Marko, 2026-11-09): an instruction about a fiber's value runs on every fiber it is about — the parallel
 * fibers — and each result is written back at its fiber, so {@code dom => rng} on a fiber is {@code bundle => bundle}
 * and the call answers the bundle, not the result of one of its fibers:
 * <pre>
 * person[int]::[name=>'marko',age=>29].plus(1)   =>   person[int]::[name=>'marko',age=>30]
 * </pre>
 * THE PROJECTION (Marko, 2026-11-09): when the instruction CHANGES a fiber's type (its type or multiplicity), the
 * call is a projection, and it answers the instruction's results rather than the bundle:
 * <pre>
 * person[ninja]::[role=>ninja::[…],name=>'marko'].fight()   =>   the str fight answers (fight: ninja => str)
 * </pre>
 * An instruction about the bundle ITSELF — a typer, a generic domain, the rec form, or the bundle's own name — is the
 * bundle's, applied to it alone as any instruction is applied to any rec, and the fibers are not asked.
 * <p>
 * The {@link Processor} knows none of this: resolution — when code compiles, and at run time
 * ({@link Selector.Helper#resolve}) — hands it the instruction a call refers to, which on a bundle is either the
 * bundle's own or the lift ({@link Helper#resolve}). The lift carries the call unbound, so fibers of different types
 * each resolve their own overload of it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface FiberedRec extends Rec {

    /**
     * @return the fibers this bundle exposes, in declaration order: the keys (or paths) its declaration's bracket names
     */
    List<fURI> fibers();

    /**
     * THE LIFT of {@code inst}: run it on every fiber whose value it is about ({@link Helper#applicable}) and write
     * each result back at its fiber — or, when a result changes its fiber's type, THE PROJECTION: the results.
     *
     * @param inst the call, resolved or not (each fiber resolves it against the object it holds)
     * @return the bundle with its fibers moved; the results, summed, when a fiber's type changes; {@code noobj} when
     * the call is about no fiber; the first uncaught fail
     */
    Obj lift(Inst inst);

    final class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * IS THIS VALUE A BUNDLE? A rec of a named FIBERED declaration ({@link #isBundle(Type)}).
         */
        public static boolean isBundle(final Obj lhs) {
            return lhs.isRec() && isBundle(lhs.type());
        }

        /**
         * IS THIS A BUNDLE'S TYPE? A named FIBERED declaration ({@code rec[role]::T[…]@person}, instantiated as
         * {@code person[ninja]} or not). An anonymous bracket ({@code rec[k1,k2]}) names the fields a literal carries
         * and exposes no fibers.
         */
        public static boolean isBundle(final Type type) {
            return type.isFibered() && null != type.vid() && !type.vid().basePath().equals(type.tid().basePath());
        }

        /**
         * THE INSTRUCTION A CALL ON {@code lhs} REFERS TO, given {@code cinst}, the one it resolved to there: itself,
         * unless {@code lhs} is a bundle that is not the subject of {@code cinst} ({@link #applicable}) — then it is
         * the LIFT of the call ({@link FiberedRec#lift}).
         *
         * @param lhs   the value the call is applied to
         * @param cinst the instruction the call resolved to against {@code lhs} (noobj, or f-less, when none bound)
         * @param inst  the call before resolution
         * @return {@code cinst}, or its lift on a bundle
         */
        public static Inst resolve(final Obj lhs, final Inst cinst, final Inst inst) {
            return !isBundle(lhs) || applicable(true, lhs, cinst, inst) ? cinst : lift(inst);
        }

        /**
         * The same question when code compiles, asked of the TYPE the call's values have: a call a bundle's type binds
         * through one of its fibers compiles to the LIFT of the call, never to the overload bound for that one fiber.
         *
         * @param type  the type the call is resolved against
         * @param cinst the instruction the call resolved to against {@code type}
         * @param inst  the call before resolution
         * @return {@code cinst}, or its lift on a bundle's type
         */
        public static Inst resolve(final Type type, final Inst cinst, final Inst inst) {
            return !isBundle(type) || applicable(true, type, cinst, inst) ? cinst : lift(inst);
        }

        /**
         * The LIFT of a call: it carries the CALL, unbound, so every fiber resolves its own overload of it (the
         * parallel fibers) — an instruction bound for one subject would be offered to fibers it is not about. It takes
         * one obj and answers any (the bundle when it lifts, the results when it projects), and shipped without its
         * function it re-resolves to the same lift.
         */
        private static Inst lift(final Inst inst) {
            return instC(inst.tid().basePath().dom(ALL).rng(ALL_STAR), lst(), (bundle, lifted) -> fiberedRec(bundle.asRec()).lift(inst));
        }

        /**
         * IS THE RESOLVED INSTRUCTION ABOUT THIS SUBJECT — the bundle, or one of its fibers? Each subject is offered
         * the instruction, and the type system says whether it is that subject's.
         * <ol>
         *   <li>The instruction did not resolve to a callable instruction (noobj, or an unresolved fallback with
         *       no function) — the subject simply does not receive it.</li>
         *   <li>A {@code typer} retypes its subject, so it is the bundle's alone and never lifted.</li>
         *   <li>A GENERIC/PATTERN domain ("any obj") is about every subject, the bundle first.</li>
         *   <li>A BASE-typed domain is about every subject whose own FORM it is: a {@code rec} domain reaches the
         *       BUNDLE (and a fiber that is a rec refinement, {@code ninja::T} being a {@code rec::T}); any other base
         *       domain reaches the fibers of that form ({@code ex::[o=>12].plus?<=int} adds at {@code o}, since the
         *       bundle is a rec, not an int). The match is on the base FORM, never on the type's name.</li>
         *   <li>Any other domain NAMES a type, and it reaches the subject whose own type identity is that name:
         *       the fiber's value for a typed call ({@code fight?<=ninja()}), the bundle for its own type
         *       ({@code ponder?<=person()}). The bundle is acceptable where a fiber's type is expected
         *       ({@link Type#recFibers()}), but that is TYPE ACCEPTANCE, NOT dispatch identity.</li>
         *   <li>A named domain that CARRIES PREDICATES names a STRUCTURAL type ({@code weaponed ->
         *       rec::T[?[weapon=>str::T]]}): it is about every FIBER whose OWN value is shaped like it
         *       ({@link #ownsShape}). The BUNDLE answers by identity only: neither its own fields nor the acceptance
         *       it borrows from a fiber is an identity.</li>
         * </ol>
         * WHEN THE CALLER NAMES A DOMAIN ({@code ?<=ninja()}), that name is the subject, not the overload the
         * resolver happened to bind. An untyped call has nothing but the resolved instruction's own domain to go on.
         *
         * @param bundle    whether {@code candidate} is the BUNDLE itself (not one of its fibers)
         * @param candidate the value the instruction was resolved against
         * @param cinst     the instruction resolved against {@code candidate}
         * @param original  the instruction before resolution (its named domain, when any, is the subject)
         * @return true when the resolved instruction is about this subject, so it must be invoked
         */
        public static boolean applicable(final boolean bundle, final Obj candidate, final Inst cinst, final Inst original) {
            if (cinst.isNoObj())
                return false;
            // an as() inst is completed by the runtime when it carries no function: every other instance of the
            // no-function fallback is a resolution that could not determine a body
            if (!cinst.hasf() && !cinst.tid().basePath().equals(AS_INST_TID))
                return false;
            if (cinst.form() == Inst.Form.typer)
                return bundle;
            final Type dom = domain(cinst, original);
            final fURI domID = Obj.Helper.specificTypeId(dom);
            if (domID.isGeneric() || domID.hasPattern())
                return true;
            if (dom.isBaseType())
                return candidate.baseTypeID().basePath().equals(dom.baseTypeID().basePath());
            return candidate.testByID(dom) || (!bundle && ownsShape(candidate, dom));
        }

        /**
         * The DOMAIN the instruction is about: the one the CALLER named ({@code ?<=ninja()}), else the resolved
         * instruction's own.
         */
        private static Type domain(final Inst cinst, final Inst original) {
            final fURI requestedID = original.hasDom() ? Obj.Helper.specificTypeId(original.dom()) : null;
            return null != requestedID && !requestedID.isGeneric() && !requestedID.hasPattern()
                    ? original.dom() : cinst.dom();
        }

        /**
         * IS THIS FIBER'S OWN VALUE SHAPED LIKE THE NAMED TYPE? A named type that carries predicates
         * ({@code weaponed -> rec::T[?[weapon=>str::T]]}) denotes the values that satisfy them, whatever their own
         * name, so it is asked of the value ITSELF. A nested bundle is judged with its declaration set aside — its own
         * fields, none of its fibers — since the acceptance a bundle borrows from a fiber is not its own shape.
         */
        private static boolean ownsShape(final Obj candidate, final Type dom) {
            final List<Call> predicates = dom.predicateStack().stream().filter(pred -> !pred.isNoObj()).toList();
            if (predicates.isEmpty())
                return false;
            final Obj value = isBundle(candidate)
                    ? candidate.clone(candidate.jvm(), REC_TID, candidate.vid())
                    : candidate;
            return predicates.stream().allMatch(pred -> !pred.dom(ALL_TYPE).apply(value).isNothing());
        }
    }
}
