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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.COMPILER;
import static studio.phaseshift.metatron.Tokens.INSTSET;
import static studio.phaseshift.metatron.Tokens.MEMORY;
import static studio.phaseshift.metatron.Tokens.NETWORK;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.PROCESSOR;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.MEM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * MACHINE SPACE — a machine's own sphere of influence, as an address space (Marko, 2026-10-25: "this space will be the
 * primary space when accessing a Machine and will be used solely for the Machine sphere of influence").
 * <p>
 * <b>IT IS NOT IN MEMORY.</b> {@link Machine#read} and {@link Machine#write} ask it FIRST — the innermost machine whose
 * sphere covers the address ({@link Machine.Helper#sphereOf}) — and only then the network and memory, where the full
 * range of spaces and patterns applies as it always has. Its pattern is the machine's vid, and its jurisdiction is what
 * a machine IS: the machine itself, the components {@code machine::T} declares ({@code instset}, {@code compiler},
 * {@code memory}, {@code network}, {@code processor}) and its system information ({@code sys}). Every other address
 * under the vid is the machine's DATA, not the machine, so it is declined ({@code noobj}) and memory's spaces answer it
 * — the per-machine data level at {@code <vid>/#} among them, which keeps a child's {@code ~/x} the child's.
 * <ul>
 *     <li>{@code <vid>} — the machine itself, live;</li>
 *     <li>{@code <vid>/network/…} — the network's own branch: the component answers from its rec as it stands, a field
 *     is written in place, and a member (a peer, a listener) is constructed, never written — {@code -> noobj} retracts
 *     it ({@link studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork});</li>
 *     <li>{@code <vid>/<component>/…} — any other component, read live;</li>
 *     <li>{@code <vid>/sys/#} — threads, processors, fails and the log sink, held in this space's OWN store (it is a
 *     {@link memSpace} for that), with the q-processors {@code ~/sys} speaks: Marko, 2026-10-25, "I would have sys/#
 *     simply go into MachineSpace". {@code ~/sys} is a holding area: its parts are expected to move, over time, into
 *     the components they belong to.</li>
 * </ul>
 * A covered address this space has no answer for is declined too ({@code noobj} is the decline, by
 * {@link studio.phaseshift.metatron.isa.Addressable}'s convention), so the dispatch goes on to the network and memory —
 * and so is a QUERY on the machine or on a component other than the network ({@code ~/processor?docq}): the
 * q-processors that answer it are memory's, and the component itself is not the answer.
 * <p>
 * The machine's own writes are the network branch's and {@code ~/sys}'s; the machine rec's own writes and the other
 * components' still go through memory, declined here.
 */
public final class MachineSpace extends memSpace {

    private static final String SYS = Machine.Helper.SYS_DIR.toString();

    /**
     * the segments a machine's sphere is made of, below its vid: its components and its system information
     */
    private static final Set<String> SPHERE = Set.of(INSTSET, COMPILER, MEMORY, NETWORK, PROCESSOR, SYS);

    private final Machine owner;
    private final fURI root;
    /**
     * how many segments the machine's vid has: an address's next segment, at this index, names the sphere's part.
     * Counted from {@code segments()}, never {@code segmentLength()}: for {@code /} (the root's resolved vid) the
     * latter answers -1 while {@code segments()} is empty, and the depth must agree with the segments it indexes.
     */
    private final int depth;

    /**
     * The sphere of {@code machine}, which must have a vid: its jurisdiction is the machine's resolved vid
     * ({@code /.} is {@code /}), and the space is never registered — it is held by the machine, not indexed by memory.
     */
    public MachineSpace(final Machine machine) {
        super(mutableMap(uri(PATTERN), uri(machine.vid().resolve().extend(ALL))), MEM_SPACE_TID, null, false);
        this.owner = machine;
        this.root = machine.vid().resolve();
        this.depth = this.root.segments().size();
        // the q-processors ~/sys speaks — the ones the sys space in memory carried before the sphere took it over:
        // a fail is minted at ~/sys/fail/_?incrq, and a ?docq, ?subq, ?lockq or ?mimeq on ~/sys/… lands here too
        this.addQ(QCollection.docQ());
        this.addQ(QCollection.subQ());
        this.addQ(QCollection.incrQ());
        this.addQ(QCollection.mimeQ());
        this.addQ(QCollection.lockQ());
    }

    /**
     * Is {@code vid} in this machine's sphere — the machine itself, one of its components, or its system information?
     * The address must already be absolute: {@code ~} is resolved by the caller, against the machine it stands in.
     */
    public boolean covers(final fURI vid) {
        if (null == this.root)
            return false;
        final fURI at = vid.qLess().resolve();
        // A SCHEME OR A HOST NAMES ANOTHER RESOURCE OR ANOTHER PROCESS, whatever its path: ws://peer:8555/network is
        // the peer's network, not this machine's. The check is here because hasPrefix leaves both unchecked when the
        // prefix has neither — and no machine vid does, so every ws://…/sys/… would otherwise be the root's.
        if (at.hasScheme() || at.hasHost() || !at.hasPrefix(this.root))
            return false;
        final List<String> segments = at.segments();
        return segments.size() == this.depth || (segments.size() > this.depth && SPHERE.contains(segments.get(this.depth)));
    }

    /**
     * the sphere's part an address it covers names — the segment below the machine's vid — or {@code null} for the
     * machine itself
     */
    private String part(final fURI vid) {
        final List<String> segments = vid.qLess().resolve().segments();
        return segments.size() > this.depth ? segments.get(this.depth) : null;
    }

    @Override
    public Obj read(final fURI vid) {
        if (null == this.owner || !this.covers(vid))
            return noobj();
        final String part = this.part(vid);
        // ~/sys is this space's own store: its q-processors run there, as a space's always do
        if (SYS.equals(part))
            return super.read(vid);
        // the network answers its own branch: fields, and members at their keys' paths (and the q-procs it speaks)
        if (NETWORK.equals(part))
            return this.owner.network().read(vid);
        // a query on the machine or a component is memory's to answer (see the class doc): the component is no doc
        if (vid.hasQ())
            return noobj();
        if (null == part)
            return this.owner;
        // any other component, read live: the path below the machine walked into the component as it stands (the
        // whole remaining path, so <vid>/memory/previous walks into the memory rather than collapsing to its name)
        final List<String> segments = vid.qLess().resolve().segments();
        return this.owner.at(uri(String.join("/", segments.subList(this.depth, segments.size()))));
    }

    /**
     * Is a write at {@code vid} this sphere's to make — and therefore final, whatever it answers? A write the sphere
     * owns is never handed on: a member's deletion answers {@code noobj} (the obj written), and passing that on as a
     * decline would land a second, stray deletion in memory. The sphere owns the network branch and {@code ~/sys}.
     */
    public boolean ownsWrite(final fURI vid) {
        if (null == this.owner || !this.covers(vid))
            return false;
        final String part = this.part(vid);
        return NETWORK.equals(part) || SYS.equals(part);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (!this.ownsWrite(vid))
            return noobj();
        // ~/sys into this space's own store, through its q-processors (a fail's ?incrq mints its key here)
        if (SYS.equals(this.part(vid)))
            return super.write(vid, obj);
        // the network writes its own branch: a field in place, a member's deletion its retraction, a value refused
        return this.owner.network().write(vid, obj);
    }
}
