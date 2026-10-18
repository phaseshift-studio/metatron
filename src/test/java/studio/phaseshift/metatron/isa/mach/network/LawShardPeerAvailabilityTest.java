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
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.web.space.ws.AbstractWebSocketServerIntegrationTest;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;

/**
 * {@code law_shard} availability needs no forked peer process — a {@code ws_client} self-dial against a
 * co-mounted {@code wsspace} mints the {@code /network/peer} the guard reads, and then {@code law_shard} must
 * be a live out-edge of the shardable chain's {@code path::T}. This is the manual console demo, automated.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class LawShardPeerAvailabilityTest extends AbstractWebSocketServerIntegrationTest {

    @Override
    protected wsSpace createWSSpace() {
        return wsSpace.of(rec(
                uri(NAME), uri("law-shard-peer-test"),
                uri(HOST), uri("ws://localhost:" + generatePort()),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
        ).jvm(), f("/sys/space/ws/law-shard-peer-test"));
    }

    @Test
    void testLawShardAvailableWhenPeered() {
        // typing is not solved across distributed executions yet — run type-check-free, as the manual demo did
        TypeCheck.disable(TypeCheck.values());
        // catRewrite.law_shard is registered by catInstSet, which the standard test boot does not mount
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());

        // a ws_client self-dial mints a /network/peer — the exact trigger law_shard's guard reads
        Machine.current().network().connect(f("ws://localhost:" + this.wsPort + "/mtron"), WS_CLIENT_TID);
        assertFalse(Machine.current().network().peers().jvm().isEmpty(), "a self-dial mints a /network/peer");

        // start({1,2,3,4}).plus(1).sum() lifted to path::T exposes law_shard as a live out-edge
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()");
        final Obj path = catInstSet.PATH_TYPE.constructor().apply(code);
        assertTrue(path.isRec() && path.asRec().has(uri("law_shard")),
                "law_shard is available as a rewrite when a peer is present: " + path);
    }
}
