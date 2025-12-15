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

package studio.phaseshift.metatron.lang.sys.router.impl;

import studio.phaseshift.metatron.Registry;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.core.mach.stackSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.util.noobjSpace;
import studio.phaseshift.metatron.lang.util.serial.Serializers;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class MRouter extends MSpace<MServer> implements Router {

    public static final Serializers SERIALIZERS = new Serializers();
    public static final Uri PRIMARY = uri("primary");
    public static final fURI ROUTER_TID = sysInstSet.SYS_TID.extend("router");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(fURI.ALL.maybeSome(), fURI.ALL.maybe(), fURI.ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Map<fURI, Set<fURI>> smallToBigRewrites = new HashMap<>();
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();
    private fURI primary = MTRON_TID;

    public MRouter(final fURI host, final fURI vid) {
        super(new MServer(host), new ConcurrentHashMap<>(Map.of(
                        uri(Tokens.PATTERN), uri(ALL),
                        PRIMARY, uri(MTRON_TID),
                        uri(Tokens.SPACE), rec(new ConcurrentHashMap<>(Map.of(uri("+/#"), new stackSpace(f("+/#"))))))), f("#"),
                sysInstSet.SYS_TID.extend("router"),
                vid);
        LOG.info("local router {{b}}%s{{/b}}", this);
        LOG.info("available serializers: %s", SERIALIZERS.getSerializers().jvm().keySet());
    }


    private static Obj appendOnRead(final boolean send, final Obj base, final Obj addition) {
        return addition.isNoObj() ? base : (send ? base.append(rel(addition.vid().toUri(), addition)) : base.append(addition));
    }

    @Override
    public MServer server() {
        return this.sjvm();
    }

    public void start() {
        this.server().start();
    }

    public Rec put(final Obj key, final Obj value) {
        if (key.equals(PRIMARY))
            this.primary = value.uriValue();
        return super.put(key, value);
    }

    public synchronized void close() {
        final List<fURI> list = this.spaces().elements().map(Rel::second).map(Obj::vid).toList();
        list.forEach(this::removeSpace);
        this.server().close();

    }

    public void registerRewrite(final fURI small, final fURI big) {
        this.smallToBigRewrites.compute(small, (k, v) -> {
            if (null == v) {
                final Set<fURI> set = new HashSet<>();
                set.add(big.basePath());
                return set;
            } else {
                v.add(big.basePath());
                return v;
            }
        });
        this.bigToSmallRewrites.put(big, small);
    }

    @Override
    public fURI rewrite(final fURI furi, final boolean big) {
        fURI temp;
        if (big) {
            final Set<fURI> set = this.smallToBigRewrites.getOrDefault(furi.basePath(), Set.of(furi));
            if (set.size() > 1) {
                final Iterator<fURI> furis = set.stream().filter(f -> f.hasPrefix(this.primary)).iterator();
                temp = furis.hasNext() ? furis.next() : set.iterator().next();
            } else {
                temp = set.iterator().next();
            }
        } else {
            temp = this.bigToSmallRewrites.getOrDefault(furi.basePath(), furi);
        }
        temp = temp.c(furi.c()).queryMap(furi.queryMap());
        temp = temp.hasDom() ? temp.dom(this.rewrite(temp.dom(), big)) : temp;
        temp = temp.hasRng() ? temp.rng(this.rewrite(temp.rng(), big)) : temp;
        return temp.resolve();
    }

    @Override
    public void addSpace(final Space space) {
        // if (this.vid != null && !(space instanceof InstSet) && (space.vid() == null || !space.vid().matches(this.vid.extend(ALL)))) {
        //     LOG.warn("space not indexed by global router: %s", space);
        //     return;
        // }
        this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .filter(s -> space.pattern().bimatches(s.pattern()))
                .findAny()
                .ifPresent(s -> {
                    LOG.error("%s and %s have overlapping address spaces: %s <=> %s", space.pattern(), s.pattern(), space, s);
                });
        this.spaces().jvm().put(null == space.vid() ? space.pattern().toUri() : space.vid().toUri(), space);
        Space.Helper.spaceOpenLog(this, space);

        //this.write(space.vid(), space);
    }

    @Override
    public void removeSpace(final fURI vid) {
        this.spaces().elements().map(Rel::second).map(Obj::<Space>as).filter(s -> Objects.equals(vid, s.vid())).forEach(s -> {
            try {
                if (null != s.vid()) {
                    final Space space = (Space) this.spaces().jvm().remove(s.vid().toUri());
                    if (null != space) {
                        Space.Helper.spaceCloseLog(this, space);
                    }
                }
            } catch (final Exception e) {
                LOG.error(e);
            }
        });
    }

    public <S extends Space> S getSpace(final fURI match) {
        if (match.matches(fURI.NOOBJ))
            return noobjSpace.single();
        //     final fURI mvid = this.smallToBigRewrites.getOrDefault(vid,vid);
        final Optional<S> space = this.spaces().jvm().values().stream() // using jvm() for speed (given the heavy use of this method)
                .map(Obj::<Space>as)
                //.filter(s -> s.status().equals(Status.active))
                .filter(s -> match.basePath().matches(s.pattern()))
                .map(s -> (S) s)
                .findAny();
        if (space.isPresent())
            return space.get();
        else if (match.basePath().matches(f("+/#")))
            return (S) THREAD_STACK.get();
        else if (Registry.open().has(match))
            return Registry.open().load(match);
        else if (!BOOTING)
            throw MTronException.of("no active space supports pattern %s", match.toUri(false));
        else
            return noobjSpace.single();
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
        if (vid.hasAuthority())
            return this.server().sendRecv((a,b)->a.authority().matches(b.remoteHost().authority()), vid, from_(vid.localize().toUri()).tryToInst());
        /// ///////////////////
        final fURI local = vid;//.authority(null).scheme(null);
        if (local.matches(f("+/#"))) {
            final Obj stack = Router.stack().read(local.basePath());
            if (!stack.isNoObj())
                return stack;
        }
        final Space space = this.getSpace(local);
        return space.read(local);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (vid.hasAuthority()) {
            this.server().send((a,b)->a.authority().matches(b.remoteHost().authority()),vid, start_(obj.vid(null)).to_(vid.localize().toUri()).tryToInst());
            return obj;
        }
        /// ///////////////
        final Space space = this.getSpace(vid);
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} in %s", obj, vid, space);
        return space.write(vid, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces().jvm().values().stream().map(Obj::<Space>as).anyMatch(s -> vid.matches(s.pattern()));
    }

    @Override
    public MRouter apply(final Obj other) {
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
    public boolean equals(final Object other) {
        return Space.Helper.spaceEquals(this, other);
    }


    @Override
    public int hashCode() {
        return Space.Helper.spaceHashCode(this);
    }

    @Override
    public String toString() {
        return Router.Helper.routerToString(this);
    }
}
