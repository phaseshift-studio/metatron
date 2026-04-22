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

package studio.phaseshift.metatron.isa.web.space.ws.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class wsmtronServer extends WSServerRec {

    public static final fURI WS_MTRON_SERVER_TID = WS_SPACE_TID.extend("wsmtron");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MTRON_SERVER_TYPE = Type.Builder.build()
            .tid(WS_SERVER_TID)
            .vid(WS_MTRON_SERVER_TID)
            .isaPredicate(rec(uri(IN), URI_TYPE, uri(OUT), URI_TYPE))
            .constructor(instC(M_ISA_INST_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_MTRON_SERVER_TID),
                    lst(T(REC_TID)), (lhs, inst) -> new wsmtronServer(new LinkedHashMap<>(inst.arg(0).asRec().jvm()), inst.arg(0).vid()))).create();


    public wsmtronServer(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, WS_MTRON_SERVER_TID, vid);
        LOG.info("wsmtron serializers: [in=>%s,out=>%s]", this.inContentType.name(), this.outContentType.name());
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


