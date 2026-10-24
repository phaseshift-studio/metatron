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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Env — the environment a rewrite is a function of, snapshotted once per {@code apply} (§6). The compiler today takes
 * {@code Machine.current()} as an implicit argument; this value makes the network's two tables an explicit, immutable
 * input so a rewrite does not hold the network open or read a status that changes mid-derivation.
 *
 * <p>For now a small Java value (the ISA union and processor capabilities are still read live by the resolver, not
 * here): the dialed {@link #peers() peer map}, the {@link #machines() roster}, and this machine's own listening
 * {@link #authorities()} (the origin place's transports). A rec projection follows (§11.7). The {@link #fingerprint()}
 * is a stable hash of those three, so "re-plan iff the fingerprint differs" is a comparison away.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Env {

    private final Rec peers;
    private final Rec machines;
    private final Set<fURI> authorities;
    private final String fingerprint;

    private Env(final Rec peers, final Rec machines, final Set<fURI> authorities) {
        this.peers = peers;
        this.machines = machines;
        this.authorities = authorities;
        this.fingerprint = Integer.toHexString(this.peers.jvm().hashCode()
                ^ this.machines.jvm().hashCode() ^ this.authorities.hashCode());
    }

    /**
     * The one immutable snapshot per application — read the machine exactly once, here.
     */
    public static Env snapshot() {
        final Network network = Machine.current().network();
        // shallow-copy the two maps so the peer/roster SET is frozen; a member's liveness is still read off its live
        // link (as shardTargets always did)
        return new Env(
                new MRec(new LinkedHashMap<>(network.peers().jvm()), network.peers().tid(), network.peers().vid()),
                new MRec(new LinkedHashMap<>(network.machines().jvm()), network.machines().tid(), network.machines().vid()),
                new LinkedHashSet<>(Network.Helper.authorities()));
    }

    /**
     * The dialed peer map, frozen at snapshot time — the reach this machine holds.
     */
    public Rec peers() {
        return this.peers;
    }

    /**
     * The roster ({@code machine => reach{+}}), frozen at snapshot time — what this machine knows.
     */
    public Rec machines() {
        return this.machines;
    }

    /**
     * This machine's own listening transports — the authority side of the origin place.
     */
    public Set<fURI> authorities() {
        return this.authorities;
    }

    /**
     * A stable hash of the snapshot, so two snapshots of the same network compare equal.
     */
    public String fingerprint() {
        return this.fingerprint;
    }
}
