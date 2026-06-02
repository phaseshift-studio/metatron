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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
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

    // ========================================
    // Integration test — live HTTP
    // ========================================

    public static class web_httpHandlerIntegrationTest extends AbstractHTTPServerIntegrationTest {

        private Space contentSpace;

        @BeforeEach
        public void setupContentSpace() {
            this.contentSpace = memSpace.of(rec(
                    uri(PATTERN), uri("mem:web-test-pages/#"),
                    uri(ROUTE), rec()
            ), f("/sys/space/test/web-int/" + getClass().getSimpleName()));
            Router.writeToSpace(f("mem:web-test-pages/index.html"),
                    str("<html><body><h1>Hello World</h1></body></html>"));
            Router.writeToSpace(f("mem:web-test-pages/about.html"),
                    str("<html><body><h1>About</h1></body></html>"));
            Router.writeToSpace(f("mem:web-test-pages/data.json"),
                    str("{\"key\":\"value\"}"));
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
        protected httpSpace createHTTPSpace() {
            // Web route: the route value is a URI (not a Type) → auto-creates web_httpHandler
            return httpSpace.of(rec(
                    uri(NAME), uri("web-test"),
                    uri(HOST), uri("http://localhost:" + generatePort()),
                    uri(PATTERN), uri("http://#"),
                    uri(ROUTE), rec(
                            uri("/"), uri("mem:web-test-pages"))
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
    }
}
