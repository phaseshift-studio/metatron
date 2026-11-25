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
import java.util.stream.Stream;

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
                    instC(AS_INST_TID.dom(LST_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(Helper.path(lhs.asLst()))),
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
         * THE CANONICAL SUM OF LSTS ({@link Lst#plus}), uri's {@code fURI.Helper.sum} over objects instead of segments.
         * Each lst counts as its words ({@link #words}): like words add their coefficients, and a word at zero drops.
         * What is left is written as one lst, the prefix and suffix every word shares around one brace of the middles,
         * sorted by their text. A middle of one letter is that letter, and any other is a sub-word lst. So is one
         * letter that is itself a lst, wrapped as {@code [[x,y]]}, since a brace splices the lsts it holds. A single word
         * is written alone, its coefficient on its first letter ({@link #scaled}), and a sum of nothing, or of words that
         * cancel, is the zero. Summing a stream at once is the fold of {@link Lst#plus} without re-expanding each
         * partial sum.
         */
        public static Lst sum(final Stream<Lst> lsts) {
            final Map<List<Obj>, cInt> words = new LinkedHashMap<>();
            lsts.forEach(lst -> words(lst.lstValue(), lst.tid().d()).forEach((word, c) -> words.merge(word, c, cInt::plus)));
            final List<Map.Entry<List<Obj>, cInt>> live = words.entrySet().stream().filter(word -> !word.getValue().isZero()).toList();
            if (live.isEmpty())
                return lst_noobj();
            if (1 == live.size())
                return scaled(live.getFirst().getKey(), live.getFirst().getValue());
            final List<Obj> first = live.getFirst().getKey();
            int shared = 0;
            for (; shared < first.size(); shared++) {
                final int i = shared;
                if (!live.stream().allMatch(word -> i < word.getKey().size() && word.getKey().get(i).equals(first.get(i))))
                    break;
            }
            final int prefix = shared;
            for (shared = 0; shared < first.size() - prefix; shared++) {
                final int j = shared;
                final Obj letter = first.get(first.size() - 1 - j);
                if (!live.stream().allMatch(word -> j < word.getKey().size() - prefix && word.getKey().get(word.getKey().size() - 1 - j).equals(letter)))
                    break;
            }
            final int suffix = shared;
            final List<Obj> slots = new ArrayList<>(first.subList(0, prefix));
            // the middles in the order of their letters' text, a middle before every middle it begins, as a path sorts
            slots.add(objs(live.stream()
                    .map(word -> Map.entry(word.getKey().subList(prefix, word.getKey().size() - suffix), word.getValue()))
                    .sorted(Comparator.comparing(middle -> middle.getKey().stream().map(Obj::toString).toArray(String[]::new), Arrays::compare))
                    .map(middle -> 1 == middle.getKey().size() && !middle.getKey().getFirst().isLst()
                            ? middle.getKey().getFirst().d(middle.getValue())
                            : lst(new ArrayList<>(middle.getKey())).d(middle.getValue()))
                    .toList()));
            slots.addAll(first.subList(first.size() - suffix, first.size()));
            return lst(slots);
        }

        /**
         * THE WORDS A LST IS (Definition 11), each its letters at point one with its coefficient, the product of the
         * points along it and the lst's own: {@code [{2}a,b]} is the word {@code a b} twice, as a lst is multilinear in
         * its letters (Claim 24). A brace is a sum at one moment whose members are letters, or lsts, which are sub-words
         * that splice (Marko, 2026-11-25): {@code [x,{[a,b],c}]} is {@code x a b + x c}, as {@code <x/{a/b,c}>} is. A
         * hole, noobj, is the zero letter, so no word runs through it. A brace multiplies the words, so their count is
         * bounded before it is built, in copies of the lst's own members, by the typer's unfold for lsts: a brace of many
         * members is as many words, while k braces of two are 2^k words from 2k members.
         */
        private static Map<List<Obj>, cInt> words(final List<Obj> slots, final cInt coefficient) {
            Map<List<Obj>, cInt> words = Map.of(List.of(), coefficient);
            long members = 0L;
            for (final Obj slot : slots) {
                final Map<List<Obj>, cInt> moments = new LinkedHashMap<>();
                slot.stream().forEach(member -> {
                    if (slot.isObjs() && member.isLst())
                        words(member.lstValue(), member.tid().d()).forEach((word, c) -> moments.merge(word, c, cInt::plus));
                    else
                        moments.merge(List.of(member.d(cInt.ONE())), member.tid().d(), cInt::plus);
                });
                members += Math.max(1L, moments.size());
                Typer.Helper.unfolds(Typer.UNFOLD_LST, Math.ceilDiv(Math.multiplyExact((long) words.size(), (long) moments.size()), members));
                final Map<List<Obj>, cInt> longer = new LinkedHashMap<>();
                words.forEach((word, c) -> moments.forEach((moment, d) -> {
                    final List<Obj> letters = new ArrayList<>(word);
                    letters.addAll(moment);
                    longer.merge(letters, c.mult(d), cInt::plus);
                }));
                words = longer;
            }
            return words;
        }

        /**
         * A word times a coefficient, written where a lst keeps its mass: on its first letter, as a lst is multilinear in
         * its letters (Claim 24), so {@code [a,b]} twice is {@code [{2}a,b]}, and a brace there scales each member. The
         * empty word has no letter to hold it, so a multiple of the unity other than itself is refused: as the lst's own
         * point, {@code {2}[,]}, it would float over the next plus, and {@code [,] + [,] + [,]} would be four of it. Inside
         * a brace it is a member like any other, {@code [a] + [a] + [a,b]} is {@code [a,{{2}[,],b}]}.
         */
        private static Lst scaled(final List<Obj> slots, final cInt coefficient) {
            if (coefficient.isOne())
                return lst(new ArrayList<>(slots));
            if (slots.isEmpty())
                throw MTronException.of("%s times the unity [,] has no letter to hold its mass: a lst keeps the mass of a sum on its letters", coefficient);
            final List<Obj> scaled = new ArrayList<>(slots);
            scaled.set(0, objs(scaled.getFirst().stream().map(member -> member.d(member.tid().d().mult(coefficient)))));
            return lst(scaled);
        }

        /**
         * A LST'S SPAN IS ITS POWER in the word ring (Marko, 2026-11-24 and 11-25; Definition 11), as a uri's is:
         * realized as a literal is parsed ({@code [1,2]{2}} is {@code [1,2,1,2]}), and by {@code pow(n)} at run time. A
         * power is n copies of the lst concatenated, the unity {@code [,]} at zero, and a negative span the converse's
         * power, so {@code [1,2,3]{-1}} is {@code [3,2,1]} ({@link #converse}). A span range is the sum of its powers,
         * {@code [a]{1,2}} being {@code [a,{[,],a}]}, a + a a. A power copies the lst's letters with their points, and
         * keeps the lst's own point once, outside it. The copies are bounded by the typer's unfold for lsts: an exact
         * span's, and a range's summed over its powers, both sides of zero together. An open side runs until a power is
         * the zero, and the powers of a lst that holds a word never are, so such a side is refused at the bound.
         */
        public static Lst power(final Lst lst, final cInt span) {
            final Long low = span.min();
            final Long high = span.max();
            if (null != low && low.equals(high))
                return copies(lst, low);
            final List<Lst> powers = new ArrayList<>();
            long held = 0L;
            // the powers at and above zero, then the converse's below it; a null end is an open side
            for (final long[] side : new long[][]{{null == low || low < 0L ? 0L : low, null == high ? Long.MAX_VALUE : high, 1L},
                    {null == high || high >= 0L ? -1L : high, null == low ? Long.MIN_VALUE + 1L : low, -1L}}) {
                for (long k = side[0]; side[2] > 0L ? k <= side[1] : k >= side[1]; k += side[2]) {
                    final Lst term = copies(lst, k);
                    if (words(term.lstValue(), cInt.ONE()).values().stream().allMatch(cInt::isZero))
                        break;
                    held = Math.addExact(held, Math.abs(k));
                    Typer.Helper.unfolds(Typer.UNFOLD_LST, held);
                    powers.add(term);
                }
            }
            return Helper.sum(powers.stream());
        }

        /**
         * the lst's exact power n ({@link #power}): n copies of it, of its converse for a negative n, bounded by the
         * typer's unfold for lsts
         */
        private static Lst copies(final Lst lst, final long n) {
            if (Long.MIN_VALUE == n)
                throw MTronException.of("%s has no power at %s: the span has no positive counterpart", lst, n);
            Typer.Helper.unfolds(Typer.UNFOLD_LST, n);
            final List<Obj> word = n < 0L ? converse(lst.lstValue()) : lst.lstValue();
            final List<Obj> slots = new ArrayList<>();
            for (long i = 0L; i < Math.abs(n); i++)
                slots.addAll(word);
            return lst.jvm(slots);
        }

        /**
         * THE FOLD, {@code >|} (Claim 22 of ring-theoretic-distributed-structures.md): a lst run as a program, each slot a
         * step applied to what the steps before it answered, a value the constant that answers it and an instruction
         * itself. A brace is a branch, each member stepping from what reached it, and a member lst is a sub-word, folded
         * as the program it is and counted once by its point, so the fold of a sum is the sum of the folds:
         * {@code [1,{[plus(2),plus(3)],[mult(2),plus(5)]}]>|} is {@code {6,7}}, as
         * {@code {[1,plus(2),plus(3)],[1,mult(2),plus(5)]}>|} is. A step after a branch applies to each obj the branch
         * answered, as a stream's steps do, and nothing is still one input, so a constant answers it. A record of data
         * that meets a record of data is their join, a record's product (Claim 28, Rec.Helper.isData), so a lst of
         * records folds to their walk: {@code [[a=>b],[b=>e]]>|} is {@code [a=>b=>e]}, while a rec that holds code applies
         * as any letter does.
         */
        public static Obj fold(final Lst word, final Obj lhs) {
            final BiFunction<Obj, Obj, Obj> step = (letter, obj) -> Rec.Helper.isData(obj) && Rec.Helper.isData(letter)
                    ? obj.asRec().mult(letter.asRec()) : letter.apply(obj);
            return word.values().reduce(lhs, (at, slot) -> objs((at.isNoObj() ? Stream.of(at) : at.stream())
                    .flatMap(obj -> !slot.isObjs() ? step.apply(slot, obj).stream() : slot.stream().flatMap(member -> member.isLst()
                            ? fold(member.d(cInt.ONE()).asLst(), obj).stream().map(folded -> folded.c(c -> c.mult(member.tid().d())))
                            : step.apply(member, obj).stream()))));
        }

        /**
         * THE PATH A LST OF ATOMS SPELLS, the word ring's embedding in the uri ring ({@code [a,b,c].as(uri)} is
         * {@code a/b/c}): the uri sum of its words ({@link #words}), each word its letters' segments with its coefficient
         * for the point. So the sum a lst factors is the sum a uri factors, {@code [a,{b,d},c]} spelling
         * {@code <a/{b,d}/c>}, and {@code (l + m).as(uri)} is {@code l.as(uri) + m.as(uri)}.
         */
        public static fURI path(final Lst lst) {
            return fURI.Helper.sum(words(lst.lstValue(), cInt.ONE()).entrySet().stream()
                    .filter(word -> !word.getValue().isZero())
                    .map(word -> word.getKey().stream().map(Obj::uriValue).reduce(fURI::extend).orElse(Singleton.f("")).d(word.getValue())));
        }

        /**
         * THE CONVERSE OF A LST, its span -1 (Claim 21): its letters in the reverse order, each held as it is, since a
         * letter's own converse is never needed, and the sub-words in a brace reversed with it, since a brace splices
         * them: {@code [x,{[a,b],c}]{-1}} is {@code [{[b,a],c},x]}. Points stay with their letters. The converse is an
         * involution and reverses a product, (u v)^-1 = v^-1 u^-1, but it is no inverse: {@code [a,b]} times its converse
         * is {@code [a,b,b,a]}.
         */
        private static List<Obj> converse(final List<Obj> slots) {
            return slots.reversed().stream()
                    .map(slot -> slot.isObjs() ? objs(slot.stream().map(member -> member.isLst() ? member.<Lst>jvm(converse(member.lstValue())) : member)) : slot)
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