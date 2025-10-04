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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.NullSpace;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;

public class MemRouter implements Router {

    private final GraphittyLogger LOG = Graphitty.log(this);
    private static final fURI ROUTER_TID = fURI.of("/mtron/sys/router");

    private fURI vid;
    private final Map<fURI, Space> spaces = new ConcurrentHashMap<>();
    private final Map<fURI, fURI> smallToBigRewrites = new HashMap<>();
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();
    private final Space localSpace;


    public MemRouter(final fURI vid) {
        this.vid = vid;
        this.localSpace = new MemSpace(this.pattern(), this.vid);
        this.spaces.put(this.vid, localSpace);
        LOG.info("%s loaded at %s", this.tid(), this.vid);
    }

    public void registerRewrite(final fURI small, final fURI big) {
        this.smallToBigRewrites.put(small, big);
        this.bigToSmallRewrites.put(big, small);
        this.localSpace.write(this.vid.extend("prefix").extend(small), big.toUri());
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
        this.write(space.vid(), space);
    }

    @Override
    public void removeSpace(final fURI vid) {
        this.spaces.values().stream().filter(s -> vid.equals(s.vid())).findFirst().ifPresent(s -> {
            try {
                final Space space = this.spaces.remove(s.pattern());
                if (null != space) {
                    space.close();
                    LOG.trace("closing space %s", s);
                }
            } catch (final Exception e) {
                LOG.error(e);
            }
        });
    }

    public <S extends Space> S getSpace(final fURI match) {
        if (match.matches(fURI.NONE))
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
            throw LOG.except("no structure supports pattern %s", match.toUri(true));
        else
            return NullSpace.single();
    }

    private static final Set<fURI> READ_AS_NOOBJ = Set.of(fURI.ALL.all(), fURI.ALL.maybe(), fURI.ALL);

    @Override
    public Obj read(final fURI vid) {
        if (vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return NoObj.single();
        final Space space = this.getSpace(vid);
        //LOG.trace("reading %s from %s", vid, space.vid());
        return ObjUtil.appendOnRead(vid.isBranch(), space.read(vid), this.vid.onlyMatches(vid) ? this : NoObj.single());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Space space = this.getSpace(vid);
        LOG.trace("writing %s to %s at {{b}}%s{{X}}", obj, space.vidOrTid(), vid);
        return space.write(vid, obj);
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces.entrySet().stream().anyMatch(kv -> vid.matches(kv.getKey()));
    }

    @Override
    public Iterable<Space> value() {
        return this.spaces.values();
    }


    @Override
    public MemRouter apply(final Obj other) {
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
    public Router clone(Object value, fURI tid, fURI vid) {
        return this;
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
}
