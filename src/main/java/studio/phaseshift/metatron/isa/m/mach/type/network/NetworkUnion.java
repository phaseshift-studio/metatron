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

package studio.phaseshift.metatron.isa.m.mach.type.network;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.ROSTER;

/**
 * NetworkUnion — one machine's OWN level of reach in a frame stack: the network's seat in the same
 * parade as {@code MemoryUnion} and the child's {@code BasicInstSet} in {@code Machine.push}. It is the level that is
 * never the root's, and it keeps the network's two laws:
 * <ol>
 *     <li><b>knowledge is shared</b> — the roster is ONE listing, the root's (like Memory's base frame, but
 *     machine-global rather than per variable stack), so this level keeps none of its own: a call at or below its
 *     {@code roster} is the root's, {@code ~/network/roster/x/y/z} answered as {@code /network/roster/x/y/z} — read,
 *     written, and walked by the uri tree there. A declaration made from a child lands in the one listing and
 *     outlives the frame.</li>
 *     <li><b>reach is not</b> — peers are neither inherited from the parent (not even shadowed) nor
 *     shared with children. A connection belongs to exactly the machine that dialed it: a pop
 *     retracts this machine's dials and nothing else, and a sibling never sees them.</li>
 * </ol>
 * A third question — DENOTATION — does accumulate, but it needs no seat here either: a name row is
 * written to its owning level's table and resolved by walking the frame stack, so the shadowing lives
 * in {@link Component.Prefix.Helper#effectivePrefix(Component.Prefix, fURI)} beside the algebra it
 * feeds, not in a per-level wrapper. A future shadow-and-deny reach rule would be born here too.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkUnion extends BasicNetwork {

    public NetworkUnion() {
        super();
    }

    /**
     * this level's address for {@code vid} when it is the roster or lies below it ({@code roster/x/y/z}), else null
     */
    private fURI rostered(final fURI vid) {
        final fURI at = this.stored(vid);
        return null != at && !at.segments().isEmpty() && ROSTER.equals(at.segments().getFirst()) ? at : null;
    }

    @Override
    public Obj read(final fURI vid) {
        final fURI at = this.rostered(vid);
        return null == at ? super.read(vid) : Machine.read(ROOT_ROSTER.retract(1).extend(at));
    }

    @Override
    public <OBJ extends Obj> OBJ at(final Obj key) {
        // The roster is the ROOT's alone (knowledge is shared), and an ancestor resolving a descendant's address walks
        // the descendant's rec with Rec.at, never network.read — so this level must redirect roster addresses here too,
        // or */a/b/network/roster read from the root answers noobj (this level's own store holds no roster).
        if (key.isUri()) {
            final fURI at = this.rostered(key.uriValue());
            if (null != at)
                return (OBJ) Machine.read(ROOT_ROSTER.retract(1).extend(at));
        }
        return super.at(key);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        // a child DECLARES knowledge, it never shadows it: the write lands in the one listing
        final fURI at = this.rostered(vid);
        return null == at ? super.write(vid, obj) : Machine.write(ROOT_ROSTER.retract(1).extend(at), obj);
    }

    /**
     * the uri tree below the roster is the root's too, named at this level's branch so a walk stays where it began
     */
    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        final Function<fURI, Iterator<IdObj>> own = super.directReader();
        return vid -> {
            final fURI at = this.rostered(vid);
            return null != at && Machine.root().network() instanceof Space root
                    ? IteratorUtil.map(root.directReader().apply(at), id -> IdObj.of(this.asked(vid, id.furi()), id.obj()))
                    : own.apply(vid);
        };
    }
}
