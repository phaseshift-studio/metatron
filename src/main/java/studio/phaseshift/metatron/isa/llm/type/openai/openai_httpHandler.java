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

package studio.phaseshift.metatron.isa.llm.type.openai;

import com.sun.net.httpserver.HttpExchange;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.space.http.HttpRec;
import studio.phaseshift.metatron.isa.web.space.http.SseStream;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.io.IOException;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.type.MIME.MIMEType.APPLICATION_JSON;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The OpenAI-compatible HTTP transport — the {@code openai_httpHandler}
 * counterpart to {@code mcp_httpHandler}.  Composes an {@link openaiServer}
 * for the wire translation and dispatches on the request path, because the
 * mount is a single {@code /v1/#} wildcard and one handler serves every
 * endpoint.
 * <p>
 * Overrides only {@code doGet} (models / health) and {@code doPost}
 * (completions) plus {@code doHead} (probe); the rest inherit
 * {@code HttpRec}'s default {@code 405}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class openai_httpHandler extends HttpRec {

    private static final fURI OPENAI_V1_ENDPOINT = f("/v1");

    // Transport-agnostic protocol core (composition), like mcp_httpHandler -> mcpServer
    private final openaiServer core;

    public openai_httpHandler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.core = new openaiServer(jvm, openaiServer.OPENAI_SERVER_TID, vid);
    }

    public openai_httpHandler(final Rec recClone) {
        this(mutableMap(recClone.jvm()), recClone.tid(), recClone.vid());
    }

    @Override
    protected void doGet(final HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        if (path.endsWith(OPENAI_V1_ENDPOINT.extend("models").toString()))
            sendJson(200, this.core.handleModels());
        else
            sendJson(200, this.core.handleInfo());
    }

    @Override
    protected void doPost(final HttpExchange exchange) throws IOException {
        final String body = readBody(exchange);
        final Obj request;
        try {
            request = APPLICATION_JSON.fromBytes(body);
        } catch (final Exception e) {
            sendError(400, "Invalid JSON body");
            return;
        }
        if (!request.isRec()) {
            sendError(400, "request must be a JSON object");
            return;
        }
        final Rec req = request.asRec();
        final Obj streamFlag = req.at(uri("stream"));
        final boolean stream = !streamFlag.isNoObj() && streamFlag.booleanCheck();
        if (stream) {
            final SseStream sse = this.openSse();
            this.core.streamCompletion(req,
                    chunk -> {
                        try {
                            sse.send(this.JSON.write(chunk).toString());
                        } catch (final IOException e) {
                            // client gone — the done hook below still closes the stream
                        }
                    },
                    () -> {
                        try {
                            sse.done();
                        } catch (final IOException e) {
                            // ignore — closing regardless
                        }
                        sse.close();
                    });
            return;
        }
        final Obj result = this.core.handleCompletion(req);
        sendJson(openaiServer.status(result), result);
    }

    @Override
    protected void doHead(final HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, APPLICATION_JSON.value);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
