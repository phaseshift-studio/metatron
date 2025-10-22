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

package studio.phaseshift.metatron.space.q;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Call;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.vm.MMachine;
import studio.phaseshift.metatron.vm.Machine;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.MTRON_SPACE_TID;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class PubSubQ extends BaseQ {

    public static final fURI SUBSCRIPTION_TID = MTRON_SPACE_TID.extend("sub");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    // <source,pattern,callback>
    protected final Obj subscriptions = MObjs.empty();
    protected final Queue<Machine> mail = new LinkedList<>();

    public PubSubQ(final Space space) {
        super(space, f("sub"), SUBSCRIPTION_TID);
        this.onRead = new OnRead();
        this.onWrite = new OnWrite();
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

    public class OnRead extends BaseQ.OnRead {

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
            return subscriptions.stream().map(Obj::<Subscription>as).filter(s -> vid.basePath().matches(s.target())).map(Obj::<Obj>as).reduce(Obj::append);
        }
    }

    public class OnWrite extends BaseQ.OnWrite {

        @Override
        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
            subscriptions.stream().map(Obj::<Subscription>as).filter(s -> vid.basePath().matches(s.target())).forEach(s -> {
                LOG.debug("sending mail: (%s, %s)", obj, s);
                mail.add(MMachine.of(obj, s.call().toCode()));
            });
            while (!mail.isEmpty()) {
                final Machine monoid = mail.poll();
                LOG.trace("processing mail: %s", monoid);
                monoid.apply();
            }
            return Optional.of(obj);
        }

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
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
