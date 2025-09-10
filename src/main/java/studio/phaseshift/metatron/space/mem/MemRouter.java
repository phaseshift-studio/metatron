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
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;

public class MemRouter implements Router {

    private static final GraphittyLogger LOG = Graphitty.log(MemRouter.class);
    public static final fURI MEMROUTER_TID = fURI.of("router:/mtron/mem");

    private fURI vid;
    private final Map<fURI, Space> routes = new HashMap<>();


    public MemRouter(final fURI vid) {
        this.vid = vid;
        LOG.info("%s loaded at %s", this.tid().toUri(true), this.vid.toUri(true));
    }

    public void registerStruct(final Space space) {
        this.routes.entrySet().stream()
                .filter(kv -> space.pattern().matches(kv.getKey()))
                .findAny()
                .ifPresent(kv -> {
                    LOG.except("%s and %s have overlapping address spaces", space.pattern(), kv.getKey());
                });
        this.routes.put(space.pattern(), space);
    }

    public Space getStruct(final fURI pattern) {
        return this.routes.entrySet().stream()
                .filter(kv -> pattern.matches(kv.getKey()))
                .findAny()
                .map(Map.Entry::getValue)
                .orElseThrow(() -> LOG.except("no structure supports pattern %s", pattern.toUri(true)));
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.equals(this.vid))
            return this;
        final Space space = this.getStruct(vid);
        LOG.trace("reading %s at %s", space, vid);
        return space.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Space space = this.getStruct(vid);
        LOG.trace("writing to %s at %s", space, vid);
        return space.write(vid, obj);
    }

    @Override
    public boolean hasStruct(final fURI vid) {
        return this.routes.entrySet().stream()
                .filter(kv -> vid.matches(kv.getKey()))
                .findAny().isPresent();
    }

    @Override
    public Map<Obj, Obj> value() {
        return Map.of();
    }

    @Override
    public MemRouter apply(Obj other) {
        return null;
    }

    @Override
    public fURI tid() {
        return MEMROUTER_TID;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj vid(final fURI furi) {
        this.vid = furi;
        return this;
    }

    @Override
    public MemRouter clone() {
        return this;
    }

    @Override
    public <O extends Obj> O clone(Object value) {
        return (O) this;
    }
}
