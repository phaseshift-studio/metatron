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
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_REMOTE_MACHINE_TID;

/**
 * A frame of reference that <b>executes somewhere else</b>. It is what {@code move(<authority>)} makes
 * {@code Machine.current()} point at, and it is deliberately thin: the authority is its address, the roster
 * transport that reaches the peer is its {@code transport} field, and it has no instset, compiler, processor or
 * memory of its own.
 * <p>
 * That thinness is the whole point. A local machine relabelled with a remote address would resolve and execute
 * <em>here</em> while merely looking remote — name resolution re-pointed, execution unmoved, and {@code ~}-relative
 * state (a fail stack, a thread) resolving to a peer address this VM cannot write. Instead every application of
 * code in this frame is handed to the peer: {@link #applyCode} ships it and returns what the peer computed. The
 * handoff has exactly one site ({@link Machine#applyCode}), so the console, {@code eval}, and any nested code all
 * follow without knowing where they ran.
 * <p>
 * Its state is the REC, not Java fields, like every other metatron object: {@code pattern} and {@code transport}
 * are addressable ({@code *<authority>/transport} is the inst), serialisable, and visible to the type system the
 * way a machine's component slots are.
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
        // THE REACH IS THE GUARD. A frame whose peer rec already carries a transport has a peer — it is a
        // materialisation (a read-back, a move), not a birth — so it must NOT spawn a twin. spawn is idempotent by
        // authority besides, so the two agree even if both run.
        final Obj peer = this.at(uri(PEER));
        if (peer.isRec() && peer.asRec().at(uri(TRANSPORT)).isNoObj())
            peer.asRec().at(uri(TRANSPORT),
                    PeerSpawn.spawn(peer.asRec().at(uri(AUTHORITY)).uriValue()), MUTABLE);
        // A remote frame is still a frame: it lives in the frame that moved into it, and it borrows that frame's
        // compiler and network — minting those here would re-enter type resolution while this frame is current
        // (which recursed until the stack died).
        final Machine parent = Machine.current();
        this.at(COMPILER, parent.compiler(), MUTABLE);
        this.at(NETWORK, parent.network(), MUTABLE);
        // ITS PROCESSOR, THOUGH, IS ITS OWN — the shipping one — and it goes IN THE SLOT like every machine's does.
        // That is the whole mechanism: every caller that evaluates code (the console, eval, a nested apply) asks the
        // machine for its processor, so a frame whose processor ships needs no case anywhere else, and the code is
        // handed over already compiled and resolved rather than as an argument the resolver would have to chase.
        // Stored, not computed on the fly: a machine that carried no processor in its rec would not be reflective.
        this.at(PROCESSOR, RemoteProcessor.of(peer.asRec().at(uri(TRANSPORT))), MUTABLE);
        this.parent(parent);
    }

    /**
     * The inst that reaches the peer — message in, that peer's answer out. It rides in the peer rec the frame
     * carries, so the frame has the reach without looking it up, and the authority beside it is what {@code reap}
     * uses to find the process (the frame's own address is local now, so it can no longer name the peer).
     */
    public Inst transport() {
        return this.at(uri(PEER)).asRec().at(uri(TRANSPORT)).as();
    }

    /** the authority this frame stands in — {@code ws://host:port}, the peer's wire address */
    public fURI authority() {
        return this.at(uri(PEER)).asRec().at(uri(AUTHORITY)).uriValue();
    }

    /**
     * Ship the application to the peer. The message is the code with the lhs threaded through it
     * ({@link Code#wrapStart}), which is the same shape a local processor runs — the peer's own {@code mtron_ws}
     * handler applies it and answers, so nothing about the computation is local.
     */
    @Override
    public Obj applyCode(final Code code, final Obj lhs) {
        // EVERY application ships, and every application ships in ONE place: the shipping lives in the processor, so
        // this routes to it rather than repeating the act. That matters because the default applyCode has a branch
        // for a SINGLE inst (call.resolve(arg).apply(arg)) which applies through the static runtime and never touches
        // the processor — a bare `1+2` in a remote frame would have run HERE with no packet and no log. The
        // processor cannot make that mistake: it only ever ships.
        if (lhs.isFail())
            return lhs; // a fail is this VM's diagnostic, not a payload
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
     * The roster view from this frame: the ENCLOSING frame's network, because membership is declared once at
     * {@code /sys/peer} and is global. A peer address read from this frame therefore dispatches by the same roster
     * the frame itself was minted from.
     * <p>
     * Inherited rather than minted, and that is load-bearing: {@code Machine.network()} CONSTRUCTS a BasicNetwork,
     * and constructing a component re-enters type resolution — which reads back through {@code Machine.read}, i.e.
     * through <em>this</em> method while this frame is current. Minting one here recursed until the stack died.
     */
    @Override
    public Network network() {
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
