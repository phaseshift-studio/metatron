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
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.Map;

public abstract class MSpace<SJVM> extends MRec implements Space {

    protected final fURI pattern;
    protected final Qs qs;
    protected SJVM sjvm;
    protected GraphittyLogger LOG;

    public MSpace(final SJVM sjvm, final Map<Obj, Obj> jvm, final fURI pattern, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.sjvm = sjvm;
        this.pattern = pattern;
        this.qs = new Qs();
        LOG = Graphitty.log(this);
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
    public SJVM sjvm() {
        return this.sjvm;
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
    public Rec vid(final fURI vid) {
        if (null != vid) {
            this.vid = vid;
            Router.global().addSpace(this);
            Router.writeToSpace(vid,this);
            LOG.trace("registering: %s", this);
            this.qs.register(new PubSubQ(this));
        }
        return super.vid(vid);
    }

    @Override
    public Rec clone() {
        return this;
    }

    /*@Override
    public Rec clone(final Object object, final fURI tid, final fURI vid) {
        return this;
    }*/
}
