/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.Qs;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.mach.type.impl.MMachine;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.Common;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        if (null != this.vid()) {
            Router.global().removeSpace(this.vid());
            //Router.global().write(this.vid().extend(fURI.ALL), noobj());
        }
        Common.close(this.sjvm());
        Common.close(this.jvm());
    }

    default Function<fURI, Map<fURI, Obj>> directReader() {
        return f -> Map.of();
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

        public static Obj resolveApply(final Space space, final Obj rhs) {
            if (rhs.isCode()) {
                return MMachine.of(rhs.as()).apply();
            } else if (rhs.isInst()) {
                return rhs.<Inst>as().apply();
            } else {
                return rhs;
            }
        }

        public static Map<fURI, Obj> unrollPoly(final Map<fURI, Obj> result, final fURI polyvid, final Poly poly, final fURI pattern) {
            poly.indexedStream()
                    .filter(r -> r.second().isPoly() || polyvid.extend(f(r.first().jvm().toString())).matches(pattern))
                    .forEach(r -> {
                        final fURI key = polyvid.extend(f(r.first().jvm().toString()));
                        if (!r.second().isPoly() || key.matches(pattern))
                            result.put(key, r.second());
                        if (r.second().isPoly())
                            unrollPoly(result, key, r.second().as(), pattern);
                    });

            return result;
        }

        public static Obj resolveRead(final Space space, final fURI pattern, final Function<fURI, Map<fURI, Obj>> directReader) { //final Map<fURI, Obj> store) {
            final Map<Uri, Obj> map = new LinkedHashMap<>();
            directReader.apply(pattern).forEach((key, value) -> map.put(key.toUri(), value));
            if (map.isEmpty() && pattern.isBranch()) {
                directReader.apply(pattern.isBranch() ? pattern.extend(fURI.ONE_WILD_STRING) : pattern.asNode()).forEach((key, value) -> {
                    if (value.isRec()) {
                        value.recValue().forEach((key2, value2) -> map.put(uri(key.extend(key2.uriValue())), value2));
                    } else if (value.isLst()) {
                        for (int i = 0; i < value.lstValue().size(); i++) {
                            map.put(uri(String.valueOf(i)), value.lstValue().get(i));
                        }
                    } else {
                        map.put(key.toUri(), value);
                    }
                });
            }
            final Pair<fURI, Poly> base = Helper.locateBasePoly(space, pattern);
            if (null != base) {
                final Poly poly = base.get1();
                Graphitty.log(space).trace("base poly found at %s: %s", base.get0(), poly);
                unrollPoly(new LinkedHashMap<>(), base.get0(), poly, pattern).forEach((key, value) -> map.put(key.toUri(), value));
            }
            return pattern.isNode() ?
                    objs(map.values()) :
                    objs(map.entrySet().stream().map(kv -> (Obj) rel(kv.getKey(), kv.getValue())).toList());
        }

        public static Obj resolveWrite(final Space space, final fURI vid, final Obj obj, final BiFunction<fURI, Obj, Obj> directWriter, final Function<fURI, Map<fURI, Obj>> directReader) {
            final Map<fURI, Obj> current = directReader.apply(vid);
            if (!current.isEmpty() && vid.isNode()) {
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
                                //.filter(kv -> nextStepAddr.extend(kv.getKey().uriValue()).matches(vid))
                                //.forEach(kv -> submap.put(kv.getKey(), kv.getValue()));
                                .forEach(kv -> Helper.resolveWrite(space, kv.getKey().uriValue(), kv.getValue(), directWriter, directReader));
                        // resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));

                    } else {
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.get1().isLst()) {
                    Lst newLst = base.get1().<Lst>as().at(uri(vid.removePrefix(base.get0()).pretract()), obj);
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
