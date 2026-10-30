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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.network.PeerCluster;
import studio.phaseshift.metatron.isa.m.mach.type.Compiler;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * ONE EVALUATION PER FRAME. An mtron frame is a program: the endpoint evaluates it once and answers what it
 * evaluated to, which is what the console prints for the same line. The reply is read here by nothing that
 * evaluates (a raw socket, the way {@code bin/wsplus} reads it) and parsed, never applied, so a call-typed answer
 * shows as itself. The endpoint used to apply the frame's answer once more, so a bare lambda or a quote came back as
 * its own application: {@code [plus(2).plus(2)]==[as(inst::T)]>-} answered nothing, and {@code |start(7)} answered
 * {@code 7}.
 * <p>
 * Two endpoints, because there are two ways to get the count wrong. The forked peer runs the production compiler,
 * which reads text itself, and is where the frame was evaluated TWICE. The in-JVM endpoint runs the test-only
 * {@link CatTheoryCompiler}, which accepts only {@code code::T}: there an evaluating read of the frame's text fails
 * and leaves the frame unevaluated, so an endpoint that merely stopped re-applying would answer the program instead
 * of its result. A forked peer cannot install that compiler, which is why that half stays in this JVM.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class mtron_wsHandlerEvaluationTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static Listener local;
    private static int localPort;

    @BeforeAll
    static void startEndpoints() throws Exception {
        // a forked peer serving mtron_ws at /mtron: the production compiler, across a real process boundary
        cluster = PeerCluster.of(1).start();
        // an in-JVM mtron_ws endpoint, for the compiler only this JVM can install
        InstSet.importInstSet(WEB_ISA_TID);
        localPort = generatePort();
        local = wsServer.of(rec(
                uri(NAME), uri("evaluation-test"),
                uri(ORIGIN), uri("ws://localhost:" + localPort),
                uri(ROUTE), rec(uri(PeerCluster.MTRON_ROUTE), uri(WS_MTRON_HANDLER_TID.toString()))
        ));
    }

    @AfterAll
    static void stopEndpoints() {
        if (null != local) {
            local.close();
        }
        if (null != cluster)
            cluster.close();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "start(7).plus(1)                       % 8",
            "[start(7).plus(1)]==[as(inst::T)]>-    % inst?#<=#(){start(7).plus(1)}",
            "[plus(2).plus(2)]==[as(inst::T)]>-     % inst?#<=#(){plus(2).plus(2)}",
            "[plus(2).plus(2)]==[as(inst::T)]       % [inst?#<=#(){plus(2).plus(2)}]",
            "|start(7)                              % start(7)",
            "[plus(1),plus(2)]                      % [plus(1),plus(2)]",
            "[a=>plus(1)]                           % [a=>plus(1)]",
            "plus(1)                                % noobj",
            "noobj                                  % noobj",
            "34                                     % 34",
            "{1,2,3}                                % {1,2,3}",
            "fail::[a]                              % <ERROR>",
            "1.plus(b)                              % <ERROR>",
            "{3}34                                  % {3}34",
    }, delimiter = '%')
    void testAFrameIsEvaluatedOnce(final String code, final String expected) throws Exception {
        this.checkAnswer(code, expected, ask("127.0.0.1", cluster.port(1), code));
    }

    @Training.SkipTraining(reason = "the rows of testAFrameIsEvaluatedOnce, under a test-only compiler")
    @ParameterizedTest
    @CsvSource(value = {
            "start(7).plus(1)                       % 8",
            "[start(7).plus(1)]==[as(inst::T)]>-    % inst?#<=#(){start(7).plus(1)}",
            "|start(7)                              % start(7)",
            "fail::[a]                              % <ERROR>",
    }, delimiter = '%')
    void testAFrameIsEvaluatedOnceByACodeOnlyCompiler(final String code, final String expected) throws Exception {
        final Compiler previous = Machine.current().compiler();
        Machine.current().compiler(CatTheoryCompiler.single());
        try {
            this.checkAnswer(code, expected, ask("127.0.0.1", localPort, code));
        } finally {
            Machine.current().compiler(previous);
        }
    }

    /**
     * AN IPv6 CLIENT IS ANSWERED. A session names its peer from the socket's remote address, and it used to do so by
     * parsing that address's {@code toString()}: for an IPv6 peer that is {@code /[0:0:0:0:0:0:0:1]:45960}, which is
     * no uri, so the session threw on every frame before {@code on_message} ran and the client heard nothing. The
     * name is now built as an authority with an RFC 3986 IP-literal host ({@code ws://[0:0:0:0:0:0:0:1]:45960}).
     * Skipped where the peer cannot be reached over IPv6 loopback.
     */
    @Training.SkipTraining(reason = "the scenario is the client's address family; the rows repeat testAFrameIsEvaluatedOnce")
    @ParameterizedTest
    @CsvSource(value = {
            "start(7).plus(1)                       % 8",
            "[plus(2).plus(2)]==[as(inst::T)]>-     % inst?#<=#(){plus(2).plus(2)}",
            "noobj                                  % noobj",
    }, delimiter = '%')
    void testAnIPv6ClientIsAnswered(final String code, final String expected) throws Exception {
        assumeTrue(reachable("::1", cluster.port(1)), "the peer is not reachable over IPv6 loopback here");
        this.checkAnswer(code, expected, ask("[::1]", cluster.port(1), code));
    }

    private static boolean reachable(final String host, final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), 2_000);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * A COEFFICIENT CROSSES THE WIRE. The handler is applied to the message, and with a {@code #{?}} domain a
     * {@code {3}34} message did not match it: while {@code inst_dom} is checked the processor compresses the
     * coefficient away, so the handler saw and sent {@code 34}. Checked against the in-JVM endpoint with
     * {@code inst_dom} pinned on, because a forked peer runs with every type check off and never compresses.
     */
    @Training.SkipTraining(reason = "the scenario is a type-check mode of the endpoint; the rows are plain values")
    @ParameterizedTest
    @CsvSource(value = {
            "{3}34                                  % {3}34",
            "{2}\"quirky\"                            % {2}\"quirky\"",
            "{4}1.5                                 % {4}1.5",
            "34                                     % 34",
    }, delimiter = '%')
    void testACoefficientCrossesTheWire(final String code, final String expected) throws Exception {
        final boolean checked = TypeCheck.inst_dom.enabled();
        TypeCheck.enable(TypeCheck.inst_dom);
        try {
            this.checkAnswer(code, expected, ask("127.0.0.1", localPort, code));
        } finally {
            if (!checked)
                TypeCheck.disable(TypeCheck.inst_dom);
        }
    }

    /**
     * A COEFFICIENT COMES BACK. A guard on the other half of the wire: a Java client hands every inbound frame to its
     * router (an inst, applied to the frame), which fills the waiting round trip. Its domain is {@code #{?}} like the
     * server handler's was, but no coefficient was found lost here even with {@code inst_dom} on, so this pins the
     * round trip rather than a fix. {@code inst_dom} is pinned on in the client's JVM; the forked peer runs unchecked.
     */
    @Training.SkipTraining(reason = "the scenario is a Java client's round trip; the rows are plain values")
    @ParameterizedTest
    @CsvSource(value = {
            "{3}34                                  % {3}34",
            "34                                     % 34",
    }, delimiter = '%')
    void testACoefficientComesBack(final String code, final String expected) {
        final boolean checked = TypeCheck.inst_dom.enabled();
        TypeCheck.enable(TypeCheck.inst_dom);
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri("ws://127.0.0.1:" + cluster.port(1) + PeerCluster.MTRON_ROUTE),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        final WebSocketRecClient client = new WebSocketRecClient(wsRec);
        try {
            final Obj reply = FutureObj.resolveFuture(wsRec.sendRecv(ObjmtronSerializer.parse(code), 15_000L));
            checkEquality(LOG, ObjmtronSerializer.parse(expected), reply, true);
        } finally {
            client.close();
            if (!checked)
                TypeCheck.disable(TypeCheck.inst_dom);
        }
    }

    /**
     * AN ABSENT ANSWER COMES BACK, ADDRESSED. A round trip whose answer is {@code noobj} used to be untaggable: the
     * shipped program was anchored at its END, and nothing can carry an address (noobj is the coefficient-zero
     * singleton, which no writer renders with a vid), so the reply arrived bare. It is now WRAPPED in
     * {@code reply?block(<program>, <tag>)} — the tag addresses the ANSWER — and an absent answer travels as the
     * round trip's mark, which the socket's router turns back into {@code noobj}.
     * <p>
     * The rows cover both halves: a payload that answers nothing directly ({@code plus(1)} on the frame's noobj), and
     * one that dies MID-CHAIN ({@code map(noobj).plus(1)} — the {@code plus} is where the monad is killed, which is
     * why a trailing anchor could not work), beside the ordinary answers that already worked.
     */
    @Training.SkipTraining(reason = "the scenario is a Java client's round trip; the rows are plain values")
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1)                                    % noobj",
            "map(noobj).plus(1)                         % noobj",
            "{3}34                                      % {3}34",
            "34                                         % 34",
    }, delimiter = '%')
    void testAnAbsentAnswerComesBackAddressed(final String code, final String expected) {
        try (final RoundTripClient client = this.roundTripClient()) {
            final Obj reply = FutureObj.resolveFuture(client.wsRec.sendRecv(ObjmtronSerializer.parse(code), 15_000L));
            checkEquality(LOG, ObjmtronSerializer.parse(expected), reply, true);
        }
    }

    /**
     * A FAIL COMES BACK AS THE PEER'S FAIL. The far side catches a fail before answering (an uncaught fail would skip
     * the handler's function — no reply at all), and catching used to DROP THE ADDRESS ({@code MCaughtFail}
     * forced a null vid), so the frame could not be matched to its trip and the caller saw a deadline instead of the
     * diagnostic. It is the peer's message that must arrive — not a parse error, not "no reply from" — which is what
     * distinguishes "the fail travelled" from "the transport timed out".
     */
    @Training.SkipTraining(reason = "the scenario is a Java client's round trip")
    @Test
    void testAFailComesBackAsThePeersFail() {
        try (final RoundTripClient client = this.roundTripClient()) {
            // `plus(a)` on the frame's noobj is NOOBJ (plus's domain refuses a noobj lhs), not a fail: the payload
            // has to produce a value first for the argument to be the thing that breaks
            final Obj reply = FutureObj.resolveFuture(client.wsRec.sendRecv(ObjmtronSerializer.parse("1.plus(a)"), 15_000L));
            LOG.debug("the peer's fail over the wire: %s", reply);
            assertTrue(reply.isFail() || reply.isCaughtFail(), Graphitty.string("a fail must come back as a fail, not %s", reply));
            final String message = reply.asFail().message();
            assertTrue(message.contains("unable to convert"), Graphitty.string("the peer's diagnostic must travel, got %s", message));
            assertFalse(message.contains("parse error"), Graphitty.string("the fail did not survive the wire: %s", message));
            assertFalse(message.contains("no reply from"), Graphitty.string("the trip timed out instead of matching its answer: %s", message));
            // THE TRIP'S TAG IS THE TRANSPORT'S, NOT THE FAIL'S: what comes back is addressed in THIS machine's fail
            // stack (~/sys/fail/_?incrq), never at the tag the connection minted to find the slot
            assertNotNull(reply.vid(), Graphitty.string("the re-homed fail must carry the stack address: %s", reply));
            assertTrue(reply.vid().toString().contains("sys/fail"),
                    Graphitty.string("a received fail belongs at the receiving machine's fail stack, got %s", reply.vid()));
        }
    }

    /**
     * TWO TRIPS IN FLIGHT, ONE OF THEM ABSENT. This is the case arrival order cannot serve: with more than one trip
     * outstanding an unanchored reply is not guessed at, so the absent one must arrive carrying its own tag. It is the
     * round trip equivalent of the pipelining a sharded scatter does — {@code cut} fires one {@code eval} per shard,
     * all awaited through futures on the same socket.
     */
    @Training.SkipTraining(reason = "the scenario is a Java client's two overlapping round trips")
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1)   % 1.plus(1)   % noobj  % 2",
            "34        % 1.plus(1)   % 34     % 2",
    }, delimiter = '%')
    void testOverlappingTripsEachAnswerThemselves(final String absentCode, final String presentCode,
                                                  final String absentExpected, final String presentExpected) {
        try (final RoundTripClient client = this.roundTripClient()) {
            // BOTH ARE SENT BEFORE EITHER IS INSPECTED: sendRecv hands back a future and ships on a virtual thread
            final Obj absent = client.wsRec.sendRecv(ObjmtronSerializer.parse(absentCode), 15_000L);
            final Obj present = client.wsRec.sendRecv(ObjmtronSerializer.parse(presentCode), 15_000L);
            checkEquality(LOG, ObjmtronSerializer.parse(presentExpected), FutureObj.resolveFuture(present), true);
            checkEquality(LOG, ObjmtronSerializer.parse(absentExpected), FutureObj.resolveFuture(absent), true);
        }
    }

    /**
     * A dialed client for the round-trip rows, closed by try-with-resources. The rec is what a console dials with, so
     * {@code sendRecv} installs the socket's router here exactly as it does in the console.
     */
    private RoundTripClient roundTripClient() {
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri("ws://127.0.0.1:" + cluster.port(1) + PeerCluster.MTRON_ROUTE),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        return new RoundTripClient(wsRec, new WebSocketRecClient(wsRec));
    }

    private record RoundTripClient(WebSocketRec wsRec, WebSocketRecClient client) implements AutoCloseable {
        @Override
        public void close() {
            this.client.close();
        }
    }

    /**
     * JSON ALWAYS ANSWERS. JSON is lossy by design (that is what {@code out=application/x-mtron} is for), but a fail
     * must still produce a reply: the handler sends a CAUGHT fail, the json writer did not recognize one (a caught
     * fail answers {@code isFail()} false) and threw, {@code send} swallowed it, and the client waited out its deadline.
     * The text is compared as json, not parsed as mtron.
     */
    @Training.SkipTraining(reason = "the rows are json text, not mtron results")
    @ParameterizedTest
    @CsvSource(value = {
            "fail::[a]                              % \"a\"",
            "[a=>34]                                % {\"a\":34}",
            "34                                     % 34",
    }, delimiter = '%')
    void testJsonAlwaysAnswers(final String code, final String expected) throws Exception {
        assertEquals(expected, ask("127.0.0.1", cluster.port(1), "?out=application/json", code));
    }

    /**
     * The answer is a VALUE: it is parsed and compared, never applied (the suite's usual helpers apply an expected
     * call, which would hide exactly the difference this test is about).
     */
    private void checkAnswer(final String code, final String expected, final String reply) {
        LOG.debug("over the wire: %s => %s [expected:%s]", code, reply, expected);
        assertNotNull(reply, Graphitty.string("no reply to %s", code));
        final Obj answer = ObjmtronSerializer.parse(reply);
        if (expected.trim().equals("<ERROR>"))
            assertTrue(answer.stream().anyMatch(Obj::isFail), Graphitty.string("%s should answer a fail, but answered %s", code, reply));
        else
            checkEquality(LOG, ObjmtronSerializer.parse(expected), answer, true);
    }

    /**
     * One frame out, one frame back, over a raw socket: the reply's TEXT, as {@code bin/wsplus} prints it.
     * <p>
     * Every wait is bounded. The client is shut down with {@code shutdownNow()}, never {@code close()}: close waits
     * for every operation to finish, and the socket of a peer that never answered never finishes, so a failing row
     * would hang the suite instead of failing it.
     */
    private static String ask(final String host, final int port, final String code) throws Exception {
        return ask(host, port, "", code);
    }

    private static String ask(final String host, final int port, final String query, final String code) throws Exception {
        final CompletableFuture<String> reply = new CompletableFuture<>();
        final StringBuilder frame = new StringBuilder();
        final HttpClient http = HttpClient.newHttpClient();
        try {
            final WebSocket socket = http.newWebSocketBuilder()
                    .buildAsync(java.net.URI.create("ws://" + host + ":" + port + PeerCluster.MTRON_ROUTE + query), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
                            frame.append(data);
                            if (last)
                                reply.complete(frame.toString());
                            webSocket.request(1);
                            return null;
                        }
                    }).get(10, TimeUnit.SECONDS);
            try {
                socket.sendText(code, true);
                return reply.get(30, TimeUnit.SECONDS);
            } finally {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "answered");
            }
        } finally {
            http.shutdownNow();
        }
    }
}
