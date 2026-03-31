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

package studio.phaseshift.metatron.isa.iot.miot.space;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class miotSpace extends mqttSpace {

    public static final fURI MIOT_SPACE_TID = MIOT_ISA_TID.extend("space").extend("miot");
    public static final Type MIOT_SPACE_TYPE = Type.Builder.build()
            .tid(MQTT_SPACE_TID)
            .vid(MIOT_SPACE_TID)
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(MIOT_SPACE_TID),
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> miotSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    public static miotSpace of(final Rec config, final fURI vid) {
        final Mqtt5Client client = MqttClient.builder()
                .identifier(config.at(uri(CLIENT).orElse(uri("mtron-" + Math.abs(UUID.randomUUID().getMostSignificantBits())))).uriValue().toString())
                .serverHost(config.at(HOST).uriValue().host())
                .serverPort(config.at(HOST).uriValue().port())
                .useMqttVersion5()
                .build();
        return new miotSpace(client, config.jvm(), vid);
    }

    protected miotSpace(final Mqtt5Client client, final Map<Obj, Obj> config, final fURI vid) {
        super(client, config, MIOT_SPACE_TID, vid);
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            LOG.debug("reading %s", pattern);
            return IteratorUtil.stream(super.directReader().apply(pattern)).map(idobj -> {
                final Obj result = idobj.obj();
                if (result.isPoly()) {
                    if (!vid.hasPattern() && vid.test(this.pattern().retractPattern().extend("+"))) {
                        Rec soc = result.asRec().tid(MIOT_DEVICE_TID).selfVID(vid).asRec();
                        if (soc.has("gpio"))
                            soc.at("gpio", soc.at("gpio").asRec().tid(MIOT_GPIO_TID), MUTABLE);
                        if (soc.has("pwm"))
                            soc.at("pwm", soc.at("pwm").asRec().tid(MIOT_PWM_TID), MUTABLE);
                        return new IdObj(idobj.furi(), soc);
                    } else if (vid.test(this.pattern().retractPattern().extend("+/gpio"))) {
                        return new IdObj(idobj.furi(), result.asRec().tid(MIOT_GPIO_TID).selfVID(vid));
                    } else if (vid.test(this.pattern().retractPattern().extend("+/pwm"))) {
                        return new IdObj(idobj.furi(), result.asRec().tid(MIOT_PWM_TID).selfVID(vid));
                    }
                }
                return idobj;
            }).iterator();
        };
    }

}
