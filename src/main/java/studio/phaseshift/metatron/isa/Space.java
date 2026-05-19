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
import studio.phaseshift.metatron.furi.QProc;
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
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Space extends Rec, Closeable {

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    default Lst qs() {
        return this.at(uri(QPROC)).orElse(lst());
    }

    default Space addQ(final QProc qProc) {
        this.at(uri(QPROC), this.at(uri(QPROC)).orElse(lst()).add(qProc, MUTABLE), MUTABLE);
        return this;
    }

    fURI pattern();

    Object sjvm();

    Map<Uri, Uri> routes();

    Stats stats();

    default Obj read(final String vid) {
        return this.read(f(vid));
    }

    Obj read(final fURI vid);

    default Obj write(final String vid, final Obj obj) {
        return this.write(f(vid), obj);
    }

    Obj write(final fURI vid, final Obj obj);

    fURI redirect(final fURI furi, final boolean big);

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
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            Space.Helper.closeSpace(this);
        }
    }

    default Function<fURI, Iterator<IdObj>> directReader() {
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

        public static Iterator<IdObj> attachVIDs(final Iterator<IdObj> iterator) {
            return IteratorUtil.map(iterator, kv -> kv.obj().vid() != null ? kv : new IdObj(kv.furi(), kv.obj().selfVID(kv.furi())));
        }

        public static void spaceCloseLog(final Obj source, final Space space) {
            if (space instanceof InstSet)
                source.logger().info("closed inst set {{b}}%s", space.vid());
            else
                source.logger().info("closed space {{b}}%s {{g}}[{{c}}pattern: {{b}}%s{{g}}]", space.vid(), space.pattern());
        }

        public static void spaceOpenLog(final Obj source, final Space space) {
            if (space instanceof InstSet)
                source.logger().info("opened inst set {{b}}%s", space.vid());
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

        public static fURI routeToSpace(final fURI vid, Map<Uri, Uri> routes) {
            final Uri vidURI = uri(vid);
            return routes.entrySet().stream()
                    //.sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                    .filter(e -> vid.hasPrefix(e.getValue().apply(vidURI).uriValue()))
                    .map(e -> e.getKey().apply(vidURI).uriValue().extend(vid.toString().replaceFirst(e.getValue().apply(vidURI).uriValue().toString(), "")).q(vid.qMap()))
                    .findFirst()
                    .orElse(vid);
        }

        public static fURI routeFromSpace(final fURI vid, Map<Uri, Uri> routes) {
            final Uri vidURI = uri(vid);
            return routes.entrySet().stream()
                    //.sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                    .filter(e -> vid.hasPrefix(e.getKey().apply(vidURI).uriValue()))
                    .map(e -> {
                        fURI remainder = vid.removePrefix(e.getKey().apply(vidURI).uriValue());
                        // When the scheme prefix is stripped (e.g. "db:" → "") the
                        // remainder often starts with "/" ("/people/+").  fURI.extend()
                        // on an empty base preserves that leading slash as an empty
                        // first path segment, which breaks isTablePath() and hasPattern().
                       // if (remainder.startsWith("/")) remainder = remainder.substring(1);
                        return e.getValue().apply(vidURI).uriValue().extend(remainder).q(vid.qMap());
                    })
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

        public static List<IdObj> unrollPoly(final fURI polyvid, final Poly<?, ?> poly, final fURI pattern) {
            final List<IdObj> results = new ArrayList<>();
            if (!pattern.hasPattern()) {
                final Obj value = poly.at(pattern.removePrefix(polyvid));
                if (!value.isNoObj()) {
                    results.add(IdObj.of(pattern, value));
                }
            }
            if (results.isEmpty()) {
                poly.indexedStream()
                        .filter(r -> r.jvm().get1().isPoly() || polyvid.extend(f(r.jvm().get0().jvm().toString())).test(pattern))
                        .forEach(r -> {
                            final fURI key = polyvid.extend(f(r.jvm().get0().jvm().toString()));
                            if (!r.jvm().get1().isPoly() || key.test(pattern))
                                results.add(IdObj.of(key, r.jvm().get1()));
                            else if (r.jvm().get1().isPoly())
                                results.addAll(unrollPoly(key, r.jvm().get1().as(), pattern));
                        });
            }
            //poly.logger().error("unrolled poly %s %s %s => %s", polyvid, pattern, poly, results);
            return results;
        }

        public static Obj resolveRead(final Space space, final fURI pattern, final Function<fURI, Iterator<IdObj>> directReader) { //final Map<fURI, Obj> store) {
            final Set<UriObj> listing = new HashSet<>();
            directReader.apply(pattern).forEachRemaining(kv -> listing.add(UriObj.of(kv.furi().toUri(), kv.obj())));
            if (listing.isEmpty()) {
                if (pattern.isBranch() && !pattern.hasPattern()) {
                    final Rec nestRec = rec();
                    directReader.apply(pattern.extend(fURI.Singleton.WILD_ONE)).forEachRemaining(kv -> {
                        if (CommonUtil.isInt(kv.furi().name()))
                            listing.add(UriObj.of(kv.furi().toUri(), kv.obj()));
                        else
                            nestRec.at(kv.furi().pretract(pattern.segmentLength()).toUri(), kv.obj(), MUTABLE);
                    });
                    if (!nestRec.isEmpty())
                        listing.add(UriObj.of(uri(pattern), nestRec));
                } else {
                    /*if(pattern.hasPattern()) {
                        directReader.apply(pattern.retractPattern()).forEachRemaining(kv -> {
                            listing.add(UriObj.of(kv.furi().toUri(), kv.obj()));
                        });
                    }*/
                    directReader.apply((pattern.isBranch() ? pattern.extend(fURI.Singleton.WILD_ONE) : pattern.asBranch())).forEachRemaining(kv -> {
                        listing.add(UriObj.of(kv.furi().toUri(), kv.obj()));
                    });
                }
            }
            if (listing.isEmpty() || pattern.hasPattern()) {
                final IdObj base = Helper.locateBasePoly(space, pattern.basePath());
                if (null != base) {
                    final Poly<?, ?> poly = base.obj().as();
                    Graphitty.log(space).trace("base poly found at %s: %s", base.furi(), poly);
                    unrollPoly(base.furi(), poly, pattern).forEach(kv -> listing.add(UriObj.of(kv.furi().toUri(), kv.obj())));
                }
            }
            final Stream<UriObj> prefix = listing.stream().filter(kv -> !kv.obj().isNoObj() && !kv.uri().isNoObj());
            return pattern.isNode() ?
                    objs(prefix.map(UriObj::obj).map(o -> o.autoResolve(o)).toList()) :
                    objs(prefix.map(kv -> rel(kv.uri(), kv.obj())));
        }

        private static Obj writeComplete(final Obj newObj, final Obj currentObj) {
            //Router.global().logger().info("write complete for %s: %s => %s", writePattern, currentObj, newObj);
            if (newObj.isNoObj()) {
                currentObj.stream().forEach(CommonUtil::close);
            }
            return currentObj;

        }

        public static Obj resolveWrite(final GraphittyLogger LOG, final Space space, final fURI vid, Obj obj, final BiFunction<fURI, Obj, Obj> directWriter, final Function<fURI, Iterator<IdObj>> directReader) {
            // if (Obj.Helper.isAuto(obj)) {
            //   LOG.info("evaluating auto %s and yielding result to: %s", obj, vid);
            //     obj = obj.apply();
            //  }

            final Iterator<IdObj> current = directReader.apply(vid);
            if (current.hasNext() && vid.isNode()) {
                writeComplete(obj, current.next().obj());
                return directWriter.apply(vid, obj);
            } else {
                final IdObj base = Helper.locateBasePoly(space, vid.basePath());
                if (null == base) {
                    if (vid.isNode() || !obj.isPoly()) {
                        return directWriter.apply(vid, obj);
                    } else if (obj.isRec()) { // branch
                        obj.recValue().forEach((key, value) -> {
                            Helper.resolveWrite(LOG, space, vid.extend(key.uriValue()), value, directWriter, directReader);
                        });
                    } else if (obj.isLst()) {
                        for (int i = 0; i < obj.lstValue().size(); i++) { // branch
                            Helper.resolveWrite(LOG, space, vid.extend(String.valueOf(i)), obj.lstValue().get(i), directWriter, directReader);
                        }
                    }
                } else if (vid.isNode() || !obj.isPoly()) {
                    if (base.obj().isRec())
                        Helper.resolveWrite(LOG, space, base.furi(), base.obj().asRec().at(uri(vid.removePrefix(base.furi())), obj), directWriter, directReader);
                    else if (base.obj().isLst())
                        Helper.resolveWrite(LOG, space, base.furi(), base.obj().asLst().append(obj), directWriter, directReader);
                    else {
                        //throw MTronException.of("unknown poly: %s %s %s", base.get1(), vid, obj);
                        writeComplete(obj, base.obj());
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.obj().isRec()) {
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
                        writeComplete(obj, base.obj());
                        return directWriter.apply(vid, obj);
                    }
                } else if (base.obj().isLst()) {
                    Lst newLst = base.obj().asLst().at(uri(vid.removePrefix(base.furi()).pretract(1)), obj, Lst.IMMUTABLE);
                    Helper.resolveWrite(LOG, space, vid, newLst, directWriter, directReader);
                }
            }
            return obj;
        }

        public static void closeSpace(final Space space) {
            if (Router.loaded())
                Router.global().removeSpace(space.vid());
        }


        public static IdObj locateBasePoly(final Space space, final fURI furi) {
            fURI newFuri = furi.retract(1).asNode();
            while (!newFuri.segments().isEmpty()) {
                Obj obj = space.read(newFuri);
                if (obj.isPoly())
                    return IdObj.of(newFuri, obj.as());
                newFuri = newFuri.retract(1);
            }
            return null;
        }

        public static IdObj locateBaseObj(final Space space, final fURI furi, final fURI stopURI) {
            fURI newFuri = furi.retract(1).asNode();
            while (!newFuri.segments().isEmpty()) {
                space.logger().info("checking %s", newFuri);
                Obj obj = space.read(newFuri);
                if (!obj.isNoObj())
                    return IdObj.of(newFuri, obj);
                newFuri = newFuri.retract(1);
                if (newFuri.equals(stopURI))
                    break;
            }
            return null;
        }

        public static File locateBaseFile(final fURI vid, final String dirRootFile) {
            // if dir, check if the root file is in dir
            if (vid.isBranch() && null != dirRootFile) {
                final Path path = Path.of(vid.extend(dirRootFile).toString());
                if (path.toFile().exists() && path.toFile().isFile())
                    return path.toFile();
            }
            if (vid.isNode()) {
                final Path path = Path.of(vid.toString());
                if (path.toFile().exists() && path.toFile().isFile())
                    return path.toFile();
            }
            fURI temp = vid.asNode();
            while (temp.segmentLength() != 0) {
                Path path = Path.of(temp.toString());
                if (path.toFile().exists() && path.toFile().isFile())
                    return path.toFile();
                if (null != dirRootFile) {
                    path = Path.of(temp.extend(dirRootFile).toString());
                    if (path.toFile().exists() && path.toFile().isFile())
                        return path.toFile();
                }
                temp = temp.retract(1);
            }
            return null;
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

    record IdObj(fURI furi, Obj obj) implements Iterable<IdObj> {
        public static IdObj of(final Obj obj) {
            return new IdObj(obj.vid(), obj);
        }

        public static IdObj of(final fURI furi, final Obj obj) {
            return new IdObj(furi, obj);
        }

        public Iterator<IdObj> iterator() {
            return IteratorUtil.of(this);
        }
    }

    record UriObj(Uri uri, Obj obj) implements Iterable<UriObj> {
        public static UriObj of(final Uri uri, final Obj obj) {
            return new UriObj(uri, obj);
        }

        public Iterator<UriObj> iterator() {
            return IteratorUtil.of(this);
        }
    }
}