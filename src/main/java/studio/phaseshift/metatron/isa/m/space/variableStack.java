/*
 * metatron: a distributed virtual machine and language
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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Stack;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Inst.ARGS_FURI;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class variableStack extends AbstractSpace<Stack<Poly<?, ?>>> {

    public static final fURI STACK_SPACE_TID = M_ISA_TID.extend("space").extend("stack");
    public static final Type STACK_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(STACK_SPACE_TID)
            .constructor(instC(Tokens.M_ISA_INST_TID.dom(ALL.maybe()).rng(STACK_SPACE_TID),
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> {
                        return Memory.ARG_STACK.get();
                    })).create();

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space root;

    public Space root() {
        return this.root;
    }

    public variableStack(final fURI pattern) {
        // The arg stack is this thread's local variables, reached via Memory.argStack(), not a machine space: its
        // bindings persist through the machine's root frame, never through the space index. So it (and its root)
        // must not auto-register into the current machine — inside a frame its +/# would match /sys and /m and
        // shadow the parent's real spaces.
        super(new Stack<>(), mutableMap(uri(PATTERN), uri(pattern)), STACK_SPACE_TID, null, false);
        this.root = memSpace.unregistered(this.pattern, null);
        this.addQ(QCollection.refQ());
        this.addQ(QCollection.mintQ());
        this.addQ(QCollection.docQ());
    }

    @Override
    public Space addQ(final QProc q) {
        this.root.addQ(q);
        return super.addQ(q);
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
        for (int i = this.sjvm().size() - 2; i >= 0; i--) {
            final Poly<?, ?> layer = this.sjvm().get(i);
            if (vid.path().getFirst().equals(ARGS_FURI.toString()))
                return vid.asNode().segmentLength() == 1 ? layer : layer.at(uri(vid.pretract(1)));
            final Uri index = vid.basePath().toUri();
            final Obj o = layer.at(index);
            if (!o.isNoObj())
                return o;
        }
        return rootFrame().at(vid.toUri());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Rec rootFrame = rootFrame();
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.sjvm, rootFrame.jvm());
        // a single-segment write is a binding (replace), a nested write is a path (merge into the poly there):
        // `a -> {5,6,7}` overwrites `a`, while `a/b/c -> {4,5,6}` folds into `a`'s c
        if (1 == vid.asNode().segmentLength())
            rootFrame.jvm().put(vid.toUri(), obj);
        else
            rootFrame.at(vid.toUri(), obj, Poly.MUTABLE);
        return obj;
    }

    /** the machine-scoped persistent bindings, shared across threads and dropped with the machine */
    private static Rec rootFrame() {
        return Machine.current().memory().rootFrame();
    }

    public Obj peek() {
        return this.sjvm().isEmpty() ? noobj() : this.sjvm().getFirst();
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
