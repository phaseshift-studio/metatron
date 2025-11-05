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

package studio.phaseshift.metatron.lang.msys.impl;

import studio.phaseshift.metatron.Registry;
import studio.phaseshift.metatron.furi.Qs;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.lang.msys.impl.net.MServer;
import studio.phaseshift.metatron.lang.msys.msysInstSet;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRel;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.NullSpace;
import studio.phaseshift.metatron.space.stack.StackSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class MRouter extends MSpace<MServer> implements Router {

    public static final fURI ROUTER_TID = msysInstSet.MSYS_TID.extend("router");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(fURI.ALL.maybeSome(), fURI.ALL.maybe(), fURI.ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Map<fURI, fURI> smallToBigRewrites = new HashMap<>();
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();
    private fURI vid;

    public MRouter(final fURI host, final fURI vid) {
        super(new MServer(host), new ConcurrentHashMap<>(Map.of(uri(SPACE), rec(new ConcurrentHashMap<>(Map.of(uri("+/#"), new StackSpace(f("+/#"))))))), f("#"), msysInstSet.MSYS_TID.extend("router"), vid);
        this.vid = vid;
        LOG.info("local router {{b}}%s{{/b}}", this);
    }

    private static Obj appendOnRead(final boolean send, final Obj base, final Obj addition) {
        return addition.isNoObj() ? base : (send ? base.append(MRel.of(addition.vid().toUri(), addition)) : base.append(addition));
    }

    @Override
    public MServer server() {
        return this.sjvm();
    }

    public void start() {
        this.server().start();
    }

    public synchronized void close() {
        final List<fURI> list = this.spaces().jvm().values().stream().map(Obj::vid).toList();
        list.forEach(this::removeSpace);
        this.server().stop();

    }

    public void registerRewrite(final fURI small, final fURI big) {
        this.smallToBigRewrites.put(small, big);
        this.bigToSmallRewrites.put(big, small);
    }

    @Override
    public fURI rewrite(final fURI furi, final boolean big) {
        fURI temp = (big ? this.smallToBigRewrites.getOrDefault(furi.basePath(), furi) : this.bigToSmallRewrites.getOrDefault(furi.basePath(), furi)).c(furi.c()).queryMap(furi.queryMap());
        temp = temp.hasDom() ? temp.dom(this.rewrite(temp.dom(), big)) : temp;
        temp = temp.hasRng() ? temp.rng(this.rewrite(temp.rng(), big)) : temp;
        return temp.resolve();
    }

    @Override
    public void addSpace(final Space space) {
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
        this.spaces().jvm().values().stream().map(Obj::<Space>as).filter(s -> Objects.equals(vid, s.vid())).forEach(s -> {
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
            return NullSpace.single();
        //     final fURI mvid = this.smallToBigRewrites.getOrDefault(vid,vid);
        final Optional<S> space = this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .filter(s -> match.basePath().matches(s.pattern()))
                .map(s -> (S) s)
                .findAny();
        if (space.isPresent())
            return space.get();
        else if (match.basePath().matches(f("+/#")))
            return (S) THREAD_STACK.get();
        else if (Registry.singleton().has(match))
            return Registry.singleton().load(match);
        else if (!BOOTING)
            throw MTronException.of("no structure supports pattern %s", match.toUri(true));
        else
            return NullSpace.single();
    }

    @Override
    public Obj read(final fURI vid) {
        // if (vid.hasAuthority()) {
        //     return objs(this.server.cluster(vid.authority()).map(v -> v.sendRecvObj(from_(uri(vid.authority(null).scheme(null))))));
        // }
        if (vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
       /* if (false && vid.hasAuthority() && !vid.hasAuthority(this.server.authority())) {
            return this.server.cluster(vid.authority().extend("#")).map(msc -> {
                final FutureObj<Obj> future = msc.sendRecvObj(from_(vid.toUri()));
                return future.get(5000);
            }).reduce(NoObj.single(), Obj::append);
        } else {*/
        final fURI local = vid;//.authority(null).scheme(null);
        final Space space = this.getSpace(local);
        //if (null != space.vid() && !space.vid().segments().isEmpty())
        //    LOG.trace("reading {{b}}%s{{/b}} from {{b}}%s{{/b}}", vid, space.vid());
        return MRouter.appendOnRead(vid.isBranch(), space.read(vid), this.vid.onlyMatches(vid) ? this : noobj());
        // }
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        fURI local;
        if (vid.hasAuthority()) {
            if (vid.hasAuthority(this.server().authority())) {
                local = vid.scheme(null).authority(null);
            } else {
                return this.server().cluster(vid.authority().extend("#")).map(msc -> {
                    final FutureObj<Obj> future = msc.sendRecvObj(start_(obj).to_(vid.toUri()));
                    return future.get(5000);
                }).reduce(noobj(), Obj::append);
            }
        } else
            local = vid;

        final Space space = this.getSpace(local);
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} in %s", obj, local, space);
        return space.write(local, obj);
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
    public fURI tid() {
        return ROUTER_TID;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Router clone(final Object jvm, final fURI tid, final fURI vid) {
        Space.Helper.noCloneWarning(this);
        return this;
    }

    @Override
    public Qs qs() {
        return null;
    }

    @Override
    public Router vid(final fURI vid) {
        this.vid = vid;
        return this;
    }

    @Override
    public String toString() {
        return Router.Helpers.routerToString(this);
    }
}
