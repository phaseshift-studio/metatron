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

package studio.phaseshift.metatron.isa.m.web.space.ws.handler;

import org.java_websocket.WebSocket;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Parser;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.webInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsHandler extends WebSocketRec {

    public static final fURI WS_MTRON_HANDLER_TID = WEB_ISA_TID.extend("ws").extend("mtron_ws");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MTRON_HANDLER_TYPE = Type.Builder.build()
            .tid(webInstSet.MTRON_TID)
            .vid(WS_MTRON_HANDLER_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value))))
            .constructor(mtron_ws -> new mtron_wsHandler(new LinkedHashMap<>(mtron_ws.asRec().jvm()), mtron_ws.asRec().vid())).create();


    /**
     * ON_MESSAGE — answer a message with the message itself. A frame is a program, and it is evaluated ONCE, as it
     * is read: by {@link #onMessage(WebSocket, String)} on a server session, by the input serializer on a client
     * that wears this protocol. What reaches this inst is therefore already the answer. Applying it once more was
     * the double apply: a value does not notice an application to noobj and a poly protects its members, but a
     * bare call-typed answer (a lambda, a quote) RAN on noobj, so the wire answered the application of the answer
     * where the console answered the answer.
     * <p>
     * The domain takes ANY multiplicity ({@code #{*}}). Declared {@code #{?}}, a message with a coefficient did not
     * match it, so while {@code inst_dom} is checked the processor compressed the coefficient away: the function saw
     * {@code 34} for {@code {3}34} and sent that, and only the inst's own return regained the {@code {3}}.
     */
    public static Inst mtron_on_message(final WebSocketRec ws) {
        return instC(M_ISA_INST_TID.dom(ALL.maybeSome()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                // caughtTransient, not caught(): caught() deletes the fail from the fail
                // space — a write the message thread must not perform just to format a
                // reply (the transport boundary already caught the fail; this covers a
                // direct ON_MESSAGE apply)
                final Obj result = lhs.isFail() ? lhs.asFail().caughtTransient() : lhs;
                ws.logger().debug("answered mtron message: %s", result);
                ws.send(result);
                // emit the fail stack trace after the reply is sent, not during its
                // serialization (where the tracer's own fail registration would delay the
                // send and leave the client waiting)
                if (result.isFail() || result.isCaughtFail())
                    MTronException.emitStackTrace(result.asFail().jvm());
                return lhs;
            } catch (final Exception e) {
                final Fail failure = fail(e).caughtTransient();
                ws.logger().debug("error processing message: %s => %s", lhs, failure);
                ws.send(failure);
                MTronException.emitStackTrace(failure.jvm());
                return failure;
            }
        });
    }

    /**
     * The mtron protocol surface, installed onto a {@link WebSocketRec} as GUARDED defaults — the same callbacks
     * the server handler wears, so a client that dials an mtron endpoint ({@code protocol => mtron_ws}) can answer
     * a server-initiated request. Explicit fields already on the rec win: the guards fill only what is absent.
     */
    public static void install(final WebSocketRec rec) {
        if (!rec.jvm().containsKey(uri(ON_MESSAGE)))
            rec.at(uri(ON_MESSAGE), mtron_on_message(rec), MUTABLE);
        if (!rec.jvm().containsKey(uri(ON_ERROR)))
            rec.at(uri(ON_ERROR), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
                try {
                    rec.send(lhs);
                    return noobj();
                } catch (final Exception e) {
                    rec.logger().error("error processing error: %s", lhs, e);
                    return noobj();
                }
            }), MUTABLE);
        if (!rec.jvm().containsKey(uri(STATE)))
            rec.at(uri(STATE), instC(M_ISA_INST_TID.dom(A.maybe()).rng(BOOL_TID), lst(), (lhs, inst) -> {
                try {
                    return bool(rec.state());
                } catch (final Exception e) {
                    return BOOL_FALSE;
                }
            }), MUTABLE);
    }

    public mtron_wsHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, WS_MTRON_HANDLER_TID, vid);
        install(this);
        this.jvm().put(uri(SEND), instC(M_ISA_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
            try {
                this.send(inst.arg(0));
                return inst.arg(0);
            } catch (final Exception e) {
                return noobj();
            }
        }));
    }

    /**
     * ONE EVALUATION PER FRAME. A text frame is read by this machine's own parser stage ({@link Machine#parse}) and
     * the program it denotes is compiled and run once ({@link Machine#evaluate(Code)}); the answer is what
     * {@code on_message} receives, and it replies with it as it stands.
     * <p>
     * The input serializer is not asked to evaluate, though {@code ObjmtronSerializer.read} does: it hands the
     * compiler TEXT, which a compiler that takes only {@code code::T} rejects, and when evaluation throws it falls
     * back to a bare parse. On those paths a frame arrived unevaluated and the handler's own application (the
     * second one everywhere else) was the only one, and a program that threw was run again from the start. Read
     * here, a frame is evaluated exactly once whichever compiler is installed, and a program that throws answers
     * its fail. A session whose input is not mtron (json, plain text) carries data and is read as it always was.
     */
    @Override
    public void onMessage(final WebSocket conn, final String message) {
        final MIME.MIMEType input = this.getIO().input();
        if (!input.isMtron() && !input.isMtronUI()) {
            super.onMessage(conn, message);
            return;
        }
        final Obj answer = mtron_wsHandler.evaluate(message);
        // the guard the serializer read applies: an uncaught fail would skip on_message's function, and the client
        // would wait out its deadline for a reply that is never sent
        this.onMessage(conn, answer.isFail() ? answer.asFail().caughtTransient() : answer);
    }

    private static Obj evaluate(final String frame) {
        try {
            return Machine.evaluate(Parser.Helper.toCode(Machine.parse(frame)));
        } catch (final Exception e) {
            return fail(e);
        }
    }

    public Obj apply(final Obj obj) {
        return this.sendRecv(obj);
    }
}


