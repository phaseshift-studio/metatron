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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.type.MIME;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * The network dials https:// as it dials http://: a read of an undeclared https authority constructs the http client
 * peer on demand — the read is the dial — and answers through its {@code from} GET, over tls.
 * <p>
 * No httpspace is mounted here, so no space can answer in the network's place: an https:// read that the network
 * declined used to fall through to an http space's https companion, which hid that the dialer table had no https row.
 * The far side is a plain JDK {@link HttpsServer} with the self-signed test certificate ({@link TlsTestKeys}), trusted
 * through the JDK's default context for the class.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class httpsDialTest extends AbstractMetatronTest {

    private static final String MOTTO = "the read is the dial";
    private static HttpsServer far;
    private static SSLContext jdkDefault;

    @BeforeAll
    public static void setup() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        jdkDefault = SSLContext.getDefault();
        SSLContext.setDefault(TlsTestKeys.trusting());
        far = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        far.setHttpsConfigurator(new HttpsConfigurator(httpSpace.tls(rec(
                uri(KEYSTORE), uri(TlsTestKeys.keystore().toAbsolutePath().toString()),
                uri(PASS), str(TlsTestKeys.PASS)))));
        far.createContext("/motto", exchange -> {
            final byte[] bytes = MOTTO.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, MIME.MIMEType.TEXT_PLAIN.value);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        far.start();
    }

    @AfterAll
    public static void teardown() {
        if (null != far)
            far.stop(0);
        if (null != jdkDefault)
            SSLContext.setDefault(jdkDefault);
    }

    @Test
    public void testReadDialsAnHttpsPeer() {
        final fURI authority = f(HTTPS + "://127.0.0.1:" + far.getAddress().getPort());
        final fURI key = Network.Helper.keyOf(authority);
        checkEquality(LOG, Machine.current().network().peerAt(key), noobj(), true);
        assertEquals(MOTTO, Machine.read(authority + "/motto").strValue(), "the https read should answer through the dialed peer");
        checkEquality(LOG, Machine.current().network().peerAt(key), noobj(), false);
    }
}
