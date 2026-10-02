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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.space.stackSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.TypeGraph;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.mach.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * AbstractMachine — the machine's concrete body: the address index, the short-name tables and the
 * authority guard, plus the {@code apply}/{@code clone}/{@code stats}/{@code close} plumbing.
 * <p>
 * {@link Machine} is the contract; this class is the one implementation of it. Before the takeover
 * it was {@code BasicRouter}, and {@code Machine} was layered on top of it — which is why a machine
 * could not be its own root. Now the arrow points the other way: a machine is the container, and
 * the address space is one of the things it holds.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMachine extends AbstractSpace<Map<Obj, Obj>> implements Machine {

    public static final Uri PRIMARY = uri("primary");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(ALL.maybeSome(), ALL.maybe(), ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    protected final Stats iostats = new MStats();

    private final ObjectMap<fURI, Set<fURI>> smallToBigRoutes = new ObjectMap<>();
    private final ObjectMap<fURI, fURI> bigToSmallRoutes = new ObjectMap<>();
    private final ObjectMap<fURI, fURI> prefixToVID = new ObjectMap<>();
    private fURI primary = M_ISA_TID;

    private Memory resolvedMemory = null;
    private Network resolvedNetwork = null;

    /**
     * Resolve each slot template exactly once. The first resolution happens in the constructor, where the
     * components are parented, so by the time the resolution path reads through these it is a field read. That
     * matters: {@code resolutionMemory()} calls this from inside {@code read}, and a slot that re-applied its
     * template on every access would allocate there — re-entering type resolution and {@code read} itself.
     * <p>
     * {@code Machine.super} is legal here rather than in {@link BasicMachine} because this class implements
     * {@code Machine} directly; a subclass implementing it only through this one cannot reach the default.
     */
    @Override
    public Memory ownMemory() {
        if (null == this.resolvedMemory)
            this.resolvedMemory = Machine.super.ownMemory();
        return this.resolvedMemory;
    }

    @Override
    public Network ownNetwork() {
        if (null == this.resolvedNetwork)
            this.resolvedNetwork = Machine.super.ownNetwork();
        return this.resolvedNetwork;
    }

    public AbstractMachine(final fURI vid) {
        this(ALL, vid);
    }

    public AbstractMachine(final fURI pattern, final fURI vid) {
        this(seedConfig(pattern), MACH_MACHINE_TID, vid);
    }

    /**
     * the pattern/vid scaffolding, with the memory built once and closed over by its slot
     */
    private static Map<Obj, Obj> seedConfig(final fURI pattern) {
        final Memory memory = seedMemory();
        return new ConcurrentHashMap<>(Map.of(
                uri(PATTERN), uri(pattern),
                PRIMARY, uri(M_ISA_TID),
                uri(MEMORY), instLambda(ignore -> memory)));
    }

    /**
     * The root memory: empty bindings, and an index that already holds the {@code +/#} stack space — the same
     * seed the router carried, now one level in where the index actually lives.
     */
    private static Memory seedMemory() {
        final Memory memory = new BasicMemory();
        memory.spaces().jvm().put(uri("+/#"), new stackSpace(f("+/#")));
        return memory;
    }

    public AbstractMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(), withMemory(jvm), tid, vid);
        this.at(uri(ROUTE), this.smallToBigRoutes.toRec(), MUTABLE);
        // Adopt the components. `at` is what sets a value's parent to the rec holding it, and
        // Machine.Component.machine() walks exactly that — seeding through the constructor map alone would leave
        // every component's machine() quietly answering mach0().
        // Parent the RESOLVED components, not the slot values: the slots hold templates now, and parenting one
        // would leave the memory's own machine() answering mach0().
        this.ownMemory().parent(this);
        this.ownNetwork().parent(this);
        //LOG.info("local router at %s", this.vid.toUri());
    }

    /**
     * Every machine has a memory from the moment it exists. Guaranteeing it here rather than creating one on
     * demand is what keeps the read path allocation-free — an {@code ownMemory()} that constructed one lazily
     * would do so inside {@code read}, and building an Obj there re-enters type resolution and {@code read}.
     */
    private static Map<Obj, Obj> withMemory(final Map<Obj, Obj> jvm) {
        // built eagerly and closed over, so applying a slot never constructs
        if (!jvm.containsKey(uri(MEMORY))) {
            final BasicMemory memory = new BasicMemory();
            jvm.put(uri(MEMORY), instLambda(ignore -> memory));
        }
        if (!jvm.containsKey(uri(NETWORK))) {
            final BasicNetwork network = new BasicNetwork();
            jvm.put(uri(NETWORK), instLambda(ignore -> network));
        }
        return jvm;
    }


    public Rec at(final Obj key, final Obj value) {
        if (key.equals(PRIMARY))
            this.primary = value.uriValue();
        return super.at(key, value);
    }

    @Override
    public synchronized void close() {
        try {
            // Snapshot the keys first: removeSpace mutates the index, and removing while iterating the live
            // entry set is a ConcurrentModificationException — which surfaced as a shutdown that reported the
            // entry set rather than the spaces it had closed.
            this.spaces().jvm().keySet().stream()
                    .filter(Obj::isUri)
                    .toList()
                    .forEach(key -> {
                        try {
                            this.removeSpace(key.uriValue());
                        } catch (final Exception e) {
                            LOG.warn(e);
                        }
                    });
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            super.close();
        }
    }

    @Override
    public Stats stats() {
        if (Machine.loaded())
            return this.iostats;
        throw MTronException.of("router not loaded");
    }

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

    public void registerRedirect(final fURI small, final fURI big) {
        if (big.isRelative())
            return;
        this.smallToBigRoutes.computeRaw(small, (k, v) -> {
            if (null == v) {
                final Set<fURI> set = Collections.synchronizedSet(new TreeSet<>(Comparator.comparingInt(fURI::pathLength)));
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

    /**
     * True if {@code target} lives under a namespace that has a prefix registered (a
     * {@code prefixToVID} entry whose vid is a path-prefix of {@code target}). Used to silence
     * the "multiple redirects ... consider prefixing import" warning once the short-name
     * collision is already disambiguable via a prefix.
     */
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
    public synchronized void addSpace(final Space space) {
        if (null == space.vid()) {
            LOG.debug("vid-less spaces are self-managed and not indexed by router: %s", space);
            return;
        }
        // NOTE (intended change, deliberately not made yet): same-pattern collision should become an
        // OVERLAP check — `space.pattern().bimatches(existing.pattern())` — and a loud conflict, rather than
        // silently closing a live space. Holding the destructive semantics through the Router takeover keeps
        // this move behaviour-preserving, so a regression here can only mean the move broke something.
        // Evict any previously registered space that shares the exact same pattern so
        // the fresh space can take its place.  Re-registering a pattern with a newer
        // incarnation (e.g. a fresh JDBC connection) replaces the stale one rather
        // than being silently dropped.  Resources (connections, etc.) are closed first.
        this.ownMemory().spaces().values()
                .map(r -> (Space) r)
                .filter(s -> space.pattern().compareTo(s.pattern()) == 0)
                .toList()
                .forEach(spc -> {
                    LOG.warn("%s evicting %s (same pattern %s)", space, spc.vid(), space.pattern());
                    // Eviction is a replacement, not a removal. spc.close() routes through
                    // removeSpace(), which drops any prefix bound to this space's pattern (e.g.
                    // `web`, `ide`, `math`). Snapshot those prefixes and restore them after close
                    // so the new incarnation keeps its short-name prefix.
                    final List<Map.Entry<Obj, Obj>> prefixes = this.prefixToVID.entrySet().stream()
                            .filter(pv -> pv.getValue().uriValue().test(spc.pattern()))
                            .toList();
                    spc.close();
                    prefixes.forEach(pv -> this.prefixToVID.put(pv.getKey(), pv.getValue()));
                    this.ownMemory().removeSpace(spc.pattern());
                });
        final Space superSpace = this.hasSpaceFor(space.pattern()) ? this.getSpaceFor(space.pattern()) : noobjSpace.single();
        final Rec subSpaces = space.jvm().getOrDefault(uri(SPACE), rec()).as();
        if (!(superSpace instanceof noobjSpace)) {
            final Rec superSpaces = superSpace.jvm().getOrDefault(uri(SPACE), rec()).as();
            subSpaces.at(uri(SUPER), null == superSpace.vid() ? uri(superSpace.pattern()) : auto_from_(superSpace.vid()).tryToInst(), MUTABLE);
            subSpaces.parent(superSpace);
            superSpaces.at(uri(SUB), superSpaces.jvm().getOrDefault(uri(SUB), MObjs.objs0()).append(auto_from_(null == space.vid() ? space.tid() : space.vid()).tryToInst()), MUTABLE);
            superSpace.at(uri(SPACE), superSpaces, MUTABLE);
        }
        if (!subSpaces.isEmpty())
            space.at(uri(SPACE), subSpaces, MUTABLE);
        this.ownMemory().addSpace(space);
        Space.Helper.spaceOpenLog(this, space);
        // save routes registered by spaceS
        this.at(uri(ROUTE), this.smallToBigRoutes.toRec(), MUTABLE);
    }

    @Override
    public synchronized void removeSpace(final fURI pattern) {
        if (null == pattern)
            return;
        this.ownMemory().spaces().jvm()
                .entrySet()
                .stream()
                .filter(kv -> kv.getKey().uriValue().test(pattern))
                .toList()
                .stream()
                .peek(kv -> this.ownMemory().removeSpace(pattern))
                .peek(kv -> {
                    this.prefixToVID.entrySet().stream().filter(pv -> pv.getValue().uriValue().test(((Space) kv.getValue()).pattern())).forEach(pv -> this.prefixToVID.remove(pv.getKey()));
                })
                .forEach(kv -> Space.Helper.spaceCloseLog(this, (Space) kv.getValue()));
    }

    // ======================== authority dispatch ========================

    /**
     * The declared peer roster: a rec of {@code <authority-uri> => <transport-inst>}. The value is the
     * transport — an inst that takes the message ({@code from(localized)} for a read, {@code start(localized)
     * .ref(obj)} for a write) and returns the peer's response. Keeping it an inst is what keeps the Router
     * free of any transport dependency: swapping ws for http, mqtt or a gRPC client is a roster change.
     * <p>
     * It lives at {@code /sys/peer}, beside {@code /sys/thread}, and <em>not</em> under {@code /sys/mach}: the
     * Machine claims {@code /sys/mach} and {@code AbstractSpace}'s default writer is a no-op, so a roster
     * written there is silently dropped — a failure mode worth remembering, because the symptom is a peer that
     * quietly resolves to the local wildcard-host space instead and <em>appears to work</em>.
     */
    public static fURI peerRosterPath() {
        return SYS.extend(PEER);
    }

    /**
     * The authority guard. Three outcomes, and the third is the important one:
     * <ol>
     *   <li><b>mine, and unclaimed by any served space</b> — strip the authority and resolve locally
     *       ({@code localize}), so {@code ws://localhost:8555/usr/x} reaches the local {@code /usr/#} space
     *       rather than the ws <em>server</em> space whose wildcard host would otherwise claim it;</li>
     *   <li><b>a declared peer</b> — delegate over its declared transport;</li>
     *   <li><b>anything else</b> — fall through untouched, so a wildcard space can still legitimately claim
     *       it ({@code httpspace}'s Jsoup fetch, {@code wsspace} as a server). Fail-closed: an undeclared host
     *       simply is not a peer.</li>
     * </ol>
     * Runs <em>before</em> {@code getSpaceFor}, never inside it — {@code getSpaceFor} is a local primitive
     * with ~20 call sites (including compile-time rewrites) that must not reach the network.
     *
     * @return the dispatched result, or empty when authority dispatch does not apply
     */
    private Optional<Obj> dispatchForeign(final fURI vid, final Obj obj) {
        if (null == vid || !vid.hasHost() || null == vid.authority())
            return Optional.empty();
        if (this.own(vid)) {
            // A space whose pattern names a host is *serving* that address — `wsspace`/`httpspace` pattern on
            // `ws://#`/`http://#` and keep a session under `ws://localhost:PORT/<route>/<n>`. Localizing such a
            // uri would strip the authority and hand it to a different space, so the session write would vanish
            // and the peer on the socket would simply never be answered. But the same authority is also a
            // legitimate way to name our *own* data (`ws://localhost:PORT/usr/x`), so defer only when
            // localizing would land on nothing: if the path still resolves, the authority was decoration.
            if (this.servesAddress(vid) && !this.localizesToSpace(vid))
                return Optional.empty();
            return Optional.of(null == obj ? this.read(vid.localize()) : this.write(vid.localize(), obj));
        }
        final Obj transport = this.resolutionNetwork().transportOf(vid.authority());
        if (transport.isNoObj() || !transport.isObjInst())
            return Optional.empty();
        final fURI remote = vid.localize();
        try {
            final Obj message = null == obj
                    ? from_(uri(remote)).tryToInst()
                    : start_(remote.toUri()).ref_(obj);
            LOG.debug("dispatching %s %s to peer %s", null == obj ? "read" : "write", remote, vid.authority());
            final Obj response = transport.apply(message);
            return Optional.of(response.isNoObj()
                    ? fail("no response from peer %s for %s", vid.authority(), remote)
                    : response);
        } catch (final Exception e) {
            return Optional.of(fail(e));
        }
    }

    /**
     * True when a mounted space's pattern names a host <em>and</em> matches {@code vid} — that space is the
     * server for this address and must win over the guard's localization. The host test is what keeps this
     * from being trivially true: a plain path space ({@code /usr/#}) or the catch-all pattern matches almost
     * anything, while only an authority-claiming space can legitimately own an authority-addressed uri.
     */
    private boolean servesAddress(final fURI vid) {
        final fURI base = vid.basePath();
        return this.spaces().values()
                .map(Obj::<Space>as)
                .anyMatch(s -> s.pattern().hasHost() && base.test(s.pattern()));
    }

    /**
     * True when the authority-free form of {@code vid} is claimed by a plain (hostless) space — the test for
     * "the authority was decoration". Only hostless patterns count: a host-pattern space claiming it is the
     * very ambiguity this is deciding.
     */
    private boolean localizesToSpace(final fURI vid) {
        final fURI local = vid.localize().basePath();
        return this.spaces().values()
                .map(Obj::<Space>as)
                .anyMatch(s -> !s.pattern().hasHost() && local.test(s.pattern()));
    }

    /**
     * The address index is the machine's Memory, so a machine's spaces are exactly the spaces its memory holds —
     * there is no second place a space could be registered. The root's memory holds every global space; a frame's
     * holds only what that frame introduced, which is what makes a pop take them with it.
     */
    @Override
    public Rec spaces() {
        // resolutionMemory, not memory(): reading must never materialize a frame. Materializing constructs a
        // component, constructing an Obj runs its type check, and the type check reads through the router — which
        // lands back in read(). Only an explicit memory() call forms a frame's views.
        return this.resolutionMemory().spaces();
    }

    /**
     * The memory resolution reads through: the frame's if it already has one, else the machine's own. It never
     * materializes a frame, because a lookup that did would allocate an Obj on the read path — and allocating an
     * Obj runs a type check, which resolves a type through the router, which lands back in {@code read}.
     */

    @Override
    public <S extends Space> S getSpaceFor(final fURI match) {
        return this.resolutionMemory().getSpaceFor(match);
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        return this.resolutionMemory().getSpace(vid);
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

    private fURI alignPrefix(final fURI vid) {
        final fURI readableVID = vid.one();
        if (readableVID.hasScheme()) {
            final fURI prefixed = this.prefixToVID.getRaw(f(readableVID.scheme()));
            if (null != prefixed) {
                final fURI suffix = readableVID.scheme(null);
                // The suffix is a short name within the prefix's namespace (e.g. `web:java`). Short
                // names are redirects — types register `java -> /m/web/mime/java`, insts register
                // `command -> /m/ide/inst/command` — and a name can be shared across instsets (both
                // /m/web and /m/ide register `java`). So resolve the suffix against the redirect table,
                // preferring the target that lives under this prefix's vid — that scoping is the whole
                // point of the prefix. Fall back to the naive prefix + path extension when no redirect
                // target sits under the prefix (e.g. `ide:inst/command`).
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

    @Override
    public Obj read(final fURI vid) {
        if (null == vid || NOOBJ.equals(vid.basePath()) || vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
        if (vid.equals(this.vid()))
            return this;
        // authority guard — mine resolves locally, a declared peer delegates, everything else falls through
        final Optional<Obj> foreign = this.dispatchForeign(vid, null);
        if (foreign.isPresent())
            return foreign.get();
        final fURI readableVID = this.alignPrefix(vid);
        /// ///////////////////
        if (readableVID.isGeneric())
            return T(readableVID);
        // Resolution belongs to Memory — the guard and the prefix alignment above are the machine's, and the
        // big() fallback below is too, but *which space answers* is memory's question and is asked in one place.
        if (readableVID.test(STACK_PATTERN)) {
            final Obj stackObj = Memory.argStack().read(readableVID.basePath());
            if (!stackObj.isNoObj())
                return stackObj;
        }
        // readAbsolute, not read: the machine resolves addresses through spaces whatever the vid's shape. The
        // relative branch is Memory's own contract, where a relative vid means "in the frame" — letting it claim
        // the machine's vids sent bulk relative writes to the argument stack instead of the space they named.
        final Obj obj = this.resolutionMemory().readAbsolute(readableVID);
        if (obj.isNoObj()) {
            final fURI bigVID = readableVID.big();
            if (!bigVID.equals(readableVID))
                return this.read(bigVID);
        }
        // todo c(mult vid.c())
        return obj;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (null == vid) {
            LOG.warn("the provided write uri was null");
            return noobj();
        }
        // authority guard — mirrors read()
        final Optional<Obj> foreign = this.dispatchForeign(vid, obj);
        if (foreign.isPresent())
            return foreign.get();
        final fURI writableVID = this.alignPrefix(vid);
        /// ///////////////
        // A machine's own address is the machine. read() already says so (`vid.equals(this.vid())` -> this);
        // write had no matching guard, and it needs one now that the root's vid is `/`, which no space covers.
        // The auto-registration path (objCheckAndSave) writes the machine back at its own vid on every mutation
        // of its rec — with vid /sys/router that silently resolved through /sys/#, and at `/` it would instead
        // fail-closed for a write that is already true. Anything else at `/` still falls through and throws.
        if (obj == this && writableVID.equals(this.vid()))
            return obj;
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} at %s", obj, vid, writableVID);
        // invalidate cached type resolutions that this write may touch --
        // before the write, so a failed write still errs on the safe side.
        TypeGraph.global().onWrite(writableVID);
        // as in read(): which space answers is Memory's question, asked in one place
        return this.resolutionMemory().writeAbsolute(writableVID, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        // alignment is the ISA's business (a short name is a redirect), so it happens here and the memory sees an
        // address it can resolve. Memory never renames an address.
        return this.resolutionMemory().hasSpaceFor(this.alignPrefix(vid));
    }


    /**
     * A machine is a callable obj: {@code machine.apply(code)} compiles then runs. The stub this
     * replaces returned {@code null}; re-pinning it to the {@link Machine} contract is the one thing
     * a concrete machine base owes the interface.
     */
    @Override
    public Obj apply(final Obj call) {
        return Machine.super.apply(call);
    }

    @Override
    public Machine clone() {
        // A real shallow copy, as MObj already provides: the override exists only because Machine.clone() narrows the
        // return type, and stubbing it made `Obj.vid(fURI)` (which IS this.clone(jvm, tid, vid)) a no-op on a
        // machine — so `clone().selfVID(v)` silently retargeted the PARENT instead of minting a child.
        // MObj.clone() already wraps the checked exception, so super.clone() here is unchecked.
        return (Machine) super.clone();
    }

    @Override
    public Machine clone(final Object jvm, final fURI tid, final fURI vid) {
        // set the fields on the COPY, never on this: that is what distinguishes vid() (copy then set) from
        // selfVID() (set in place), and the difference is the whole point of the pair.
        return (Machine) this.clone().self(jvm, tid, vid);
    }

    /*@Override
    public String toString() {
        return 
    }*/
}
