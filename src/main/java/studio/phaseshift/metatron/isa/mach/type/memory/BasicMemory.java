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

package studio.phaseshift.metatron.isa.mach.type.memory;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.TypeGraph;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MEMORY_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicMemory — one level of a machine's memory: its own relative bindings, the spaces it holds,
 * and the namespace (short-name route table + prefix table).
 * <p>
 * It is a {@link MRec} and holds <b>no state in Java fields</b> apart from the namespace tables, for the
 * same reason {@link BasicMachine} does not: everything has to be reachable from mtron. The relative
 * bindings <em>are</em> this rec, and the absolute index is the {@code space} entry inside it — so
 * {@code >>space} reaches the index from the language, and no part of memory hides behind an accessor the
 * language cannot call.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicMemory extends MRec implements Memory {

    private final GraphittyLogger LOG = Graphitty.log(this);

    // ======================== the namespace ========================
    private final ObjectMap<fURI, Set<fURI>> smallToBigRoutes = new ObjectMap<>();
    private final ObjectMap<fURI, fURI> bigToSmallRoutes = new ObjectMap<>();
    private final ObjectMap<fURI, fURI> prefixToVID = new ObjectMap<>();
    private fURI primary = M_ISA_TID;

    /**
     * The absolute index — one stable rec for the lifetime of this memory. It is a Java field, never re-derived
     * from the rec's own bindings: {@code Rec.at(key, value, MUTABLE)} (the relative write path) rebuilds the
     * memory's backing map on every write, so an index stored as a rec entry would be orphaned by the next
     * relative write. A field cannot be orphaned that way, which is what makes {@code addSpace}'s put stick.
     */
    private final Rec spaces = rec(mutableMap());
    private final Rec rootFrame = rec(mutableMap());
    private final TypeGraph typeGraph = new TypeGraph();

    public BasicMemory() {
        this(mutableMap(), null);
    }

    /**
     * the union this level is the own level of, when it is one: its machine is the frame this level serves (the level
     * itself is never parented — see {@link MemoryUnion}), and that frame's mounted instruction sets are what this
     * level's lookup weighs
     */
    private Memory union = null;

    public BasicMemory union(final Memory union) {
        this.union = union;
        return this;
    }

    /**
     * the vocabulary this level routes: the instset of the frame it serves — the union's machine for a frame's own
     * level, this level's own machine otherwise (the root's memory)
     */
    private InstSet vocabulary() {
        final Machine machine = null != this.union ? this.union.machine() : this.machine();
        return null == machine ? null : machine.instset();
    }

    /**
     * @param jvm the relative bindings — the rec this memory <em>is</em>
     * @param vid the address of this memory, if it has one; a frame's memory does not
     */
    public BasicMemory(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MACH_MEMORY_TID, vid);
        // The index rec is reflected into the bindings too (`>>space` reads it), so register it once here.
        this.jvm().put(uri(SPACE), this.spaces);
    }

    // ======================== hot slot read ========================

    /**
     * The index is consulted on every address resolution, so it is read from the raw jvm map rather than through
     * {@code Rec.at}, skipping the rec recursion {@code at} does for uri keys. Same shape as {@link BasicMachine}'s
     * processor and compiler short-circuits, and for the same reason: this is the hottest lookup in the machine.
     */
    @Override
    public <OBJ extends Obj> OBJ at(final Obj key) {
        if (key.equals(uri(SPACE)))
            return (OBJ) this.jvm().getOrDefault(uri(SPACE), noobj());
        return super.at(key);
    }

    // ======================== the two projections ========================

    /**
     * This level's index. A single stable object — a Java field — so the spaces registered into it are never
     * orphaned by a relative write rebuilding the memory's backing map.
     */
    @Override
    public Rec spaces() {
        return this.spaces;
    }

    /**
     * This machine's persistent relative bindings — shared across threads, dropped with the machine.
     */
    @Override
    public Rec stackBaseFrame() {
        return this.rootFrame;
    }

    @Override
    public TypeGraph typeGraph() {
        return this.typeGraph;
    }

    // ======================== index maintenance ========================

    @Override
    public void addSpace(final Space space) {
        // AN INSTRUCTION SET IS NOT A DATA SPACE (Marko, 2026-10-25: "Instsets shouldn't be in Memory as spaces
        // (though they are spaces). They should be in InstSet only."). One handed here — a hand-mounted schema
        // instset, an indexed construction — is referred into the machine's instset instead and is never entered in
        // this index. The addresses it holds stay routable: this level's lookup (findSpace) weighs the
        // libraries its machine mounted against its data spaces, the most specific answering. The guard is the root's
        // own construction: ROOT_MACHINE is assigned after it returns, and no ISA is mounted before that.
        // REFER ONLY — the sugars are the import's to add (InstSet.mount), never this door's: the parser's sugar set
        // grows with every add (each is a new Parser), and a fixture mounting an ISA per test row (catInstSetTest) made
        // every later parse in the fork pay for thousands of duplicates until it ran out of heap.
        if (space instanceof InstSet isa) {
            if (null != isa.vid() && Machine.loaded())
                Machine.current().instset().refer(isa);
            return;
        }
        // A space with no vid is managed by the object that created it, not by the memory's index: it is reached
        // through its creator's own reference (a field, a closure), never by resolving an address. Indexing one by
        // its pattern would let an anonymous catch-all (the arg stack's +/#, the qproc-internal # spaces) shadow the
        // parent's real spaces from inside a frame. Only addressable (vid'd) spaces enter the index.
        //if (null == space.vid())
        //   return; // TODO: spaces with null vids should not be indexed
        final Obj key = null == space.vid() ? space.pattern().retractPattern().toUri() : space.vid().toUri();
        final Obj displaced = this.spaces().jvm().put(key, space);
        // journaled, so a construction that fails after mounting can retract the mount (Space.Helper.construct)
        Space.Helper.mounted(this, key, displaced, space);
    }

    @Override
    public void removeSpace(final fURI pattern) {
        if (null == pattern)
            return;
        this.spaces().jvm().keySet().stream()
                .filter(k -> k.isUri() && k.uriValue().test(pattern))
                .toList()
                .forEach(k -> Space.Helper.spaceCloseLog(this, (Space) this.spaces().jvm().remove(k)));
        // an instruction set this level's machine mounted is UNMOUNTED here too: no longer routed by address, still a
        // member of the vocabulary — what removing it from this index used to do while ISAs were indexed here
        if (this.vocabulary() instanceof BasicInstSet vocabulary)
            vocabulary.ownReferVids().stream()
                    .filter(reference -> reference.isUri() && reference.uriValue().test(pattern))
                    .forEach(reference -> vocabulary.unroute(reference.uriValue()));
    }

    // ======================== lookup ========================

    /**
     * THE INSTRUCTION SETS THIS LEVEL'S LOOKUP WEIGHS (Marko, 2026-10-25: "Instsets shouldn't be in Memory as spaces
     * (though they are spaces). They should be in InstSet only."). They are not in this index — {@link #spaces()}
     * holds data spaces alone — but the libraries this level's machine mounted ({@link BasicInstSet#ownLibraries()})
     * compete with its data spaces for an address, so the most specific claimant answers exactly as when they were
     * indexed here: "the most specific gains the write" — /m/math/cat/… to /m/math/cat, /m/math/… to /m/math.
     */
    private List<Space> libraries() {
        final InstSet vocabulary = this.vocabulary();
        if (null == vocabulary)
            return List.of();
        return vocabulary instanceof BasicInstSet basic
                ? List.copyOf(basic.ownLibraries())
                : vocabulary.members().stream().filter(BasicInstSet::isLibrary).map(isa -> (Space) isa).toList();
    }

    @Override
    public <SPACE extends Space> SPACE findSpace(final fURI vid) {
        // snapshot first: the space index is a mutable LinkedHashMap, and addSpace can land a space on another thread
        // mid-iteration (or re-entrantly during boot), which trips the map's fail-fast iterator
        final List<Space> candidates = new ArrayList<>();
        new ArrayList<Obj>(this.spaces().jvm().values()).forEach(space -> candidates.add(space.as()));
        candidates.addAll(this.libraries());
        return Memory.mostSpecific(candidates, vid);
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        final Space indexed = this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .filter(s -> s.vid().test(vid))
                .findFirst()
                .orElse(null);
        if (null != indexed)
            return (SPACE) indexed;
        return (SPACE) this.libraries().stream().filter(s -> s.vid().test(vid)).findFirst().orElse(null);
    }

    /**
     * Release the spaces this level holds, and nothing else.
     * <p>
     * This is what makes the lease real: a frame's spaces were created by that frame, so closing the frame's
     * memory collects them, and an address that existed for the computation stops existing with it. Nothing
     * inherited is reachable from here — an inherited space is in the previous level, not this one — so a frame
     * cannot close its parent's resources by mistake.
     */
    @Override
    public void close() {
        this.spaces().jvm().values().stream()
                .filter(v -> v instanceof AutoCloseable)
                .forEach(CommonUtil::close);
        this.spaces().jvm().clear();
        LOG.info("closing memory for machine %s", this.machine().vid());
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .anyMatch(s -> vid.test(s.pattern()))
                || this.libraries().stream().anyMatch(s -> vid.test(s.pattern()));
    }

    // ======================== the namespace: short names + prefixes ========================

    @Override
    public void unregisterRedirect(final fURI small, final fURI big) {
        if (big.isRelative())
            return;
        this.smallToBigRoutes.computeRaw(small, (k, v) -> {
            if (null != v) {
                v.removeIf(x -> x.equals(big.basePath()));
                if (v.isEmpty())
                    return null;
                return v;
            }
            return null;
        });
        this.bigToSmallRoutes.remove(uri(big));
    }

    @Override
    public void registerRedirect(final fURI small, final fURI big) {
        if (big.isRelative())
            return;
        this.smallToBigRoutes.computeRaw(small, (k, v) -> {
            if (null == v) {
                // ORDERED SHORTEST-FIRST, DISTINCT BY PATH. the comparator must not stop at pathLength: a
                // TreeSet treats compare==0 as "already present", so a length-only comparator silently DROPS
                // every same-length route for the name -- which route survived depended on registration order,
                // and the contains() guard below (same comparator) suppressed even the warning. the toString
                // tiebreak keeps shortest-first iteration (redirect() prefers the primary prefix, then the
                // first = shortest) while letting same-length routes coexist, loudly.
                final Set<fURI> set = Collections.synchronizedSet(new TreeSet<>(
                        Comparator.comparingInt(fURI::pathLength).thenComparing(fURI::toString)));
                set.add(big.basePath());
                return set;
            } else {
                if (!v.contains(big.basePath()) && !this.hasRegisteredPrefix(big) && v.stream().noneMatch(this::hasRegisteredPrefix))
                    LOG.warn("multiple redirects for {{b}}%s{{X}}: {{b}}%s {{g}}+ {{b}}%s{{X}} (consider prefixing import)", small, big, v.toString().replace("[", "").replaceAll("]", ""));
                v.add(big.basePath());
                return v;
            }
        });
        this.bigToSmallRoutes.putRaw(big, small);
    }

    private boolean hasRegisteredPrefix(final fURI target) {
        for (final Obj value : this.prefixToVID.values()) {
            if (target.hasPrefix((fURI) value.jvm()))
                return true;
        }
        return false;
    }

    @Override
    public fURI redirect(final fURI furi, final boolean external) {
        if (!furi.hasPoly() && furi.isGeneric())
            return furi;
        fURI temp;
        if (external) {
            final Set<fURI> set = this.smallToBigRoutes.getOrDefaultRaw(furi.basePath(), Set.of(furi));
            if (set.isEmpty()) {
                temp = this.getSpaceFor(furi).redirect(furi, true);
            } else if (set.size() > 1) {
                final Optional<fURI> preferred = set.stream().filter(f -> f.hasPrefix(this.primary.toString())).findFirst();
                temp = preferred.orElse(set.iterator().next());
            } else {
                temp = set.iterator().next();
            }
        } else {
            temp = this.bigToSmallRoutes.getOrDefaultRaw(furi.basePath(), furi);
        }
        temp = furi.hasPoly() ? temp.poly(furi.poly().stream().map(x -> this.redirect(f(x), external)).map(fURI::toString).toList()) : temp;
        temp = temp.c(furi.c()).q(furi.qMap());
        temp = furi.hasDom() ? temp.dom(this.redirect(furi.dom(), external)) : temp;
        temp = furi.hasRng() ? temp.rng(this.redirect(furi.rng(), external)) : temp;
        return temp;
    }

    @Override
    public void registerPrefix(final fURI prefix, final fURI vid) {
        final fURI existing = this.prefixToVID.getRaw(prefix);
        if (existing != null && !Objects.equals(vid, existing))
            throw MTronException.of("%s prefix already bound: %s + %s", prefix, vid, existing);
        this.prefixToVID.putRaw(prefix, vid);
        this.at(uri(PREFIX), this.prefixToVID.toRec(), MUTABLE);
        LOG.info("prefix {{b}}%s {{g}}=> {{b}}%s{{X}} registered", prefix, vid);
    }

    @Override
    public fURI alignPrefix(final fURI vid) {
        final fURI readableVID = vid.one();
        if (readableVID.hasScheme()) {
            final fURI prefixed = this.prefixToVID.getRaw(f(readableVID.scheme()));
            if (null != prefixed) {
                final fURI suffix = readableVID.scheme(null);
                final Set<fURI> routes = this.smallToBigRoutes.getOrDefaultRaw(suffix.basePath(), Set.of());
                final Optional<fURI> target = routes.stream()
                        .filter(r -> r.hasPrefix(prefixed.toString()))
                        .findFirst();
                if (target.isPresent())
                    return target.get().c(suffix.c()).q(suffix.qMap());
                return prefixed.extend(suffix);
            }
        }
        return readableVID;
    }

    public Obj read(final fURI vid) {
        if (null == vid || vid.isZero()) // || READ_AS_NOOBJ.contains(vid))
            return noobj();
        if (vid.equals(this.vid()) || vid.equals(vid.id()))
            return this;
        if (vid.hasPrefix(f("~"))) {
            // ~ resolves to the current machine's vid, before the relative/absolute split: ~/sys → /xyz/sys.
            // The bare ~ is the node vid (/. for the root); any extension resolves (. collapses): ~/thread → /thread.
            final fURI home = Machine.current().vid();
            return this.read(vid.equals(f("~")) ? home : home.extend(vid.pretract(1)).resolve());
        }

        final fURI readableVID = this.alignPrefix(vid);
        /// ///////////////////
        if (readableVID.isGeneric())
            return T(readableVID);
        // Resolution belongs to Memory — the guard and the prefix alignment above are the machine's, and the
        // big() fallback below is too, but *which space answers* is memory's question and is asked in one place.
        if (readableVID.test(STACK_PATTERN)) {
            final Obj stackObj = this.stack().read(readableVID);
            if (!stackObj.isNoObj())
                return stackObj;
        }
        // readAbsolute, not read: the machine resolves addresses through spaces whatever the vid's shape. The
        // relative branch is Memory's own contract, where a relative vid means "in the frame" — letting it claim
        // the machine's vids sent bulk relative writes to the argument stack instead of the space they named.
        final Obj obj = this.readAbsolute(readableVID);
        if (obj.isNoObj()) {
            final fURI bigVID = readableVID.big();
            // compare the RECONSTRUCTED retry against what was already tried: big() answers the base
            // path with the q dropped, so a q-carrying miss compared big-to-readable is NEVER equal,
            // and the raw comparison retried the same address forever -- a StackOverflowError at this
            // line, armed by any q-read that misses every space. the retry must carry the q back
            // BEFORE deciding whether it is new.
            final fURI retryVID = bigVID.q(vid.qMap());
            if (!retryVID.equals(readableVID) && !retryVID.equals(vid))
                return this.read(retryVID);
        }
        // todo c(mult vid.c())
        return obj;
    }
}
