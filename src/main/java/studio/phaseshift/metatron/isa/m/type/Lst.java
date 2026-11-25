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
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MLst;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ProjectionFailureException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.google.common.math.LongMath.saturatedAdd;
import static com.google.common.math.LongMath.saturatedMultiply;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Lst extends Poly<Lst, List<Obj>> {

    // Lst with coefficient {0}: noobj typed as a Lst, for a method that answers a Lst or nothing.
    // a poly's payload is read unguarded (count(), isEmpty()), so it is empty and immutable rather than null
    Lst LST_NOOBJ = new MLst(List.of(), LST_TID.zero(), null);

    static Lst lst_noobj() {
        return LST_NOOBJ;
    }

    @Override
    default Stream<Rel> indexedStream() {
        final AtomicInteger i = new AtomicInteger(0);
        return this.jvm().stream().map(e -> rel(jnt(i.getAndIncrement()), e).c(c -> this.c()).as());
    }

    @Override
    default <OBJ extends Obj> Stream<OBJ> valueElements() {
        return this.elements();
    }

    @Override
    Lst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    default Lst add(final Obj obj) {
        return this.add(obj, IMMUTABLE);
    }

    default Lst add(final Obj obj, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
        newList.add(obj);
        return (Lst) operation.apply(this, newList);
    }

    @Override
    default boolean has(final Obj key) {
        return key.isInt() ? this.jvm().size() > key.intValue().intValue() :
                key.isUri() &&
                        !key.uriValue().isEmpty() &&
                        CommonUtil.isInt(key.uriValue().path().getFirst()) &&
                        this.jvm().size() > Integer.valueOf(key.uriValue().path().getFirst());
    }

    default <OBJ extends Obj> Stream<OBJ> elements() {
        return (Stream) this.jvm().stream().map(e -> (null == e ? noobj() : e).autoResolve(this).c(c -> c.mult(this.c())));
    }

    @Override
    default Stream<Obj> values() {
        return this.elements();
    }

    @Override
    default Stream<Obj> keys() {
        return this.indexedStream().map(Rel::first);
    }

    @Override
    default <OBJ extends Obj> OBJ atDirect(final Obj key) {
        return Lst.Helper.atToggle(this, key, false);
    }

    default Lst at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        if (key.isInt()) {
            final int keyIndex = key.intValue().intValue();
            final int effectiveIdx = keyIndex < 0 ? this.jvm().size() + keyIndex : keyIndex;
            if (effectiveIdx < 0 || effectiveIdx >= this.jvm().size())
                throw MTronException.of("lst index out of bounds: %d > %d", Math.abs(keyIndex), this.jvm().size());
            final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
            if (value.isNoObj())
                newList.remove(effectiveIdx);
            else
                newList.set(effectiveIdx, value);
            return (Lst) operation.apply(this, newList);
        } else if (key.isUri()) {
            final Int k = jnt(Long.parseLong(key.uriValue().path().get(0)));
            if (key.uriValue().path().size() == 1) {
                return this.at(k, value, operation);
            } else {
                final Obj v = this.jvm().get(k.intValue().intValue());
                if (v.isPoly()) {
                    return this.at(k, v.<Poly<?, ?>>as().at(uri(key.<Uri>as().uriValue().pretract(1)), value, operation), operation).as();
                } else {
                    throw MTronException.of("unknown key value for lst: %s => %s", key, value);
                }
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    default <OBJ extends Obj> OBJ at(final int index) {
        return this.at(jnt(index));
    }

    @Override
    default <OBJ extends Obj> OBJ at(final Obj key) {
        return Lst.Helper.atToggle(this, key, true);
    }

    @Override
    default Lst c(final Function<cInt, cInt> f) {
        return (Lst) Poly.super.c(f);
    }

    /**
     * THE WORD RING'S SUM (Marko, 2026-11-24 and 11-25; Claim 19 of ring-theoretic-distributed-structures.md): two
     * lsts add as two timelines, the prefix and suffix their words share factored out around one brace of what lies
     * between, as a uri's sum factors its paths: {@code [a,b,c] + [a,d,c]} is {@code [a,{b,d},c]}. Lsts that share no
     * moment are one lst whose slot branches them, {@code [1,2,3] + [4,5,6]} is {@code [{[1,2,3],[4,5,6]}]}, as
     * {@code <1/2/3> + <4/5/6>} is {@code <{1/2/3,4/5/6}>}: a stream of the two would not stay their sum under the
     * next plus, which maps over a stream. The sum reads its letters only by equality and applies none of their
     * operations ({@link Helper#sum}). This lst's own point floats over it (Theorem 3), as a uri's does, while the
     * argument counts by its point and the sum keeps that mass on its letters: {@code [a,b,c] + {2}[a,d,c]} is
     * {@code [a,{b,{2}d},c]}, and {@code [a,b] + [a,b]} is {@code [{2}a,b]}.
     */
    default Lst plus(final Lst rhs) {
        if (this.isNoObj())
            return Helper.sum(Stream.of(rhs));
        final Lst sum = Helper.sum(Stream.of(this.d(cInt.ONE()).asLst(), rhs));
        return sum.isNoObj() ? sum : this.jvm(sum.lstValue());
    }

    /**
     * THE ZERO OF THE WORD RING, noobj typed as a lst ({@link #lst_noobj()}): the sum of nothing, what a sum answers
     * when its words cancel, and what absorbs a product. The empty lst {@code [,]} is the unity ({@link #one()}).
     */
    @Override
    default Lst zero() {
        return lst_noobj();
    }

    /**
     * THE WORD RING'S PRODUCT, concatenation: one timeline after another, {@code [1,2,3] * [4,5,6]} is
     * {@code [1,2,3,4,5,6]}, with the empty lst as its unity ({@link #one()}) and the zero absorbing it. It splices the
     * slots as they are, braces included, so it distributes over the sum on both sides, and it applies none of its
     * letters' operations: {@code [[a=>b],c] * [[b=>e]]} is {@code [[a=>b],c,[b=>e]]}, the records' join left to a
     * fold. This lst's own point floats over it, and the argument's multiplies into the product's first letter, as a
     * lst is multilinear in its letters (Claim 24).
     */
    default Lst mult(final Lst rhs) {
        if (this.isNoObj() || rhs.isNoObj())
            return lst_noobj();
        final List<Obj> slots = new ArrayList<>(this.lstValue());
        slots.addAll(rhs.lstValue());
        return this.jvm(Helper.scaled(slots, rhs.tid().d()).lstValue());
    }

    /**
     * THE UNITY OF THE WORD RING, the empty word {@code [,]}: the identity of concatenation on both sides.
     */
    default Lst one() {
        return lst();
    }

    /**
     * THE ADDITIVE INVERSE, -1 on the lst's mass where the sum keeps it (Marko, 2026-11-25): on its first letter, as a
     * lst is multilinear in its letters (Claim 24), so {@code [1,2,3].neg()} is {@code [{-1}1,2,3]} and
     * {@code x + x.neg()} is the zero, while a point -1 on every slot is (-1)^n of the lst.
     */
    default Lst neg() {
        return this.isNoObj() ? this : this.jvm(Helper.scaled(this.lstValue(), cInt.of(-1L)).lstValue());
    }

    @Override
    default boolean test(final Obj rhs) {
        if (this == rhs)
            return true;
        return rhs.isLst() ? Lst.Helper.testIndices(this, rhs.asLst()) : Poly.super.test(rhs);
    }

    public static final class LstType {

        /*
         * WHAT REMAINS HERE, and why: the `as` conversions that declare nothing (lst->uri/lst/code). They are
         * registered with instC, so none stages a `?decq` claim -- no law, no inverse. lst<->rec preserves nothing in
         * EITHER direction (the round trip re-keys and reorders what it carries), so both directions declare NO
         * inverse, and lst->rec lives in mInsts.lsts_insts() beside the lst operations.
         */
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    // THE PATH A LST OF ATOMS SPELLS, the word ring's embedding in the uri ring ([a,b,c].as(uri) is a/b/c):
                    // the uri sum of its words, each word its letters' segments with its coefficient for the point, so the
                    // sum a lst factors is the sum a uri factors, [a,{b,d},c] spelling <a/{b,d}/c>
                    instC(AS_INST_TID.dom(LST_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(fURI.Helper.sum(Helper.words(lhs.asLst().lstValue(), cInt.ONE())
                            .entrySet().stream()
                            .filter(word -> !word.getValue().isZero())
                            .map(word -> word.getKey().stream().map(Obj::uriValue).reduce(fURI::extend).orElse(Singleton.f("")).d(word.getValue()))))),
                    instC(AS_INST_TID.dom(LST_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lhs.tid(boundOrDeclared(lhs.asLst(), inst.arg(0).vidOrTid())).c(c -> c.mult(lhs.c()))),
                    instC(AS_INST_TID.dom(LST_TID).rng(CODE_TID), lst(T(CODE_TID)), (lhs, inst) -> MCode.code(lhs.asLst().jvm().stream().flatMap(o -> o.isCall() ? o.asCall().insts().stream() : map_(o).tryToInst().insts().stream()).toList()).c(c -> c.mult(lhs.c())))
            ));
        }

        /**
         * A SLOT IS A VARIABLE WHEN IT IS SPELLED AS ONE: {@code A} — or {@code A{2}} — is a generic, which is
         * exactly what {@link fURI#isGeneric()} recognizes (all-caps segments), while {@code int} and {@code /m/int}
         * are concrete types. {@code A}..{@code G} are the constants on {@link InstSet}; reading them by spelling
         * means a type reached through a scheme or a query is never mistaken for a variable.
         */
        public static boolean hasGenericSlot(final List<String> poly) {
            for (final String slot : poly)
                if (isVariable(slot))
                    return true;
            return false;
        }

        /**
         * An index spelled as a variable: {@code A}, {@code A{2}}, or {@code {*}A{*}} — a bracket writes an index
         * {@code {point}value{span}}, so its name is read past a leading point. A cheap spelling guard comes first:
         * only an all-caps NAME can be a variable, so the text is read before the furi parser is asked about it (a
         * {@code ,} or a {@code +} index is not a name at all).
         */
        private static boolean isVariable(final String index) {
            String text = null == index ? "" : index.trim();
            if (text.startsWith("{"))
                text = text.indexOf('}') < 0 ? "" : text.substring(text.indexOf('}') + 1);
            return !text.isEmpty() && Character.isUpperCase(text.charAt(0)) && Singleton.f(text).isGeneric();
        }

        /**
         * THE BINDINGS A LST BRACKET'S VARIABLES TAKE FROM THE LST IT MEETS: the bracket's own test, binding
         * ({@link Type.Helper#testTensor(List, List, Map)}), so a lst binds a bracket exactly when it is one (one
         * algorithm). The lst's indices are a value's entries laid ({@link Helper#laid}) or a lst type's own bracket
         * ({@link Type#lstIndices}). {@code lst[A{2},str]} over {@code [1,2,'hello']} binds A to int,
         * {@code lst[A{*},B{?}]} over {@code [1,1,'hello']} binds A to int and B to str, and over {@code [1,1]} A to
         * int alone; {@code lst[{*}A{*}]} over order's {@code lst[{*}int{*}]} binds A to int.
         *
         * @return the bindings, empty when the bracket names no variable or the lhs holds no lst indices to read; null
         * when the lst is not the bracket ({@code lst[A{2},str]} over {@code [1,'hello']})
         */
        public static Map<fURI, fURI> bindings(final Obj lhs, final fURI bracket) {
            if (null == bracket || !bracket.hasPoly() || !hasGenericSlot(bracket.poly()) || !isLst(bracket))
                return Map.of();
            final List<? extends Obj> indices = lhs.isType()
                    ? (isLst(lhs.tid()) && lhs.tid().hasPoly() ? lhs.asType().lstIndices() : List.of())
                    : lhs.isLst() ? Helper.laid(lhs.asLst().lstValue()) : List.of();
            if (indices.isEmpty())
                return Map.of();
            final Map<fURI, fURI> bindings = new LinkedHashMap<>();
            return Type.Helper.testTensor(indices, T(bracket).lstIndices(), bindings) ? bindings : null;
        }

        /**
         * A BRACKET WITH ITS VARIABLES BOUND: each index naming a bound variable names its binding, with the index's
         * own point and span as written ({@code lst[{*}A{*}]} with A bound to int is {@code lst[{*}int{*}]}); the id
         * itself when no index is bound.
         */
        public static fURI bound(final fURI tid, final Map<fURI, fURI> bindings) {
            if (null == tid || !tid.hasPoly() || null == bindings || bindings.isEmpty())
                return tid;
            boolean changed = false;
            final List<String> indices = new ArrayList<>(tid.poly().size());
            for (final String index : tid.poly()) {
                final fURI declared = isVariable(index) ? Singleton.f(index.trim()) : null;
                final fURI binding = null == declared ? null : bindings.get(declared.basePath());
                if (null == binding) {
                    indices.add(index);
                    continue;
                }
                indices.add(binding.basePath().b(declared.b()).d(declared.d()).toString());
                changed = true;
            }
            return changed ? tid.poly(indices) : tid;
        }

        private static boolean isLst(final fURI tid) {
            return LST_TID.basePath().equals(tid.polyLess().basePath());
        }

        /**
         * BIND a list poly's generics against the slots of {@code value} — {@code lst[A,B]::[1,'x']} is
         * {@code lst[int,str]::[1,'x']}. The poly IS the value's shape, position by position (see
         * {@code fURI.Helper.validatePoly}), so slot {@code i} of the poly is determined by slot {@code i} of the
         * value: a generic binds to that value's {@code tid()}, a variable occurring in several positions must bind to
         * the SAME type (unified here), and a concrete slot must already accept its value (today's behavior).
         * <p>
         * NO GENERAL INFERENCE: only the declared poly's OWN variables bind, from the positions that determine them.
         * Nothing is inferred from the value into the surrounding code, and {@code rec} polys are deliberately not
         * bound (traits are nominal — a generic trait is either in scope or an error, a separate decision).
         *
         * @param value the lst being typed
         * @param tid   the type id it was declared with
         * @return the bound type id; {@code tid} itself when there is nothing to bind (no poly, or no generic slot);
         * or {@code null} when the value contradicts the poly (arity, or one variable bound to two types) — the
         * caller then fails it through the ordinary "does not match the poly" verdict
         */
        public static fURI boundTID(final Lst value, final fURI tid) {
            if (null == tid || !tid.hasPoly() || !hasGenericSlot(tid.poly()))
                return tid;
            // the one walk a bracket is tested through: a lst[A,B]::[1] leaves an index no value fills (arity), and a
            // concrete index its value does not hold fails the unfold, so both answer null, as a contradiction does
            final Map<fURI, fURI> bindings = bindings(value, tid);
            return null == bindings ? null : bound(tid, bindings);
        }

        /**
         * {@link #boundTID} for a construction site that must still SET a type: the bound id when the value binds,
         * otherwise the id as declared — so a contradiction is reported by the type check that follows (one error for
         * "the value does not match the poly", whether the poly was concrete or bound-from-generics), not here.
         */
        public static fURI boundOrDeclared(final Lst value, final fURI tid) {
            final fURI bound = boundTID(value, tid);
            return null == bound ? tid : bound;
        }
    }

    public static final class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * A SPAN ON AN AXIS IS EXTENSION (Marko, 2026-11-17 for values, 2026-11-18 for types and instructions): a lst,
         * and an argument list, is an axis a span unfolds along, so it lays {@code 3{2}} as a 3 at each of two indices,
         * {@code [3{2}]} is {@code [3,3]}, a lst of two, while {@code [{2}3]} keeps two 3s at its one index. A type and
         * an instruction lay the same way, whatever side of the name their literal writes the span on:
         * {@code [{4}int::T]} is {@code [int::T,int::T,int::T,int::T]}, so {@code 1-<[{4}int::T]} is
         * {@code [1,1,1,1]} as {@code 1-<[int{4}::T]} is {@code [{4}1]}, and {@code 1-<[{2}plus(1),{3}mult(2)]} maps
         * one obj over a lst of instructions. A span of zero lays nothing ({@code [{0}int::T]} is {@code [,]}). With no
         * axis the span stays a power of the obj itself: a stream curls a value's, and a type's or an instruction's is
         * its composition ({@code {4}int::T} is {@code int::T.int::T.int::T.int::T}, which is {@code int::T}). Only an
         * exact span lays; a range ({@code {*}int::T}, {@code {?}int::T}) stays a run, so a declared {@code {*}int::T}
         * takes any number of arguments. The entries as they are when none lays.
         */
        public static List<Obj> laid(final List<Obj> entries) {
            final Predicate<Obj> lays = entry -> (entry.isMono() || entry.isPoly() || entry.isType() || entry.isCall()) &&
                    !entry.tid().b().isOne() && entry.tid().b().isExact() && null != entry.tid().b().min();
            return entries.stream().noneMatch(lays) ? entries : entries.stream()
                    .flatMap(entry -> lays.test(entry) ?
                            Stream.generate(() -> withoutSpan(entry)).limit(Typer.Helper.unfolds(Typer.UNFOLD_LST, Math.max(0L, entry.tid().b().min()))) :
                            Stream.of(entry))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /**
         * One index of a laid run: the entry without its span. A type keeps its span on its vid too (a base type is
         * named by it), so both lose it.
         */
        private static Obj withoutSpan(final Obj entry) {
            final Obj one = entry.tid(entry.tid().b(null));
            return entry.isType() && null != entry.vid() ? one.vid(entry.vid().b(null)) : one;
        }

        /**
         * THE CANONICAL SUM OF LSTS ({@link Lst#plus}), uri's {@code fURI.Helper.sum} over objects instead of segments,
         * with its scans for the shared prefix and suffix ({@link fURI.Helper#sharedPrefix}). Each lst counts as its
         * words ({@link #words}): like words add their coefficients, and a word at zero drops. What is left is written as
         * one lst, the prefix and suffix every word shares around one brace of the middles, sorted by their text. A
         * middle of one letter is that letter, and any other is a sub-word lst. So is one letter that is itself a lst,
         * wrapped as {@code [[x,y]]}, since a brace splices the lsts it holds. A single word is written alone, its
         * coefficient on its first letter ({@link #scaled}), and a sum of nothing, or of words that cancel, is the zero.
         * Summing a stream at once is the fold of {@link Lst#plus} without re-expanding each partial sum.
         */
        public static Lst sum(final Stream<Lst> lsts) {
            final Map<List<Obj>, cInt> words = new LinkedHashMap<>();
            lsts.forEach(lst -> words(lst.lstValue(), lst.tid().d()).forEach((word, c) -> words.merge(word, c, cInt::plus)));
            final List<Map.Entry<List<Obj>, cInt>> live = words.entrySet().stream().filter(word -> !word.getValue().isZero()).toList();
            if (live.isEmpty())
                return lst_noobj();
            if (1 == live.size())
                return scaled(live.getFirst().getKey(), live.getFirst().getValue());
            final List<List<Obj>> letters = live.stream().map(Map.Entry::getKey).toList();
            final int prefix = fURI.Helper.sharedPrefix(letters);
            final int suffix = fURI.Helper.sharedSuffix(letters, prefix);
            final List<Obj> first = letters.getFirst();
            final List<Obj> slots = new ArrayList<>(first.subList(0, prefix));
            // the middles in the order of their letters' text, a middle before every middle it begins, as a path sorts. A
            // brace is a sum, so its members may all be negative, as a negated brace's are: it is no stream of objs
            slots.add(objs(live.stream()
                    .map(word -> Map.entry(word.getKey().subList(prefix, word.getKey().size() - suffix), word.getValue()))
                    .sorted(Comparator.comparing(middle -> middle.getKey().stream().map(Obj::toString).toArray(String[]::new), Arrays::compare))
                    .map(middle -> 1 == middle.getKey().size() && !middle.getKey().getFirst().isLst()
                            ? middle.getKey().getFirst().d(middle.getValue())
                            : lst(new ArrayList<>(middle.getKey())).d(middle.getValue()))));
            slots.addAll(first.subList(first.size() - suffix, first.size()));
            return lst(slots);
        }

        /**
         * THE WORDS A LST IS (Definition 11), each its letters at point one with its coefficient, the product of the
         * points along it and the lst's own: {@code [{2}a,b]} is the word {@code a b} twice, as a lst is multilinear in
         * its letters (Claim 24). A brace is a sum at one moment whose members are letters, or lsts, which are sub-words
         * that splice (Marko, 2026-11-25): {@code [x,{[a,b],c}]} is {@code x a b + x c}, as {@code <x/{a/b,c}>} is. A
         * brace of equal sub-words is bulked into one lst with a point ({@link #isSubWord}), spliced with that mass:
         * {@code [x,{[a,b],[a,b]}]} is {@code 2 x a b}. A hole, noobj, is the zero letter, so no word runs through it.
         * A brace multiplies the words, and a span shares one sub-word among all its copies, so a short literal can hold
         * more letters than memory: the words are measured before any is built ({@link #measure}) and bounded by the
         * typer's unfold for lsts, in copies of the letters the lst holds ({@link #held}): a brace of many members is as
         * many words, linear in the lst, while k braces of two are 2^k words of k letters, held in 2k, and five nested
         * spans of 100, [{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{100}, one word of 2·10^10 letters, held in 200. A
         * lst with a hole is no word at all, so none is built, however many its braces before the hole would make.
         */
        private static Map<List<Obj>, cInt> words(final List<Obj> slots, final cInt coefficient) {
            final Map<Obj, long[]> measured = new IdentityHashMap<>();
            final long[] measure = measure(slots, measured);
            if (0L == measure[0])
                return new LinkedHashMap<>();
            Typer.Helper.unfolds(Typer.UNFOLD_LST, Math.ceilDiv(Math.max(measure[0], measure[1]),
                    Math.max(1L, held(slots, Collections.newSetFromMap(new IdentityHashMap<>())))));
            final Map<List<Obj>, cInt> words = new LinkedHashMap<>();
            expand(slots, coefficient, measured).forEach(word -> words.merge(word.getKey(), word.getValue(), cInt::plus));
            return words;
        }

        /**
         * the words a lst's slots are, measured before any is built ({@link #words}): how many, and how many letters they
         * hold together. A slot of m moments makes w words of l letters m w words of m l letters and w times the moments'
         * letters, and a hole, a slot of no moment, makes no word. Each sub-word is measured once, however many slots hold
         * it ({@code measured}, by identity), as a span's copies share theirs. Counted in saturating longs, as the word
         * count can pass any long: a product with a zero factor is zero, whatever the other, and one past a long's range
         * is the long's largest, past the bound, so no measure is undefined, and a hole is no word in whatever order it
         * meets an unbounded count
         */
        private static long[] measure(final List<Obj> slots, final Map<Obj, long[]> measured) {
            return slots.stream()
                    .map(slot -> {
                        if (Thread.currentThread().isInterrupted())
                            throw MTronException.of("the words of %s slots were interrupted as they were measured", slots.size());
                        return slot.stream()
                                .map(member -> isSubWord(slot, member) ? measured.computeIfAbsent(member, sub -> measure(sub.lstValue(), measured)) : new long[]{1L, 1L})
                                .reduce(new long[]{0L, 0L}, (a, b) -> new long[]{saturatedAdd(a[0], b[0]), saturatedAdd(a[1], b[1])});
                    })
                    .reduce(new long[]{1L, 0L}, (at, slot) -> new long[]{saturatedMultiply(at[0], slot[0]),
                            saturatedAdd(saturatedMultiply(at[1], slot[0]), saturatedMultiply(at[0], slot[1]))});
        }

        /**
         * the letters a lst holds in memory ({@link #words}): each slot's letters, and each sub-word's once, however many
         * slots hold it ({@code seen}, by identity), so a span's copies of a sub-word hold its letters once
         */
        private static long held(final List<Obj> slots, final Set<Obj> seen) {
            return slots.stream()
                    .flatMap(slot -> slot.stream().map(member -> !isSubWord(slot, member) ? 1L : seen.add(member) ? held(member.lstValue(), seen) : 0L))
                    .reduce(0L, (a, b) -> saturatedAdd(a, b));
        }

        /**
         * the words of a lst's slots as they are built ({@link #words}), each its own letters at point one with its
         * coefficient, like words merged after: a slot of one moment extends every word in place, so a lst without braces
         * is one word built once, linear in its length, and a slot of many moments copies each word once per moment. A
         * sub-word that is no word ({@link #measure}) is never built
         */
        private static List<Map.Entry<List<Obj>, cInt>> expand(final List<Obj> slots, final cInt coefficient, final Map<Obj, long[]> measured) {
            final List<Map.Entry<List<Obj>, cInt>> words = new ArrayList<>(List.of(Map.<List<Obj>, cInt>entry(new ArrayList<>(), coefficient)));
            for (final Obj slot : slots) {
                if (Thread.currentThread().isInterrupted())
                    throw MTronException.of("the words of %s slots were interrupted as they were built", slots.size());
                final List<Map.Entry<List<Obj>, cInt>> moments = slot.stream().flatMap(member -> !isSubWord(slot, member)
                        ? Stream.of(Map.<List<Obj>, cInt>entry(List.of(member.d(cInt.ONE())), member.tid().d()))
                        : 0L == measured.computeIfAbsent(member, sub -> measure(sub.lstValue(), measured))[0] ? Stream.empty()
                        : expand(member.lstValue(), member.tid().d(), measured).stream()).toList();
                final List<Map.Entry<List<Obj>, cInt>> longer = words.stream().flatMap(word -> moments.stream().map(moment -> {
                    final List<Obj> letters = 1 == moments.size() ? word.getKey() : new ArrayList<>(word.getKey());
                    letters.addAll(moment.getKey());
                    return Map.entry(letters, word.getValue().mult(moment.getValue()));
                })).toList();
                words.clear();
                words.addAll(longer);
            }
            return words;
        }

        /**
         * A LST WITH A POINT IS A BRACE: a brace of equal sub-words is bulked into one lst with a point,
         * {@code {[a,b],[a,b]}} being {@code {2}[a,b]}, so a lst slot whose point is not one is a sub-word with that mass,
         * as a lst member of a brace is, while a lst slot at point one is a letter: {@code [[a,b]]} is the word of one
         * letter, the lst {@code [a,b]}. A brace of one sub-word at point one is that letter ({@code [x,{[a,b]},y]} is
         * {@code [x,[a,b],y]}). The words ({@link #words}), the converse, the fold and a scaled word read a slot so
         */
        private static boolean isSubWord(final Obj slot, final Obj member) {
            return member.isLst() && (slot.isObjs() || !member.tid().d().isOne());
        }

        /**
         * A word times a coefficient, written where a lst keeps its mass: on its first letter, as a lst is multilinear in
         * its letters (Claim 24), so {@code [a,b]} twice is {@code [{2}a,b]}, and a brace there scales each member. A lst
         * with a point is a sub-word ({@link #isSubWord}), so the mass never lands on a lst letter: a lst letter is wrapped
         * as the sub-word that holds it ({@code [[x,y],z]} twice is {@code [{2}[[x,y]],z]}), a sub-word's point takes the
         * mass, and one back at point one is spliced into its letters ({@code [{-1}[a,b],c].neg()} is {@code [a,b,c]}).
         * The unity has no letter, so it carries its mass as the sub-word it is, as {@code <> + <>} is {@code <{2}>}:
         * {@code [,] + [,]} is {@code [{2}[,]]}.
         */
        private static Lst scaled(final List<Obj> slots, final cInt coefficient) {
            if (coefficient.isOne())
                return lst(new ArrayList<>(slots));
            if (slots.isEmpty())
                return lst(lst().d(coefficient));
            final Obj first = slots.getFirst();
            final List<Obj> scaled = new ArrayList<>(slots.subList(1, slots.size()));
            if (isSubWord(first, first) && first.tid().d().mult(coefficient).isOne())
                scaled.addAll(0, first.lstValue());
            else
                scaled.addFirst(first.isLst() && !isSubWord(first, first)
                        ? lst(first).d(coefficient)
                        : objs(first.stream().map(member -> member.d(member.tid().d().mult(coefficient)))));
            return lst(scaled);
        }

        /**
         * A LST'S SPAN IS ITS POWER in the word ring (Marko, 2026-11-24 and 11-25; Definition 11), as a uri's is:
         * realized as a literal is parsed ({@code [1,2]{2}} is {@code [1,2,1,2]}), and by {@code pow(n)} at run time. A
         * power is n copies of the lst concatenated, the unity {@code [,]} at zero, and a negative span the converse's
         * power, so {@code [1,2,3]{-1}} is {@code [3,2,1]} ({@link #converse}). A span range is the sum of its powers,
         * {@code [a]{1,2}} being {@code [a,{[,],a}]}, a + a a. A power copies the lst's letters with their points, and
         * keeps the lst's own point once, outside it. Every power of a lst that holds no word is the zero but the unity, so
         * only the lst itself is read for one, and every power of a lst that holds a word holds one, so an open side never
         * ends and is refused at the bound. A range is bounded before any of its powers is built, by the typer's unfold
         * for lsts: its farthest power, then the copies all of them hold, both sides of zero together.
         */
        public static Lst power(final Lst lst, final cInt span) {
            final Long low = span.min();
            final Long high = span.max();
            if (null != low && low.equals(high))
                return copies(lst, low);
            if (words(lst.lstValue(), cInt.ONE()).values().stream().allMatch(cInt::isZero))
                return (null == low || low <= 0L) && (null == high || high >= 0L) ? lst() : lst_noobj();
            Typer.Helper.unfolds(Typer.UNFOLD_LST, null == low || null == high ? Long.MAX_VALUE : Math.max(Math.abs(Math.max(low, -Long.MAX_VALUE)), Math.abs(high)));
            Typer.Helper.unfolds(Typer.UNFOLD_LST, LongStream.rangeClosed(low, high).map(Math::abs).sum());
            return Helper.sum(LongStream.rangeClosed(low, high).mapToObj(k -> copies(lst, k)));
        }

        /**
         * the lst's exact power n ({@link #power}): n copies of it, of its converse for a negative n, bounded by the
         * typer's unfold for lsts
         */
        private static Lst copies(final Lst lst, final long n) {
            if (Long.MIN_VALUE == n)
                throw MTronException.of("%s has no power at %s: the span has no positive counterpart", lst, n);
            Typer.Helper.unfolds(Typer.UNFOLD_LST, n);
            final List<Obj> word = n < 0L ? converse(lst.lstValue(), new IdentityHashMap<>()) : lst.lstValue();
            final List<Obj> slots = new ArrayList<>();
            for (long i = 0L; i < Math.abs(n); i++)
                slots.addAll(word);
            return lst.jvm(slots);
        }

        /**
         * THE FOLD, {@code >|} (Claim 22 of ring-theoretic-distributed-structures.md): a lst run as a program, each slot a
         * step applied to what the steps before it answered, a value the constant that answers it and an instruction
         * itself. A brace is a branch, each member stepping from what reached it, and a sub-word ({@link #isSubWord}) is
         * folded as the program it is and counted by its point, so the fold of a sum is the sum of the folds:
         * {@code [1,{[plus(2),plus(3)],[mult(2),plus(5)]}]>|} is {@code {6,7}}, as
         * {@code {[1,plus(2),plus(3)],[1,mult(2),plus(5)]}>|} is. A branch answers a stream, and an instruction runs over
         * what reached it as its code runs over a stream, a map on each obj and a reducer on them all:
         * {@code [1,{plus(1),plus(2)},sum()]>|} is {@code 5}, as {@code {2,3}.sum()} is. Nothing is still one input, so a
         * constant answers it. A record of walks ({@code Rec.Helper.holdsWalks}) that meets a record of walks is their
         * join, a record's product, so a lst of them folds to their product (Claim 28): {@code [[a=>b],[b=>e]]>|} is
         * {@code [a=>b=>e]}, and {@code [[a=>b],[c=>d]]>|} the zero, the empty rec. The join runs no code a record holds
         * as a value ({@code [[x=>a],[a=>plus(1)]]>|} is {@code [x=>a=>plus(1)]}), while a key that acts, code or a type,
         * runs as a WHERE on the ends it meets, as in the product. A live rec holds no walks, so it applies as itself,
         * running whatever its apply runs: a space, a network among them, passes a value on and runs the code it meets
         * ({@code Space.Helper.resolveApply}), and a parser parses a str. The zero drops from a stream that holds anything else, and a stream of zeros is the zero,
         * as in a sum: {@code [[x=>a],{[a=>b],[a=>c]},[b=>e]]>|} is {@code [x=>a=>b=>e]}, and
         * {@code [[x=>a],{[b=>c],[d=>e]}]>|} is {@code [=>]}.
         */
        public static Obj fold(final Lst word, final Obj lhs) {
            final Predicate<Obj> zero = obj -> Rec.Helper.holdsWalks(obj) && obj.asRec().isEmpty();
            return word.values().reduce(lhs, (at, slot) -> {
                final Obj answered = objs(slot.stream().flatMap(member -> isSubWord(slot, member)
                        ? fold(member.d(cInt.ONE()).asLst(), at).stream().map(folded -> folded.c(c -> c.mult(member.tid().d())))
                        : member.isCall() && !at.isNoObj()
                        ? Code.wrapStart(at, MCode.code(member.d(cInt.ONE()).asCall().insts())).apply(at.isMonad() ? at : noobj())
                        .stream().map(out -> out.c(c -> c.mult(member.tid().d())))
                        : (at.isNoObj() ? Stream.of(at) : at.stream()).map(obj -> Rec.Helper.holdsWalks(member) && Rec.Helper.holdsWalks(obj)
                        ? obj.asRec().mult(member.asRec()) : member.apply(obj))));
                return answered.stream().noneMatch(zero) ? answered
                        : answered.stream().allMatch(zero) ? rec() : objs(answered.stream().filter(zero.negate()));
            });
        }

        /**
         * THE CONVERSE OF A LST, its span -1 (Claim 21): its letters in the reverse order, each held as it is, since a
         * letter's own converse is never needed, and the sub-words in a brace reversed with it, since a brace splices
         * them: {@code [x,{[a,b],c}]{-1}} is {@code [{[b,a],c},x]}, as is a lst slot with a point, a brace of equal
         * sub-words ({@link #isSubWord}). Points stay with their letters. A sub-word is reversed once however many slots
         * hold it ({@code reversed}, by identity), so the converse writes the slots the lst holds, sharing what it shares,
         * and never the letters a span's copies stand for. The converse is an
         * involution and reverses a product, (u v)^-1 = v^-1 u^-1, but it is no inverse: {@code [a,b]} times its converse
         * is {@code [a,b,b,a]}.
         */
        private static List<Obj> converse(final List<Obj> slots, final Map<Obj, Obj> reversed) {
            return slots.reversed().stream()
                    .map(slot -> {
                        if (Thread.currentThread().isInterrupted())
                            throw MTronException.of("the converse of %s slots was interrupted", slots.size());
                        return slot.isObjs() || isSubWord(slot, slot) ? objs(slot.stream().map(member -> isSubWord(slot, member)
                                ? reversed.computeIfAbsent(member, sub -> sub.<Lst>jvm(converse(sub.lstValue(), reversed))) : member)) : slot;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /**
         * A LST TESTS A LST INDEX BY INDEX (Marko, 2026-11-17), walked as a bracket is (Marko, 2026-11-18). An index is
         * an address that is not the obj, so a point is mass AT an index and a span a run OF indices: {@code [{2}3]} is
         * two 3s at index 0, a lst of one index, and {@code [3{2}]} a 3 at each of indices 0 and 1, a lst of two. A
         * type's and an instruction's exact span lay the same way ({@link #laid}): {@code [{2}int::T]} is
         * {@code [int::T,int::T]}. A ranged span cannot lay, so it is a run, and the patterns are the entries of the run
         * walk ({@link Type.Helper#runs}): {@code [str::T,{?}int::T]} reads as {@code lst[str,int{?}]} does. Each index
         * holds what its pattern expects ({@link Type.Helper#holds}): its mass is within the pattern's and every copy
         * it holds is one of the pattern's: {@code [{2}3]} is an {@code [int{2}::T]}, {@code [3{2}]} is not, and
         * {@code [{2}3,5]} is no {@code [int::T,int::T,int::T]}. Past the last pattern the lhs is open, even inside a
         * value's run ({@code [3{2}]} is an {@code [int::T]}), and a pattern past the last lhs index is one the lhs
         * leaves empty, so it passes only if its mass admits zero. A lst TYPE's bracket reads its types as values
         * ({@link Type.Helper#testTensor}).
         */
        public static boolean testIndices(final Lst lhs, final Lst rhs) {
            final Function<Lst, List<Obj>> resolved = lst ->
                    laid(lst.lstValue().stream().map(entry -> (Obj) entry.autoResolve(lhs)).toList());
            return !Type.Helper.runs(resolved.apply(lhs), resolved.apply(rhs)).isEmpty();
        }

        public static <OBJ extends Obj> OBJ atToggle(final Lst alst, final Obj key, final boolean doAuto) {
            final cInt cKey = key.c();
            if (key.isInt())
                return (OBJ) autoToggle(alst, key.intValue() < 0 ?
                        ((alst.jvm().size() + 1 > (-1 * key.intValue())) ? alst.jvm().get((int) (alst.jvm().size() + key.asInt().intValue())) : noobj()) :
                        ((alst.jvm().size() > key.intValue()) ? alst.jvm().get(key.asInt().intValue().intValue()) : noobj()), doAuto)
                        .c(c -> c.mult(cKey));
            else if (key.isType()) {
                return (OBJ) objs(doAuto ? alst.elements().filter(e -> !key.apply(e).isNoObj()) : alst.lstValue().stream().filter(e -> !key.apply(e).isNoObj()));
            } else if (key.isUri()) {
                // LOG.info("key: %s", key);
                // if (key.uriValue().isEmpty())
                //            return this.c(c -> c.mult(cKey)).as();

                if (key.uriValue().segmentLength() == 0)
                    return (OBJ) noobj();
                // A TRAILING '/' NAMES THE RELATION OF THE WHOLE READ (Marko, 2026-11-25), keyed by the whole address as a
                // rec's read is, so the read that runs through a slot is one rel: [[a=>[b=>1]]]>>(0/a/b/) is 0/a/b=>1, not
                // the a/b=>1 the rec below would key. A pattern's read keys each match as it always has
                if (key.uriValue().isBranch() && key.uriValue().segmentLength() > 1 && !key.uriValue().hasPattern()) {
                    final Uri address = uri(key.uriValue().asNode());
                    final Obj read = atToggle(alst, address, doAuto);
                    return (OBJ) (read.isNoObj() ? noobj() : rel(address, read).c(c -> c.mult(cKey)));
                }
                final String step = key.uriValue().segments().getFirst();
                final boolean isBranch = key.uriValue().isBranch();
                // a trailing '/' asks for rels at the LAST step only: an earlier step hands its elements on, and the
                // walk below carries the branch to the step it belongs to (+/+/ is each inner lst's entries)
                final boolean rels = isBranch && key.uriValue().segmentLength() == 1;
                Stream<Obj> result;
                // LOG.info("step: %s", step);
                if (step.equals(Singleton.WILD_ONE.toString()) || step.equals(ALL.toString())) {
                    result = rels ? (Stream) alst.indexedStream() : alst.elements();
                } else {
                    if (!CommonUtil.isInt(step))
                        return (OBJ) noobj();
                    //throw MTronException.of("path segment is not an int: %s", step);
                    final Int k = jnt(Long.parseLong(step));
                    if (alst.jvm().size() <= k.intValue().intValue())
                        return (OBJ) noobj();
                    result = rels ? Stream.of(rel(uri(step),
                            autoToggle(alst, alst.jvm().get(k.intValue().intValue()), doAuto))) :
                            Stream.of(autoToggle(alst, alst.jvm().get(k.intValue().intValue()), doAuto));
                }
                if (key.uriValue().segmentLength() == 1) {
                    return (OBJ) objs(result.filter(x -> !x.isNoObj()).map(x -> x.c(c -> c.mult(cKey)).parent(alst)));
                } else {
                    // Multi-segment path: recurse into the matched element keeping the
                    // doAuto toggle — atDirect must stay raw to the very end (only the
                    // final stored value is resolved/unresolved by the toggle).
                    return (OBJ) objs(result.filter(x -> !x.isNoObj()).filter(Obj::isPoly).map(x -> (Poly<?, ?>) x.c(c -> c.mult(cKey)).parent(alst))
                            .map(r -> isBranch ?
                                    (doAuto ? r.at(uri(key.<Uri>as().uriValue().pretract(1).asBranch()))
                                            : r.atDirect(uri(key.<Uri>as().uriValue().pretract(1).asBranch()))) :
                                    (doAuto ? r.at(uri(key.<Uri>as().uriValue().pretract(1)))
                                            : r.atDirect(uri(key.<Uri>as().uriValue().pretract(1))))));
                }
            } else {
                throw MTronException.of("unknown key for lst: %s", key);
            }
        }

        public static Obj rshiftLst(final Lst lhs, final Obj arg) {
            return arg.isNoObj() ? objs(lhs.valueElements()) : objs(arg.stream().map(lhs::at));
        }

        /**
         * Universal Structural Projection for Lists.
         */
        public static List<Obj> project(final Lst lhs, final Obj rulesObj, boolean verify) {
            if (rulesObj == null || rulesObj.isNoObj()) {
                return lhs.elements().toList();
            }
            Rec rulesRec;
            try {
                rulesRec = rulesObj.asRec();
            } catch (Exception e) {
                if (verify) throw ProjectionFailureException.instance();
                return lhs.elements().toList();
            }
            List<Obj> resultElements = new ArrayList<>();
            // We'll use this to track which indices were actually transformed
            // so we can handle identity pass-through for non-matched elements in select mode.
            Set<Integer> matchedIndices = new HashSet<>();

            // Iterate through the la-palette rules (the "Contract")
            for (Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                Obj predicate = entry.getKey();
                Obj constraintOrTransform = entry.getValue();

                // Find all indices in the target list that match this predicate
                List<Integer> matchingIndices = new ArrayList<>();
                int idx = 0;
                for (Obj element : lhs.elements().toList()) {
                    if (jnt(idx).test(predicate)) {
                        matchingIndices.add(idx);
                    }
                    idx++;
                }
                if (verify) {
                    /* --- VERIFY MODE: Structural Validation --- */
                    // 1. MANDATORY EXISTENCE: The la-palette requires this predicate to match something.
                    if (matchingIndices.isEmpty()) {
                        throw ProjectionFailureException.instance();
                    }
                    // 2. CONSTRAINT SATISFACTION: Every matched element must satisfy the rule.
                    for (Integer index : matchingIndices) {
                        Obj val = lhs.elements().toList().get(index);
                        if (!val.test(constraintOrTransform)) {
                            throw ProjectionFailureException.instance();
                        }
                    }
                } else {
                    /* --- TRANSFORM MODE:L surgical mutation --- */
                    for (Integer index : matchingIndices) {
                        Obj val = lhs.elements().toList().get(index);
                        if (constraintOrTransform.isNone()) {
                            // Marked for removal - we don't add it to the result list
                        } else {
                            Obj resVal = constraintOrTransform.apply(val);
                            if (resVal == null || resVal.isNothing()) {
                                if (!constraintOrTransform.isNone())
                                    throw ProjectionFailureException.instance();
                            } else {
                                // We'll handle the final ordering in the reassembly phase
                                resultElements.add(resVal); // Note: Simple add is for logic; see below for order
                            }
                        }
                        matchedIndices.add(index);
                    }
                }
            }

            if (!verify) {
                // REASSEMBLY: Preserve original elements that weren't matched by any la-palette rule
                List<Obj> finalElements = new ArrayList<>();
                int idx = 0;
                for (Obj element : lhs.elements().toList()) {
                    if (matchedIndices.contains(idx)) {
                        // Find the transformation for this specific index
                        for (Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                            if (jnt(idx).test(entry.getKey())) {
                                Obj transformation = entry.getValue();
                                if (!transformation.isNone()) {
                                    finalElements.add(transformation.apply(element));
                                }
                                break;
                            }
                        }
                    } else {
                        // Identity pass-through for non-matched elements
                        finalElements.add(element);
                    }
                    idx++;
                }
                return finalElements;
            }
            return resultElements;
        }
    }
}