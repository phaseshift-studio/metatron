/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.space;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Poly;
import studio.phaseshift.metatron.lang.obj.Uri;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

public interface Space extends Poly, AutoCloseable {

    fURI MTRON_TID = fURI.of("/mtron");
    fURI MTRON_SPACE_TID = MTRON_TID.extend("space");

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
            Graphitty.log(space.getClass()).warn("cloning a space does not create a copy of that space");
        }

        public static Obj resolveRead(final Space space, final fURI vid, final Function<fURI, Map<fURI, Obj>> resolvedReader) { //final Map<fURI, Obj> store) {
            if (false && vid.isBranch()) {
                // pattern/branch
                if (vid.hasPattern()) {
                    Graphitty.log(space).info("processing pattern %s", vid.toUri());
                    return new MObjs((Iterable) resolvedReader.apply(fURI.of("#")).entrySet()
                            .stream()
                            .flatMap(kv -> kv.getValue().isRec() ? kv
                                    .getValue()
                                    .recValue()
                                    .entrySet()
                                    .stream()
                                    .filter(kv2 -> !kv2.getValue().isNoObj())
                                    .flatMap(kv2 -> Map.of(kv.getKey().extend(kv2.getKey().uriValue()), kv2.getValue()).entrySet().stream()) : Stream.of(kv))
                            .flatMap(kv -> Map.of(kv.getKey().toUri(), kv.getValue()).entrySet().stream())
                            .filter(kv -> {
                                final boolean check = kv.getKey().matches(vid.toUri()) || kv.getKey().matches(vid.retractPattern().asNode().toUri());
                                Graphitty.log(space).info("checking %s against %s at %s [%s]", vid.asNode(), kv.getValue(), kv.getKey(), check ? "{{g}}OK{{X}}" : "{{r}}X{{X}}");
                                return check;
                            })
                            .map(kv -> MRel.of(kv.getKey(), kv.getValue())).toList());
                } else {
                    // resolved/branch
                    Graphitty.log(space).info("searching %s", vid.extend("+").toUri());
                    return space.read(vid.extend("+").asBranch());// new SObj.Objs(List.of(new SObj.Rel(Pair.with(SObj.Uri.of(addr), this.store.getOrDefault(addr, NoObj.of())), REL_URI, fURI.NONE)), OBJS_URI, fURI.NONE);
                }
            } else {
                Map<Uri, Obj> map = new LinkedHashMap<>();
                if (vid.hasPattern()) {
                    resolvedReader.apply(fURI.of("#")).entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().matches(vid))
                            .forEach(kv ->
                                    map.put(kv.getKey().toUri(), kv.getValue()));
                } else if (resolvedReader.apply(fURI.of("#")).containsKey(vid))
                    map.put(vid.toUri(), resolvedReader.apply(fURI.of("#")).get(vid));
                if (map.isEmpty()) {
                    final Optional<Pair<fURI, Poly>> pair = Space.Helpers.locateBasePoly(space, vid);
                    if (pair.isPresent()) {
                        final Poly poly = pair.get().getValue1();
                        //    LOG.trace("base poly found at %s: %s", pair.get().getValue0(), poly);
                        final fURI furiSubpath = vid.removeSubpath(pair.get().getValue0()).asNode();
                        ///    LOG.trace("searching base poly %s for %s", poly, furiSubpath.toUri());
                        final Obj readObj = poly.at(furiSubpath.toUri());
                        //    LOG.trace("located poly obj %s in %s", readObj, poly);
                        if (!readObj.isNoObj())
                            map.put(vid.retractPattern().toUri(), readObj);
                    }
                }
                if (map.isEmpty())
                    return NoObj.single();
                else if (vid.isNode()) {
                    return MObjs.ofUsage(new ArrayList<>(map.values())); // TODO: no need to maintain a map, a list will do
                } else {
                    return MObjs.ofUsage(map.entrySet().stream().map(kv -> MRel.of(kv.getKey(), kv.getValue())));
                }
            }
        }

        public static void resolveWrite(final fURI vid, final fURI stepvid, final Obj obj, final BiConsumer<fURI, Obj> resolveWriter) {
            if (obj.isRec()) {
                obj.recValue().forEach((key, value) -> {
                    final fURI nextStepAddr = stepvid.extend(key.uriValue());
                    // System.out.println("!!!!" + nextStepAddr);
                    // final fURI resolvedKey = addr.hasPattern() ? addr.extend(resolvedAddr) : extendedKey;
                    if (value.isRec()) {
                        if (nextStepAddr.isBranch()) {
                            Space.Helpers.resolveWrite(vid, nextStepAddr, value, resolveWriter);
                        } else {
                            final Map<Obj, Obj> submap = new LinkedHashMap<>();
                            value.recValue()
                                    .entrySet()
                                    .stream()
                                    .filter(kv -> nextStepAddr.extend(kv.getKey().uriValue()).matches(vid))
                                    .forEach(kv -> submap.put(kv.getKey(), kv.getValue()));
                            //    System.out.println("SUBMAP TO WRITE: " + submap);
                            resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));
                        }
                    } else if (nextStepAddr.retract().matches(vid)) {
                        //  System.out.println("WRITING VALUE: " + nextStepAddr + "~" + vid);
                        resolveWriter.accept(nextStepAddr, value);
                    }
                    //System.out.println("JUST CHECKING VALUE: " + nextStepAddr + "~" + vid);
                });
            } else if (stepvid.matches(vid)) {
                resolveWriter.accept(stepvid, obj);
            }
        }


        public static Optional<Pair<fURI, Poly>> locateBasePoly(final Space space, final fURI furi) {
            fURI newFuri = furi.retract().asNode();
            Obj obj = NoObj.single();
            while (!newFuri.segments().isEmpty()) {
                obj = space.read(newFuri);
                if (!obj.isNoObj())
                    break;
                newFuri = newFuri.retract().asNode();
            }
            if (obj.isPoly()) {
                return Optional.of(Pair.with(newFuri.retractPattern(), obj.as()));
            } else return Optional.empty();
        }
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    default Space clone(final Object value, final fURI tid, final fURI vid) {
        Space.Helpers.noCloneWarning(this);
        return this;
    }

    @Override
    Object value();

    fURI pattern();

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }

    Obj read(final fURI vid);

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    Obj write(final fURI vid, final Obj obj);

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
    default List<Obj> elements() {
        return List.of();

    }

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }


}
