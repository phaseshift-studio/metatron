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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.MStats;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.Stats;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public abstract class AbstractSpace<SJVM> extends MRec implements Space {

    protected final Map<Uri, Uri> routes = new LinkedHashMap<>();
    protected final fURI pattern;
    protected SJVM sjvm;
    protected Stats ioStats;
    protected GraphittyLogger LOG;

    public AbstractSpace(final SJVM sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(config, tid, vid);
        InstSet.JREService.Helper.verifyClass(this.getClass(), tid);
        this.sjvm = sjvm;
        this.pattern = this.at(PATTERN).uriValue();
        this.ioStats = new MStats();
        final Obj temp = config.getOrDefault(uri(ROUTE), rec());
        if (temp.isRec())
            temp.asRec().elements().forEach(kv -> this.routes.put(kv.first().asUri(), kv.second().asUri()));
        else
            this.routes.put(temp.asRel().first().asUri(), temp.asRel().second().asUri());
        LOG = Graphitty.log(this);
        if (Router.loaded() && !this.pattern.equals(STACK_PATTERN) && !(this instanceof Router))
            Router.global().addSpace(this);
    }

    @Override
    public Map<Uri, Uri> routes() {
        return this.routes;
    }

    @Override
    public Stats stats() {
        return this.ioStats;
    }


    @Override
    public fURI rewrite(final fURI furi, final boolean big) {
        return big ? Space.Helper.routeFromSpace(furi, this.routes()) : Space.Helper.routeToSpace(furi, this.routes());
    }

    @Override
    public Obj parent() {
        return null == this.parent ? this.at(uri(SUPER)).orElse(Router.global()) : this.parent;
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
    public Space tid(final fURI tid) {
        //Space.Helper.noCloneWarning(this);
        return this;
    }

    @Override
    public Space clone() {
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
