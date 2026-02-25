/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.monad;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Monad;
import studio.phaseshift.metatron.util.MTronException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMonad implements Monad {

    protected fURI tid;
    protected fURI vid;
    
    public AbstractMonad(final fURI tid, final fURI vid) {
        this.tid = tid;
        this.vid = vid;
    }

    @Override
    public fURI tid() {
        return this.tid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Monad tid(final fURI tid) {
        this.tid = tid;
        return this;
    }


    @Override
    public boolean equals(final Object other) {
        return Monad.Helpers.monadEquals(this, other);
    }

    @Override
    public int hashCode() {
        return Monad.Helpers.monadHashCode(this);
    }


    @Override
    public String toString() {
        return Monad.Helpers.monadToString(this);
    }

    @Override
    public Monad clone() {
        try {
            return (Monad) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }
}