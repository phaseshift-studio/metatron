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

package studio.phaseshift.metatron.isa.web.space;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * A listener (a server that binds a socket) either holds its port or fails its construction, and its presence is the
 * network's {@code listener} entry, so a construction that fails leaves none.
 * <p>
 * Java-WebSocket binds on its own selector thread and reports a lost bind only to {@code onError(null, ex)}, so a
 * websocket server on a port already in use once constructed "successfully" while nothing listened, and the failure
 * surfaced later, as a dial's unrelated {@code socket ... is not connected}: a {@code ws_server} awaits its bind. An
 * {@code http_server} binds synchronously in {@code HttpServer.create}. One row per server.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class ListenerBindTest extends AbstractMetatronTest {

    @BeforeAll
    public static void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

    private static fURI socket(final String scheme, final int port) {
        return f(scheme + "://127.0.0.1:" + port);
    }

    private static Obj entry(final String scheme, final int port) {
        return Machine.current().network().listenerAt(Network.Helper.keyOf(socket(scheme, port)));
    }

    /**
     * a lost bind fails the construction, and leaves no presence behind
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws   % ws_server",
            "http % http_server",
    }, delimiter = '%')
    public void testOccupiedPortFailsConstruction(final String scheme, final String server) throws IOException {
        try (final ServerSocket occupant = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            final int port = occupant.getLocalPort();
            checkCodeEvaluate(LOG, server + "::[host=><" + socket(scheme, port) + ">]", "noobj", "<ERROR>");
            checkEquality(LOG, entry(scheme, port), noobj(), true);
        }
    }

    /**
     * a free port is listened on the moment the construction returns, and the socket is this machine's presence: its
     * listener entry — whether the server is constructed by its type's name or, as the boot helpers
     * ({@code boot/space/web/*.mtron}) build theirs, by splitting the host they are applied to into a config and casting
     * it
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws   % ws_server::[host=><{{socket}}>]",
            "http % http_server::[host=><{{socket}}>]",
            "ws   % <{{socket}}>.-<([host => _]).as(ws_server::T)",
            "http % <{{socket}}>.-<([host => _]).as(http_server::T)",
    }, delimiter = '%')
    public void testFreePortListens(final String scheme, final String construction) throws IOException {
        final int port = generatePort();
        try {
            checkCodeEvaluate(LOG, construction.replace("{{socket}}", socket(scheme, port).toString()),
                    "*<~/network/listener/" + scheme + "/localhost/" + port + "/authority>", "<" + socket(scheme, port) + ">");
            // no settle: a dial right away must find the socket bound
            new Socket(InetAddress.getLoopbackAddress(), port).close();
        } finally {
            Machine.current().network().unlisten(socket(scheme, port));
        }
    }

    /**
     * a route is keyed by the path it serves: a table that cannot route fails the construction, and the port the
     * server bound for it is released with it
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws   % ws_server",
            "http % http_server",
    }, delimiter = '%')
    public void testUnroutableTableFailsConstruction(final String scheme, final String server) throws IOException {
        final int port = generatePort();
        checkCodeEvaluate(LOG, server + "::[host=><" + socket(scheme, port) + ">,route=>[13=>/usr]]", "noobj", "<ERROR>");
        checkEquality(LOG, entry(scheme, port), noobj(), true);
        // a port the failed construction still held would refuse this bind
        new ServerSocket(port, 50, InetAddress.getLoopbackAddress()).close();
    }
}
