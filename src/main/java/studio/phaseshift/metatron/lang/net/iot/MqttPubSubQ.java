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

package studio.phaseshift.metatron.lang.net.iot;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.SUB;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttPubSubQ extends PubSubQ {

    private final mqttSpace space;

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
                    LOG.info("unsubscribed from {{y}}%s{{X}}\n\t%s", vid.basePath(), space.client.toBlocking()
                            .unsubscribeWith()
                            .topicFilter(space.toMqttTopic(vid.basePath()))
                            .send());
                            /*whenComplete((m, e) -> {
                                if (null != e)
                                    LOG.error(e);
                                else
                                    LOG.info("unsubscribed from %s", m);
                            });*/
                } else {
                    space.client.toAsync()
                            .subscribeWith()
                            .topicFilter(space.toMqttTopic(vid.basePath()))
                            .callback(p -> {
                                LOG.trace("received %s", p);
                                final fURI t = space.toMtronVid(p.getTopic().toString());
                                Obj o;
                                if (p.getPayload().isPresent()) {
                                    final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                    o = space.jsonTranslator.translateString(json);
                                } else
                                    o = noobj();
                                space.cache.write(t, o);
                                super.qlessWrite(source, t, o);
                            })
                            .send()
                            .whenComplete((m, e) -> {
                                if (null != e)
                                    LOG.error(e);
                                else
                                    LOG.info("subscribed to {{y}}%s{{X}}\n\t%s", vid.basePath(), m);
                            });
                }
            }
            return super.preWrite(source, vid, obj);
        }
    }
}