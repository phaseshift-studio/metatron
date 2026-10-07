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

    /**
     * The persistent root frame — the one binding store that survives frame pops <em>and</em> thread
     * boundaries. It is static (shared across every per-thread {@link #ARG_STACK} instance) because a
     * relative write is a machine-scoped binding, not a thread-local arg; the arg frames in
     * {@code sjvm} are the only part of this stack that is per-thread.
     */
    private static final Rec rootFrame = rec(mutableMap());

    public Space root() {
        return this.root;
    }

    public variableStack(final fURI pattern) {
        super(new Stack<>(), mutableMap(uri(PATTERN), uri(pattern)), STACK_SPACE_TID, null);
        this.root = memSpace.of(this.pattern, null);
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
        return this.rootFrame.at(vid.toUri());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.sjvm, this.rootFrame.jvm());
        this.rootFrame.at(vid.toUri(), obj, Poly.MUTABLE);
        return obj;
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
