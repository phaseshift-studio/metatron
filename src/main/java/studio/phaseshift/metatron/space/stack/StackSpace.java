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

package studio.phaseshift.metatron.space.stack;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Poly;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.kv.KVSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedList;

import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;

public class StackSpace extends MSpace<LinkedList<KVSpace>> {

    public static final fURI STACKSPACE_TID = MTRON_SPACE_TID.extend("stack");
    public static final String ARG_PREFIX = "";

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space root;

    public StackSpace(final fURI pattern, final fURI vid) {
        super(new LinkedList<>(), pattern, STACKSPACE_TID, vid);
        this.root = new KVSpace(this.pattern, null);
    }

    @Override
    public void close() {
        try {
            this.root.close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Obj read(final fURI vid) {
        LOG.trace("reading {{b}}%s{{/b}} in %s [{{y}}root{{/y}}: %s]", vid, this.jvm, this.root.jvm());
        // if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        boolean isArg = vid.toString().matches("a\\d+"); // skip first encounter of list arg variable as it's a variable to grab the variable
        for (final KVSpace layer : this.jvm()) {
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
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.jvm, this.root.jvm());
        if(obj.isUri() && obj.uriValue().equals(vid))
            return obj;
        if (!this.jvm().isEmpty())
            this.jvm().get(0).write(vid, obj);
        this.root.write(vid, obj);
        return obj;
    }

    public boolean pop() {
        final KVSpace frameSpace = this.jvm().pop();
        if (null == frameSpace)
            return false;
        LOG.trace("popped frame {{_&r}}off{{/r&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frameSpace.jvm(), this.jvm().size());
        return true;
    }

    public void push(final Poly frame) {
        final KVSpace frameSpace = new KVSpace(pattern, null);
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
        this.jvm().push(frameSpace);
        LOG.trace("pushed frame {{_&g}}on{{/g&/_}} stack: %s [{{y}}depth{{/y}}: %d]", frameSpace.jvm(), this.jvm().size());
    }
}
