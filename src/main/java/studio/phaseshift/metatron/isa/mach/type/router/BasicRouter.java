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

package studio.phaseshift.metatron.isa.mach.type.router;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.space.stackSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.m.type.reflect.ObjReflection;
import studio.phaseshift.metatron.isa.mach.type.MStats;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.Stats;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

@ObjReflection
public class BasicRouter extends AbstractSpace<Map<Obj, Obj>> implements Router {

    public static final Uri PRIMARY = uri("primary");
    public static final fURI ROUTER_TID = MACH_ISA_TID.extend("router");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(ALL.maybeSome(), ALL.maybe(), ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    protected final Stats iostats = new MStats();

    @JRecElement(key = "small_route", rng = "/m/lst[/m/uri]")
    private final ObjectMap<fURI, Set<fURI>> smallToBigRoutes = new ObjectMap<>();
    @JRecElement(key = "big_route", rng = "/m/uri")
    private final ObjectMap<fURI, fURI> bigToSmallRoutes = new ObjectMap<>();
    private final ObjectMap<fURI, fURI> prefixToVID = new ObjectMap<>();
    private fURI primary = M_ISA_TID;

    public BasicRouter(final fURI vid) {
        super(new HashMap<>(), new ConcurrentHashMap<>(Map.of(
                        uri(PATTERN), uri(ALL),
                        PRIMARY, uri(M_ISA_TID),
                        uri(Tokens.SPACE), rec(new ConcurrentHashMap<>(Map.of(uri("+/#"), new stackSpace(f("+/#"))))))),
                ROUTER_TID,
                vid);
        this.at(uri(ROUTE), this.smallToBigRoutes.toRec(), MUTABLE);
        //LOG.info("local router at %s", this.vid.toUri());
    }


    private static Obj appendOnRead(final boolean send, final Obj base, final Obj addition) {
        return addition.isNoObj() ? base : (send ? base.append(rel(addition.vid().toUri(), addition)) : base.append(addition));
    }

    public Rec at(final Obj key, final Obj value) {
        if (key.equals(PRIMARY))
            this.primary = value.uriValue();
        return super.at(key, value);
    }

    public synchronized void close() {
        try {
            this.spaces().jvm().keySet().forEach(vid -> this.removeSpace(vid.uriValue()));
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            super.close();
        }
    }

    @Override
    public Stats stats() {
        if (Router.loaded())
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
                final Set<fURI> set = new TreeSet<>(Comparator.comparingInt(fURI::pathLength));
                set.add(big.basePath());
                return set;
            } else {
                if (!v.contains(big.basePath()))
                    LOG.warn("multiple redirects for {{b}}%s{{X}}: {{b}}%s {{g}}+ {{b}}%s{{X}} (consider prefixing import)", small, big, v.toString().replace("[", "").replaceAll("]", ""));
                v.add(big.basePath());
                return v;
            }
        });
        this.bigToSmallRoutes.putRaw(big, small);
    }

    @Override
    public void registerPrefix(final fURI prefix, final fURI vid) {
        final fURI existing = this.prefixToVID.getRaw(prefix);
        if (existing != null && !Objects.equals(vid, existing))
            throw MTronException.of("%s prefix already bound: %s + %s", prefix, vid, existing);
        this.prefixToVID.putRaw(prefix, vid);
        this.at(uri(PREFIX), this.prefixToVID.toRec(), MUTABLE);
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
        return temp.resolve();
    }

    @Override
    public void addSpace(final Space space) {
        if (null == space.vid()) {
            LOG.debug("vid-less spaces are self-managed and not indexed by router: %s", space);
            return;
        }
        // Evict any previously registered space that shares the exact same pattern so
        // the fresh space can take its place.  Re-registering a pattern with a newer
        // incarnation (e.g. a fresh JDBC connection) replaces the stale one rather
        // than being silently dropped.  Resources (connections, etc.) are closed first.
        this.spaces().values()
                .map(r -> (Space) r)
                .filter(s -> space.pattern().compareTo(s.pattern()) == 0)
                .toList()
                .forEach(spc -> {
                    LOG.warn("%s evicting %s (same pattern %s)", space, spc.vid(), space.pattern());
                    spc.close();
                    if (spc.vid() != null)
                        this.spaces().jvm().remove(spc.vid().toUri());
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
        space.at(uri(SPACE), subSpaces, MUTABLE);
        this.spaces().jvm().put(null == space.vid() ? space.pattern().toUri() : space.vid().toUri(), space);
        Space.Helper.spaceOpenLog(this, space);
        // save routes registered by spaceS
        this.at(uri(ROUTE), this.smallToBigRoutes.toRec(), MUTABLE);
    }

    @Override
    public void removeSpace(final fURI pattern) {
        if (null == pattern)
            return;
        this.spaces().jvm()
                .entrySet()
                .stream()
                .filter(kv -> kv.getKey().uriValue().test(pattern))
                .toList()
                .stream()
                .peek(kv -> this.spaces().jvm().remove(kv.getKey()))
                .peek(kv -> {
                    this.prefixToVID.entrySet().stream().filter(pv -> pv.getValue().uriValue().test(((Space) kv.getValue()).pattern())).forEach(pv -> this.prefixToVID.remove(pv.getKey()));
                })
                .forEach(kv -> Space.Helper.spaceCloseLog(this, (Space) kv.getValue()));
    }

    @Override
    public <S extends Space> S getSpaceFor(final fURI match) {
        if (match.test(NOOBJ))
            return noobjSpace.single();
        // using jvm() for speed (given the heavy use of this method)
        final Optional<S> space = this.spaces().values()
                .map(Obj::<S>as)
                .filter(s -> match.basePath().test(s.pattern()))
                .min(Comparator.comparing(Space::pattern));
        if (space.isPresent())
            return space.get();
        else if (match.basePath().test(STACK_PATTERN))
            return (S) THREAD_STACK.get();
        else if (!BOOTING)
            throw MTronException.of("no active space supports pattern %s", match.toUri(false));
        else
            return noobjSpace.single();
    }

    private fURI alignPrefix(final fURI vid) {
        final fURI readableVID = vid.one();
        if (readableVID.hasScheme()) {
            final fURI prefixed = this.prefixToVID.getRaw(f(readableVID.scheme() + ":"));
            if (null != prefixed) {
                final fURI aligned = prefixed.extend(readableVID.scheme(null));
                return aligned;
            }
        }
        return readableVID;
    }

    @Override
    public void start() {

    }

    @Override
    public Obj read(final fURI vid) {
        if (null == vid || NOOBJ.equals(vid.basePath()) || vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
        if (vid.equals(this.vid()))
            return this;
        // if (vid.hasAuthority())
        //   return this.server().sendRecv((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, from_(vid.localize().toUri()).tryToInst());
        //   return this.server().sendRecv((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, from_(vid.localize().toUri()).tryToInst());
        final fURI readableVID = this.alignPrefix(vid);
        /// ///////////////////
        if (readableVID.isGeneric())
            return T(readableVID);
        if (readableVID.test(STACK_PATTERN)) {
            final Obj stackObj = Router.stack().read(readableVID.basePath());
            if (!stackObj.isNoObj())
                return stackObj;
        }
        final Space space = this.getSpaceFor(readableVID);
        final Obj obj = space.read(readableVID);
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
        /*if (vid.hasAuthority()) {
            this.server().send((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, start_(obj.vid(null)).to_(vid.localize().toUri()).tryToInst());
            return obj;
        }*/
        if (null == vid) {
            LOG.warn("the provided write uri was null");
            return noobj();
        }
        final fURI writableVID = this.alignPrefix(vid);
        /// ///////////////
        final Space space = this.getSpaceFor(writableVID);
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} in %s", obj, vid, space.vid());
        return space.write(writableVID, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        final fURI alignedVID = this.alignPrefix(vid);
        return this.spaces().jvm().values().stream().map(Obj::<Space>as).anyMatch(s -> alignedVID.test(s.pattern()));
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        return (SPACE) this.spaces().jvm().values().stream().map(Obj::<Space>as).filter(s -> s.vid().test(vid)).findFirst().orElse(null);
    }

    @Override
    public BasicRouter apply(final Obj other) {
        return null;
    }

    @Override
    public Router clone() {
        Space.Helper.noCloneWarning(this);
        return this;
    }

    @Override
    public Router clone(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = jvm;
        this.tid = tid;
        this.vid = vid;
        return this;
    }

    @Override
    public String toString() {
        return Router.Helper.routerToString(this);
    }
}
