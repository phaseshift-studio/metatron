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

package studio.phaseshift.metatron.lang;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public abstract class MSpace<SJVM> extends MRec implements Space {

    protected final fURI pattern;
    protected SJVM sjvm;
    protected GraphittyLogger LOG;

    public MSpace(final SJVM sjvm, final Map<Obj, Obj> jvm, final fURI pattern, final fURI tid, final fURI vid) {
        super(new HashMap<>(jvm), tid, vid);
        this.sjvm = sjvm;
        this.pattern = pattern;
        this.jvm().put(uri(Tokens.STATUS), uri(Tokens.ACTIVE));
        LOG = Graphitty.log(this);
    }

    @Override
    public void onPut(final fURI key, final Obj value) {
      /*  if (key.matches(f("q"))) {
            value.<Lst>as().elements().forEach(q -> {
                this.qs.add(q);
            });
        } else*/
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
    public Rec vid(final fURI vid) {
        if (null != vid) {
            this.vid = vid;
            Router.global().addSpace(this);
            Router.writeToSpace(vid, this);
            // LOG.trace("registering: %s", this);
            //this.qs.register(new PubSubQ());
        }
        return super.vid(vid);
    }

    @Override
    public Rec clone() {
        //Space.Helper.noCloneWarning(this);
        return this;
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
}
