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

package studio.phaseshift.metatron.isa.iot.space;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.furi.q.SubQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.iot.MoquetteServer;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBSCRIPTION_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * mqttSpace has different behavior (pub/sub messaging).
 * Excludes abstract tests as they don't apply to MQTT's pub/sub model.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mqttSpaceTest extends AbstractSpaceTest implements SubQTest {
    private static final int PORT = generatePort();

    public mqttSpaceTest() {
        super(() -> {
            try {
                return mqttSpace.of(rec(
                        uri(QPROC), lst(QCollection.subq()),
                        uri(HOST), uri("mqtt://127.0.0.1:" + PORT),
                        uri(PATTERN), uri("/t/#"),
                        uri(SERIALIZER), ObjmtronSerializer.single(), // USING MTRON SERIALIZER (JSON SERIALIZER ISN'T ONE-TO-ONE WITH TEST EXPECTATION TYPES)
                        uri(REWRITE), rel(uri("/t"), uri("/t"))), f("/sys/router/space/t"));
                //space.directWriter().apply(f("#"), noobj());
            } catch (Exception e) {
                throw MTronException.of(e);
            }
        });
        this.sleepBetweenReads = 20;
    }

    @BeforeAll
    public static void setupAll() {
        InstSet.importInstSet(IOT_ISA_TID);
        MoquetteServer.run(PORT);
    }

    @AfterAll
    public static void stopAll() {
        MoquetteServer.clear();
        MoquetteServer.stop();
        CommonUtil.sleepThread(1000);
        AbstractMetatronTest.end();
    }

    @Override
    @Disabled
    public void testMonoUpdate() {
    }

    @Override
    @Disabled
    public void testSequentialUpdates(int iterations) {
    }

    @Override
    @Disabled
    public void testBasicCRUD(String description, String key, String valueStr) {
    }

    @Override
    @Disabled
    public void testTypeChanges(String description, Obj initialValue, Obj updatedValue) {
    }

    @Override
    @Disabled
    public void testMultiFieldUpdates(int fieldCount) {
    }

}
