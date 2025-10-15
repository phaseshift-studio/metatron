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

package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;


public class MemSpace extends MSpace<Map<fURI,Obj>> implements Space {

    public static final fURI MEMSPACE_TID = MTRON_SPACE_TID.extend("mem");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    

    public MemSpace(final fURI pattern, final fURI vid) {
        super(new HashMap<>(),pattern, MEMSPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
       //System.out.println("%s".formatted(this.structure.keySet()));
        return this.qs().processPreRead(vid, vid).orElseGet(() -> Helpers.resolveRead(this, vid, (key) -> {
            if (key.equals(fURI.ALL))
                return this.structure;
            else {
                if (key.hasPattern()) {
                    return this.structure.entrySet().stream().filter(kv -> kv.getKey().matches(key.asNode())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Obj::append, LinkedHashMap::new));
                } else {

                    final Obj value = this.structure.get(key);
                    return null == value ? Map.of() : Map.of(key, value);
                }
            }
        }));
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Obj ret = this.qs().processPreWrite(vid, vid, obj).orElse(null);
        if (null != ret)
            return ret;
        Space.Helpers.resolveWrite(this,vid.basePath(), obj, (key, value) -> {
            final Obj current = this.read(key);
            if(value.isNoObj() && current instanceof Space) {
                try {
                    ((Space) current).close();
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
            this.structure.put(key,value);
            //LOG.trace("raw write of %s to %s {{g}}@{{b}}%s{{X}}", value, this, key);
           /* if (value.isNoObj()) {
                if (key.hasPattern()) // delete all existing values that match key pattern
                    this.structure.entrySet().stream().filter(kv -> kv.getKey().matches(key)).forEach(kv -> this.structure.remove(kv.getKey()));
                else
                    this.structure.remove(key); // delete existing value that matches key pattern
            } else {
                if (key.hasPattern()) // overwrite all existing values that match key pattern
                    this.structure.entrySet().stream().filter(kv -> kv.getKey().matches(key)).forEach(kv -> {
                        if (key.isNode())
                            this.structure.put(kv.getKey(), value);
                        else {
                            this.structure.compute(key.asNode(), (k, current) -> null == current ? value : current.append(value));
                        }
                    });
                else { // overwwrite existing value that matches key pattern
                    if (key.isNode())
                        this.structure.put(key, value);
                    else
                        this.structure.compute(key.asNode(), (k, current) -> null == current ? value : current.append(value));
                }
            }*/
        });
        this.qs().processPostWrite(vid, vid, obj);
        return this.qs().processQlessWrite(vid, vid, obj).orElse(obj);
    }

    @Override
    public long count() {
        return this.structure.size();
    }

    @Override
    public Iterator<Obj> iterator() {
        return this.structure.entrySet().stream().map(kv -> MRel.of(kv.getKey().toUri(), kv.getValue())).map(r -> (Obj) r).iterator();
    }
}
