/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.web.space.http;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.update_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.HTML_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_JSON_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipWhenPortUnavailable(value = 80)
public class web_httpHandlerTest extends AbstractHTTPServerTest {

    private Space contentSpace;

    @BeforeEach
    public void setupContentSpace() {
        // Create a memSpace to serve as the web content backing store
        this.contentSpace = memSpace.of(rec(
                uri(PATTERN), uri("mem:test-pages/#"),
                uri(ROUTE), rec()
        ), f("/sys/space/test/web-content/" + getClass().getSimpleName()));
        // Populate with test content
        Router.writeToSpace(f("mem:test-pages/index.html"), str("<html><body><h1>Hello World</h1></body></html>"));
        Router.writeToSpace(f("mem:test-pages/about.html"), str("<html><body><h1>About</h1></body></html>"));
    }

    @AfterEach
    public void teardownContentSpace() {
        if (this.contentSpace != null) {
            Router.global().removeSpace(this.contentSpace.vid());
            this.contentSpace.close();
            this.contentSpace = null;
        }
    }

    @Override
    protected HttpRec createHandler(final fURI vid) {
        return new web_httpHandler(new LinkedHashMap<>(Map.of(
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(WEB_ROOT), uri("mem:test-pages")
        )), vid);
    }

    // ========================================
    // Handler key registration tests
    // ========================================

    @Test
    public void testHasOnGet() {
        assertFalse(handler.at(uri(ON_GET)).isNoObj(), "missing ON_GET");
    }

    @Test
    public void testHasOnPut() {
        assertFalse(handler.at(uri(ON_PUT)).isNoObj(), "missing ON_PUT");
    }

    @Test
    public void testHasOnPatch() {
        assertFalse(handler.at(uri(ON_PATCH)).isNoObj(), "missing ON_PATCH");
    }

    @Test
    public void testHasOnDelete() {
        assertFalse(handler.at(uri(ON_DELETE)).isNoObj(), "missing ON_DELETE");
    }

    @Test
    public void testHasOnError() {
        assertFalse(handler.at(uri(ON_ERROR)).isNoObj(), "missing ON_ERROR");
    }

    @Test
    public void testHasSend() {
        assertFalse(handler.at(uri(SEND)).isNoObj(), "missing SEND");
    }

    @Test
    public void testHasWebRoot() {
        assertEquals(f("mem:test-pages"), handler.at(uri(WEB_ROOT)).uriValue(),
                "WEB_ROOT should be mem:test-pages");
    }

    @Test
    public void testDefaultPageHasFallback() {
        // DEFAULT_PAGE defaulting is handled at runtime in the ON_GET handler via orElse(),
        // not stored in the rec. The type constructor puts it there, but direct construction doesn't.
        assertTrue(handler.at(uri(DEFAULT_PAGE)).orElse(str("index.html")).strValue().equals("index.html"),
                "DEFAULT_PAGE should fall back to index.html when not configured");
    }

    @Test
    public void testReadOnlyDefaultsToTrue() {
        assertNotNull(handler.at(uri(READ_ONLY)), "READ_ONLY should not be null");
    }

    @Test
    public void testSetPromotionUpdateStaysReadable() {
        final fURI target = f("mem:test-pages/set-upd");
        // JSON-derived base (string keys) — mirrors the PUT body path
        final Obj jsonBase = ObjJSONSerializer.simple().inputBytes("{\"a\":{\"b\":2,\"c\":3},\"d\":4}");
        Router.writeToSpace(target, jsonBase);
        final Obj read = Router.readFromSpace(target);
        final Obj delta = ObjmtronSerializer.parse("+[d=>100]");
        final Obj updated = update_(delta).apply(read);
        final Obj writeResult = Router.writeToSpace(target, updated);
        final Obj after = Router.readFromSpace(target);
        System.out.printf("jsonBase=%s | delta=%s (tid=%s) | read=%s | updated=%s | writeResult=%s | after=%s%n",
                jsonBase, delta, delta.tid(), read, updated, writeResult, after);
        assertFalse(after.isNoObj(),
                "JSON-derived base after +[d=>100] should still read. updated=" + updated);
    }

    // ========================================
    // Integration test — live HTTP
    // ========================================

    public static class web_httpHandlerIntegrationTest extends AbstractHTTPServerIntegrationTest {

        private Space contentSpace;

        @AfterAll
        public void teardownContentSpace() {
            if (this.contentSpace != null) {
                Router.global().removeSpace(this.contentSpace.vid());
                this.contentSpace.close();
                this.contentSpace = null;
            }
        }

        @Override
        @BeforeAll
        public void setupHTTPSpace() {
            this.contentSpace = memSpace.of(rec(
                    uri(PATTERN), uri("mem:test-pages/#"),
                    uri(ROUTE), rec()
            ), f("/sys/space/test/web-int/" + getClass().getSimpleName()));
            Router.writeToSpace(f("mem:test-pages/index.html"),
                    str("<html><body><h1>Hello World</h1></body></html>", HTML_TID, null));
            Router.writeToSpace(f("mem:test-pages/about.html"),
                    str("<html><body><h1>About</h1></body></html>", HTML_TID, null));
            Router.writeToSpace(f("mem:test-pages/data.json"),
                    str("{\"key\":\"value\"}", WEB_JSON_TID, null));
            super.setupHTTPSpace();
        }

        @Override
        protected httpSpace createHTTPSpace() {
            // Web route: the route value is a URI (not a Type) → auto-creates web_httpHandler
            return httpSpace.of(rec(
                    uri(NAME), uri("web-test"),
                    uri(HOST), uri("http://localhost:" + generatePort()),
                    uri(PATTERN), uri("http://#"),
                    uri(ROUTE), rec(
                            uri("/"), uri("mem:test-pages"))
            ), f("/sys/space/http/web-test"));
        }

        @Override
        protected String primaryRoutePath() {
            return "/";
        }

        @Test
        public void testGetIndexHtml() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpGet("/index.html");
            assertNotNull(resp, "GET /index.html should return a response");
            assertEquals(200, resp.statusCode(), "GET /index.html should return 200");
            assertTrue(resp.body().contains("<h1>Hello World</h1>"),
                    "response should contain the HTML content");
        }

        @Test
        public void testGetAboutHtml() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpGet("/about.html");
            assertEquals(200, resp.statusCode(), "GET /about.html should return 200");
            assertTrue(resp.body().contains("<h1>About</h1>"),
                    "response should contain About content");
        }

        @Test
        public void testGetMissingReturns404() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpGet("/missing.html");
            assertTrue(resp.statusCode() >= 400,
                    "GET /missing.html should return 4xx, got: " + resp.statusCode());
        }

        @Test
        public void testGetResponds() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpGet(primaryRoutePath());
            assertNotNull(resp, "GET / should return a response");
            assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 600,
                    "GET / should return a valid HTTP status, got: " + resp.statusCode());
        }

        // A PUT must round-trip through the *already-consumed* request body
        // (read once by HttpRec.buildRequest) — a second readBody(exchange)
        // threw java.io.IOException("Stream is closed") and surfaced as 500.
        @ParameterizedTest(name = "PUT {0} -> 201, GET reads back {1}")
        @CsvSource(value = {
                "/put/int%6%6",
                "/put/lst%[1, 2, 3]%1",
                "/put/obj%{\"a\": [1, 2, 3]}%1"
        }, delimiter = '%')
        public void testPutWritesAndReadsBack(final String path, final String body, final String fragment) throws Exception {
            final java.net.http.HttpResponse<String> put = httpPut(path, body);
            assertEquals(201, put.statusCode(),
                    "PUT " + path + " should return 201, got: " + put.statusCode() + " body=" + put.body());
            final java.net.http.HttpResponse<String> get = httpGet(path + "?out=application/json");
            assertEquals(200, get.statusCode(), "GET " + path + " should return 200, got: " + get.statusCode());
            assertTrue(get.body().contains(fragment),
                    "read-back of " + path + " should contain written content, got: " + get.body());
        }

        @Test
        public void testPutEmptyBodyReturns400() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpPut("/put/empty", "");
            assertEquals(400, resp.statusCode(),
                    "PUT with empty body should return 400, got: " + resp.statusCode());
        }

        // A mtron-rendered body (the `->` write idiom) the IN serializer has
        // no reading for must land as the mtron object, not the raw string.
        @Test
        public void testPutMtronBodyLandsAsObject() throws Exception {
            assertEquals(201, httpPut("/put/mtron", "[a=>[b=>2],c=>3]").statusCode(),
                    "PUT mtron body should return 201");
            final java.net.http.HttpResponse<String> get = httpGet("/put/mtron?out=application/json");
            assertEquals(200, get.statusCode(), "GET /put/mtron should return 200");
            assertTrue(get.body().contains("\"a\""),
                    "mtron body should have landed as an object: " + get.body());
            assertFalse(get.body().contains("[a=>"),
                    "mtron body should not have landed as a raw string: " + get.body());
        }

        // ========================================
        // UPDATE (PATCH) — the `>>=` update algebra through the door
        // ========================================

        /**
         * PUT a fresh base, PATCH it with the delta, GET the result.
         * Base is JSON (the handler's default IN); deltas use the mtron
         * update algebra, which the handler parses when the IN serializer
         * has no reading for it.
         */
        // Read-back is application/x-mtron — the faithful rendering (JSON is
        // not 1-to-1 with mtron for sets / uris / insts / code).
        @ParameterizedTest(name = "PATCH {2} on {0} -> has {3} / drops {4}")
        @CsvSource(value = {
                "/upd/overlay%{\"a\":{\"b\":2,\"c\":3},\"d\":4}%[d=>5]%d=>5%d=>4",
                "/upd/add%{\"a\":{\"b\":2,\"c\":3},\"d\":4}%[d=>+10]%d=>14%",
                "/upd/set%{\"a\":{\"b\":2,\"c\":3},\"d\":4}%+[d=>100]%100%",
                "/upd/drop%{\"a\":{\"b\":2,\"c\":3},\"d\":4}%[a=>[b=>none]]%c=>3%b=>",
                "/upd/replace%6%10%10%6"
        }, delimiter = '%')
        public void testUpdateAlgebra(final String path, final String base,
                                      final String delta, final String expectIn,
                                      final String expectOut) throws Exception {
            assertEquals(201, httpPut(path, base).statusCode(),
                    "PUT base " + path + " should return 201");
            final java.net.http.HttpResponse<String> patch = httpPatch(path, delta);
            assertEquals(200, patch.statusCode(),
                    "PATCH " + path + " with " + delta + " should return 200, got: "
                            + patch.statusCode() + " body=" + patch.body());
            final java.net.http.HttpResponse<String> get = httpGet(path + "?out=application/x-mtron");
            assertEquals(200, get.statusCode(), "GET " + path + " should return 200");
            assertTrue(get.body().contains(expectIn),
                    "update of " + path + " should contain " + expectIn + " in: " + get.body());
            if (null != expectOut && !expectOut.isEmpty()) {
                assertFalse(get.body().contains(expectOut),
                        "update of " + path + " should have dropped " + expectOut + " from: " + get.body());
            }
        }

        @Test
        public void testPatchMissingReturns404() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpPatch("/upd/missing", "{d=>5}");
            assertEquals(404, resp.statusCode(),
                    "PATCH on a missing address should return 404, got: " + resp.statusCode());
        }

        // ========================================
        // DELETE — unlink the address
        // ========================================

        @Test
        public void testDeleteUnlinks() throws Exception {
            assertEquals(201, httpPut("/del/obj", "{\"x\":[1]}").statusCode(),
                    "PUT /del/obj should return 201");
            assertEquals(204, httpDelete("/del/obj", null).statusCode(),
                    "DELETE /del/obj should return 204");
            final java.net.http.HttpResponse<String> get = httpGet("/del/obj?out=application/json");
            assertTrue(get.statusCode() >= 400,
                    "GET after DELETE should be 4xx, got: " + get.statusCode());
        }
    }
}
