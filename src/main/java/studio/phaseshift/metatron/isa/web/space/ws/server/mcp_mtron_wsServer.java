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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_wsServer.MCP_WS_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_mtron_wsServer extends WSServerRec {

    public static final fURI MCP_MTRON_WS_TID = WS_SPACE_TID.extend("mcp_mtron_ws");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MCP_MTRON_SERVER_TYPE = Type.Builder.build()
            .tid(MCP_WS_TID)
            .vid(MCP_MTRON_WS_TID)
            .isaPredicate(rec(uri(IN), URI_TYPE, uri(OUT), URI_TYPE))
            .constructor(instC(MCP_MTRON_WS_TID.extend(CTOR).dom(ALL.maybe()).rng(MCP_MTRON_WS_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Rec config = inst.arg(0).asRec();
                return new mcp_mtron_wsServer(new LinkedHashMap<>(config.jvm()), config.vid());
            })).create();


    public mcp_mtron_wsServer(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MCP_MTRON_WS_TID, vid);
        this.jvm().put(uri(ON_OPEN), instC(vid.extend(ON_OPEN), lst(URI_TYPE), (lhs, inst) -> {
            LOG.info("mcp_mtron_ws session opened w/ serializers: [in=>%s,out=>%s]", this.inContentType.name(), this.outContentType.name());
            return noobj();
        }));
        this.jvm().put(uri(ON_MESSAGE), instC(vid.extend(ON_MESSAGE).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                return lhs.apply(noobj());
            } catch (final Exception e) {
                LOG.error("error processing message: %s", lhs, e);
                this.send(fail(e));
                return fail(e);
            }
        }));
        this.jvm().put(uri(ON_CLOSE), instC(vid.extend(ON_CLOSE), rec(uri(CODE), INT_TYPE, uri(REASON), STR_TYPE), (lhs, inst) -> {
            LOG.info("closing mtron endpoint w/ %s: code={{y}}%s{{X}}, reason={{y}}%s{{X}}", this.socket.getRemoteSocketAddress(), inst.arg(CODE), inst.arg(REASON));
            return noobj();
        }));
        this.jvm().put(uri(ON_ERROR), instC(vid.extend(ON_ERROR), lst(FAIL_TYPE), (lhs, inst) -> {
            LOG.error("error occurred w/ %s: %s", this.socket.getRemoteSocketAddress(), inst.arg(0));
            return noobj();
        }));

        this.jvm().put(uri(SEND), instC(this.vid().extend(SEND).dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
            this.logger().info("sending %s to %s", lhs, this.vid());
            this.send(inst.arg(0));
            return inst.arg(0);
        }));

    }
}

