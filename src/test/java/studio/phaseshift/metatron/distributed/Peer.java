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

package studio.phaseshift.metatron.distributed;

import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;

/**
 * One running peer VM: where it is, and how to make it do something.
 * <p>
 * The handle a {@link TestSpace} applier takes — {@code attach(peers, definitions)} — so a declaration can be
 * modulated to and evaluated on each peer without the caller knowing about ports, sockets or transports.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Peer {

    private final PeerCluster cluster;
    private final int index;

    Peer(final PeerCluster cluster, final int index) {
        this.cluster = cluster;
        this.index = index;
    }

    /** the 1-based index, as {@code $n} names it */
    public int index() {
        return this.index;
    }

    /** the mtron port this peer listens on */
    public int port() {
        return this.cluster.port(this.index);
    }

    /** this peer's connectable data-root prefix ({@code ws://localhost:<port>/n}) */
    public String prefix() {
        return this.cluster.prefix(this.index);
    }

    /**
     * Evaluate a declaration on this peer, modulated to it first — so {@code $index}, {@code $self},
     * {@code $root} and {@code $n} all resolve from this peer's point of view.
     */
    public Obj evaluate(final String definition) {
        return this.cluster.send(this.index, this.cluster.modulate(definition, this.index));
    }

    /** is this peer's process still up? */
    public boolean alive() {
        return this.cluster.alive(this.index);
    }

    /** one {@code profile()} flow field for a peer-local evaluation — see {@link PeerCluster#FLOW_PROBE} */
    public long flow(final String localCode, final String field) {
        return this.cluster.flow(this.index, localCode, field);
    }

    /** one raw {@code profile()>>flow/<field>} value for a peer-local evaluation */
    public Obj flowField(final String localCode, final String field) {
        return this.cluster.flowField(this.index, localCode, field);
    }

    /** evaluate an already-parsed call on this peer — see {@link PeerCluster#peerAnalysis} */
    public Obj evaluate(final Call call) {
        return this.cluster.send(this.index, call);
    }

    /** this peer's trace: what it mounted, what it was provisioned with, what it is serving */
    public String trace() {
        return this.cluster.trace(this.index);
    }

    @Override
    public String toString() {
        return "peer[" + this.index + "@" + this.port() + "]";
    }
}
