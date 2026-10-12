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

package studio.phaseshift.metatron.isa.mach.type.network;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A frame of reference that <b>executes somewhere else</b>. It is what {@code move(<authority>)} makes
 * {@code Machine.current()} point at, and it is deliberately thin: the connection it stands in rides in its own
 * {@code network.peer} — the peer's authority and the transport that reaches it — and it has no instset, compiler,
 * processor or memory of its own.
 * <p>
 * That thinness is the whole point. A local machine relabelled with a remote address would resolve and execute
 * <em>here</em> while merely looking remote — name resolution re-pointed, execution unmoved, and {@code ~}-relative
 * state (a fail stack, a thread) resolving to a peer address this VM cannot write. Instead every application of
 * code in this frame is handed to the peer: {@link #applyCode} ships it and returns what the peer computed. The
 * handoff has exactly one site ({@link Machine#applyCode}), so the console, {@code eval}, and any nested code all
 * follow without knowing where they ran.
 * <p>
 * Its state is the REC, not Java fields, like every other metatron object: {@code pattern} and the connection's
 * {@code authority} and {@code transport} are addressable, serialisable within the VM, and visible to the type
 * system the way a machine's component slots are.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteMachine extends MRec implements Machine {

    /**
     * The standard metatron obj constructor. The work a frame needs at birth lives HERE rather than in a factory,
     * because the enclosing frame is reachable from inside a constructor ({@link Machine#current()}).
     * <p>
     * THE VID IS THE REGISTRATION, and it is a LOCAL path, so {@code objCheckAndSave}'s write-back is exactly what
     * we want: the frame lands in this machine's memory where a plain local read finds it. (A null vid would skip
     * that write, leaving a frame that carries its address without ever being stored at it.) The rec it is handed
     * carries a PATTERN only because {@code machine::T} is declared a refinement of {@code space::T}, whose rec
     * demands one — a machine is a rec, so that requirement is the type's, not the frame's.
     */
    public RemoteMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        // THE VID IS THE REGISTRATION, and it is a LOCAL path (~/network/peer/<scheme>/<host>/<port>/<ns>), so
        // objCheckAndSave's write-back is exactly right: the frame lands in this machine's memory where a plain
        // local read finds it. A NULL vid here would SKIP that write — the frame would carry the address without
        // ever being stored at it, and nothing could read it back.
        super(jvm, tid, vid);
        // THE REACH IS THE GUARD. The rec this frame is handed carries its OWN network level holding the connection
        // it stands in (network.peer: <service-uri> => peer::T[authority]); a connection that ALREADY carries a
        // transport has a peer — it is a materialisation (a read-back, a move), not a birth — so it must NOT spawn
        // a twin. spawn is idempotent by authority besides, so the two agree even if both run.
        final Rec connection = this.connection();
        if (connection.at(uri(TRANSPORT)).isNoObj())
            connection.at(uri(TRANSPORT), PeerSpawn.spawn(this.authority()), MUTABLE);
        // A remote frame is still a frame: it lives in the frame that moved into it, and it borrows that frame's
        // compiler — minting one here would re-enter type resolution while this frame is current (which recursed
        // until the stack died). Its NETWORK, though, is its own level, handed to it by the push that made it: the
        // connection it stands in belongs to this frame, so a pop retracts exactly what this frame dialed.
        final Machine parent = Machine.current();
        this.at(COMPILER, parent.compiler(), MUTABLE);
        // ITS PROCESSOR, THOUGH, IS ITS OWN — the shipping one — and it goes IN THE SLOT like every machine's does.
        // That is the whole mechanism: every caller that evaluates code (the console, eval, a nested apply) asks the
        // machine for its processor, so a frame whose processor ships needs no case anywhere else, and the code is
        // handed over already compiled and resolved rather than as an argument the resolver would have to chase.
        // Stored, not computed on the fly: a machine that carried no processor in its rec would not be reflective.
        this.at(PROCESSOR, RemoteProcessor.of(this.transport()), MUTABLE);
        this.parent(parent);
    }

    /**
     * The connection this frame stands in — the entry of its own {@code network.peer}: a {@code peer::T} rec
     * carrying the authority it was pushed for and the transport that reaches it. A remote frame IS a connection,
     * so it has exactly one, and the single entry is the answer.
     */
    private Rec connection() {
        return Network.Helper.connections(this.atDirect(uri(NETWORK))).jvm().values().stream()
                .filter(Obj::isRec)
                .map(Obj::asRec)
                .findFirst()
                .orElseThrow(() -> MTronException.of("remote frame %s carries no peer connection", this.vid()));
    }

    /**
     * The inst that reaches the peer — message in, that peer's answer out. It rides in the connection this frame
     * carries, so the frame has the reach without looking it up.
     */
    public Inst transport() {
        return this.connection().at(uri(TRANSPORT)).as();
    }

    /**
     * the authority this frame stands in — {@code ws://host:port/<namespace>}, the peer's wire address
     */
    public fURI authority() {
        return this.connection().at(uri(AUTHORITY)).uriValue();
    }

    /**
     * Ship the application to the peer. The message is the code with the lhs threaded through it
     * ({@link Code#wrapStart}), which is the same shape a local processor runs — the peer's own {@code mtron_ws}
     * handler applies it and answers, so nothing about the computation is local.
     */
    @Override
    public Obj applyCode(final Obj lhs, final Code code) {
        return this.processor().code(code.as()).apply(lhs);
    }

    /**
     * The frame INHERITS its enclosing scope. Local bookkeeping that is not the peer's — the declared roster at
     * {@code /sys/peer}, {@code ~/thread}, the tracer — has to keep resolving while this frame is current, or the
     * roster would read empty from inside the very frame it declared and no peer address could dispatch.
     * <p>
     * Execution never comes here: every code application is shipped ({@link #applyCode}), so this is the local
     * address path only.
     */
    @Override
    public Memory memory() {
        final Obj parent = this.parent();
        return parent instanceof Machine machine ? machine.memory() : Machine.super.memory();
    }

    /**
     * The network this frame stands in: <b>its own level</b> when it carries one — the connection it was pushed
     * for — and otherwise the enclosing frame's, because a peer address must still dispatch by a network this frame
     * can reach. Inheritance is not membership: a connection belongs to the frame that dialed it, so a pop retracts
     * exactly what that frame made.
     * <p>
     * A slot that already holds a network is handed back <b>as itself</b>, never minted: {@code Machine.network()}
     * CONSTRUCTS a BasicNetwork and writes it into the slot, and constructing a component re-enters type resolution
     * — which reads back through {@code Machine.read}, i.e. through <em>this</em> method while this frame is
     * current. Minting one here recursed until the stack died.
     */
    @Override
    public Network network() {
        final Obj slot = this.atDirect(uri(NETWORK));
        if (slot instanceof Network network)
            return network;
        final Obj parent = this.parent();
        return parent instanceof Machine machine ? machine.network() : Machine.super.network();
    }

    /**
     * A remote frame owns no resources: the process behind it belongs to the push that forked it
     * ({@link PeerSpawn#reap}), not to the frame that names it.
     */
    @Override
    public void close() {
        // nothing local to release
    }
}
