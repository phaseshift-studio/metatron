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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MMachine;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.sysInstSet;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class PubSubQ extends BaseQ {

    public static final fURI SUBQ_TID = sysInstSet.Q_TID.extend("subq");
    public static final fURI SUBSCRIPTION_TID = SUBQ_TID.extend("sub");
    // <source,pattern,callback>
    protected final Rec subscriptions = rec();
    protected final Queue<Machine> mail = new LinkedList<>();

    public static final Type SUBQ_TYPE = Type.Builder.build()
            .vid(SUBQ_TID)
            .tid(REC_TID)
            .constructor(
                    instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(SUBQ_TID),
                            lst(isa_(rec()).tryToInst()),
                            (lhs, inst) -> new PubSubQ())).create();

    public static final Type SUBSCRIPTION_TYPE =
            Type.Builder.build()
                    .vid(SUBSCRIPTION_TID)
                    .tid(REC_TID)
                    .predicate(isa_(rec(SRC, T(URI_TID), TGT, T(URI_TID), ON_RECV, T(ALL))))
                    .constructor(instC(INST_TID.dom(ALL_STAR).rng(SUBSCRIPTION_TID), lst(), (lhs, inst) -> {
                        if (lhs instanceof Subscription) {
                            return lhs;
                        } else if (lhs.isRec()) {
                            return new Subscription(lhs.asRec());
                        } else {
                            return new Subscription(f("/mqtt/test/#"), f("/mqtt/test/#"), lhs.<Call>as());
                        }
                    }))
                    .create();

    public PubSubQ() {
        super(mutableMap(), f(SUB), SUBQ_TID);
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

    public static class Subscription extends MRec {

        public Subscription(final Rec prerec) {
            super(prerec.jvm(), SUBSCRIPTION_TID, fURI.fnull);
        }

        public Subscription(final fURI source, final fURI target, final Call call) {
            super(CommonUtil.immutableOrderedMap(
                    uri(SRC), uri(source),
                    uri(TGT), uri(target),
                    uri(ON_RECV), call), SUBSCRIPTION_TID, fURI.fnull);
        }

        public fURI source() {
            return this.at(SRC).uriValue();
        }

        public fURI target() {
            return this.at(TGT).uriValue();
        }

        public Call call() {
            return this.at(ON_RECV);
        }
    }

    public class OnRead extends BaseOnRead {

        public OnRead() {
            super(noobj(), noobj());
        }

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            if (vid.hasQuery(SUB)) {
                LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
                return Optional.of(subscriptions.elements().map(Rel::second).map(Obj::<Subscription>as).filter(s -> vid.basePath().bimatches(s.target())).map(Obj::<Obj>as).reduce(Obj::append).orElse(noobj()));
            }
            return Optional.empty();
        }
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.debug("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
            subscriptions.elements().map(Rel::second).map(Obj::<Subscription>as).filter(s -> vid.matches(s.target())).forEach(s -> {
                LOG.debug("sending mail: (%s, %s)", obj, s);
                mail.add(MMachine.of(lst(List.of(vid.toUri(), obj)), s.call().toCode()));
            });
            BootLoader.getExecutor().submit(new Thread(() -> {
                while (!mail.isEmpty()) {
                    final Machine machine = mail.poll();
                    if (null == machine)
                        break;
                    LOG.trace("processing mail: %s", machine);
                    machine.apply();
                }
            }));
            return Optional.of(obj);
        }

        @Override
        public Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj obj, final Obj obj2) {
            LOG.debug("evaluating {{y}}postwrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQuery(SUB)) {
                if (obj.isNoObj()) {
                    subscriptions.jvm().remove(vid.basePath().toUri());
                } else if (obj.tid().basePath().equals(SUBSCRIPTION_TID)) {
                    subscriptions.jvm().put(vid.basePath().toUri(), new Subscription(obj.as()));
                } else
                    subscriptions.jvm().put(vid.basePath().toUri(), new Subscription(source, vid.basePath(), obj.as()));
                LOG.debug("current subscriptions: %s", subscriptions);
                return Optional.of(obj);
            }
            return Optional.empty();
        }

      /*  @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.debug("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQuery(SUB)) {
                Obj ret = noobj();
                if (obj.isNoObj()) {
                    subscriptions.self(List.of(), subscriptions.tid(), subscriptions.vid());
                    //subscriptions.(s -> vid.basePath().matches(s.vid()));
                } else if (obj.matches(SUBSCRIPTION_TYPE)) {
                    subscriptions.append(obj.tid(SUBSCRIPTION_TID));
                    ret = obj;
                } else {
                    ret = new Subscription(source, vid.basePath(), obj.as());
                    subscriptions.append(ret);
                }
                LOG.debug("current subscriptions: %s", subscriptions);
                return Optional.of(ret);
            }
            return Optional.empty();
        }*/
    }
}
