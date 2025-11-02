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

package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.furi.Qs;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObj;
import studio.phaseshift.metatron.space.stack.StackSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

public abstract class MSpace<J> extends MObj implements Space {

    protected final GraphittyLogger LOG;
    protected final fURI pattern;
    protected final Qs qs;

    public MSpace(final J jvm, final fURI pattern, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        LOG = Graphitty.log(this);
        this.pattern = pattern;
        this.qs = new Qs();
    }

    @Override
    public Qs qs() {
        return this.qs;
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }

    @Override
    public J jvm() {
        return (J) this.jvm;
    }

    @Override
    public String toString() {
        return Space.Helper.spaceToString(this);
    }

    @Override
    public int hashCode() {
        return Space.Helper.spaceHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Space.Helper.spaceEquals(this, other);
    }

    @Override
    public Obj vid(final fURI vid) {
        if (null != vid) {
            Router.writeToSpace(vid.extend("pattern"), this.pattern().toUri());
            Router.global().addSpace(this.pattern, this);
            LOG.trace("registering: %s",this);
            this.qs.register(new PubSubQ(this));
        }
        return super.vid(vid);
    }

    @Override
    public Space clone() {
        return this;
    }
}
