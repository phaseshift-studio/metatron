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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * A listener (a space that binds a server socket) either holds its port or fails its construction.
 * <p>
 * Java-WebSocket binds on its own selector thread and reports a lost bind only to {@code onError(null, ex)}, so a
 * {@code wsspace} on a port already in use used to construct "successfully": the space mounted at its vid while
 * nothing listened, and the failure surfaced later, as a dial's unrelated {@code socket ... is not connected}.
 * {@code httpspace} binds synchronously in {@code HttpServer.create}; its rows pin that it stays loud.
 * <p>
 * {@code {{port}}} is the row's port: held by a plain socket in the occupied rows, a {@link #generatePort()} pick in
 * the free ones. Whether a space is mounted is asked of the memory, not of a read: {@code [..]@vid} anchors the config
 * rec at the vid whether or not the cast to a space succeeds, so the vid reads back the rec either way.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class ListenerBindTest extends AbstractMetatronTest {

    /**
     * where the rows mount their listeners, unmounted after every row so no socket outlives it
     */
    private static final List<String> VIDS = List.of("/sys/space/test/listener_ws", "/sys/space/test/listener_http");

    @BeforeAll
    public static void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

    @AfterEach
    public void unmount() {
        VIDS.forEach(vid -> {
            final Obj space = mounted(vid);
            Machine.current().memory().removeSpace(f(vid));
            CommonUtil.close(space);
        });
    }

    /**
     * a lost bind fails the construction, and nothing is mounted at the vid
     */
    @ParameterizedTest
    @CsvSource(value = {
            "wsspace::[pattern=><ws://127.0.0.1:{{port}}/#>,host=><ws://127.0.0.1:{{port}}>]@/sys/space/test/listener_ws         % /sys/space/test/listener_ws",
            "httpspace::[pattern=><http://127.0.0.1:{{port}}/#>,host=><http://127.0.0.1:{{port}}>]@/sys/space/test/listener_http % /sys/space/test/listener_http",
    }, delimiter = '%')
    public void testOccupiedPortFailsConstruction(final String construction, final String vid) throws IOException {
        try (final ServerSocket occupant = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            checkCodeEvaluate(LOG, construction.replace("{{port}}", String.valueOf(occupant.getLocalPort())), "noobj", "<ERROR>");
            checkEquality(LOG, mounted(vid), noobj(), true);
        }
    }

    /**
     * a free port mounts the space, and it is listening the moment the construction returns
     */
    @ParameterizedTest
    @CsvSource(value = {
            "wsspace::[pattern=><ws://127.0.0.1:{{port}}/#>,host=><ws://127.0.0.1:{{port}}>]@/sys/space/test/listener_ws         % /sys/space/test/listener_ws   % */sys/space/test/listener_ws>>host   % <ws://127.0.0.1:{{port}}>",
            "httpspace::[pattern=><http://127.0.0.1:{{port}}/#>,host=><http://127.0.0.1:{{port}}>]@/sys/space/test/listener_http % /sys/space/test/listener_http % */sys/space/test/listener_http>>host % <http://127.0.0.1:{{port}}>",
    }, delimiter = '%')
    public void testFreePortListens(final String construction, final String vid, final String fetch, final String expected) throws IOException {
        final int port = generatePort();
        checkCodeEvaluate(LOG, construction.replace("{{port}}", String.valueOf(port)), fetch, expected.replace("{{port}}", String.valueOf(port)));
        checkEquality(LOG, mounted(vid), noobj(), false);
        // no settle: a dial right away must find the socket bound
        new Socket(InetAddress.getLoopbackAddress(), port).close();
    }

    /**
     * a listener's session cache is its own — read and written through the listener, behind its q-procs — and never
     * claims an address: while the listener listens, it alone claims its pattern
     */
    @ParameterizedTest
    @CsvSource(value = {
            "wsspace::[pattern=><ws://127.0.0.1:{{port}}/#>,host=><ws://127.0.0.1:{{port}}>]@/sys/space/test/listener_ws         % /sys/space/test/listener_ws   % ws://127.0.0.1:{{port}}/#",
            "httpspace::[pattern=><http://127.0.0.1:{{port}}/#>,host=><http://127.0.0.1:{{port}}>]@/sys/space/test/listener_http % /sys/space/test/listener_http % http://127.0.0.1:{{port}}/#",
    }, delimiter = '%')
    public void testSessionCacheClaimsNoAddress(final String construction, final String vid, final String pattern) {
        final String port = String.valueOf(generatePort());
        final Obj listener = this.listen(construction.replace("{{port}}", port), vid);
        assertEquals(List.of(listener), claimants(f(pattern.replace("{{port}}", port))), "the listener alone should claim its pattern");
    }

    /**
     * a closed listener is unmounted: memory no longer holds it at its vid, and nothing of it — the listener itself or
     * its session cache — is left claiming its pattern around a stopped server
     */
    @ParameterizedTest
    @CsvSource(value = {
            "wsspace::[pattern=><ws://127.0.0.1:{{port}}/#>,host=><ws://127.0.0.1:{{port}}>]@/sys/space/test/listener_ws         % /sys/space/test/listener_ws   % ws://127.0.0.1:{{port}}/#",
            "httpspace::[pattern=><http://127.0.0.1:{{port}}/#>,host=><http://127.0.0.1:{{port}}>]@/sys/space/test/listener_http % /sys/space/test/listener_http % http://127.0.0.1:{{port}}/#",
    }, delimiter = '%')
    public void testClosedListenerClaimsNothing(final String construction, final String vid, final String pattern) {
        final String port = String.valueOf(generatePort());
        CommonUtil.close(this.listen(construction.replace("{{port}}", port), vid));
        checkEquality(LOG, mounted(vid), noobj(), true);
        assertEquals(List.of(), claimants(f(pattern.replace("{{port}}", port))), "nothing of a closed listener should claim its pattern");
    }

    /**
     * construct a listener from its mtron and answer the space mounted at its vid
     */
    private Obj listen(final String construction, final String vid) {
        checkCodeEvaluate(LOG, construction, "noobj", "noobj");
        final Obj listener = mounted(vid);
        checkEquality(LOG, listener, noobj(), false);
        return listener;
    }

    /**
     * the mounted spaces whose pattern is exactly this one
     */
    private static List<Obj> claimants(final fURI pattern) {
        return Machine.current().memory().spaces().jvm().values().stream()
                .filter(space -> space instanceof Space claimant && pattern.equals(claimant.pattern()))
                .toList();
    }
}
