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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Characterization of {@code httpSpace}'s route dispatch — the three-lane ladder — pinned *before* it is
 * replaced by protocol projections (P1). One route per lane, asserted by the lane's observable consequence:
 * <ul>
 *   <li>{@code /mcp} — a {@code Type} target resolves (as an {@code mcp_server} type it is materialized) and is
 *   wrapped in the mcp transport, so a json-rpc POST handshakes;</li>
 *   <li>{@code /content} — a uri target becomes a web root and serves;</li>
 * </ul>
 * Handler *behaviour* is not re-characterized — {@code mcp_mtronTest}, {@code web_httpHandlerTest} and
 * {@code mtron_httpHandlerTest} own that. This pins only what the ladder wires up, which is exactly what P1
 * replaces: if a lane changes which handler a mount gets, one of these four fails.
 * <p>
 * Two constraints learned the hard way. (1) A route value must be uri-convertible *and* its vid must fall
 * inside a live space's pattern: the constructor logs {@code r.second().uriValue()}, so a value that cannot
 * convert throws inside the constructor, is caught, logged as "server not started" — and then every request
 * times out rather than failing. An instance-valued route is therefore only expressible by writing the
 * instance into a space and naming that uri. (2) Bind and client on the same address family: the integration
 * base derives its client host from {@code HOST}, so {@code 127.0.0.1} keeps both on IPv4. Boots on an
 * ephemeral port, so it never collides with a running server — which is why {@code httpSpaceTest} is excluded
 * from CI and this one need not be.
 * <p>
 * The lanes are: (a) a {@code Type} target — which, when it is an {@code mcp_server}, is materialized into an
 * instance and then wrapped by the same {@code instanceof mcpServer} branch a literal instance would take, so
 * {@code /mcp} covers both; (b) a uri target, which becomes a web root; (c) no match at all.
 * <p>
 * Every route value must be uri-convertible *and* resolve in the Router — the invariant the base class's
 * {@code testHandlerTypeIsRegisteredInRouter} encodes. Violating the first kills the server at boot (the
 * constructor logs the value as a uri and its {@code catch} reports "server not started"); violating the
 * second fails that inherited test. Both are things {@code route::T} should enforce at boot rather than
 * leave to be discovered.
 * <p>
 * Lane (c) is **not exercised, because it is unreachable**: every route value must be uri-convertible (the
 * constructor logs {@code r.second().uriValue()}), and any value that could reach the no-lane branch is by
 * definition not a uri — so it throws at boot and takes the whole server down before dispatch ever happens.
 * The lane is dead code guarded by a crash; P1 replaces it with a boot-time error.
 */
@Timeout(60)
class httpRouteLadderTest extends AbstractHTTPServerIntegrationTest {

    private static final String INITIALIZE =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

    @Override
    protected httpSpace createHTTPSpace() {
        // The space the templated mounts below actually address: a memspace over /data/#, holding people. Without
        // it the route expands to an address nothing owns and every request is a 404 — a mount is only as real as
        // the space behind it.
        memSpace.of(rec(uri(PATTERN), uri(f("/data/#"))), f("/sys/space/test/data"));
        studio.phaseshift.metatron.isa.mach.type.Router.writeToSpace(f("/data/person/34"),
                rec(uri("name"), str("Ada"), uri("born"), jnt(1815)));
        studio.phaseshift.metatron.isa.mach.type.Router.writeToSpace(f("/data/person/35"),
                rec(uri("name"), str("Grace"), uri("born"), jnt(1906)));
        return httpSpace.of(rec(
                uri(PATTERN), uri(f("/test/httpRouteLadder/#")),
                uri(HOST), uri(f("http://127.0.0.1:" + generatePort())),
                uri(ROUTE), rec(
                        uri(f("/mcp")), uri(f("mcp_mtron")),
                        uri(f("/content")), uri(f("/m/web/helper")),
                        // two templated mounts, addressing the same leaf by two different spellings
                        uri(f("/name")), ObjmtronSerializer.parse("</m/web/${name()}>"),
                        uri(f("/pos")), ObjmtronSerializer.parse("</m/web/${as(rec::T).>>path/2}>"),
                        // a templated mount over real data: /person/34 => /data/person/34
                        uri(f("/person")), ObjmtronSerializer.parse("</data/person/${name()}>"))),
                f("/sys/space/test/httpRouteLadder"));
    }

    /**
     * A templated mount addresses <b>per request</b>: the request uri is the route value's lhs, so
     * {@code ${name()}} is the tail segment and {@code ${as(rec::T).>>path/2}} the third one (0-indexed, with an
     * empty leading element — for {@code /pos/helper} that is {@code helper}). The last row is the discriminator:
     * the same mount, a different address, a different outcome. A value resolved once at boot could only ever
     * answer one of these three, so nothing short of per-request resolution passes all three.
     * <p>
     * The mount keys are literal prefixes because {@code HttpServer.createContext} matches literally — a wildcard
     * key ({@code /name/#}) would create a context that matches no path at all. Nothing is lost: the value's
     * expressions see the whole request uri, so the pattern never has to carry the capture.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/name/helper % 200 % ${name()} addresses the request's tail segment",
            "/pos/helper  % 200 % ${as(rec::T).>>path/2} addresses the same segment positionally",
            "/name/nope   % 404 % one mount, another address, another outcome — per request, not per boot",
    }, delimiter = '%')
    void testTemplatedMountAddressesPerRequest(final String path, final int status, final String desc) throws Exception {
        assertEquals(status, httpGet(path).statusCode(), desc);
    }

    /**
     * A served document carries a revalidation policy. Responses have no validator of their own (no ETag, no
     * Last-Modified), so without this a client that cached a bad copy has nothing to check it against and can keep
     * serving it — which is exactly what happened after the binary read was fixed: the page's image refreshed and
     * the tab icon, the same file, did not. "no-cache" means revalidate, not "do not cache".
     */
    @Test
    void testServedContentRevalidates() throws Exception {
        final var response = httpGet("/content");
        assertEquals(200, response.statusCode());
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse("<absent>"),
                "a served document must be revalidated before reuse: " + response.headers().map());
    }

    /**
     * A request whose uri the space cannot parse must still be <em>answered</em>. The failure is logged and then
     * the response is attempted; when that attempt itself fails nothing used to complete the exchange, so the
     * client received zero bytes and waited out its own timeout (observed live on
     * {@code /marko/metatron/command?dom=}, an empty query value, which {@code uri(...)} rejects).
     * <p>
     * The assertion is deliberately about <em>completion</em> rather than a status code: any response, or a
     * refused connection, is correct behaviour here; a timeout is not, and is the only outcome this test rejects.
     */
    @Test
    void testUnparseableRequestUriIsStillAnswered() {
        final var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl() + "/content?dom="))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            final var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            LOG.info("unparseable request answered with %d", response.statusCode());
        } catch (final java.net.http.HttpTimeoutException e) {
            throw new AssertionError("the server must answer or refuse, never leave the client waiting: " + e);
        } catch (final IOException e) {
            LOG.info("unparseable request connection ended without a response (%s) — acceptable, it did not hang",
                    e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * A templated mount over a real space: one mount, two requests, two different people. The route value is
     * evaluated with the request uri as its lhs, so {@code ${name()}} is the segment the client asked for and the
     * address is resolved per request — the same handler serving {@code Ada} and {@code Grace} in turn, and a 404
     * for a person who is not there.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/person/34 % 200 % Ada   % the first person, addressed by the request",
            "/person/35 % 200 % Grace % the second, through the same mount and the same handler",
            "/person/99 % 404 % -     % nobody there: a live space, not a lookup table of two",
    }, delimiter = '%')
    void testTemplatedMountOverRealData(final String path, final int status, final String body,
                                        final String desc) throws Exception {
        final var response = httpGet(path);
        assertEquals(status, response.statusCode(), desc);
        if (!"-".equals(body))
            assertTrue(response.body().contains(body), desc + ": expected " + body + " in " + response.body());
    }

    /**
     * lane 1: a Type target is materialized and wrapped in the mcp transport
     */
    @Test
    void testMcpTypeLaneHandshakes() throws Exception {
        final var response = httpPost("/mcp", INITIALIZE, null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(true, response.body().contains("protocolVersion"), response.body());
    }

    /**
     * lane 3: a uri target whose read yields a non-type (here a const rec) becomes a web root and serves.
     * Note that a uri whose read yields a *type* takes the handler lane instead and 500s, because the type
     * has no constructor to build an HttpRec from.
     */
    @Test
    void testUriLaneServes() throws Exception {
        assertEquals(200, httpGet("/content").statusCode());
    }

    /**
     * A uri target that resolves to nothing still registers a web handler whose root is empty, so the request
     * is served as a 404 rather than falling through to no handler at all.
     */
    @Test
    void testUnresolvableTargetIs404() throws Exception {
        assertEquals(404, httpGet("/missing").statusCode());
    }
}
