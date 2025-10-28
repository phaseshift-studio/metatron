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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.furi.Qs;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.furi.fURI.f;

public abstract class MSpace<J> implements Space {

    protected final GraphittyLogger LOG;
    protected final J jvm;
    protected final fURI pattern;
    protected final fURI tid;
    protected final fURI vid;
    protected final Qs qs;

    public MSpace(final J jvm, final fURI pattern, final fURI tid, final fURI vid) {
        LOG = Graphitty.log(this);
        this.jvm = jvm;
        this.pattern = pattern;
        this.tid = tid.big();
        this.vid = vid;
        if (!(this instanceof StackSpace)) {
            if (null != vid)
                this.qs = new Qs(f(""));
            else this.qs = new Qs(f(""));
        } else {
            this.qs = null;
        }
    }

    @Override
    public Qs qs() {
        return this.qs;
    }

    @Override
    public J jvm() {
        return this.jvm;
    }

    @Override
    public fURI pattern() {
        return this.pattern;
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
    public String toString() {
        return Helper.spaceToString(this);
    }

    @Override
    public int hashCode() {
        return Helper.spaceHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Helper.spaceEquals(this, other);
    }

    @Override
    public void close() {

    }

    @Override
    public Space clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Obj clone() {
        try {
            return (Obj) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
