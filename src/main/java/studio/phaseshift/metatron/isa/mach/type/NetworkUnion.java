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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.Tokens.PREVIOUS;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_NETWORK_TID;

/**
 * The network of a frame: the peers it dialed, read through to the ones it inherited.
 * <p>
 * Reachability is scoped, denotation is not. A frame that dials a peer can reach it and its descendants can too;
 * nothing outside the frame can. What an absolute URI <em>means</em> never changes — an unreachable authority fails
 * loudly rather than resolving somewhere else.
 * <p>
 * Unlike the ISA and memory this is the one accumulating component whose divergence can acquire an external
 * resource, so it is the only one that can be wrong destructively.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkUnion extends MRec implements Network, ComponentUnion<Network> {

    private final Network current;

    public NetworkUnion(final Network previous, final Network current) {
        super(new LinkedHashMap<>(), MACH_NETWORK_TID, null);
        // The inherited side lives in the rec, so `>>previous` and previous() cannot disagree — one
        // source of truth, and mtron can reach it.
        this.jvm().put(uri(PREVIOUS), null == previous ? new BasicNetwork() : previous);
        this.current = null == current ? new BasicNetwork() : current;
    }

    @Override
    public Network previous() {
        return (Network) this.jvm().getOrDefault(uri(PREVIOUS), noobj());
    }

    @Override
    public Network current() {
        return this.current;
    }

    /** a frame reaches what it dialed, and what it inherited. */
    @Override
    public Obj transportOf(final String authority) {
        final Obj mine = this.current.transportOf(authority);
        return mine.isNoObj() ? this.previous().transportOf(authority) : mine;
    }

    /** the reachable authority set is the union of both levels — a frame adds reach, it never subtracts it. */
    @Override
    public Set<String> authorities() {
        final Set<String> authorities = new LinkedHashSet<>(this.previous().authorities());
        authorities.addAll(this.current.authorities());
        return authorities;
    }

    /**
     * Release what this frame dialed, and nothing else.
     * <p>
     * Load-bearing rather than decorative: an inherited class method shadows an interface default, so without this
     * override {@link ComponentUnion#close()} would never run.
     */
    @Override
    public void close() {
        ComponentUnion.super.close();
    }
}
