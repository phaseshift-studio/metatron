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

import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.selectRelRecursion;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Rel extends Poly<Rel, Tuple.Pair<Obj, Obj>>, MultMonoid.O<Rel>, PlusMonoid.O<Rel> {

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
    default Stream<Obj> keys() {
        return this.first().stream();
    }

    @Override
    default Stream<Obj> values() {
        return this.second().stream();
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
            final boolean singleSegment = key.uriValue().path().size() == 1;
            final String step = singleSegment ? key.uriValue().toString() : key.uriValue().path().getFirst();
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
                final fURI nextKey = key.uriValue().isBranch() ? key.uriValue().pretract(1).asBranch() : key.uriValue().pretract(1);
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

    @Override
    default <OBJ extends Obj> Stream<OBJ> valueElements() {
        return (Stream<OBJ>) this.second().stream();
    }


    /*default Type dom() {
        return this.value().getValue0().dom();
    }

    default Type rng() {
        return this.value().getValue1().rng();
    }*/

    ///////////////////////////////////////////////////////////////////////////
    // RING OPERATIONS
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Multiplicative identity: the identity relation id()
     * For any relation r, r.mult(one()) = r and one().mult(r) = r
     */
    @Override
    default Rel one() {
        return rel(instA(ID_INST_TID.dom(A).rng(A)), instA(ID_INST_TID.dom(A).rng(A)));
    }

    /**
     * Check if this relation is the multiplicative identity.
     * A relation is one if it's the identity relation id()=>id()
     */
    @Override
    default boolean isOne() {
        // Check if both domain and range are id() instructions
        // Use path() to get base TID without query parameters (dom/rng qualifiers)
        return this.jvm().get0().isObjInst() &&
                this.jvm().get1().isObjInst() &&
                this.jvm().get0().asInst().tid().path().equals(ID_INST_TID.path()) &&
                this.jvm().get1().asInst().tid().path().equals(ID_INST_TID.path());
    }

    /**
     * Additive identity: the zero relation (noobj=>noobj)
     * For any relation r, r.plus(zero()) = r
     */
    @Override
    default Rel zero() {
        return rel(noobj(), noobj());
    }

    /**
     * Check if this relation is the additive identity.
     * A relation is zero if both domain and range are noobj
     */
    @Override
    default boolean isZero() {
        return this.jvm().get0().isNoObj() && this.jvm().get1().isNoObj();
    }

    /**
     * Relation multiplication: composition of relations.
     * (a=>b).mult(b=>c) = (a=>c) when the range of first matches domain of second.
     * If they don't compose, returns noobj relation.
     */
    @Override
    default Rel mult(final Rel rhs) {
        // Handle identity cases first
        if (this.isOne()) return rhs;
        if (rhs.isOne()) return this;
        if (this.isZero() || rhs.isZero()) return this.zero();

        // Relation composition: (a=>b) × (b=>c) = (a=>c)
        // The range of this must match (or be compatible with) the domain of rhs
        if (this.jvm().get1().test(rhs.jvm().get0())) {
            // Compose: take domain from this, range from rhs
            // Use jvm().get0() and jvm().get1() directly to avoid autoResolve
            return rel(this.jvm().get0(), rhs.jvm().get1());
        } else {
            // Non-composable relations return zero
            return this.zero();
        }
    }

    /**
     * Additive inverse: swap domain and range.
     * -(a=>b) = (b=>a)
     * This makes relations form an additive group where negation is relation reversal.
     */
    // @Override
    default Rel neg() {
        return rel(this.second(), this.first());
    }

    /**
     * Addition: combine relations into an Objs collection.
     * (a=>b).plus(c=>d) = {(a=>b), (c=>d)}
     * Special case: zero + a = a (additive identity)
     */
    @Override
    default Rel plus(final Rel rhs) {
        // Handle additive identity: 0 + a = a and a + 0 = a
        if (this.isZero()) return rhs;
        if (rhs.isZero()) return this;
        // create objs collection from both relations
        return this;// objs(this, rhs);
    }

    /**
     * Subtraction: r1.minus(r2) = r1.plus(r2.neg())
     * Inherited from Ring interface.
     */
   /* @Override
    default Rel minus(final Rel r) {
        return Ring.O.super.minus(r);
    }*/

    public static final class RelType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(REL_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asRel().jvm().get0(), lhs.asRel().jvm().get1())),
                    instC(AS_INST_TID.dom(REL_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(lhs.asRel().jvm().get0(), lhs.asRel().jvm().get1())),
                    instC(MERGE_INST_TID.dom(REL_TID.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream().map(Obj::as), inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as)).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                    instC(SPLIT_INST_TID.dom(ALL).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> rel(Tuple.Pair.with(inst.arg(0).asRel().first().apply(lhs), inst.arg(0).asRel().second().apply(lhs)), inst.arg(0).tid(), null)),
                    //  instC(MERGE_INST_TID.dom(REL_TID).rng(ALL.c("2")), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(DOM_INST_TID.dom(REL_TID).rng(ALL.maybeSome()), lst(), (lhs, inst) -> lhs.relValue().get0()),
                    instC(RNG_INST_TID.dom(REL_TID).rng(ALL.maybeSome()), lst(), (lhs, inst) -> lhs.relValue().get1()),
                    //instC(LSHIFT_INST_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().first()),
                    // instC(RSHIFT_INST_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().second()),
                    // instC(RSHIFT_INST_TID.dom(REL_TID).rng(ALL_STAR), lst(T(ALL)), (lhs, inst) -> lhs.asRel().at(inst.arg(0))),
                    instC(GET_INST_TID.dom(REL_TID).rng(A.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.<Rel>as().at(inst.arg(0))),
                    instC(SELECT_INST_TID.dom(REL_TID).rng(REL_TID.maybe()), lst(T(REL_TID)), (lhs, inst) -> selectRelRecursion(lhs.asRel(), inst.arg(0).asRel())),
                    // Ring operations
                    instC(PLUS_INST_TID.dom(REL_TID).rng(REL_TID.maybeSome()), lst(T(REL_TID.maybeSome())), (lhs, inst) -> {
                        if (inst.arg(0).isObjs()) {
                            // Distributive addition: a + {b, c} = {a+b, a+c}
                            return objs(inst.arg(0).stream().map(Obj::<Rel>as).map(rhs -> {
                                // Handle identity directly to avoid recursion
                                if (lhs.asRel().isZero()) return rhs;
                                if (rhs.isZero()) return lhs.asRel();
                                return (Rel) objs(lhs, rhs);
                            }));
                        } else {
                            // Direct addition of two relations
                            if (lhs.asRel().isZero()) return inst.arg(0);
                            if (inst.arg(0).asRel().isZero()) return lhs.asRel();
                            return objs(lhs.asRel(), inst.arg(0).asRel());
                        }
                    }),
                    instC(MULT_INST_TID.dom(REL_TID).rng(REL_TID.maybeSome()), lst(T(REL_TID.maybeSome())), (lhs, inst) -> {
                        if (lhs.asRel().isZero())
                            return lhs.asRel().c(cInt.ZERO());
                        if (inst.arg(0).isObjs()) {
                            return objs(inst.arg(0).stream().map(Obj::<Rel>as).map(rhs -> {
                                if (rhs.isZero())
                                    return rhs.c(cInt.ZERO());
                                if (lhs.asRel().isOne())
                                    return rhs;
                                else if (rhs.isOne())
                                    return lhs;
                                return lhs.asRel().mult(rhs);
                            }));
                        } else {
                            if (lhs.asRel().isOne())
                                return inst.arg(0);
                            else if (inst.arg(0).asRel().isOne())
                                return lhs;
                            return lhs.asRel().mult(inst.arg(0).asRel());
                        }
                    }),
                    instC(NEG_INST_TID.dom(REL_TID).rng(REL_TID), lst(), (lhs, inst) -> lhs.asRel().neg()),
                    instC(ONE_INST_TID.dom(REL_TID).rng(REL_TID), lst(), (lhs, inst) -> lhs.asRel().one()),
                    instC(ZERO_INST_TID.dom(REL_TID).rng(REL_TID), lst(), (lhs, inst) -> lhs.asRel().zero())
            ));


        }
    }

    public static final class Helper {
        private Helper() {
            // do nothing
        }

        public static Obj rshiftRel(final Rel lhs, final Inst inst) {
            return inst.arg(0).isNoObj() ? lhs.asRel().second() : lhs.asRel().at(inst.arg(0));
        }
    }

}