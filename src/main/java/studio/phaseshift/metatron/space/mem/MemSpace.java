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
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.MTRON_TID;


public class MemSpace extends MSpace implements Space {

    public static final fURI MEMSPACE_TID = MTRON_SPACE_TID.extend("mem");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    final Map<fURI, Obj> store = new HashMap<>();

    public MemSpace(final fURI pattern, final fURI vid) {
        super(pattern, MEMSPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helpers.resolveRead(this, vid, (key) -> {
            if (key.equals(fURI.ANY))
                return this.store;
            else
                return Map.of(key, this.store.get(key));
        });
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        Space.Helpers.resolveWrite(vid, vid.retractPattern(), obj, (key, value) -> {
            //LOG.trace("raw write of %s to %s {{g}}@{{b}}%s{{X}}", value, this, key);
            if (value.isNoObj())
                this.store.remove(key);
            else
                this.store.put(key, value);
        });
        return obj;
    }

    @Override
    public long count() {
        return this.store.size();
    }

    @Override
    public Iterator<Obj> iterator() {
        return this.store.entrySet().stream().map(kv -> MRel.of(kv.getKey().toUri(), kv.getValue())).map(r -> (Obj) r).iterator();
    }
}
