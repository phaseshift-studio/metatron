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

package studio.phaseshift.metatron.space.remote;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Code;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.router.FutureObj;
import studio.phaseshift.metatron.space.router.net.MClient;
import studio.phaseshift.metatron.space.router.net.MConnection;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteSpace extends MSpace<MConnection> {

    public static final fURI REMOTE_TID = MTRON_SPACE_TID.extend("remote");
    private final GraphittyLogger LOG;

    public RemoteSpace(final fURI authority, final fURI pattern, final fURI vid) {
        super(MClient.of(authority), pattern, REMOTE_TID, vid);
        LOG = Graphitty.log(this);
    }

    public static RemoteSpace open(final fURI authority, final fURI pattern, final fURI vid) {
        //try {
        return new RemoteSpace(authority, pattern, vid);
        // } catch (final MTronException e) {
        //     return NullSpace.single();
        // }
    }

    @Override
    public void close() {
        this.jvm().close();
    }


    @Override
    public Obj read(final fURI vid) {
        final Inst code = from_(vid.authority(null).scheme(null).toUri()).insts().get(0);//, vid.query("tag","abc"));
        LOG.info("performing remote read: %s", code);
        final FutureObj<Obj> future = this.jvm().sendRecvObj(code);
        return future;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Code code = start_(obj).to_(vid.toUri());
        LOG.info("performing remote write: %s", code);
        this.jvm().sendObj(code);
        return obj;
    }

    @Override
    public Obj apply(final Obj obj) {
        LOG.info("performing remote apply: %s", obj);
        final FutureObj<Obj> future = this.jvm().sendRecvObj(obj);
        return future;
    }
}
