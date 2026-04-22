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

package studio.phaseshift.metatron.isa.m.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Stack;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Inst.ARGS_FURI;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class stackSpace extends AbstractSpace<Stack<Poly<?, ?>>> {

    public static final fURI STACK_SPACE_TID = M_ISA_TID.extend("space").extend("stack");
    public static final Type STACK_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(STACK_SPACE_TID)
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(STACK_SPACE_TID),  // constructor
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> {
                        final Space space = new stackSpace(inst.arg(0).asRec().at(PATTERN).uriValue());
                        Router.global().addSpace(space);
                        return space;
                    })).create();

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space root;

    public stackSpace(final fURI pattern) {
        super(new Stack<>(), mutableMap(uri(PATTERN), uri(pattern)), STACK_SPACE_TID, null);
        this.root = memSpace.of(this.pattern, null);
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

    public void clear() {
        this.sjvm().clear();
    }

    @Override
    public Obj read(final fURI vid) {
        //if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        //int offset = vid.toString().matches("\\d+") ? 2 : 2; // ensure lst args are not the top frame
        for (int i = this.sjvm().size() - 2; i >= 0; i--) { // the top frame is the current arg being processed, thus, offset is set to 2
            final Poly<?, ?> layer = this.sjvm().get(i);
            if (vid.path().getFirst().equals(ARGS_FURI.toString()))
                return vid.asNode().segmentLength() == 1 ? layer : layer.at(uri(vid.pretract(1)));
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
            this.sjvm().getFirst().<Poly>as().at(vid.toUri(), obj);
        // else
        this.root.write(vid, obj);
        return obj;
    }

    public Obj peek() {
        return this.sjvm().isEmpty() ? noobj() : this.sjvm().getFirst();
    }

   /* public Obj peekAll() {
        return lst(this.sjvm().stream().map(Obj::<Obj>as).toList());
    }*/

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
