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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.ifURI.parseQuery;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ifURITest extends AbstractMetatronTest {


    @ParameterizedTest
    @CsvSource(value = {
            "a                             | null | null | -1  | a       | 1  | null | null",
            "mtron:                        | null | null | -1  | mtron:   | 1  | null | null",
            "mtron:abc                     | mtron| null | -1  | abc   | 1  | null | null",
            "a/b                           | null | null | -1 | a/b     | 1  | null | null",
            "a/b/c                         | null | null | -1 | a/b/c   | 1  | null | null",
            "a/b/c/d                       | null | null | -1 | a/b/c/d | 1  | null | null",
            "/a/b/c                        | null | null | -1 | /a/b/c   | 1  | null | null",
            "/a/b/c{2,3}                   | null | null | -1 | /a/b/c   | 2,3  | null | null",
            "/a/b/c{?}                     | null | null | -1 | /a/b/c   | 0,1  | null | null",
            "/a/b/c{*}                     | null | null | -1 | /a/b/c   | 0,  | null | null",
            "/a/b/c{**}                    | null | null | -1 | /a/b/c   | ,  | null  | null",
            "/a/b/c{**}?a=b                | null | null | -1 | /a/b/c   | ,  | null  | a=b",
            "mtron:/a/b/c{**}?a=b          | mtron | null | -1 | /a/b/c   | ,  | null | a=b",
            "mtron://a/b/c{**}?a=b         | mtron | a    | -1 | /b/c     | ,  | null | a=b",
            "mtron:a/b/c                   | mtron | null | -1 | a/b/c    | 1  | null | null",
            "mtron://a/b/c{?}?a=b&c=d      | mtron | a    | -1 | /b/c     | 0,1 | null | a=b&c=d",
            "mtron://a:34/b/c{?}?a=b&c=d   | mtron | a    | 34| /b/c      | 0,1 | null | a=b&c=d",
            "mtron://a:34/b/c{-10,100}?a=b&c=d   | mtron | a    | 34| /b/c      | -10,100  | null | a=b&c=d",
            "mtron://a:34/b/c?a=b&c=d            | mtron | a    | 34| /b/c      | 1        | null | a=b&c=d",
            "mtron:/b/c?a=b&c=d                  | mtron | null | -1 | /b/c     | 1        | null | a=b&c=d"},

            delimiter = '|', nullValues = "null")
    public void testParse(final String furi, final String scheme, final String host, final int port, final String path, final String coefficient, final String poly, final String query) {
        final ifURI parse = ifURI.of(furi);
        final ifURI components = ifURI.of(scheme, host, port, null == path ? null : Arrays.asList(path.split("/")), cInt.of(coefficient), List.of(), parseQuery(query));
        LOG.error("testing:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", parse, components);
        checkfURI(parse, components);
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
        assertEquals(scheme, ifURI.of(furi).scheme());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  ",
            "a/b/c                      |  ",
         //   "//x.com/a/b/c              |  x.com",
         //   "//x/a/b/c                  |  x",
       //     "//x:8080/a/b/c             |  x",
         //   "//x.com                    |  x.com",
          //  "//x                        |  x",
            "http://x.com/a/b/c         |  x.com",
            "http://x.com:80/a/b/c      |  x.com",
            "mtron://lang/obj           |  lang",
            "mtron:lang/obj             |  "
    }, delimiter = '|')
    public void testHostOrSegment(final String a, final String b) {
        assertEquals(ifURI.of(a).host(), b);
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
        LOG.error("testing {{b}}%s{{X}} has prefix {{b}}%s{{X}} [expected: %s]", ifURI.of(a), ifURI.of(b), hasPrefix);
        assertEquals(hasPrefix, ifURI.of(a).hasPrefix(b));
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
        LOG.error("testing {{b}}%s{{X}} has postfix {{b}}%s{{X}} [expected: %s]", ifURI.of(a), ifURI.of(b), hasPostfix);
        assertEquals(hasPostfix, ifURI.of(a).hasPostfix(b));
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
        final ifURI furi1 = ifURI.of(f1);
        final ifURI furi2 = ifURI.of(f1);
        checkfURI(furi1, furi2);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                  |     2|            /a",
            "/a/b/c/                 |     3|            /a/b",
            "a/b/c                   |     2|            a/b",
            "a/b/c                   |     3|            a/b/c",
            "/a/b/c                  |     4|            /a/b/c",
            //  "/a/b/c                  |     5|            /a/b/c",
            "http://x.com/a/b/c      |     4|            http://x.com/a/b/c",
            "http://x.com/a/b/c      |     3|            http://x.com/a/b",
            "http://x.com/a/b/c      |     2|            http://x.com/a",
            "http://x.com/a/b/c      |     1|            http://x.com/",
            // "http://a:b@x.com/a/b/c  |     2|            http://a:b@x.com/a/b", username password not implemented yet
    },
            delimiter = '|')
    public void testHead(final String f, final int steps, final String head) {
        final ifURI furi = ifURI.of(f);
        final ifURI computedHead = furi.head(steps);
        final ifURI expectedHead = ifURI.of(head);
        assertEquals(expectedHead, computedHead);
        //assertEquals(computedHead,furi.retract(furi.segments().size()-steps));
        assertEquals(furi.path().size(), computedHead.path().size() + (furi.path().size() - steps));
        assertEquals(steps, computedHead.path().size());
        checkfURI(furi.head(steps), expectedHead);
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
        assertEquals(ifURI.of(expected), ifURI.of(base).prepend(prepend));
        checkfURI(ifURI.of(base).prepend(prepend), ifURI.of(expected));
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
        LOG.error("testing {{b}}%s{{X}} extend {{b}}%s{{X}} [expected: %s]", ifURI.of(base), ifURI.of(prepend), ifURI.of(expected));
        assertEquals(ifURI.of(expected), ifURI.of(base).extend(prepend));
        checkfURI(ifURI.of(base).extend(prepend), ifURI.of(expected));
    }


    private void checkfURI(final ifURI furiA, final ifURI furiB) {
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

}
