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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SoC {

    private SoC() {
        // do nothing
    }

    private static class PinMap extends AbstractMap<Integer, String> {
        private final Map<Integer, String> map = new LinkedHashMap<>();

        public PinMap() {
        }

        public void addGnd(final int pin) {
            this.map.put(pin, "{{k}}" + pin);
        }

        public void addVcc(final int pin) {
            this.map.put(pin, "{{r}}" + pin);
        }

        public void addGPIO(final int pin) {
            this.map.put(pin, "{{g}}" + pin);
        }

        public void addPWM(final int pin) {
            this.map.put(pin, "{{y}}" + pin);
        }

        @Override
        public Set<Entry<Integer, String>> entrySet() {
            return this.map.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), this.get(e.getKey()))).collect(Collectors.toSet());
        }

        @Override
        public boolean containsKey(Object key) {
            return this.map.containsKey(key);
        }

        @Override
        public String get(final Object key) {
            return this.map.getOrDefault(key, "{{w}}" + key);
        }

        @Override
        public String merge(Integer key, String value,
                            BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return this.map.merge(key, value, (a, b) -> b);
        }
    }

    private static Map<Integer, String> pinMap(final Rec soc) {
        final PinMap map = new PinMap();
        map.addGnd(1);
        map.addGnd(12);
        map.addGnd(8);
        map.addVcc(7);
        map.addVcc(15);
        soc.at("gpio").asRec().elements().forEach(kv -> map.addGPIO(Integer.parseInt(kv.first().uriValue().name().replace("<", "").replace(">", ""))));
        soc.at("pwm").asRec().elements().forEach(kv -> map.addPWM(Integer.parseInt(kv.first().uriValue().name().replace("<", "").replace(">", ""))));
        if (soc.has("i2c"))
            soc.at("i2c").asRec().elements()
                    .map(kv -> new AbstractMap.SimpleEntry<>(kv.first().uriValue(), kv.second().intValue()))
                    .collect(Collectors.toMap(
                            e -> Integer.parseInt(e.getKey().name().replace("<", "").replace(">", "")),
                            e -> "{{c}}" + Integer.parseInt(e.getKey().name().replace("<", "").replace(">", "")),
                            (a, b) -> b,
                            () -> map));
        if (soc.has("spi"))
            soc.at("spi").asRec().elements()
                    .map(kv -> new AbstractMap.SimpleEntry<>(kv.first().uriValue(), kv.second().intValue()))
                    .collect(Collectors.toMap(
                            e -> Integer.parseInt(e.getKey().name().replace("<", "").replace(">", "")),
                            e -> "{{b}}" + Integer.parseInt(e.getKey().name().replace("<", "").replace(">", "")),
                            (a, b) -> b,
                            () -> map));
        return map;
    }

    public static void installTypes(final Set<Type> types, final Set<Inst> insts) {
        /// /////////////////////////////////////////////////////////
        Type.Builder.build()
                .tid(MIOT_DEVICE_TID)
                .vid(MIOT_SOC_TID)
                .isaPredicate(rec(
                        uri("code").<Uri>maybe(), URI_TYPE,
                        uri("arch"), is_(or_(eq_(uri("esp32")), eq_(uri("esp8266"))))))
                .create(types, insts);
        /// /////////////////////////////////////////////////////////
        Type.Builder.build()
                .tid(MIOT_SOC_TID)
                .vid(MIOT_ESP32_TID)
                .isaPredicate(rec(uri("arch"), uri("esp32")))
                .create(types, insts);
        /// /////////////////////////////////////////////////////////
        Type.Builder.build()
                .tid(MIOT_ESP32_TID)
                .vid(MIOT_WEMOS_D1_MINI_TID)
                .isaPredicate(rec(uri("arch"), uri("esp32")))
                .inst(MIOT_INST_TID.extend("render").dom(MIOT_WEMOS_D1_MINI_TID).rng(STR_TID), lst(), (lhs, inst) -> {
                    final Map<Integer, String> pinMap = pinMap(lhs.asRec());
                    final String rendering = """
                                             
                                             {{b}}___________________
                                             {{b}}|                 |
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|%7s  %7s    %7s  %7s {{b}}|  %4s
                                             {{b}}|          %7s  %7s{{b}} |
                                             {{b}} \\_               |
                                             {{b}}   +____{{y}}USB{{b}}_______|{{X}}
                                             
                                             """.formatted(
                            pinMap.get(1), pinMap.get(16), pinMap.get(23), pinMap.get(8),
                            "{{w}}nc",
                            pinMap.get(2), pinMap.get(17), pinMap.get(24), pinMap.get(9),
                            "{{r}}vcc",
                            pinMap.get(3), pinMap.get(18), pinMap.get(25), pinMap.get(10),
                            "{{k}}gnd",
                            pinMap.get(4), pinMap.get(19), pinMap.get(26), pinMap.get(11),
                            "{{g}}gpio",
                            pinMap.get(5), pinMap.get(20), pinMap.get(27), pinMap.get(12),
                            "{{y}}pwm",
                            pinMap.get(6), pinMap.get(21), pinMap.get(28), pinMap.get(13),
                            "{{c}}i2c",
                            pinMap.get(7), pinMap.get(22), pinMap.get(29), pinMap.get(14),
                            "{{b}}spi",
                            pinMap.get(30), pinMap.get(15));
                    return str(rendering);
                })
                .inst(miotInstSet.MIOT_INST_TID.extend("reboot").dom(MIOT_DEVICE_TID).rng(MIOT_DEVICE_TID), lst(),
                        (lhs, inst) -> {
                            final fURI toVID = miotInstSet.deduceVID(lhs, f("+").extend(lhs.tid().name()));
                            Router.global().write(toVID.extend("status"), uri("offline"));
                            return lhs;
                        })
                .create(types, insts);
    }

}
                
