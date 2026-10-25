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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE CROSS-HOST TWIN. When one machine dials another, the dialed machine's listener co-mounts the inbound socket as
 * a {@code peer::T} ({@code Network.accept}) — keyed by the dialer's ephemeral source port, with no {@code host}, and
 * with the server session's reply channel as its transport. The far end of that socket is a CLIENT, and a plain
 * {@code ws_client} only prints what a server sends it, so a shard shipped down the twin never comes back: the round
 * trip waits out its deadline and the fail poisons the reduction ({@code unable to convert mfail to int::T}). This is
 * the cross-host sibling of {@link SelfLoopPeerTest} — a loopback twin is declined at accept, a cross-host one cannot
 * be, so {@code law_shard} must route around it.
 * <p>
 * No test can open a cross-host socket (a loopback dialer is declined at accept, which is the very case this is not),
 * so both peers are minted through the network's own entry points with stand-in reaches: the twin through
 * {@code accept}, keyed by a TEST-NET address (RFC 5737, never routable), and the dialed peer through {@code index},
 * as a {@code ws_client} constructor indexes itself. The reaches answer the way the print-only client does — never.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class AcceptedPeerShardTest extends AbstractMetatronTest {

    /**
     * the dialer as the dialed machine's listener sees it: a source port, not a place to dial back to
     */
    private static final fURI TWIN = f("ws://192.0.2.17:57440/mtron");
    /**
     * a machine this machine dialed itself: its host is where the dial went, route included
     */
    private static final fURI DIALED = f("ws://198.51.100.4:8555/mtron");

    @BeforeEach
    void setupTwin() {
        // typing is not solved across distributed executions yet — run type-check-free, as the manual demo did
        TypeCheck.disable(TypeCheck.values());
        // catRewrite.law_shard is registered by catInstSet, which the standard test boot does not mount
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        // the twin exactly as the listener mints it: the inbound session's send_recv is its reach
        Machine.current().network().accept(TWIN, rec(uri(SEND_RECV), silent(TWIN)));
    }

    @AfterEach
    void retractPeers() {
        Machine.current().network().disconnect(TWIN);
        Machine.current().network().disconnect(DIALED);
    }

    /**
     * a reach that never answers — what a print-only client is to the server it dialed
     */
    private static Inst silent(final fURI peer) {
        return instLambda((lhs, inst) -> fail("no reply from %s", peer));
    }

    /**
     * The dialed machine before it dials anyone back: the twin is the only peer it holds, so there is nothing to
     * fan out over, the rewrite is the identity, and the chain computes at home.
     */
    @Training.SkipTraining(reason = "the result is plain arithmetic; the scenario is the peer table, which a row cannot express")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,23,4}.plus(10).sum()        % 70",
            "{1,32,4,5,999,6}.plus(1).sum()   % 1053",
    }, delimiter = '%')
    void testATwinAloneKeepsTheChainHome(final String code, final int expected) {
        final Code original = ObjmtronSerializer.parse(code).asCode();
        final Code compiled = Machine.current().compiler().rewriter().apply(original).asCode();
        assertEquals(TransportRewriteHarness.ops(original), TransportRewriteHarness.ops(compiled),
                "a twin is not a shard target, so law_shard must leave the chain home: " + compiled);
        final Obj result = TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled));
        checkEquality(LOG, jnt(expected), result.clone().selfVID(null), true);
    }

    /**
     * The dialed machine dials its dialer back: the twin stays where accept put it and a dialed peer joins it. Every
     * shard goes where the dial went — none down the socket the dialer opened.
     */
    @Test
    void testTheTwinIsNotAShardTarget() {
        // indexed as a ws_client constructor indexes itself: the host is the claim, and the client serves to — a shard
        // target must be online AND serve the route its slave ships through (Places.serves)
        Machine.current().network().index(rec(
                uri(HOST), uri(DIALED),
                uri(SEND_RECV), silent(DIALED),
                uri(INST), rec(uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> fail("no reply from %s", DIALED)))));
        // routing is decided at compile time, so neither reach is ever asked
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,32,4,5,999,6}.plus(1).sum()")).asCode();
        assertEquals(List.of(Network.Helper.serviceOf(DIALED)), Reduction.routes(compiled),
                "law_shard must fan out over the dialed peer alone, never the accepted twin " + TWIN);
    }
}
