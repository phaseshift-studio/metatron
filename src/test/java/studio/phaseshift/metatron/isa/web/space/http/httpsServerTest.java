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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.sys.space.fsSpace;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mtron_httpHandler.MTRON_HTTP_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.HTML_TYPE;

/**
 * An http_server whose host is {@code https://} serves tls with the certificate in its {@code keystore}, opened with its
 * {@code pass}. The whole {@link AbstractHTTPServerIntegrationTest} suite runs here over tls; what is particular to tls
 * is below: plaintext is refused, the {@code https://#} gateway client reads what it serves, the authority it answers on
 * keeps its scheme, the listener entry keeps no secret, and every keystore that cannot serve fails the construction at
 * boot, before a port is bound.
 * <p>
 * The certificate is self-signed ({@link TlsTestKeys}), so a client has to be told to trust it: the suite's client
 * through {@link #createHttpClient()}, and the gateway client — Jsoup and HttpClient, which both take the JDK's default
 * context — through {@link SSLContext#setDefault}, restored after the class.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class httpsServerTest extends AbstractHTTPServerIntegrationTest {

    private static final String GATEWAY = "https://#";
    /**
     * isolated copy of the test web resources: a write through a route lands here, never in src/test/resources/
     */
    private static final Path WEB_DATA_DIR = Path.of("target", "test-https-data");

    private SSLContext jdkDefault;
    /**
     * the socket a test built from mtron, released after it
     */
    private int constructed = -1;

    @Override
    protected Listener createHTTPServer() {
        try {
            final File web = WEB_DATA_DIR.resolve("web").toFile();
            if (web.exists())
                CommonUtil.deleteDirectory(web.toPath());
            web.mkdirs();
            CommonUtil.copyDirectory(Path.of("src", "test", "resources", "web"), web.toPath());
            // the keystore again, beside the served root rather than in it: read through the fsspace, it is bytes
            Files.createDirectories(WEB_DATA_DIR.resolve("tls"));
            Files.copy(TlsTestKeys.keystore(), WEB_DATA_DIR.resolve("tls").resolve("server.p12"), StandardCopyOption.REPLACE_EXISTING);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        fsSpace.of(FileSystems.getDefault(), rec(
                uri(PATTERN), uri("local:#"),
                uri(ROUTE), rec(uri("local:"), uri(WEB_DATA_DIR + "/"))), f("/sys/space/fs"));
        return httpServer.of(rec(
                uri(HOST), uri("https://127.0.0.1:" + generatePort()),
                uri(KEYSTORE), uri(TlsTestKeys.keystore().toAbsolutePath().toString()),
                uri(PASS), str(TlsTestKeys.PASS),
                uri(ROUTE), rec(
                        uri("/mtron"), uri(MTRON_HTTP_TID.toString()),
                        uri("/"), uri("local:web"))));
    }

    @Override
    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder().sslContext(TlsTestKeys.trusting()).build();
    }

    @BeforeAll
    public void trustTheTestCertificate() throws Exception {
        this.jdkDefault = SSLContext.getDefault();
        SSLContext.setDefault(TlsTestKeys.trusting());
        // the https gateway: the client the server's own address is read back through
        Machine.current().network().connect(f(GATEWAY));
    }

    @AfterAll
    public void restoreTheJdkTrust() {
        Machine.current().network().disconnect(f(GATEWAY));
        SSLContext.setDefault(this.jdkDefault);
    }

    @AfterEach
    public void releaseConstructed() {
        if (-1 != this.constructed)
            Machine.current().network().unlisten(f("https://127.0.0.1:" + this.constructed));
        this.constructed = -1;
    }

    private static String listener(final int port) {
        return "~/network/listener/https/localhost/" + port;
    }

    // ========================================
    // the server half
    // ========================================

    @Test
    public void testServesOverTls() throws Exception {
        final HttpResponse<String> response = this.httpGet("/index.html");
        assertEquals(200, response.statusCode(), "the page should be served over tls");
        assertTrue(response.body().contains("<html"), "the page should be the html document");
        assertTrue(response.sslSession().isPresent(), "the response should have come over a tls session");
    }

    @Test
    public void testRefusesPlaintext() {
        try (final HttpClient plaintext = HttpClient.newHttpClient()) {
            assertThrows(IOException.class, () -> plaintext.send(HttpRequest.newBuilder()
                            .uri(java.net.URI.create(HTTP + "://" + this.httpHost + ":" + this.httpPort + "/index.html"))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()), "a tls listener must not answer plaintext http");
        }
    }

    @Test
    public void testListensOnItsHttpsAuthority() {
        assertFalse(this.server.closed(), "a tls server is a listener");
        assertTrue(Network.Helper.authorities().contains(f(this.baseUrl())),
                "the authority this machine answers on should keep its https scheme: " + Network.Helper.authorities());
    }

    /**
     * the certificate is the socket's: the listener entry any reader of ~/network/listener sees carries the server, its
     * authority and its routes, and neither the keystore nor its pass
     */
    @Test
    public void testTheListenerEntryKeepsNoSecret() {
        final Obj entry = Machine.read(f(listener(this.httpPort)));
        assertFalse(entry.isNoObj(), "the server should have a listener entry");
        assertFalse(entry.toString().contains(TlsTestKeys.PASS), "the listener entry must not carry the pass: " + entry);
        checkEquality(LOG, Machine.read(f(listener(this.httpPort) + "/" + KEYSTORE)), noobj(), true);
        checkEquality(LOG, Machine.read(f(listener(this.httpPort) + "/" + PASS)), noobj(), true);
    }

    // ========================================
    // the client half: the gateway reads the server's tls address
    // ========================================

    @Test
    public void testReadsItsOwnHttpsAddress() {
        final Obj page = Machine.read(this.baseUrl() + "/index.html");
        assertTrue(page.test(HTML_TYPE), "the https gateway should fetch the html page over tls, got: " + page);
    }

    // ========================================
    // construction from mtron
    // ========================================

    /**
     * the user-facing form: built from mtron — the keystore a path, or read by address as its bytes — an https host
     * serves tls on the port it names, from its listener entry
     */
    @ParameterizedTest
    @CsvSource(value = {
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=><{{keystore}}>,pass=>'{{pass}}',route=>[/=><local:web>]]",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=>*<local:tls/server.p12>,pass=>'{{pass}}',route=>[/=><local:web>]]",
    }, delimiter = '%')
    public void testMtronConstructionServesTls(final String construction) throws Exception {
        final int port = generatePort();
        this.constructed = port;
        checkCodeEvaluate(LOG, fill(construction, port), "*<" + listener(port) + "/authority>", "<https://127.0.0.1:" + port + ">");
        final HttpResponse<String> response = this.httpClient.send(HttpRequest.newBuilder()
                        .uri(java.net.URI.create(HTTPS + "://127.0.0.1:" + port + "/index.html"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the mtron-built server should serve over tls");
        assertTrue(response.sslSession().isPresent(), "the response should have come over a tls session");
    }

    /**
     * a keystore that cannot serve fails the construction at boot — missing, unopened, misopened, absent from disk,
     * holding no private key, or neither a path nor bytes — and since the certificate is loaded before the bind,
     * no listener entry is made and the port was never held
     */
    @ParameterizedTest
    @CsvSource(value = {
            "http_server::[host=><https://127.0.0.1:{{port}}>]                                                    % <ERROR>",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=><{{keystore}}>]                           % <ERROR>",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=><{{keystore}}>,pass=>'out-of-tune']       % <ERROR>",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=><{{keystore}}.gone>,pass=>'{{pass}}']     % <ERROR>",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=><{{certificate_only}}>,pass=>'{{pass}}'] % <ERROR>",
            "http_server::[host=><https://127.0.0.1:{{port}}>,keystore=>8443,pass=>'{{pass}}']                    % <ERROR>",
    }, delimiter = '%')
    public void testUnservableKeystoreFailsConstruction(final String construction, final String expected) throws IOException {
        final int port = generatePort();
        checkCodeEvaluate(LOG, fill(construction, port), "noobj", expected);
        checkEquality(LOG, Machine.current().network().listenerAt(Network.Helper.keyOf(f("https://127.0.0.1:" + port))), noobj(), true);
        // a port the failed construction held would refuse this bind
        new ServerSocket(port, 50, InetAddress.getLoopbackAddress()).close();
    }

    private static String fill(final String construction, final int port) {
        return construction
                .replace("{{port}}", String.valueOf(port))
                .replace("{{keystore}}", TlsTestKeys.keystore().toAbsolutePath().toString())
                .replace("{{certificate_only}}", TlsTestKeys.certificateOnly().toAbsolutePath().toString())
                .replace("{{pass}}", TlsTestKeys.PASS);
    }
}
