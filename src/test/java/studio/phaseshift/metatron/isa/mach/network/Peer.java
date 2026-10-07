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

package studio.phaseshift.metatron.isa.mach.network;

import studio.phaseshift.metatron.furi.fURI;

/**
 * One running peer VM: where it is. The thin handle that names a peer's address without exposing ports,
 * sockets or transports.
 */
public final class Peer {

    private final PeerCluster cluster;
    private final int index;

    Peer(final PeerCluster cluster, final int index) {
        this.cluster = cluster;
        this.index = index;
    }

    /** the 1-based index, as {@code $index} names it */
    public int index() {
        return this.index;
    }

    /** the mtron port this peer listens on */
    public int port() {
        return this.cluster.port(this.index);
    }

    /** this peer's connectable data-root prefix ({@code ws://localhost:<port>/n}) */
    public fURI prefix() {
        return this.cluster.prefix(this.index);
    }

    /** is this peer's process still up? */
    public boolean alive() {
        return this.cluster.alive(this.index);
    }

    @Override
    public String toString() {
        return "peer[" + this.index + "@" + this.port() + "]";
    }
}
