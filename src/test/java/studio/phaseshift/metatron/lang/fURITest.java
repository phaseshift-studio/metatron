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
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.lang.fURI.f;

public class fURITest {

    private static final GraphittyLogger LOG = Graphitty.log(fURITest.class);

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
            "http:// adb/dg",
            "http:// adb/dg   ",
            "   http:// adb/dg   "
    })
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
            "/mtron/an_inst?dom=#&rng=+|{dom=#, rng=+}"},
            delimiter = '|')
    public void testQueryRead(final String f, final String queryMap) {
        final fURI furi = fURI.of(f);
        assertEquals(queryMap, furi.queryMap().toString());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a               | a[2]",
            "a/b/c[23]          | a/b/c[2]        | a/b/c[25]",
            "a/b/c/[2,5]        | z[4,6]          | ERROR",
            "a/b/c[6]?a=1&b=2   | a/+/c[4,6]?c=3  | a/b/c[10,12]?a=1&b=2&c=3",
            "a/b/c[+]?a=1&b=2   | #[*]            | a/b/c[1,]?a=1&b=2",
            "a/b/c[*]?a=1&b=2   | #[+]            | a/b/c[+]?a=1&b=2",
            "a/b/c[+]?a=1&b=2   | #[+]            | a/b/c[2,]?a=1&b=2",
            "a/b/c[+]?a=1&b=2   | #[?]            | a/b/c[+]?a=1&b=2",
            "a/b/c[?]?a=1&b=2   | #[?]            | a/b/c[0,2]?a=1&b=2",
    }, delimiter = '|')
    public void testPlus(final String f1, final String f2, final String expected) {
        final fURI furi1 = fURI.of(f1);
        final fURI furi2 = fURI.of(f2);
        if (expected.equals("ERROR")) {
            assertThrows(MTronException.class, () -> furi1.plus(furi2));
        } else
            assertEquals(fURI.of(expected), furi1.plus(furi2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a               | a/a[1]",
            "a                  | a               | a/a[1]",
            "a/b/c[23]          | a/b/c[2]        | a/b/c/a/b/c[46]",
            "a/b/c[2,5]         | z[4,6]          | a/b/c/z[8,30]",
            "a/b/c[2,5]         | z[4,6]          | a/b/c/z[8,30]",
            "a/b/c[6]?a=1&b=2   | a/+/c[4,6]?c=3  | a/b/c/a/+/c[24,36]?a=1&b=2&c=3",
            "a/b/c[+]?a=1&b=2   | #[*]            | a/b/c/#[0,]?a=1&b=2",
    }, delimiter = '|')
    public void testMult(final String f1, final String f2, final String expected) {
        final fURI furi1 = fURI.of(f1);
        final fURI furi2 = fURI.of(f2);
        if (expected.equals("ERROR")) {
            assertThrows(MTronException.class, () -> furi1.mult(furi2));
        } else
            assertEquals(fURI.of(expected), furi1.mult(furi2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |     1|            /a",
            "a/b/c                   |     2|            a/b",
            "/a/b/c                  |     3|            /a/b/c",
            "http://x.com/a/b/c      |     3|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     2|            http://x.com/a/b",
            "http://x.com/a/b/c      |     1|            http://x.com/a",
            "http://x.com/a/b/c      |     0|            http://x.com/",
            // "http://a:b@x.com/a/b/c  |     2|            http://a:b@x.com/a/b", username password not implemented yet
    },
            delimiter = '|')
    public void testHead(final String f, final int steps, final String head) {
        final fURI furi = f(f);
        final fURI computedHead = furi.head(steps);
        final fURI expectedHead = f(head);
        assertEquals(expectedHead, computedHead);
        //assertEquals(computedHead,furi.retract(furi.segments().size()-steps));
        assertEquals(furi.segments().size(), computedHead.segments().size() + (furi.segments().size() - steps));
        assertEquals(steps, computedHead.segments().size());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/test.com?a=1&b=2|/test.com|a|1|b|2"},
            delimiter = '|')
    public void testQueryWrite(final String expected, final String base, final String k1, final String v1, final String k2, final String v2) {
        final fURI expectedfURI = fURI.of(expected);
        final fURI resultfURI = fURI.of(base).query(k1, v1).query(k2, v2);
        assertEquals(expectedfURI, resultfURI);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |  ",
            "a/b/c                   |  ",
            "http://x.com/a/b/c      |  http",
            "mtron://lang/obj        |  mtron",
            "mtron:lang/obj          |  mtron"
    }, delimiter = '|')
    public void testScheme(final String furi, final String scheme) {
        assertEquals(scheme, f(furi).scheme());
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

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a         | 1   | http://fhatos.org",
            //    "http://fhatos.org/a/         | 1   | http://fhatos.org/",
            "http://fhatos.org/a/b       | 1   | http://fhatos.org/a",
            "http://fhatos.org/a/b/      | 1   | http://fhatos.org/a/",
            "http://fhatos.org/a/b       | 2   | http://fhatos.org",
            "http://fhatos.org/a/b       | 3   | http://fhatos.org",
            "http://fhatos.org:81/a      | 1   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b    | 1   | http://fhatos.org:81/a",
            "http://fhatos.org:81/a/b    | 2   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b    | 3   | http://fhatos.org:81",
            "/fhat.org/a/b               | 1   | /fhat.org/a",
            "fhat.org/a/b                | 1   | fhat.org/a",
            "fhat.org/a/b                | 3   | ",
            "/a/b/c?a=b&c=d              | 1   | /a/b?a=b&c=d",
            "/a/b/c?a=b&c=d              | 2   | /a?a=b&c=d",
            "/a/b/c/?a=b&c=d             | 2   | /a/?a=b&c=d",
            "/a/b/c[*]?a=b&c=d           | 1   | /a/b[*]?a=b&c=d",
            "/a/b/c[2,3]?a=b&c=d         | 2   | /a[2,3]?a=b&c=d",
            // "/a/b/c/[0]?a=b&c=d          | 2   | /a/[0]?a=b&c=d",
            // "/a/b/c/[?]?a=b&c=d          | 2   | /a/[?]?a=b&c=d",
            // "/a/b?a=b&c=d                | 2   | ?a=b&c=d",
    }, delimiter = '|')
    public void testRetract(final String furi, final int steps, final String expected) {
        final fURI start = fURI.of(furi);
        final fURI end = fURI.of(expected);
        assertEquals(end, start.retract(steps));
        LOG.debug("testing %s retracted %d steps is %s", start, steps, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a         | 1   | http://fhatos.org",
            //    "http://fhatos.org/a/         | 1   | http://fhatos.org/",
            "http://fhatos.org/a/b       | 1   | http://fhatos.org/b",
            "http://fhatos.org/a/b/      | 1   | http://fhatos.org/b/",
            "http://fhatos.org/a/b       | 2   | http://fhatos.org",
            "http://fhatos.org/a/b       | 3   | http://fhatos.org",
            "http://fhatos.org:81/a      | 1   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b    | 1   | http://fhatos.org:81/b",
            "http://fhatos.org:81/a/b    | 2   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b    | 3   | http://fhatos.org:81",
            "/fhat.org/a/b               | 1   | /a/b",
            "fhat.org/a/b                | 1   | a/b",
            "/a/b/c?a=b&c=d              | 1   | /b/c?a=b&c=d",
            "/a/b/c?a=b&c=d              | 2   | /c?a=b&c=d",
            "/a/b/c/?a=b&c=d             | 2   | /c/?a=b&c=d",
            "/a/b/c[*]?a=b&c=d           | 1   | /b/c[*]?a=b&c=d",
            "/a/b/c[2,3]?a=b&c=d         | 2   | /c[2,3]?a=b&c=d",
    }, delimiter = '|')
    public void testPretract(final String furi, final int steps, final String expected) {
        final fURI start = fURI.of(furi);
        final fURI end = fURI.of(expected);
        assertEquals(end, start.pretract(steps));
        LOG.debug("testing %s pretracted %d steps is %s", start, steps, expected);
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
    @CsvSource(value = {
            "http://a/b/c[1]|1",
            "/mtron/int[1,5]|1,5",
            "http://a/b/c[*]|*",
            "/mtron/int[0]?rng=/mtron/int[23]|0"
    }, delimiter = '|')
    void testCoefficients(final String furi, final String coefficient) {
        assertEquals(coefficient, fURI.of(furi).coefficient());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a|a|true",
            "a|+|true",
            "a|+/|true", // TODO: should we match on ssend? (and sstart?)
            "a|/+|false",
            "+|a|false",
            "|a|false",
            "#|a|false",
            "a|#|true",
            "#|#|true",
            "a||false",
            "|/|false",
            // "[0]|[0]/|true", TODO: this should match. both are noobj?!
            // ",|false", // should noobj match noobj?
            ///
            "http://fhatos.org/a|http://fhatos.org/a|true",
            "http://fhatos.org/a|http://fhatos.org/a/b|false",
            "http://fhatos.org/a/b|http://fhatos.org/a|false",
            "http://fhatos.org/a/b|http://fhatos.org/a/+|true",
            "http://fhatos.org/a/b|http://fhatos.org/a/#|true",
            "http://fhatos.org/a/b/c|http://fhatos.org/a/#|true",
            "http://fhatos.org/a/b/c|http://fhatos.org/a/+/c|true",
            "http://fhatos.org/a/b/c|http://fhatos.org/a/+/+|true",
            "http://fhatos.org/a/b/c|http://fhatos.org/+/+/+|true",
            "http://fhatos.org/a/b/c|http://+/a/b/c|true",
            //  "http://fhatos.org/a/b/c|http://#|true", matching on authority
            "http://fhatos.org/a/b/c|http://fhatos.org/#|true",
            "/a/b/c|/a/b/+|true",
            "/a/b/c|/a/+/c|true",
            "/a/b/c|/a/b/#|true",
            "/a/b/c|/a/#|true",
            "/a/b/c|#|true",
            "a/b/c|a/b/+|true",
            "a/b/c|a/+/c|true",
            "a/b/c|a/b/#|true",
            "a/b/c|a/#|true",
            "a/b/c|#|true",
            "b|b/#|true",
            "http://fhatos.org/a/b/c|http://fhatos.org/+/c/+|false",
            "http://fhatos.org/a/b/c|http://fhatos.org/+/b|false",
            "http://fhatos.org/a/b/c|http://fhatos.com/a/b/c|false",
            "http://fhatos.org/a/b/c|http://fhatos.org/b/#|false",
            "b|/sys/#|false",
            "/sys/#|b|false",
            "b|b/c|false",
            "b|b/c/+|false",
            "b|b/c/#|false",
            "b|b/+|false",
            "a|b/c/+|false",
            "a|b/c/#|false",
            "a/b/c|/a/b/+|false",
            "a/b/c|/a/+/c|false",
            "a/b/c|/a/b/#|false",
            "a/b/c|/a/#|false",
            "a/b/c|/#|false",
            "|#|true",
            "|+|false",
            "abc|+|true",
            "abc/a|+|false",
            "abc/a|+/+|true",
            "abc/a/c|+/+|false",
            "abc/a/c|+/+/#|true",
            "abc/a/c|abc/+/c|true",
            "abc/a|#|true",
            /// ///
            "abc/a[1]|abc/a[0]|false",
            "abc/a[1]|abc/a[?]|true",
            "abc/a[1]|abc/a[*]|true",
            "abc/a[1]|#[*]|true",
            "abc/a[1]|+[*]|false",
            "abc/a[1]|abc/+[*]|true",
            "abc/a[1]|+/+[*]|true",
            "abc/a[0]|#[*]|true",
            "abc/a[0]|+[+]|false",
            "abc/a[2]|abc/a[?]|false",
            "abc/a[2]|abc/a[0,3]|true",
            "abc/a[*]|abc/a[*]|true",
            "abc/a[0]|abc/a[0]|true",
            "abc/a[1,1]|abc/a[1]|true",
            "abc/a[+]|abc/a[1,]|true",
            "abc/a[*]|abc/a[0,]|true",
            "abc/a[?]|abc/a[0,1]|true",
            "/mtron/rec|#|true",
    }, delimiter = '|')
    void testMatches(final String a, final String b, final boolean shouldMatch) {
        if (shouldMatch) assertTrue(fURI.of(nullToEmpty(a)).matches(fURI.of(nullToEmpty(b))));
        else assertFalse(fURI.of(nullToEmpty(a)).matches(fURI.of(nullToEmpty(b))));
    }

    private String nullToEmpty(final String s) {
        return null == s ? "" : s;
    }

    @Test
    public void testQueryRead() {
        assertEquals(Map.of("a", "1", "b", "2"), fURI.of("http://meta.tron/query?a=1&b=2").queryMap());
        assertEquals(Map.of("a", "", "b", "2"), fURI.of("http://meta.tron/query?a&b=2").queryMap());
        assertEquals(Map.of(), fURI.of("http://meta.tron/query").queryMap());
        assertEquals(Map.of("sub", ""), fURI.of("http://meta.tron/query?sub").queryMap());
        //  assertEquals(fURI.of("http://meta.tron/query?a=1&b=2"), fURI.of("http://meta.tron/query").query(Map.of("a", "", "b", "2")));
    }
}
