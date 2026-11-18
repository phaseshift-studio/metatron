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
            for (final String slot : poly) {
                // a cheap spelling guard first: only an all-caps NAME can be a variable, so the slot's own text is
                // read before the furi parser is asked about it (a `,` or a `+` slot is not a name at all)
                final String text = null == slot ? "" : slot.trim();
                if (!text.isEmpty() && Character.isUpperCase(text.charAt(0)) && Singleton.f(text).isGeneric())
                    return true;
            }
            return false;
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
            final List<String> poly = tid.poly();
            final List<Obj> slots = value.lstValue();
            if (poly.size() != slots.size())
                return null; // arity: lst[A,B]::[1] has no second slot to bind B from
            final Map<fURI, fURI> bindings = new LinkedHashMap<>();
            final List<String> bound = new ArrayList<>(poly.size());
            for (int i = 0; i < poly.size(); i++) {
                final String declared = poly.get(i);
                final fURI slot = Singleton.f(declared.trim());
                final Obj slotValue = null == slots.get(i) ? noobj() : slots.get(i);
                if (!slot.isGeneric()) {
                    if (!slotValue.test(T(slot))) {
                        return null;
                    }
                    bound.add(declared);
                    continue;
                }
                final fURI type = slotValue.tid();
                final fURI prior = bindings.putIfAbsent(slot.big(), type);
                if (null != prior && !prior.equals(type))
                    return null; // a contradiction: the one variable would be two types
                bound.add((null == prior ? type : prior).toString());
            }
            return tid.poly(bound);
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
         * A VALUE'S SPAN IS A RUN OF INDICES (Marko, 2026-11-17): a lst is the axis a value's span unfolds along, so it
         * lays {@code 3{2}} as a 3 at each of two indices, {@code [3{2}]} is {@code [3,3]}, a lst of two, while
         * {@code [{2}3]} keeps two 3s at its one index. A stream has no axis, so there the span stays curled; an
         * instruction or a type keeps its span, a run of its own, at one index. The entries as they are when none lays.
         */
        public static List<Obj> laid(final List<Obj> entries) {
            final Predicate<Obj> lays = entry -> (entry.isMono() || entry.isPoly()) && !entry.isType() &&
                    !entry.tid().b().isOne() && entry.tid().b().isExact() && null != entry.tid().b().min();
            return entries.stream().noneMatch(lays) ? entries : entries.stream()
                    .flatMap(entry -> lays.test(entry) ?
                            Stream.generate(() -> entry.tid(entry.tid().b(null))).limit(Math.max(0L, entry.tid().b().min())) :
                            Stream.of(entry))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /**
         * A LST TESTS A LST INDEX BY INDEX (Marko, 2026-11-17). An index is an address that is not the obj, so a point
         * is mass AT an index and a span a run OF indices: {@code [{2}3]} is two 3s at index 0, a lst of one index, and
         * {@code [3{2}]} a 3 at each of indices 0 and 1, a lst of two. Only a value's span lays indices, a value having no
         * product of its own to be a power in: an instruction's span is its run, one step held at one index, and a
         * type's is a run of a filter, P·P = P, so {@code [{2}int::T]} is {@code [int::T]}, with a zero span the
         * identity, which takes any obj. An lhs index passes the rhs index at its place when its mass is within the rhs
         * index's (a type's point times its own count {@code ::T{n}}, a value's point, a stream's count) and every copy
         * it holds is one of the rhs index's, asked at the domain gate ({@link Inst.Helper#filterOnDomainAllowUnique}):
         * {@code [{2}3]} is an {@code [int{2}::T]}, {@code [3{2}]} is not, and {@code [{2}3,5]} is no
         * {@code [int::T,int::T,int::T]}. Past the last rhs index the
         * lhs is open, even inside a value's run ({@code [3{2}]} is an {@code [int::T]}, {@code [3{2},5{2}]} an
         * {@code [int::T,int::T,int::T]}), and an rhs index past the last lhs index is one the lhs leaves empty, so it
         * passes only if its mass admits zero. A lst TYPE's bracket reads its types as values
         * ({@link Type.Helper#testTensor}).
         */
        public static boolean testIndices(final Lst lhs, final Lst rhs) {
            final Function<Lst, List<Obj>> resolved = lst ->
                    laid(lst.lstValue().stream().map(entry -> (Obj) entry.autoResolve(lhs)).toList());
            final Function<Obj, cInt> mass = entry -> entry.isType() || entry.isCall() ? entry.tid().d().mult(entry.isType() &&
                    null != entry.vid() && TYPE_TID.equals(entry.vid().basePath()) ? entry.vid().c() : cInt.ONE()) : entry.c();
            final List<Obj> lefts = resolved.apply(lhs);
            final List<Obj> rights = resolved.apply(rhs);
            for (int i = 0; i < rights.size(); i++) {
                final Obj right = rights.get(i);
                if (i >= lefts.size()) {
                    if (!mass.apply(right).isZeroable())
                        return false;
                    continue;
                }
                final Obj left = lefts.get(i);
                final cInt has = mass.apply(left);
                if (!has.within(mass.apply(right)))
                    return false;
                if (!has.isZero() && !(right.isType() && right.tid().b().isZero())) {
                    // a type is asked at the domain gate, so {3}5 is an int as 5 is; an instruction or a value at the
                    // index is asked as a pattern is, each copy alone (is(gt(3)) runs its predicate)
                    final Obj unit = Type.Helper.unit(right);
                    if (!left.stream().allMatch(copy -> right.isType() ?
                            Inst.Helper.filterOnDomainAllowUnique(Type.Helper.unit(copy), unit) :
                            Type.Helper.unit(copy).test(unit)))
                        return false;
                }
            }
            return true;
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