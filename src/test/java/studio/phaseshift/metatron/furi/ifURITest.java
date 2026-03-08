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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.fURI.Singleton.parseQuery;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ifURITest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a         | 1   | http://fhatos.org",
            "http://fhatos.org/a/        | 2   | http://fhatos.org/",
            "http://fhatos.org/a/b       | 2   | http://fhatos.org",
            "http://fhatos.org/a/b/      | 2   | http://fhatos.org/",
            "http://fhatos.org/a/b       | 2   | http://fhatos.org",
            "http://fhatos.org/a/b       | 3   | http://fhatos.org",
            "http://fhatos.org:81/a      | 1   | http://fhatos.org:81",
            "http://fhatos.org:81/a/     | 2   | http://fhatos.org:81/",
            "http://fhatos.org:81/a      | 2   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b/   | 1   | http://fhatos.org:81/a/",
            "http://fhatos.org:81/a/b    | 1   | http://fhatos.org:81/a",
            "http://fhatos.org:81/a/b    | 2   | http://fhatos.org:81",
            "http://fhatos.org:81/a/b/   | 2   | http://fhatos.org:81/",
            "http://fhatos.org:81/a/b    | 3   | http://fhatos.org:81",
            "/fhat.org/a/b               | 1   | /fhat.org/a",
            "fhat.org/a/b                | 1   | fhat.org/a",
            "fhat.org/a/b                | 3   | null",
            "/a/b/c?a=b&c=d              | 1   | /a/b?a=b&c=d",
            "/a/b/c?a=b&c=d              | 2   | /a?a=b&c=d",
            "./a/./././././?a=b&c=d      | 5   | ./a/?a=b&c=d",
            "/a//b//c?a=b&c=d            | 2   | /a//b?a=b&c=d",
            "/a/b/c/?a=b&c=d             | 3   | /?a=b&c=d",
            "/a/b/c{*}?a=b&c=d           | 1   | /a/b{*}?a=b&c=d",
            "/a/b/c{2,3}?a=b&c=d         | 2   | /a{2,3}?a=b&c=d",
            "a/b/c{2,3}?a=b&c=d          | 2   | a{2,3}?a=b&c=d",
            ".//a/b/c{2,3}?a=b&c=d       | 2   | .//a{2,3}?a=b&c=d",
            // "/a/b/c/[0]?a=b&c=d          | 2   | /a/[0]?a=b&c=d",
            // "/a/b/c/{?}?a=b&c=d          | 2   | /a/{?}?a=b&c=d",
            // "/a/b?a=b&c=d                | 2   | ?a=b&c=d",
    }, delimiter = '|', nullValues = "null")
    public void testRetract(final String furi, final int steps, final String expected) {
        final fURI start = f(furi);
        final fURI end = f(expected);
        assertEquals(end, start.retract(steps), printComponents(start.retract(steps)) + printComponents(end));
        LOG.error("testing %s retracted %d steps is %s", start, steps, expected);
        printComponents(start);
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
            "/a/b/c?a=b&c=d              | 2   | /b/c?a=b&c=d",
            "/a/b/c/?a=b&c=d             | 2   | /b/c/?a=b&c=d",
            "/a/b/c{*}?a=b&c=d           | 1   | /b/c{*}?a=b&c=d",
            "/a/b/c{2,3}?a=b&c=d         | 2   | /b/c{2,3}?a=b&c=d",
    }, delimiter = '|')
    public void testPretract(final String furi, final int steps, final String expected) {
        final fURI start = f(furi);
        final fURI end = f(expected);
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
        final fURI f = f(furi);
        if (host.equals("null"))
            assertNull(f.host());
        else
            assertEquals(host, f.host());
        assertEquals(port, f.port());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a     | false | false",
            "http://fhatos.org:80/a/ | false | true",
            "http://fhatos.org/      | false | true",
            "http://fhatos.org/a/b   | false | false",
            "+/a/b/c/                | true  | true",
            "http://#/a/b/c          | false | false",
            "http://#:29/a/b/c       | false | false",
            "/a/b/c/                 | false | true"
    }, delimiter = '|')
    void testSlashes(final String furi, final boolean isRelative, final boolean isBranch) {
        final fURI f = f(furi);
        assertEquals(isRelative, f.isRelative());
        assertEquals(isBranch, f.isBranch());
        assertEquals(!isRelative, f.isAbsolute());
        assertEquals(!isBranch, f.isNode());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/    | /",
            "http://fhatos.org/a     | http://fhatos.org/a",
            "http://fhatos.org:80/a/ | http://fhatos.org:80/a/",
            "http://fhatos.org/      | http://fhatos.org",
            "http://fhatos.org/a/b   | http://fhatos.org/a/b",
            "+/a/b/c/                | +/a/b/c/",
            "http://#/a/b/c          | http://#/a/b/c",
            "http://#:29/a/b/c       | http://#:29/a/b/c",
            "/a/b/c/                 | /a/b/c/"
    }, delimiter = '|')
    void testString(final String furi, final String furiString) {
        final fURI f = f(furi);
        assertEquals(furiString, f.toString());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "int     | /m/int",
            "int{3}  | /m/int{3}",
            "str{+}  | /m/str{1,}",
            "plus    | /m/inst/plus",
            "plus?int<=int | /m/inst/plus?/m/int<=/m/int"
    }, delimiter = '|')
    void testBig(final String furi, final String fURIBig) {
        final fURI f = f(furi);
        assertEquals(f(fURIBig), f.big());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a     | 2",
            "http://fhatos.org:80/a/ | 3",
            "http://fhatos.org/      | 0", // TODO: shouldn't this be 1?
            "http://fhatos.org       | 0",
            "http://fhatos.org/a/b   | 3",
            "+/a/b/c/                | 5",
            "http://#/a/b/c          | 4",
            "http://#:29/a/b/c       | 4",
            "http://#:29/a/b/c/      | 5",
            "/a/b/c/                 | 5"
    }, delimiter = '|')
    void testPathLength(final String furi, final int length) {
        final fURI f = f(furi);
        assertEquals(length, f.pathLength());
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
        assertEquals(cInt.of(coefficient), f(furi).c());
        assertEquals(coefficient, f(furi).c().toString());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                                   | null | null  | -1  | a        | 1        | null | null",
            "mtron:                              | mtron| null  | -1  | null     | 1        | null | null",
            "mtron:abc                           | mtron| null  | -1  | abc      | 1        | null | null",
            "a/b                                 | null | null  | -1  | a/b      | 1        | null | null",
            "a/b/c                               | null | null  | -1  | a/b/c    | 1        | null | null",
            "a/b/c/d                             | null | null  | -1  | a/b/c/d  | 1        | null | null",
            "/a/b/c                              | null | null  | -1  | /a/b/c   | 1        | null | null",
            "/a/b/c{2,3}                         | null | null  | -1  | /a/b/c   | 2,3      | null | null",
            "/a/b/c{?}                           | null | null  | -1  | /a/b/c   | 0,1      | null | null",
            "/a/b/c{*}                           | null | null  | -1  | /a/b/c   | 0,       | null | null",
            "/a/b/c{**}                          | null | null  | -1  | /a/b/c   | ,        | null | null",
            "/a/b/c{**}?a=b                      | null | null  | -1  | /a/b/c   | ,        | null | a=b",
            "mtron:/a/b/c{**}?a=b                | mtron | null | -1  | /a/b/c   | ,        | null | a=b",
            "mtron://a/b/c{**}?a=b               | mtron | a    | -1  | /b/c     | ,        | null | a=b",
            "mtron:a/b/c                         | mtron | null | -1  | a/b/c    | 1        | null | null",
            "mtron://a/b/c{?}?a=b&c=d            | mtron | a    | -1  | /b/c     | 0,1      | null | a=b&c=d",
            "mtron://a:34/b/c{?}?a=b&c=d         | mtron | a    | 34  | /b/c     | 0,1      | null | a=b&c=d",
            "mtron://a:34/b/c{-10,100}?a=b&c=d   | mtron | a    | 34  | /b/c     | -10,100  | null | a=b&c=d",
            "mtron://a:34/b/c?a=b&c=d            | mtron | a    | 34  | /b/c     | 1        | null | a=b&c=d",
            "mtron:/b/c?a=b&c=d                  | mtron | null | -1  | /b/c     | 1        | null | a=b&c=d",
            "mtron:/b/c?xyz<=abc&a=b&c=d         | mtron | null | -1  | /b/c     | 1        | null | rng=xyz&dom=abc&a=b&c=d",
            "mtron:/b/c?xyz{+}<=abc{2}&a=b&c=d   | mtron | null | -1  | /b/c     | 1        | null | rng=xyz{+}&dom=abc{2}&a=b&c=d"},
            delimiter = '|', nullValues = "null")
    public void testParse(final String furi, final String scheme, final String host, final int port, final String path, final String coefficient, final String poly, final String query) {
        final fURI parse = f(furi);
        final fURI components = fURI.of(scheme, host, port, null == path ? List.of() : Arrays.asList(path.split("/")), cInt.of(coefficient), List.of(), parseQuery(query));
        LOG.error("testing:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", parse, components);
        checkEquals(parse, components);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  | null",
            "a/b/c                   | null",
            "http://x.com/a/b/c      | http",
            "mtron://lang/obj        | mtron",
            "mtron:lang/obj          | mtron",
            "./mtron:lang            | null",
            "http:m:m:m              | http",
            "m:m:m:m                 | m"
    }, delimiter = '|', nullValues = "null")
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
        printComponents(f(a));
        assertEquals(f(a).host(), b);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  /a       | true",
            "a/b/c                      |  a        | true",
            "/a/b/c                     |  a        | false",
            "/a/b/c                     |  +        | true",
            "a/b/c                      |  /a       | false", // TODO: should authority-less furis check on start /?
            "//x.com/a/b/c              |  x.com    | false",
            "//x/a/b/c                  |  x        | false",
            "a/b/c/d                    |  a        | true",
            "a/b/c/d                    |  a/b/     | true",
            "a/b/c/d                    |  a/+      | true",
            "a/b/c/d                    |  a/d      | false",
            "a/b/c/d                    |  a/+/c    | true",
    }, delimiter = '|')
    public void testHasPrefix(final String a, final String b, final boolean hasPrefix) {
        LOG.error("testing {{b}}%s{{X}} has prefix {{b}}%s{{X}} [expected: %s]", f(a), f(b), hasPrefix);
        assertEquals(hasPrefix, f(a).hasPrefix(b));
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
            "./../../../.  |  ../../..",
            "./../../a     |  ../../a",
            "a/./z/../b    | a/b",
    }, delimiter = '|')
    public void testResolve(final String f1, final String f2) {
        final fURI furi1a = f(f1);
        final fURI furi1b = f(f2);
        //  final fURI furi2a = mParser.m_furi().parse(f1).get();
        //  final fURI furi2b = mParser.m_furi().parse(f2).get();
        // LOG.info("testing {{b}}%s{{/b}} {{g}}=>{{/g}} {{b}}%s{{b}} resolution", furi1a, furi2b);
        //assertEquals(furi1a.resolve(), furi2b);
        //assertEquals(furi2a.resolve(), furi1b);
        assertEquals(furi1a.resolve(), furi1b);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  /a       | false",
            "a/b/c                      |  c        | true",
            "/a/b/c                     |  c/       | false",
            "/a/b/c                     |  +/       | false",
            "/a/b/c                     |  +        | true",
            "a/b/c                      |  /b/c     | false",
            "//x.com/a/b/c              |  x.com    | false",
            "//x/a/b/c                  |  x        | false",
            "a/b/c/d                    |  d        | true",
            "a/b/c/d                    |  c/d      | true",
            "a/b/c/d                    |  b/c/d    | true",
            "a/b/c/d                    |  a/d/     | false",
            "a/b/c/d                    |  b/+/+    | true",
            "a/b/c/d                    |  b/c/+    | true",
            "a/b/c/d                    |  +/c/d    | true",
    }, delimiter = '|')
    public void testHasPostfix(final String a, final String b, final boolean hasPostfix) {
        LOG.error("testing {{b}}%s{{X}} has postfix {{b}}%s{{X}} [expected: %s]", f(a), f(b), hasPostfix);
        assertEquals(hasPostfix, f(a).hasPostfix(b));
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
            "a{**}              | a{,}",
            "a{*}               | a{,0}",
            "a{?}               | a{-1,0}",
            "a{+}               | a{,-1}",
            "a{10}              | a{-10}",
            "a{10,}             | a{,-10}"
    }, delimiter = '|')
    public void testNeg(final String f1, final String expected) {
        final fURI furi1 = f(f1);
        final fURI furi2 = f(expected);
        checkEquals(furi2, furi1.neg());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | b{-1}             | a/b{-1}",
            "a{1,1}             | a{-1}             | a/a{-1}",
            "a{-1}              | a                 | a/a{-1}",
            "a{-1}              | a{-2}             | a/a{2}",
            "a/b/c{2,3}         | a/d/c{-3,-2}      | a/b/c/a/d/c{-6}",
            "a{0}               | a{0}              | a/a{0}",
            "a{,10}             | a{-10,}           | a/a{,}",
            "http://a.com/a{2,} | b/c{4,}           | http://a.com/a/b/c{8,}",
            "a                  |                   | a",
            "a?a=1&b=2          | b?a=3&c=6         | a/b?a=3&b=2&c=6",
            "/a/?a=1&b=2          | /b/?a=3&c=6         | /a/b/?a=3&b=2&c=6"
    }, delimiter = '|')
    public void testMult(final String f1, final String f2, final String expected) {
        final fURI furi1 = f(f1);
        final fURI furi2 = f(f2);
        final fURI result = f(expected);
        checkEquals(result, furi1.mult(furi2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                    | a{-1}                       | a{0}",
            "a{1,1}               | a{-1}                       | a{0}",
            "a/b{23}              | a/b                         | a/b{24}",
            "a{-1}                | a{-2}                       | a{-3}",
            "a/b/c{2,3}           | a/d/c{-3,-2}                | <ERROR>",
            "a{0}                 | a{0}                        | a{0}",
            "a{,10}               | a{-10,}                     | a{0}",
            "a{1,10}              | a{-10,-1}                   | a{-9,9}",
            "http://a.com/a/b{2,} | http://a.com/a/b{4,}        | http://a.com/a/b{6,}",
            "http://a.com/a{5}    | ws://a.com/a{4}             | <ERROR>",
            "a?a=1&b=2            | a?a=3&c=6                   | a{2}?a=3&b=2&c=6",
            "/a/b/{2}?a=1&b=2     | /a/b/?a=3&c=6               | /a/b/{3}?a=3&b=2&c=6"
    }, delimiter = '|')
    public void testPlus(final String f1, final String f2, final String expected) {
        final fURI furi1 = f(f1);
        final fURI furi2 = f(f2);
        final fURI result = f(expected);
        if (result.toString().equals("<ERROR>")) {
            try {
                furi1.plus(furi2);
                assertTrue(false);
            } catch (final Exception e) {
                assertTrue(true);
            }
        } else
            checkEquals(result, furi1.plus(furi2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/test.com?a=1&b=2|/test.com|a|1|b|2",
            "/test.com?a=7|/test.com|a|1|a|7",
            "/test.com?monad|/test.com|monad|null|monad|null",
            "/test.com?monad&a=7|/test.com|monad|null|a|7",
            "/test.com?c=abc|/test.com|c|abc|c|abc"},
            delimiter = '|', nullValues = "null")
    public void testQueryWrite(final String expected, final String base, final String k1, final String v1, final String k2, final String v2) {
        final fURI expectedfURI = f(expected);
        final fURI resultfURI = f(base).q(k1, v1).q(k2, v2);
        assertEquals(expectedfURI, resultfURI);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |     1|            /a",
            "/a/b/c/                  |     1|           /a/",
            "/a/b/c                  |     2|            /a/b",
            "/a/b/c/                 |     3|            /a/b/c/",
            "a/b/c                   |     2|            a/b",
            "a/b/c                   |     3|            a/b/c",
            "a/b/c?a=b&c=2           |     2|            a/b?a=b&c=2",
            "/a/b/c                  |     4|            /a/b/c",
            "http://x.com/a/b/c      |     4|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     3|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     2|            http://x.com/a/b",
            "http://x.com/a/b/c     |     1|             http://x.com/a",
            "http://a:b@x.com/a/b/c  |     2|            http://a:b@x.com/a",
            "http://a:b@x.com/a/b/c  |     3|            http://a:b@x.com/a/b",
            "http://a:b@x.com/a/b/c  |     4|            http://a:b@x.com/a/b/c"// username password not implemented yet
    },
            delimiter = '|')
    public void testHead(final String f, final int steps, final String head) {
        final fURI furi = f(f);
        final fURI computedHead = furi.head(steps);
        final fURI expectedHead = f(head);
        assertEquals(expectedHead, computedHead);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |     1|            /c",
            "/a/b/c                  |     2|            /b/c",
            "/a/b/c/                 |     3|            /a/b/c/",
            "a/b/c                   |     2|            b/c",
            "a/b/c                   |     3|            a/b/c",
            "a/b/c?a=b&c=2           |     2|            b/c?a=b&c=2",
            "/a/b/c                  |     4|            /a/b/c",
            "http://x.com/a/b/c      |     4|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     3|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     2|            http://x.com/b/c",
            "http://x.com/a/b/c      |     1|            http://x.com/c",
            "http://a:221/a/b/c/     |     1|            http://a:221/c/",
            "http://a:221/a/b/c      |     1|            http://a:221/c",
            "http://a:221/a/b/c      |     2|            http://a:221/b/c",
            "http://a:222/a/b/c      |     3|            http://a:222/a/b/c",
            "http://a:223/a/b/c      |     4|            http://a:223/a/b/c"// username password not implemented yet
    },
            delimiter = '|')
    public void testTail(final String f, final int steps, final String tail) {
        final fURI furi = f(f);
        final fURI computedHead = furi.tail(steps);
        final fURI expectedHead = f(tail);
        assertEquals(expectedHead, computedHead);
        checkEquals(furi.tail(steps), expectedHead);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/b         | a       |  http://fhatos.org/a/b",
            "http://fhatos.org/b/c/d     | a       |  http://fhatos.org/a/b/c/d",
            "/b/c/d                      | a       |  a/b/c/d",
            "/b/c/d                      | /a      |  /a/b/c/d",
            "mtron:/b/c/d                | /a      |  mtron:/a/b/c/d",
            "mtron:/b/c/d                | a       |  mtron:a/b/c/d",
            "mtron:/b/c/d                | a/      |  mtron:a/b/c/d",
            "mtron:/b/c/d                | /a/     |  mtron:/a/b/c/d",
            "mtron://www.com:8999/b/c/d  | a/b/c   |  mtron://www.com:8999/a/b/c/b/c/d",
            "mtron://www.com/b/c/d       | /a/b/c  |  mtron://www.com//a/b/c/b/c/d",
            "mtron://www.com/b/c/d{2}    | /a/b/c  |  mtron://www.com//a/b/c/b/c/d{2}",
    },
            delimiter = '|')
    public void testPrepend(final String base, final String prepend, final String expected) {
        assertEquals(f(expected), f(base).prepend(prepend));
        checkEquals(f(base).prepend(prepend), f(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/b         | a       |  http://fhatos.org/b/a",
            "http://fhatos.org/b/c/d     | a       |  http://fhatos.org/b/c/d/a",
            "/b/c/d                      | a       |  /b/c/d/a",
            // "/b/c/d                      | /a      |  /b/c/d//a",
            "mtron:/b/c/d                | /a      |  mtron:/b/c/d//a",
            "mtron:/b/c/d                | #      |  mtron:/b/c/d/#",
            "mtron:/b/c/d                | a       |  mtron:/b/c/d/a",
            "mtron:/b/c/d                | a/      |  mtron:/b/c/d/a/",
            "mtron:/b/c/d                | /a/     |  mtron:/b/c/d//a/",
            "mtron://www.com:8999/b/c/d  | a/b/c   |  mtron://www.com:8999/b/c/d/a/b/c",
            "mtron://www.com/b/c/d       | /a/b/c  |  mtron://www.com/b/c/d//a/b/c",
            "mtron://www.com/b/c/d{2}    | /a/b/c  |  mtron://www.com/b/c/d//a/b/c{2}",
    }, delimiter = '|')
    public void testExtend(final String base, final String prepend, final String expected) {
        LOG.error("testing {{b}}%s{{X}} extend {{b}}%s{{X}} [expected: %s]", f(base), f(prepend), f(expected));
        assertEquals(f(expected), f(base).extend(prepend));
        checkEquals(f(base).extend(prepend), f(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a|a|true",
            "a|+|true",
            "a|+/|false",
            "a|/+|false",
            "+|a|false",
            "null|a|false",
            "#|a|false",
            "a|#|true",
            "#|#|true",
            "a|null|false",
            //"null|/|false",
            "{0}|a/b{*}|true",
            "/a/b{0}|/a/b{*}|true",
            "/{0}|/{*}|true",
            "null|null|true",
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
            "null|#|true",
            "null|+|false",
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
            //"metatron.org:1234|metatron.org:+|true",
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
           /* "xxx[A,B]|xxx[#,+]|true",
            "xxx[ab,cd]|xxx[ab,cd{?}]|true",
            "xxx[ab,cd]|xxx[ab{*},cd{?}]{?}|true",
            "xxx[ab,cd{0}]|xxx[ab{*},cd{+}]{?}|false",
            "xxx[ab,cd{0}]|xxx[ab{2},cd{0}]{?}|false",
            "xxx[ab,cd]|xxx[ab{*},cd]{+}|true",
            "/m/lst[ab,cd]|/m/lst[ab{*},cd]{+}|true",
            "xxx[ab{2},cd{0}]|xxx[ab{1,3},cd{0}]{1,5}|true",
            "xxx[ab{2},cd{1,3}]{2,3}|xxx[ab{1,3},cd{0,100}]{1,5}|true",
            "xxx[ab{2},cd{1,3}]{2,3}|xxx[ab{1,3},cd{0,2}]{1,5}|false",*/
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
            "http://localhost:8080|http://+/abc|false",
            "x:abc|+:abc|true",
            "http://localhost:8080/abc|+://localhost:8080/abc|true",
            "http://localhost:8080/abc|+://+/abc|true",
            "http://localhost:8080|+://#|true",
            "x|+/#|true",
            "x/y|+/#|true",
            "x/y|+/+|true",
            "x/y/z|+/#|true",
            "x/y/z|+/+/+|true",
            "x/y/z|+/+|false",
            "/x/y/z|+/+|false",
            "/x/y/z|+/#|false",
            "/x/y/z|+/+/+|false",
            "x:y/z|+/+|false",
            "x:y/z|+/+/+|false",
            "x:y/z|+:+/+|true",
            "/x/y/z|+/+/+|false",
            "/x/y/z|+/+|false",
            "/x/y/z|+/#|false",
            "x:y/z|+:/#|false",
            "x:y/z|+:#|true",
            "x:/y/z|+:/#|true",
            "x:y/z|+:/+/+|false",
            "x:y/z|+:/+/+/+|false",
            "x:y/z|+:+/+|true",
            "x:y|+:+/+|false",
            "x:y/z|+/+|false",
            "/x/y/z|+/+/+|false",
            "/x/y/z|+/+|false",
            "/x/y/z|+/#|false",
            "x://y/z|+:+|false",
            "x://y/z|+://+/+|true",
            "x://y/z|+://+|false",
            "x://y/z|+://#|true",
            "x://y.com/z|+://#|true",
            "x://y.com:97/z|+://#|true",
            "x://y.com/z|+://+/+|true",
            "x://y.com/z|+://+/z|true",
            "x://y.com/z|+://+/y|false",
            "z://y.com/z|a://+/+|false",
            "z://y.com/z|a://y.com/z|false",
            "x://y.com:87/z|x://y.com:97/z|false",
            "x://y.com:87/z|x://y.com:87/z|true",
            "x://y.com:87/z|x://y.org:87/z|false",
            "x://y.com:87/z|x://y.org:87/#|false",
            "x://y.com:87/z|x://y.com:87/#|true",
            "x://y.com:97/z|+://+/+|true",
            "x://y.com/z|+://+/+|true",
            "x:a|+:+|true",
            "x:a|+|false",
            "x:|+|false",
            "x:y|+:y|true",
            "x:y|x:+|true",
            "+:+|x:y|false",
            "+:+|+:#|true",
            "a/b/c?a=2|+/+/+?a=+|true",
            "a/b/c?a=2|+/+/+?a=3|false",
            "a/b/c?a=2|a/b/c/?a=2|false",
            "a/b/c?a=2|a/b/c/?#|false",
            "a/b/c?a=2|+/+/+?b=2|false",
            "a/b/c?a=2|+/+/+?b=+|false",
            "a/b/c?a=2|a/+/c?+|true",
            "a/b/c?a=2|a/b/c?#|true",
            "a/b/c?a=2|a/#|true",
            "#|#|true"
            //":y|+:+|false",       why are these wrong? they are inverses of each other.
            //        ":y|:+|true",
    }, delimiter = '|', nullValues = "null")
    void testMatches(final String a, final String b, final boolean shouldMatch) {
        final fURI furi1a = f(a);
        final fURI furi1b = f(b);
       /* final boolean doObjParser = null != a && null != b && !a.equals("{0}");
        final fURI furi2a = doObjParser ? mParser.m_furi().parse(a).get() : f(a);
        final fURI furi2b = doObjParser ? mParser.m_furi().parse(b).get() : f(b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.trace("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
        assertEquals(furi1a, furi2a);
        if (shouldMatch) {
            assertTrue(furi1a.test(furi1b));
            assertTrue(furi2a.test(furi2b));
            assertTrue(furi1a.test(furi2b));
            assertTrue(furi2a.test(furi1b));
        } else {
            assertFalse(furi1a.test(furi1b));
            assertFalse(furi2a.test(furi2b));
            assertFalse(furi1a.test(furi2b));
            assertFalse(furi2a.test(furi1b));
        }*/
        checkMatches(furi1a, furi1b, shouldMatch);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c{2}                |c",
            "a/b/c                   |c",
            "a/b/c{*}                |c",
            "c{*}                    |c",
            "+                       |+",
            "{2}                     |\'\'",
            "a/b/..                  |..",
            "a/b/.                   |.",
    }, delimiter = '|')
    void testName(final String furi, final String name) {
        assertEquals(name, f(furi).name());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c{2}                |false",
            "a/b/c                   |false",
            "a/b/c{*}                |false",
            "c{*}                    |false",
            "+                       |true",
            "{2}                     |false",
            "a/b/..                  |false",
            "a/b/.                   |false",
            "a/b/+                   |true",
            "a/b/+/c                 |true",
            "#                       |true",
            "+{2,3}                  |true",
            "#{?}                    |true",
            "#{0}                    |true",
            "#{1}                    |true",
            "a/b/c{+}                |false",
            "a/b/c{?}                |false"
    }, delimiter = '|')
    void testHasPattern(final String furi, final boolean pattern) {
        assertEquals(pattern, f(furi).hasPattern());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "temp{32}?a<=b                                         | temp                      | a         | b       | 32   |",
            "temp{32}?a<=b                                         | temp                      | a         | b       | 32   |",
            "temp{2,32}?/m/int<=/m/str                             | temp                      | /m/int    | /m/str  | 2,32 |",
            "temp{2,32}?int{?}<=str{*}                             | temp                      | int{0,1}  | str{0,} | 2,32 |",
            "http://test.com:56/temp{?}?int{2,35}<=str{**}         | http://test.com:56/temp   | int{2,35} | str{,}  | 0,1  |",
            "abc{*}?int{2,35}<=str{**}                             | abc                       | int{2,35} | str{,}  | 0,   |",
            "temp{*}?int{2,35}<=str{**}&a=b&c=d                    | temp                      | int{2,35} | str{,}  | 0,   |a=b&c=d",
            "/temp{*}?int{2,35}<=str{**}&a=2&c&g=/m/int            | /temp                     | int{2,35} | str{,}  | 0,   |a=2&c&g=/m/int",
            "/temp{*}?rng=int{2,35}&dom=str{**}&a=2&c&g=/m/int     | /temp                     | int{2,35} | str{,}  | 0,   |a=2&c&g=/m/int",
    }, delimiter = '|')
    void testDomRng(final String furi, final String base, final String rng, final String dom, final String coefficient, final String query) {
        final fURI furiObj = f(furi);
        final fURI baseObj = f(base);
        final fURI domObj = f(dom);
        final fURI rngObj = f(rng);
        final C<?, ?> cObj = cInt.of(coefficient);
        assertEquals(baseObj, furiObj.basePath());
        assertEquals(cObj, furiObj.c());
        assertEquals(domObj, furiObj.dom());
        assertEquals(rngObj, furiObj.rng());
        Map<String, String> queryMap = parseQuery(query);
        queryMap.forEach((k, v) -> assertEquals(v, furiObj.q(k)));
        //System.out.println(queryMap + "---" + furiObj.qMap());
        assertEquals(furiObj.qMap().size(), queryMap.size() + 2); // every test case must have a dom<=rng 
        assertTrue(furiObj.hasQ(DOM));
        assertTrue(furiObj.hasQ(RNG));
        assertFalse(furiObj.hasQ("fAkE"));
        boolean meta = furiObj.toString().startsWith("http");
        assertEquals(meta, furiObj.hasHost());
        assertEquals(meta, furiObj.hasPort());
        assertEquals(meta, furiObj.hasScheme());
    }


    private void checkMatches(final fURI furiA, final fURI furiB, final boolean matches) {
        LOG.error("testing equality:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", furiA, furiB);
        LOG.error("parse class    : %s", furiA.getClass().getSimpleName());
        LOG.error("component class: %s", furiB.getClass().getSimpleName());
        if (false && matches) {

            assertTrue(f(furiA.scheme()).test(f(furiB.scheme())), "schemas don't match");
            assertTrue(f(furiA.host()).test(f(furiB.host())), "hosts don't match");
            assertEquals(furiA.port(), furiB.port(), "ports don't match");
            assertTrue(f(furiA.pathString()).test(f(furiB.pathString())), "paths don't match");
            assertTrue(((C) furiA.c()).within(furiB.c()), "coefficients don't match");
            assertEquals(furiA.qMap(), furiB.qMap(), "queries don't match");
        }
        /// /
        assertEquals(matches, furiA.test(furiB), "furis " + (matches ? "don't" : "shouldn't") + " match");
    }

    private void checkEquals(final fURI furiA, final fURI furiB) {
        LOG.error("testing equality:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", furiA, furiB);
        LOG.error("parse class    : %s", furiA.getClass().getSimpleName());
        LOG.error("component class: %s", furiB.getClass().getSimpleName());
        assertEquals(furiA.scheme(), furiB.scheme(), "schemas don't match");
        assertEquals(furiA.host(), furiB.host(), "hosts don't match");
        assertEquals(furiA.port(), furiB.port(), "ports don't match");
        assertEquals(furiA.pathString(), furiB.pathString(), "paths don't match");
        assertEquals(furiA.c(), furiB.c(), "coefficients don't match");
        assertEquals(furiA.qMap(), furiB.qMap(), "queries don't match");
        /// /
        assertEquals(furiA, furiB, "furis don't match");

    }

    private String printComponents(final fURI furi) {
        LOG.error("parse: {{b}}%s{{X}}", furi);
        LOG.error("class:  %s", furi.getClass().getSimpleName());
        LOG.error("schema: %s", furi.scheme());
        LOG.error("host:   %s", furi.host());
        LOG.error("port:   %s", furi.port());
        LOG.error("path:   %s", furi.pathString());
        LOG.error("  path: %s", furi.path());
        LOG.error("  size: %d", furi.path().size());
        LOG.error("coeff:  %s", furi.c());
        LOG.error("query:  %s", furi.qMap());
        return "";
    }

}
