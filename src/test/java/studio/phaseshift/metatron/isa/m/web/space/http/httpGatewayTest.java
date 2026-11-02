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

package studio.phaseshift.metatron.isa.m.web.space.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/**
 * THE WEB GATEWAY IS A CLIENT SOMEONE CONSTRUCTED. {@code http_client::[host=><https://#>]} is a peer for every https
 * authority: an https read goes through its {@code from} (a GET over tls) and an https write through its {@code to} (a
 * POST), and a read with no client to go through answers nothing — a read never dials. A client built for one site
 * answers for that site before the wildcard does.
 * <p>
 * The far side is a plain JDK {@link HttpsServer} with the self-signed test certificate ({@link TlsTestKeys}), trusted
 * through the JDK's default context — which Jsoup and HttpClient both take — for the class.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class httpGatewayTest extends AbstractMetatronTest {

    private static final String GATEWAY = "https://#";
    private static final String MOTTO = "address first, then tls";
    private static final AtomicReference<String> POSTED = new AtomicReference<>();

    private static HttpsServer far;
    private static SSLContext jdkDefault;

    @BeforeAll
    public static void setup() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        jdkDefault = SSLContext.getDefault();
        SSLContext.setDefault(TlsTestKeys.trusting());
        far = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        far.setHttpsConfigurator(new HttpsConfigurator(httpServer.tls(rec(
                uri(KEYSTORE), uri(TlsTestKeys.keystore().toAbsolutePath().toString()),
                uri(PASS), str(TlsTestKeys.PASS)))));
        far.createContext("/motto", exchange -> respond(exchange, MIME.MIMEType.TEXT_PLAIN.value, MOTTO));
        far.createContext("/decide", exchange -> {
            POSTED.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, MIME.MIMEType.APPLICATION_JSON.value, "{\"ok\":true}");
        });
        far.start();
    }

    @AfterAll
    public static void teardown() {
        Machine.current().network().drop(f(GATEWAY));
        if (null != far)
            far.stop(0);
        if (null != jdkDefault)
            SSLContext.setDefault(jdkDefault);
    }

    private static void respond(final HttpExchange exchange, final String contentType, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String far() {
        return HTTPS + "://127.0.0.1:" + far.getAddress().getPort();
    }

    private static Network network() {
        return Machine.current().network();
    }

    /**
     * a wildcard authority covers the services its pattern matches — a host wildcard any host, a port only that port —
     * and a literal authority covers nothing (an exact match is sameService's)
     */
    @ParameterizedTest
    @CsvSource(value = {
            "http://#                  % http://markorodriguez.com      % true",
            "http://#                  % http://markorodriguez.com:8080 % true",
            "http://#                  % https://markorodriguez.com     % false",
            "https://#                 % https://127.0.0.1:8443         % true",
            "http://+:8777             % http://ginger.local:8777       % true",
            "http://+:8777             % http://ginger.local:8555       % false",
            "http://markorodriguez.com % http://markorodriguez.com      % false",
    }, delimiter = '%')
    void testAWildcardCoversWhatItMatches(final String authority, final String service, final boolean covers) {
        assertEquals(covers, Network.Helper.covers(f(authority), f(service)), authority + " covers " + service);
    }

    @Test
    public void testAReadWithNoClientAnswersNothing() {
        network().drop(f(GATEWAY));
        // A READ NEVER DIALS: no client covers the address, so nothing answers it and no peer is left behind
        checkEquality(LOG, Machine.read(far() + "/motto"), noobj(), true);
        checkEquality(LOG, network().peerOf(f(far())), noobj(), true);
    }

    @Test
    public void testTheGatewayIsAPeer() {
        Network.Helper.connect(network(), f(GATEWAY));
        final Obj gateway = network().peer(f(GATEWAY).pathEncoding());
        assertFalse(gateway.isNoObj(), "the constructed gateway should be a peer");
        checkEquality(LOG, uri(Network.Helper.originOf(gateway)), uri(f(GATEWAY)), true);
        // a wildcard host names no one server to ask: the gateway is live
        assertTrue(Network.Helper.online(gateway), "the gateway should read live");
        // and it covers the far side, whose origin no client was built for
        assertSame(gateway, network().peerOf(f(far())), "the gateway should answer for an https origin");
    }

    /**
     * the boots' spelling: a client constructed from mtron with a wildcard host registers itself as the gateway peer
     */
    @Test
    public void testTheBootsSpellingConstructsTheGateway() {
        final fURI web = f("http://#");
        try {
            checkCodeEvaluate(LOG, "http_client::[host=><http://#>]", "noobj", "noobj");
            final Obj gateway = network().peer(web.pathEncoding());
            assertFalse(gateway.isNoObj(), "the constructed client should be a peer");
            assertSame(gateway, network().peerOf(f("http://markorodriguez.com")), "it should cover every http origin");
        } finally {
            network().drop(web);
        }
    }

    @Test
    public void testReadsHttpsThroughTheGateway() {
        Network.Helper.connect(network(), f(GATEWAY));
        final Obj motto = Machine.read(far() + "/motto");
        assertTrue(motto.isStr(), "the read should reach the tls server through the gateway, got: " + motto);
        assertEquals(MOTTO, motto.strValue());
    }

    @Test
    public void testWritesHttpsThroughTheGateway() {
        Network.Helper.connect(network(), f(GATEWAY));
        POSTED.set(null);
        final Obj written = ObjmtronSerializer.parse("<" + far() + "/decide?mimeq=" + MIME.MIMEType.APPLICATION_JSON.value + "> -> [verdict=>'tls']").apply();
        assertFalse(written.isFail(), "the post should reach the tls server through the gateway, got: " + written);
        assertEquals("{\"verdict\":\"tls\"}", POSTED.get());
    }

    @Test
    public void testASiteClientAnswersBeforeTheGateway() {
        Network.Helper.connect(network(), f(GATEWAY));
        final fURI site = f(far());
        Network.Helper.connect(network(), site);
        try {
            final Obj member = network().peerOf(site);
            checkEquality(LOG, uri(Network.Helper.originOf(member)), uri(Network.Helper.serviceOf(site)), true);
            assertEquals(MOTTO, Machine.read(far() + "/motto").strValue(), "the site's own client should answer the read");
        } finally {
            network().drop(site);
        }
        // the gateway answers again once the site's client is gone
        checkEquality(LOG, uri(Network.Helper.originOf(network().peerOf(site))), uri(f(GATEWAY)), true);
    }
}
