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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.TARGET;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The NETWORK as a q-proc source — step S2.
 * <p>
 * A subscription to a peer's namespace is LOCAL bookkeeping: {@code ws://peer/x/#?subq -> code} must register in
 * this VM and fire in this VM. Before the layer sat on the machine's network arm the {@code ?subq} rode the wire
 * and registered on the peer, which is the opposite of what a watcher on this machine needs.
 * <p>
 * No live peer is needed for any of this, and that is the point of the last case: the write is addressed at an
 * authority with no transport at all, so nothing can leave the machine — and the subscriber still fires. The
 * {@code ?subq} registration and the broadcast are answered by the layer, not by the wire.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkQProcTest extends AbstractMetatronTest {

    /**
     * an authority with no declared transport: writes to it decline at the network and fall through to memory,
     * so whatever happens to them happened locally
     */
    private static final String UNREACHABLE = "ws://unreachable.invalid:1";

    /** the value a fired subscription writes, so "did it run" is a read */
    private static final fURI FIRED = f("/t/net/fired");

    /**
     * The registry and the marker are shared state on a booted VM, so each case starts from nothing. The marker
     * needs a space of its own: a write to an address no space covers is dropped, which would look exactly like
     * a subscription that never fired.
     */
    @BeforeEach
    void resetNetworkSubscriptions() {
        if (Machine.read(f("/sys/space/tnet")).isNoObj())
            memSpace.of(f("/t/net/#"), f("/sys/space/tnet"));
        Machine.current().network().qs().lstValue().stream()
                .filter(q -> q.tid().equals(SUBQ_TID))
                .forEach(q -> q.asRec().at(uri(OBJ)).asLst().lstValue().clear());
        Machine.write(FIRED, noobj());
    }

    /**
     * How many subscriptions the network's subq processor holds for the {@code ?subq} key.
     */
    /**
     * The subscription's payload: a real {@code code}, not its result. A {@code >>>} block parses only in a value
     * position, so it is read back out of a rec literal — applying it here would register the result instead.
     * {@code >>>1} scales {@code $} by one, so the subscription writes the obj it was handed: the marker holds
     * the payload, which is what makes "did it see what was written" the assertion.
     */
    private static Obj code() {
        return ObjmtronSerializer.parse("[code=>>>1.to(" + FIRED + ")]").apply().asRec().at(uri(CODE));
    }

    private static int subscriptions() {
        return Machine.current().network().qs().lstValue().stream()
                .filter(q -> q.tid().equals(SUBQ_TID))
                .findFirst()
                .map(q -> q.<QProc>as().asRec().at(uri(OBJ)).lstValue().size())
                .orElseThrow(() -> new IllegalStateException("the network declares no subq processor"));
    }

    /**
     * The declaration: a network is a q-proc source, and subq is what it speaks.
     */
    @Test
    void shouldDeclareSubqOnTheNetwork() {
        final Network network = Machine.current().network();
        assertTrue(network.hasQ(f("subq")), "the network declares the processor a peer subscription needs");
        assertTrue(network.hasQProcs(), "and the layer's fast path sees it");
    }

    /**
     * Registration is consumed by the layer: the answer is the subscription, the authority's data is untouched,
     * and the write never became data in this machine (which is what the old fall-through did).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://unreachable.invalid:1/probe   % probe  % a quoted path addresses one endpoint",
            "ws://unreachable.invalid:1/probe/a % nested % a deeper path addresses a subtree",
    }, delimiter = '%')
    void shouldRegisterAPeerSubscriptionLocally(final String target, final String description) {
        final fURI vid = f(target).addQ("subq");
        final Obj answer = Machine.write(vid, code());
        assertNotEquals(noobj(), answer, description + ": a registration answers with the subscription it made");
        assertTrue(answer.isRec(), description + ": the answer is the subscription rec");
        assertEquals(f(target), answer.asRec().at(uri(TARGET)).uriValue(), description + ": it targets the address that was written");
        assertEquals(1, subscriptions(), description + ": the network holds exactly one subscription");
        // the registration was CONSUMED: nothing was stored at the address it was addressed to
        assertEquals(noobj(), Machine.read(f(target)), description + ": a registration is not data at its own address");
    }

    /**
     * The write that matches a peer subscription fires it — locally, in this VM, with no wire involved.
     */
    @Test
    void shouldFireAPeerSubscriptionLocally() {
        Machine.write(f(UNREACHABLE + "/#").addQ("subq"), code());
        assertEquals(1, subscriptions(), "one subscription is armed for the whole peer namespace");
        // a plain write to a peer address: the layer broadcasts it to the subscriptions before the transport
        // (which here does not exist) ever sees it
        assertEquals(str("payload"), Machine.write(f(UNREACHABLE + "/x"), str("payload")),
                "the write still answers what it wrote: the broadcast does not consume it");
        CommonUtil.sleepThread(700);   // the broadcast applies its code on a virtual thread, as subq always has
        assertEquals(str("payload"), Machine.read(FIRED),
                "the subscriber ran, in this VM, and saw exactly the obj written to the peer address");
    }

    /**
     * A plain write to a peer registers nothing — the layer does not invent subscriptions.
     */
    @Test
    void shouldNotRegisterAnythingForAPlainWrite() {
        assertEquals(0, subscriptions(), "nothing is armed to begin with");
        Machine.write(f(UNREACHABLE + "/y"), str("value"));
        assertEquals(0, subscriptions(), "a write with no ?subq registers nothing");
    }
}
