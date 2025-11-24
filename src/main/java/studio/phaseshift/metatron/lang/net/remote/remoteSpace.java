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

package studio.phaseshift.metatron.lang.net.remote;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.sys.router.impl.FutureObj;
import studio.phaseshift.metatron.lang.sys.router.impl.MClient;
import studio.phaseshift.metatron.lang.sys.router.impl.MConnection;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;

import studio.phaseshift.metatron.lang.util.serial.ObjByteBufferSerializer;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class remoteSpace extends MSpace<MConnection> {

    public static final int RETRY_SECONDS = 5;
    public static final fURI REMOTE_TID = SYS_TID.extend(Tokens.SPACE).extend("remote");
    private final GraphittyLogger LOG;

    public static final Type REMOTE_TYPE = T(REMOTE_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(REMOTE_TID), lst(T(REC_TID, isa_(rec(uri(Tokens.PATTERN), T(URI_TID), uri(Tokens.HOST), T(URI_TID))))), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(Tokens.PATTERN).uriValue();
        final fURI host = inst.arg(0).<Rec>as().at(Tokens.HOST).uriValue();
        final Space remote = new remoteSpace(host, pattern, inst.arg(0).vid());
        Router.global().addSpace(remote);
        return remote;
    }));

    public remoteSpace(final fURI authority, final fURI pattern, final fURI vid) {
        super(MClient.of(authority, new ObjByteBufferSerializer()), Map.of(uri(Tokens.PATTERN), uri(pattern)), pattern, REMOTE_TID, vid);
        LOG = Graphitty.log(this);
    }

    public static remoteSpace of(final fURI authority, final fURI pattern, final fURI vid) {
        while (true) {
            try {
                return new remoteSpace(authority, pattern, vid);
            } catch (final Exception e) {
                Graphitty.log(Router.global()).error("retrying connection in %d seconds: %s", RETRY_SECONDS, e);
                MTronException.wrap(() -> TimeUnit.SECONDS.sleep(RETRY_SECONDS));
            }
        }
    }

    @Override
    public Obj read(final fURI vid) {
        final Inst code = from_(vid.authority(null).scheme(null).toUri()).insts().get(0);//, vid.query("tag","abc"));
        LOG.info("performing remote read: %s", code);
        final FutureObj<Obj> future = this.sjvm().sendRecvObj(code);
        return future;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Code code = start_(obj).to_(vid.toUri());
        LOG.info("performing remote write: %s", code);
        this.sjvm().sendObj(code);
        return obj;
    }

    @Override
    public Obj apply(final Obj obj) {
        LOG.info("performing remote apply: %s", obj);
        final FutureObj<Obj> future = this.sjvm().sendRecvObj(obj);
        return future;
    }
}
