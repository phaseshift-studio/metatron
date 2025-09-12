/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class fURITest {

    static Stream<Arguments> testSegmentsData() {
        return Stream.of(
                Arguments.of("http://fhatos.org/a/b/c", List.of("a", "b", "c")),
                Arguments.of("http://fhatos.org:8080/a/b/c", List.of("a", "b", "c")),
                Arguments.of("http://fhatos.org", List.of()),
                Arguments.of("http://fhatos.org:8080", List.of()),
                Arguments.of("a/b/c", List.of("a", "b", "c")),
                Arguments.of("/a/b/c", List.of("a", "b", "c")),
                Arguments.of("a/b/c/", List.of("a", "b", "c")),
                Arguments.of("/a/b/c/", List.of("a", "b", "c")),
                Arguments.of("a/b/c?a=1&c=2", List.of("a", "b", "c")),
                Arguments.of("a/b/c/?a=3&c=4", List.of("a", "b", "c")),
                Arguments.of("/a/b/c?a=5&c=6", List.of("a", "b", "c")),
                Arguments.of("/a/b/c/?a=7&c=8", List.of("a", "b", "c")),
                Arguments.of("/mtron/int", List.of("mtron", "int")),
                Arguments.of("/mtron/int?sub", List.of("mtron", "int")),
                Arguments.of("/mtron/int?sub=noobj", List.of("mtron", "int")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fhatos.org/a/b/c",
            "http://fhatos.org:8080/a/b/c",
            "http://fhatos.org",
            "http://fhatos.org:8080",
            "a/b/c",
            "/a/b/c",
            "a/b/c/",
            "/a/b/c/",
            "a/b/c?a=1&c=2",
            "a/b/c/?a=3&c=4",
            "/a/b/c?a=5&c=6",
            "/a/b/c/?a=7&c=8",
            "/mtron/int",
            "/mtron/int?sub",
            "/mtron/int?sub=noobj",
            /*"http:// adb/dg",
            "http:// adb/dg   ",
            "   http:// adb/dg   "*/})
    public void testParse(final String f) {
        final fURI furi = fURI.of(f);
        assertEquals(f, furi.toString());
        assertEquals(furi, fURI.of(furi.toString()));
    }

    @ParameterizedTest
    @MethodSource("testSegmentsData")
    public void testSegments(final String f, final List<String> segments) {
        final fURI furi = fURI.of(f);
        assertEquals(segments, furi.segments());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/test.com?a=1&b=2&c=3|{a=1, b=2, c=3}",
            "/test.com|{}",
            "/test.com?|{}",
            "/test.com?a|{a=}",
            "/test.com?a=|{a=}",
            "/test.com?a=&b=&c=|{a=, b=, c=}",
            "/test.com?a&b&c|{a=, b=, c=}",
            "/test.com?a=a:url&b=2&c=metatron.org|{a=a:url, b=2, c=metatron.org}",
            "/test.com?a=a/b/c&b=aaa&c=0.2|{a=a/b/c, b=aaa, c=0.2}",
            "/test.com?a=a/b/c&b=http://aaa&c=0.2|{a=a/b/c, b=http://aaa, c=0.2}",
            "http://test.com?a=a/b/c&b=sss.com&c=0.2|{a=a/b/c, b=sss.com, c=0.2}",
            "/mtron/an_inst?dom=#&rng=+|{dom=#, rng=+}" },
            delimiter = '|')
    public void testQuery(final String f, final String queryMap) {
        final fURI furi = fURI.of(f);
        assertEquals(queryMap, furi.query().toString());
    }



    @Test
    public void testScheme() {
        assertEquals("http", new fURI("http://fhatos.org/b").scheme());
        assertNull(new fURI("a/b/c/d").scheme());
    }

    @Test
    public void testHostOrSegment() {
        assertEquals("fhatos.org", new fURI("http://fhatos.org/b").hostOrSegment());
        assertEquals("a", new fURI("a/b/c/d").hostOrSegment());
    }

    @Test
    public void testPrepend() {
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/b").prepend("a"));
        assertEquals(new fURI("http://fhatos.org/a/b/c/d"), new fURI("http://fhatos.org/d").prepend("a/b/c"));

    }

    @Test
    public void testExtend() {
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/a").extend("b"));
        assertEquals(new fURI("http://fhatos.org/a/b/c/d"), new fURI("http://fhatos.org/a").extend("b/c/d"));
        //assertEquals(new fURI("http://fhatos.org/a/b/d"), new fURI("http://fhatos.org/a").extend("b/./d"));

    }

    @Test
    public void testIsAbsolute() {
        assertTrue(new fURI("http://fhatos.org/a").isAbsolute());
        assertTrue(new fURI("http://fhatos.org").isAbsolute());
        assertFalse(new fURI("").isAbsolute());
        assertFalse(new fURI("a/b").isAbsolute());
        assertTrue(new fURI("/a/b").isAbsolute());
        assertTrue(new fURI("/a/+/b").isAbsolute());
        assertTrue(new fURI("/a/+/#").isAbsolute());
        assertFalse(new fURI("a/+/b").isAbsolute());
        assertFalse(new fURI("a/+/#").isAbsolute());
    }

    @Test
    public void testRetract() {
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a").retract(1));
        assertEquals(new fURI("http://fhatos.org/a"), new fURI("http://fhatos.org/a/b").retract(1));
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a/b").retract(2));
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a/b").retract(3));
        ///
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a").retract(1));
        assertEquals(new fURI("http://fhatos.org:4500/a"), new fURI("http://fhatos.org:4500/a/b").retract(1));
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a/b").retract(2));
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a/b").retract(3));
        ///
        assertEquals(new fURI("/fhatos.org/a"), new fURI("/fhatos.org/a/b").retract(1));
        assertEquals(new fURI("/fhatos.org/a"), new fURI("/fhatos.org/a/b").retract(1));
        assertEquals(new fURI("fhatos.org/a"), new fURI("fhatos.org/a/b").retract(1));
    }

    @Test
    public void testPretract() {
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a").pretract(1));
        assertEquals(new fURI("http://fhatos.org/b"), new fURI("http://fhatos.org/a/b").pretract(1));
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a/b").pretract(2));
        assertEquals(new fURI("http://fhatos.org"), new fURI("http://fhatos.org/a/b").pretract(3));
        ///
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a").pretract(1));
        assertEquals(new fURI("http://fhatos.org:4500/b"), new fURI("http://fhatos.org:4500/a/b").pretract(1));
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a/b").pretract(2));
        assertEquals(new fURI("http://fhatos.org:4500"), new fURI("http://fhatos.org:4500/a/b").pretract(3));
        ///
        assertEquals(new fURI("/a/b"), new fURI("/fhatos.org/a/b").pretract(1));
        assertEquals(new fURI("a/b"), new fURI("fhatos.org/a/b").pretract(1));
    }

    @ParameterizedTest
    @CsvSource({"http://fhatos.org/a,fhatos.org,-1",
            "http://fhatos.org:80/a,fhatos.org,80",
            "http://fhatos.org/a,fhatos.org,-1",
            "http://fhatos.org/a/b,fhatos.org,-1",
            "http://+/a/b/c,+,-1",
            "http://#/a/b/c,#,-1",
            "http://#:12/a/b/c,#,12",
            "/a/b/c,null,-1",
            "/a/b/c,null,-1",
            "/a/b/c/,null,-1",
            "a/b/c,null,-1",
            "a/b/c,null,-1",
            "b/#,null,-1",
            ",null,-1"
    })
    void testAuthority(final String furi, final String host, final int port) {
        final fURI f = fURI.of(furi);
        if (host.equals("null"))
            assertNull(f.host());
        else
            assertEquals(host, f.host());
        assertEquals(port, f.port());
    }

    @ParameterizedTest
    @CsvSource({
            "a,#,true",
            "#,#,true",
            "a,,false",
            // ",,false", should noobj match noobj?
            ///
            "http://fhatos.org/a,http://fhatos.org/a,true",
            "http://fhatos.org/a,http://fhatos.org/a/b,false",
            "http://fhatos.org/a/b,http://fhatos.org/a,false",
            "http://fhatos.org/a/b,http://fhatos.org/a/+,true",
            "http://fhatos.org/a/b,http://fhatos.org/a/#,true",
            "http://fhatos.org/a/b/c,http://fhatos.org/a/#,true",
            "http://fhatos.org/a/b/c,http://fhatos.org/a/+/c,true",
            "http://fhatos.org/a/b/c,http://fhatos.org/a/+/+,true",
            "http://fhatos.org/a/b/c,http://fhatos.org/+/+/+,true",
            "http://fhatos.org/a/b/c,http://+/a/b/c,true",
            //  "http://fhatos.org/a/b/c,http://#,true", matching on authority
            "http://fhatos.org/a/b/c,http://fhatos.org/#,true",
            "/a/b/c,/a/b/+,true",
            "/a/b/c,/a/+/c,true",
            "/a/b/c,/a/b/#,true",
            "/a/b/c,/a/#,true",
            "/a/b/c,#,true",
            "a/b/c,a/b/+,true",
            "a/b/c,a/+/c,true",
            "a/b/c,a/b/#,true",
            "a/b/c,a/#,true",
            "a/b/c,#,true",
            "b,b/#,true",
            "http://fhatos.org/a/b/c,http://fhatos.org/+/c/+,false",
            "http://fhatos.org/a/b/c,http://fhatos.org/+/b,false",
            "http://fhatos.org/a/b/c,http://fhatos.com/a/b/c,false",
            "http://fhatos.org/a/b/c,http://fhatos.org/b/#,false",
            "b,/sys/#,false",
            "/sys/#,b,false",
            "b,b/c,false",
            "b,b/c/+,false",
            "b,b/c/#,false",
            "b,b/+,false",
            "a,b/c/+,false",
            "a,b/c/#,false",
            "a/b/c,/a/b/+,false",
            "a/b/c,/a/+/c,false",
            "a/b/c,/a/b/#,false",
            "a/b/c,/a/#,false",
            "a/b/c,/#,false",
            ",#,true",
            ",+,false"
    })
    void testMatches(final String a, final String b, final boolean shouldMatch) {
        if (shouldMatch) assertTrue(fURI.of(nullToEmpty(a)).matches(fURI.of(nullToEmpty(b))));
        else assertFalse(fURI.of(nullToEmpty(a)).matches(fURI.of(nullToEmpty(b))));
    }

    private String nullToEmpty(final String s) {
        return null == s ? "" : s;
    }

    @Test
    public void testQuery() {
        assertEquals(Map.of("a", "1", "b", "2"), fURI.of("http://meta.tron/query?a=1&b=2").query());
        assertEquals(Map.of("a", "", "b", "2"), fURI.of("http://meta.tron/query?a&b=2").query());
        assertEquals(Map.of(), fURI.of("http://meta.tron/query").query());
        assertEquals(Map.of("sub", ""), fURI.of("http://meta.tron/query?sub").query());
        //  assertEquals(fURI.of("http://meta.tron/query?a=1&b=2"), fURI.of("http://meta.tron/query").query(Map.of("a", "", "b", "2")));
    }
}
