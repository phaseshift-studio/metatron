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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * THE VM'S END IS EVERY LEVEL'S END. The root's close closes the root's own network level, but a frame that dialed
 * and was never popped holds a supervised level of its own — and its supervisor would otherwise keep ticking into
 * whatever boots next in this JVM. Shutting the VM down must leave no supervisor running.
 * <p>
 * A class of its own because it shuts the VM down mid-test (and boots it again for the harness's own teardown).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class NetworkSupervisorShutdownTest extends AbstractMetatronTest {

    @Test
    void testNoSupervisorOutlivesTheVM() {
        InstSet.importInstSet(WEB_ISA_TID);
        final int port = generatePort();
        final wsSpace server = wsSpace.of(rec(
                uri(NAME), uri("supervisor-shutdown"),
                uri(HOST), uri("ws://localhost:" + port),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
        ).jvm(), f("/sys/space/ws/supervisor-shutdown"));
        CommonUtil.sleepThread(300);
        try {
            // a dial at the root, and one in a frame that is never popped
            Machine.current().network().connect(f("ws://localhost:" + port + "/mtron"), WS_CLIENT_TID);
            Machine.current().push(f("never-popped"));
            Machine.current().network().connect(f("ws://localhost:" + port + "/mtron"), WS_CLIENT_TID);
            assertTrue(NetworkSupervisor.supervised() >= 2,
                    "both levels that dialed a link are supervised: " + NetworkSupervisor.supervised());
            AbstractMetatronTest.end();
            assertEquals(0, NetworkSupervisor.supervised(),
                    "the VM's shutdown releases every supervised level, popped or not");
        } finally {
            CommonUtil.close(server);
            AbstractMetatronTest.begin();
        }
    }
}
