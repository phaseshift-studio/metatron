/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.NullSpace;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static studio.phaseshift.metatron.BootLoader.BOOTING;

public class MemRouter implements Router {

    private final GraphittyLogger LOG = Graphitty.log(this);
    private static final fURI ROUTER_TID = fURI.of("/mtron/sys/router");

    private fURI vid;
    private final Map<fURI, Space> spaces = new HashMap<>();
    private final Map<fURI, fURI> smallToBigRewrites = new HashMap<>();
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();


    public MemRouter(final fURI vid) {
        this.vid = vid;
        LOG.info("%s loaded at %s", this.tid(), this.vid);
    }

    public void registerRewrite(final fURI small, final fURI big) {
        this.smallToBigRewrites.put(small, big);
        this.bigToSmallRewrites.put(big, small);
    }

    @Override
    public fURI rewrite(final fURI furi, final boolean big) {
        return (big ? this.smallToBigRewrites.getOrDefault(furi.basePath(), furi) : this.bigToSmallRewrites.getOrDefault(furi.basePath(), furi)).coefficient(furi.coefficient()).queryMap(furi.queryMap());
    }

    public void registerSpace(final Space space) {
        this.spaces.entrySet().stream()
                .filter(kv -> space.pattern().matches(kv.getKey()))
                .findAny()
                .ifPresent(kv -> {
                    LOG.except("%s and %s have overlapping address spaces", space.pattern(), kv.getKey());
                });
        this.spaces.put(space.pattern(), space);
    }

    public <S extends Space> S getSpace(final fURI match) {
        if (match.matches(fURI.NONE))
            return NullSpace.single();
        //     final fURI mvid = this.smallToBigRewrites.getOrDefault(vid,vid);
        Optional<S> space = this.spaces.entrySet().stream()
                .filter(kv -> match.basePath().matches(kv.getKey()))
                .findAny()
                .map(Map.Entry::getValue).map(s -> (S) s);
        if (space.isPresent())
            return space.get();
        else if (!BOOTING)
            throw LOG.except("no structure supports pattern %s", match.toUri(true));
        else
            return NullSpace.single();
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.equals(this.vid))
            return this;
        final Space space = this.getSpace(vid);
        //LOG.trace("reading %s from %s", vid, space.vid());
        return space.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Space space = this.getSpace(vid);
        LOG.trace("writing %s to %s", obj, space.vidOrTid());
        return space.write(vid, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces.entrySet().stream().anyMatch(kv -> vid.matches(kv.getKey()));
    }

    @Override
    public Map<fURI, Space> value() {
        return this.spaces;
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
    public <O extends Obj> O clone(Object value, fURI tid, fURI vid) {
        return (O) this;
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
