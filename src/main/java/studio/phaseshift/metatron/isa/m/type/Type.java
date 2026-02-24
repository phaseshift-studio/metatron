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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Type extends Obj, PlusMonoid<Type> {

    Type TYPE_TYPE = T(f("T"));
    static final GraphittyLogger LOG = Graphitty.log(Type.class);

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
        return T(this.tid());
    }

    default String namedType() {
        return (null == this.vid() ? this.tid().name() : this.vid().name()) + "::T";
    }

    default boolean isBaseType() {
        return mInstSet.BASE_TYPES.contains(this.tid().basePath());
    }


    default Type parentType() {
        if (this.tid().equals(this.vid()))
            return null;
        return Router.global().read(this.tid().basePath()).asType();
    }

    default Call constructor() {
        return this.jvm().get1();
    }

    default Call predicate() {
        return this.jvm().get0();
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
        if (!this.c().within(rhs.c()))
            return false;
        // if(rhs.asType().parentType()!= null && !this.test(rhs.asType().parentType()))
        //     return false;
        if (rhs.asType().isBaseType())
            return this.baseType().test(rhs.tid()) && (!rhs.asType().hasPredicate() || Objects.equals(this.predicate(), rhs.asType().predicate())); // matches any abstract type to it's base type as long as within the coefficient boundaries
        if (rhs.tid().isGeneric())
            return !this.tid().isGeneric() || (this.c().within(rhs.c()) && this.tid().basePath().equals(rhs.tid().basePath()));
        return !rhs.asType().hasPredicate() || Objects.equals(this.asType().predicate(), rhs.asType().predicate());// || !rhs.asType().predicate().apply(this).isNoObj();
    }

    @Override
    default Obj apply(final Obj obj) {
        Obj parentType = this.parentType();
        if (null != parentType) {
            final Obj parentApply = parentType.apply(obj);
            if (parentApply.isNoObj())
                return noobj();
        }
        return null == this.predicate() || obj.test(predicate().apply(obj)) ?
                obj :
                noobj();
    }

    @Override
    default Type plus(final Type other) {
        if (this.isNoObj())
            return other;
        if (other.isNoObj())
            return this;
        final fURI tidPlus = this.tid().plus(other.tid());
        final Call constructor = null == this.constructor() ? other.constructor() : null == other.constructor() ? this.constructor() : this.constructor().plus(other.constructor());
        final Call predicate = null == this.predicate() ? other.predicate() : null == other.predicate() ? this.predicate() : this.predicate().plus(other.predicate());
        return this.clone(Tuple.Pair.with(constructor, predicate), tidPlus, tidPlus);
    }

    @Override
    default Type zero() {
        return this.tid(this.tid().zero()).jvm(Tuple.Pair.with(null, null));
    }

    final static class Helper {
        public static Obj typePredicateObj(final Type type) {
            if (type.hasPredicate() && type.predicate().insts().size() == 1 && type.predicate().insts().getFirst().tid().basePath().equals(ISA_INST_TID))
                return type.predicate().insts().getFirst().arg(0);
            return type.predicate();
        }
    }

    final class TypeType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    // instC(PLUS_INST_TID.dom(A).rng(B), lst(T(C)), (lhs, inst) -> lhs.jvm(ByteBuffer.wrap(Arrays.copyOfRange(lhs.bytesValue().array(), inst.arg(0).intValue().intValue(), lhs.bytesValue().array().length)))),
                    instC(RSHIFT_INST_TID.dom(BYTES_TID).rng(BYTES_TID), lst(isa_(T(BYTES_TID)).else_(jnt(1)).tryToInst()), (lhs, inst) -> lhs.jvm(ByteBuffer.wrap(Arrays.copyOf(lhs.bytesValue().array(), lhs.bytesValue().array().length - inst.arg(0).intValue().intValue())))),
                    instC(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> lhs.<Bytes>as().plus(inst.arg(0).as())),
                    instC(AS_INST_TID.dom(BYTES_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(new String(lhs.bytesValue().array(), StandardCharsets.UTF_8)))
                   /* instC(SUM_INST_TID.dom(BYTES_TID.maybeSome()).rng(BYTES_TID), lst(), (lhs,inst) -> lhs.elements().reduce(bytes(ByteBuffer.allocate((int)lhs.stream().count())),(a,b) -> bytes(a.bytesValue().put(b.bytesValue())))),
                    instC(SPLIT_INST_TID.dom(BYTES_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> {
                        final List<Bytes> list = new ArrayList<>();
                        byte[] bb = lhs.<Bytes>as().jvm().array();
                        for (byte b : bb) {
                            list.add(bytes(ByteBuffer.wrap(new byte[]{b})));
                        }
                        return lst((List)list);
                    })*/
            ));
        }
    }

    class Builder {

        public fURI vid = null;
        public fURI tid = null;
        public Call predicate = null;
        public Call constructor = null;
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

        public Builder predicate(final Call predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder predicate(final BiFunction<Obj, Inst, Obj> predicate) {
            return this.predicate(instC(INST_TID.dom(ALL.maybe()).rng(this.vid), lst(), predicate));
        }

        public Builder isaPredicate(final Poly<?, ?> predicate) {
            return this.predicate(isa_(predicate).tryToInst());
        }

        public Builder constructor(final Call constructor) {
            this.constructor = constructor;
            return this;
        }

        public Builder constructor(final Function<Obj, Obj> function) {
            return this.constructor(instC(INST_TID.dom(ALL.maybe()).rng(this.vid), lst(T(ALL)), (lhs, inst) -> function.apply(inst.arg(0))));
        }

        public Builder constructor(final Supplier<Obj> supplier) {
            return this.constructor(instC(INST_TID.dom(ALL.maybe()).rng(this.vid), lst(), (lhs, inst) -> supplier.get()));
        }

        public Builder inst(final fURI tid, final Poly<?, ?> args, final BiFunction<Obj, Inst, Obj> func) {
            this.insts.add(instC(tid, args, func));
            return this;
        }

        public Builder doc(final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            docWrap(this.insts.stream().toList().getLast(), domDesc, rngDesc, argDescription, description);
            return this;
        }

        public Type create(final Set<Type> typeSet, final Set<Inst> instSet) {
            LOG.info("installing %s type", this.vid);
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