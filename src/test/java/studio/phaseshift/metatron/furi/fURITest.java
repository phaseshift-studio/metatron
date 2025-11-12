/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.f;

public class fURITest {

    private static final GraphittyLogger LOG = Graphitty.log(fURITest.class);
    private static final Logger log = LoggerFactory.getLogger(fURITest.class);

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
    @ValueSource(strings = {
            "_0",
            "http://fhatos.org/a/b/c",
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
            "http://_adb/dg",
    })
    public void testParse(final String f) {
        final fURI furi1 = fURI.of(f);
        final fURI furi2 = mParser.m_furi().parse(f).get();
        assertEquals(f, furi1.toString());
        assertEquals(f, furi2.toString());
        assertEquals(furi1, furi2);
        assertEquals(furi1, fURI.of(furi1.toString()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c         |  a/b/c",
            "./b/c         |  b/c",
            "a/./c         |  a/c",
            "a/b/.         |  a/b",
            "a/./.         |  a",
            "a/././d       |  a/d",
            "a/././d/      |  a/d/",
            // "././.      |   ",
            "a/b/..        |  a",
            "a/../..       |  ..",
            "./../../../.  |  ..",
            "./../../a     |  a",
            "a/./z/../b    | a/b",
    }, delimiter = '|')
    public void testResolve(final String f1, final String f2) {
        final fURI furi1a = fURI.of(f1);
        final fURI furi1b = fURI.of(f2);
        final fURI furi2a = mParser.m_furi().parse(f1).get();
        final fURI furi2b = mParser.m_furi().parse(f2).get();
        LOG.info("testing {{b}}%s{{/b}} {{g}}=>{{/g}} {{b}}%s{{b}} resolution", furi1a, furi2b);
        assertEquals(furi1a.resolve(), furi2b);
        assertEquals(furi2a.resolve(), furi1b);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "#                                    | false",
            "+                                    | false",
            "                                     | false",
            "A                                    | true",
            "a                                    | false",
            "ABC                                  | true",
            "/+/+/A                               | true",
            "/mtron/+/A                           | true",
            "AbC                                  | false",
            "AbC/A                                | true",
            "abc/A                                | true",
            "abc/d                                | false",
            "A/B/C                                | true",
            "A/+/C                                | true",
            "A/#                                  | true",
            "A/#{*}                               | true"
    }, delimiter = '|')
    public void testGeneric(final String f, final boolean isGeneric) {
        final fURI furi1 = fURI.of(null == f ? "" : f);
        if (null == f) {
            assertEquals(isGeneric, furi1.isGeneric());
            return;
        }
        final fURI furi2 = mParser.m_furi().parse(f).get();
        assertEquals(f, furi1.toString());
        assertEquals(f, furi2.toString());
        assertEquals(furi1, furi2);
        assertEquals(furi1, fURI.of(furi1.toString()));
        LOG.info("testing {{b}}%s{{/b}} %s generics", furi1, isGeneric ? "{{g}}for{{/g}}" : "{{r}}for no{{/r}}");
        assertEquals(isGeneric, furi1.isGeneric());
        assertEquals(isGeneric, furi2.isGeneric());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "A             |  A             | true",
            "A/b/c         |  A/B/C         | true",
            "a/b/c         |  D             | true",
            "A/B           |  A/C           | false",
            "A{+}          |  A{*}          | true",
            "A/B{2,4}      |  a/#{*}        | true",
            "A/B/C{2,4}    |  a/#{*}        | true",
            //"A/{+}         |  A/#{*}        | true",
            "A/{0}         |  A/#{2}        | false",
            "A/aB{0}       |  Z/+{0}        | true",
            "a{1}          |  A{1}          | true"
    }, delimiter = '|')
    public void testGenericMatch(final String f1, final String f2, final boolean matches) {
        final Map<fURI, fURI> generics = new HashMap<>(Map.of(f("A"), f("a"), f("B"), f("b"), f("C"), f("c"), f("D"), f("a/b/c")));
        final fURI lhs = f(f1);
        final fURI lhsResolved = lhs.resolve(generics);
        final fURI rhs = f(f2);
        final fURI rhsResolved = rhs.resolve(generics);
        final boolean resultMatch = lhsResolved.matches(rhsResolved);
        LOG.info("testing {{b}}%s{{/b}} [resolved: {{m}}%s{{/m}}] %s {{b}}%s{{/b}} [resolved: {{m}}%s{{/m}}]", lhs, lhsResolved, matches ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", rhs, rhsResolved);
        assertEquals(matches, resultMatch);
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
        final fURI furi1 = fURI.of(f);
        final fURI furi2 = mParser.m_furi().parse(f).get();
        assertEquals(queryMap, furi1.queryMap().toString());
        assertEquals(queryMap, furi2.queryMap().toString());
        //assertEquals(f, furi1.toString());
        //assertEquals(f, furi2.toString());
        assertEquals(furi1.toString(), furi2.toString());
        assertEquals(furi1, furi2);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a               | a{2}",
            "a/b/c{23}          | a/b/c{2}        | a/b/c{25}",
            "a/b/c/{2,5}        | z{4,6}          | #{6,11}",
            "a/b/c{6}?a=1&b=2   | a/+/c{4,6}?c=3  | a/b/c{10,12}?a=1&b=2&c=3",
            "a/b/c{+}?a=1&b=2   | #{*}            | a/b/c{1,}?a=1&b=2",
            "a/b/c{*}?a=1&b=2   | #{+}            | a/b/c{+}?a=1&b=2",
            "a/b/c{+}?a=1&b=2   | #{+}            | a/b/c{2,}?a=1&b=2",
            "a/b/c{+}?a=1&b=2   | #{?}            | a/b/c{+}?a=1&b=2",
            "a/b/c{?}?a=1&b=2   | #{?}            | a/b/c{0,2}?a=1&b=2",
    }, delimiter = '|')
    public void testPlus(final String f1, final String f2, final String expected) {
        final fURI furi1a = fURI.of(f1);
        final fURI furi1b = fURI.of(f2);
        final fURI furi2a = mParser.m_furi().parse(f1).get();
        final fURI furi2b = mParser.m_furi().parse(f2).get();
        //assertEquals(furi1a, furi2a); // TODO: important ssend issue
        assertEquals(furi1b, furi2b);
        if (expected.equals("ERROR")) {
            LOG.trace("testing adding {{b}}%s{{/b}} and {{b}}%s{{/b}} = {{r}}ERROR{{/r}}", furi1a, furi1b);
            assertThrows(MTronException.class, () -> LOG.error("this shouldn't work: %s", furi1a.plus(furi1b)));
            assertThrows(MTronException.class, () -> furi2a.plus(furi2b));
        } else {
            LOG.trace("testing adding {{b}}%s{{/b}} and {{b}}%s{{/b}} = {{b}}%s{{/b}}", furi1a, furi1b, furi1a.plus(furi1b));
            assertEquals(fURI.of(expected), furi1a.plus(furi1b));
            assertEquals(fURI.of(expected), furi2a.plus(furi2b));

        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a               | a/a{1}",
            "a                  | a               | a/a{1}",
            "a/b/c{23}          | a/b/c{2}        | a/b/c/a/b/c{46}",
            "a/b/c{2,5}         | z{4,6}          | a/b/c/z{8,30}",
            "a/b/c{2,5}         | z{4,6}          | a/b/c/z{8,30}",
            "a/b/c{6}?a=1&b=2   | a/+/c{4,6}?c=3  | a/b/c/a/+/c{24,36}?a=1&b=2&c=3",
            "a/b/c{+}?a=1&b=2   | #{*}            | a/b/c/#{0,}?a=1&b=2",
    }, delimiter = '|')
    public void testMult(final String f1, final String f2, final String expected) {
        final fURI furi1a = fURI.of(f1);
        final fURI furi1b = fURI.of(f2);
        final fURI furi2a = mParser.m_furi().parse(f1).get();
        final fURI furi2b = mParser.m_furi().parse(f2).get();
        assertEquals(furi1a, furi2a);
        assertEquals(furi1b, furi2b);
        if (expected.equals("ERROR")) {
            assertThrows(MTronException.class, () -> furi1a.mult(furi1b));
            assertThrows(MTronException.class, () -> furi2a.mult(furi2b));
        } else {
            assertEquals(fURI.of(expected), furi1a.mult(furi1b));
            assertEquals(fURI.of(expected), furi2a.mult(furi2b));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a{-1}",
            "a{1,1}             | a{-1}",
            "a{-1}              | a",
            "a{-1}              | a{1}",
            "a{2,3}             | a{-3,-2}",
            "a{0}               | a{0}",
            "a{,10}             | a{-10,}",
            "a{2,}              | a{,-2}",
            "a{*}               | a{,0}",
            "a{?}               | a{-1,0}",
            "a{+}               | a{,-1}",
            "a{10}              | a{-10}",
            "a{10,}             | a{,-10}"
    }, delimiter = '|')
    public void testNeg(final String f1, final String expected) {
        final fURI furi1 = fURI.of(f1);
        final fURI furi2 = mParser.m_furi().parse(f1).get();
        //assertEquals(furi1a, furi2a); // TODO: important ssend issue
        assertEquals(furi1, furi2);
        if (expected.equals("ERROR")) {
            // assertThrows(MTronException.class, () -> furi1.);
            // assertThrows(MTronException.class, () -> furi2a.plus(furi2b));
        } else {
            assertEquals(fURI.of(expected), furi1.neg());
            assertEquals(fURI.of(expected), furi2.neg());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |     1|            /a",
            "/a/b/c/                 |     1|            /a/",
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
            "/a/b/c                  |     1|            /c",
            "/a/b/c/                 |     1|            /c/",
            "a/b/c                   |     2|            b/c",
            "/a/b/c                  |     3|            /a/b/c",
            "http://x.com/a/b/c      |     3|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     2|            http://x.com/b/c",
            "http://x.com/a/b/c      |     1|            http://x.com/c",
            "http://x.com/a/b/c      |     0|            http://x.com/",
            // "http://a:b@x.com/a/b/c  |     2|            http://a:b@x.com/a/b", username password not implemented yet
    },
            delimiter = '|')
    public void testTail(final String f, final int steps, final String tail) {
        final fURI furi = f(f);
        final fURI computeTail = furi.tail(steps);
        final fURI expectedTail = f(tail);
        assertEquals(expectedTail, computeTail);
        //assertEquals(computedHead,furi.retract(furi.segments().size()-steps));
        assertEquals(furi.segments().size(), computeTail.segments().size() + (furi.segments().size() - steps));
        assertEquals(steps, computeTail.segments().size());
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
            "/a/b/c                  | ",
            "a/b/c                   | ",
            "http://x.com/a/b/c      | http",
            "mtron://lang/obj        | mtron",
            "mtron:lang/obj          | mtron",
            "./mtron:lang            | ",
            "http:m:m:m              | http",
            "m:m:m:m                 | m"
    }, delimiter = '|')
    public void testScheme(final String furi, final String scheme) {
        assertEquals(scheme, f(furi).scheme());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  ",
            "a/b/c                      |  ",
            "//x.com/a/b/c              |  x.com",
            "//x/a/b/c                  |  x",
            "//x:8080/a/b/c             |  x",
            "//x.com                    |  x.com",
            "//x                        |  x",
            "http://x.com/a/b/c         |  x.com",
            "http://x.com:80/a/b/c      |  x.com",
            "mtron://lang/obj           |  lang",
            "mtron:lang/obj             |  "
    }, delimiter = '|')
    public void testHostOrSegment(final String a, final String b) {
        assertEquals(f(a).host(), b);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  /a       | true",
            "a/b/c                      |  a        | true",
            "/a/b/c                     |  a        | true",
            "a/b/c                      |  /a       | true", // TODO: should authority-less furis check on start /?
            "//x.com/a/b/c              |  x.com    | false",
            "//x/a/b/c                  |  x        | false",
            "a/b/c/d                    |  a        | true",
            "a/b/c/d                    |  a/b/     | true",
            "a/b/c/d                    |  a/+      | true",
            "a/b/c/d                    |  a/d      | false",
            "a/b/c/d                    |  a/+/c    | true",
    }, delimiter = '|')
    public void testHasPrefix(final String a, final String b, final boolean hasPrefix) {
        assertEquals(hasPrefix, f(a).hasPrefix(f(b)));
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
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/a/b/c/d").extend(".././.."));
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/a/b/c").extend("../z/.."));
        assertEquals(new fURI("http://fhatos.org/a/b/d"), new fURI("http://fhatos.org/a").extend("b/./d"));

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
            "/a/b/c{*}?a=b&c=d           | 1   | /a/b{*}?a=b&c=d",
            "/a/b/c{2,3}?a=b&c=d         | 2   | /a{2,3}?a=b&c=d",
            // "/a/b/c/[0]?a=b&c=d          | 2   | /a/[0]?a=b&c=d",
            // "/a/b/c/{?}?a=b&c=d          | 2   | /a/{?}?a=b&c=d",
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
            "/a/b/c{*}?a=b&c=d           | 1   | /b/c{*}?a=b&c=d",
            "/a/b/c{2,3}?a=b&c=d         | 2   | /c{2,3}?a=b&c=d",
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
            "http://a/b/c{1}|1",
            "/mtron/int{1,5}|1,5",
            "http://a/b/c{*}|*",
            "/mtron/int{0}?rng=/mtron/int{23}|0",
            "/mtron/+/plus{3}?rng=/mtron/int{23}|3",
            "/mtron/+/plus{?}?rng=/mtron/int{0,23}|?"
    }, delimiter = '|')
    void testCoefficients(final String furi, final String coefficient) {
        assertEquals(coefficient, fURI.of(furi).c());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a|a|true",
            "a|+|true",
            "a|+/|false",
            "a|/+|false",
            "+|a|false",
            "|a|false",
            "#|a|false",
            "a|#|true",
            "#|#|true",
            "a||false",
            "|/|false",
            "{0}|{0}/|true",
            "{0}|a/b{*}|true",
            "/a/b{0}|/a/b{*}|true",
            "/{0}|/{*}|true",
            "||true",
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
            "abc/a/c|+/+/+|true",
            "abc/a/c|abc/+/+|true",
            "abc/a/c|abc/+/c|true",
            "abc/a/c|+/+/#|true",
            "abc/a/c|abc/+/c|true",
            "abc/a|#|true",
            "abc/a{1}|abc/a{0}|false",
            "abc/a{1}|abc/a{?}|true",
            "abc/a{1}|abc/a{*}|true",
            "abc/a{1}|#{*}|true",
            "abc/a{1}|+{*}|false",
            "abc/a{1}|abc/+{*}|true",
            "abc/a{1}|+/+{*}|true",
            "abc/a{0}|#{*}|true",
            "abc/a{0}|+{+}|false",
            "abc/a{2}|abc/a{?}|false",
            "abc/a{2}|abc/a{0,3}|true",
            "abc/a{*}|abc/a{*}|true",
            "abc/a{0}|abc/a{0}|true",
            "abc/a{1,1}|abc/a{1}|true",
            "abc/a{+}|abc/a{1,}|true",
            "abc/a{*}|abc/a{0,}|true",
            "abc/a{?}|abc/a{0,1}|true",
            "/mtron/rec|#|true",
            "/mtron/inst/plus{4}|/mtron/+/+{4}|true",
            "/mtron/inst/plus{4}|/mtron/+/+/+{4}|false",
            "/mtron/inst/plus{4}|/+/inst/+{4}|true",
            "/mtron/inst/plus|/mtron/+/plus|true",
            "/mtron/inst/plus|/mtron/+/plus{?}|true",
            "/mtron/inst/plus{1}|/mtron/#{?}|true",
            "/mtron/+/plus{1}|/mtron/#{?}|true",
            "/mtron/+/plus{1}|/mtron/+/+{?}|true",
            "/mtron/+/plus{1}|/mtron/+/#{?}|true",
            "/mtron/inst/plus|/mtron/+/plus{?}|true",
            "/mtron/inst/+{1}|/mtron/+/+{?}|true",
            "/mtron/+/+{1}|/mtron/+/+{?}|true",
            "/mtron/+/+{1}|/mtron/+/#{?}|true",
            "/mtron/inst/plus{1}|/mtron/inst/#{?}|true",
            "/mtron/inst/plus{1}|/mtron/+/#{?}|true",
            //"/mtron/inst/plus/|/mtron/+/plus/{?}|true",
            "/mtron/+/+|/mtron/+/+{?}|true",
            //"/+/+/+|/mtron/+/+{?}|true",
            "/mtron/+/plus|/mtron/+/plus{?}|true",
            "/mtron/inst/plus|/mtron/+/plus{?}|true",
            "ws://metatron.org:1234/abc|ws://metatron.org:1234/abc|true",
            "ws://metatron.org:1234/abc|ws://metatron.org:1234/#|true",
            "ws://metatron.org:1234/abc|ws://+/abc|true",
            "ws://metatron.org:1234/abc|ws://+:0/abc|true",
            "ws://metatron.org:1234/abc|ws://+:1234/abc|true",
            "ws://metatron.org:1234/abc|ws://another.org/abc|false",
            "ws://metatron.org:1234/abc|//another.org/abc|false",
            "ws://metatron.org:1234/abc|//metatron.org/abc|false",
            "ws://metatron.org:1234/abc|//metatron.org:1234/abc|false",
            "ws://metatron.org:1234/abc|http://metatron.org:1234/abc|false",
            "ws://metatron.org:1234/abc|ws://metatron.org:1234/abc|true",
            "ws://metatron.org:1234/abc|ws://metatron.org:4567/abc|false",
            "metatron.org:1234|metatron.org:4567|false",
            "metatron.org:1234|metatron.org:+|true",
            "metatron.org:1234|+:+|true",
            "ws://metatron.org:1234|ws://+:1234|true",
            "ws://metatron.org:1234|http://metatron.org:1234|false",
            "ws://metatron.org:1234|//metatron.org:1234|false",
            "ws://metatron.org:1234|metatron.org:1234|false",
            "metatron.org:1234|+:8888|false",
            "ws://metatron.org:1234|ws://metatron.org:8888|false",
            "ws://metatron.org:1234|+://+|true",
            "//metatron.org:1234|//+|true",
            "//metatron.org:1234|//+:1234|true",
            "ws://metatron.org:1234|ws://+:1234|true",
            "ws://metatron.org:1234|ws://+:5678|false",
            "ws://metatron.org:1234|http://+:5678|false",
            "ws://metatron.org:1234/abc|+://+/abc|true",
            "a/plus{4}|+/+{4}|true",
            "a/plus{4}|+/+|false",
            //"a/plus{4}|+/plus{4}|true", // TODO:?!? STRANGE!?!?
            //"/mtron/inst/plus{4}|/mtron/+/plus{4}|true" // TODO:?!? STRANGE!?!?
            "/m/lst[A,B]|/m/lst[A,B]|true",
            "xxx[A,B]|xxx[#,+]|true",
            "xxx[ab,cd]|xxx[ab,cd{?}]|true",
            "xxx[ab,cd]|xxx[ab{*},cd{?}]{?}|true",
            "xxx[ab,cd{0}]|xxx[ab{*},cd{+}]{?}|false",
            "xxx[ab,cd{0}]|xxx[ab{2},cd{0}]{?}|false",
            "xxx[ab,cd]|xxx[ab{*},cd]{+}|true",
            "/m/lst[ab,cd]|/m/lst[ab{*},cd]{+}|true",
            "xxx[ab{2},cd{0}]|xxx[ab{1,3},cd{0}]{1,5}|true",
            "xxx[ab{2},cd{1,3}]{2,3}|xxx[ab{1,3},cd{0,100}]{1,5}|true",
            "xxx[ab{2},cd{1,3}]{2,3}|xxx[ab{1,3},cd{0,2}]{1,5}|false",
            "http://localhost:8080/abc|http://#|true",
            "http://localhost:8080/abc|http://+:8081/+|false",
            "http://localhost:8080/abc|http://+:8080/+|true",
            "http://localhost:8080/abc|http://+:8081|false",
            "http://localhost:8080/abc|http://+:8080|false",
            "http://localhost:8080/abc|http://+/+|true",
            "http://localhost:8080/abc|http://+/abc|true",
            "http://localhost:8080/abc|http://+/xyz|false",
            "http://localhost:8080/abc|http://localhost:8081|false",
            "http://localhost:8080/abc|http://localhost:8080/#|true",
            "http://localhost:8080/abc|//localhost:8080/#|false",
            "/shared|http://#|false",
            "http://localhost:8080|http://#|true",
            "http://localhost:8080/|http://#|true",
            "http://localhost:8080/abc|http://+/abc|true",
            "http://localhost:8080|http://+/abc|false"
    }, delimiter = '|')
    void testMatches(final String a, final String b, final boolean shouldMatch) {
        final fURI furi1a = fURI.of(nullToEmpty(a));
        final fURI furi1b = fURI.of(nullToEmpty(b));
        final boolean doObjParser = null != a && null != b && !a.equals("{0}");
        final fURI furi2a = doObjParser ? mParser.m_furi().parse(nullToEmpty(a)).get() : fURI.of(nullToEmpty(a));
        final fURI furi2b = doObjParser ? mParser.m_furi().parse(nullToEmpty(b)).get() : fURI.of(nullToEmpty(b));
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
        assertEquals(furi1a, furi2a);
        if (shouldMatch) {
            assertTrue(furi1a.matches(furi1b));
            assertTrue(furi2a.matches(furi2b));
            assertTrue(furi1a.matches(furi2b));
            assertTrue(furi2a.matches(furi1b));
        } else {
            assertFalse(furi1a.matches(furi1b));
            assertFalse(furi2a.matches(furi2b));
            assertFalse(furi1a.matches(furi2b));
            assertFalse(furi2a.matches(furi1b));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                 |+                 |[a]",
            "a/b               |+/b               |[a]",
            "/a/b              |/+/b              |[a]",
            "a/b/c             |+/b/+             |[a, c]",
            "a/b/c             |a/#               |[b, c]",
            "a/b/c             |#                 |[a, b, c]",
            "a/b/c             |+/#               |[a, b, c]",
            "a/b/c             |+/+/+             |[a, b, c]",
            "a/b/c             |a/b/c             |[]",
            "a/b/c             |+                 |[]",
            "a/b/c             |+/b/d             |[]",
            "+/b/c             |+/b/c             |[+]",
            "+/#               |+/b/c             |[]",
            "+/#               |+/+/+             |[+, #]",
            "+/b/c             |+/b/+             |[+, c]",
    }, delimiter = '|')
    void testSelect(final String a, final String b, final String matches) {
        final fURI furi1a = fURI.of(nullToEmpty(a));
        final fURI furi1b = fURI.of(nullToEmpty(b));
        final boolean doObjParser = null != a && null != b && !a.equals("[0]");
        final fURI furi2a = doObjParser ? mParser.m_furi().parse(nullToEmpty(a)).get() : fURI.of(nullToEmpty(a));
        final fURI furi2b = doObjParser ? mParser.m_furi().parse(nullToEmpty(b)).get() : fURI.of(nullToEmpty(b));
        LOG.trace("testing: {{b}}%s{{/b}} selects %s from {{b}}%s{{/b}}", furi2a, matches, furi1a);
        assertEquals(furi1a, furi2a);
        assertEquals(matches, furi1a.select(furi1b).toString());
        assertEquals(matches, furi2a.select(furi2b).toString());
        assertEquals(matches, furi1a.select(furi2b).toString());
        assertEquals(matches, furi2a.select(furi1b).toString());
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

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c[int,int]{2}  |2               |[int, int]",
            "a/b/c[A{2},B{3}]   |                |[A{2}, B{3}]"
    }, delimiter = '|')
    void testPoly(final String furi, final String c, final String typeParams) {
        final fURI furiA = f(furi);
        assertEquals(c, furiA.c());
        assertEquals(typeParams, furiA.poly().toString());
    }

}
