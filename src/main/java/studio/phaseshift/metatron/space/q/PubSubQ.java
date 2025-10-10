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
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.space.Qs;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.space.Space.MTRON_SPACE_TID;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class PubSubQ extends BaseQ {

    public static final fURI SUBSCRIPTION_TID = MTRON_SPACE_TID.extend("sub");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static class Subscription extends MObj {


        public Subscription(final fURI source, final fURI target, final Call code) {
            super(Triplet.with(source, target, code), SUBSCRIPTION_TID, fURI.NULL);
        }

        public Triplet<fURI, fURI, Call> value() {
            return (Triplet<fURI, fURI, Call>) super.value();
        }

        public fURI source() {
            return this.value().get0();
        }

        public fURI target() {
            return this.value().get1();
        }

        public Call code() {
            return this.value().get2();
        }
    }


    // <source,pattern,callback>
    protected final List<Subscription> subscriptions = new ArrayList<>();
    protected final Queue<Runnable> mail = new LinkedList<>();

    public PubSubQ() {
        super(f("sub"));
        this.onRead = new OnRead();
        this.onWrite = new OnWrite();
    }

    public class OnRead extends BaseQ.OnRead {

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
            return subscriptions.stream().filter(s -> vid.basePath().matches(s.target())).map(Obj::<Obj>as).reduce(Obj::append);
        }
    }

    public class OnWrite extends BaseQ.OnWrite {

        @Override
        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
            subscriptions.stream().filter(s -> vid.basePath().matches(s.target())).forEach(s -> {
                LOG.debug("adding mail: (%s, %s)", obj, s);
                mail.add(() -> s.code().apply(obj));
            });
            while (!mail.isEmpty()) {
                final Runnable runnable = mail.poll();
                LOG.trace("evaluating mail");
                runnable.run();
            }
            return Optional.of(obj);
        }

        @Override
        public Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            LOG.trace("evaluating {{y}}post write{{/y}}: %s (old:%s) => %s", newObj, oldObj, vid);
            if (vid.hasQuery("sub")) {
                if (newObj.isNoObj()) {
                    subscriptions.removeIf(s -> vid.basePath().matches(s.vid()));
                } else
                    subscriptions.add(new Subscription(source, vid.basePath(), newObj.as()));
                LOG.debug("current subscriptions: %s", subscriptions);
            }
            return Optional.empty();
        }
    }
}
