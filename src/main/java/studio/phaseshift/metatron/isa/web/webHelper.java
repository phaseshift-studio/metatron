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

package studio.phaseshift.metatron.isa.web;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_INST_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webHelper {

    private webHelper() {
        // do nothing
    }

    public static Inst remoteConsole() {
        return docWrap(instC(M_ISA_INST_TID, lst(), (lhs, inst) -> {
            try {
                Console.LOCAL_INSTANCE.logger().none("select location for remote console: ");
                final Str location = instB(SYS_INST_TID.extend("stdin"), lst()).apply().as();
                fURI locationUri = f("").authority(location.strValue());
                locationUri = locationUri.hasScheme() ? locationUri : locationUri.scheme("ws");
                locationUri = locationUri.segmentLength() > 0 ? locationUri : locationUri.segments(List.of("mtron"));
                Console.LOCAL_INSTANCE.logger().none("\nsetting up remote console for {{g}}%s{{X}}\n", uri(locationUri));
                final WebSocketRecClient client = new WebSocketRecClient(new WebSocketRec(mutableMap(uri(HOST), locationUri.toUri()), WS_CLIENT_TID, CommonUtil.mintShortUUID(f("/sys/web/ws"), true)));
                Console.LOCAL_INSTANCE.input = instLambda((lhs2, inst2) -> client.jvm().get(uri("send_recv")).apply(inst2.arg(0)));
                return location;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }), "creates a remote console connection");
    }
}
