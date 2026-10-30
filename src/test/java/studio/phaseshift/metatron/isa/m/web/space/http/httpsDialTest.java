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

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

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
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/**
 * The network dials https:// as it dials http://: a dial of an https authority constructs the http client peer (the
 * dialer table's https row), and a read through it answers by its {@code from} GET, over tls. Construction is
 * connection — a read alone dials nothing: addressing an authority no client was constructed for makes no peer.
 * <p>
 * No gateway client ({@code http_client::[host=><https://#>]}) is constructed here, so only the dialed peer can answer:
 * a read the network declines is answered by nothing at all.
 * The far side is a plain JDK {@link HttpsServer} with the self-signed test certificate ({@link TlsTestKeys}), trusted
 * through the JDK's default context for the class.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class httpsDialTest extends AbstractMetatronTest {

    private static final String MOTTO = "construction is connection";
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
    public void testAnHttpsDialReadsOverTls() {
        final fURI authority = f(HTTPS + "://127.0.0.1:" + far.getAddress().getPort());
        final fURI key = authority.pathEncoding();
        final Network network = Machine.current().network();
        checkEquality(LOG, network.peer(key), noobj(), true);
        // A READ NEVER DIALS: the network answers nothing for an authority no client was constructed for, and the
        // read leaves no peer behind
        checkEquality(LOG, network.read(f(authority + "/motto")), noobj(), true);
        checkEquality(LOG, network.peer(key), noobj(), true);
        // the dial constructs the http client, and a read through it answers over tls
        Network.Helper.connect(network, authority);
        try {
            checkEquality(LOG, network.peer(key), noobj(), false);
            assertEquals(MOTTO, Machine.read(authority + "/motto").strValue(), "the https read should answer through the dialed peer");
        } finally {
            network.drop(authority);
        }
    }
}
