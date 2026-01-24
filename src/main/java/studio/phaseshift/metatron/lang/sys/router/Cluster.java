/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.sys.router;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.impl.MConnection;

import java.util.Map;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Cluster {

    Map<fURI, MConnection> nodes();

    fURI host();

    default void send(final fURI furi, final Obj obj) {
        this.send((f, conn) -> conn.remoteHost().bimatches(furi), furi, obj);
    }

    default Obj sendRecv(final fURI furi, final Obj obj) {
        return this.sendRecv((f, conn) -> conn.remoteHost().bimatches(furi), furi, obj);
    }

    default void send(final BiPredicate<fURI, MConnection> match, final fURI furi, final Obj obj) {
        this.nodes().values().stream().filter(conn -> match.test(furi, conn)).forEach(conn -> {
            conn.sendObj(obj);
        });
    }

    default Obj sendRecv(final BiPredicate<fURI, MConnection> match, final fURI furi, final Obj obj) {
        return objs(this.nodes().values().stream().filter(mConnection -> match.test(furi, mConnection)).map(mConnection -> mConnection.sendRecvObj(obj)));
    }
}
