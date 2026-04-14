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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.SkipInheritedTests;
import studio.phaseshift.metatron.SkipInheritedTestsExtension;
import studio.phaseshift.metatron.TestTag;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
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
    private static final String BASE_URL = "http://localhost:8666";

    public httpSpaceTest() {
        super(f("/sys/space/web"), () -> httpSpace.of(rec(
                uri(HOST), uri(BASE_URL),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/"), uri("src/test/resources/web"))), f("/sys/space/web")));
        InstSet.loadInstSetProvider(WEB_ISA_TID.extend("#"));
    }

    @Override
    public fURI getTestDataUriPrefix() {
        return f(BASE_URL + "/test/");
    }


    @ParameterizedTest
    @CsvSource(value = {
           // "/|notnoobj",
           // "/index.html|notnoobj",
            "/index.html/html/body/a/b/c/text|\"a1.b1.c1.text\"",
            "/index.html/html/body/div/div/div/text|\"a2.b2.c2.text\"",
            "/html/body/a/b/c/text|\"a1.b1.c1.text\"",
            "/html/body/div/div/div/text|\"a2.b2.c2.text\"",
            "/test.json/hello|world",
            "/test.txt|\"This is a plain text file for httpSpaceTest.\"",
            "/missing.html|noobj"
    }, delimiter = '|')
    public void testResourceAccess(final String path, final String expected) {
        String url = BASE_URL + path;
        var result = Router.readFromSpace(url);
        assertEquals(ObjmtronSerializer.parse(expected), result, "Unexpected resource content for: " + url);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Top-level fields
            "/test.json/hello|world",
            "/test.json/number|42",
            "/test.json/active|true",
            "/test.json/nothing|noobj",

            // Nested object fields
            "/test.json/meta/created|<2024-06-01T12:00:00Z>",
            "/test.json/meta/details/score|99.5",
            "/test.json/meta/details/valid|false",

            // Arrays
            "/test.json/items/0|1",
            "/test.json/items/1|two",
            "/test.json/items/2|false",
            "/test.json/items/3|noobj",
            "/test.json/items/4/deep|value",
            "/test.json/meta/tags/0|alpha",
            "/test.json/meta/tags/1|beta"
    }, delimiter = '|')
    public void testJsonResourceFields(String path, String expected) {
        String url = BASE_URL + path;
        var result = Router.readFromSpace(url);
        assertEquals(ObjmtronSerializer.parse(expected), result, "Unexpected value for: " + url);
    }
    
    @Test
    public void testIndexHTMLRedirect() {
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/index.html"));
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/"));
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL));
    }

    @Test
    public void testServerSideRecursion() {
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/#/"));
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/index.html"));
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace(BASE_URL + "/index.html/html/body/a/b/c/text"));
        assertEquals(str("a1.b1.c1.text").c(cInt.of(2)), Router.readFromSpace(BASE_URL + "/index.html/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace(BASE_URL + "/index.html/html/body/div/div/div/text"));
        assertEquals(str("a2.b2.c2.text").c(cInt.of(2)), Router.readFromSpace(BASE_URL + "/index.html/html/body/div/+/+/text"));
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace(BASE_URL + "/html/body/a/b/c/text"));
        assertEquals(str("a1.b1.c1.text").c(cInt.of(2)), Router.readFromSpace(BASE_URL + "/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace(BASE_URL + "/html/body/div/div/div/text"));
        assertEquals(str("a2.b2.c2.text").c(cInt.of(2)), Router.readFromSpace(BASE_URL + "/html/body/div/+/+/text"));
    }

}
