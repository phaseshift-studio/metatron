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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
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
        return this.hasVID() && (this.isRootType() || mInstSet.BASE_TYPES.contains(this.vid().basePath()));
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
        Type current = this;
        while (!current.isRootType()) {
            if (current.hasVID() && current.vid().basePath().equals(other.tid().basePath()) || current.vid().basePath().equals(other.vid().basePath()))
                return this.c().within(other.c());
            if (current.isBaseType()) break;
            current = current.parentType();
        }
        return false;
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
        Type type = this;
        while (!type.isRootType()) {
            if (type.hasPredicate())
                result.add(type.predicate());
            type = type.parentType();
        }
        return result;
    }

    default Type parentType() {
        if (this.isRootType())
            return this;
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
        if (rhs.asType().isNominal())
            return this.testNominally(rhs);
        if (null != this.vid() && Objects.equals(this.vid(), rhs.vid()))
            return this.c().within(rhs.c()) &&
                    (!rhs.asType().hasPredicate() || Objects.equals(this.predicate(), rhs.asType().predicate()));
        if (rhs.isType() && rhs.asType().isRootType() && !rhs.asType().hasPredicate())
            return this.c().within(rhs.c());
        if (null != this.vid() &&
                this.vid().test(rhs.vid()) &&
                (!rhs.asType().hasPredicate() || (Objects.equals(this.predicate(), rhs.asType().predicate()))))
            return this.c().within(rhs.c());
        if (!this.isGeneric() && !rhs.asType().isGeneric() && !this.testNominally(rhs))
            return false;
        if (!this.c().within(rhs.c()))
            return false;
        if (!rhs.asType().parentType().isRootType() && !this.test(rhs.asType().parentType()))
            return false;
        if (rhs.asType().isBaseType() && !this.baseTypeID().test(rhs.tid()))
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
            if (Router.loaded()) {
                Router.writeToSpace(lcdVID, noobj());
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
            if (!obj.baseTypeID().test(type.baseTypeID()))
                return false;
            if (objType.isBaseType() && objType.vid().test(type.baseTypeID()))
                return true;
            while (true) {
                // LOG.warn("checking %s is a %s",objType, type);
                if (objType.isRootType())
                    return false;
                if (objType.vid().test(nominalVID))
                    return true;
                if (objType.isBaseType())
                    return false;
                // final Type temp = objType.parentType();
                //if (temp.equals(objType.parentType()))
                //       return false;
                objType = objType.parentType();
            }
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
                    return lhs.baseTypeID().test(rhs.tid()) &&
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
                if (rhs.tid().isGeneric() || rhs.isObjCall())
                    return true;
                if (rhs.tid().hasPoly()) {
                    if (!lhs.test(rhs.tid().polyParsed().orElse(null)))
                        return false;
                }
                if (lhs.isObjs() && lhs.stream().anyMatch(Obj::isObjCall)) // TODO: a hack (see RecTest requirements vs. TypeTest requirements)
                    return false;
                if (lhs.isObjs() && lhs.stream().allMatch(o -> o.test(rhs.asType().hasPredicate() ? rhs : rhs.tid(rhs.tid().c(o.c())))))
                    return true;
                if (rhs.asType().isBaseType() && !lhs.baseTypeID().test(rhs.tid()))
                    return false;
                return !rhs.asType().hasPredicate() || (!rhs.asType().predicate().apply(lhs.clone().selfTID(lhs.baseTypeID())).isNothing()); // selfTID() prevents infinite recursion on type checking
            } else {
                return lhs.test(rhs);
            }
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
            return this.predicate(instC(INST_PRED_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL_STAR)), predicate));
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
            return this.constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(T(ALL_STAR)), (lhs, inst) -> function.apply(inst.arg(0))));
        }

        public Builder constructor(final Supplier<Obj> supplier) {
            if (null == this.vid)
                throw MTronException.of("vid must be set prior to specifying constructor");
            return this.constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(), (lhs, inst) -> supplier.get()));
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
            this.insts.forEach(inst -> Router.global().write(inst.tid(), inst));
            return T(Tuple.Pair.with(this.predicate, this.constructor), this.tid, this.vid);
        }
    }
}