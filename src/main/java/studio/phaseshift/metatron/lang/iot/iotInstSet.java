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

package studio.phaseshift.metatron.lang.iot;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.iot.mqtt.mqttSpace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class iotInstSet extends MInstSet {

    public static final fURI IOT_INSTSET_TID = f("/iot");
    public static final fURI INST_TID = IOT_INSTSET_TID.extend("inst");

    public iotInstSet(final fURI vid) {
        super(IOT_INSTSET_TID, vid);
    }

    public static iotInstSet create() {
        return new iotInstSet(fURI.fnull);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                mqttSpace.MQTT_TYPE).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = new ArrayList<>();
        return new LinkedHashSet<>(insts);
    }
}