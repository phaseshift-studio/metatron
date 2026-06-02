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

package studio.phaseshift.metatron.isa.mach.type.monad;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.util.MTronException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractPCMonad implements PCMonad {

    protected fURI tid;
    protected fURI vid;

    public AbstractPCMonad(final fURI tid, final fURI vid) {
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
    public PCMonad tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    @Override
    public PCMonad vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    public PCMonad jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    public boolean equals(final Object other) {
        return PCMonad.Helpers.monadEquals(this, other);
    }

    @Override
    public int hashCode() {
        return PCMonad.Helpers.monadHashCode(this);
    }


    @Override
    public String toString() {
        return PCMonad.Helpers.monadToString(this);
    }

    @Override
    public PCMonad clone() {
        try {
            return (PCMonad) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }
}