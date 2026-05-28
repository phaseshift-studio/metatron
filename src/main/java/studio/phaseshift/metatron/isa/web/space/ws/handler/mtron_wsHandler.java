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

package studio.phaseshift.metatron.isa.web.space.ws.handler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.CONTENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsHandler extends WebSocketRec {

    public static final fURI WS_MTRON_HANDLER_TID = WS_SPACE_TID.extend("mtron_ws");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MTRON_HANDLER_TYPE = Type.Builder.build()
            .tid(WS_HANDLER_TID)
            .vid(WS_MTRON_HANDLER_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value))))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WS_MTRON_HANDLER_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Map<Obj, Obj> config = new LinkedHashMap<>(inst.arg(0).asRec().jvm());
                return new mtron_wsHandler(config, inst.arg(0).asRec().vid());
            })).create();


    public mtron_wsHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, WS_MTRON_HANDLER_TID, vid);
        this.jvm().put(uri(ON_MESSAGE), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                final Obj rhs = lhs.apply(noobj());
                LOG.debug("processed mtron message: %s => %s", lhs, rhs);
                this.send(rhs);
                return rhs;
            } catch (final Exception e) {
                LOG.error("error processing message: %s => %s", lhs, fail(e));
                final Fail failure = fail(e);
                this.send(failure);
                return failure;
            }
        }));
        this.jvm().put(uri(ON_ERROR), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                this.send(lhs);
                return noobj();
            } catch (final Exception e) {
                LOG.error("error processing error: %s", lhs, e);
                return noobj();
            }
        }));
        this.jvm().put(uri(SEND), instC(M_ISA_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
          try {
            this.send(inst.arg(0));
            return inst.arg(0);
          } catch (final Exception e) {
            return noobj();
          }
        }));

    }
}


