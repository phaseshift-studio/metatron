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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.Stats;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Space extends Rec, Closeable {

    public static enum METHOD {FROM_SPACE, TO_SPACE}

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    default Lst qs() {
        return this.at(uri(Tokens.Q)).orElse(lst());
    }

    fURI pattern();

    Object sjvm();

    Map<Uri, Uri> routes();

    Stats stats();

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }

    Obj read(final fURI vid);

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    Obj write(final fURI vid, final Obj obj);

    fURI rewrite(final fURI furi, final boolean big);

    default Obj[] write(final Object... kv) {
        int count = (int) ((double) kv.length / 2.0d);
        int running = 0;
        final Obj[] results = new Obj[count];
        for (int i = 0; i < kv.length; i = i + 2) {
            results[running++] = this.write((fURI) kv[i], (Obj) kv[i + 1]);
        }
        return results;
    }

    @Override
    default void close() {
        try {
            CommonUtil.close(this.sjvm());
            //CommonUtil.close(this.jvm());
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            Space.Helper.closeSpace(this);
        }
    }

    default Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
        return f -> IteratorUtil.of();
    }

    default BiFunction<fURI, Obj, Obj> directWriter() {
        return (k, v) -> v;
    }

    @Override
    default Obj apply(final Obj other) {
        return Helper.resolveApply(this, other);
    }

    class Helper {

        public static void spaceCloseLog(final Obj source, final Space space) {
            source.logger().info("closed space %s", space);
        }

        public static void spaceOpenLog(final Obj source, final Space space) {
            if (space instanceof InstSet)
                source.logger().info("opened inst set %s", space);
            else
                source.logger().info("opened space %s", space);
        }

        public static String spaceToString(final Space space) {
            return Obj.Helper.objToString(space);
        }

        public static int spaceHashCode(final Space space) {
            return Objects.hash(space.tid(), space.vid());
        }

        public static boolean spaceEquals(final Space space, final Object other) {
            return other instanceof Space &&
                    ((Space) other).tid().equals(space.tid()) &&
                    (space.vid() != null && ((Space) other).vid() != null && ((Space) other).vid().equals(space.vid()));
        }

        public static void noCloneWarning(final Space space) {
            space.logger().warn("the clone of a space is the space itself");
        }


        public static fURI toRewrite(final fURI vid, final Pair<String, String> rewrite) {
            return null == rewrite ? vid : f(rewrite.get1() + vid.toString().replaceFirst(rewrite.get0(), ""));
        }

        public static fURI fromRewrite(final fURI vid, final Pair<String, String> rewrite) {
            return null == rewrite ? vid : f(rewrite.get0() + vid.toString().replaceFirst(rewrite.get1(), ""));
        }


        public static String toNativeSpace(final fURI vid, final Pair<String, String> rewrite) {
            return null == rewrite ? vid.toString() : rewrite.get1() + vid.toString().replaceFirst(rewrite.get0(), "");
        }


        public static fURI fromNativeSpace(final String vid, final Pair<String, String> rewrite) {
            return null == rewrite ? f(vid) : f(rewrite.get0() + vid.replaceFirst(rewrite.get1(), ""));
        }

        public static fURI fromNativeSpace(final fURI vid, Map<Uri, Uri> routes) {
            return routes.entrySet().stream()
                    .filter(e -> vid.toString().contains(e.getValue().uriValue().toString()))
                    .map(e -> e.getKey().uriValue().extend(vid.toString().replaceFirst(e.getValue().uriValue().toString(), "")))
                    .findFirst()
                    .orElse(vid);
        }

        public static fURI toNativeSpace(final fURI vid, Map<Uri, Uri> routes) {
            return routes.entrySet().stream()
                    .filter(e -> vid.toString().contains(e.getKey().uriValue().toString()))
                    .map(e -> e.getValue().uriValue().extend(vid.toString().replaceFirst(e.getKey().uriValue().toString(), "")))
                    .findFirst()
                    .orElse(vid);
        }

        public static Obj resolveApply(final Space space, final Obj rhs) {
            if (rhs.isCode()) {
                return SwarmMachine.of(rhs.as()).apply();
            } else if (rhs.isInst()) {
                return rhs.<Inst>as().apply();
            } else {
                return rhs;
            }
        }

        public static Tuple.Pair<String, String> extractRewrite(final Map<Obj, Obj> config) {
            final String prefix = config.containsKey(uri(Tokens.REWRITE)) ? config.get(uri(Tokens.REWRITE)).asRel().first().uriValue().toString() : "";
            final String prepend = config.containsKey(uri(Tokens.REWRITE)) ? config.get(uri(Tokens.REWRITE)).asRel().second().uriValue().toString() : "";
            return Tuple.Pair.with(prefix, prepend);
        }

        public static List<Pair<fURI, Obj>> unrollPoly(final fURI polyvid, final Poly<?, ?> poly, final fURI pattern) {
            final List<Pair<fURI, Obj>> results = new ArrayList<>();
            poly.indexedStream()
                    .filter(r -> r.jvm().get1().isPoly() || polyvid.extend(f(r.jvm().get0().jvm().toString())).test(pattern))
                    .forEach(r -> {
                        final fURI key = polyvid.extend(f(r.jvm().get0().jvm().toString()));
                        if (!r.jvm().get1().isPoly() || key.test(pattern))
                            results.add(Pair.with(key, r.jvm().get1()));
                        else if (r.jvm().get1().isPoly())
                            results.addAll(unrollPoly(key, r.jvm().get1().as(), pattern));
                    });
            return results;
        }

        public static Obj resolveRead(final Space space, final fURI pattern, final Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader) { //final Map<fURI, Obj> store) {
            final Set<Pair<Uri, Obj>> listing = new HashSet<>();
            directReader.apply(pattern).forEachRemaining(kv -> listing.add(Pair.with(kv.get0().toUri(), kv.get1())));
            if (listing.isEmpty()) {
                if (pattern.isBranch() && !pattern.hasPattern()) {
                    final Rec nestRec = rec();
                    directReader.apply(pattern.extend(fURI.ONE_WILD_STRING)).forEachRemaining(kv -> {
                        if (CommonUtil.isInt(kv.get0().name()))
                            listing.add(Pair.with(kv.get0().toUri(), kv.get1()));
                        else
                            nestRec.at(kv.get0().pretract(pattern.pathLength()).toUri(), kv.get1(), MUTABLE);
                    });
                    if (!nestRec.isEmpty())
                        listing.add(Pair.with(uri(pattern), nestRec));
                } else {
                    directReader.apply((pattern.isBranch() ? pattern.extend(fURI.ONE_WILD_STRING) : pattern.asBranch())).forEachRemaining(kv -> {
                        listing.add(Pair.with(kv.get0().toUri(), kv.get1()));
                    });
                }
            }
            if (listing.isEmpty() || pattern.hasPattern()) {
                final Pair<fURI, Poly> base = Helper.locateBasePoly(space, pattern);
                if (null != base) {
                    final Poly poly = base.get1();
                    Graphitty.log(space).trace("base poly found at %s: %s", base.get0(), poly);
                    unrollPoly(base.get0(), poly, pattern).forEach(kv -> listing.add(Pair.with(kv.get0().toUri(), kv.get1())));
                }
            }
            return pattern.isNode() ?
                    objs(listing.stream().map(Pair::get1).map(o -> o.autoResolve(o)).toList()) :
                    objs(listing.stream().map(kv -> rel(kv.get0(), kv.get1())));
        }

        private static Obj writeComplete(final fURI writePattern, final Obj newObj, final Obj currentObj) {
            //Router.global().logger().info("write complete for %s: %s => %s", writePattern, currentObj, newObj);
            if (newObj.isNoObj()) {
                currentObj.stream().forEach(CommonUtil::close);
            }
            return currentObj;

        }

        public static Obj resolveWrite(final GraphittyLogger LOG, final Space space, final fURI vid, Obj obj, final BiFunction<fURI, Obj, Obj> directWriter, final Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader) {
            if (Obj.Helper.isAuto(obj)) {
                LOG.info("evaluating auto %s and yielding result to: %s", obj, vid);
                obj = obj.apply();
            }

            final Iterator<Tuple.Pair<fURI, Obj>> current = directReader.apply(vid);
            if (current.hasNext() && vid.isNode()) {
                writeComplete(vid, obj, current.next().get1());
                return directWriter.apply(vid, obj);
            } else {
                final Pair<fURI, Poly> base = Helper.locateBasePoly(space, vid);
                if (null == base) {
                    if (vid.isNode() || !obj.isPoly()) {
                        return directWriter.apply(vid, obj);
                    } else if (obj.isRec()) { // branch
                        obj.recValue().forEach((key, value) -> Helper.resolveWrite(LOG, space, vid.extend(key.uriValue()), value, directWriter, directReader));
                    } else if (obj.isLst()) {
                        for (int i = 0; i < obj.lstValue().size(); i++) { // branch
                            Helper.resolveWrite(LOG, space, vid.extend(String.valueOf(i)), obj.lstValue().get(i), directWriter, directReader);
                        }
                    }
                } else if (vid.isNode() || !obj.isPoly()) {
                    if (base.get1().isRec())
                        Helper.resolveWrite(LOG, space, base.get0(), base.get1().<Rec>as().at(uri(vid.removePrefix(base.get0())), obj), directWriter, directReader);
                    else if (base.get1().isLst())
                        Helper.resolveWrite(LOG, space, base.get0(), base.get1().<Lst>as().append(obj), directWriter, directReader);
                    else {
                        //throw MTronException.of("unknown poly: %s %s %s", base.get1(), vid, obj);
                        writeComplete(vid, obj, base.get1());
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.get1().isRec()) {
                    if (obj.isRec()) {
                        obj.recValue()
                                .entrySet()
                                .stream()
                                .filter(kv -> !kv.getValue().isNoObj())
                                //.filter(kv -> nextStepAddr.extend(kv.getKey().uriValue()).matches(vid))
                                //.forEach(kv -> submap.put(kv.getKey(), kv.getValue()));
                                .forEach(kv -> Helper.resolveWrite(LOG, space, kv.getKey().uriValue(), kv.getValue(), directWriter, directReader));
                        // resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));

                    } else {
                        writeComplete(vid, obj, base.get1());
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.get1().isLst()) {
                    Lst newLst = base.get1().<Lst>as().at(uri(vid.removePrefix(base.get0()).pretract()), obj, Lst.IMMUTABLE);
                    Helper.resolveWrite(LOG, space, vid, newLst, directWriter, directReader);
                }
            }
            return obj;
        }

        public static void closeSpace(final Space space) {
            if (Router.loaded())
                Router.global().removeSpace(space.vid());
        }


        public static Pair<fURI, Poly> locateBasePoly(final Space space, final fURI furi) {
            boolean last = furi.segments().isEmpty();
            fURI newFuri = furi.retract().asNode();
            Obj obj = noobj();
            while (!last) {
                obj = space.read(newFuri);
                if (!obj.isNoObj())
                    break;
                last = newFuri.segments().isEmpty();
                newFuri = newFuri.retract().asNode();

            }
            return obj.isPoly() ? Pair.with(newFuri.retractPattern(), obj.as()) : null;
        }
    }

    final class SpaceType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    //instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                    //instC(CLOSE_INST_TID.dom(REC_TID).rng(NOOBJ_TID), lst(), (lhs, inst) -> Stream.of(noobj()).peek(o -> lhs.<Space>as().close()).findFirst().orElse(noobj()))
            ));
        }
    }
}