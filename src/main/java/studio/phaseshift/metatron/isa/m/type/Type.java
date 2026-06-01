/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import static studio.phaseshift.metatron.Tokens.CTOR;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
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
        return this.vid() != null && (this.isRootType() || mInstSet.BASE_TYPES.contains(this.vid().basePath()));
    }

    default boolean isGeneric() {
        return (null != this.vid() && this.vid().isGeneric()) || this.tid().isGeneric();
    }

    default boolean isPattern() {
        return (null != this.vid() && this.vid().hasPattern()) || this.tid().hasPattern();
    }

    default boolean isRefinementOf(final Type other) {
        if (this == other || other.isRootType())
            return this.c().within(other.c());
        Type current = this;
        while (!current.isRootType()) {
            if (current.vid() != null && current.vid().equals(other.vid()))
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
        if (rhs.isType() && rhs.asType().isRootType() && !rhs.asType().hasPredicate())
            return this.c().within(rhs.c());
        if (null != this.vid() &&
                this.vid().test(rhs.vid()) &&
                (!rhs.asType().hasPredicate() || (Objects.equals(this.predicate(), rhs.asType().predicate()))))
            return this.c().within(rhs.c());
        if (!this.c().within(rhs.c()))
            return false;
        if (!rhs.asType().parentType().isRootType() && !this.test(rhs.asType().parentType()))
            return false;
        if (rhs.asType().isBaseType() && !this.baseType().test(rhs.tid()))
            return false;//&& (!rhs.asType().hasPredicate() || Objects.equals(this.predicate(), rhs.asType().predicate())); // matches any abstract type to it's base type as long as within the coefficient boundaries
        if (rhs.tid().isGeneric())
            return !this.tid().isGeneric() || (this.c().within(rhs.c()) && this.tid().basePath().equals(rhs.tid().basePath()));
        return !rhs.asType().hasPredicate() || Objects.equals(this.asType().predicate(), rhs.asType().predicate());// || !rhs.asType().predicate().apply(this).isNoObj();
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
            final cInt lcdC = types.stream().map(Type::c).reduce(cInt::plus).orElse(cInt.ONE());
            // Walk the chain outward; the first ancestor that ALL types share is the LCD
            for (final Type candidate : chain) {
                if (types.stream().allMatch(type -> type.isRefinementOf(candidate)))
                    return candidate.c(lcdC).as();
            }
            // No common ancestor — types are from disjoint hierarchies; fall back to the universal type
            return ALL_TYPE.c(lcdC).as();
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
                if (rhs.isType() && lhs.tid().hasPoly() && rhs.tid().hasPoly()) {
                    if (!lhs.tid().polyParsed().orElse(lst()).test(rhs.tid().polyParsed().orElse(lst())))
                        return false;
                }
                if (!lhs.test(rhs.asType().parentType()))
                    return false;
                if (lhs.asType().isBaseType())
                    return lhs.baseType().test(rhs.tid()) &&
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
                if (lhs.isObjs() && lhs.stream().allMatch(o -> o.test(rhs.tid(rhs.tid().c(o.c())))))
                    return true;
                if (rhs.asType().isBaseType() && !lhs.baseType().test(rhs.tid()))
                    return false;
                return !rhs.asType().hasPredicate() || !rhs.asType().predicate().apply(lhs.clone().selfTID(lhs.baseType())).isNoObj(); // selfTID() prevents infinite recursion on type checking
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
            return this.predicate(instC(INST_PRED_TID.dom(ALL.maybe()).rng(this.vid), lst(), predicate));
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
            return this.constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(this.vid), lst(T(this.tid)), (lhs, inst) -> function.apply(inst.arg(0))));
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