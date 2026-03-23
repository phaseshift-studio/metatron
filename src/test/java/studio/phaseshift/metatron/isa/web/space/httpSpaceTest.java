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

package studio.phaseshift.metatron.isa.web.space;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.SkipInheritedTests;
import studio.phaseshift.metatron.SkipInheritedTestsExtension;
import studio.phaseshift.metatron.TestTag;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.httpSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
@ExtendWith(SkipInheritedTestsExtension.class)
@SkipInheritedTests(tags = {
        TestTag.CRUD,        // Skip all CRUD tests
        TestTag.BOUNDARY,    // Skip all boundary value tests
        TestTag.TYPE,        // Skip all type preservation tests
        TestTag.NESTED,      // Skip all nested structure tests
        TestTag.LIST,        // Skip all list handling tests
        TestTag.SPECIAL      // Skip all special value tests
}, include = {
        "testMonoReadWrite"  // Include this CRUD test even though CRUD tag is skipped
})
public class httpSpaceTest extends AbstractSpaceTest {

    public httpSpaceTest() {
        super(f("/sys/space/web"), () -> httpSpace.of(rec(
                uri(HOST), uri("http://localhost:8777"),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/"), uri("src/test/resources/web"))), f("/sys/space/web")));
        BootLoader.loadInstSetProvider(WEB_ISA_TID.extend("#"));
    }

    @Override
    protected String getTestDataUriPrefix() {
        return "http://localhost:8777/test/";
    }

    @Test
    public void testIndexHTMLRedirect() {
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/index.html"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777"));
    }

    @Test
    public void testServerSideRecursion() {
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/#/"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/index.html"));
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/a/b/c/text"));
        // assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/div/div/div/text"));
        //  assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/div/+/+/text"));
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/html/body/a/b/c/text"));
        //   assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/html/body/div/div/div/text"));
        //   assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/html/body/div/+/+/text"));
    }

}
