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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.selectRelRecursion;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Rel extends Poly<Rel, Tuple.Pair<Obj, Obj>>, Obj {

    Type REL_TYPE = Type.Builder.build().tid(REL_TID).vid(REL_TID).create();

    @Override
    Rel clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Pair<Obj, Obj> jvm();

    @Override
    default boolean isResolved(final boolean nested) {
        return this.jvm().get0().isResolved(nested) && this.jvm().get1().isResolved(nested);
    }

    @Override
    default long count() {
        return 2;
    }

    /// /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////

    default Obj first() {
        return (this.c().isOne() ? this.jvm().get0() : this.jvm().get0().c(c -> c.mult(this.c()))).autoResolve(this);
    }

    default Obj second() {
        return (this.c().isOne() ? this.jvm().get1() : this.jvm().get1().c(c -> c.mult(this.c()))).autoResolve(this);
    }

    default Rel first(final Obj key) {
        return this.jvm(Pair.with(key, this.jvm().get1()));
    }

    default Rel second(final Obj value) {
        return this.jvm(Pair.with(this.jvm().get0(), value));
    }

    @Override
    default boolean has(final Obj key) {
        return this.jvm().get0().test(key);
    }
    
    
    /*@Override
    default Rel autoResolve(final Obj obj) {
        return this.first(this.first().autoResolve(obj)).second(this.second().autoResolve(obj));
    }*/

    default <O extends Obj> O at(final Obj key) {
        if (key.isUri()) {
            final boolean singleSegment = key.uriValue().segments().size() == 1;
            final String step = singleSegment ? key.uriValue().toString() : key.uriValue().segments().getFirst();
            O result;
            final Uri asNode = uri(key.uriValue().asNode());
            if (this.jvm().get0().test(asNode))
                return (O) (key.uriValue().isBranch() ? rel(asNode, this.jvm().get1()) : this.jvm().get1()).autoResolve(this);
            else {
                final Obj temp = (this.jvm().get0().test(uri(f(step).asNode())) ? this.jvm().get1() : NoObj.noobj()).autoResolve(this);
                result = (O) (key.uriValue().isBranch() ? rel(key.uriValue().asNode().toUri(), temp) : temp);
            }
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
            if (singleSegment) {
                return result;
            } else {
                final fURI nextKey = key.uriValue().isBranch() ? key.uriValue().pretract().asBranch() : key.uriValue().pretract();
                return (O) (this.jvm().get1().isPoly() ? this.jvm().get1().<Poly>as().at(uri(nextKey)) : noobj());
            }
        } else {
            return (O) (this.jvm().get0().test(key) ? this.jvm().get1() : noobj());
        }
    }

    /*@Override
    default <O extends Obj> O at(final Obj key) {
        return (O) (this.first().matches(key) ? this.second() : noobj());
    }*/

    @Override
    default Rel at(final Obj first, final Obj second, final BiFunction operation) {
        return (Rel) operation.apply(this, Pair.with(first, second));
    }

    @Override
    default <O extends Obj> Stream<O> elements() {
        return Stream.of(this.jvm().get0().c(c -> c.mult(this.c())).as(), this.jvm().get1().c(c -> c.mult(this.c())).as());
    }


    /*default Type dom() {
        return this.value().getValue0().dom();
    }

    default Type rng() {
        return this.value().getValue1().rng();
    }*/

    public static final class RelType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(REL_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asRel().jvm().get0(), lhs.asRel().jvm().get1())),
                    instC(AS_INST_TID.dom(REL_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(lhs.asRel().jvm().get0(), lhs.asRel().jvm().get1())),
                    instC(MERGE_INST_TID.dom(REL_TID.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream().map(Obj::as), inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as)).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                    //  instC(MERGE_INST_TID.dom(REL_TID).rng(ALL.c("2")), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(DOM_INST_TID.dom(REL_TID).rng(ALL), lst(), (lhs, inst) -> lhs.relValue().get0()),
                    instC(RNG_INST_TID.dom(REL_TID).rng(ALL.some()), lst(), (lhs, inst) -> lhs.relValue().get1()),
                    instC(LSHIFT_INST_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().first()),
                    instC(RSHIFT_INST_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().second()),
                    instC(GET_INST_TID.dom(REL_TID).rng(A.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.<Rel>as().at(inst.arg(0))),
                    instC(SELECT_INST_TID.dom(REL_TID).rng(REL_TID.maybe()), lst(T(REL_TID)), (lhs, inst) -> selectRelRecursion(lhs.asRel(), inst.arg(0).asRel()))
            ));


        }
    }

}