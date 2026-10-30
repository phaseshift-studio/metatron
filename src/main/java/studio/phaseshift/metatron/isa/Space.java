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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.mach.type.Stats;
import studio.phaseshift.metatron.isa.m.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;

public interface Space extends Addressable, Rec, Closeable {

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    /**
     * qs(), hasQ() and the declaration itself live on {@link Addressable}: every source can carry q-procs. A
     * space narrows only the return type, so {@code space.addQ(...)} still chains as a Space.
     */
    @Override
    default Space addQ(final QProc qProc) {
        Addressable.super.addQ(qProc);
        return this;
    }

    fURI pattern();

    Object sjvm();

    Map<Uri, Obj> routes();

    Stats stats();

    // read(fURI)/write(fURI, Obj) and their String overloads are the Addressable contract: a Space is the
    // storage-shaped Addressable (pattern, q-procs, routes) among the machine's uniform read/write sources.
    @Override
    Obj read(final fURI vid);

    @Override
    Obj write(final fURI vid, final Obj obj);

    /**
     * Read all concrete addresses matching a pattern, returning each as an {@link IdObj} pair.
     * The returned fURIs are always concrete paths (never wildcards).
     * Default implementation wraps {@link #read(fURI)} for backward compatibility;
     * space implementations should override for native bulk pattern support.
     */
    default Stream<IdObj> readStream(final fURI pattern) {
        final Obj result = this.read(pattern);
        if (result.isNoObj())
            return Stream.empty();
        if (result.isObjs())
            return IteratorUtil.stream(result.iterator()).map(o ->
                    IdObj.of(null != o.vid() ? o.vid() : pattern, o));
        return Stream.of(IdObj.of(pattern, result));
    }

    /**
     * Write an object to all concrete addresses matching a pattern, returning each
     * written pair as an {@link IdObj}.  The returned fURIs are always concrete paths.
     * Default implementation wraps {@link #write(fURI, Obj)} for backward compatibility;
     * space implementations should override for native bulk pattern support.
     */
    default Stream<IdObj> writeStream(final fURI pattern, final Obj obj) {
        if (pattern.hasPattern()) {
            return this.readStream(pattern).map(kv -> {
                this.write(kv.furi(), obj);
                return IdObj.of(kv.furi(), obj);
            });
        }
        final Obj result = this.write(pattern, obj);
        if (result.isNoObj())
            return Stream.empty();
        return Stream.of(IdObj.of(result.hasVID() ? result.vid() : pattern, result));
    }

    fURI redirect(final fURI furi, final boolean big);

    @Override
    default void close() {
        try {
            // A space's own farewell must never abandon the teardown around it: on_close is arbitrary
            // mtron -- a reaper that dials a daemon, say -- so its failure is reported and swallowed.
            // Closing sjvm is ours, so a failure there is still a real error.
            try {
                final Obj farewell = this.at(Tokens.ON_CLOSE).apply();
                if (null != farewell && farewell.isFail())
                    this.logger().warn("on_close failed for %s: %s (close continues)",
                            this.hasVID() ? this.vid() : "<no-vid>", farewell);
            } catch (final Exception e) {
                this.logger().warn("on_close failed for %s: %s (close continues)",
                        this.hasVID() ? this.vid() : "<no-vid>", e.getMessage());
            }
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

        public static void recursivelyStripVID(final Obj obj, final boolean root) {
            if (obj.isLst()) {
                obj.lstValue().forEach(e -> recursivelyStripVID(e, false));
            } else if (obj.isRec()) {
                obj.recValue().forEach((key, value) -> {
                    recursivelyStripVID(key, false);
                    recursivelyStripVID(value, false);
                });
            } else if (obj.isRel()) {
                recursivelyStripVID(obj.relValue().get0(), false);
                recursivelyStripVID(obj.relValue().get1(), false);
            }
            if (!root)
                obj.selfVID(null);
        }

        public static void spaceCloseLog(final Obj source, final Space space) {
            if (space instanceof InstSet)
                source.logger().info("closed instset {{b}}%s", space.vid());
            else
                source.logger().info("closed space {{b}}%s {{g}}[{{c}}pattern: {{b}}%s{{g}}]", space.hasVID() ? space.vid() : "<no-vid>", space.pattern());
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
            if (!(other instanceof Space))
                return false;
            final Space that = (Space) other;
            if (!that.tid().equals(space.tid()))
                return false;
            // A space with no vid is not indexed by the Router -- a service's own bookkeeping memory, for itself,
            // unaddressed and ephemeral (qprocs lean on this heavily). Having no position, there is nothing to
            // compare it by, so the answer is identity. Requiring both vids to be non-null made this
            // non-reflexive: space.equals(space) was false for every vid-less space, silently breaking
            // Set/Map membership for exactly the spaces that have no other way to be identified.
            return null == space.vid() || null == that.vid() ? space == that : that.vid().equals(space.vid());
        }

        public static void noCloneWarning(final Space space) {
            space.logger().warn("the clone of a space is the space itself");
        }

        public static fURI routeToSpace(final fURI vid, Map<Uri, Obj> routes) {
            if (null == vid)
                return null;
            final Uri vidURI = uri(vid);
            // THE WAY BACK, matched the same way as the way in: a target is an ADDRESS, not a text prefix, and when two
            // targets both contain the vid the LONGER one owns it. String surgery got both wrong -- /srv/mcp captured
            // /srv/mcp-api and stripped it to "-api", and an earlier, shorter target swallowed a later, more specific
            // one purely because the rec listed it first.
            return routes.entrySet().stream()
                    .map(e -> Map.entry(e.getValue().apply(vidURI).uriValue(), e.getKey()))
                    .filter(route -> vid.hasPrefix(route.getKey()))
                    .max(java.util.Comparator
                            .comparingInt((Map.Entry<fURI, Uri> route) -> route.getKey().pathLength())
                            .thenComparing(route -> route.getKey().toString()))
                    .map(route -> route.getValue().apply(vidURI).uriValue()
                            .extend(vid.removePrefix(route.getKey()).qLess()).q(vid.qMap()))
                    .orElse(vid);
        }

        public static fURI routeFromSpace(final fURI vid, Map<Uri, Obj> routes) {
            if (null == vid)
                return null;
            final Uri vidURI = uri(vid);
            // MOST SPECIFIC WINS: several keys can match one vid (`/` matches everything, `/mcp` matches /mcp/...), so
            // routing is a LONGEST-KEY match. findFirst() over the rec's iteration order made the outcome of a read depend
            // on which route the rec happened to list first: a catch-all registered before a specific route swallowed it,
            // and the same table could route differently after a rewrite reordered the rec. The key is resolved ONCE (its
            // apply may compute), the longest wins, and ties break by text so every run answers the same.
            return routes.entrySet().stream()
                    .map(e -> Map.entry(e.getKey().apply(vidURI).uriValue(), e.getValue()))
                    .filter(route -> vid.hasPrefix(route.getKey()))
                    .max(java.util.Comparator
                            .comparingInt((Map.Entry<fURI, Obj> route) -> route.getKey().pathLength())
                            .thenComparing(route -> route.getKey().toString()))
                    .map(route -> {
                        // When the scheme prefix is stripped (e.g. "db:" -> "") the remainder often starts with "/"
                        // ("/people/+"); extend() on an empty base preserves that leading slash as an empty first
                        // segment, which breaks isTablePath() and hasPattern().
                        final fURI remainder = vid.removePrefix(route.getKey());
                        return route.getValue().apply(vidURI).uriValue().extend(remainder.qLess()).q(vid.qMap());
                    })
                    .orElse(vid);
        }

        public static Obj resolveApply(final Space space, final Obj rhs) {
            if (rhs.isCode()) {
                return SwarmProcessor.of(rhs.as()).apply();
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
                        .filter(r -> null != r.jvm().get1())          // a slot's value can be absent (noobj);
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

        public static Obj resolveRead(final Space space, final fURI patternPre, final Function<fURI, Iterator<IdObj>> directReader) { //final Map<fURI, Obj> store) {
            final Set<UriObj> listing = new HashSet<>();
            final fURI pattern = patternPre;//.qLessExceptDomRng();
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
            if (listing.isEmpty() || pattern.hasPattern("#")) {
                Helper.locateBasePoly(space, pattern.basePath()).forEach(base -> {
                    final Poly<?, ?> poly = base.poly();
                    Graphitty.log(space).trace("base poly found at %s: %s", base.furi(), poly);
                    unrollPoly(base.furi(), poly, pattern).forEach(kv -> listing.add(UriObj.of(kv.furi().toUri(), kv.obj())));
                });
            }
            final Stream<UriObj> prefix = listing.stream().filter(kv -> !kv.obj().isNoObj() && !kv.uri().isNoObj());
            return pattern.isNode() ?
                    objs(prefix.map(UriObj::obj).map(o -> o.autoResolve(o)).toList()) :
                    objs(prefix.map(kv -> rel(kv.uri(), kv.obj())));
        }

        private static Obj writeComplete(final Obj newObj, final Obj currentObj) {
            //Machine.authority().logger().info("write complete for %s: %s => %s", writePattern, currentObj, newObj);
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
                return directWriter.apply(vid.resolve(), obj);
            } else {
                final Iterator<IdPoly> itty = Helper.locateBasePoly(space, vid.basePath().resolve()).iterator();
                if (!itty.hasNext()) {
                    if (vid.isNode() || !obj.isPoly()) {
                        return directWriter.apply(vid.resolve(), obj);
                    } else if (obj.isRec()) { // branch
                        obj.recValue().forEach((key, value) -> {
                            Helper.resolveWrite(LOG, space, vid.extend(key.uriValue()), value, directWriter, directReader);
                        });
                    } else if (obj.isLst()) {
                        for (int i = 0; i < obj.lstValue().size(); i++) { // branch
                            Helper.resolveWrite(LOG, space, vid.extend(String.valueOf(i)), obj.lstValue().get(i), directWriter, directReader);
                        }
                    }
                } else {
                    return IteratorUtil.stream(itty).map(base -> {
                        if (vid.isNode() || !obj.isPoly()) {
                            if (base.poly().isRec())
                                Helper.resolveWrite(LOG, space, base.furi(), base.poly().asRec().at(uri(vid.removePrefix(base.furi())), obj), directWriter, directReader);
                            else if (base.poly().isLst()) {
                                // A deep write into a list is a POSITIONAL set, not an append: the
                                // address names a cell inside the list (…/grid/0/1), and appending
                                // put the value BESIDE the list at the parent vid instead of in it.
                                // Mirror the rec branch above — the relative path is the list's own
                                // key walk, which Lst.at(uri) sets by index, recursively.  An
                                // un-addressed write (no path below the base) keeps the append,
                                // which is what writing a list with no address has always meant.
                                final fURI relative = vid.removePrefix(base.furi());
                                // Only a numeric tail addresses a cell.  A wildcard (`…/+/2`,
                                // `…/4/+`) never arrives here — Space.write expands a pattern into
                                // one concrete key write per match (the pattern.hasPattern()
                                // branch), which is the deliberate way to fill a column or a row.
                                // This guard is for a leaf/name address, which keeps the old append
                                // rather than throwing on a non-index key.
                                final boolean addressed = !relative.path().isEmpty()
                                        && CommonUtil.isInt(relative.path().getFirst());
                                Helper.resolveWrite(LOG, space, base.furi(),
                                        addressed
                                                ? base.poly().asLst().at(relative, obj, MUTABLE)
                                                : base.poly().asLst().append(obj),
                                        directWriter, directReader);
                            } else {
                                writeComplete(obj, base.poly());
                                return directWriter.apply(vid, obj);
                            }
                        } else if (base.poly().isRec()) {
                            if (obj.isRec()) {
                                obj.recValue()
                                        .entrySet()
                                        .stream()
                                        .filter(kv -> !kv.getValue().isNoObj())
                                        .forEach(kv -> Helper.resolveWrite(LOG, space, kv.getKey().uriValue(), kv.getValue(), directWriter, directReader));
                            } else {
                                writeComplete(obj, base.poly());
                                return directWriter.apply(vid, obj);
                            }
                        } else if (base.poly().isLst()) {
                            Lst newLst = base.poly().asLst().at(uri(vid.removePrefix(base.furi()).pretract(1)), obj, Lst.IMMUTABLE);
                            Helper.resolveWrite(LOG, space, vid, newLst, directWriter, directReader);
                        }
                        return obj;
                    }).findFirst().orElse(obj);
                }
            }
            return obj;
        }

        public static void closeSpace(final Space space) {
            // UNREGISTER ONLY.  This is close()'s last step (its finally), so calling space.close() here
            // recurses: close -> closeSpace -> close -> StackOverflowError on every single space close,
            // which takes Machine.close()/pop() and therefore shutdown down with it.  The close log lives
            // in Memory.removeSpace (spaceCloseLog), so there is nothing left to do here but unregister.
            if (Machine.loaded()) {
                Machine.current().memory().removeSpace(space.vid());
            }
        }

        /**
         * the mounts made by the constructions running on this thread, innermost construction on top, each
         * construction's newest mount first: see {@link #construct(Supplier)}
         */
        private static final ThreadLocal<Deque<Deque<Runnable>>> CONSTRUCTIONS = ThreadLocal.withInitial(ArrayDeque::new);

        /**
         * Construct a space so that a failed construction mounts nothing.
         * <p>
         * A space mounts itself as the first step of its construction ({@link AbstractSpace}'s constructor), before
         * its own constructor body runs, and it has to: a body that reads or writes through the machine reaches the
         * space being built that way (memSpace loads its persisted data so). The price is that a construction that
         * fails after that point leaves a half-built space claiming its vid. Under this guard every mount made while
         * the construction runs (the space, and whatever it mounts on its way) is kept when the construction returns
         * and retracted when it throws: the claim removed, or the predecessor it displaced put back. A nested
         * construction's mounts are kept only as long as the enclosing construction's are.
         * <p>
         * Retracting is not closing. A half-built space's {@code close()} may assume a whole one (memSpace's saves
         * over its persisted data, and {@code on_close} is user mtron), so what a construction acquired, its factory
         * releases.
         */
        public static <S extends Space> S construct(final Supplier<S> construction) {
            final Deque<Runnable> mounts = new ArrayDeque<>();
            CONSTRUCTIONS.get().push(mounts);
            try {
                final S space = construction.get();
                CONSTRUCTIONS.get().pop();
                if (!CONSTRUCTIONS.get().isEmpty())
                    mounts.descendingIterator().forEachRemaining(CONSTRUCTIONS.get().peek()::push);
                return space;
            } catch (final RuntimeException | Error e) {
                CONSTRUCTIONS.get().pop();
                mounts.forEach(Runnable::run);
                throw e;
            }
        }

        /**
         * Journal a mount for the construction running on this thread, if one is: {@code memory}'s index now maps
         * {@code key} to {@code space}, and mapped it to {@code displaced} (null for nothing) before. Called by the
         * memory that took the mount.
         */
        public static void mounted(final Memory memory, final Obj key, final Obj displaced, final Space space) {
            final Deque<Deque<Runnable>> constructions = CONSTRUCTIONS.get();
            if (constructions.isEmpty())
                return;
            constructions.peek().push(() -> {
                final Map<Obj, Obj> index = memory.spaces().jvm();
                // a later mount at the key outranks the retraction: only this space's claim is withdrawn
                if (index.get(key) != space)
                    return;
                if (null == displaced)
                    index.remove(key);
                else
                    index.put(key, displaced);
                space.logger().debug("retracted the mount at %s: its construction failed", key);
            });
        }


        /**
         * Walk up the path by successive retractions, yielding every poly found
         * along the way.  Each result is an {@link IdPoly} whose fURI is the poly's
         * concrete address — making it trivial for callers to compute the remaining
         * path segments with {@code originalFuri.removePrefix(poly.furi())}.
         * <p>
         * final List<IdPoly> polys = space.read(newFuri).stream()
         * .filter(Obj::isPoly)
         * .map(oi -> IdPoly.of(newNewFuri, oi.asPoly()))
         * .toList();
         */
        public static Stream<IdPoly> locateBasePoly(final Space space, final fURI furi) {
            fURI newFuri = furi.retract(1).resolve().asNode();
            while (!newFuri.segments().isEmpty()) {
                final List<IdPoly> polys = space.readStream(newFuri)
                        .filter(oi -> oi.obj().isPoly())
                        .map(IdPoly::from)
                        .toList();
                if (!polys.isEmpty())
                    return polys.stream();
                newFuri = newFuri.retract(1);
            }
            return Stream.empty();
        }

        public static IdObj locateBaseObj(final Space space, final fURI furi, final fURI stopURI) {
            fURI newFuri = furi.retract(1).resolve().asNode();
            while (!newFuri.segments().isEmpty()) {
                space.logger().debug("checking %s", newFuri);
                Obj obj = space.read(newFuri);
                if (!obj.isNoObj())
                    return IdObj.of(newFuri, obj);
                newFuri = newFuri.retract(1);
                if (newFuri.equals(stopURI))
                    break;
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

    record IdPoly(fURI furi, Poly<?, ?> poly) implements Iterable<IdPoly> {
        public static IdPoly of(final fURI furi, final Poly<?, ?> poly) {
            return new IdPoly(furi, poly);
        }

        public static IdPoly from(final IdObj kv) {
            return new IdPoly(kv.furi(), kv.obj().as());
        }

        public IdObj toIdObj() {
            return IdObj.of(furi, (Obj) poly);
        }

        public Iterator<IdPoly> iterator() {
            return IteratorUtil.of(this);
        }

        public Stream<IdPoly> stream() {
            return Stream.of(this);
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