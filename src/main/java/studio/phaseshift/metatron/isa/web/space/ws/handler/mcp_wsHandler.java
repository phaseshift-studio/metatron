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

package studio.phaseshift.metatron.isa.web.space.ws.handler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.mcp_Server;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;

/**
 * MCP (Model Context Protocol) server for wsSpace.
 * Handles JSON-RPC based communication for AI/LLM tool integration.
 * <p>
 * The MCP server type exposes three categories of capabilities:
 * <ul>
 *   <li><b>tool</b> — functions that LLMs can invoke (e.g. search, calculate)</li>
 *   <li><b>resource</b> — data/context that can be read (e.g. files, APIs)</li>
 *   <li><b>prompt</b> — templated messages/workflows for users</li>
 * </ul>
 * <p>
 * Users extend this server by defining a type in mtron:
 * <pre>
 * mymcp::T[?[
 *   tool=>[
 *     myTool=>myTool(a1,a2){ ... },
 *     ...],
 *   resource=>[
 *     myRes=>!*&lt;http://...&gt;,
 *     ...],
 *   prompt=>[
 *     myPrompt=>"template string",
 *     ...]
 * ]]@mymcp
 * </pre>
 * Then register it in the wsSpace route table and connect via WebSocket.
 */
public class mcp_wsHandler extends WebSocketRec {

    public static final fURI WS_MCP_HANDLER_TID = WS_SPACE_TID.extend("mcp_ws");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MCP_HANDLER_TYPE = Type.Builder.build()
            .tid(WS_HANDLER_TID)
            .vid(WS_MCP_HANDLER_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE).maybe(),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(WS_MCP_HANDLER_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_MCP_HANDLER_TID), lst(T(REC_TID)), (lhs, inst) ->
                    new mcp_wsHandler(new LinkedHashMap<>(inst.arg(0).asRec()
                            .at(uri(IN), uri(Content.ContentType.APPLICATION_JSON.value))
                            .at(uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value)).jvm()), WS_MCP_HANDLER_TID, inst.arg(0).vid()))).create();

    // Transport-agnostic protocol handler (composition)
    private final mcp_Server mcp;

    public mcp_wsHandler(final Rec recClone) {
        this(recClone.jvm(), recClone.tid(), recClone.vid());
    }

    public mcp_wsHandler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.mcp = new mcp_Server(jvm, tid, vid);
        this.jvm().put(uri(ON_MESSAGE), instC(vid.extend(ON_MESSAGE).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                LOG.info("incoming mcp message from %s: %s", this.getOtherVID(), lhs);
                final Obj result = this.mcp.handleMessage(lhs);
                if (null != result && !result.isNoObj()) send(result);
                return result;
            } catch (final Exception e) {
                LOG.error("error sending mcp response: %s", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                send(fail(e));
                return fail(e);
            }
        }));
    }

    @Override
    public IO getIO() {
        return new IO(
                Content.ContentType.of(this.at(IN).orElse(uri(Content.ContentType.APPLICATION_JSON.value)).uriValue().toString()),
                Content.ContentType.of(this.at(OUT).orElse(uri(Content.ContentType.APPLICATION_JSON.value)).uriValue().toString()));
    }

    public Rec getToolList() {
        return this.mcp.getToolList();
    }
}
