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
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;

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
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
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
        this.at(ON_OPEN, instLambda((lhs, inst) -> {
            LOG.info("wsmtron serializers: [in=>%s,out=>%s]", this.inContentType.name(), this.outContentType.name());
            return noobj();
        }), MUTABLE);
        this.at(ON_CLOSE, instLambda((lhs, inst) -> {
            LOG.info("wsmtron serializers: [in=>%s,out=>%s]", this.inContentType.name(), this.outContentType.name());
            return noobj();
        }), MUTABLE);
        this.at(ON_MESSAGE, instLambda((lhs, inst) -> {
            try {
                return lhs.apply(noobj());
            } catch (final Exception e) {
                LOG.error("error processing message: %s", lhs, e);
                this.send(fail(e));
                return fail(e);
            }
        }), MUTABLE);
        this.at(ON_CLOSE, instLambda((lhs, inst) -> {
            LOG.info("closing mtron endpoint w/ %s", this.socket.getRemoteSocketAddress());
            return noobj();
        }), MUTABLE);

    }
}


