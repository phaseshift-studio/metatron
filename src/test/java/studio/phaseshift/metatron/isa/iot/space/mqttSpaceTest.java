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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TestSkip;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.iot.MoquetteServer;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.PubSubQ.SUBSCRIPTION_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
//@TestSkip(testClass = AbstractSpaceTest.class, testMethods = {"testMonoReadWrite"})
public class mqttSpaceTest extends AbstractSpaceTest {
    private static final int PORT = generatePort();

    public mqttSpaceTest() {
        super(() -> {
            try {
                return mqttSpace.of(rec(
                        uri(HOST), uri("mqtt://127.0.0.1:" + PORT),
                        uri(PATTERN), uri("/t/#"),
                        uri(REWRITE), rel(uri("/t"), uri("/t"))), f("/sys/router/space/t"));
                //space.directWriter().apply(f("#"), noobj());
            } catch (Exception e) {
                throw MTronException.of(e);
            }
        });
        this.sleepBetweenReads = 20;
        BootLoader.loadInstSetProvider(IOT_ISA_TID);
    }

    @BeforeAll
    public static void setupAll() {
        AbstractMetatronTest.begin();
        MoquetteServer.run(PORT);
    }

    @AfterAll
    public static void stopAll() {
        MoquetteServer.stop();
        CommonUtil.sleepThread(100);
        AbstractMetatronTest.end();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/t/a?sub -> sub::[src=>a,tgt=>/t/a,on_recv=><abc>->3]                                        % /t/a -> 4                           % *abc.?=3",
            "/t/b?sub -> sub::[src=>a,tgt=>/t/b,on_recv=><abc>->4]                                        % /t/b -> 3                           % *abc.?=4",
            "/t/c/+?sub -> sub::[src=>a,tgt=>/t/+,on_recv=>(){*<zzz>.else(0).plus(4).to(zzz).print(_)}]   % 3.to(/t/c/b).map(7).to(/t/c/a)      % *zzz.?=8",
          //  "/t/c?sub -> sub::[src=>a,tgt=>/t/c,on_recv=>(){*</t/c>.plus(4).to(yyy).print(_)}]            % 3.to(/t/c)                          % *yyy.?=7",
            "/t/d?sub -> sub::[src=>a,tgt=>/t/d,on_recv=>(){*</t/d>.plus(4).to(ggg).print(_)}]            % 1.to(ggg).map(3).to(/t/c)           % *ggg.?=1",
    }, delimiter = '%')
    public void testSubscriptions(final String subscription, final String write, final String check) {
        final PubSubQ.Subscription sub = mParser.eval(subscription);
        CommonUtil.sleepThread(this.sleepBetweenReads+50);
        assertEquals(SUBSCRIPTION_TID, sub.tid());
        mParser.eval(write);
        CommonUtil.sleepThread(this.sleepBetweenReads+50);
        final Obj checkObj = mParser.eval(check);
        assertNotEquals(noobj(), checkObj);
        MoquetteServer.clear();
    }

}
