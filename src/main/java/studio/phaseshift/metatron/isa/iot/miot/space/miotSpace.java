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
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.UUID;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class miotSpace extends mqttSpace {
    
    public static final fURI MIOT_SPACE_TID = MIOT_ISA_TID.extend("space").extend("miot");
    public static final Type MIOT_SPACE_TYPE = Type.Builder.build()
            .tid(MQTT_SPACE_TID)
            .vid(MIOT_SPACE_TID)
            .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(MIOT_SPACE_TID),
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> {
                        final Space space = miotSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                        Router.global().addSpace(space);
                        return space;
                    })).create();

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

    public Obj read(final fURI vid) {
        final Obj result = super.read(vid);
        if (result.isNoObj())
            return result;
       // final fURI toVID = Space.Helper.toNativeSpace(vid,this.routes());
        // TODO: branch uri/pattern and device construction
        if (!vid.hasPattern() && vid.test(this.pattern().retractPattern().extend("+"))) {
            return result.asRec().tid(MIOT_DEVICE_TID).selfVID(vid);
        } else if(vid.test(this.pattern().retractPattern().extend("+/gpio"))) {
            return result.asRec().tid(MIOT_GPIO_TID).selfVID(vid);
        } else if(vid.test(this.pattern().retractPattern().extend("+/pwm"))) {
            return result.asRec().tid(MIOT_PWM_TID).selfVID(vid);
        }
        return result;
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
