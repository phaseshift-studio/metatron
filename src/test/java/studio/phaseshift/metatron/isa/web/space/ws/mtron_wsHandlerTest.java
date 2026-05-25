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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsHandlerTest extends AbstractWebSocketServerTest {
    @Override
    protected WebSocketRec createServer(final fURI vid) {
        return new mtron_wsHandler(new LinkedHashMap<>(Map.of(
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value))), vid);
    }

    public static class mtron_wsHandlerIntegrationTest extends AbstractWebSocketServerIntegrationTest {

        @Override
        protected wsSpace createWSSpace() {
            return wsSpace.of(rec(
                    uri(NAME), uri("mtron-test"),
                    uri(HOST), uri("ws://localhost:" + generatePort()),
                    uri(PATTERN), uri("ws://#"),
                    uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
            ).jvm(), f("/sys/space/ws/mtron-test"));
        }
    }
}

