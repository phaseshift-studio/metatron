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

package studio.phaseshift.metatron.struct.mem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.struct.Router;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;

public class MemRouter implements Router {

    private static final Logger LOG = LoggerFactory.getLogger(MemRouter.class);
    public static final fURI MEMROUTER_TID = fURI.of("router:/mtron/mem");

    private fURI vid;
    private final Map<fURI, Struct> routes = new HashMap<>();


    public MemRouter(final fURI vid) {
        this.vid = vid;
        LOG.info(Graphitty.parse("%s loaded at %s".formatted(this.tid().toUri(true), this.vid.toUri(true))));
    }

    public void registerStruct(final Struct struct) {
        this.routes.entrySet().stream()
                .filter(kv -> struct.vid().matches(kv.getKey()))
                .findAny()
                .ifPresent(x -> {
                    throw new IllegalStateException("existing pattern for: " + x.getValue());
                });
        this.routes.put(struct.vid(), struct);
    }

    public Struct getStruct(final fURI pattern) {
        return this.routes.entrySet().stream()
                .filter(kv -> pattern.matches(kv.getKey()))
                .findAny()
                .map(Map.Entry::getValue)
                .orElseThrow(() -> new IllegalStateException("unknown"));
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.equals(this.vid))
            return new SObj.Rec((Map) this.routes.entrySet().stream()
                    .map(kv -> Map.of(new SObj.Uri(kv.getKey()), kv.getValue()))
                    .reduce(new HashMap<>(), (a, b) -> {
                        a.putAll(b);
                        return a;
                    }));
        return this.getStruct(vid).read(vid);

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.getStruct(vid).write(vid, obj);
    }

    @Override
    public Map<Obj, Obj> value() {
        return Map.of();
    }

    @Override
    public BObj.Rec apply(Obj other) {
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
