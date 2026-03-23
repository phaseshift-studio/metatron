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

package studio.phaseshift.metatron.isa.iot.miot.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.miot.miotInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.SUPER;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Entity {

    private static final GraphittyLogger LOG = Graphitty.log(Entity.class);

    private Entity() {
        // do nothing
    }

    private static Rec writePin(final Rec pinholder, final fURI entityName, final Obj pinID, final Function<Int, Int> updateFunction) {
        AtomicBoolean found = new AtomicBoolean(false);
        final Uri pinUri = pinID.isUri() ? pinID.asUri() : uri("" + pinID.asInt().intValue());
        pinholder.elements().forEach(kv -> {
            if (kv.first().test(pinUri)) {
                final Int currentValue = kv.second().asInt();
                final Int newValue = updateFunction.apply(currentValue);
                final boolean atEntity = pinholder.tid().name().equals(entityName.name());
                final fURI toVID = deduceVID(atEntity ? pinholder : pinholder.at(entityName), atEntity ? entityName : f("+/+").extend(entityName));
                if (toVID == null)
                    pinholder.logger().warn("no vid associated with %s", pinholder, entityName);
                else {
                    pinholder.logger().info("writing to vid: %s", toVID.extend(kv.first().uriValue().toString()));
                    Router.writeToSpace(toVID.extend(kv.first().uriValue().toString()), newValue);
                }
                found.set(true);
            }
        });
        if (!found.get()) {
            final long currentValue = pinholder.asRec().at(pinholder).orElse(jnt(0)).asInt().intValue();
            final Int newValue = 0 == currentValue ? jnt(1) : jnt(0);
            final boolean atEntity = pinholder.tid().name().equals(entityName.name());
            final fURI toVID = deduceVID(atEntity ? pinholder : pinholder.at(entityName), atEntity ? entityName : f("+/+").extend(entityName));
            if (toVID == null)
                pinholder.logger().warn("no vid associated with %s", pinholder, entityName);
            else {
                pinholder.logger().info("writing to vid: %s", toVID.extend(pinUri.uriValue().toString()));
                Router.writeToSpace(toVID.extend(pinUri.uriValue().toString()), newValue);
            }
        }
        return pinholder;
    }


    public static void installTypes(final Set<Type> types, final Set<Inst> insts) {
        Type.Builder.build()
                .tid(REC_TID)
                .vid(MIOT_ENTITY_TID)
                .isaPredicate(rec(uri(SUPER), T(MIOT_DEVICE_TID)))
                .create(types, insts);
        Type.Builder.build()
                .tid(MIOT_ENTITY_TID)
                .vid(miotInstSet.MIOT_GPIO_TID)
                .isaPredicate(rec(URI_TYPE, INT_TYPE))
                .inst(miotInstSet.MIOT_INST_TID.extend("toggle").dom(miotInstSet.MIOT_GPIO_TID).rng(miotInstSet.MIOT_GPIO_TID), lst(INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("gpio"), inst.arg(0), currentValue -> 0 == currentValue.intValue() ? jnt(1) : jnt(0)))
                .inst(miotInstSet.MIOT_INST_TID.extend("on").dom(miotInstSet.MIOT_GPIO_TID).rng(miotInstSet.MIOT_GPIO_TID), lst(INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("gpio"), inst.arg(0), _ -> jnt(1)))
                .inst(miotInstSet.MIOT_INST_TID.extend("off").dom(miotInstSet.MIOT_GPIO_TID).rng(miotInstSet.MIOT_GPIO_TID), lst(INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("gpio"), inst.arg(0), _ -> jnt(0)))
                .create(types, insts);
        Type.Builder.build()
                .tid(MIOT_ENTITY_TID)
                .vid(miotInstSet.MIOT_PWM_TID)
                .isaPredicate(rec(URI_TYPE, INT_TYPE))
                .inst(miotInstSet.MIOT_INST_TID.extend("freq").dom(miotInstSet.MIOT_PWM_TID).rng(miotInstSet.MIOT_PWM_TID), lst(INT_TYPE, INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("pwm"), inst.arg(0), _ -> jnt(inst.arg(1).asInt().intValue())))
                .inst(miotInstSet.MIOT_INST_TID.extend("on").dom(miotInstSet.MIOT_PWM_TID).rng(miotInstSet.MIOT_PWM_TID), lst(INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("pwm"), inst.arg(0), _ -> jnt(255)))
                .inst(miotInstSet.MIOT_INST_TID.extend("off").dom(miotInstSet.MIOT_PWM_TID).rng(miotInstSet.MIOT_PWM_TID), lst(INT_TYPE),
                        (lhs, inst) -> writePin(lhs.asRec(), f("pwm"), inst.arg(0), _ -> jnt(0)))
                .create(types, insts);
    }
}