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
import static studio.phaseshift.metatron.isa.m.mInstSet.ONE_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.mult_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.one_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
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
     * The slots of both lsts, in order. A lst is slot-oriented, never a bag of copies: each operand's coefficient is
     * read into its slots ONCE — {@link #elements()} already carries it there — so {@code [] + {2}[1,2]} is
     * {@code [{2}1,{2}2]}, and the result stands alone ({@code c = 1}).
     */
    default Lst plus(final Lst rhs) {
        final List<Obj> list = new ArrayList<>();
        this.<Obj>elements().forEach(list::add);
        rhs.<Obj>elements().forEach(list::add);
        return this.<Lst>jvm(list).c(cInt::one);
    }

    @Override
    default Lst zero() {
        return lst(new ArrayList<>());
    }

    /**
     * The slots of both lsts multiplied pairwise, lhs-major — {@code [x,y]·[a,b]} is {@code [x·a,x·b,y·a,y·b]} — by
     * each slot type's own product, so a lst multiplies as the ordered sum of its slots ({@link #plus}): the product
     * is right-distributive, and associative wherever the slots' products are. A slot holding the one() instruction
     * is the unit on either side ({@link #one()}). A product that is noobj is no slot (arrows that do not meet add
     * nothing to a relation), and one that yields several objects is a slot each (an arrow whose join reaches two
     * vertices is two arrows).
     */
    default Lst mult(final Lst rhs) {
        final List<Obj> list = new ArrayList<>();
        this.<Obj>elements().forEach(a -> rhs.<Obj>elements().forEach(b ->
                (isOne(a) ? b : isOne(b) ? a : mult_(b).apply(a)).stream().forEach(list::add)));
        return this.<Lst>jvm(list).c(cInt::one);
    }

    /**
     * The unit of {@link #mult}: one slot holding the one() instruction. The instruction stands for the one of
     * whatever slot it meets, so this one finite lst is the unit for lsts of ints, of reals, of arrows, … — a family
     * no single value could be.
     */
    default Lst one() {
        return lst(one_().tryToInst());
    }

    private static boolean isOne(final Obj slot) {
        return slot.isObjInst() && slot.asInst().tid().path().equals(ONE_INST_TID.path());
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
                    instC(AS_INST_TID.dom(LST_TID).rng(URI_TID), lst(T(LST_TID.poly(URI_TID))), (lhs, inst) -> lhs.lstValue().stream().reduce(uri(""), (a, b) -> uri(a.uriValue().extend(b.uriValue())))),
                    instC(AS_INST_TID.dom(LST_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lhs.tid(boundOrDeclared(lhs.asLst(), inst.arg(0).vidOrTid())).c(c -> c.mult(lhs.c()))),
                    instC(AS_INST_TID.dom(LST_TID).rng(CODE_TID), lst(T(CODE_TID)), (lhs, inst) -> code(lhs.asLst().jvm().stream().flatMap(o -> o.isCall() ? o.asCall().insts().stream() : map_(o).tryToInst().insts().stream()).toList()).c(c -> c.mult(lhs.c())))
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
                            Stream.generate(() -> withoutSpan(entry)).limit(Math.max(0L, entry.tid().b().min())) :
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
         * EQUAL NEIGHBOURS SHARE ONE INDEX (Marko, 2026-11-18: equal objs sort to one index as a point): a run of equal
         * objs in a sorted sequence becomes one entry carrying the run's whole mass, so {@code 1,1,2} lays as
         * {@code [{2}1,2]}, and an entry that came bulked keeps its mass. It is the one rule for the folds that answer a
         * sorted lst, {@code order} and its combiner {@code kmerge}: the same bag sorts to the same lst whether its equals
         * were bulked before the sort or met in it, so a sort sharded across places agrees with the sort held home.
         */
        public static List<Obj> bulkAdjacent(final List<Obj> sorted) {
            final List<Obj> bulked = new ArrayList<>(sorted.size());
            for (final Obj obj : sorted) {
                if (!bulked.isEmpty() && bulked.getLast().clessEquals(obj))
                    bulked.set(bulked.size() - 1, bulked.getLast().c(c -> c.plus(obj.c())));
                else
                    bulked.add(obj);
            }
            return bulked;
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