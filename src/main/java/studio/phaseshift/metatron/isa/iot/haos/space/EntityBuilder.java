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

package studio.phaseshift.metatron.isa.iot.haos.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.iot.iotInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeSpec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.haos.haosInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EntityBuilder {


    private final haosSpace haosSpace;
    private final fURI haosPrefix;
    private final fURI entityVid;
    private final Rec settings;
    private Function<Object, Object> writeFunction;
    private Function<Object, Object> readFunction;
    private final Type entityType;
    private final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device;

    public EntityBuilder(final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device, final Type entityType, final fURI entityVID) {
        this.device = device;
        this.entityType = entityType;
        this.entityVid = entityVID;
        this.writeFunction = null;
        this.readFunction = null;
        final fURI vidOrTid = TypeSpec.Helper.vidOrTid(this.getClass().getConstructors()[0].getParameterTypes()[0].getAnnotation(TypeSpec.class));
        this.haosSpace = Router.global().getSpaceFor(vidOrTid);
        this.haosPrefix = f(Space.Helper.extractRewrite(this.haosSpace.jvm()).get1()).asNode();
        final Obj deviceType = Router.readFromSpace(vidOrTid);
        assert device.test(deviceType);
        this.settings = rec(uri("unique_id"), uri(entityVID), uri("dev"), rec(uri("identifiers"), uri(device.vid()), uri("name"), device.at(uri("name"))));
    }

    public static EntityBuilder build_switch(final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device, final fURI entityVID) {
        return new EntityBuilder(device, HAOS_SWITCH_TYPE, entityVID);
    }

    public static EntityBuilder build_button(final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device, final fURI entityVID) {
        return new EntityBuilder(device, HAOS_BUTTON_TYPE, entityVID);
    }

    public static EntityBuilder build_number(final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device, final fURI entityVID) {
        return new EntityBuilder(device, HAOS_NUMBER_TYPE, entityVID);
    }

    public static EntityBuilder build_sensor(final @TypeSpec(tid = iotInstSet.IOT_DEVICE_TID_STRING) Rec device, final fURI entityVID) {
        return new EntityBuilder(device, HAOS_SENSOR_TYPE, entityVID);
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////

    public EntityBuilder name(final String name) {
        final String fixedName = name.replace(' ', '_').toLowerCase();
        this.settings.at(uri("name"), uri(fixedName), MUTABLE);
        this.settings.at(uri("stat_t"), uri(this.haosPrefix.extend("state").extend(fixedName)), MUTABLE);
        return this;
    }

    public EntityBuilder commandTopic(final fURI commandTopic) {
        this.settings.at(uri("cmd_t"), uri(commandTopic), MUTABLE);
        return this;
    }

    public EntityBuilder payloadOn(final Obj payload) {
        this.settings.at(uri("payload_on"), payload, MUTABLE);
        return this;
    }

    public EntityBuilder payloadOff(final Obj payload) {
        this.settings.at(uri("payload_off"), payload, MUTABLE);
        return this;
    }

    public EntityBuilder diagnostic() {
        this.settings.at(uri("entity_category"), uri("diagnostic"), MUTABLE);
        return this;
    }

    public EntityBuilder config() {
        this.settings.at(uri("entity_category"), uri("config"), MUTABLE);
        return this;
    }

    public EntityBuilder enabled(final boolean enabled) {
        this.settings.at(uri("enabled"), bool(enabled), MUTABLE);
        return this;
    }

    public EntityBuilder onRead(final Function<Object, Object> func) {
        this.readFunction = func;
        return this;
    }

    public EntityBuilder onWrite(final Function<Object, Object> func) {
        this.writeFunction = func;
        return this;
    }

    public EntityBuilder mode(final String mode) {
        this.settings.at(uri("mode"), uri(mode), MUTABLE);
        return this;
    }

    public EntityBuilder icon(final String icon) {
        this.settings.at(uri("icon"), uri(icon), MUTABLE);
        return this;
    }

    public EntityBuilder minMax(final int minimum, final int maximum) {
        this.settings.at(uri("min"), jnt(minimum), MUTABLE);
        this.settings.at(uri("max"), jnt(maximum), MUTABLE);
        return this;
    }

    public EntityBuilder unitOfMeasurement(final String uofm) {
        this.settings.at(uri("unit_of_measurement"), uri(uofm), MUTABLE);
        return this;
    }

    public EntityBuilder deviceClass(final String dc) {
        this.settings.at(uri("device_class"), uri(dc), MUTABLE);
        return this;
    }

    public EntityBuilder optimistic(final boolean optimistic) {
        this.settings.at(uri("optimistic"), bool(optimistic), MUTABLE);
        return this;
    }

    public @TypeSpec(tid = iotInstSet.IOT_ENTITY_TID_STRING) Rec create() {
        return (Rec) this.haosSpace.write(this.entityVid, this.settings);
/*        Entity entity;  
       if (HAOS_SENSOR_TYPE.equals(this.entityType)) {
            entity = new DevicePublisher.Sensor(this.haosSpace.getDevicePublisher(), this.entityVid, this.settings);
            this.haosSpace.sjvm().toAsync().publishWith()
                    .topic(this.conf.get("stat_t").toString())
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            this.lastPayload = payload;


            // Store entity with read/write functions if needed
            this.haosSpace.registerEntity(this.entityVid, entity, this.readFunction, this.writeFunction);
        } else if ("number".equals(this.kind)) {
            DevicePublisher.Number numberEntity = new DevicePublisher.Number(this.haosSpace.getDevicePublisher(), this.entityVid, this.settings);
            if (this.writeFunction != null) {
                numberEntity.setAction(msg -> this.writeFunction.apply(msg));
            }
            entity = numberEntity;
            this.haosSpace.registerEntity(this.entityVid, entity, this.readFunction, this.writeFunction);
        } else if ("button".equals(this.kind)) {
            DevicePublisher.Button buttonEntity = new DevicePublisher.Button(this.haosSpace.getDevicePublisher(), this.entityVid, this.settings);
            if (this.writeFunction != null) {
                buttonEntity.setAction(msg -> this.writeFunction.apply(msg));
            }
            entity = buttonEntity;
            this.haosSpace.registerEntity(this.entityVid, entity, this.readFunction, this.writeFunction);
        } else if ("switch".equals(this.kind)) {
            DevicePublisher.Switch switchEntity = new DevicePublisher.Switch(this.haosSpace.getDevicePublisher(), this.entityVid, this.settings);
            if (this.writeFunction != null) {
                switchEntity.setAction(msg -> this.writeFunction.apply(msg));
            }
            entity = switchEntity;
            this.haosSpace.registerEntity(this.entityVid, entity, this.readFunction, this.writeFunction);
        } else {
            throw new RuntimeException(this.kind + " is currently not supported");
        }

        return entity;*/
    }
}
