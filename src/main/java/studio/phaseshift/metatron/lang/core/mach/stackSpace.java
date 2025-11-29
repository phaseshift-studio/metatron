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

package studio.phaseshift.metatron.lang.core.mach;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Poly;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Stack;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;

public class stackSpace extends MSpace<Stack<Poly>> {

    public static final fURI STACK_TID = sysInstSet.SPACE_TID.extend("stack");
    public static final String ARG_PREFIX = "";

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space root;

    public stackSpace(final fURI pattern) {
        super(new Stack<>(), mutableMap(uri(Tokens.PATTERN), uri(pattern)), pattern, STACK_TID, fURI.fnull);
        this.root = kvSpace.of(this.pattern, fURI.fnull);
    }

    @Override
    public void close() {
        try {
            this.root.close();
            super.close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Obj read(final fURI vid) {
        //if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        //int offset = vid.toString().matches("\\d+") ? 2 : 2; // ensure lst args are not the top frame
        for (int i = this.sjvm().size() - 2; i >= 0; i--) { // the top frame is the current arg being processed, thus, offset is set to 2
            final Poly<?, ?> layer = this.sjvm().get(i);
            final Uri index = vid.basePath().toUri();
            final Obj o = layer.at(index);
            if (!o.isNoObj())
                return o;
        }
        return this.root.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.sjvm, this.root.jvm());
        // if (obj.isUri() && obj.uriValue().equals(vid))
        //    return obj;
        if (!this.sjvm().isEmpty())
            this.sjvm().get(0).<Poly>as().at(vid.toUri(), obj);
        // else
        this.root.write(vid, obj);
        return obj;
    }

    public boolean pop() {
        final Poly frame = this.sjvm().pop();
        LOG.trace("popped frame {{_&r}}off{{/r&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frame, this.sjvm().size());
        return true;
    }

    public void push(final Poly frame) {
        this.sjvm().push(frame);
        LOG.trace("pushed frame {{_&g}}on{{/g&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frame, this.sjvm().size());
    }
}
