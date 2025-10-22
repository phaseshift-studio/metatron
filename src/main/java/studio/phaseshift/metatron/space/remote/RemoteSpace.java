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

import studio.phaseshift.metatron.io.net.FutureObj;
import studio.phaseshift.metatron.io.net.MClient;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.MTRON_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteSpace extends MSpace<MClient> {

    public static final fURI REMOTE_TID = MTRON_SPACE_TID.extend("remote");

    public RemoteSpace(final fURI pattern, final fURI vid) {
        super(new MClient(pattern.authority()), pattern, REMOTE_TID, vid);
        try {
            LOG.info("{{g}}connecting{{/g}} to {{b}}%s{{/b}}", this.jvm().server());
            this.jvm().connectBlocking();
            //LOG.info("{{^<2&X-&g}}connected{{X}} to {{b}}%s{{/b}}{{v1}}", this.jvm().server());
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        this.jvm().close();
    }


    @Override
    public Obj read(final fURI vid) {
        LOG.info("%s", vid.toUri());
        final Inst code = from_(vid.authority(null).scheme(null).toUri()).insts().get(0);//, vid.query("tag","abc"));
        LOG.info("performing remote read: %s", code);
        final FutureObj<Obj> future = this.jvm().sendRecv(code);
        LOG.info("future %s", future);
        LOG.info("future obj %s", future.get(10000));
        return future.get(1);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        this.jvm().send(obj);
        return obj;
    }
}
