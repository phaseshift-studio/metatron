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

package studio.phaseshift.metatron.lang.core.mach;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Poly;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedList;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class stackSpace extends MSpace<LinkedList<kvSpace>> {

    public static final fURI STACK_TID = sysInstSet.SPACE_TID.extend("stack");
    public static final String ARG_PREFIX = "";

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space root;

    public stackSpace(final fURI pattern) {
        super(new LinkedList<>(), Map.of(uri("pattern"), uri(pattern)), pattern, STACK_TID, fURI.NULL);
        this.root = kvSpace.of(this.pattern, fURI.NULL);
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
        LOG.trace("reading {{b}}%s{{/b}} in %s [{{y}}root{{/y}}: %s]", vid, this.sjvm, this.root.jvm());
        // if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        boolean isArg = vid.toString().matches("a\\d+"); // skip first encounter of list arg variable as it's a variable to grab the variable
        for (final kvSpace layer : this.sjvm()) {
            final Obj o = layer.read(vid.basePath());
            if (!o.isNoObj()) {
                if (isArg) isArg = false;
                else return o;
            }
        }
        return this.root.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.sjvm, this.root.jvm());
        if (obj.isUri() && obj.uriValue().equals(vid))
            return obj;
        if (!this.sjvm().isEmpty())
            this.sjvm().get(0).write(vid, obj);
        this.root.write(vid, obj);
        return obj;
    }

    public boolean pop() {
        final kvSpace frameSpace = this.sjvm().pop();
        if (null == frameSpace)
            return false;
        LOG.trace("popped frame {{_&r}}off{{/r&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frameSpace.jvm(), this.sjvm().size());
        return true;
    }

    public void push(final Poly frame) {
        final kvSpace frameSpace = new kvSpace(pattern, null);
        if (frame.isRec()) {
            frame.recValue().forEach((key, value) -> {
                frameSpace.write(key.uriValue(), value);
                Router.global().write(key.uriValue(), value);
            });
        } else {
            for (int i = 0; i < frame.lstValue().size(); i++) {
                frameSpace.write(fURI.of(ARG_PREFIX + i), frame.lstValue().get(i));
                Router.global().write(fURI.of(ARG_PREFIX + i), frame.lstValue().get(i));
            }
        }
        this.sjvm().push(frameSpace);
        LOG.trace("pushed frame {{_&g}}on{{/g&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frameSpace.jvm(), this.sjvm().size());
    }
}
