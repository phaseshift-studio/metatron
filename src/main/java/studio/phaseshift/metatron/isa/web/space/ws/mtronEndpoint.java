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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtronEndpoint extends wsSpace.wsEndPoint {

    public static final fURI MTRON_ENDPOINT_TID = WEB_ISA_TID.extend("space/mtron_endpoint");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    private final Content.ContentType inContentType;
    private final Content.ContentType outContentType;

    public mtronEndpoint(final fURI vid) {
        super(mutableMap(), MTRON_ENDPOINT_TID, vid);
        this.inContentType = Content.ContentType.of(vid.qValue("in_content", String.class));
        this.outContentType = Content.ContentType.of(vid.qValue("out_content", String.class));
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
        LOG.info("opening mtron endpoint w/ %s", conn.getRemoteSocketAddress());
        super.onOpen(conn, handshake);
    }


    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.info("closing mtron endpoint w/ %s (%d, %s)", conn.getRemoteSocketAddress(), code, reason);
        super.onClose(conn, code, reason, remote);
    }


    @Override
    public void onMessage(final WebSocket conn, final String message) {
        try {
            final Obj obj = this.inContentType.serializer().inputBytes(ByteBuffer.wrap(message.getBytes()));
            final Obj result = obj.apply(noobj());
            conn.send(this.outContentType.serializer().outputBytes(result));
        } catch (final Exception e) {
            LOG.error("error processing message: %s", message, e);
            conn.send(fail(e).toString());
        }
    }


    @Override
    public void onError(final WebSocket conn, final Exception e) {
       try {
            conn.send(fail(e).toString());
        } catch (final Exception ex) {
            LOG.error("error sending error message: %s", e, ex);
        }
    }


    public void onStart() {

    }
}


