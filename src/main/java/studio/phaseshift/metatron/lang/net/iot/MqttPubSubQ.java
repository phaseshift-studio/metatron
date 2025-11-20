package studio.phaseshift.metatron.lang.net.iot;

import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

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
            if (vid.hasQuery("sub")) {
                if (obj.isNoObj()) {
                    space.client.toAsync()
                            .unsubscribeWith()
                            .topicFilter(space.toMqttTopic(vid.basePath()))
                            .send().
                            whenComplete((m, e) -> {
                                if (null != e)
                                    LOG.error(e);
                                else
                                    LOG.debug("unsubscribed from %s", m);
                            });
                } else {
                    space.client.toAsync()
                            .subscribeWith()
                            .topicFilter(space.toMqttTopic(vid.basePath()))
                            .callback(p -> {
                                LOG.trace("received %s", p);
                                if (p.getPayload().isPresent()) {
                                    final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                    final Obj o = space.jsonTranslator.translateString(json);
                                    space.cache.write(space.toMtronVid(p.getTopic().toString()), o);
                                    final Obj result = obj.apply(o);
                                    LOG.debug("subscription evaluation of %s => %s yielded %s", o, obj, result);
                                } else {
                                    space.cache.write(space.toMtronVid(p.getTopic().toString()), NoObj.noobj());
                                    final Obj result = obj.apply();
                                    LOG.debug("subscription evaluation of %s => %s yielded %s", NoObj.noobj(), obj, result);
                                }
                            })
                            .send()
                            .whenComplete((m, e) -> {
                                if (null != e)
                                    LOG.error(e);
                                else
                                    LOG.debug("subscribed to %s", m);
                            });
                }
                return super.preWrite(source, vid, obj);
            }
            return Optional.empty();
        }
    }
}