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
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

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
    default boolean isNominal() {
        return !this.hasPredicate() && !this.hasConstructor() && this.hasVID() && !this.isBaseType() && !this.vid().hasPattern() && !this.vid().isGeneric();
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
        return this.hasPredicate() && this.predicate().isObjCall() && this.predicate().asCall().insts().size() == 1 &&
                this.predicate().asCall().insts().getFirst().tid().equals(ISA_INST_TID);
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
     * The types at a tensor's indices ({@code lst[int,str]}); empty for any other bracket.
     */
    default List<Obj> lstIndices() {
        return this.isTensored() ? this.tid().polyParsed().map(Obj::lstValue).orElse(List.of()) : List.of();
    }

    /**
     * The types at a path's vertices ({@code rel[int,str]}); empty for any other bracket.
     */
    default List<Obj> relPaths() {
        return this.isPathed() ? this.tid().polyParsed().map(Obj::lstValue).orElse(List.of()) : List.of();
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
        // identity, not a claim: erasure is explicit here
        if (rhs.asType().isBaseType() && !Helper.testByIdentity(this.baseTypeID(), rhs.tid()))
            return false;
        if (rhs.tid().isGeneric())
            return !this.tid().isGeneric() || (this.c().within(rhs.c()) && this.tid().basePath().equals(rhs.tid().basePath()));
        return !rhs.asType().hasPredicate() || Objects.equals(this.asType().predicate(), rhs.asType().predicate());
    }

    @Override
    default Obj apply(final Obj obj) {
        Type parentType = this.parentType();
        if (!parentType.isRootType()) {
            final Obj parentApply = parentType.apply(obj);
            if (parentApply.isNoObj())
                return noobj();
        }
        return null == this.predicate() || obj.test(predicate().apply(obj)) ?
                obj :
                noobj();
    }

    final static class Helper {

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
            // Walk the chain outward; the first ancestor that ALL types share is the LCD
            for (final Type candidate : chain) {
                if (types.stream().allMatch(type -> type.isRefinementOf(candidate)))
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
                            : !member.isType() || !poly.isTensored() || testTensor(lst(member.asType().lstIndices()), poly)));
        }

        /**
         * A fibered rec carries every field its bracket names ({@code rec[k1,k2]}, or a declaration's fibers
         * {@code rec[role]::T[…]@person}). A field may be absent only where its entry admits zero ({@code rec[k{?},j]})
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
         * A tensored lst holds the type at each of its indices ({@code lst[int,str]}): {@link Lst#test(Obj)} asks index
         * by index, and a missing trailing index passes when its entry admits zero.
         */
        public static boolean testTensor(final Lst lst, final Type tensoredLst) {
            return lst.test(lst(tensoredLst.lstIndices()));
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
         * vertex's coefficient is the multiplicity of the value at it, so a vertex that admits zero may be skipped.
         */
        private static boolean pathTested(final Obj chain, final List<Obj> vertices) {
            if (vertices.isEmpty())
                return false;
            final Obj vertex = vertices.getFirst();
            final List<Obj> rest = vertices.subList(1, vertices.size());
            if (vertex.c().isZeroable() && pathTested(chain, rest))
                return true;
            return chain.isRel()
                    ? chain.asRel().first().test(vertex) && pathTested(chain.asRel().second(), rest)
                    : chain.test(vertex) && rest.stream().allMatch(next -> next.c().isZeroable());
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
                final List<Obj> path = type.relPaths();
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
         * request that names arguments ({@code person[ninja]}, {@code lst[int]}, {@code object[int]})
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
                // The type's constraints are the conjunction of EVERY predicate from here to the root, not
                // this level's alone: a stockholmare must satisfy creature[age:int] and human[name:str] and
                // swedish[name has 'son'] as well as its own city requirement. predicateStack() is leaf-first
                // and skips levels that carry no predicate of their own.
                // NOTE: selfTID() prevents infinite recursion on type checking.
                final fURI lhsTID = lhs.tid();
                //final Obj predLhs = lhs.clone().selfTID(lhs.baseTypeID());
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

    class Builder {

        public fURI vid = null;
        public fURI tid = null;
        public Call predicate = null;
        public Call constructor = null;
        public Obj zero = null;
        public Obj one = null;
        public Inst plus = null;
        public Inst mult = null;
        public Inst neg = null;
        public Set<Inst> insts = new LinkedHashSet<>();
        /**
         * THE ALGEBRA AWAITING ITS TYPE'S SPACE — the theory instances declared through {@link #law}, keyed by the
         * type's vid. A type is built before its instset is mounted, so the declaration cannot be persisted at
         * {@code create()} time (the same reason {@code docWrap} soft-fails during an ISA's own setup); the
         * instset persists it through {@link #drain} as soon as its space exists. Staging only — {@code ?lawq} is
         * the store of record, and nothing reads this map.
         */
        private static final Map<fURI, Rec> LAWS = new ConcurrentHashMap<>();

        public static Builder build() {
            return new Builder();
        }

        public Builder vid(fURI vid) {
            this.vid = vid;
            return this;
        }

        public Builder tid(final fURI tid) {
            this.tid = tid;
            return this;
        }

        public Builder zero(final Obj zero) {
            this.zero = zero;
            return this;
        }

        public Builder one(final Obj one) {
            this.one = one;
            return this;
        }

        public Builder plus(final Inst plus) {
            this.plus = plus;
            return this;
        }

        public Builder mult(final Inst mult) {
            this.mult = mult;
            return this;
        }

        public Builder neg(final Inst neg) {
            this.neg = neg;
            return this;
        }

        public Builder predicate(final Call predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder predicate(final BiFunction<Obj, Inst, Obj> predicate) {
            if (null == this.vid)
                throw MTronException.of("vid must be set prior to specifying predicate");
            if (null == this.tid)
                throw MTronException.of("tid must be set prior to specifying predicate");
            return this.predicate(instC(Tokens.INST_PRED_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(Tokens.ALL_STAR)), predicate));
        }

        public Builder isaPredicate(final Obj predicate) {
            return this.predicate(isa_(predicate).tryToInst());
        }

        public Builder constructor(final Call constructor) {
            this.constructor = constructor;
            return this;
        }

        public Builder constructor(final Function<Obj, Obj> function) {
            if (null == this.vid)
                throw MTronException.of("vid must be set prior to specifying constructor");
            return this.constructor(instC(Tokens.INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(T(Tokens.ALL_STAR)), (lhs, inst) -> function.apply(inst.arg(0))));
        }

        /**
         * DECLARE the type's algebra in the builder — the theory instances it models, e.g.
         * {@code law(mutableMap(uri("ring"), rec(...)))}. Composes with the rest of the builder and is persisted
         * to {@code ?lawq} by the instset that registers the type (see {@link #drain}).
         */
        public Builder law(final Map<Obj, Obj> theories) {
            return this.law(rec(studio.phaseshift.metatron.util.CommonUtil.mutableMap(theories)));
        }

        public Builder law(final Rec law) {
            if (null == this.vid)
                throw MTronException.of("vid must be set prior to declaring its algebra");
            LAWS.put(this.vid.basePath(), law);
            return this;
        }

        /**
         * DECLARE a type's algebra after the type was built — the form for a type whose declaration must sit below
         * the instruction addresses it points at (a field initializer may not read a field declared later).
         */
        public static void declare(final Type type, final Rec law) {
            if (null == type || null == type.vid() || null == law)
                return;
            LAWS.put(type.vid().basePath(), law);
        }

        /**
         * DECLARE an algebra at an ADDRESS — the form for a type this class does not hold as a {@link Type} object,
         * e.g. a THEORY type, whose only fact a declaration keys on is its vid (the same key
         * {@link #declare(Type, Rec)} writes). The drain that persists {@code ?lawq} reads this map by the vid's base
         * path, so a declaration staged here is persisted by the instset that registers that address.
         */
        public static void declare(final fURI vid, final Rec law) {
            if (null == vid || null == law)
                return;
            LAWS.put(null == vid.basePath() ? vid : vid.basePath(), law);
        }

        /**
         * PERSIST a type's declared algebra — the call an instset makes once its space is mounted, which is what
         * makes a declaration written at build time reach {@code ?lawq}. A type that declared none is untouched.
         */
        public static void drain(final Type type) {
            if (null == type || null == type.vid())
                return;
            final Rec law = LAWS.get(type.vid().basePath());   // a DECLARATION, not a one-shot: each VM boot repersists it
            if (null != law)
                studio.phaseshift.metatron.furi.q.QCollection.lawWrap(type, law);
        }

        public Builder constructor(final Supplier<Obj> supplier) {
            if (null == this.vid)
                throw MTronException.of("vid must be set prior to specifying constructor");
            return this.constructor(instC(Tokens.INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(), (lhs, inst) -> supplier.get()));
        }

        public Builder inst(final fURI tid, final Poly<?, ?> args, final BiFunction<Obj, Inst, Obj> func) {
            this.insts.add(instC(tid, args, func));
            return this;
        }

        public Builder inst(final Inst inst) {
            this.insts.add(inst);
            return this;
        }

        public Builder doc(final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            docWrap(this.insts.stream().toList().getLast(), domDesc, rngDesc, argDescription, description);
            return this;
        }

        public Type create(final Set<Type> typeSet, final Set<Inst> instSet) {
            //  LOG.info("installing %s type", this.vid);
            final Type type = this.create();
            typeSet.add(type);
            instSet.addAll(this.insts);
            return type;
        }

        public Type create() {
            assert this.tid != null;
            //assert this.vid != null;
            this.insts.forEach(inst -> Machine.current().memory().write(inst.tid(), inst));
            return T(Tuple.Pair.with(this.predicate, this.constructor), this.tid, this.vid);
        }
    }
}