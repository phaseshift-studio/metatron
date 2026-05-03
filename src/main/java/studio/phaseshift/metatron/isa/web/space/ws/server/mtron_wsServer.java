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
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.CONTENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsServer extends WebSocketRec {

    public static final fURI MTRON_WS_TID = WS_SPACE_TID.extend("mtron_ws");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MTRON_SERVER_TYPE = Type.Builder.build()
            .tid(WS_SERVER_TID)
            .vid(MTRON_WS_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(Content.ContentType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(Content.ContentType.APPLICATION_MTRON.value))))
            .constructor(instC(MTRON_WS_TID.extend(CTOR).dom(ALL.maybe()).rng(MTRON_WS_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Map<Obj, Obj> config = new LinkedHashMap<>(inst.arg(0).asRec().jvm());
                return new mtron_wsServer(config, inst.arg(0).asRec().vid());
            })).create();


    public mtron_wsServer(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MTRON_WS_TID, vid);
        this.jvm().put(uri(ON_MESSAGE), instC(vid.extend(ON_MESSAGE).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                final Obj rhs = lhs.apply(noobj());
                LOG.debug("received mtron message: %s => %s", lhs, rhs);
                this.send(rhs);
                return noobj();
            } catch (final Exception e) {
                LOG.error("error processing message: %s => %s", lhs, fail(e));
                this.send(fail(e));
                return noobj();
            }
        }));
        this.jvm().put(uri(ON_ERROR), instC(this.vid().extend(ON_ERROR).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                this.send(lhs);
                return noobj();
            } catch (final Exception e) {
                LOG.error("error processing error: %s", lhs, e);
                return noobj();
            }
        }));
        this.jvm().put(uri(SEND), instC(this.vid().extend(SEND).dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
          try {
            this.logger().info("sending %s to %s", lhs, this.vid());
            this.send(inst.arg(0));
            return inst.arg(0);
          } catch (final Exception e) {
            LOG.error("error sending %s to %s: %s", inst.arg(0), this.vid(), e);
            return noobj();
          }
        }));

    }
}


