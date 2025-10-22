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

package studio.phaseshift.metatron.space.router;

import studio.phaseshift.metatron.io.net.FutureObj;
import studio.phaseshift.metatron.io.net.MServer;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.space.NullSpace;
import studio.phaseshift.metatron.space.Qs;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.MTRON_TID;

public class MRouter implements Router {

    private static final fURI ROUTER_TID = MTRON_TID.extend("router");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(fURI.ALL.maybeSome(), fURI.ALL.maybe(), fURI.ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Map<fURI, Space> spaces = new ConcurrentHashMap<>();
    private final Map<fURI, fURI> smallToBigRewrites = new HashMap<>();
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();
    private fURI vid;
    private MServer server;

    public MRouter(final fURI host, final fURI vid) {
        this.vid = vid;
        this.server = new MServer(host);
        LOG.info("{{y}}router{{/y}} loaded at {{b}}%s{{/b}} [addr: {{b}}%s{{/b}}]", this.vid, this.server.getAddress());
    }

    private static Obj appendOnRead(final boolean send, final Obj base, final Obj addition) {
        return addition.isNoObj() ? base : (send ? base.append(MRel.of(addition.vid().toUri(), addition)) : base.append(addition));
    }

    public void start() {
        this.server.start();
    }

    public synchronized void close() {
        final List<fURI> list = this.spaces.values().stream().map(Space::vid).toList();
        list.forEach(this::removeSpace);
        this.spaces.clear();
        LOG.info("closing server at %s", this.server.authority().toUri());
        this.server.stop();

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
        return temp;
    }

    public void addSpace(final Space space) {
        this.spaces.entrySet().stream()
                .filter(kv -> space.pattern().matches(kv.getKey()))
                .findAny()
                .ifPresent(kv -> {
                    LOG.except("%s and %s have overlapping address spaces", space.pattern(), kv.getKey());
                });
        this.spaces.put(space.pattern(), space);
        //this.write(space.vid(), space);
    }

    @Override
    public void removeSpace(final fURI vid) {
        this.spaces.values().stream().filter(s -> vid.equals(s.vid())).findFirst().ifPresent(s -> {
            try {
                final Space space = this.spaces.remove(s.pattern());
                if (null != space) {
                    Space.Helpers.spaceCloseLog(this, space);
                    space.close();
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
        final Optional<S> space = this.spaces.entrySet().stream()
                .filter(kv -> match.basePath().matches(kv.getKey()))
                .map(Map.Entry::getValue)
                .map(s -> (S) s)
                .findAny();
        if (space.isPresent())
            return space.get();
        else if (!BOOTING)
            throw MTronException.of("no structure supports pattern %s", match.toUri(true));
        else
            return NullSpace.single();
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return NoObj.single();
        if (vid.hasAuthority() && !vid.hasAuthority(this.server.authority())) {
            return this.server.getRouters(vid.authority().extend("#")).stream().map(msc -> {
                final FutureObj<Obj> future = msc.sendRecvObj(from_(vid.toUri()));
                return future.get(5000);
            }).reduce(NoObj.single(), Obj::append);
        } else {
            final fURI local = vid.authority(null).scheme(null);
            final Space space = this.getSpace(local);
            //if (null != space.vid() && !space.vid().segments().isEmpty())
            //    LOG.trace("reading {{b}}%s{{/b}} from {{b}}%s{{/b}}", vid, space.vid());
            return MRouter.appendOnRead(vid.isBranch(), space.read(vid), this.vid.onlyMatches(vid) ? this : NoObj.single());
        }
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        fURI local;
        if (vid.hasAuthority()) {
            if (vid.hasAuthority(this.server.authority())) {
                local = vid.scheme(null).authority(null);
            } else {
                return this.server.getRouters(vid.authority().extend("#")).stream().map(msc -> {
                    final FutureObj<Obj> future = msc.sendRecvObj(start_(obj).to_(vid.toUri()));
                    return future.get(5000);
                }).reduce(NoObj.single(), Obj::append);
            }
        } else
            local = vid;
        final Space space = this.getSpace(local);
        /// TOTAL HACK -- find a more elegant solution ///
        if (obj.isNoObj() && !local.hasPattern())
            this.removeSpace(local);
        if (obj instanceof Space && !(obj instanceof Router))
            this.addSpace((Space) obj);
        /// ///////////////////////////////////////////////
        LOG.trace("writing %s to {{b}}%s{{/b}} at {{b}}%s{{X}}", obj, space.vidOrTid(), local);
        return space.write(local, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces.entrySet().stream().anyMatch(kv -> vid.matches(kv.getKey()));
    }

    @Override
    public Iterable<Space> jvm() {
        return this.spaces.values();
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
        Space.Helpers.noCloneWarning(this);
        return this;
    }

    @Override
    public Qs qs() {
        return null;
    }

    @Override
    public Obj vid(final fURI furi) {
        this.vid = furi;
        return this;
    }

    @Override
    public String toString() {
        return Router.Helpers.routerToString(this);
    }

    @Override
    public Obj clone() {
        try {
            return (Obj) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
