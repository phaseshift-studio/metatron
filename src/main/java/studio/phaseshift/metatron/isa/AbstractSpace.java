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

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.MStats;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.Stats;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public abstract class AbstractSpace<SJVM> extends MRec implements Space {

    protected final fURI pattern;
    protected SJVM sjvm;
    protected Stats ioStats;
    protected GraphittyLogger LOG;

    public AbstractSpace(final SJVM sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(config, tid, vid);
        InstSet.JREService.Helper.verifyClass(this.getClass(), vid);
        this.sjvm = sjvm;
        this.pattern = this.at(PATTERN).uriValue();
        /// //////////// BAD
        //final Lst qprocs = this.at(uri(QPROC)).orElse(lst());
        //this.jvm().remove(uri(QPROC));
        //qprocs.elements().forEach(q -> this.addQ(QProc.Helper.wrap(q.asRec())));
        /// //////////// BAD
        this.ioStats = new MStats();
        LOG = Graphitty.log(this);
        // Don't auto-register InstSets - they're registered via importInstSetStream AFTER full construction
        // This ensures docq and other post-super() setup is complete before registration
        if (Router.loaded() && !this.pattern.equals(STACK_PATTERN) && !(this instanceof Router) && !(this instanceof InstSet))
            Router.global().addSpace(this);
    }

    @Override
    public Obj read(final fURI vid) {
        // LOG.warn("reading %s => %s", vid, Space.Helper.routeFromSpace(vid, this.routes()));
        QProc.Helper.checkSpaceQProcs(this, vid);
        return QProc.Helper.processPreRead(this.qs(), vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid, directReader());
            return QProc.Helper.processPostRead(this.qs(), vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        // LOG.warn("writing %s => %s", vid, Space.Helper.routeFromSpace(vid, this.routes()));
        // Root type enforcement: if this space declares a root type constraint and the write
        // targets a document root (1 or 2 non-branch path segments), reject non-conforming values.
        //   1-segment: e.g. mongo:ddd -> [...] (auto-generated document ID)
        //   2-segment: e.g. mongo:col/docId -> [...] (explicit document write)
        // Deletes (noobj) are always permitted. Sub-field writes (3+ segments) bypass this check.
        if (!obj.isNoObj()) {
            final Obj rootConstraint = this.at(uri(ROOT)).orElse(null);
            if (rootConstraint != null && !rootConstraint.isNoObj()
                    && !vid.isBranch()
                    && vid.segments().size() >= 1 && vid.segments().size() <= 2
                    && !obj.test(rootConstraint.as())) {
                return fail("space %s requires %s at root; got %s", this.vid(), rootConstraint, obj.type());
            }
        }
        QProc.Helper.checkSpaceQProcs(this, vid);
        return QProc.Helper.processPreWrite(this.qs(), vid, obj)
                .orElseGet(() -> QProc.Helper.processQlessWrite(this.qs(), vid, obj).orElseGet(() -> {
                    Space.Helper.resolveWrite(LOG, this, vid, obj, this.directWriter(), this.directReader());
                    return QProc.Helper.processPostWrite(this.qs(), vid, obj).orElse(obj);
                }));
    }

    @Override
    public Map<Uri, Uri> routes() {
        return this.at(ROUTE).orElse(rec0()).jvmAs();
    }

    @Override
    public Stats stats() {
        return this.ioStats;
    }
    
    @Override
    public fURI redirect(final fURI furi, final boolean external) {
        return external ? Space.Helper.routeFromSpace(furi, this.routes()) : Space.Helper.routeToSpace(furi, this.routes());
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
