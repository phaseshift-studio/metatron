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

package studio.phaseshift.metatron.isa.iot.miot.space;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.*;
import studio.phaseshift.metatron.furi.q.SubQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.iot.MoquetteServer;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBSCRIPTION_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.MIOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ExtendWith(SkipInheritedTestsExtension.class)
@SkipInheritedTests(tags = {
        TestTag.CRUD,        // Skip all CRUD tests
        TestTag.BOUNDARY,    // Skip all boundary value tests
        TestTag.TYPE,        // Skip all type preservation tests
        TestTag.NESTED,      // Skip all nested structure tests
        TestTag.LIST,        // Skip all list handling tests
        TestTag.SPECIAL      // Skip all special value tests
}, include = {
       // "testMonoReadWrite"  // Include this CRUD test even though CRUD tag is skipped
})
public class miotSpaceTest extends AbstractSpaceTest implements SubQTest {

    private static final int PORT = generatePort();

    public miotSpaceTest() {
        super(() -> {
            try {
                return miotSpace.of(rec(
                        uri(HOST), uri("mqtt://127.0.0.1:" + PORT),
                        uri(PATTERN), uri("/t/#"),
                        uri(REWRITE), rel(uri("/t"), uri("/t"))), f("/sys/router/space/t"));
                //space.directWriter().apply(f("#"), noobj());
            } catch (Exception e) {
                throw MTronException.of(e);
            }
        });
        BootLoader.loadInstSetProvider(IOT_ISA_TID);
        BootLoader.loadInstSetProvider(MIOT_ISA_TID);
    }
    
    @BeforeAll
    public static void setupAll() {
        AbstractMetatronTest.begin();
        MoquetteServer.run(PORT);
    }

    @AfterAll
    public static void stopAll() {
        MoquetteServer.clear();
        MoquetteServer.stop();
        AbstractMetatronTest.end();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/t/a?subq -> sub::[target=>/t/a,on_recv=><abc>->3]                            % /t/a -> 4                       % *abc.?=3",
            "/t/b?subq -> sub::[target=>/t/b,on_recv=><abc>->4]                            % /t/b -> 3                       % *abc.?=4",
    }, delimiter = '%')
    public void testSubscriptions(final String subscription, final String write, final String check) {
        final Rec sub = mParser.eval(subscription);
        assertEquals(SUBSCRIPTION_TID, sub.tid());
        final Obj writeObj = mParser.eval(write);
        final Obj checkObj = mParser.eval(check);
        assertNotEquals(noobj(), checkObj);
    }
}
