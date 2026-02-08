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

package studio.phaseshift.metatron.isa.iot.space.haos;

import studio.phaseshift.metatron.isa.iot.type.Device;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DeviceBuilder<D extends Device> {

    private final haosSpace haosSpace;
    private String entityVid;
    private String kind;
    private final Map<String, Object> settings;
    private Function<Object, Object> writeFunction;
    private Function<Object, Object> readFunction;

    public DeviceBuilder(final haosSpace haosSpace) {
        this.haosSpace = haosSpace;
        this.settings = new HashMap<>();
        this.writeFunction = null;
        this.readFunction = null;
    }

    public static <D extends Device> DeviceBuilder<D> build(final haosSpace haosSpace) {
        return new DeviceBuilder<>(haosSpace);
    }

    public DeviceBuilder<D> entityVid(final String entityVid) {
        this.entityVid = entityVid;
        return this;
    }

    public DeviceBuilder<D> kind(final String kind) {
        this.kind = kind;
        return this;
    }

    public DeviceBuilder<D> platform(final String platform) {
        this.settings.put("platform", platform);
        return this;
    }

    public DeviceBuilder<D> primary() {
        return this;
    }

    public DeviceBuilder<D> payloadOn(final Object payload) {
        this.settings.put("payload_on", payload);
        return this;
    }

    public DeviceBuilder<D> payloadOff(final Object payload) {
        this.settings.put("payload_off", payload);
        return this;
    }

    public DeviceBuilder<D> diagnostic() {
        this.settings.put("entity_category", "diagnostic");
        return this;
    }

    public DeviceBuilder<D> config() {
        this.settings.put("entity_category", "config");
        return this;
    }

    public DeviceBuilder<D> enabled(final boolean enabled) {
        this.settings.put("enabled", enabled);
        return this;
    }

    public DeviceBuilder<D> onRead(final Function<Object, Object> func) {
        this.readFunction = func;
        return this;
    }

    public DeviceBuilder<D> onWrite(final Function<Object, Object> func) {
        this.writeFunction = func;
        return this;
    }

    public DeviceBuilder<D> mode(final String mode) {
        this.settings.put("mode", mode);
        return this;
    }

    public DeviceBuilder<D> icon(final String icon) {
        this.settings.put("icon", icon);
        return this;
    }

    public DeviceBuilder<D> minMax(final int minimum, final int maximum) {
        this.settings.put("min", minimum);
        this.settings.put("max", maximum);
        return this;
    }

    public DeviceBuilder<D> unitOfMeasurement(final String uofm) {
        this.settings.put("unit_of_measurement", uofm);
        return this;
    }

    public DeviceBuilder<D> deviceClass(final String dc) {
        this.settings.put("device_class", dc);
        return this;
    }

    public DeviceBuilder<D> optimistic(final boolean optimistic) {
        this.settings.put("optimistic", optimistic);
        return this;
    }

    public DevicePublisher.Entity create() {
        this.haosSpace.logInfo("registering {} as a {} {}", this.entityVid, this.kind,
                 this.settings.getOrDefault("entity_category", ""));

        DevicePublisher.Entity entity = null;

        if ("sensor".equals(this.kind)) {
            entity = new DevicePublisher.Sensor(this.haosSpace.getDevicePublisher(), this.entityVid, this.settings);
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

        return entity;
    }
}
