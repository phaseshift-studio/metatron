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

import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DevicePublisher - Handles Home Assistant MQTT device discovery and entity publishing.
 * This class clones the behavior from uhome.py
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DevicePublisher {

    private Mqtt5Client mqttClient;
    private final List<Entity> entities;
    private final String name;
    private final String id;
    private final String discoveryPrefix;
    private final String willTopic;
    private final String haStatusTopic;
    private byte[] haStatus;
    private final Map<String, Object> device;
    private long lastPing;
    private int pingInterval;

    /**
     * Initializes the device with the given name and optional keyword arguments passed
     * directly to the Home Assistant config.
     *
     * @param deviceName      The name of the device.
     * @param discoveryPrefix The prefix used for discovery topics. Defaults to 'homeassistant'.
     * @param kwargs          Additional keyword arguments to be added to the configuration of the device.
     */
    public DevicePublisher(final String deviceName, final String discoveryPrefix, final Map<String, Object> kwargs) {
        this.name = deviceName;
        this.id = this.name.replace(' ', '_').toLowerCase();
        this.discoveryPrefix = discoveryPrefix != null ? discoveryPrefix : "homeassistant";
        this.willTopic = String.format("%s/availability/%s", this.discoveryPrefix, this.id);
        this.haStatusTopic = String.format("%s/status", this.discoveryPrefix);
        this.haStatus = null;
        this.device = new HashMap<>(kwargs != null ? kwargs : new HashMap<>());
        this.device.put("name", this.name);
        this.device.put("ids", this.id);
        this.entities = new ArrayList<>();
    }

    public DevicePublisher(final String deviceName) {
        this(deviceName, "homeassistant", null);
    }

    /**
     * Connects to the MQTT broker and sets up the necessary configurations.
     *
     * @param mqttClient The MQTT client instance to be used for the connection.
     */
    public void connect(final Mqtt5Client mqttClient) {
        this.mqttClient = mqttClient;
        // Assuming keepalive is 60 seconds by default, adjust as needed
        this.pingInterval = (int) (60 * 0.8);
        this.lastPing = System.currentTimeMillis();

        // Set up last will
        this.mqttClient.toAsync().connectWith()
                .willPublish()
                .topic(this.willTopic)
                .payload("offline".getBytes(StandardCharsets.UTF_8))
                .retain(true)
                .applyWillPublish()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        throw new RuntimeException("Failed to connect", throwable);
                    }
                    // Publish online status
                    this.mqttClient.toAsync().publishWith()
                            .topic(this.willTopic)
                            .payload("online".getBytes(StandardCharsets.UTF_8))
                            .retain(true)
                            .send();

                    // Subscribe to HA status topic
                    this.mqttClient.toAsync().subscribeWith()
                            .topicFilter(this.haStatusTopic)
                            .callback(this::mqttCallback)
                            .send();
                });
    }

    /**
     * Callback function for handling incoming MQTT messages.
     *
     * @param publish The received MQTT message.
     */
    private void mqttCallback(final Mqtt5Publish publish) {
        final String topic = publish.getTopic().toString();
        final byte[] msg = publish.getPayloadAsBytes();

        if (topic.equals(this.haStatusTopic)) {
            if (new String(msg, StandardCharsets.UTF_8).equals("online")) {
                this.discoverAll();
            }
            this.haStatus = msg;
        }

        for (final Entity entity : this.entities) {
            if (topic.equals(entity.getTopic())) {
                entity.handleAction(new String(msg, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Main loop function to handle MQTT broker ping and message checking.
     */
    public void loop() {
        final long now = System.currentTimeMillis();
        if ((now - this.lastPing) > this.pingInterval * 1000L) {
            // Send ping if needed (HiveMQ client handles this automatically)
            this.lastPing = now;
        }
        // HiveMQ client handles message checking automatically
    }

    /**
     * (Re-)sends the discovery message for all entities to Home Assistant.
     */
    public void discoverAll() {
        for (final Entity entity : this.entities) {
            entity.discover();
        }
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getDiscoveryPrefix() {
        return discoveryPrefix;
    }

    public Map<String, Object> getDevice() {
        return device;
    }

    public Mqtt5Client getMqttClient() {
        return mqttClient;
    }

    public String getWillTopic() {
        return willTopic;
    }

    /**
     * Base Entity class for all Home Assistant entities.
     */
    public abstract static class Entity {
        protected final DevicePublisher device;
        protected final String name;
        protected final String entity;
        protected final String uniqueId;
        protected final String topicPrefix;
        protected final String discoveryTopic;
        protected final String topic;
        protected final Map<String, Object> conf;
        protected final String entityType;

        public Entity(final DevicePublisher device, final String entityName, final String entityType, final Map<String, Object> kwargs) {
            this.device = device;
            this.device.entities.add(this);
            this.name = entityName;
            this.entity = entityName.replace(' ', '_').toLowerCase();
            this.entityType = entityType;
            this.uniqueId = String.format("%s_%s", this.device.id, this.entity);
            this.topicPrefix = String.format("%s/%s/%s", this.device.discoveryPrefix, this.entityType, this.device.id);
            this.discoveryTopic = String.format("%s/%s/config", this.topicPrefix, this.entity);
            this.topic = String.format("%s/state/%s", this.topicPrefix, this.entity);
            this.conf = makeConf(kwargs != null ? kwargs : new HashMap<>());
        }

        protected Map<String, Object> makeConf(final Map<String, Object> kwargs) {
            final Map<String, Object> conf = new HashMap<>();
            conf.put("name", this.name);
            conf.put("dev", this.device.device);
            conf.put("uniq_id", this.uniqueId);
            conf.put("avty_t", this.device.willTopic);

            if ("sensor".equals(this.entityType) || "binary_sensor".equals(this.entityType)) {
                conf.put("stat_t", this.topic);
            } else if ("number".equals(this.entityType)) {
                conf.put("stat_t", this.topic);
                conf.put("cmd_t", this.topic);
            } else if ("button".equals(this.entityType)) {
                conf.put("cmd_t", this.topic);
            } else if ("switch".equals(this.entityType)) {
                conf.put("stat_t", this.topic);
                conf.put("cmd_t", this.topic);
            }

            conf.putAll(kwargs);
            return conf;
        }

        public void discover() {
            final String json = toJson(this.conf);
            this.device.mqttClient.toAsync().publishWith()
                    .topic(this.discoveryTopic)
                    .payload(json.getBytes(StandardCharsets.UTF_8))
                    .send();
        }

        public String getTopic() {
            return this.topic;
        }

        protected void handleAction(final String message) {
            // Override in subclasses that support actions
        }

        private String toJson(final Map<String, Object> map) {
            // Simple JSON serialization - in production, use a proper JSON library
            final StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (final Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    sb.append("\"").append(entry.getValue()).append("\"");
                } else if (entry.getValue() instanceof Map) {
                    sb.append(toJson((Map<String, Object>) entry.getValue()));
                } else {
                    sb.append(entry.getValue());
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * MQTT Sensor entity.
     * More information: https://www.home-assistant.io/integrations/sensor.mqtt/
     */
    public static class Sensor extends Entity {
        private String lastPayload;

        public Sensor(final DevicePublisher device, final String entityName, final Map<String, Object> kwargs) {
            super(device, entityName, "sensor", kwargs);
            this.lastPayload = null;
        }

        public void publish(final String payload) {
            if (payload.equals(this.lastPayload)) return;
            this.device.mqttClient.toAsync().publishWith()
                    .topic(this.conf.get("stat_t").toString())
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            this.lastPayload = payload;
        }
    }

    /**
     * MQTT Binary Sensor entity.
     * More information: https://www.home-assistant.io/integrations/binary_sensor.mqtt/
     */
    public static class BinarySensor extends Entity {
        private String lastPayload;

        public BinarySensor(final DevicePublisher device, final String entityName, final Map<String, Object> kwargs) {
            super(device, entityName, "binary_sensor", kwargs);
            this.lastPayload = null;
        }

        public void publish(final String payload) {
            if (payload.equals(this.lastPayload)) return;
            this.device.mqttClient.toAsync().publishWith()
                    .topic(this.conf.get("stat_t").toString())
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            this.lastPayload = payload;
        }
    }

    /**
     * MQTT Button entity.
     * More information: https://www.home-assistant.io/integrations/button.mqtt/
     */
    public static class Button extends Entity {
        private Consumer<String> action;

        public Button(final DevicePublisher device, final String entityName, final Map<String, Object> kwargs) {
            super(device, entityName, "button", kwargs);
            this.action = null;
        }

        public String getCommandTopic() {
            return this.conf.get("cmd_t").toString();
        }

        public void setAction(final Consumer<String> action) {
            this.device.mqttClient.toAsync().subscribeWith()
                    .topicFilter(this.conf.get("cmd_t").toString())
                    .callback(publish -> handleAction(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8)))
                    .send();
            this.action = action;
        }

        @Override
        protected void handleAction(final String message) {
            if (this.action != null) {
                this.action.accept(message);
            }
        }
    }

    /**
     * MQTT Switch entity.
     * More information: https://www.home-assistant.io/integrations/switch.mqtt
     */
    public static class Switch extends Entity {
        private Consumer<String> action;
        private String lastPayload;

        public Switch(final DevicePublisher device, final String entityName, final Map<String, Object> kwargs) {
            super(device, entityName, "switch", kwargs);
            this.action = null;
            this.lastPayload = null;
        }

        public void publish(final String payload) {
            if (payload.equals(this.lastPayload)) return;
            this.device.mqttClient.toAsync().publishWith()
                    .topic(this.conf.get("stat_t").toString())
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            this.lastPayload = payload;
        }

        public String getCommandTopic() {
            return this.conf.get("cmd_t").toString();
        }

        public void setAction(final Consumer<String> action) {
            this.device.mqttClient.toAsync().subscribeWith()
                    .topicFilter(this.conf.get("cmd_t").toString())
                    .callback(publish -> handleAction(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8)))
                    .send();
            this.action = action;
        }

        @Override
        protected void handleAction(final String message) {
            if (this.action != null) {
                this.action.accept(message);
            }
        }
    }

    /**
     * MQTT Number entity.
     * More information: https://www.home-assistant.io/integrations/number.mqtt/
     */
    public static class Number extends Entity {
        private Consumer<String> action;
        private String lastPayload;

        public Number(final DevicePublisher device, final String entityName, final Map<String, Object> kwargs) {
            super(device, entityName, "number", kwargs);
            this.action = null;
            this.lastPayload = null;
        }

        public void publish(final String payload) {
            if (payload.equals(this.lastPayload)) return;
            this.device.mqttClient.toAsync().publishWith()
                    .topic(this.conf.get("stat_t").toString())
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            this.lastPayload = payload;
        }

        public String getCommandTopic() {
            return this.conf.get("cmd_t").toString();
        }

        public void setAction(final Consumer<String> action) {
            this.device.mqttClient.toAsync().subscribeWith()
                    .topicFilter(this.conf.get("cmd_t").toString())
                    .callback(publish -> handleAction(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8)))
                    .send();
            this.action = action;
        }

        @Override
        protected void handleAction(final String message) {
            if (this.action != null) {
                this.action.accept(message);
            }
        }
    }
}
