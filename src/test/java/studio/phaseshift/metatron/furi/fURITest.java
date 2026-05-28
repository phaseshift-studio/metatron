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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.fURI.Singleton.parseQuery;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fURITest extends AbstractMetatronTest {

    protected static fURI idem(final String furi) {
        if (furi == null) return f(null);
        final fURI f = f(furi);
        final String f2 = f.toString();
        final fURI f3 = f(f2);
        assertEquals(f, f3);
        final fURI f4 = mParser.m_furi().parse(furi).get();
        assertEquals(f, f4);
        assertEquals(f3, f4);
        return f;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a       | b     | true",
            "aa      | b     | true",
            "aaa     | aa    | true",
            "aaa     | bb    | true",
            "aa      | bbb   | true",
            "a/b     | b/c/d | true",
            "a       | a/a   | true",
            "a/b/c   | b/c/d | true",
            "a/b/c/d | a/b/c | true",
            "a/b/c   | a/b   | true"
    }, delimiter = '|', nullValues = "null")
    public void testComparable(final String furiA, final String furiB, final boolean lessThan) {
        final fURI fa = f(furiA);
        final fURI fb = f(furiB);
        LOG.debug("testing %s compared to %s is %s", fa, fb, lessThan);
        assertEquals(fa.compareTo(fb) < 0, lessThan);
        assertEquals(fb.compareTo(fa) > 0, !lessThan);

    }

    @ParameterizedTest
    @CsvSource(value = {
            "/g/S/+                      | 1   | /g/S",
            "/g/S/+/                     | 1   | /g/S/",
            "/g/S/+                      | 2   | /g",
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
            "/a/b/c/[0]?a=b&c=d          | 2   | /a/[0]?a=b&c=d",
            "/a/b/c/{?}?a=b&c=d          | 2   | /a/{?}?a=b&c=d",
            "/a/b?a=b&c=d                | 2   | ?a=b&c=d",
    }, delimiter = '|', nullValues = "null")
    public void testRetract(final String furi, final int steps, final String expected) {
        final fURI start = f(furi);
        final fURI end = f(expected);
        assertEquals(end, start.retract(steps), printComponents(start.retract(steps)) + printComponents(end));
        LOG.debug("testing %s retracted %d steps is %s", start, steps, expected);
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
            "/a/b/c?a=b&c=d              | 2   | /c?a=b&c=d",
            "/a/b/c/?a=b&c=d             | 2   | /c/?a=b&c=d",
            "/a/b/c{*}?a=b&c=d           | 1   | /b/c{*}?a=b&c=d",
            "/a/b/c{2,3}?a=b&c=d         | 2   | /c{2,3}?a=b&c=d",
    }, delimiter = '|')
    public void testPretract(final String furi, final int steps, final String expected) {
        final fURI start = idem(furi);
        final fURI end = idem(expected);
        assertEquals(end, start.pretract(steps));
        LOG.debug("testing %s pretracted %d steps is %s", start, steps, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<http://fhatos.org/a>                              | []                      | []",
            "</api/v1/users>                                    | []                      | []",
            "<ftp://files.org:21/docs>                          | []                      | []",
            "<http://fhatos.org:${port}/a/b>                    | [port]                  | [PORT]",
            "<http://api.com:${+80}>                            | [+80]                   | [PORT]",
            "<http://example.com/${user}/profile>               | [user]                  | [PATH]",
            "</api/${version}/users>                            | [version]               | [PATH]",
            "</data/${id}/${type}>                              | [id,type]               | [PATH,PATH]",
            "<${proto}://example.com/data>                      | [proto]                 | [SCHEME]",
            "<http://${domain}/api>                             | [domain]                | [HOST]",
            "<http://search.com?${query}>                       | [query]                 | [QUERY]",
            "<http://api.com/data?${params}>                    | [params]                | [QUERY]",
            "<${scheme}://${host}:${port}/${path}>              | [scheme,host,port,path] | [SCHEME,HOST,PORT,PATH]",
            "<http://api.com:${port}/${version}/users?${q}>     | [port,version,q]        | [PORT,PATH,QUERY]",
            "<http://example.com:${+10}>                        | [+10]                   | [PORT]",
            "<http://api.com/${*2}/data>                        | [*2]                    | [PATH]",
            "<http://search.com?${[a=>b,c=>d]}>                 | [[a=>b,c=>d]]           | [QUERY]",
    }, delimiter = '|')
    public void testTemplates(final String furi, final String expectedTemplates, final String expectedComponents) {
        final fURI start = idem(furi);
        final List<String> actualTemplates = start.templates().stream().map(Tuple.Pair::get1).toList();
        final List<String> actualComponents = start.templates().stream().map(t -> t.get0().toString()).toList();

        // Handle empty list case - use bracket-aware splitting to handle commas inside nested brackets
        final List<String> expTemplates = expectedTemplates.equals("[]") ? List.of() :
                splitBracketAware(expectedTemplates.substring(1, expectedTemplates.length() - 1));
        final List<String> expComponents = expectedComponents.equals("[]") ? List.of() :
                splitBracketAware(expectedComponents.substring(1, expectedComponents.length() - 1));

        assertEquals(expTemplates, actualTemplates,
                String.format("Template expressions mismatch for %s", furi));
        assertEquals(expComponents, actualComponents,
                String.format("Component types mismatch for %s", furi));
        LOG.debug("testing {} has templates {} and components {}", start, expectedTemplates, expectedComponents);
    }

    /**
     * Split a string by comma, but respect bracket nesting (don't split inside [...])
     */
    private List<String> splitBracketAware(final String input) {
        final List<String> result = new ArrayList<>();
        int bracketDepth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '[') {
                bracketDepth++;
                current.append(c);
            } else if (c == ']') {
                bracketDepth--;
                current.append(c);
            } else if (c == ',' && bracketDepth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<http://example.com:${>>port}/api>                   | [port=>8080]                              | http://example.com:8080/api",
            "<http://example.com/${>>user}/profile>               | [user=>john]                              | http://example.com/john/profile",
            "</api/${>>version}/users>                            | [version=>v2]                             | /api/v2/users",
            "</data/${>>id}/${>>type}>                            | [id=>123,type=>json]                      | /data/123/json",
            "<http://api.com/search?${>>query}>                   | [query=>[q=>test]]                        | http://api.com/search?q=test",
            "<http://api.com/search?${>>query>>q}>              | [query=>[q=>test]]                          | http://api.com/search?test",
            "<http://api.com/search?${>>query>>q>>}>              | [query=>[q=>test]]                        | http://api.com/search?noobj",
            "<http://api.com:${>>port}${>>version}>               | [port=>9000,version=>v1]                  | http://api.com:9000/v1",
            "<http://api.com:${>>port}${>>version}/${>>path}>     | [port=>9000,version=>v1,path=>[a,b,c]]    | http://api.com:9000/v1/a/b/c",
            "<http://api.com/${plus([d])}>                        | [a,b,c]                                   | http://api.com/a/b/c/d",
            "<http://api.com/${within(>-.mult(x))}>               | [a,b,c]                                   | http://api.com/a/x/b/x/c/x",
            "<${>>1}://api.com/${within(>-.mult(x))}>             | [a,b,c]                                   | b://api.com/a/x/b/x/c/x",
    }, delimiter = '|')
    public void testUriTemplateExpansion(final String templateUri, final String lhsRec, final String expectedUri) {
        final fURI template = idem(templateUri);
        final Uri uri = uri(template);
        final Obj lhs = mParser.m_obj().parse(lhsRec).get();
        final Obj result = uri.apply(lhs);
        assertTrue(result.isUri(), "result should be a uri");
        assertEquals(f(expectedUri), f(result.uriValue().toString()));
        assertEquals(expectedUri, result.uriValue().toString(), String.format("template expansion mismatch for %s with %s", templateUri, lhsRec));
        LOG.debug("testing {} with {} = {}", templateUri, lhsRec, result.uriValue());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a         | a",
            "http://fhatos.org/a/b       | a/b",
            "http://fhatos.org/a/b/      | /a/b",
            "http://fhatos.org/a/b       | /a",
            "http://fhatos.org/a/b       | /a/",
            "http://fhatos.org:81/a      | /a",
            "http://fhatos.org:81/a/b    | a/b/",
            "http://fhatos.org:81/a/b    | a/b",
            "http://fhatos.org:81/a/b    | /a/b",
            "/fhat.org/a/b               | a/b",
            "fhat.org/a/b                | /a/b",
            "/a/b/c?a=b&c=d              | a",
            "/a/b/c?a=b&c=d              | /a",
            "/a/b/c/?a=b&c=d             | /a/b",
            "/a/b/c{*}?a=b&c=d           | /a/b/c",
            "/a/b/c{2,3}?a=b&c=d         | /a/b/"
    }, delimiter = '|')
    @Disabled
    public void testPretractPrefix(final String furi, final String pretraction) {
        final fURI a = idem(furi);
        final fURI pa = a.pretract(pretraction);
        final fURI b = pa.prepend(pretraction);
        LOG.debug("testing %s pretracted by %s is %s and then prepended is %s", a, pretraction, pa, b);
        //assertNotEquals(a, pa);
        assertEquals(a, b);
    }


    @ParameterizedTest
    @CsvSource(value = {"http://fhatos.org/a,fhatos.org,-1",
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
            "null,null,-1"
    }, nullValues = "")
    void testAuthority(final String furi, final String host, final int port) {
        for (final fURI parse : Arrays.asList(f(furi), mParser.m_furi().parse(furi).<fURI>get())) {
            if (null == parse)
                continue;
            if (host.equals("null"))
                assertNull(parse.host());
            else
                assertEquals(host, parse.host());
            assertEquals(port, parse.port());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://fhatos.org/a     | false | false",
            "http://fhatos.org:80/a/ | false | true",
            "http://fhatos.org/      | false | false",
            "http://fhatos.org       | false | false",
            "http://fhatos.org/a/    | false | true",
            "http://fhatos.org/a/b   | false | false",
            "+/a/b/c/                | true  | true",
            "http://#/a/b/c          | false | false",
            "http://#:29/a/b/c       | false | false",
            "/a/b/c/                 | false | true"
    }, delimiter = '|')
    void testSlashes(final String furi, final boolean isRelative, final boolean isBranch) {
        final fURI f = idem(furi);
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
        final fURI f = idem(furi);
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
        final fURI f = idem(furi);
        assertEquals(idem(fURIBig), f.big());
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
        assertEquals(cInt.of(coefficient), idem(furi).c());
        assertEquals(coefficient, idem(furi).c().toString());
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
        for (final fURI parse : Arrays.asList(f(furi), mParser.m_furi().parse(furi).<fURI>get())) {
            final fURI components = fURI.of(scheme, host, port, null == path ? List.of() : Arrays.asList(path.split("/")), cInt.of(coefficient), List.of(), parseQuery(query), null);
            LOG.debug("testing:" +
                    "\n\tparse    : {{b}}%s{{X}} " +
                    "\n\tcomponent: {{b}}%s{{X}}", parse, components);
            checkEquals(parse, components);
        }
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
        printComponents(idem(a));
        assertEquals(idem(a).host(), b);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c                     |  /a       | true",
            "a/b/c                      |  a        | true",
            "/a/b/c                     |  a        | false",
            "/a/b/c                     |  +        | false",
            "a/b/c                      |  /a       | false", // TODO: should authority-less furis check on start /?
            "//x.com/a/b/c              |  x.com    | false",
            "//x/a/b/c                  |  x        | false",
            "a/b/c/d                    |  a        | true",
            "a/b/c/d                    |  a/b     | true",
           // "a/b/c/d                    |  a/b/     | true",
            "a/b/c/d                    |  a/+      | true",
            "a/b/c/d                    |  a/d      | false",
            "a/b/c/d                    |  a/+/c    | true",
    }, delimiter = '|')
    public void testHasPrefix(final String a, final String b, final boolean hasPrefix) {
        LOG.debug("testing {{b}}%s{{X}} has prefix {{b}}%s{{X}} [expected: %s]", f(a), f(b), hasPrefix);
        assertEquals(hasPrefix, idem(a).hasPrefix(b));
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
        final fURI furi1a = idem(f1);
        final fURI furi1b = idem(f2);
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
            "/a/b/c                     |  +        | false",
            "a/b/c                      |  /b/c     | true",
            "//x.com/a/b/c              |  x.com    | false",
            "//x/a/b/c                  |  x        | false",
            "a/b/c/d                    |  d        | true",
            "a/b/c/d                    |  c/d      | true",
            "a/b/c/d                    |  b/c/d    | true",
            "a/b/c/d                    |  a/d/     | false",
            "a/b/c/d                    |  b/+/+    | false",
            "a/b/c/d                    |  b/c/+    | false",
            "a/b/c/d                    |  +/c/d    | false",
    }, delimiter = '|')
    public void testHasPostfix(final String a, final String b, final boolean hasPostfix) {
        LOG.debug("testing {{b}}%s{{X}} has postfix {{b}}%s{{X}} [expected: %s]", f(a), f(b), hasPostfix);
        assertEquals(hasPostfix, idem(a).hasPostfix(b));
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
            "a{0}               | a{0}              | noobj{0}",
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
            "a/b/c{2,3}           | a/d/c{-3,-2}                | #{-1,1}",
            "a{0}                 | a{0}                        | a{0}",
            "a{,10}               | a{-10,}                     | a{0}",
            "a{1,10}              | a{-10,-1}                   | a{-9,9}",
            "http://a.com/a/b{2,} | http://a.com/a/b{4,}        | http://a.com/a/b{6,}",
            "http://a.com/a{5}    | ws://a.com/a{4}             | #{9}",
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
            "/test.com?a=1&a=2|a=1;2",
            "/test.com?a=1&a=2&a=56&b=2|a=1;2;56&b=2"},
            delimiter = '|', nullValues = "null")
    public void testQueryParse(final String furi, final String expectedQuery) {
        assertEquals(expectedQuery, f(furi).qString());
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
            "http://fhatos.org/b/#        |  http://fhatos.org/b",
            "http://fhatos.org/b/#/       |  http://fhatos.org/b/",
            "http://fhatos.org/b/#/       |  http://fhatos.org/b/",
            "a/b/#/                       |  a/b/",
            "a/b/+/+/+                    |  a/b",
            "/a/b/+/+/#/                  | /a/b/",
            "/a/b/+/+/+/                  | /a/b/",
            "a/b/+/+/+/?a=b               | a/b/?a=b",
            "a/b/+/+/+/+/{2,4}            | a/b/{2,4}",
            "a/b/+/+/+/+/+                | a/b",
            "a/b/+/+/+/+/+/+/[A,B]{*}     | a/b/[A,B]{*}",
            "a/b/+/+/+/+/+/+/+            | a/b",
            "/a/b/+/+/+/+/+/+/+/+/        | /a/b/",
            "a/b/+/+/+/+/+/+/+/+/+/       | a/b/",
            "/a/b/+/+/+/+/+/+/+/+/+/+     | /a/b",
            "a/b/+/+/+/+/+/+/+/+/+/+/+/   | a/b/",
            "a/b/+/+/+/+/+/+/+/+/+/+/+/+/ | a/b/",
    }, delimiter = '|')
    public void testRetractPattern(final String pattern, final String retraction) {
        LOG.debug("testing {{b}}%s{{X}} retractPattern {{b}}%s{{X}}", f(pattern), f(retraction));
        assertEquals(f(retraction), f(pattern).retractPattern());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                           | /b/     |  a/b/",
            "a                           | /b      |  a/b",
            "a                           | /       |  a/",
            "a/                          | /       |  a/",
            "a/                          | b/      |  a/b/",
            "a/                          | /b/     |  a/b/",
            //    "a/                          | //b/    |  a/b/",
            //    "/a/                         | //b//   |  /a/b/",
            "http://fhatos.org/b         | /       |  http://fhatos.org/b/",
            "http://fhatos.org/b         | a       |  http://fhatos.org/b/a",
            "http://fhatos.org/b/c/d     | a       |  http://fhatos.org/b/c/d/a",
            "/b/c/d                      | a       |  /b/c/d/a",
            // "/b/c/d                      | /a      |  /b/c/d//a",
            "mtron:/b/c/d                | /a      |  mtron:/b/c/d/a",
            "mtron:/b/c/d                | #       |  mtron:/b/c/d/#",
            "mtron:/b/c/d                | a       |  mtron:/b/c/d/a",
            "mtron:/b/c/d                | a/      |  mtron:/b/c/d/a/",
            "mtron:/b/c/d                | /a/     |  mtron:/b/c/d/a/",
            "mtron://www.com:8999/b/c/d  | a/b/c   |  mtron://www.com:8999/b/c/d/a/b/c",
            "mtron://www.com/b/c/d       | /a/b/c  |  mtron://www.com/b/c/d/a/b/c",
            "mtron://www.com/b/c/d{2}    | /a/b/c  |  mtron://www.com/b/c/d/a/b/c{2}",
    }, delimiter = '|')
    public void testExtend(final String base, final String prepend, final String expected) {
        LOG.debug("testing {{b}}%s{{X}} extend {{b}}%s{{X}} [expected: %s]", f(base), f(prepend), f(expected));
        assertEquals(f(expected), f(base).extend(prepend));
        checkEquals(f(base).extend(prepend), f(expected));
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
            "/mtron/+/A                           | false",
            "AbC                                  | false",
            "AbC/A                                | false",
            "abc/A                                | false",
            "abc/d                                | false",
            "A/B/C                                | true",
            "A/+/C                                | true",
            "A/#                                  | true",
            "A/#{*}                               | true"
    }, delimiter = '|')
    public void testGeneric(final String f, final boolean isGeneric) {
        final fURI furi1 = f(null == f ? "" : f);
        if (null == f) {
            assertEquals(isGeneric, furi1.isGeneric());
            return;
        }
        final fURI furi2 = mParser.m_furi().parse(f).get();
        assertEquals(f, furi1.toString());
        assertEquals(f, furi2.toString());
        assertEquals(furi1, furi2);
        assertEquals(furi1, f(furi1.toString()));
        LOG.debug("testing {{b}}%s{{/b}} %s generics", furi1, isGeneric ? "{{g}}for{{/g}}" : "{{r}}for no{{/r}}");
        assertEquals(isGeneric, furi1.isGeneric());
        assertEquals(isGeneric, furi2.isGeneric());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "A             |  A             | true",
            "A/b/c         |  A/B/C         | false",
            "a/b/c         |  D             | true",
            "A/B           |  A/C           | false",
            "A{+}          |  A{*}          | true",
            "A/B{2,4}      |  a/#{*}        | true",
            "A/B/C{2,4}    |  a/#{*}        | true",
            "A/{+}         |  A/#{*}        | true",
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
        final boolean resultMatch = lhsResolved.test(rhsResolved);
        LOG.debug("testing {{b}}%s{{/b}} [resolved: {{m}}%s{{/m}}] %s {{b}}%s{{/b}} [resolved: {{m}}%s{{/m}}]", lhs, lhsResolved, matches ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", rhs, rhsResolved);
        assertEquals(matches, resultMatch);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                  | a               | 0",
            "m:a                | m:a             | 0",
            "a/b                | a/b/c           | -1",
            "m:a/b              | m:a/b/c         | -1",
            "a/#                | a/b/c           | 1",
            "m://a/#            | m://a/b/c       | 1",
            "m://a/#            | m://#           | -1",
            "a/b/+              | a/b/c           | 1",
            "a/b/c              | a/b/c           | 0",
            "a/b/+              | c/b/a           | -1",
    }, delimiter = '|')
    public void testCompareTo(final String f1, final String f2, final int order) {
        final fURI furi1a = idem(f1);
        final fURI furi1b = idem(f2);
        final fURI furi2a = mParser.m_furi().parse(f1).get();
        final fURI furi2b = mParser.m_furi().parse(f2).get();
        //assertEquals(furi1a, furi2a); // TODO: important ssend issue
        assertEquals(furi1b, furi2b);
        LOG.debug("testing {{b}}%s{{/b}} superset of {{b}}%s{{/b}} = {{y}}%s{{/b}}", furi1a, furi1b, "" + furi1a.compareTo(furi1b));
        assertEquals(order, furi1a.compareTo(furi1b));
        assertEquals(order, furi2a.compareTo(furi2b));

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
            "null|/|false",
            "{0}|a/b{*}|true",
            "/a/b{0}|/a/b{*}|true",
            // TODO:     "/{0}|/{*}|true",
            "null|null|true",
            "http://fhatos.org/a|#://fhatos.org/a|true", // should fail as # is not the last or second last character
            "http://fhatos.org/a|+://fhatos.org/a|true",
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
            "http://fhatos.org/a/b/c|http://#|true",
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
            "/mtron/inst/plus/|/mtron/+/plus/{?}|true",
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
            "a/plus{4}|+/plus{4}|true",
            "/mtron/inst/plus{4}|/mtron/+/plus{4}|true",
            "/m/lst[AA,BB]{2}|/m/lst[AA,BB]{2}|true",
            "/m/lst[AA,BB]{2}|/m/lst[AA,BB]{1,6}|true",
            "/m/lst[AA,BB]{2}|/m/lst[AA,BB]{-6,-1}|false",
            //     "/m/lst[A,B]|/m/lst[A,B]|true",
            "/m/lst[aa,bb]|/m/lst[aa,bb]|true",
            "/m/lst[AA,BB]|/m/lst[AA,BB]|true",
            //    "xxx[A,B]|xxx[A,B]|true",
            "xxx[a/b/c,b/c/d]|xxx[a/b/c,b/c/d]|true",
          /*  "xxx[a/b/c,b/c/d]|xxx[a/+/+,b/c/#]|true",
            "xxx[a/b/c,b/c/d]|xxx[a/+/c,b/c/d]|true",
            "xxx[A,B]|xxx[#,+]|true",
            "xxx[a,b]|xxx[+,+]|true",
            "xxx[ab,bc]|xxx[#,+]|true",*/
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
            "#|#|true",
            "+:y|+:+|true",
            //  ":y|:+|true",
    }, delimiter = '|', nullValues = "null")
    void testMatches(final String a, final String b, final boolean shouldMatch) {
        final fURI furi1a = idem(a);
        final fURI furi1b = idem(b);
        final boolean doObjParser = null != a && null != b;
        final fURI furi2a = doObjParser ? mParser.m_furi().parse(a).get() : idem(a);
        final fURI furi2b = doObjParser ? mParser.m_furi().parse(b).get() : idem(b);
        assertEquals(furi1a, furi2a);
        assertEquals(furi1b, furi2b);
        LOG.debug("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
        LOG.debug("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.debug("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi1a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi2b);
        LOG.debug("testing: {{b}}%s{{/b}} %s {{b}}%s{{/b}}", furi2a, shouldMatch ? "{{g}}should match{{/g}}" : "{{r}}should not match{{/r}}", furi1b);
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
        }
        checkMatches(furi1a, furi1b, shouldMatch);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c[int,int]{2}  |2               |[int, int]",
            "a/b/c[A{2},B{3}]   |1               |[A{2}, B{3}]"
    }, delimiter = '|')
    void testPoly(final String furi, final String c, final String typeParams) {
        final fURI furiA = idem(furi);
        assertEquals(c, furiA.c().toString());
        assertEquals(typeParams, furiA.poly().toString());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c{2}                |[a,b,c]",
            "a/b/c                   |[a,b,c]",
            "a/b/c{*}                |[a,b,c]",
            "/a/b/c{2}               |[<>,a,b,c]",
            "/a/b/c                  |[<>,a,b,c]",
            "/a/b/c/{*}              |[<>,a,b,c,<>]",
            "/a/b/c/{2}              |[<>,a,b,c,<>]",
            "/a/b/c/?a=b&c=2         |[<>,a,b,c,<>]",
            "/a/                     |[<>,a,<>]",
            /*"//                      |[]", // resolves to the schema://host 
            "///                     |[]", // resolves to the schema://host 
            "////                    |[]", // resolves to the schema://host */
            "c{*}                    |[c]",
            "+                       |[+]",
            "/                       |[<>]",
            "/#                       |[<>,#]",
            "#/                       |[#,<>]",
            "/#/                       |[<>,#,<>]",
            "#                       |[#]",
            "a/b/..                  |[a,b,<..>]",
            // "/a/b/.                   |[<>,a,b,.]",
    }, delimiter = '|')
    void testPathStructure(final String furi, final String path) {
        final fURI f = idem(furi);
        final Lst l = mParser.m_lst().parse(path).get();
        final List<String> pathList = f.path();
        LOG.debug("testing: {{b}}%s{{X}} parsed to {{b}}%s{{X}} [path: %s] [expected: %s]", furi, f, f.path(), l);
        assertEquals(l.count(), f.pathLength());
        for (int i = 0; i < pathList.size(); i++) {
            assertEquals(l.at(i).uriValue().toString(), pathList.get(i));
        }

    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c{2}                |c",
            "a/b/c                   |c",
            "a/b/c{*}                |c",
            "c{*}                    |c",
            "+                       |+",
            //     "{2}                     |\'\'",
            "a/b/..                  |..",
            "a/b/.                   |.",
            "a/b/#                   |#",
            "a/b/#/                  |''",
    }, delimiter = '|')
    void testName(final String furi, final String name) {
        assertEquals(name, idem(furi).name());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c{2}                |false",
            "a/b/c                   |false",
            "a/b/c{*}                |false",
            "c{*}                    |false",
            "+                       |true",
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
        assertEquals(pattern, idem(furi).hasPattern());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "temp{32}?a<=b                                         | temp                      | a              | b            | 32   |",
            "temp{32}?a<=b                                         | temp                      | a              | b            | 32   |",
            "temp{2,32}?/m/int<=/m/str                             | temp                      | /m/int         | /m/str       | 2,32 |",
            "temp{2,32}?int{?}<=str{*}                             | temp                      | int{0,1}       | str{0,}      | 2,32 |",
            "http://test.com:56/temp{?}?int{2,35}<=str{**}         | http://test.com:56/temp   | int{2,35}      | str{,}       | 0,1  |",
            "abc{*}?int{2,35}<=str{**}                             | abc                       | int{2,35}      | str{,}       | 0,   |",
            "temp{*}?int{2,35}<=str{**}&a=b&c=d                    | temp                      | int{2,35}      | str{,}       | 0,   |a=b&c=d",
            "/temp{*}?int{2,35}<=str{**}&a=2&c&g=/m/int            | /temp                     | int{2,35}      | str{,}       | 0,   |a=2&c&g=/m/int",
            //   "/temp{*}?lst[int{2,35}]<=lst[str{**}]&a=2&c&g=/m/int  | /temp                     | lst[int{2,35}] | lst[str{,}]  | 0,   |a=2&c&g=/m/int",
            "/temp{*}?rng=int{2,35}&dom=str{**}&a=2&c&g=/m/int     | /temp                     | int{2,35}      | str{,}       | 0,   |a=2&c&g=/m/int",
            "/temp{*}?rng=+&dom=#&a=2&c&g=/m/int                   | /temp                     | +              | #            | 0,   |a=2&c&g=/m/int",
            "temp_abc{2,3}?rng=+&dom=#&a=2&c&g=/m/int              | temp_abc                  | +              | #            | 2,3  |a=2&c&g=/m/int",
            "temp_abc{,3}?rng=+/#&dom=abc/#&a=2&c&d                | temp_abc                  | +/#            | abc/#        | ,3   |a=2&c&d",
    }, delimiter = '|')
    void testDomRng(final String furi, final String base, final String rng, final String dom, final String coefficient, final String query) {
        for (final fURI furiObj : Arrays.asList(idem(furi).big(), mParser.m_furi().parse(furi).<fURI>get().big())) {
            final fURI baseObj = idem(base).big();
            final fURI domObj = idem(dom).big();
            final fURI rngObj = idem(rng).big();
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
    }


    private void checkMatches(final fURI furiA, final fURI furiB, final boolean matches) {
        LOG.debug("testing equality:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", furiA, furiB);
        LOG.debug("parse class    : %s", furiA.getClass().getSimpleName());
        LOG.debug("component class: %s", furiB.getClass().getSimpleName());
        if (false && matches) {

            assertTrue(idem(furiA.scheme()).test(idem(furiB.scheme())), "schemas don't match");
            assertTrue(idem(furiA.host()).test(idem(furiB.host())), "hosts don't match");
            assertEquals(furiA.port(), furiB.port(), "ports don't match");
            assertTrue(idem(furiA.pathString()).test(idem(furiB.pathString())), "paths don't match");
            assertTrue(((C) furiA.c()).within(furiB.c()), "coefficients don't match");
            assertEquals(furiA.qMap(), furiB.qMap(), "queries don't match");
        }
        /// /
        assertEquals(matches, furiA.test(furiB), "furis " + (matches ? "don't" : "shouldn't") + " match");
    }

    private void checkEquals(final fURI furiA, final fURI furiB) {
        LOG.debug("testing equality:" +
                "\n\tparse    : {{b}}%s{{X}} " +
                "\n\tcomponent: {{b}}%s{{X}}", furiA, furiB);
        LOG.debug("parse class    : %s", furiA.getClass().getSimpleName());
        LOG.debug("component class: %s", furiB.getClass().getSimpleName());
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
        LOG.info("parse: {{b}}%s{{X}}", furi);
        LOG.info("class:  %s", furi.getClass().getSimpleName());
        LOG.info("schema: %s", furi.scheme());
        LOG.info("host:   %s", furi.host());
        LOG.info("port:   %s", furi.port());
        LOG.info("path:   %s", furi.pathString());
        LOG.info("  path: %s", furi.path());
        LOG.info("  size: %d", furi.path().size());
        LOG.info("coeff:  %s", furi.c());
        LOG.info("query:  %s", furi.qMap());
        return "";
    }

    @ParameterizedTest
    @CsvSource(value = {
            "test:/a/b/c     | test:/a/b",
            "test:/a/b       | test:/a",
            "test:/a         | test:",
            "test:           | test:",
            "mem:/x/y        | mem:/x",
            "mem:/x          | mem:",
            "mem:            | mem:",
            "/a/b/c          | /a/b",
    }, delimiter = '|')
    public void testRetract(final String original, final String expected) {
        final fURI furi = f(original);
        final fURI retracted = furi.retract(1);
        final fURI expectedFuri = f(expected);
        LOG.debug("retract: %s -> %s (expected: %s)  class=%s  segments=%s  segmentLen=%d  pathLen=%d",
                furi, retracted, expectedFuri,
                furi.getClass().getSimpleName(),
                furi.segments(), furi.segmentLength(), furi.pathLength());
        assertEquals(expectedFuri, retracted, "retract(1) of " + original);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "test:/a/b/c     | 3       | 4",
            "test:/a/b       | 2       | 3",
            "test:/a         | 1       | 2",
            "test:           | 0       | 0",
            "mem:/x/y        | 2       | 3",
            "mem:            | 0       | 0",
            "/a/b/c          | 3       | 4",
    }, delimiter = '|')
    public void testSegmentLength(final String furiStr, final int expectedSegLen, final int expectedPathLen) {
        final fURI furi = f(furiStr);
        assertEquals(expectedSegLen, furi.segmentLength(), "segmentLength of " + furiStr);
        assertEquals(expectedPathLen, furi.pathLength(), "pathLength of " + furiStr);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "test:/a/b/c     | #               | ",
            "test:/a/b/c     | test:/a/b/c     | ",
            "test:/a         | test:/a         | ",
            "test:/a/b/c     | test:/a/b       | /c",
            "test:/a/b/c     | test:#          | ",
            "test:/a/b/c     | test:/d/#       | test:/a/b/c",
            "test:/a/b/c     | test:/a/b/      | c",
            "test:/a/b/c     | test:/a/+/      | c",
            "test:/a/b/c     | test:/+/+/      | c",
            "test:/a/b/c?x=1 | test:/+/+/+?x=1 | ",
            "test:/a/b/c?x=1 | #               | ",
           // TODO: query params "test:/a/b/c?x=1 | #?+=+           | "
    }, delimiter = '|')
    public void testRemovePrefix(final String vid, final String base, final String expected) {
        final fURI vidF = f(vid);
        final fURI baseF = f(base);
        final fURI remainder = vidF.removePrefix(baseF);
        LOG.debug("removePrefix: %s - %s = %s [actual: %s]", vidF, baseF, expected, remainder);
        assertEquals(f(expected), remainder);

    }

}
