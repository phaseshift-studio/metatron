/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.isa.web.space.ws.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MType;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * MCP (Model Context Protocol) server for wsSpace.
 * Handles JSON-RPC based communication for AI/LLM tool integration.
 */
public class mcpServer extends WSServerRec {

    private static final Logger LOG = LoggerFactory.getLogger(mcpServer.class);

    public static final Type MCP_SERVER_TYPE = T("wsmcpServer", REC_TID,
            instC("onOpen", INST_TYPE, uri("onOpen", URI_TYPE)),
            instC("onClose", INST_TYPE, uri("onClose", URI_TYPE)),
            instC("onError", INST_TYPE, uri("onError", URI_TYPE)),
            instC("onMessage", INST_TYPE, uri("onMessage", URI_TYPE)),
            instC("onRaw", INST_TYPE, uri("onRaw", URI_TYPE))
    );

    private final Map<WebSocket, String> clientSessions = new ConcurrentHashMap<>();

    public mcpServer(final InetSocketAddress address, final memSpace space) {
        super(address, space);
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
        super.onOpen(conn, handshake);
        LOG.info("MCP client connected: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.info("MCP client disconnected: {} code={} reason={}", conn.getRemoteSocketAddress(), code, reason);
        clientSessions.remove(conn);
        super.onClose(conn, code, reason, remote);
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("MCP error with {}: {}", conn != null ? conn.getRemoteSocketAddress() : "unknown", ex.getMessage());
        super.onError(conn, ex);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
        try {
            final Obj result = this.space.at(ROUTE)
                    .flatMap(r -> r.at(uri("onMessage", URI_TYPE)))
                    .flatMap(inst -> inst.execute(
                            uri("onMessage", URI_TYPE),
                            this.space,
                            lst(
                                    uri("conn", URI_TYPE).to(conn),
                                    uri("message", URI_TYPE).to(message)
                            )
                    ))
                    .orElse(noobj());

            if (result.isNot(noobj())) {
                conn.send(result.toString());
            }
        } catch (Exception e) {
            LOG.error("Error processing MCP message: {}", e.getMessage());
        }
    }

    @Override
    public void onRaw(final WebSocket conn, final ByteBuffer raw) {
        super.onRaw(conn, raw);
    }

    @Override
    public String toString() {
        return "wsmcpServer{" +
                "address=" + this.socket.getAddress() +
                ", port=" + this.socket.getPort() +
                '}';
    }
}
