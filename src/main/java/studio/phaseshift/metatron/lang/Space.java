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

package studio.phaseshift.metatron.lang;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.mach.type.impl.MMachine;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Space extends Rec, Closeable {

    public enum Status {
        paused,
        active
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    default Status status() {
        return Status.valueOf(this.at(Tokens.STATUS).uriValue().toString());
    }

    default Space status(final Status status) {
        return (Space) this.put(Tokens.STATUS, uri(status.name()));
    }

    default Lst qs() {
        return this.jvm().getOrDefault(uri(Tokens.Q), lst()).as();
    }

    fURI pattern();

    Object sjvm();

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }

    default void onPut(final fURI key, final Obj value) {
    }

    Obj read(final fURI vid);

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    Obj write(final fURI vid, final Obj obj);

    default Obj[] write(final Object... kv) {
        int count = (int) ((double) kv.length / 2.0d);
        int running = 0;
        final Obj[] results = new Obj[count];
        for (int i = 0; i < kv.length; i = i + 2) {
            results[running++] = this.write((fURI) kv[i], (Obj) kv[i + 1]);
        }
        return results;
    }

    default Space pause() {
        return (Space) this.put(Tokens.STATUS, uri(Tokens.PAUSED));
    }

    @Override
    default void close() {
        //Common.close(this.sjvm());
        Common.close(this.jvm());
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
            //return Graphitty.string("{{b}}" + space.tid() + "{{g}}::[{{c}}pattern:{{b}}" + space.pattern() + "{{g}}]{{X}}");
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

        public static String toNativeSpace(final fURI vid, final Pair<String, String> rewrite) {
            return null == rewrite ? vid.toString() : rewrite.get1() + vid.toString().replaceFirst(rewrite.get0(), "");
        }

        public static fURI fromNativeSpace(final String vid, final Pair<String, String> rewrite) {
            return null == rewrite ? f(vid) : f(rewrite.get0() + vid.replaceFirst(rewrite.get1(), ""));
        }

        public static Obj resolveApply(final Space space, final Obj rhs) {
            if (rhs.isCode()) {
                return MMachine.of(rhs.as()).apply();
            } else if (rhs.isInst()) {
                return rhs.<Inst>as().apply();
            } else {
                return rhs;
            }
        }

        public static List<Pair<fURI, Obj>> unrollPoly(final fURI polyvid, final Poly<?, ?> poly, final fURI pattern) {
            final List<Pair<fURI, Obj>> results = new ArrayList<>();
            poly.indexedStream()
                    .filter(r -> r.second().isPoly() || polyvid.extend(f(r.first().jvm().toString())).matches(pattern))
                    .forEach(r -> {
                        final fURI key = polyvid.extend(f(r.first().jvm().toString()));
                        if (!r.second().isPoly() || key.matches(pattern))
                            results.add(Pair.with(key, r.second()));
                        else if (r.second().isPoly())
                            results.addAll(unrollPoly(key, r.second().as(), pattern));
                    });
            return results;
        }

        public static Obj resolveRead(final Space space, final fURI pattern, final Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader) { //final Map<fURI, Obj> store) {
            final Set<Pair<Uri, Obj>> listing = new HashSet<>();
            directReader.apply(pattern).forEachRemaining(kv -> listing.add(Pair.with(kv.get0().toUri(), kv.get1())));
            if (listing.isEmpty() && pattern.isBranch()) {
                directReader.apply(pattern.isBranch() ? pattern.extend(fURI.ONE_WILD_STRING) : pattern.asNode()).forEachRemaining(kv -> {
                    if (kv.get1().isRec()) {
                        kv.get1().recValue().forEach((key2, value2) -> listing.add(Pair.with(uri(kv.get0().extend(key2.uriValue())), value2)));
                    } else if (kv.get1().isLst()) {
                        for (int i = 0; i < kv.get1().lstValue().size(); i++) {
                            listing.add(Pair.with(uri(String.valueOf(i)), kv.get1().lstValue().get(i)));
                        }
                    } else {
                        listing.add(Pair.with(kv.get0().toUri(), kv.get1()));
                    }
                });
            }
            final Pair<fURI, Poly> base = Helper.locateBasePoly(space, pattern);
            if (null != base) {
                final Poly poly = base.get1();
                Graphitty.log(space).trace("base poly found at %s: %s", base.get0(), poly);
                unrollPoly(base.get0(), poly, pattern).forEach(kv -> listing.add(Pair.with(kv.get0().toUri(), kv.get1())));
            }
            return pattern.isNode() ?
                    objs(listing.stream().map(Pair::get1)) :
                    objs(listing.stream().map(kv -> rel(kv.get0(), kv.get1())));
        }

        public static Obj resolveWrite(final Space space, final fURI vid, final Obj obj, final BiFunction<fURI, Obj, Obj> directWriter, final Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader) {
            final Iterator<Tuple.Pair<fURI, Obj>> current = directReader.apply(vid);
            if (current.hasNext() && vid.isNode()) {
                return directWriter.apply(vid, obj);
            } else {
                final Pair<fURI, Poly> base = Helper.locateBasePoly(space, vid);
                if (null == base) {
                    if (vid.isNode() || !obj.isPoly()) {
                        return directWriter.apply(vid, obj);
                    } else if (obj.isRec()) { // branch
                        obj.recValue().forEach((key, value) -> Helper.resolveWrite(space, vid.extend(key.uriValue()), value, directWriter, directReader));
                    } else if (obj.isLst()) {
                        for (int i = 0; i < obj.lstValue().size(); i++) { // branch
                            Helper.resolveWrite(space, vid.extend(String.valueOf(i)), obj.lstValue().get(i), directWriter, directReader);
                        }
                    }
                } else if (vid.isNode() || !obj.isPoly()) {
                    if (base.get1().isRec())
                        Helper.resolveWrite(space, base.get0(), base.get1().<Rec>as().put(uri(vid.removePrefix(base.get0())), obj), directWriter, directReader);
                    else if (base.get1().isLst())
                        Helper.resolveWrite(space, base.get0(), base.get1().<Lst>as().append(obj), directWriter, directReader);
                    else {
                        //throw MTronException.of("unknown poly: %s %s %s", base.get1(), vid, obj);
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
                                .forEach(kv -> Helper.resolveWrite(space, kv.getKey().uriValue(), kv.getValue(), directWriter, directReader));
                        // resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));

                    } else {
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.get1().isLst()) {
                    Lst newLst = base.get1().<Lst>as().at(uri(vid.removePrefix(base.get0()).pretract()), obj, Lst.IMMUTABLE);
                    Helper.resolveWrite(space, vid, newLst, directWriter, directReader);
                }
            }
            return obj;
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

}
