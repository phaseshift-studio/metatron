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
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class MemSpace extends MSpace implements Space {

    public static final fURI MEMSPACE_TID = MTRON_SPACE_TID.extend("mem");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    final Map<fURI, Obj> pathStore = new HashMap<>();

    public MemSpace(final fURI pattern, final fURI vid) {
        super(pattern, MEMSPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helpers.resolveRead(this, vid, (key) -> {
            if (key.equals(fURI.ALL_WILD))
                return this.pathStore;
            else {
                if (key.hasPattern()) {
                    return this.pathStore.entrySet().stream().filter(kv -> kv.getKey().matches(key.asNode())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
                } else {
                    final Obj value = this.pathStore.get(key.asNode());
                    return null == value ? Map.of() : Map.of(key.asNode(), value);
                }
            }
        });
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        Space.Helpers.resolveWrite(vid, obj, (key, value) -> {
            //LOG.trace("raw write of %s to %s {{g}}@{{b}}%s{{X}}", value, this, key);
            if (value.isNoObj()) {
                if (key.hasPattern()) // delete all existing values that match key pattern
                    this.pathStore.entrySet().stream().filter(kv -> kv.getKey().matches(key)).forEach(kv -> this.pathStore.remove(kv.getKey()));
                else
                    this.pathStore.remove(key); // delete existing value that matches key pattern
            } else {
                if (key.hasPattern()) // overwrite all existing values that match key pattern
                    this.pathStore.entrySet().stream().filter(kv -> kv.getKey().matches(key)).forEach(kv -> {
                        if (key.isNode())
                            this.pathStore.put(kv.getKey(), value);
                        else {
                            this.pathStore.compute(key.asNode(), (k, current) -> null == current ? value : current.append(value));
                        }
                    });
                else { // overwwrite existing value that matches key pattern
                    if (key.isNode())
                        this.pathStore.put(key, value);
                    else
                        this.pathStore.compute(key.asNode(), (k, current) -> null == current ? value : current.append(value));
                }
            }
        });
        return obj;
    }

    @Override
    public long count() {
        return this.pathStore.size();
    }

    @Override
    public Iterator<Obj> iterator() {
        return this.pathStore.entrySet().stream().map(kv -> MRel.of(kv.getKey().toUri(), kv.getValue())).map(r -> (Obj) r).iterator();
    }
}
