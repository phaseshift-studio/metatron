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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.f;

public abstract class AbstractSpace<SJVM> extends MRec implements Space {

    protected final fURI pattern;
    protected SJVM sjvm;
    protected GraphittyLogger LOG;

    public AbstractSpace(final SJVM sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(config, tid, vid);
        ServiceMetadata.Helper.verifyClass(this.getClass(), tid);
        this.sjvm = sjvm;
        this.pattern = this.at(PATTERN).uriValue();
        LOG = Graphitty.log(this);
        if (Router.loaded() && !this.pattern.equals(f("+/#")) && !(this instanceof Router))
            Router.global().addSpace(this);
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }

    @Override
    public SJVM sjvm() {
        return this.sjvm;
    }

    /*@Override
    public Rec vid(final fURI vid) {
        if (null != vid) {
            this.vid = vid;
            Router.global().addSpace(this);
            Router.writeToSpace(vid, this);
            // LOG.trace("registering: %s", this);
            //this.qs.register(new PubSubQ());
            return this;
        } else
            return super.vid(vid);
    }*/

    /*@Override
    public Rec tid(final fURI tid) {
        Space.Helper.noCloneWarning(this);
        return this;
    }

    @Override
    public Rec clone() {
        Space.Helper.noCloneWarning(this);
        return this;
    }*/

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
}
