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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.web.type.MIME;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * An {@code http://#} httpspace mounts its {@code https://#} twin: a server-less httpspace under its vid, the same
 * client over tls, that claims the scheme the space itself cannot (a space claims exactly one pattern) and goes when
 * the space goes.
 * <p>
 * The far side is a plain JDK {@link HttpsServer}, not an httpspace, so the companion is the only space that claims
 * {@code https://} here: whatever reaches it went through the companion. Its certificate is self-signed
 * ({@link TlsTestKeys}), so the JDK's default context — which Jsoup and HttpClient both take — trusts it for the class.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class httpsCompanionTest extends AbstractMetatronTest {

    private static final String VID = "/sys/space/test/companion";
    private static final String MOTTO = "address first, then tls";
    private static final AtomicReference<String> POSTED = new AtomicReference<>();

    private static httpSpace space;
    private static HttpsServer far;
    private static SSLContext jdkDefault;

    @BeforeAll
    public static void setup() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        space = httpSpace.of(rec(
                uri(QPROC), lst(QCollection.mimeQ()),
                uri(HOST), uri("http://127.0.0.1:" + generatePort()),
                uri(PATTERN), uri("http://#")), f(VID));
        jdkDefault = SSLContext.getDefault();
        SSLContext.setDefault(TlsTestKeys.trusting());
        far = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        far.setHttpsConfigurator(new HttpsConfigurator(httpSpace.tls(rec(
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
        if (null != far)
            far.stop(0);
        if (null != jdkDefault)
            SSLContext.setDefault(jdkDefault);
        if (null != space)
            space.close();
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

    /**
     * how many mounted spaces claim exactly this pattern — the companion, never a session cache beside it
     */
    private static long claimants(final fURI pattern) {
        return Machine.current().memory().spaces().jvm().values().stream()
                .filter(s -> s instanceof Space claimant && pattern.equals(claimant.pattern()))
                .count();
    }

    @Test
    public void testCompanionClaimsHttps() {
        final Obj mounted = mounted(VID + "/" + HTTPS);
        assertInstanceOf(httpSpace.class, mounted, "an http://# space should mount an https:// companion under its vid");
        final httpSpace companion = (httpSpace) mounted;
        assertEquals(f("https://#"), companion.pattern(), "the companion claims the twin of its space's pattern");
        assertFalse(companion.listens(), "the companion binds nothing, so it answers on nothing");
        checkEquality(LOG, companion.at(uri(QPROC)), space.at(uri(QPROC)), true);
        assertSame(companion, Machine.current().memory().getSpaceFor(f(far() + "/motto")), "https:// addresses route to the companion");
        assertSame(space, Machine.current().memory().getSpaceFor(f("http://127.0.0.1:8777/motto")), "http:// addresses still route to the space");
        assertEquals(1, claimants(f("https://#")), "only the companion claims https://#");
        assertEquals(1, claimants(f("http://#")), "only the space claims http://#");
    }

    @Test
    public void testReadsHttpsThroughTheCompanion() {
        final Obj motto = Machine.read(far() + "/motto");
        assertTrue(motto.isStr(), "the read should reach the tls server through the companion, got: " + motto);
        assertEquals(MOTTO, motto.strValue());
    }

    @Test
    public void testWritesHttpsThroughTheCompanion() {
        POSTED.set(null);
        final Obj response = ObjmtronSerializer.parse("<" + far() + "/decide?mimeq=" + MIME.MIMEType.APPLICATION_JSON.value + "> -> [verdict=>'tls']").apply();
        assertFalse(response.isFail(), "the post should reach the tls server through the companion, got: " + response);
        assertEquals("{\"verdict\":\"tls\"}", POSTED.get());
    }

    /**
     * the twin of a narrowed pattern is narrowed alike, and the companion is unmounted with its space — nothing of the
     * closed space, neither the companion nor either session cache, claims an address afterwards
     */
    @Test
    public void testCompanionClosesWithItsSpace() {
        final String vid = VID + "_narrowed";
        final int port = generatePort();
        final fURI http = f("http://127.0.0.1:" + port + "/#");
        final fURI https = f("https://127.0.0.1:" + port + "/#");
        final httpSpace narrowed = httpSpace.of(rec(
                uri(HOST), uri("http://127.0.0.1:" + port),
                uri(PATTERN), uri(http)), f(vid));
        try {
            final Obj companion = mounted(vid + "/" + HTTPS);
            assertInstanceOf(httpSpace.class, companion);
            assertEquals(https, ((httpSpace) companion).pattern());
            assertEquals(1, claimants(http));
            assertEquals(1, claimants(https));
        } finally {
            narrowed.close();
        }
        checkEquality(LOG, mounted(vid), noobj(), true);
        checkEquality(LOG, mounted(vid + "/" + HTTPS), noobj(), true);
        assertEquals(0, claimants(http), "nothing of the closed space should claim its http:// pattern");
        assertEquals(0, claimants(https), "nothing of the closed space should claim its https:// pattern");
        // the other space's companion is untouched
        assertInstanceOf(httpSpace.class, mounted(VID + "/" + HTTPS));
    }
}
