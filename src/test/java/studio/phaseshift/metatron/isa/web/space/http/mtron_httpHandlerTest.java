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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.web.space.http.handler.mtron_httpHandler;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mtron_httpHandler.MTRON_HTTP_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_httpHandlerTest extends AbstractHTTPServerTest {

    @Override
    protected HttpRec createHandler(final fURI vid) {
        return new mtron_httpHandler(new LinkedHashMap<>(Map.of(
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value))), vid);
    }

    // ========================================
    // Handler key registration tests
    // ========================================

    @Test
    public void testHasOnGet() {
        assertFalse(handler.at(uri(ON_GET)).isNoObj(), "missing ON_GET");
    }

    @Test
    public void testHasOnPost() {
        assertFalse(handler.at(uri(ON_POST)).isNoObj(), "missing ON_POST");
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
    public void testHasClose() {
        assertFalse(handler.at(uri(CLOSE)).isNoObj(), "missing CLOSE");
    }

    // ========================================
    // Integration test — live HTTP
    // ========================================

    public static class mtron_httpHandlerIntegrationTest extends AbstractHTTPServerIntegrationTest {

        @Override
        protected httpSpace createHTTPSpace() {
            return httpSpace.of(rec(
                    uri(NAME), uri("mtron-test"),
                    uri(HOST), uri("http://localhost:" + generatePort()),
                    uri(PATTERN), uri("http://#"),
                    uri(ROUTE), rec(uri("/mtron"), uri(MTRON_HTTP_TID.toString()))
            ), f("/sys/space/http/mtron-test"));
        }

        @Test
        public void testGetResponds() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpGet(primaryRoutePath());
            assertNotNull(resp, "GET should return a response");
            assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 600,
                    "GET should return a valid HTTP status, got: " + resp.statusCode());
        }

        @Test
        public void testPostResponds() throws Exception {
            final java.net.http.HttpResponse<String> resp = httpPost(primaryRoutePath(),
                    "{\"test\":\"data\"}", null);
            assertNotNull(resp, "POST should return a response");
            assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 600,
                    "POST should return a valid HTTP status, got: " + resp.statusCode());
        }
    }
}
