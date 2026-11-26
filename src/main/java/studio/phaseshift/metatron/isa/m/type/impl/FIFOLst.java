/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static studio.phaseshift.metatron.Tokens.LST_TID;

/**
 * FIFOLst — a lst that keeps a bounded history (Marko, 2026-11-25): an {@link #offer} past the cap drops the eldest
 * members that are not pinned, so the lst holds every pinned member and the newest {@code cap} of the rest, in the order
 * they arrived. The backing list is the {@link CopyOnWriteArrayList} handed to {@link MLst}, so a reader walks a
 * snapshot while offers go on.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FIFOLst extends MLst {

    private final int cap;
    /**
     * the members never dropped, by identity: two equal members are two arrivals, and only the one offered pinned stays
     */
    private final Set<Obj> pinned = Collections.newSetFromMap(new IdentityHashMap<>());

    public FIFOLst(final int cap) {
        super(new CopyOnWriteArrayList<>(), LST_TID, null);
        this.cap = cap;
    }

    /**
     * Append {@code obj}, and drop the eldest unpinned members past the cap.
     *
     * @param pinned whether {@code obj} stays for good, whatever arrives after it
     * @return the members dropped, eldest first
     */
    public synchronized List<Obj> offer(final Obj obj, final boolean pinned) {
        if (pinned)
            this.pinned.add(obj);
        this.jvm().add(obj);
        final List<Obj> dropped = new ArrayList<>();
        int unpinned = this.jvm().size() - this.pinned.size();
        for (int i = 0; unpinned > this.cap && i < this.jvm().size(); ) {
            if (this.pinned.contains(this.jvm().get(i)))
                i++;
            else {
                dropped.add(this.jvm().remove(i));
                unpinned--;
            }
        }
        return dropped;
    }
}
