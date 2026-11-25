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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The mtron_ws endpoint in this JVM. Its over-the-wire contract lives in
 * {@link mtron_wsHandlerIntegrationTest} — as a TOP-LEVEL class, because the integration test used to be a static
 * nested subclass here, and Surefire's file-name scan never selects a nested class (that test had never run).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class mtron_wsHandlerTest extends AbstractWebSocketServerTest {
    @Override
    protected WebSocketRec createServer(final fURI vid) {
        return new mtron_wsHandler(new LinkedHashMap<>(Map.of(
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value))), vid);
    }
}
