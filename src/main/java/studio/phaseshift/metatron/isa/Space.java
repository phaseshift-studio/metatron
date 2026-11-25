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
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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

    // read(fURI)/write(fURI, Obj) and their String overloads are the Addressable contract: a Space is the
    // storage-shaped Addressable (pattern, q-procs, routes) among the machine's uniform read/write sources.
    @Override
    Obj read(final fURI vid);

    @Override
    Obj write(final fURI vid, final Obj obj);

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
                return Machine.current().processor().apply(rhs.asCode()).apply();
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
                                // a poly is unrolled only where the pattern can still match beneath it: a component's
                                // pattern read must not walk every computer's code and its running monads
                            else if (pattern.hasPattern("#") || (key.segmentLength() < pattern.segmentLength()
                                    && key.test(pattern.asNode().head(key.segmentLength()))))
                                results.addAll(unrollPoly(key, r.jvm().get1().as(), pattern));
                        });
            }
            //poly.logger().error("unrolled poly %s %s %s => %s", polyvid, pattern, poly, results);
            return results;
        }

        /**
         * READ, WITH WHAT A READ MEANS — derived once, here, for every space. A space's {@code directReader} answers only
         * what is explicitly in its store: an exact address its stored value, a pattern the stored entries it matches
         * (Marko, 2026-11-19). Everything else follows from Space's law, that every layout of the same data reads alike
         * (docs/website/adoc/uri.adoc; {@code AbstractSpaceTest#testReadsAreInvariantUnderFactorization}):
         * <ul>
         *     <li>an address answers what is stored at it, else what the nearest stored poly above it holds there, else —
         *     an interior address — its whole subtree, nested from the entries stored below it;</li>
         *     <li>a {@code +} step walks the logical tree one level: a held poly's keys, and the addresses the entries
         *     stored below lie under;</li>
         *     <li>{@code #} answers only leaves, each at its full flattened uri: a poly is structure, not data;</li>
         *     <li>a trailing {@code /} names the relation from an address to its obj ({@code x/} is {@code x => *x}, and
         *     {@code x/+/} each child's: Marko, 2026-11-10).</li>
         * </ul>
         */
        public static Obj resolveRead(final Space space, final fURI pattern) {
            final Function<fURI, Iterator<IdObj>> directReader = space.directReader();
            // a dot segment is no level of the tree (test:. is test:): the walk compares segments literally
            final fURI node = pattern.asNode().resolve();
            final List<IdObj> found = !node.hasPattern()
                    ? Stream.of(IdObj.of(node, valueAt(node, directReader))).filter(id -> !id.obj().isNoObj()).toList()
                    // a wildcard only in the scheme, the host or the query is no step of the tree: the store's own matches
                    : node.segments().stream().noneMatch(step -> "+".equals(step) || "#".equals(step))
                    ? IteratorUtil.stream(directReader.apply(node)).filter(id -> !id.obj().isNoObj()).toList()
                    : node.hasPattern("#")
                    ? leaves(node, directReader)
                    : located(node, directReader).entrySet().stream()
                    .flatMap(at -> at.getValue().isEmpty()
                            ? Stream.of(IdObj.of(at.getKey(), valueAt(at.getKey(), directReader)))
                            : at.getValue().stream().map(obj -> IdObj.of(at.getKey(), obj)))
                    .filter(id -> !id.obj().isNoObj())
                    .toList();
            return pattern.isBranch()
                    ? objs(found.stream().map(id -> rel(uri(id.furi()), id.obj())))
                    : objs(found.stream().map(IdObj::obj).map(obj -> obj.autoResolve(obj)).toList());
        }

        /**
         * the addresses a pattern names in a space's logical tree: its literal head, then every child for a {@code +}
         * step, the one child for a literal step, and everything below for {@code #} — so it never names an address no
         * data lies at or below (uri.adoc: navigation never invents an address). A level is read as its listing needs:
         * the store's listing, and the poly held at the address.
         */
        public static List<fURI> addresses(final Space space, final fURI pattern) {
            return new ArrayList<>(located(pattern.asNode().resolve(), space.directReader()).keySet());
        }

        /**
         * the addresses a pattern names, each with the objs the store's own listing answered there — none where nothing
         * was listed (an interior node, a step taken by name) and a read must derive it; several where a store lists
         * several at one address (a graph's traversal). A {@code +} step lists the level once and keeps the objs the
         * listing answered, so a {@code +} read looks up only what was not listed.
         */
        private static Map<fURI, List<Obj>> located(final fURI pattern, final Function<fURI, Iterator<IdObj>> directReader) {
            final List<String> steps = pattern.segments();
            final int literal = (int) steps.stream().takeWhile(step -> !"+".equals(step) && !"#".equals(step)).count();
            Map<fURI, List<Obj>> frontier = new LinkedHashMap<>();
            offer(frontier, 0 == literal ? root(pattern) : pattern.head(literal), List.of());
            for (final String step : steps.subList(literal, steps.size())) {
                final Map<fURI, List<Obj>> next = new LinkedHashMap<>();
                if ("#".equals(step)) {
                    // the frontier and every level below it, until a level lists nothing new
                    for (Map<fURI, List<Obj>> level = frontier; !level.isEmpty(); ) {
                        level.forEach((at, objs) -> offer(next, at, objs));
                        final Map<fURI, List<Obj>> below = new LinkedHashMap<>();
                        level.keySet().forEach(at -> children(at, directReader).forEach((child, objs) -> offer(below, child, objs)));
                        below.keySet().removeAll(next.keySet());
                        level = below;
                    }
                } else if ("+".equals(step))
                    frontier.keySet().forEach(at -> children(at, directReader).forEach((child, objs) -> offer(next, child, objs)));
                else
                    frontier.keySet().forEach(at -> offer(next, at.extend(step), List.of()));
                frontier = next;
            }
            return frontier;
        }

        /**
         * where a pattern that opens with a wildcard starts: its root -- its scheme and host as they are, and the leading
         * {@code /} of an absolute path. {@code head(0)} (and a full retract, and retractPattern) answer an empty, relative
         * path, so {@code /+} would list as the relative {@code +}; extending an empty path by the empty segment is
         * {@code /}.
         */
        public static fURI root(final fURI pattern) {
            final fURI root = pattern.head(0);
            return pattern.isAbsolute() && !pattern.path().isEmpty() ? root.extend("") : root;
        }

        /**
         * an address joins a level once, with every distinct obj a listing answers there ({@code null}: none yet)
         */
        private static void offer(final Map<fURI, List<Obj>> level, final fURI at, final Collection<Obj> objs) {
            final List<Obj> listed = level.computeIfAbsent(at, k -> new ArrayList<>());
            objs.stream().filter(obj -> !listed.contains(obj)).forEach(listed::add);
        }

        /**
         * what an address holds in the logical tree: what {@link #held} finds, else — an interior address — its whole
         * subtree. The first answer, not the sum of every level's: a store may answer one datum at several addresses (a
         * document and its fields, a graph element and its properties), so its levels are views, not terms, and adding
         * them would count the datum again. Summing a space's terms, as Rec.at sums a poly's (Marko, 2026-11-25, "keep
         * every term"), needs a store to tell its terms from its views (open)
         */
        private static Obj valueAt(final fURI address, final Function<fURI, Iterator<IdObj>> directReader) {
            final Obj held = held(address, directReader, false);
            return held.isNoObj() ? subtree(address, directReader) : held;
        }

        /**
         * what is stored at an address, else what the nearest stored entry above it holds there — an entry that is no poly
         * holds nothing below it, and a stream of polys stored at one address (a value written as {@code {…}}) holds what
         * each of them holds. A WALK ({@code walk}) follows the layout alone (see {@link #within}), so the uri tree it
         * walks is the one the data lays out, and finite.
         */
        private static Obj held(final fURI address, final Function<fURI, Iterator<IdObj>> directReader, final boolean walk) {
            final Obj stored = storedAt(address, directReader);
            if (!stored.isNoObj())
                return stored;
            for (fURI base = address.retract(1); base.segmentLength() > 0; base = base.retract(1)) {
                final Obj above = storedAt(base, directReader);
                if (!above.isNoObj()) {
                    // the rest below the base, a bare relative path: a rec's key is a path, never a scheme, a host or a q
                    final fURI rest = address.pretract(base.segmentLength()).asRelative().scheme(null).host(null).qLess();
                    // what each poly stored there holds below it, read in its normal form (Poly.Helper.readsBelow) one
                    // poly at a time: the polys of a stream stored at one address are the store's answers there, kept
                    // apart (a graph answers a step with each element it reaches)
                    final Obj inside = objs(above.stream()
                            .flatMap(answer -> Poly.Helper.readsBelow(answer, null, poly -> within(poly, rest, walk)).stream())
                            .filter(obj -> !obj.isNoObj())
                            .toList());
                    // an entry above that holds nothing there does not end the search: a store that answers inside its
                    // documents (a field path) may answer an address no poly is stored at
                    if (!inside.isNoObj())
                        return inside;
                }
            }
            return noobj();
        }

        /**
         * what is stored at exactly this address: every obj the store answers there (a graph answers a traversal step
         * with each element it reaches). An address holding a wildcard segment the layout put there (the key
         * {@code http/#}) names that one entry, where the raw store would read it as a pattern: every entry it matches.
         */
        private static Obj storedAt(final fURI address, final Function<fURI, Iterator<IdObj>> directReader) {
            return objs(IteratorUtil.stream(directReader.apply(address))
                    .filter(id -> !address.hasPattern() || id.furi().equals(address))
                    .map(IdObj::obj)
                    .filter(obj -> !obj.isNoObj())
                    .toList());
        }

        /**
         * what a poly holds at a relative address. A rec's KEY IS A PATH, whatever obj it is (the int key 0 is the segment
         * 0), compared segment by segment and LITERALLY: a wildcard in a key is a segment of the layout, never a query.
         * Every key that meets the rest adds, as in Rec.at: the key that is the rest; each key the rest runs through, the
         * rest read below it in the key's value in the merge's normal form (Poly.Helper.readsBelow: a bag's recs fused
         * into one, a bulked rec's multiplicity spread); and the keys under the rest, its subtree. On a READ of an address
         * with no wildcard, an auto_from at the key that is the rest, or at a key the rest runs through, fires, and the
         * polys of data read below a key take its holder as their parent. A key meets the rest literally, so a literal
         * path answers before a wildcard key that would match it (the web gateway's {@code http/#} beside a dialed
         * {@code http/quarry/8777}). Only when the keys' sum is noobj, and only on a READ of an address with no wildcard,
         * does the poly answer by its own walk, Rec.at (a position, a wildcard key matching a concrete address). A WALK
         * stops at what the layout holds: no auto_from fires and no wildcard key matches deeper, or a key like
         * {@code http/#} would answer every address below it, without end, and the keys it runs through parent nothing.
         */
        private static Obj within(final Poly<?, ?> poly, final fURI rest, final boolean walk) {
            if (poly.isRec()) {
                // A READ IS LINEAR, as Rec.at's is: the key that is the rest, each key the rest runs through (the rest
                // read below it), and the keys under the rest, its subtree, all add ({@link Poly.Helper#added})
                final Function<Obj, fURI> path = key -> key.isUri() ? key.uriValue() : f(key.jvm().toString());
                final List<Obj> reads = new ArrayList<>();
                final List<IdObj> under = new ArrayList<>();
                poly.asRec().jvm().forEach((key, value) -> {
                    final fURI keyPath = path.apply(key);
                    if (keyPath.segments().equals(rest.segments()))
                        // the key's own value, an auto_from firing on a read, as Rec.at's named key: Rec.at itself would
                        // add the keys under it a second time
                        reads.add(walk || rest.hasPattern() ? value : Poly.Helper.autoToggle(poly, value, true));
                    else if (runsThrough(keyPath, rest)) {
                        // the reads below the key in its value's normal form, as Rec.at's (Poly.Helper.readsBelow). A
                        // read fires an auto_from there first and parents the polys of data it reads to this poly; a
                        // walk, or a rest holding a layout wildcard, fires nothing and parents nothing. A value holding
                        // no code is not toggled, so a live one keeps its parent. The rest names one address (a wildcard
                        // in it is a segment), so its reads add
                        final boolean raw = walk || rest.hasPattern();
                        final Obj holds = raw || value.stream().noneMatch(Obj::isObjCall)
                                ? value : Poly.Helper.autoToggle(poly, value, true);
                        reads.addAll(Poly.Helper.readsBelow(holds, raw ? null : poly,
                                member -> within(member, rest.pretract(keyPath.segmentLength()), walk)));
                    }
                    else if (runsThrough(rest, keyPath))
                        under.add(IdObj.of(keyPath.pretract(rest.segmentLength()), value));
                });
                if (!under.isEmpty())
                    reads.add(sum(under));
                final Obj sum = Poly.Helper.added(reads);
                if (walk || !sum.isNoObj())
                    return sum;
            }
            if (rest.hasPattern())
                return noobj();
            return walk ? poly.atDirect(uri(rest)) : poly.at(uri(rest));
        }

        /**
         * an interior address's whole subtree: every entry stored below it, nested by its path there. An entry the store
         * answers by reference stays one (fsSpace's files: Marko, 2026-11-21), so the tree is laid out without reading a
         * leaf, and a leaf is read when its key is ({@code Rec.at} fires the reference; {@code atDirect} walks past it).
         */
        private static Obj subtree(final fURI address, final Function<fURI, Iterator<IdObj>> directReader) {
            final List<IdObj> entries = new ArrayList<>();
            directReader.apply(address.extend(fURI.Singleton.ALL)).forEachRemaining(id -> {
                if (runsThrough(address, id.furi()) && !id.furi().hasQ() && !id.obj().isNoObj())
                    entries.add(IdObj.of(id.furi().pretract(address.segmentLength()).asRelative().scheme(null).host(null).qLess(), id.obj()));
            });
            return nest(entries);
        }

        /**
         * a store's entries below an address nested into one rec by their paths, a shallower entry first so a deeper one
         * nests below it, replacing a value there: a store's listing may hold views of one datum at several levels (a
         * document and its fields), and the views collapse to one. On a store of terms this drops a value with entries
         * below it (n/m/r -> 1 and n/m/r/s -> 2 read *n/m as [r=>[s=>2]], where the rule is [r=>{1,[s=>2]}]: open, as
         * {@link #valueAt} says); the stored objs are never mutated (each step answers a new rec)
         */
        public static Obj nest(final List<IdObj> entries) {
            Rec tree = rec();
            for (final IdObj entry : entries.stream().sorted(Comparator.comparingInt(entry -> entry.furi().segmentLength())).toList())
                tree = tree.at(uri(entry.furi()), entry.obj());
            return tree.isEmpty() ? noobj() : tree;
        }

        /**
         * relative entries nested by their paths and ADDED as terms ({@link Poly.Helper#added}), so an address holding a
         * value with entries below it keeps both (Marko, 2026-11-25, "keep every term": [n/m/r=>1,n/m/r/s=>2] reads n/m
         * as [r=>{1,[s=>2]}]). For the keys of one poly, each a term of it (Rec.at, {@link #within}); a store's listing
         * nests as views ({@link #nest})
         */
        public static Obj sum(final List<IdObj> entries) {
            final Obj tree = Poly.Helper.added(entries.stream().map(entry -> (Obj) rec().at(uri(entry.furi()), entry.obj())).toList());
            return tree.isRec() && tree.asRec().isEmpty() ? noobj() : tree;
        }

        /**
         * the addresses one level below an address in the logical tree, each with the obj the listing answered for it
         * ({@code null}: derive it). The store lists the level itself with one {@code +}: what is stored there, and — a
         * store that keeps its structure (a trie's nodes, a filesystem's directories) — the interior nodes there, as
         * noobj. The poly the address holds adds its keys, in the merge's normal form, each slot's obj with the
         * coefficient its rel carries (the node form resolves it). Only a level that lists nothing is looked for below,
         * with {@code #}: a store keeping flat keys and no node for the levels between (Marko, 2026-11-19: + is the
         * everyday step, # is there for mqtt).
         */
        private static Map<fURI, List<Obj>> children(final fURI address, final Function<fURI, Iterator<IdObj>> directReader) {
            final Map<fURI, List<Obj>> children = new LinkedHashMap<>();
            final int depth = address.segmentLength();
            directReader.apply(address.extend(fURI.Singleton.WILD_ONE)).forEachRemaining(id -> {
                final boolean under = runsThrough(address, id.furi());
                if (under && id.furi().segmentLength() == depth + 1) {
                    if (!id.furi().hasQ())
                        offer(children, id.furi(), id.obj().isNoObj() ? List.of() : List.of(id.obj()));
                } else if (!id.obj().isNoObj())
                    // a store may answer a level with what lives elsewhere (a database's collections by their types, each
                    // addressed where its type lives): that answer is the child, at the address the store gives it
                    offer(children, id.furi(), List.of(id.obj()));
                else if (under && !id.furi().hasQ())
                    offer(children, id.furi().head(depth + 1), List.of());
            });
            // a held poly the store also listed as a child is that child (a graph's step holds the elements it reaches),
            // not a container whose keys are children
            final Set<Obj> listed = children.values().stream().flatMap(List::stream).collect(Collectors.toSet());
            // a bulked poly lists its children in the merge's normal form, its multiplicity spread into them
            held(address, directReader, true).stream().filter(Obj::isPoly).filter(poly -> !listed.contains(poly))
                    .map(Poly.Helper::normal)
                    .filter(Obj::isPoly)
                    .forEach(poly -> poly.<Poly<?, ?>>as().indexedStream()
                    .filter(slot -> null != slot.jvm().get1())
                    .forEach(slot -> {
                        final fURI key = f(slot.jvm().get0().jvm().toString());
                        if (key.segmentLength() > 0)
                            // the slot's value with the coefficient its rel carries (a bulked poly's multiplicity)
                            offer(children, address.extend(key.head(1)), key.segmentLength() == 1 ? List.of(slot.secondDirect()) : List.of());
                    }));
            if (children.isEmpty())
                directReader.apply(address.extend(fURI.Singleton.ALL)).forEachRemaining(id -> {
                    if (runsThrough(address, id.furi()) && !id.furi().hasQ())
                        offer(children, id.furi().head(depth + 1), List.of());
                });
            return children;
        }

        /**
         * {@code #}: the LEAVES of the uri tree at or below the addresses the pattern names before its {@code #} — the
         * longest paths, each prefixing no other (Marko, 2026-11-19: the leaf uris of {@code x.{*}>>}) — with the value
         * at each: data, at its full flattened uri, and never a poly, which is structure. A value stored at an address
         * that has entries below it is not a leaf (uri.adoc: {@code *n/m} answers {@code [r=>[s=>2]]}).
         */
        private static List<IdObj> leaves(final fURI pattern, final Function<fURI, Iterator<IdObj>> directReader) {
            final fURI before = 1 == pattern.segmentLength() ? root(pattern) : pattern.retract(1);
            final Map<fURI, List<Obj>> leaves = new LinkedHashMap<>();
            for (final fURI base : before.hasPattern() ? located(before, directReader).keySet() : Set.of(before)) {
                held(base, directReader, true).stream().forEach(obj -> leavesOf(base, obj, leaves));
                // a store may answer an entry by reference (fsSpace's lazy files: Marko, 2026-11-21) -- the same data
                // as the obj it reads, so its leaves are the leaves of that obj: # answers data, and reads through it
                directReader.apply(base.extend(fURI.Singleton.ALL)).forEachRemaining(id -> {
                    if (runsThrough(base, id.furi()) && !id.furi().hasQ())
                        id.obj().stream()
                                .flatMap(obj -> obj.isAutoFrom() ? obj.dereference().stream() : Stream.of(obj))
                                .forEach(obj -> leavesOf(id.furi(), obj, leaves));
                });
            }
            // a leaf's proper heads are interior addresses, so a value stored at one is not a leaf
            final Set<fURI> inner = leaves.keySet().stream()
                    .flatMap(leaf -> IntStream.range(1, leaf.segmentLength()).mapToObj(leaf::head))
                    .collect(Collectors.toSet());
            return leaves.entrySet().stream()
                    .filter(entry -> !inner.contains(entry.getKey()))
                    .flatMap(entry -> entry.getValue().stream().map(obj -> IdObj.of(entry.getKey(), obj)))
                    .toList();
        }

        private static void leavesOf(final fURI at, final Obj obj, final Map<fURI, List<Obj>> leaves) {
            // a value is walked in the merge's normal form, a bag's recs fused and a bulked rec's copies spread, as a
            // read below it reads it, and each slot carries the coefficient its rel holds (a bulked lst's)
            final Obj value = Poly.Helper.normal(obj);
            if (value.isPoly())
                value.<Poly<?, ?>>as().indexedStream()
                        .filter(slot -> null != slot.jvm().get1())
                        .forEach(slot -> leavesOf(at.extend(f(slot.jvm().get0().jvm().toString())),
                                slot.secondDirect(), leaves));
            else if (!value.isNoObj())
                offer(leaves, at, List.of(value));
        }

        /**
         * whether {@code prefix} is a proper prefix of {@code address}, segment by segment and literally — in a layout a
         * wildcard is a segment like any other ({@code hasPrefix} would read it as a pattern)
         */
        public static boolean runsThrough(final fURI prefix, final fURI address) {
            // the scheme and authority first, as hasPrefix asks them: an empty path (a store's root, mongo:) prefixes
            // only the addresses of its own store, never every path
            return (!prefix.hasScheme() || prefix.scheme().equals(address.scheme()))
                    && (!prefix.hasAuthority() || prefix.authority().equals(address.authority()))
                    && address.segmentLength() > prefix.segmentLength()
                    && address.head(prefix.segmentLength()).segments().equals(prefix.segments());
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

            // A WRITE ASKS ONLY WHETHER SOMETHING IS STORED AT ITS ADDRESS, never what (Marko, 2026-11-21): the value is
            // not taken, so a store that answers that without reading (fsSpace, by the file's name) reads nothing to be
            // written over or deleted -- one that decodes in its exact read (tble, grph) still does. A store holding
            // live objects closes what a delete removes itself (AbstractMemorySpace, each member of a stream too).
            final Iterator<IdObj> current = directReader.apply(vid);
            if (current.hasNext() && vid.isNode()) {
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
         * <p>
         * STORED POLYS ONLY, read off the space's raw store ({@code directReader}): a write lands in the poly that is
         * stored, never in a subtree a read derives (an interior address's container is built for reading, and storing a
         * copy of it would put the flat entries it came from beside it twice).
         */
        public static Stream<IdPoly> locateBasePoly(final Space space, final fURI furi) {
            final Function<fURI, Iterator<IdObj>> directReader = space.directReader();
            fURI newFuri = furi.retract(1).resolve().asNode();
            while (!newFuri.segments().isEmpty()) {
                // an exact lookup answers what is stored AT the address asked, so that is the poly's address -- a store may
                // name its entries in its own terms (a vector collection by document id), and writing back there misses
                final fURI at = newFuri;
                final List<IdPoly> polys = IteratorUtil.stream(directReader.apply(at))
                        .flatMap(id -> id.obj().stream().filter(Obj::isPoly).map(poly -> IdPoly.of(at, poly.as())))
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

}