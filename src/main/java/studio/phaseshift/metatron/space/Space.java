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

package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Space extends Poly, AutoCloseable {

    fURI MTRON_TID = fURI.of("/mtron");
    fURI MTRON_SPACE_TID = MTRON_TID.extend("space");

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    default Space clone(final Object value, final fURI tid, final fURI vid) {
        Space.Helpers.noCloneWarning(this);
        return this;
    }

    Qs qs();

    fURI pattern();

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
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

    void append(final fURI addr, final Obj... obj);

    @Override
    default Obj apply(final Obj other) {
        return this;
    }

    @Override
    default long count() {
        return 0;
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        return (O) this.read(key.uriValue());
    }

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }

    @Override
    default <O extends Obj> Iterable<O> elements() {
        return (Iterable<O>) this.value();
    }

    class Helpers {
        public static String spaceToString(final Space space) {
            return Graphitty.string("{{b}}" + space.tid() + "{{g}}::[{{c}}pattern:{{b}}" + space.pattern() + "{{g}}]@{{b}}" + space.vid() + "{{X}}");
        }

        public static int spaceHashCode(final Space space) {
            return Objects.hash(space.tid(), space.vid());
        }

        public static boolean spaceEquals(final Space space, final Object other) {
            return other instanceof Space && ((Space) other).tid().equals(space.tid()) && ((Space) other).vid().equals(space.vid());
        }

        public static void noCloneWarning(final Space space) {
            Graphitty.log(space.getClass()).warn("the clone of a space is the space itself");
        }

        public static Obj resolveRead(final Space space, final fURI vid, final Function<fURI, Map<fURI, Obj>> directReader) { //final Map<fURI, Obj> store) {
            final Map<Uri, Obj> map = new LinkedHashMap<>();
            directReader.apply(vid).forEach((key, value) -> map.put(key.toUri(), value));
            if (map.isEmpty()) {
                directReader.apply(vid.isBranch() ? vid.extend(fURI.ONE_WILD_STRING) : vid.asNode()).forEach((key, value) -> {
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
            final Pair<fURI, Poly> base = Space.Helpers.locateBasePoly(space, vid);
            if (null != base) {
                final Poly poly = base.get1();
                Graphitty.log(space).trace("base poly found at %s: %s", base.get0(), poly);
                final fURI relativeVid = vid.removePrefix(base.get0()).asNode();
                Graphitty.log(space).trace("searching for %s in base poly %s", relativeVid.toUri(), poly);
                final Obj readObj = poly.at(relativeVid.toUri());
                Graphitty.log(space).trace("located poly obj %s in %s", readObj, poly);
                if (!readObj.isNoObj())
                    map.put(vid.retractPattern().toUri(), readObj);
            }
            if (vid.isNode()) {
                return MObjs.ofUsage(new ArrayList<>(map.values())); // TODO: no need to maintain a map, a list will do
            } else {
                return MObjs.ofUsage(map.entrySet().stream().map(kv -> MRel.of(kv.getKey(), kv.getValue())));
            }
        }

        public static void resolveWrite(final Space space, final fURI vid, final Obj obj, final BiConsumer<fURI, Obj> directWriter) {
            final Obj current = space.read(vid);
            if (!current.isNoObj() && vid.isNode()) {
                directWriter.accept(vid, obj);
            } else {
                final Pair<fURI, Poly> base = Space.Helpers.locateBasePoly(space, vid);
                if (null == base) {
                    if (vid.isNode() || !obj.isPoly()) {
                        directWriter.accept(vid, obj);
                    } else if (obj.isRec()) { // branch
                        obj.recValue().forEach((key, value) -> Helpers.resolveWrite(space, vid.extend(key.uriValue()), value, directWriter));
                    } else if (obj.isLst()) {
                        for (int i = 0; i < obj.lstValue().size(); i++) { // branch
                            Helpers.resolveWrite(space, vid.extend(String.valueOf(i)), obj.lstValue().get(i), directWriter);
                        }
                    }
                } else if (vid.isNode() || !obj.isPoly()) {
                    if (base.get1().isRec())
                        Space.Helpers.resolveWrite(space, base.get0(), base.get1().<Rec>as().at(uri(vid.removePrefix(base.get0())), obj), directWriter);
                    else if (base.get1().isLst())
                        Space.Helpers.resolveWrite(space, base.get0(), base.get1().<Lst>as().append(obj), directWriter);
                    else {
                        //throw MTronException.of("unknown poly: %s %s %s", base.get1(), vid, obj);
                        directWriter.accept(vid, obj);
                    }
                } else if (base.get1().isRec()) {
                    if (obj.isRec()) {
                        obj.recValue()
                                .entrySet()
                                .stream()
                                //.filter(kv -> nextStepAddr.extend(kv.getKey().uriValue()).matches(vid))
                                //.forEach(kv -> submap.put(kv.getKey(), kv.getValue()));
                                .forEach(kv -> Space.Helpers.resolveWrite(space, kv.getKey().uriValue(), kv.getValue(), directWriter));
                        // resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));

                    } else {
                        directWriter.accept(vid, obj);
                    }
                } else if (base.get1().isLst()) {
                    Lst newLst = base.get1().<Lst>as().at(uri(vid.removePrefix(base.get0()).pretract()), obj);
                    Space.Helpers.resolveWrite(space, vid, newLst, directWriter);
                }
            }
        }


        public static Pair<fURI, Poly> locateBasePoly(final Space space, final fURI furi) {
            fURI newFuri = furi.retract().asNode();
            Obj obj = NoObj.single();
            while (!newFuri.segments().isEmpty()) {
                obj = space.read(newFuri);
                if (!obj.isNoObj())
                    break;
                newFuri = newFuri.retract().asNode();
            }
            return obj.isPoly() ? Pair.with(newFuri.retractPattern(), obj.as()) : null;
        }
    }

}
