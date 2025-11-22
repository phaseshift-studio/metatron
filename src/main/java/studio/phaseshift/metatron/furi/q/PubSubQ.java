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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.mach.type.Machine;
import studio.phaseshift.metatron.lang.core.mach.type.impl.MMachine;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Call;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Common.mutableMap;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class PubSubQ extends BaseQ {

    public static final fURI SUBQ_TID = Q_TID.extend("subq");
    public static final fURI SUBSCRIPTION_TID = SUBQ_TID.extend("sub");
    // <source,pattern,callback>
    protected final Obj subscriptions = MObjs.empty();
    protected final Queue<Machine> mail = new LinkedList<>();

    public static final Type SUBQ_TYPE = T(SUBQ_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(SUBQ_TID), lst(isa_(rec()).tryToInst()), (lhs, inst) -> {
        final Q q = new PubSubQ();
        return q;
    }));

    public PubSubQ() {
        super(mutableMap(), f("sub"), SUBQ_TID);
        this.onRead = new PubSubQ.OnRead();
        this.onWrite = new PubSubQ.OnWrite();
    }

    public PubSubQ clone() {
        return (PubSubQ) super.clone();
    }

    public PubSubQ clone(final Object jvm, final fURI tid, final fURI vid) {
        final PubSubQ clone = this.clone();
        clone.jvm = jvm;
        clone.tid = tid;
        clone.vid = vid;
        return clone;
    }

    public static class Subscription extends MObj {

        public Subscription(final fURI source, final fURI target, final Call call) {
            super(Triplet.with(source, target, call), SUBSCRIPTION_TID, fURI.NULL);
        }

        public Triplet<fURI, fURI, Call> jvm() {
            return super.jvm();
        }

        public fURI source() {
            return this.jvm().get0();
        }

        public fURI target() {
            return this.jvm().get1();
        }

        public Call call() {
            return this.jvm().get2();
        }
    }

    public class OnRead extends BaseOnRead {

        public OnRead() {
            super(noobj(), noobj());
        }

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
            return subscriptions.stream().map(Obj::<Subscription>as).filter(s -> vid.basePath().matches(s.target())).map(Obj::<Obj>as).reduce(Obj::append);
        }
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.debug("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
            subscriptions.stream().map(Obj::<Subscription>as).filter(s -> vid.basePath().matches(s.target())).forEach(s -> {
                LOG.debug("sending mail: (%s, %s)", obj, s);
                mail.add(MMachine.of(obj, s.call().toCode()));
            });
            while (!mail.isEmpty()) {
                final Machine machine = mail.poll();
                LOG.trace("processing mail: %s", machine);
                machine.apply();
            }
            return Optional.of(obj);
        }

        @Override
        public Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj obj, final Obj obj2) {
            LOG.debug("evaluating {{y}}postwrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQuery("sub")) {
                if (obj.isNoObj()) {
                    subscriptions.append(new Subscription(source, vid.basePath(), obj.<Call>as()));
                    //subscriptions.(s -> vid.basePath().matches(s.vid()));
                } else if (obj.tid().basePath().equals(SUBSCRIPTION_TID)) {
                    subscriptions.append(obj);
                } else
                    subscriptions.append(new Subscription(source, vid.basePath(), obj.as()));
                LOG.debug("current subscriptions: %s", subscriptions);
                return Optional.of(obj);
            }
            return Optional.empty();
        }

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.debug("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQuery("sub")) {
                if (obj.isNoObj()) {
                    subscriptions.append(new Subscription(source, vid.basePath(), obj.<Call>as()));
                    //subscriptions.(s -> vid.basePath().matches(s.vid()));
                } else if (obj.tid().basePath().equals(SUBSCRIPTION_TID)) {
                    subscriptions.append(obj);
                } else
                    subscriptions.append(new Subscription(source, vid.basePath(), obj.as()));
                LOG.debug("current subscriptions: %s", subscriptions);
                return Optional.of(obj);
            }
            return Optional.empty();
        }
    }
}
