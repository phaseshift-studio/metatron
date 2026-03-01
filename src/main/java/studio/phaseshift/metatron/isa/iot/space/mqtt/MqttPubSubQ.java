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

package studio.phaseshift.metatron.isa.iot.space.mqtt;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.nio.ByteBuffer;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.SUB;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class MqttPubSubQ extends PubSubQ {

    protected final mqttSpace space;

    public MqttPubSubQ(final mqttSpace space) {
        super();
        this.space = space;
        this.onWrite = new MqttPubSubQ.OnWrite();
        this.onRead = new PubSubQ.OnRead();
    }

    public class OnWrite extends PubSubQ.OnWrite {
        @Override
        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQuery(SUB)) {
                if (obj.isNoObj()) {
                    space.sjvm().toAsync()
                            .unsubscribeWith()
                            .topicFilter(space.rewrite(vid.basePath(), true).toString())
                            .send()
                            .whenComplete((m, e) -> {
                                if (null != e) {
                                    Router.global().stats().ioStats().incrBytesRecv(e.toString().length());
                                    LOG.error(e);
                                } else {
                                    Router.global().stats().ioStats().incrBytesRecv(m.toString().length());
                                    // super.qlessWrite(source, vid, noobj());
                                    // space.cache.write(vid, noobj());
                                    // subscriptions = subscriptions.stream().filter(x -> !x.<Subscription>as().target().bimatches(vid.qLess())).reduce(noobj(), (a, b) -> a.append(b));
                                    LOG.info("unsubscribed from {{y}}%s{{X}}\n\t%s", vid.basePath(), m);
                                }
                            });
                } else {
                    space.sjvm().toAsync()
                            .subscribeWith()
                            .topicFilter(space.rewrite(vid.basePath(), false).toString())
                            .callback(p -> {
                                LOG.trace("received %s", p);
                                final fURI topic = space.rewrite(f(p.getTopic().toString()), false);
                                Obj o;
                                if (p.getPayload().isPresent()) {
                                    Router.global().stats().ioStats().incrBytesRecv(p.toString().length());
                                    o = space.serializer.inputBytes(ByteBuffer.wrap(p.getPayloadAsBytes()));
                                } else
                                    o = noobj();
                                super.qlessWrite(source, topic, o);
                                space.cache.write(topic, o);

                            })
                            .executor(BootLoader.getExecutor())
                            .send()
                            .whenComplete((m, e) -> {
                                if (null != e)
                                    LOG.error(e);
                                else
                                    LOG.info("subscribed to {{y}}%s{{X}}\n\t%s", vid.basePath(), MObjFactory.of().toObj(m).asRec());
                            });
                }
            }
            return Optional.empty();
        }
    }
}