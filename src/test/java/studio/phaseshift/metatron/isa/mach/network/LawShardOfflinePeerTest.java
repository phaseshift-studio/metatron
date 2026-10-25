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

import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * {@code law_shard} PLANS OVER THE PEERS THAT ANSWER. A dialed peer whose link is down stays in the map while it is
 * redialed, but it is no shard target: a part shipped down it would come back {@code not connected} and poison the
 * reduction. So an outage shrinks the fan-out, and the fold completes on what is left — here, at home.
 * <p>
 * The peer is this JVM's own machine behind a real websocket (the {@code LawShardPeerAvailabilityTest} self-dial),
 * so the outage is the server stopping.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class LawShardOfflinePeerTest extends AbstractMetatronTest {

    private static final String FOLD = "{1,2,3,4}.plus(1).sum()";

    @Test
    void testAnOfflinePeerIsNoShardTarget() throws InterruptedException {
        // typing is not solved across distributed executions yet — run type-check-free, as the manual demo did
        TypeCheck.disable(TypeCheck.values());
        // catRewrite.law_shard is registered by catInstSet, which the standard test boot does not mount
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        InstSet.importInstSet(WEB_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        final int port = generatePort();
        final fURI service = f("ws://localhost:" + port);
        final Listener server = wsServer.of(rec(
                uri(NAME), uri("law-shard-offline-peer"),
                uri(HOST), uri(service),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
        ));
        CommonUtil.sleepThread(300);
        try {
            Machine.current().network().connect(service.extend("mtron"), WS_CLIENT_TID);
            final List<fURI> across = this.planned();
            assertEquals(true, across.contains(SELECT_INST_TID),
                    "an online peer is a shard target, so the fold is planned across it: " + across);
            CommonUtil.close(server);
            this.awaitOffline(service);
            assertEquals(1L, Machine.current().network().peers().elements().count(),
                    "the offline peer is still listed: it is being redialed");
            final List<fURI> home = this.planned();
            assertEquals(false, home.contains(SELECT_INST_TID),
                    "an offline peer is no target, so the fold is planned at home: " + home);
            checkCodeParseApply(LOG, FOLD, "14");
        } finally {
            Machine.current().network().disconnect(service);
            CommonUtil.close(server);
        }
    }

    /**
     * the master's top-level ops once the rewrite stage has planned the fold
     */
    private List<fURI> planned() {
        final Code original = ObjmtronSerializer.parse(FOLD).asCode();
        return TransportRewriteHarness.ops(Machine.current().compiler().rewriter().apply(original).asCode());
    }

    private void awaitOffline(final fURI service) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            final Obj peer = Machine.current().network().peerOf(service);
            if (peer.isRec() && !Network.Helper.online(peer))
                return;
            Thread.sleep(50);
        }
        fail("the peer at " + service + " never went offline");
    }
}
