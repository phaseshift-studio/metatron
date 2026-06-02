/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.iot.haos;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.haos.space.haosSpace.HAOS_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/iot/haos")
public class haosInstSet extends AbstractInstSet {

    public static final fURI HAOS_ISA_TID = IOT_ISA_TID.extend("haos");
    public static final fURI HAOS_INST_TID = HAOS_ISA_TID.extend("inst");

    public static final fURI HAOS_ENTITY_TID = HAOS_ISA_TID.extend("entity");
    public static final fURI HAOS_SENSOR_TID = HAOS_ISA_TID.extend("sensor");
    public static final fURI HAOS_NUMBER_TID = HAOS_ISA_TID.extend("number");
    public static final fURI HAOS_BUTTON_TID = HAOS_ISA_TID.extend("button");
    public static final fURI HAOS_SWITCH_TID = HAOS_ISA_TID.extend("switch");
    public static final fURI HAOS_LIGHT_TID = HAOS_ISA_TID.extend("light");
    public static final fURI HAOS_BINARY_SENSOR_TID = HAOS_ISA_TID.extend("binary_sensor");
    public static final fURI HAOS_AUTOMATION_TID = HAOS_ISA_TID.extend("automation");
    public static final fURI HAOS_SELECT_TID = HAOS_ISA_TID.extend("select");

    public static final Rec HAOS_DEVICE_CONFIG = rec(
            uri("configuration_url").maybe().asUri(), URI_TYPE,
            uri("connections").maybe(), LST_TYPE,
            uri("hw_version").maybe(), STR_TYPE,
            uri("identifiers").maybe(), LST_TYPE,
            uri("name").maybe(), STR_TYPE);


    public static final Map<String, Tuple.Pair<fURI, fURI>> discoveryAbbrevMap = Collections.unmodifiableMap(
            new HashMap<>() {{
                put("command_topic", Tuple.Pair.with(f("cmd_t"), f("command_topic")));
                put("state_topic", Tuple.Pair.with(f("stat_t"), f("state_topic")));
                put("availability", Tuple.Pair.with(f("avty"), f("availability")));
                put("availability_topic", Tuple.Pair.with(f("avty_t"), f("availability_topic")));
                put("availability_mode", Tuple.Pair.with(f("avty_mode"), f("availability_mode")));
                put("availability_template", Tuple.Pair.with(f("avty_tpl"), f("availability_template")));
                put("device_class", Tuple.Pair.with(f("dev_cla"), f("device_class")));
                put("icon", Tuple.Pair.with(f("ic"), f("icon")));
                put("name", Tuple.Pair.with(f("name"), f("name")));
                put("unique_id", Tuple.Pair.with(f("uniq_id"), f("unique_id")));
                put("unit_of_measurement", Tuple.Pair.with(f("unit_of_meas"), f("unit_of_measurement")));
                put("value_template", Tuple.Pair.with(f("val_tpl"), f("value_template")));
                put("json_attributes", Tuple.Pair.with(f("json_attr"), f("json_attributes")));
                put("json_attributes_topic", Tuple.Pair.with(f("json_attr_t"), f("json_attributes_topic")));
                put("json_attributes_template", Tuple.Pair.with(f("json_attr_tpl"), f("json_attributes_template")));
                put("device", Tuple.Pair.with(f("dev"), f("device")));
                put("topic", Tuple.Pair.with(f("t"), f("topic")));
                put("encoding", Tuple.Pair.with(f("e"), f("encoding")));
                put("payload_on", Tuple.Pair.with(f("pl_on"), f("payload_on")));
                put("payload_off", Tuple.Pair.with(f("pl_off"), f("payload_off")));
                put("payload_available", Tuple.Pair.with(f("pl_avail"), f("payload_available")));
                put("payload_not_available", Tuple.Pair.with(f("pl_not_avail"), f("payload_not_available")));
                put("optimistic", Tuple.Pair.with(f("opt"), f("optimistic")));
                put("qos", Tuple.Pair.with(f("qos"), f("qos")));
                put("retain", Tuple.Pair.with(f("ret"), f("retain")));
                put("force_update", Tuple.Pair.with(f("frc_upd"), f("force_update")));
                put("expire_after", Tuple.Pair.with(f("exp_aft"), f("expire_after")));
            }});


    public static final Type HAOS_ENTITY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(HAOS_ENTITY_TID)
            .isaPredicate(rec(
                    uri("entity_category").<Uri>maybe(), T(ALL),
                    uri("icon").maybe(), URI_TYPE,
                    uri("name").maybe(), STR_TYPE,
                    uri("unique_id").maybe(), URI_TYPE,
                    uri("state_topic").maybe(), URI_TYPE,
                    uri("state"), T(ALL),
                    uri("availability_topic").maybe(), URI_TYPE,
                    uri("stat_t").maybe(), URI_TYPE,
                    uri("cmd_t").maybe(), URI_TYPE,
                    uri("retain").maybe(), else_(bool(true)).tryToInst(),
                    uri("last_updated").maybe(), STR_TYPE,
                    uri("last_changed").maybe(), STR_TYPE,
                    uri("device").maybe(), HAOS_DEVICE_CONFIG))
            .create();
    public static final Type HAOS_SENSOR_TYPE = Type.Builder.build()
            .tid(HAOS_ENTITY_TID)
            .vid(HAOS_SENSOR_TID)
            .isaPredicate(rec(
                    uri("device_class"), is_(or_(URI_TYPE, STR_TYPE)).tryToInst(),
                    uri("unit_of_measurement"), T(ALL),
                    uri("command_topic").maybe(), URI_TYPE)).create();
    public static final Type HAOS_NUMBER_TYPE = Type.Builder.build()
            .tid(HAOS_ENTITY_TID)
            .vid(HAOS_NUMBER_TID)
            .isaPredicate(rec(
                    uri("platform"), uri("number"),
                    uri("payload_off"), T(ALL),
                    uri("enabled").maybe(), BOOL_TYPE,
                    uri("optimistic").maybe(), BOOL_TYPE,
                    uri("command_topic").maybe(), URI_TYPE)).create();
    public static final Type HAOS_BUTTON_TYPE = Type.Builder.build()
            .tid(HAOS_ENTITY_TID)
            .vid(HAOS_BUTTON_TID).create();
    public static final Type HAOS_SWITCH_TYPE = Type.Builder.build()
            .tid(HAOS_ENTITY_TID)
            .vid(HAOS_SWITCH_TID)
            .isaPredicate(rec(
                    uri("payload_on").maybe().asUri(), T(ALL),
                    uri("payload_off").maybe(), T(ALL),
                    uri("enabled").maybe(), BOOL_TYPE,
                    uri("optimistic").maybe(), BOOL_TYPE,
                    uri("command_topic").maybe(), URI_TYPE)).create();
    public static final Type HAOS_LIGHT_TYPE = Type.Builder.build()
            .tid(HAOS_ENTITY_TID)
            .vid(HAOS_LIGHT_TID)
            .isaPredicate(rec(
                    uri("payload_on").maybe().asUri(), T(ALL),
                    uri("payload_off").maybe(), T(ALL),
                    uri("command_topic").maybe(), URI_TYPE)).create();

    public haosInstSet() {
        this(HAOS_ISA_TID);
    }

    public haosInstSet(final fURI vid) {
        super(HAOS_ISA_TID, vid);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                HAOS_ENTITY_TYPE,
                HAOS_SENSOR_TYPE,
                HAOS_NUMBER_TYPE,
                HAOS_BUTTON_TYPE,
                HAOS_SWITCH_TYPE,
                HAOS_LIGHT_TYPE,
                HAOS_SPACE_TYPE).collect(Collectors.toSet());
    }

    public static final fURI HAOS_TOGGLE_INST_TID = HAOS_INST_TID.extend("toggle");


    private Obj toggle(final Obj lhs) {
        final Obj currentState = lhs.asRec().at("state").orSupply(() -> Router.readFromSpace(lhs.asRec().at(discoveryAbbrevMap.get("state_topic").get1().toUri()).uriValue()));
        final Obj payloadOn = Router.readFromSpace(lhs.asRec().at(discoveryAbbrevMap.get("payload_on").get1().toUri()).orElse(NOOBJ_TID.toUri()).uriValue()).orElse(currentState.isInt() ?
                jnt(1) : currentState.isUri() ?
                uri("on") :
                str("on"));
        final Obj payloadOff = Router.readFromSpace(lhs.asRec().at(discoveryAbbrevMap.get("payload_off").get1().toUri()).orElse(NOOBJ_TID.toUri()).uriValue()).orElse(currentState.isInt() ?
                jnt(0) : currentState.isUri() ?
                uri("off") :
                str("off"));
        return currentState.equals(payloadOn) ? payloadOff : payloadOn;
    }

    private fURI getCommandTopic(final Obj lhs) {
        final Obj topic = lhs.asRec().at(discoveryAbbrevMap.get("command_topic").get1().toUri());
        if (!topic.isNoObj())
            return topic.uriValue();
        if (null != lhs.vid())
            return lhs.vid().asNode().extend("state");
        throw MTronException.of("unable to determine command topic for %s", lhs);
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = List.of(
              /*  instC(HAOS_TOGGLE_INST_TID.dom(HAOS_SWITCH_TID).rng(HAOS_SWITCH_TID), lst(), (lhs, inst) -> {
                    Router.writeToSpace(getCommandTopic(lhs), toggle(lhs));
                    return lhs;
                }),*/
               /* instC(HAOS_TOGGLE_INST_TID.dom(HAOS_LIGHT_TID).rng(HAOS_LIGHT_TID), lst(), (lhs, inst) -> {
                    final fURI commandTopic = getCommandTopic(lhs);
                    final Obj payload = toggle(lhs);
                    LOG.info("toggling {{b}}%s{{X}} with %s", commandTopic, payload);
                    Router.writeToSpace(commandTopic, payload);
                    //Router.writeToSpace(lhs.vid().extend("last_updated"), str(CommonUtil.getTimeStamp(null)));
                    //Router.writeToSpace(lhs.vid().extend("last_changed"), str(CommonUtil.getTimeStamp(null)));
                    return lhs;
                })*/);
        return new LinkedHashSet<>(insts);
    }
}
