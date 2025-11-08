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

package studio.phaseshift.metatron.lang.msys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.impl.FutureObj;
import studio.phaseshift.metatron.lang.msys.impl.net.MClient;
import studio.phaseshift.metatron.lang.msys.impl.net.MConnection;
import studio.phaseshift.metatron.lang.mtron.type.Code;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteSpace extends MSpace<MConnection> {

    public static final int RETRY_SECONDS = 5;
    public static final fURI REMOTE_TID = MTRON_TID.extend("space").extend("remote");
    private final GraphittyLogger LOG;

    public RemoteSpace(final fURI authority, final fURI pattern, final fURI vid) {
        super(MClient.of(authority), Map.of(uri("pattern"), uri(pattern)), pattern, REMOTE_TID, vid);
        LOG = Graphitty.log(this);
    }

    public static RemoteSpace of(final fURI authority, final fURI pattern, final fURI vid) {
        while (true) {
            try {
                return new RemoteSpace(authority, pattern, vid);
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
