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

package studio.phaseshift.metatron.io.net;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.ObjSerializer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;

public class MClient extends WebSocketClient {

    protected final GraphittyLogger LOG;
    protected final ObjSerializer<ByteBuffer> serializer;

    public MClient(URI serverUri, Draft draft) {
        super(serverUri, draft);
        LOG = Graphitty.log(this);
        this.serializer = new ObjByteBufferSerializer();
    }

    public MClient(URI serverURI) {
        this(serverURI, new Draft_6455());
    }

    public static void main(String[] args) throws URISyntaxException {
        Log.setSLF4J("TRACE");
        WebSocketClient client = new MClient(new URI("ws://localhost:8887"));
        client.connect();
    }

    @Override
    public void onOpen(final ServerHandshake handshakedata) {
        send(serializer.write(str("a welcome str")));
        LOG.debug("new connection opened");
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        LOG.debug("closed with exit code %s [reason:%s]", reason);
    }

    @Override
    public void onMessage(final String message) {
        LOG.trace("%s received [raw string:%s]", message);
        final Obj obj = ObjParser.parse(message);
        LOG.trace("%s received [parsed]", obj);

    }

    @Override
    public void onMessage(final ByteBuffer message) {
        Obj obj = this.serializer.read(message);
        LOG.trace("%s received [raw bytes:%s]", obj, message);
    }

    @Override
    public void onError(Exception ex) {
        LOG.error("an error occurred on connection: %s", ex);
    }
}
