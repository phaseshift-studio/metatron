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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Type extends Obj {

    // the type of nothing: every {0} type resolves to it (MType.T), so this is the one typed zero for Type
    static Type type_noobj() {
        return NOOBJ_TYPE;
    }

    Type TYPE_TYPE = T(f("T"));
    GraphittyLogger LOG = Graphitty.log(Type.class);

    @Override
    Type clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Tuple.Pair<Call, Call> jvm();

    @Override
    default Type dom() {
        return this;
    }

    @Override
    default Type rng() {
        return this;
    }

    @Override
    default Obj clone() {
        return this;
    }

    @Override
    default Type type() {
        if (this.isBaseType())
            return this;
        return T(this.tid());
    }

    default String namedType() {
        return (null == this.vid() ? this.tid().name() : this.vid().name()) + "::T";
    }

    default boolean isBaseType() {
        return this.hasVID() && (this.isRootType() || Tokens.BASE_TYPES.contains(this.vid().basePath()));
    }

    default boolean isGeneric() {
        return (null != this.vid() && this.vid().isGeneric()) || this.tid().isGeneric();
    }

    default boolean isPattern() {
        return (null != this.vid() && this.vid().hasPattern()) || this.tid().hasPattern();
    }

    // Base types are excluded because they carry coefficient variance ({1}, {2}, …): `test()` and
    // `testObjs()` use isNominal() as a shortcut to the coefficient-blind testNominally(), and base
    // types must instead take the coefficient-aware full path. (testNominally strips coefficients.)
    // A name over the root is not nominal either: #::T@b is a b that is anything, and nothing is nominally a b.
    default boolean isNominal() {
        return !this.hasPredicate() && !this.hasConstructor() && this.hasVID() && !this.isBaseType() && !this.vid().hasPattern() && !this.vid().isGeneric()
                && !this.tid().basePath().equals(ALL);
    }

    default boolean isStructural() {
        return this.hasPredicate();
    }

    /**
     * Returns true if this type is a structural refinement of other:
     * nominal refinement AND this's predicate stack includes all of other's predicates.
     * More restrictive than {@link #isRefinementOf(Type)} (predicate-blind),
     * less restrictive than {@link #test(Obj)} (exact predicate equality).
     */
    default boolean isStructuralRefinementOf(final Type other) {
        if (!this.isRefinementOf(other))
            return false;
        if (!other.hasPredicate())
            return true;
        final List<Call> thisStack = this.predicateStack();
        final List<Call> otherStack = other.predicateStack();
        for (final Call otherPred : otherStack) {
            boolean found = false;
            for (final Call thisPred : thisStack) {
                if (Objects.equals(thisPred, otherPred)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    default boolean isRefinementOf(final Type other) {
        if (this == other)
            return true;
        if (other.isRootType())
            return this.c().within(other.c());
        // address-only nominal walk: no Type construction (parentType() -> T())
        // final boolean refines = Obj.Helper.isRefinementOfTid(this.vid(), this.tid(), other.vid())
        //       || Obj.Helper.isRefinementOfTid(this.vid(), this.tid(), other.tid());
        //   return refines && this.c().within(other.c());

        /*if (Obj.Helper.isRefinementOfTid(this.vid(), this.tid(), other.vid())
                || Obj.Helper.isRefinementOfTid(this.vid(), this.tid(), other.tid()))
            return this.c().within(other.c());*/
        // fallback: Obj walk (parentType()) for types not in an InstSet, which also
        // reaches the root and so covers a #{n} rhs without a separate root guard.
        final fURI otherVid = other.vid();
        final fURI otherTid = other.tid();
        Type t = this;
        int hops = 0;
        while (null != t && hops < 256) {
            if (Helper.basePathEquals(t.vid(), otherVid) || Helper.basePathEquals(t.vid(), otherTid)
                    || Helper.basePathEquals(t.tid(), otherVid) || Helper.basePathEquals(t.tid(), otherTid))
                return this.c().within(other.c());
            if (t.isRootType())
                break;
            t = t.parentType();
            hops++;
        }
        return false;

    }

    default boolean isEphemeral() {
        return !Tokens.BASE_TYPES.contains(this.tid().basePath()) && this.tid().equals(this.vid());
    }

    /**
     * whether the single refinement path from this type to the root ({@code #::T})
     * includes {@code node} — eg {@code nat.pathIncludes(int)} is true because
     * {@code nat -> int -> #}, while {@code imperial.pathIncludes(metric)} is
     * false: imperial's path is {@code imperial -> real -> #} and never
     * includes the sibling metric, although {@code metric.isRefinementOf(imperial)}
     * is true (both branch off real). this is the admission rule for instruction
     * family reads on the {@code dom} axis: what is on the path is what may be
     * legally used; siblings and cousins are not.
     * <p>
     * root ({@code #}) and generic nodes are on every path, as they bind to
     * anything.
     *
     * @param node the candidate (eg a contract's dom) this type's path may include
     * @return true if node lies on this type's chain to the root
     */
    default boolean pathIncludes(final Type node) {
        if (null == node || node.isGeneric() || node.isRootType())
            return true;
        Type t = this;
        int hops = 0;
        while (null != t && !t.isRootType() && hops < 256) {
            if (null != t.vid() && null != node.vid() && t.vid().basePath().equals(node.vid().basePath()))
                return true;
            if (t.isBaseType())
                break;
            t = t.parentType();
            hops++;
        }
        return false;
    }

    /**
     * distance in refinement hops from this type down to {@code node} on the
     * path to the root, or a large distance if the node is generic/root (least
     * specific). used to rank family read results, closest-to-the-token first.
     */
    default int pathTo(final Type node) {
        if (null == node || node.isGeneric() || node.isRootType())
            return Integer.MAX_VALUE / 2;
        Type t = this;
        int hops = 0;
        while (null != t && !t.isRootType() && hops < 256) {
            if (null != t.vid() && null != node.vid() && t.vid().basePath().equals(node.vid().basePath()))
                return hops;
            if (t.isBaseType())
                break;
            t = t.parentType();
            hops++;
        }
        return Integer.MAX_VALUE;
    }

    default boolean isIsaPredicate() {
        // THE OPERATION KIND IS THE BASE PATH: the predicate inst's tid carries the CONTRACT (dom/rng/args — the type
        // argument it was minted with), none of which changes WHICH operation it is. A full-tid equality here made a
        // predicate like `isa?args=[...]` read as "no isa predicate", and isPredicateObj() then answered null to a
        // caller that assumes a predicate.
        return this.hasPredicate() && this.predicate().isObjCall() && this.predicate().asCall().insts().size() == 1 &&
                this.predicate().asCall().insts().getFirst().tid().basePath().equals(ISA_INST_TID);
    }

    default Obj isPredicateObj() {
        return this.isIsaPredicate() ? this.predicate().asCall().insts().getFirst().arg(0) : null;
    }

    /**
     * Returns the full combined predicate for this type by chaining all
     * levels of the predicate stack via {@link Call#mult(Call)}.
     * Returns null if this type has no predicates at any level.
     */
    default Call combinedPredicate() {
        final List<Call> stack = this.predicateStack();
        if (stack.isEmpty()) return null;
        Call result = stack.get(0);
        for (int i = 1; i < stack.size(); i++) {
            result = result.mult(stack.get(i));
        }
        return result;
    }

    default List<Call> predicateStack() {
        final List<Call> result = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        Type type = this;
        int hops = 0;
        while (!type.isRootType() && hops++ < 256) {
            // the parent chain is not guaranteed acyclic: parentType() resolves a parent by
            // re-entering the type space (T(tid())), and a type whose tid and vid disagree --
            // /m/llm/session_or_agent{?}@/m/llm/session_or_agent, where only the tid carries the
            // pattern piece -- resolves back to itself. Unbounded, this loop re-added the same
            // predicate until the heap died (an ArrayList.add OutOfMemoryError, not a stack one),
            // so a level already visited ends the walk. 256 mirrors the bound on the sibling
            // walks (pathIncludes, isRefinementOf's fallback).
            if (!seen.add(System.identityHashCode(type) + "@" + type.tid() + ":" + type.vid()))
                break;
            if (type.hasPredicate())
                result.add(type.predicate());
            type = type.parentType();
        }
        return result;
    }

    /**
     * The types on this type's chain whose tid carries a bracket, leaf-first: the poly readings a value of this type
     * is held to ({@code cmplx = lst[real,real]@cmplx}), read by {@link Type.Helper#testTypePoly(Obj, Type)}.
     * Distinct from {@link #predicateStack()}: those are the jvm predicates. A tid is shared by a type and its parent
     * (parentType() is T(tid())), so each one is collected once.
     */
    default List<Type> refinesPolys() {
        final List<Type> result = new ArrayList<>();
        final Set<String> seenType = new HashSet<>();
        Type type = this;
        int hops = 0;
        while (!type.isRootType() && hops++ < 256) {
            if (!seenType.add(System.identityHashCode(type) + "@" + type.tid() + ":" + type.vid()))
                break;
            final fURI tid = type.tid();
            if (tid.hasPoly() && result.stream().noneMatch(poly -> poly.tid().equals(tid)))
                result.add(type);
            type = type.parentType();
        }
        return result;
    }

    /**
     * The tid is a rec bracket: the fields a rec carries ({@code rec[k1,k2]}), which a declaration under a name of
     * its own exposes as fibers ({@code rec[role]::T[…]@person}).
     */
    default boolean isFibered() {
        return this.tid().hasPoly() && Tokens.REC_TID.equals(this.tid().basePath());
    }

    /**
     * The tid is a lst bracket: the types at a list's indices ({@code lst[int,str]}).
     */
    default boolean isTensored() {
        return this.tid().hasPoly() && Tokens.LST_TID.equals(this.tid().basePath());
    }

    /**
     * The tid is a rel bracket: the vertices of a path ({@code rel[int,str]} is {@code int => str}).
     */
    default boolean isPathed() {
        return this.tid().hasPoly() && Tokens.REL_TID.equals(this.tid().basePath());
    }

    /**
     * The types an instance's bracket gives its fibers ({@code person[ninja]} gives {@code role} a ninja), at the
     * instance's multiplicity ({@code person[ninja]{*}} holds {@code ninja{*}}). Empty unless the vid instantiates
     * the declaration: an anonymous rec bracket names fields, not types.
     */
    default List<Type> recFibers() {
        return null == this.vid() || this.vid().basePath().equals(this.tid().basePath()) ? List.of() :
                this.vid().polyParsed().map(Obj::lstValue).orElse(List.of()).stream()
                        .map(fiber -> fiber.c(fiber.c().mult(this.c()))).map(Obj::asType).toList();
    }

    /**
     * The types at a tensor's indices ({@code lst[int,str]}); empty for any other bracket. A BRACKET READS ITS TYPES AS
     * VALUES (Marko, 2026-11-18: "in brackets, we have uris ... so they are values. that means {point}value{span}"):
     * {@code {4}int} is an int with mass {4} and {@code int{4}} an int with span {4}, so each entry is answered the way
     * the run walk ({@link Helper#runs}) reads one, {@code {span}type{mass}}: {@code lst[int{4}]} has the entry
     * {@code {4}int::T}, four int indices, and {@code lst[{4}int]} the entry {@code int{4}::T}, one index holding four
     * ints. A path's vertices read the same way ({@link #relPaths()}).
     */
    default List<Type> lstIndices() {
        return this.tid().poly().stream().map(u -> f(u)).map(entry -> T(entry.b(entry.d()).d(entry.b()))).toList();
    }

    /**
     * The types at a path's vertices ({@code rel[int,str]}); empty for any other bracket. Its bracket holds uris as a
     * lst's does, so it is read as one ({@link #lstIndices()}): {@code rel[int,{2}person]} holds two people at its second
     * vertex, and {@code rel[int,person{2}]} is a run of two person vertices, {@code int=>person=>person}.
     */
    default List<Type> relPaths() {
        return this.lstIndices();
    }

    default Type parentType() {
        if (this.isRootType())
            return this;
        // nothing refines to nothing: the noobj type's parent is the root itself (the root with {0} is noobj again)
        if (this.isNoObj())
            return ALL_TYPE;
        if (this.tid().equals(this.vid()))
            return ALL_TYPE.c(this.c()).asType();
        return T(this.tid());
    }

    default boolean isRootType() {
        return Objects.equals(this.vid(), ALL) || (null == this.vid() && Objects.equals(this.tid().basePath(), ALL));
    }

    default Call constructor() {
        return this.jvm().get1();
    }

    default Call predicate() {
        return this.jvm().get0();
    }

    default Type predicate(final Call predicate) {
        return this.clone(Tuple.Pair.with(predicate, this.constructor()), this.tid(), this.vid());
    }

    default boolean hasPredicate() {
        return null != this.jvm().get0() && !this.jvm().get0().isNoObj();
    }

    default boolean hasConstructor() {
        return null != this.jvm().get1() && !this.jvm().get1().isNoObj();
    }

    @Override
    default boolean test(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        if (rhs.isNoObj() && this.c().isZeroable())
            return true;
        if (rhs.isCall())
            return this.test(rhs.dom());
        if (!rhs.isType())
            return false;
        // A TYPE'S SPAN is how many in a row: a type is within another only when its span is, as its point is. A
        // generic, the root and the type of types are not runs, so they take any span. The coefficient compared
        // later is the count (span times point), so a run judges its point here: {1}int{6} is no {*}int{0,5}.
        if ((!this.tid().b().isOne() || !rhs.tid().b().isOne())
                && !(this.tid().b().within(rhs.tid().b()) && this.tid().d().within(rhs.tid().d()))
                && !rhs.asType().isGeneric() && !rhs.asType().isRootType()
                && !Tokens.TYPE_TID.equals(rhs.tid().basePath()))
            return false;
        if (this.isFibered() && this.recFibers().stream().anyMatch(fiber -> fiber.test(rhs)))
            return true;
        if (rhs.asType().isNominal())
            return this.testNominally(rhs);
        if (null != this.vid() && Objects.equals(this.vid(), rhs.vid()))
            return this.c().within(rhs.c()) &&
                    (!rhs.asType().hasPredicate() || Objects.equals(this.predicate(), rhs.asType().predicate()));
        if (rhs.isType() && rhs.asType().isRootType() && !rhs.asType().hasPredicate())
            return this.c().within(rhs.c());
        if (null != this.vid() &&
                // identity, not a claim: erasure is explicit here
                Helper.testByIdentity(this.vid(), rhs.vid()) &&
                (!rhs.asType().hasPredicate() || (Objects.equals(this.predicate(), rhs.asType().predicate()))))
            return this.c().within(rhs.c());
        if (!this.isGeneric() && !rhs.asType().isGeneric() && !this.testNominally(rhs))
            return false;
        if (!this.c().within(rhs.c()))
            return false;
        if (!rhs.asType().parentType().isRootType() && !this.test(rhs.asType().parentType()))
            return false;
        // identity, not a claim: erasure is explicit here. Two lst brackets are the run walk's to compare
        // (testNominally reads them through Type.Helper.testTypePoly), not the fURI bracket rule's
        final boolean brackets = this.isTensored() && rhs.asType().isTensored();
        if (rhs.asType().isBaseType() && !Helper.testByIdentity(brackets ? this.baseTypeID().polyLess() : this.baseTypeID(),
                brackets ? rhs.tid().polyLess() : rhs.tid()))
            return false;
        if (rhs.tid().isGeneric())
            return !this.tid().isGeneric() || (this.c().within(rhs.c()) && this.tid().basePath().equals(rhs.tid().basePath()));
        return !rhs.asType().hasPredicate() || Objects.equals(this.asType().predicate(), rhs.asType().predicate());
    }

    /**
     * A TYPE APPLIED IS A POINTED FILTER (Marko, 2026-11-17): a type is a predicate instruction, {@code A{?}<=A}, so
     * applied to x it answers c·x where x is one of it, and noobj elsewhere, c its point. The point floats:
     * {@code map(int{2}::T)} is {@code map{2}(int::T)}, so {@code 1.branch(int::T,int::T)} is {@code {2}1}, while
     * {@code 1.branch(int::T,str::T)} is {@code 1} and {@code true.branch(int::T,str::T)} is nothing. A span is a run
     * of the filter, P·P = P, so it tells only at zero, where {@code {0}P{c}} is c·id and takes every obj
     * ({@code {2}int{3}::T} is {@code int{3}::T}). Only an exact point makes copies: a ranged one
     * ({@code int{?}::T}, the {@code {*}} of an argument pattern) is a quantifier, how many objs a place takes, and
     * passes the obj as it is.
     * Membership is asked of each obj the stream holds at the domain gate
     * ({@link Inst.Helper#filterOnDomainAllowUnique}), against the type without its counts ({@link Helper#unit}), so
     * {@code {3}5} is an int as 5 is: a base type asks it of its base, a refinement of its parent and then its
     * predicate. An uncaught fail passes, as it passes every step.
     */
    @Override
    default Obj apply(final Obj obj) {
        final cInt stated = this.tid().d();
        // AN EXACT POINT IS COPIES, A RANGED ONE A QUANTIFIER: int{2}::T is two filters, while the {*} of <#{*}>::T, a
        // pattern an argument is computed by, says how many objs the place takes and makes none. Exact is a stated
        // number: the {**} of a type read for any multiplicity has no bound at all, and isExact() alone took it
        final cInt point = stated.isExact() && null != stated.min() ? stated : cInt.ONE();
        final Type parentType = this.parentType();
        return objs(obj.stream().map(one -> {
            if (one.isUncaughtFail())
                return one;
            // a base type asks membership; a pattern with no base of its own (a generic, a variable, a root) passes
            final boolean member = this.tid().b().isZero() ||
                    ((parentType.isRootType() ?
                            !this.isBaseType() || Inst.Helper.filterOnDomainAllowUnique(one, Helper.unit(this)) :
                            !parentType.apply(one).isNoObj()) &&
                            (null == this.predicate() || one.test(predicate().apply(one))));
            if (!member)
                return noobj();
            // the point goes on as the processor puts an inst's point on its answer: a mono value's span stays its span
            return point.isZero() ? noobj() : point.isOne() ? one : one.tid().b().isOne() || !one.isMono() ?
                    one.c(c -> c.mult(point)) : one.d(one.tid().d().mult(point));
        }));
    }

    /**
     * A SPAN OF ZERO IS id() (Marko, 2026-11-17): {@code {0}P{c}} is c·P⁰, the predicate not applied, so the obj is the
     * obj and never noobj. A type is the zero exactly when its point is, as an instruction is ({@link Inst#isNoObj}):
     * {@link Obj#isNoObj()} tests c(), which is span x point, and read {@code {0}int::T} as nothing.
     */
    @Override
    default boolean isNoObj() {
        return this.tid().d().isZero();
    }

    final static class Helper {

        /**
         * ONE COPY AT ONE INDEX: the obj without its counts, span and point, in its tid and, for a type, in its vid
         * too, since a base type is tested by its vid and keeps its counts there ({@code int{2}::T} has the vid
         * {@code /m/int{2}}). A type's filter asks membership of it ({@link Type#apply}), and a lst asks it index by
         * index ({@link Lst#test}).
         */
        public static Obj unit(final Obj obj) {
            final Obj bare = obj.tid(obj.tid().cLess().b(null));
            return obj.isType() && null != obj.vid() ? bare.vid(obj.vid().cLess().b(null)) : bare;
        }

        /**
         * ONE INDEX HOLDS WHAT IS EXPECTED OF IT: the one judgment every index walk asks, a lst against a lst of
         * patterns ({@link Lst.Helper#testIndices}) and a value laid against a run of a bracket or a signature
         * ({@link #runs}, {@link #unfold}) alike (Marko, 2026-11-18: one funnel, not implementations that may
         * conflict). A point is mass at the index, so the value's mass is within the expected one's ({@link #mass}),
         * and every copy the index holds is one of the expected, both without their counts ({@link #unit}): a type
         * asks it at the domain gate ({@link Inst.Helper#filterOnDomainAllowUnique}), so {@code {3}5} is an int as
         * 5 is, and a value or an instruction is asked as a pattern is ({@code is(gt(3))} runs its predicate). An
         * index with no mass holds what its mass allows, so a missing index is {@code holds(noobj(), expected)}, and
         * a type of span zero, its filter run zero times (P⁰ = id), takes any copy.
         */
        public static boolean holds(final Obj value, final Obj expected) {
            final cInt has = mass(value);
            if (!has.within(mass(expected)))
                return false;
            if (has.isZero() || (expected.isType() && expected.tid().b().isZero()))
                return true;
            final Obj one = unit(expected);
            return value.stream().allMatch(copy -> expected.isType() ?
                    Inst.Helper.filterOnDomainAllowUnique(unit(copy), one) :
                    unit(copy).test(one));
        }

        /**
         * The mass at one index: a type's or an instruction's point (its span is a run of it, P·P = P for a type),
         * a value's or a stream's count.
         */
        public static cInt mass(final Obj obj) {
            return obj.isType() || obj.isCall() ? obj.tid().d() : obj.c();
        }

        static boolean basePathEquals(final fURI a, final fURI b) {
            return null != a && null != b && a.basePath().equals(b.basePath());
        }

        public static Type findLCD(final List<Type> types) {
            if (types == null || types.isEmpty())
                return null;
            if (types.size() == 1)
                return types.getFirst();
            // Build the full ancestry chain of the first type, from most-specific to most-general
            final List<Type> chain = new ArrayList<>();
            Type t = types.getFirst();
            while (!t.isRootType()) {
                chain.add(t);
                if (t.isBaseType()) break;
                t = t.parentType();
            }
            final cInt lcdC = types.stream().map(Type::c).reduce(cInt::span).orElse(cInt.ONE());
            // Walk the chain outward; the first ancestor ON EVERY TYPE'S OWN ANCESTRY is the LCD (Marko, 2026-11-16).
            // isRefinementOf would not do: it lets a parent refine its child and a sibling refine a sibling, so [pupa, int]
            // answered pupa and [nat, pupa] answered nat, where both are int.
            final List<List<fURI>> ancestries = types.stream().map(Helper::vidAncestryChain).toList();
            for (final Type candidate : chain) {
                if (candidate.hasVID() && ancestries.stream().allMatch(ancestry -> ancestry.contains(candidate.vid().basePath())))
                    return candidate.c(lcdC).as();
            }
            // No common ancestor — types are from disjoint hierarchies; fall back to the universal type
            return ALL_TYPE.c(lcdC).as();
        }

        /**
         * Generates the Lowest Common Denominator type for a set of types.
         * Finds the common nominal ancestor, structurally merges isa predicates,
         * and concatenates non-isa predicates via map() chaining.
         *
         * @param types  the set of types to compute the LCD for
         * @param lcdVID the VID to assign to the generated LCD type
         * @return the most specific type that subsumes all input types
         */
        public static Type generateLCD(final Set<Type> types, final fURI lcdVID) {
            if (types == null || types.isEmpty())
                return null;
            if (types.size() == 1)
                return types.iterator().next().asType();

            // 1. Find the common nominal ancestor TID via VID-chain intersection
            final fURI commonTID = commonTID(types);

            // Disjoint hierarchies: return ALL_TYPE directly so isRefinementOf works
            if (ALL.equals(commonTID.basePath())) {
                return ALL_TYPE.c(types.stream()
                        .map(Type::c)
                        .reduce(cInt::span)
                        .orElse(cInt.ONE())).asType();
            }

            // 2. Separate isa records and non-isa calls from all predicate stacks
            final List<Obj> allIsaRecords = new ArrayList<>();
            final List<Call> allNonIsaCalls = new ArrayList<>();
            for (final Type type : types) {
                final Tuple.Pair<List<Obj>, List<Call>> separated = separatePredicates(type);
                allIsaRecords.addAll(separated.get0());
                allNonIsaCalls.addAll(separated.get1());
            }

            // 3. Merge isa record constraints structurally (recursive on field types)
            final Obj mergedIsaRec = mergeIsaRecords(allIsaRecords, lcdVID);

            // 4. Build the combined predicate via split/merge (OR semantics)
            //    -<[isa(mergedRec), nonIsaPred1, nonIsaPred2, ...]>-
            final Call combinedPred;
            final List<Obj> predicateBranches = new ArrayList<>();
            if (null != mergedIsaRec) {
                predicateBranches.add(instB(ISA_INST_TID, lst(mergedIsaRec)));
            }
            for (final Call call : allNonIsaCalls) {
                predicateBranches.add(call.tryToInst());
            }

            if (predicateBranches.isEmpty()) {
                combinedPred = null;
            } else if (predicateBranches.size() == 1) {
                combinedPred = (Call) predicateBranches.get(0);
            } else {
                // OR all branches: split_([...]).merge_()
                final List<Inst> insts = new ArrayList<>();
                insts.add(instB(SPLIT_INST_TID, lst(lst(predicateBranches.toArray(new Obj[0])))));
                insts.add(instB(MERGE_INST_TID, lst()));
                combinedPred = Call.from(insts);
            }

            // 6. Compute span coefficient (not sum)
            final cInt lcdC = types.stream()
                    .map(Type::c)
                    .reduce(cInt::span)
                    .orElse(cInt.ONE());

            // 7. Assemble the LCD type (clear any prior registration to avoid stale cache)
            if (Machine.loaded()) {
                Machine.write(lcdVID, noobj());
            }
            return T(Tuple.Pair.with(combinedPred, null), commonTID.big(), lcdVID.big()).c(lcdC).asType();
        }

        /**
         * Collects the VID ancestry chain from this type up to (but excluding) root.
         */
        private static List<fURI> vidAncestryChain(final Type type) {
            final List<fURI> chain = new ArrayList<>();
            Type current = type;
            while (!current.isRootType()) {
                if (current.hasVID())
                    chain.add(current.vid().basePath());
                if (current.isBaseType()) break;
                current = current.parentType();
            }
            return chain;
        }

        /**
         * Finds the deepest VID shared by all types' ancestry chains (the LCD's TID).
         */
        private static fURI commonTID(final Set<Type> types) {
            final List<List<fURI>> chains = types.stream()
                    .map(Helper::vidAncestryChain)
                    .toList();

            // Use string comparison for intersection to avoid fURI hashCode inconsistencies
            final Set<String> intersection = new LinkedHashSet<>();
            for (final fURI vid : chains.get(0)) {
                intersection.add(vid.toString());
            }
            for (int i = 1; i < chains.size(); i++) {
                final Set<String> other = new HashSet<>();
                for (final fURI vid : chains.get(i)) {
                    other.add(vid.toString());
                }
                intersection.retainAll(other);
            }

            if (intersection.isEmpty())
                return ALL;

            // First VID in the first chain whose string is in the intersection = deepest common
            for (final fURI vid : chains.get(0)) {
                if (intersection.contains(vid.toString()))
                    return vid;
            }

            return ALL;
        }

        /**
         * Separates a type's predicate stack into isa-predicate records
         * and non-isa predicate calls.
         */
        private static Tuple.Pair<List<Obj>, List<Call>> separatePredicates(final Type type) {
            final List<Obj> isaRecords = new ArrayList<>();
            final List<Call> nonIsaCalls = new ArrayList<>();

            for (final Call predCall : type.predicateStack()) {
                if (predCall.isNoObj()) continue;
                final List<Inst> insts = predCall.insts();
                if (insts.size() == 1 &&
                        insts.get(0).tid().basePath().equals(ISA_INST_TID)) {
                    isaRecords.add(insts.get(0).arg(0));
                } else if (!insts.isEmpty()) {
                    nonIsaCalls.add(predCall);
                }
            }

            return Tuple.Pair.with(isaRecords, nonIsaCalls);
        }

        /**
         * Merges a list of isa-predicate record constraints into a single
         * structural constraint. Fields present in all records are recursively
         * LCD'd; fields present in only some are LCD'd then relaxed to optional.
         */
        private static Obj mergeIsaRecords(final List<Obj> records, final fURI parentVID) {
            if (records.isEmpty()) return null;

            final Set<Obj> allKeys = new LinkedHashSet<>();
            for (final Obj rec : records) {
                if (rec.isRec()) {
                    rec.asRec().keys().forEach(allKeys::add);
                }
            }

            if (allKeys.isEmpty()) return null;

            final Map<Obj, Obj> mergedFields = new LinkedHashMap<>();
            for (final Obj key : allKeys) {
                final List<Type> fieldTypes = new ArrayList<>();
                for (final Obj rec : records) {
                    if (rec.isRec()) {
                        final Obj fieldType = rec.asRec().at(key);
                        if (!fieldType.isNoObj() && fieldType.isType()) {
                            fieldTypes.add(fieldType.asType());
                        }
                    }
                }

                if (fieldTypes.isEmpty()) continue;

                final Type mergedType;
                final String keyName = key.isUri() ? key.uriValue().name() : key.toString();
                final fURI nestedVID = parentVID.extend("_" + keyName);

                if (fieldTypes.size() == records.size()) {
                    // Present in all records: recursive LCD
                    mergedType = generateLCD(new LinkedHashSet<>(fieldTypes), nestedVID);
                } else {
                    // Present in some records: LCD then relax coefficient to include 0
                    final Type lcd = generateLCD(new LinkedHashSet<>(fieldTypes), nestedVID);
                    mergedType = lcd.c(cInt.of(0L, lcd.c().max())).asType();
                }

                mergedFields.put(key, mergedType);
            }

            if (mergedFields.isEmpty()) return null;
            return rec(mergedFields);
        }

        public static boolean nominalTypeChecker(final Obj obj, final Type type) {
            Type objType = Obj.Helper.specificType(obj);
            final fURI nominalVID = type.vid();
            // identity, not a claim: erasure is explicit here
            if (!testByIdentity(obj.baseTypeID(), type.baseTypeID()))
                return false;
            // identity, not a claim: erasure is explicit here
            if (objType.isBaseType() && testByIdentity(objType.vid(), type.baseTypeID()))
                return testTypePoly(obj, type);
            while (true) {
                // LOG.warn("checking %s is a %s",objType, type);
                if (objType.isRootType())
                    return false;
                // identity, not a claim: erasure is explicit here
                if (testByIdentity(objType.vid(), nominalVID))
                    return testTypePoly(obj, type);
                if (objType.isBaseType())
                    return false;
                // final Type temp = objType.parentType();
                //if (temp.equals(objType.parentType()))
                //       return false;
                objType = objType.parentType();
            }
        }

        /**
         * The poly readings {@code object} is held to: every bracketed type on {@code type}'s chain
         * ({@link Type#refinesPolys()}) reads each member of its own kind — a fibered type a rec
         * ({@link #testFibration}), a tensored type a lst ({@link #testTensor}), a pathed type a rel
         * ({@link #testPaths}). A type is read by its own bracket ({@code lst[int]} is not a {@code lst[str]}).
         */
        public static boolean testTypePoly(final Obj object, final Type type) {
            final List<Type> polys = type.refinesPolys();
            return object.stream().allMatch(member -> polys.stream().allMatch(poly ->
                    member.isRec() && poly.isFibered() ? testFibration(member.asRec(), poly)
                            : member.isLst() && poly.isTensored() ? testTensor(member.asLst(), poly)
                            : member.isRel() && poly.isPathed() ? testPaths(member.asRel(), poly)
                            // a bracket against a bracket is the same run walk, closed on the lhs: each of its entries is a
                            // run the rhs's entries must take, a signature against a signature, since a type's bracket is
                            // its argument list and a claim with no acceptor fails
                            : !member.isType() || !poly.isTensored() || runs(member.asType().lstIndices(), poly.lstIndices()).contains(member.asType().lstIndices().size())));
        }

        /**
         * A fibered rec carries every field its bracket names ({@code rec[k1,k2]}, or a declaration's fibers
         * {@code rec[role]::T[…]@person}). A field may be absent only where its entry admits zero ({@code rec[{?}k,j]})
         * or the declaration types it so ({@code role=>?rec::T}); a field nothing types is required.
         */
        public static boolean testFibration(final Rec rec, final Type fiberedRec) {
            return fiberedRec.tid().poly().stream().map(entry -> f(entry.trim())).allMatch(field ->
                    field.c().isZeroable() || rec.has(field.basePath())
                            || noobj().test(Optional.ofNullable(declaredField(fiberedRec, field.basePath())).orElse(ALL_TYPE)));
        }

        /**
         * The type a fibered rec's declaration gives one of its fields: the rec shape in the type's own predicates,
         * leaf-first ({@code rec[role]::T[?[…,role=>?uri::T]]@person} answers the field {@code role} its declared
         * type). Null when no predicate types the field — a bare {@code rec[k1,k2]} types nothing.
         */
        private static Obj declaredField(final Type fiberedRec, final fURI field) {
            Type level = fiberedRec;
            for (int hops = 0; !level.isRootType() && hops < 256; hops++) {
                if (level.hasPredicate()) {
                    final Obj shape = typePredicateObj(level);
                    if (shape.isRec()) {
                        final Obj declared = shape.asRec().at(field);
                        if (!declared.isNoObj())
                            return declared;
                    }
                }
                final Type parent = level.parentType();
                if (parent.equals(level))
                    break;
                level = parent;
            }
            return null;
        }

        /**
         * A tensored lst holds the types its bracket names ({@code lst[int,str]}), read the way values are written
         * (Marko, 2026-11-18): {@code int{4}} is a run of four int indices and {@code {4}int} one index holding four ints
         * ({@link Type#lstIndices()}). So {@code [3{4}]}, which is {@code [3,3,3,3]}, is a {@code lst[int{4}]} and a
         * {@code lst[int{2},int,int]}, while {@code [{4}3]}, four ints at one index, is neither: mass never spreads over
         * extent, and indices never pool into mass. Each entry takes k indices, k in its span, every one holding the
         * entry at its mass, and a trailing entry that admits zero may be absent. Past the last entry the lst is open,
         * unless that entry's span is unbounded: a {@code {*}} tail claims the rest, so {@code lst[int{*}]} holds ints
         * only, and a tail of indices that hold nothing, {@code lst[int{2},{0}#{*}]}, is exactly two ints. The walk is
         * {@link #runs}, the one funnel a run of slots is tested through.
         */
        public static boolean testTensor(final Lst lst, final Type tensoredLst) {
            final List<Type> entries = tensoredLst.lstIndices();
            final List<Obj> values = Lst.Helper.laid(lst.lstValue().stream().map(value -> (Obj) value.autoResolve(lst)).toList());
            final Set<Integer> reached = runs(values, entries);
            return entries.isEmpty() || null != entries.getLast().tid().b().max() ? !reached.isEmpty() : reached.contains(values.size());
        }

        /**
         * The runs {@code entries} lay over {@code values}: the indices the entries, in order, can end at. An entry's
         * SPAN covers a run, {@code {span}type{point}}: it takes k values, k in its span, each holding the entry
         * without its span ({@link #holds}, the judgment a lst of patterns asks of an index too), and a trailing entry
         * whose point admits zero may take none. A value that is itself a
         * spanned type is a whole run, so it takes an entry it tests whole: a signature against a signature. A lst
         * type is open past its last entry ({@link #testTensor}); an instruction's args are closed ({@link Inst#test}).
         */
        public static Set<Integer> runs(final List<? extends Obj> values, final List<? extends Obj> entries) {
            Set<Integer> reached = Set.of(0); // the indices the entries so far can end at
            for (final Obj entry : entries)
                reached = run(values, entry, reached);
            return reached;
        }

        /**
         * The entries laid over the values, one for each value: a value gets the entry whose run claims it, without
         * its span, so {@code f({*}int::T)} called as {@code f(1,2,3)} reads {@code f(int::T,int::T,int::T)} (Marko,
         * 2026-11-08: args unfold). A spanned type taken whole keeps the entry, an entry whose run claims nothing has no
         * place, and an earlier entry claims as much as it can. Null when no runs of the entries claim every value.
         */
        public static List<Obj> unfold(final List<? extends Obj> values, final List<? extends Obj> entries) {
            final List<Set<Integer>> reached = new ArrayList<>(List.of(Set.of(0))); // reached.get(e): where entry e can start
            for (final Obj entry : entries)
                reached.add(run(values, entry, reached.getLast()));
            if (!reached.getLast().contains(values.size()))
                return null;
            final Obj[] unfolded = new Obj[values.size()];
            int end = values.size();
            for (int e = entries.size() - 1; e >= 0; e--) {
                final Obj entry = entries.get(e);
                final Obj slot = entry.tid(entry.tid().b(null));
                final int stop = end;
                end = reached.get(e).stream().filter(start -> start <= stop && run(values, entry, Set.of(start)).contains(stop))
                        .max(Integer::compare).orElseThrow();
                for (int v = end; v < stop; v++)
                    unfolded[v] = fills(values.get(v), slot) ? slot : entry;
            }
            return new ArrayList<>(Arrays.asList(unfolded));
        }

        /**
         * Where one entry's run can end, from each of the starts: k values on, k in its span, every one holding the
         * entry without its span; one value on for a spanned type that tests the entry whole; and right where it
         * starts at the end of the values, when its point admits zero (a missing trailing index, as Lst.test allows).
         */
        private static Set<Integer> run(final List<? extends Obj> values, final Obj entry, final Set<Integer> starts) {
            final cInt span = entry.tid().b();
            final Obj slot = entry.tid(entry.tid().b(null)); // one index of the run: the entry without its span
            final long least = Math.max(0L, null == span.min() ? 0L : span.min());
            final Set<Integer> next = new TreeSet<>();
            for (final int start : starts) {
                if (start == values.size() && entry.c().isZeroable())
                    next.add(start);
                if (start < values.size() && !values.get(start).tid().b().isOne() && values.get(start).test(entry))
                    next.add(start + 1);
                if (null != span.max() && span.max() < least)
                    continue;
                int end = start;
                while (end < values.size() && (null == span.max() || end - start < span.max()) && fills(values.get(end), slot))
                    end++;
                for (long k = least; start + k <= end; k++)
                    next.add(start + (int) k);
            }
            return next;
        }

        /**
         * Whether a value fills one index of a run: it holds the run's slot ({@link #holds}), as an index of a lst of
         * patterns does. A value that is itself a spanned type or instruction is a run of its own rather than one
         * index, so it is tested whole, as a signature is against a signature ({@link #run}'s whole-run step).
         */
        private static boolean fills(final Obj value, final Obj slot) {
            return value.tid().b().isOne() ? holds(value, slot) : value.test(slot);
        }

        /**
         * A pathed rel is a right-nested chain through the vertices its bracket names ({@code rel[A,B,C]} is
         * {@code A => (B => C)}).
         */
        public static boolean testPaths(final Rel rel, final Type pathedRel) {
            return pathTested(rel, pathedRel.relPaths());
        }

        /**
         * The walk behind {@link #testPaths}: a rel's {@code first} is at the next vertex and its {@code second} is the
         * rest of the path, while an ordinary obj closes the path at a vertex after which every entry admits zero. A
         * vertex entry is a run of vertices, each holding the value at its mass ({@link Type#relPaths()}), so
         * {@code rel[int,person{2}]} is {@code int=>person=>person} and {@code rel[int,{2}person]} two people at one
         * vertex. A vertex whose run or mass admits zero may be skipped.
         */
        private static boolean pathTested(final Obj chain, final List<Type> vertices) {
            if (vertices.isEmpty())
                return false;
            final Type vertex = vertices.getFirst();
            final List<Type> rest = vertices.subList(1, vertices.size());
            if (vertex.c().isZeroable() && pathTested(chain, rest))
                return true;
            final cInt run = vertex.tid().b();
            if (null != run.max() && run.max() < 1)
                return false;
            // one vertex of the run, at its mass, and the run that is left behind it
            final Obj one = vertex.tid(vertex.tid().b(null));
            final Type left = vertex.tid(vertex.tid().b(cInt.of(Math.max(0L, null == run.min() ? 0L : run.min() - 1),
                    null == run.max() ? null : run.max() - 1))).asType();
            final List<Type> after = new ArrayList<>(List.of(left));
            after.addAll(rest);
            return chain.isRel()
                    ? chain.asRel().first().test(one) && pathTested(chain.asRel().second(), after)
                    : chain.test(one) && after.stream().allMatch(next -> next.c().isZeroable());
        }

        /**
         * Validates a type's bracket where the type is constructed, so a value is never held to an illegal one: a rel
         * bracket names a path of at least one hop (two vertices) whose vertex coefficients are multiplicities (not
         * negative), and an instance of a rec declaration names no more types than the declaration has fibers.
         *
         * @param type the type whose bracket is validated
         */
        public static void validatePoly(final Type type) {
            if (type.isPathed()) {
                final List<Type> path = type.relPaths();
                final String first = type.tid().poly().getFirst().trim();
                if (path.size() < 2)
                    throw MTronException.of(
                            "rel[%s] is not a path: a rel poly names the vertices of a chain of at least one hop, so it takes at least two vertices (rel[%s,%s])",
                            first, first, first);
                for (final Obj vertex : path)
                    if (vertex.c().isNeg())
                        throw MTronException.of(
                                "rel path vertex coefficient {%s} on %s is not yet matched: a vertex coefficient is the multiplicity of the value at that vertex, which is not negative (one, {?}, {n}, {n,m}, {+} and {*} are matched)",
                                vertex.c(), vertex);
            } else if (Tokens.REC_TID.basePath().equals(type.tid().basePath())) {
                final List<String> fibers = type.isFibered() ? type.tid().poly() : List.of();
                if (type.recFibers().size() > fibers.size())
                    throw MTronException.of("%s has no fiber for %s: a bracket entry types a fiber, and the declaration %s names %s",
                            type.vid(), String.join(",", type.vid().poly().subList(fibers.size(), type.vid().poly().size()).stream().map(String::trim).toList()),
                            type.tid(), fibers.isEmpty() ? "none" : String.join(",", fibers.stream().map(String::trim).toList()));
            }
        }

        /**
         * THE REQUESTED BRACKET SURVIVES RESOLUTION. A type's bracket is its TYPE ARGUMENT list, so a
         * request that names arguments ({@code person[ninja]}, {@code lst[int]})
         * must keep them on the type it resolves to. Resolving reads the registered declaration, and the
         * declaration's own bracket is its PARAMETER list ({@code person[role]::T}), so the request
         * substitutes that bound positionally: {@code person[role]} + {@code [ninja]} -> {@code
         * person[ninja]}. Any parameter the request leaves unclaimed keeps the declaration's entry, and a
         * request naming no arguments leaves the declaration exactly as it stands.
         *
         * @param declaredID  the registered type's id (carrying the declaration's parameters, if any)
         * @param requestedID the id the request spelled (carrying the arguments, if any)
         * @return {@code declaredID} instantiated with the requested arguments
         */
        public static fURI instantiate(final fURI declaredID, final fURI requestedID) {
            if (null == declaredID || null == requestedID)
                return declaredID;
            final List<String> requested = requestedID.poly();
            if (requested.isEmpty())
                return declaredID;
            final List<String> declared = declaredID.poly();
            final List<String> instantiated = new ArrayList<>(requested);
            for (int i = requested.size(); i < declared.size(); i++)
                instantiated.add(declared.get(i));
            return declaredID.poly(instantiated);
        }

        /**
         * IDENTITY, NOT A CLAIM: do these two ids name the same type FAMILY? A bracket on a type's id is
         * an instantiation of its declaration ({@code object[/m/code]} instantiates {@code object}), so a
         * bracketed id compared against its own ERASURE -- an rhs that carries no bracket, the
         * uninstantiated name -- is compared erased. This is the only erasure in the system: {@link
         * fURI#test} keeps the bracket rule's asymmetric CLAIM semantics untouched, so
         * {@code person[ninja].test(person)} stays false. A bracketed rhs is still a claim position and is
         * compared as one (a {@code lst[int]} value does not satisfy a {@code lst[str]} contract).
         *
         * @param lhs the id whose family membership is asked (may be an instantiation)
         * @param rhs the id it is asked against (the erasure when the question is identity)
         * @return true when lhs is rhs's family
         */
        public static boolean testByIdentity(final fURI lhs, final fURI rhs) {
            if (null == lhs || null == rhs)
                return false;
            // identity, not a claim: erasure is explicit here
            return (rhs.poly().isEmpty() ? lhs.polyLess() : lhs).test(rhs);
        }

        public static Obj typePredicateObj(final Type type) {
            if (type.hasPredicate() && type.predicate().insts().size() == 1 && type.predicate().insts().getFirst().tid().basePath().equals(ISA_INST_TID))
                return type.predicate().insts().getFirst().arg(0);
            return type.predicate();
        }

        public static Poly<?, ?> polyTypePredicateObj(final Type type) {
            if (type.hasPredicate() && type.predicate().insts().size() == 1 && type.predicate().insts().getFirst().tid().basePath().equals(ISA_INST_TID))
                return type.predicate().insts().getFirst().arg(0).isPoly() ? type.predicate().insts().getFirst().arg(0).as() : null;
            return null;
        }

        /**
         * The level of {@code type}'s predicate stack whose OWN predicate rejects {@code obj}, or null
         * when every level accepts it. Leaf-first, mirroring {@link Type#predicateStack()}.
         * <p>
         * This exists because a type's constraints are the conjunction of its whole stack, so the leaf
         * alone does not explain a rejection: young::-3 fails nat's ?>=0 while young's own ?<25 accepts
         * it, and a message that prints only the leaf names the predicate that passed.
         */
        public static Type rejectedBy(final Obj obj, final Type type) {
            final Obj lhs = obj.clone().selfTID(obj.baseTypeID());
            Type level = type;
            while (!level.isRootType()) {
                if (level.hasPredicate()) {
                    final Call predicate = level.predicate();
                    if (null != predicate && !predicate.isNoObj() && predicate.apply(lhs).isNothing())
                        return level;
                }
                final Type parent = level.parentType();
                if (parent.equals(level))
                    break;
                level = parent;
            }
            return null;
        }

        public static boolean typeCheck(final Obj lhs, final Obj rhs) {
            if (null != lhs.vid() && Objects.equals(lhs.vid(), rhs.vid()))
                return lhs.c().within(rhs.c()) &&
                        (!rhs.isType() || !rhs.asType().hasPredicate() ||
                                (lhs.isType() && Objects.equals(lhs.asType().predicate(), rhs.asType().predicate())));
            if (lhs.isType()) {
                /// /////////////////////////
                /// TYPE <=> OBJ or TYPE ///
                /// ////////////////////////
                if (Obj.Helper.isAuto(rhs))
                    return true;
                if (rhs.isNoObj() && lhs.c().isZeroable())
                    return true;
                if (rhs.isObjCall())
                    return lhs.test(rhs.dom());
                if (!rhs.isType())
                    return false;
                if (!lhs.c().within(rhs.c()))
                    return false;
                if (rhs.asType().isNominal())
                    return Type.Helper.nominalTypeChecker(lhs, rhs.asType());
                if (lhs.tid().hasPoly() && rhs.tid().hasPoly()) {
                    if (!lhs.tid().polyParsed().orElse(lst()).test(rhs.tid().polyParsed().orElse(lst())))
                        return false;
                }
                if (!lhs.test(rhs.asType().parentType()))
                    return false;
                if (lhs.asType().isBaseType())
                    // identity, not a claim: erasure is explicit here
                    return testByIdentity(lhs.baseTypeID(), rhs.tid()) &&
                            (!rhs.asType().hasPredicate() || Objects.equals(lhs.asType().predicate(), rhs.asType().predicate())); // matches any abstract type to it's base type as long as within the coefficient boundaries
                if (rhs.tid().isGeneric())
                    return !lhs.tid().isGeneric() ||
                            (lhs.c().within(rhs.c()) && lhs.tid().basePath().equals(rhs.tid().basePath()));
                return !rhs.asType().hasPredicate() ||
                        Objects.equals(lhs.asType().predicate(), rhs.asType().predicate());// || !rhs.asType().predicate().apply(this).isNoObj();
            } else if (rhs.isType()) {
                /// //////////////////
                /// OBJ <=> TYPE ///
                /// //////////////////
                /*if (!rhs.asType().isGeneric() && rhs.asType().isEphemeral()) {
                    Type non = T(rhs.vid());
                    rhs.self(non.jvm(), non.tid(), non.vid());
                }*/
                if (rhs.vid().isGeneric() || rhs.isObjCall())
                    return true;
                if (!testTypePoly(lhs, rhs.asType()))
                    return false;
                if (lhs.isObjs() && lhs.c().within(rhs.c()) && lhs.stream().allMatch(o -> o.test(rhs.c(o.c()))))
                    return true;
                if (rhs.asType().isBaseType() && !lhs.baseTypeID().test(rhs.vid()))
                    return false;
                final fURI lhsTID = lhs.tid();
                boolean accepted = true;
                for (final Call pred : rhs.asType().predicateStack()) {
                    if (pred.isNoObj())
                        continue;
                    if (pred.dom(ALL_TYPE).apply(lhs).isNothing()) {
                        accepted = false;
                        break;
                    }
                }
                // Obj.Helper.objClone is an identity function when tid, vid and jvm are all unchanged, so
                // lhs.clone() can BE lhs. The base-type re-tag above would then mutate the caller's object --
                // a test that strips a handler or a typed value of its type as a side effect. Undo exactly
                // that case; a genuine clone is left alone.
                if (!Objects.equals(lhs.tid(), lhsTID))
                    lhs.selfTID(lhsTID);
                return accepted;
            } else {
                return lhs.test(rhs);
            }
        }

        public static void typeCheck(final Obj checkObj, final Type requiredType) {
            if (!checkObj.test(requiredType))
                throw MTronException.of("%s is not a %s", Obj.Helper.specificTypeId(checkObj), requiredType.vid());
        }
    }

    final class TypeType {
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    //        instC(RSHIFT_INST_TID.dom(TYPE_TID).rng(ALL_STAR), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(inst.arg(0).orElse((Obj) uri(ALL)).stream().flatMap(u -> rec(
                    //            uri("pred"), lhs.asType().hasPredicate() ? lhs.asType().predicate() : noobj(),
                    //           uri("cons"), lhs.asType().hasConstructor() ? lhs.asType().constructor() : noobj()).at(u).stream())))
            ));
        }
    }

}
