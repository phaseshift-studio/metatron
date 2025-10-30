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

package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.space.kv.KVSpace;
import studio.phaseshift.metatron.space.mqtt.MqttSpace;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.URI_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class spaceInstSet extends MInstSet {

    public static final fURI SPACE_TID = f("/space");
    public static final fURI KV_TID = SPACE_TID.extend("kv");
    public static final fURI REMOTE_TID = SPACE_TID.extend("remote");
    public static final fURI MQTT_TID = SPACE_TID.extend("mqtt");
    // private static Map<fURI, Class<? extends Space>> SPACES = new HashMap<>();

   /* static {
        SPACES.put(KVSpace.KVSPACE_TID, KVSpace.class);
        SPACES.put(RemoteSpace.REMOTE_TID, KVSpace.class);
        SPACES.put(MqttSpace.MQTT_TID, MqttSpace.class);
    }*/

    public spaceInstSet(final fURI vid) {
        super(SPACE_TID, vid);
    }

    public static spaceInstSet of(final fURI vid) {
        return new spaceInstSet(vid);
    }

    /*public Space load(final fURI pattern, final fURI tid, final fURI vid) {
        try {
            return SPACES.get(tid).getConstructor(fURI.class, fURI.class).newInstance(pattern, vid);
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to load space %s", tid);
        }
    }*/

    @Override
    public Set<Type> types() {
        return Stream.of(T(REMOTE_TID), T(KV_TID), T(MQTT_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(KV_TID.dom(ALL.maybe()).rng(KV_TID), lst(T(URI_TID)), (lhs, inst) -> KVSpace.of(inst.arg(0).uriValue(), fURI.NULL)),
                instC(MQTT_TID.dom(ALL.maybe()).rng(MQTT_TID), lst(T(URI_TID)), (lhs, inst) -> MqttSpace.of(inst.arg(0).uriValue(), fURI.NULL))
        ).collect(Collectors.toSet());
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(NoObj.noobj()).collect(Collectors.toSet());
    }

}
