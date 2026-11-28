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


import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.impl.MFiberedRec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ProjectionFailureException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.ObjFactory.LOG;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.immutableMap;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public interface Rec extends Poly<Rec, Map<Obj, Obj>> {

    Rec EMPTY_REC = rec(immutableMap());
    // Rec with coefficient {0}: noobj typed as a Rec, for a method that answers a Rec or nothing.
    // cast, not asRec(): asRec() answers a zero with noobjRec(), i.e. this field, which is still null while it initializes
    Rec REC_NOOBJ = (Rec) rec(immutableMap()).c(cInt.ZERO());

    static Rec rec_noobj() {
        return REC_NOOBJ;
    }

    @Override
    default Stream<Rel> indexedStream() {
        return this.jvm().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(this.c()).as());
    }

    @Override
    default Rec zero() {
        return rec();
    }

    @Override
    Rec clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Map<Obj, Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    @Override
    default Stream<Rel> elements() {
        return this.recValue().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(c -> c.mult(this.c())).as());
    }

    @Override
    default <OBJ extends Obj> Stream<OBJ> valueElements() {
        return this.elements().map(r -> (OBJ) r.second());
    }

    @Override
    default <O extends Obj> O jvm(final Object jvm) {
        return (O) this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Stream<Obj> values() {
        return this.elements().map(Rel::second);
    }

    @Override
    default Stream<Obj> keys() {
        return this.elements().map(Rel::first);
    }

    @Override
    default boolean test(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        if (rhs.isRec()) {
            return rhs.asRec().elements().allMatch(r -> {
                // a value is tested only under a key that matched: pairing it with every value of rhs ran unrelated
                // predicates on it (an instset tested against itself ran percent::T's ?>=0 on its docq), and a
                // predicate that runs as code is an evaluation, which files its miss. A MISSING KEY DOES NOT MATCH A
                // VALUE PATTERN: only an optional key may be absent, which the zeroable checks below decide -- a rec
                // without `services` is not a compose, and a rec without `b` is not a `[b=>2]`.
                final boolean found = this.elements()
                        .anyMatch(l -> l.jvm().get0().test(r.jvm().get0()) && l.jvm().get1().test(r.jvm().get1()));
                if (found) return true;
                boolean notFound = r.jvm().get0().c().isZeroable() && this.elements().noneMatch(l -> l.jvm().get0().test(r.jvm().get0()));
                if (notFound) return true;
                final Obj thisValue = this.at(r.jvm().get0()); // can't make this jvm()-based
                return (thisValue.isNoObj() && r.jvm().get0().c().isZeroable()) || thisValue.test(r.jvm().get1());
            });
        } else {
            return Poly.super.test(rhs);
        }
    }

    default Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        if (key.isUri()) {
            final fURI k = key.uriValue().resolve();
            if (k.path().isEmpty())
                return this;
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.compute(uri(k.path().getFirst()), (k1, v) ->
                    k.path().size() == 1 ?
                            (value.isNoObj() ? null : (null != v && v.isObjs() ? v.append(value.parent(this)) : value.parent(this))) :
                            (null != v && v.isPoly()
                                    ? v.<Poly<?, ?>>as().at(k.pretract(1).toUri(), value.parent(this), operation)
                                    : rec().at(k.pretract(1).toUri(), value.parent(this), operation)));
            final Poly<?, ?> applied1 = operation.apply(this, map);
            if (applied1 instanceof Fail fail)
                throw MTronException.of("rec at(%s): operation failed: %s", key, fail.message());
            return (Rec) applied1;
        } else {
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.put(key, value);
            final Poly<?, ?> applied2 = operation.apply(this, map);
            if (applied2 instanceof Fail fail)
                throw MTronException.of("rec at(%s): operation failed: %s", key, fail.message());
            return (Rec) applied2;
        }
    }

    @Override
    default <OBJ extends Obj> OBJ at(final Obj key) {
        return Rec.Helper.atToggle(this, key, true);
    }

    @Override
    default <O extends Obj> O atDirect(final Obj key) {
        return Rec.Helper.atToggle(this, key, false);
    }

    default Rec plus(final Rec rhs) {
        return Poly.Helper.mergeObjRecursion(this, rhs).asRec();
    }

    default Rec minus(final Rec rhs) {
        return this.plus(rhs.neg());
    }

    default Rec neg() {
        return this.elements().map(rkv -> rel(rkv.first(), rkv.second().c(C::inv))).collect(new CommonUtil.RecCollector(this.tid(), this.vid()));
    }

    /**
     * THE WALK JOIN (Marko, 2026-11-24): a rec is a sum of walks, and the product joins each walk of this rec to each
     * walk of the argument where the first ends at an object the second's key meets, KEEPING the middle:
     * {@code [a=>b]·[b=>c]} is {@code [a=>(b=>c)]}. A walk is the chain of objects its arrow steps through: a rel range
     * steps on (a nested rel is a path, 2026-11-13), a rec range steps through each of its keys ({@code [a=>[b=>1]]}
     * holds the walk {@code a=>b=>1}, the trie reading the merge keeps), and any other range ends the walk, CODE
     * INCLUDED: a range is a value and is never run, so {@code [a=>5]·[_=>plus(1)]} is {@code [a=>(5=>plus(1))]}. A
     * span is the object's, never the walk's: {@code <b>{5}} is the one uri {@code b/b/b/b/b}. A walk reads the argument
     * at its end BY ADDRESS, as at()/atDirect() read ({@link Helper#steps}; Marko, 2026-11-24), so flat, path-keyed and
     * nested arguments join alike: {@code [x=>a]·[a/b=>1]} is {@code [x=>(a=>(b=>1))]}. Only an acting key (code, a
     * type) meets an end it answers ({@code ?>1} meets 5 and not 0), and the identity {@code _} meets every end. As
     * {@code =>} nests to the right, the join splices at the innermost range, so joined walks concatenate and the
     * product is associative: the path algebra of the keys' graph. An identity term {@code _=>_} adds no step, so the
     * other term passes as it is, scaled, and {@code [_=>_]} is the unity ({@link #one()}); the empty rec, with no walk
     * to join, is the zero. The ring is that of the recs the merge normalizes (no empty sub-rec, a key's recs one rec):
     * on a literal that holds an empty sub-rec, {@code [a=>[=>]]·[_=>_]} is its normal form {@code [=>]}.
     * <p>
     * MULTIPLICITIES ARE POINTS, and they multiply onto the joined walk's outermost range, never onto a key. A rec's own
     * coefficient is read as the merge reads it: this rec's is the stream's and stays on the product, and the argument's
     * is its copies' and multiplies onto the walks it joins, so {@code [a=>1]·{2}[1=>c]} is {@code [a=>{2}(1=>c)]}. The
     * product is bilinear and distributes over the merge of recs whose multiplicities sit on their values. A bulked lhs
     * of the merge is the stream's over the whole sum ({@code {2}y + z} is {@code {2}(y + z)}), so a bulked summand
     * counts both walks: {@code [a=>b]·({2}[b=>c] + [b=>d])} is {@code [a=>{{2}(b=>c),{2}(b=>d)}]}.
     */
    default Rec mult(final Rec rhs) {
        // the argument at point one: its own coefficient is its copies, multiplied onto every walk it joins
        final Rec right = rhs.d(cInt.ONE()).asRec();
        final cInt copies = rhs.tid().d();
        final List<Rel> nexts = right.elements().toList();
        final List<Rel> acting = nexts.stream()
                .filter(next -> next.firstDirect().isObjCall() || next.firstDirect().isType())
                .toList();
        return this.d(cInt.ONE()).asRec().elements().flatMap(arrow -> arrow.secondDirect().stream().flatMap(value -> {
            if (rel(arrow.firstDirect(), value).isOne())
                return nexts.stream().flatMap(next -> next.secondDirect().stream().map(range ->
                        rec(next.firstDirect(), range.d(range.tid().d().mult(copies).mult(value.tid().d())))));
            final Stream<Rec> passed = acting.stream().flatMap(next -> next.secondDirect().stream()
                    .filter(range -> rel(next.firstDirect(), range).isOne())
                    .map(range -> rec(arrow.firstDirect(), value.d(value.tid().d().mult(copies).mult(range.tid().d())))));
            final Stream<Rec> joined = Helper.walks(value).flatMap(walk -> {
                final Obj unit = walk.d(cInt.ONE());
                return Helper.steps(nexts, Helper.end(unit)).map(step -> rec(arrow.firstDirect(),
                        Helper.spliced(unit, step.d(cInt.ONE())).d(copies.mult(walk.tid().d()).mult(step.tid().d()))));
            });
            return Stream.concat(passed, joined);
        })).reduce(this.zero(), Rec::plus).d(this.tid().d()).asRec();
    }

    /**
     * THE UNITY OF THE WALK JOIN: the identity arrow {@code _=>_}, the walk of length zero at every object, as a rec,
     * {@code [_=>_]}, spelled as {@code _} parses (an {@code id()} of no args), so it is the rec ring's declared one.
     * {@link Rel#one()} spells its {@code id()} over {@code A}, and the join reads either as the identity, since
     * {@link Rel#isOne()} compares paths.
     */
    default Rec one() {
        final Obj id = instB(ID_INST_TID, lst());
        return rec(id, id);
    }

    @Override
    default Rec vid(final fURI vid) {
        return (Rec) Poly.super.vid(vid);
    }

    @Override
    default Rec tid(final fURI tid) {
        return (Rec) Poly.super.tid(tid);
    }

    @Override
    default Rec selfVID(final fURI vid) {
        return (Rec) Poly.super.selfVID(vid);
    }

    @Override
    default Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (this.isNoObj())
            return obj;
        return objs(this, obj);
    }

    @Override
    Rec self(final Object jvm, final fURI tid, final fURI vid);

    static <T> T wrap(final Obj obj, final Class<T> t) {
        if (null == obj || obj.isNoObj() || !obj.isRec())
            throw MTronException.of("%s is not a rec::T", obj);
        if (t.isAssignableFrom(obj.getClass()))
            return (T) obj;
        try {
            return t.getConstructor(Map.class, fURI.class, fURI.class).newInstance(new LinkedHashMap<>(obj.jvm()), obj.tid(), obj.vid());
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    final class Helper {
        public Helper() {
            // do nothing
        }

        /**
         * THE WALKS A RANGE HOLDS ({@link Rec#mult(Rec)}), each the chain of objects it steps through as a nested rel, with its
         * multiplicity on the outermost and every object inside at point one. A rel steps from its key to the walks of
         * its range, and a rec of walks ({@link #holdsWalks}) steps through each of its keys to the walks of that key's value.
         * Any other object, code and a live rec included, ends the walk. The empty rec holds no walk.
         */
        static Stream<Obj> walks(final Obj range) {
            return range.stream().flatMap(member -> {
                final Obj unit = member.d(cInt.ONE());
                if (!unit.isRel() && !holdsWalks(unit))
                    return Stream.of(member);
                return (unit.isRel() ? Stream.of(unit.asRel()) : unit.asRec().elements())
                        .flatMap(step -> walks(step.secondDirect()).map(rest ->
                                rel(step.firstDirect(), rest.d(cInt.ONE())).d(member.tid().d().mult(rest.tid().d()))));
            });
        }

        /**
         * WHETHER AN OBJ IS A REC OF WALKS, the walks through its keys ({@link #walks}, and the merge's
         * {@code Poly.Helper.withWalks}): a plain rec, or a fibered bundle. A live one (a space, a machine, a socket, any
         * component built on a rec) is an atom to both walks, read where it lives; two live recs that collide at one
         * key still merge as recs, as they always have.
         */
        static boolean holdsWalks(final Obj obj) {
            return obj.isRec() && (MRec.class == obj.getClass() || obj instanceof MFiberedRec);
        }

        /**
         * THE WALKS THE ARGUMENT HOLDS AT AN END ({@link Rec#mult(Rec)}), term by term, so terms that overlap each join
         * and the product stays bilinear. A term whose key is a value is read at a uri end by address, as at()/atDirect()
         * read (Marko, 2026-11-24): an exact key, a path, an interior subtree, a stored pattern, so a term's layout does
         * not matter. An end that is no uri has no address, and meets a value key it tests as. A term whose key acts
         * (code, a type) meets an end the key answers. The identity {@code _} answers every end, code included: the
         * processor resolves no {@code id()} on an instruction, so {@code [a=>plus(1)]·[_=>x]} joins by this clause
         * alone. An end that is code meets only a key that acts: {@code count()} answers an instruction, while
         * {@code neg()}, {@code plus(1)} and {@code ?>1} do not. An identity term is passed whole by the product, never
         * joined.
         */
        static Stream<Obj> steps(final List<Rel> nexts, final Obj end) {
            return nexts.stream().flatMap(next -> {
                final Obj key = next.firstDirect();
                if (key.isObjCall() || key.isType())
                    return isIdentity(key) || !key.apply(end).isNothing()
                            ? next.secondDirect().stream().filter(range -> !rel(key, range).isOne()).flatMap(Helper::walks)
                            : Stream.empty();
                if (end.isObjCall())
                    return Stream.empty();
                if (end.isUri())
                    return walks(rec(key, next.secondDirect()).atDirect(end));
                return end.test(key) ? walks(next.secondDirect()) : Stream.empty();
            });
        }

        /**
         * A REC'S SPAN IS ITS POWER under the walk join (Marko, 2026-11-24: record spans, Claim 18 of
         * ring-theoretic-distributed-structures.md), as a uri's span is its power: {@code r{n}} is the join of n copies of
         * r, {@code r{0}} the unity [_=>_], and a negative span a power of the converse ({@link #converse}), so
         * {@code [a=>b]{-1}} is {@code [b=>a]}. A span range is the sum of its powers: {@code {1,2}} is r + r·r. An open
         * side sums its powers until one holds no walk, so {@code [a=>b,b=>c]{+}}, the transitive closure, is
         * {@code [a=>{b,(b=>c)},b=>c]}. A finite span past the typer's unfold bound for recs fails, and so does an open
         * one with a power past the bound that holds a walk, as a cycle's closure does, and every power past the first
         * is bounded by the copies of the rec's own walks it holds as well, since a cycle with a branch doubles them at
         * every power, and so is the whole span a range realizes, its two sides of zero together. The parser realizes a
         * literal's span here, so a rec literal never holds one. A power is the
         * product's, so code the rec holds as a value joins as the code it is, as in r·r: a range is a value, never run,
         * and a literal's code stays code when the rec is applied ({@code [a=>*x,b=>c]{2}} is {@code [=>]}, as
         * {@code [a=>*x,b=>c] * [a=>*x,b=>c]} is). A key that acts runs as the join tests the ends it meets, and the
         * parser runs no code, so a span is refused over a rec with a key that is code (the identity aside), a type that
         * is no plain base type (its predicate runs, {@code nat::T}'s {@code ?>=0} on every end, and may read memory
         * before the statements ahead of it have run), or a poly (it tests an end with its entries, code among them).
         */
        public static Rec power(final Rec rec, final cInt span) {
            rec.d(cInt.ONE()).asRec().elements().map(Rel::firstDirect)
                    .filter(key -> (key.isObjCall() && !isIdentity(key)) || key.isPoly()
                            || (key.isType() && (key.asType().hasPredicate() || !key.asType().isBaseType())))
                    .findFirst()
                    .ifPresent(key -> {
                        throw MTronException.of("a rec's span is realized as it is parsed, and its key %s would run as the join tests the ends it meets", key);
                    });
            final Long low = span.min();
            final Long high = span.max();
            final Rec ahead = null != high && high < 0L ? rec() : powers(rec, null == low || low < 0L ? 0L : low, high);
            final Rec behind = null != low && low >= 0L ? rec()
                    : powers(converse(rec), null == high || high >= 0L ? 1L : -high, null == low ? null : -low);
            // each side bounds its own sum, and a range across zero sums both: the span is bounded as one
            final Rec spanned = ahead.plus(behind);
            Typer.Helper.unfolds(Typer.UNFOLD_REC, Math.ceilDiv(walks(spanned).count(), Math.max(1L, walks(rec).count())));
            return spanned;
        }

        /**
         * the sum of base^k for k from {@code from} to {@code to} ({@link #power}), {@code to} null running until a power
         * holds no walk (every later one holds none). A finite span is bounded once, by its largest power, and an open
         * one by each power that holds a walk, so the empty power that ends a closure is never refused. Every power past
         * the first is bounded by its walks too, counted in copies of the base's: a power holds at most the bound's
         * multiple of them, so the square of a chain, never larger than the chain, passes, while a cycle with a branch,
         * which doubles its walks at every power, is refused within a few powers. So is the sum this side of a range
         * builds, however many powers it sums; {@link #power} bounds the two sides of zero together
         */
        private static Rec powers(final Rec base, final long from, final Long to) {
            if (null != to)
                Typer.Helper.unfolds(Typer.UNFOLD_REC, to);
            final long size = Math.max(1L, walks(base).count());
            long held = 0L;
            Rec sum = rec();
            Rec term = base.one();
            for (long k = 0L; null == to || k <= to; k++) {
                if (k > 0L) {
                    term = term.mult(base);
                    if (term.isEmpty())
                        break;
                    if (null == to)
                        Typer.Helper.unfolds(Typer.UNFOLD_REC, k);
                }
                final long walks = walks(term).count();
                if (k > 1L)
                    Typer.Helper.unfolds(Typer.UNFOLD_REC, Math.ceilDiv(walks, size));
                if (k >= from) {
                    sum = sum.plus(term);
                    held += walks;
                    Typer.Helper.unfolds(Typer.UNFOLD_REC, Math.ceilDiv(held, size));
                }
            }
            return sum;
        }

        /**
         * THE CONVERSE OF A REC, its span -1 (Claim 18): each walk reversed, its multiplicity kept, so
         * {@code [a=>(b=>c)]{-1}} is {@code [c=>(b=>a)]}, and the converse reverses a join: (r·s)^-1 is s^-1·r^-1. The
         * unity is its own converse. It reverses a walk letter by letter ({@link #letter}), so a walk through code, a
         * type, a pattern, or a path of more than one segment has no converse a rec can list. A nested rec's converse is
         * its walks reversed, so its converse's converse is its walks, the same structure written as walks:
         * {@code [a=>[b=>c]]} comes back as {@code [a=>(b=>c)]}. This rec's own coefficient is the stream's, as in the
         * product.
         */
        public static Rec converse(final Rec rec) {
            return rec.d(cInt.ONE()).asRec().elements().flatMap(arrow -> arrow.secondDirect().stream().flatMap(value -> {
                if (rel(arrow.firstDirect(), value).isOne())
                    return Stream.of(rec(arrow.firstDirect(), value));
                return walks(value).map(walk -> {
                    final Rel back = reversed(walk.d(cInt.ONE()), letter(arrow.firstDirect()));
                    return rec(letter(back.firstDirect()), back.secondDirect().d(walk.tid().d()));
                });
            })).reduce(rec(), Rec::plus).d(rec.tid().d()).asRec();
        }

        /**
         * a walk reversed onto what came before it ({@link #converse}): its end becomes the key, and the rest runs back
         * to {@code back}, each object it moves a letter
         */
        private static Rel reversed(final Obj walk, final Obj back) {
            return walk.isRel() ? reversed(walk.asRel().secondDirect(), rel(letter(walk.asRel().firstDirect()), back)) : rel(walk, back);
        }

        /**
         * an object the converse can move ({@link #converse}): a letter, a mono a join meets only at itself. Code and a type
         * meet every object they answer, a poly every end its entries test as (a lst key holding b/+ meets the end
         * [b/c], while [b/+] meets no key [b/c]), a pattern every path it matches, and a path of more than one segment the
         * paths through it and under it, which its own segments, reversed, never meet, so a walk through any of them has
         * no converse a rec can list: the law (r·s)^-1 = s^-1·r^-1 and flat, path-keyed and nested layouts would disagree
         */
        private static Obj letter(final Obj obj) {
            if (obj.isObjCall() || obj.isType() || obj.isPoly())
                throw MTronException.of("a walk through code, a type or a poly has no converse a rec can list: %s", obj);
            if (obj.isUri() && (1 != obj.uriValue().segmentLength() || !obj.uriValue().isRelative() || obj.uriValue().isBranch()
                    || obj.uriValue().hasPattern() || obj.uriValue().hasScheme() || obj.uriValue().hasAuthority()))
                throw MTronException.of("a walk through a path or a pattern has no converse a rec can list: %s", obj);
            return obj;
        }

        /**
         * the identity {@code _}, matched by its path, so {@link Rel#one()}'s id over A counts too ({@link #steps},
         * {@link #power})
         */
        static boolean isIdentity(final Obj obj) {
            return obj.isObjCall() && obj.asCall().tryToInst().tid().path().equals(ID_INST_TID.path());
        }

        /**
         * a walk's last object ({@link Rec#mult(Rec)})
         */
        static Obj end(final Obj walk) {
            return walk.isRel() ? end(walk.asRel().secondDirect()) : walk;
        }

        /**
         * a walk at point one with a step after its last object ({@link Rec#mult(Rec)})
         */
        static Obj spliced(final Obj walk, final Obj step) {
            return walk.isRel() ? rel(walk.asRel().firstDirect(), spliced(walk.asRel().secondDirect(), step)) : rel(walk, step);
        }

        public static Obj typeToRec(final Obj maybeType, final Supplier<Obj> notType) {
            if (maybeType.isType()) {
                final Type type = maybeType.asType();
                return rec(
                        uri(TID), uri(type.tid()),
                        uri(VID), uri(type.vid()),
                        uri(PRED), !type.hasPredicate() ? noobj() : type.isIsaPredicate() ?
                                rec(uri(CODE), type.predicate(), uri(ARGS), Type.Helper.typePredicateObj(type)) :
                                rec(uri(CODE), type.predicate()),
                        uri(CTOR), type.hasConstructor() ? type.constructor() : noobj());
            }
            return notType.get();
        }

        public static <OBJ extends Obj> OBJ atToggle(final Rec arec, final Obj key, final boolean doAuto) {
            if (!key.isUri())
                return autoToggle(arec, arec.jvm().getOrDefault(key, NoObj.noobj()), doAuto);
            else {
                //if (key.uriValue().isEmpty())
                //   return this.c(c -> c.mult(key.c())).as();
                final fURI keyF = key.uriValue().resolve();
                if (keyF.segmentLength() == 0)
                    return (OBJ) noobj();
                final boolean singleSegment = keyF.segmentLength() == 1;
                final String step = singleSegment ? keyF.asNode().toString() : keyF.path().getFirst();
                Obj result;
                final Uri asNode = uri(keyF.asNode());
                final cInt cKey = key.c();
                final boolean isBranch = keyF.isBranch();
                if (step.equals("..")) {
                    // UP: the ancestor keeps its own link. The re-parenting below would point it back down at arec
                    final Obj up = arec.parent();
                    return singleSegment ? up.c(c -> c.mult(cKey)).as()
                            : (OBJ) (up.isPoly() ? up.<Poly<?, ?>>as().at(uri(isBranch ? keyF.pretract(1).asBranch() : keyF.pretract(1))) : noobj());
                } else if (step.equals("+") || step.equals("#")) {
                    // a trailing '/' asks for rels at the LAST step only: an earlier step hands its values on, and the
                    // walk below carries the branch to the step it belongs to (z/+/ is z's entries, not z as a rel)
                    result = objs(isBranch && singleSegment ?
                            arec.jvm().entrySet().stream().map(e -> rel(
                                            autoToggle(arec, e.getKey(), doAuto),
                                            autoToggle(arec, e.getValue(), doAuto)))
                                    .map(o -> o.c(c -> c.mult(cKey))) :
                            arec.jvm().values().stream().map(obj -> (Obj) autoToggle(arec, obj, doAuto))
                                    // a step that hands its values on hands each in the merge's normal form
                                    // (Poly.Helper.normal); a path key the wildcard would match is not read here
                                    // (open: [a=>[b=>[x=>1]],a/b=>[y=>2]]>><+/b> is [x=>1], where a/b reads [x=>1,y=>2])
                                    .map(o -> singleSegment ? o : Poly.Helper.normal(o))
                                    .map(o -> o.c(c -> c.mult(cKey))));
                } else {
                    // A READ IS LINEAR (Claim 4 of ring-theoretic-distributed-structures.md), and flat, path-keyed and
                    // nested recs are one term (Marko, 2026-11-24: [a/b=>35]>>a is [b=>35]), so an address without a
                    // pattern reads every key it meets, and the reads add as the terms do (Poly.Helper.added): the key it
                    // names; each key it runs through, the rest read below it ([a=>[x=>1],a/b=>[c=>2]]>>(a/b/c) is 2, as
                    // [a/b=>[c=>2]]>>(a/b/c) is); and the keys under it, its subtree, a value among them kept (Marko,
                    // 2026-11-25: [a=>5,a/b=>6]>>a is {5,[b=>6]}). Below a key the rest reads the key's value in the
                    // merge's normal form (Marko, 2026-11-25, "keep the sum"; Poly.Helper.readsBelow), so
                    // [a=>{[b=>[x=>1]],[b=>[y=>2]]}] reads a/b as [x=>1,y=>2], as [a/b/x=>1,a/b/y=>2] does. A trailing
                    // '/' names the relation of the whole read, x/ being x=>*x. A key whose rest past the address is a
                    // pattern is a pattern key ([b/#=>x]>>b is x), and pattern keys answer only when no key answers the
                    // address, so a key that names the address overrides a pattern. An address holding a pattern past its
                    // first segment reads below each key the same way, and its reads gather, as values at the addresses
                    // they match (open: two keys that reach one matched address gather rather than add)
                    final int length = keyF.segmentLength();
                    final boolean whole = isBranch && !keyF.hasPattern();
                    final List<Obj> reads = new ArrayList<>();
                    final List<Space.IdObj> under = new ArrayList<>();
                    final Obj named = arec.jvm().get(asNode);
                    if (null != named)
                        reads.add(isBranch && !whole ? rel(asNode, autoToggle(arec, named, doAuto)) : autoToggle(arec, named, doAuto));
                    arec.jvm().forEach((k, v) -> {
                        // a key is read only where it can meet the address, so the usual rec, its keys and its address of
                        // one segment each and its values monos, is a loop of field reads (the resolver reads recs so)
                        if (!k.isUri() || k.uriValue().segmentLength() == length)
                            return;
                        final fURI kF = k.uriValue();
                        if (kF.segmentLength() < length && (v.isPoly() || v.isObjs() || v.isObjCall()) && Space.Helper.runsThrough(kF, keyF)) {
                            // the raw toggle runs the whole path: atDirect stays raw below the key, as on a flat key
                            final Uri rest = uri(isBranch && !whole ? keyF.pretract(kF.segmentLength()).asBranch() : keyF.pretract(kF.segmentLength()).asNode());
                            reads.addAll(Poly.Helper.readsBelow(autoToggle(arec, v, doAuto), arec,
                                    poly -> doAuto ? poly.at(rest) : poly.atDirect(rest)));
                        } else if (kF.segmentLength() > length && Space.Helper.runsThrough(keyF, kF) && !kF.pretract(length).hasPattern())
                            under.add(new Space.IdObj(kF.pretract(length), v));
                    });
                    if (!under.isEmpty()) {
                        final Obj subtree = Space.Helper.sum(under);
                        reads.add(isBranch && !whole ? rel(asNode, subtree) : subtree);
                    }
                    final Obj sum = keyF.hasPattern() ? objs(reads.stream().flatMap(Obj::stream)) : Poly.Helper.added(reads);
                    // a sum whose terms cancel answered the address; only an address no key answers falls to the patterns
                    if (sum.isNoObj() && reads.stream().allMatch(Obj::isNoObj))
                        return (OBJ) objs(arec.jvm().entrySet()
                                .stream()
                                .filter(kv -> kv.getKey().isUri())
                                .filter(kv -> kv.getKey().uriValue().bimatches(asNode.uriValue()))
                                .map(kv -> isBranch ? rel(
                                        kv.getKey(), autoToggle(arec, kv.getValue(), doAuto)) :
                                        autoToggle(arec, kv.getValue(), doAuto)));
                    return (OBJ) (whole ? rel(asNode, sum) : sum).c(c -> c.mult(cKey));
                }
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
                if (singleSegment) {
                    return result.parent(arec).c(c -> c.mult(cKey)).as();
                } else {
                    final fURI nextKey = isBranch ? keyF.pretract(1).asBranch() : keyF.pretract(1);
                    // the raw toggle runs the whole path: atDirect stays raw past the first hop, as on a flat key
                    return (OBJ) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(o -> o.parent(arec).<Poly<?, ?>>as())
                            .map(r -> doAuto ? r.<Poly<?, ?>>as().at(uri(nextKey)) : r.<Poly<?, ?>>as().atDirect(uri(nextKey))));
                }
            }
        }

        public static Map<Obj, Obj> cleanMap(final Map<Obj, Obj> jvm) {
            if (jvm.isEmpty())
                return jvm;
            try {
                jvm.remove(noobj());
                jvm.entrySet().stream().filter(e -> e.getValue() == null || e.getValue().isNoObj()).map(Map.Entry::getKey).toList().forEach(jvm::remove);
            } catch (final UnsupportedOperationException e) {
                LOG.error("underlying jvm object is immutable: %s %s", jvm.getClass().getName().toLowerCase(), jvm);
            }
            return jvm;
        }

        public static Rec stripNone(final Rec rec) {
            rec.recValue().entrySet().stream().filter(e -> e.getValue().isNone()).toList().forEach(e -> rec.jvm().remove(e.getKey()));
            return rec;
        }

        public static Obj rshiftRec(final Rec lhs, final Obj arg) {
            return arg.isNoObj() ? objs(lhs.asRec().valueElements()) : objs(arg.stream().map(k -> lhs.asRec().at(k)));
        }

        /**
         * The Universal Structural Projection logic for Records.
         *
         * @param lhs      The target record to be projected.
         * @param rulesRec The la-palette projector (a rec of predicate -> transformation/constraint).
         * @param verify   If true, operates in 'where' mode (strict validation).
         *                 If false, operates in 'select' mode (structural mutation).
         * @return A map containing the results of the projection. In verify mode,
         * this is primarily used for internal state; the real result is either
         * success or a ProjectionFailureException.
         * @throws ProjectionFailureException if verification fails or a transformation errors.
         */
        public static Map<Obj, Obj> project(final Rec lhs, final Rec rulesRec, boolean verify) {
            if (rulesRec.isNoObj()) {
                if (verify) throw ProjectionFailureException.instance();
                return mutableMap();
            }
            if (verify) {
                for (final Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                    if (entry.getKey().isUri()) {
                        final Obj foundValue = lhs.at(entry.getKey());
                        if (foundValue.isNoObj() || !foundValue.test(entry.getValue()))
                            throw ProjectionFailureException.instance();
                    }
                }
            }
            final Map<Obj, Obj> result = mutableMap();
            for (final Rel rel : lhs.elements().toList()) {
                for (final Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                    final Obj predicate = entry.getKey();
                    final Obj transformOrConstrain = entry.getValue();
                    if (predicate.test(rel.first())) {
                        if (verify) {
                            if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                project(rel.second().asRec(), transformOrConstrain.asRec(), true);
                            } else if (!rel.second().test(transformOrConstrain))
                                throw ProjectionFailureException.instance();
                        } else {
                            if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                result.put(rel.first(), rec(project(rel.second().asRec(), transformOrConstrain.asRec(), false)));
                            } else {
                                final Obj transformedValue = transformOrConstrain.apply(rel.second());
                                if (!transformedValue.isNothing())
                                    result.put(rel.first(), transformedValue);
                            }
                        }
                        break;
                    } else if (predicate.isUri()) {
                        final Obj newValue = lhs.at(predicate);
                        if (!newValue.isNoObj()) {
                            if (verify) {
                                if (!newValue.test(transformOrConstrain))
                                    throw ProjectionFailureException.instance();
                            } else {
                                if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                    result.put(rel.first(), rec(project(rel.second().asRec(), transformOrConstrain.asRec(), false)));
                                } else {
                                    final Obj transformedValue = transformOrConstrain.apply(newValue);
                                    if (!transformedValue.isNothing())
                                        result.put(predicate, transformedValue);
                                }
                            }
                        }
                    }
                }
            }
            // In verify mode, if we reached this point without throwing an exception, the profile is satisfied.
            return result;
        }
    }

}
