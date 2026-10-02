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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The composition motif: {@code auto_from} ({@code !*}) makes a reference resolve <em>in place</em>, so a
 * structure assembled from remote pieces reads exactly like one nested locally.
 * <pre>
 *   [a=>[b=>c]]              [a=>!*&#60;ws://server/x&#62;]      // and ws://server/x holds [b=>c]
 *   *a/b   ==&#62; c             *a/b   ==&#62; c
 * </pre>
 * To a user those two structures are the same, and every downstream access agrees — that referential
 * identity is what lets a distributed topology be written as ordinary local data instead of as a protocol.
 * <p>
 * It is also <em>live</em>: the reference is re-resolved on each access, so a change on the far side is seen
 * on the next read rather than frozen into a copy at composition time.
 * <p>
 * This is the first real distributed motif, and it needs no new machinery — {@code auto_from} is the existing
 * lazy-reference primitive and the authority guard is what carries it across the socket.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedCompositionTest extends AbstractMetatronTest {

    private static final String INLINE = "/sys/space/probe/compose/inline";
    private static final String COMPOSED = "/sys/space/probe/compose/byref";

    private static PeerCluster cluster;

    @BeforeAll
    public static void setup() throws Exception {
        cluster = PeerCluster.of(1)
                .store(PeerCluster.DEFAULT_ROOT)
                .seed(PeerCluster.DEFAULT_SEED_MATRIX)
                .start()
                .connect();
        // the peer holds a rec; this VM never writes that rec itself
        cluster.send(1, "[b=>'the-value'].to(" + cluster.root() + "/x)");
    }

    @AfterAll
    public static void teardown() {
        if (null != cluster) {
            cluster.close();
            cluster = null;
        }
    }

    /**
     * the claim, stated as an equality: nesting and reference are indistinguishable at the point of use
     */
    @Test
    public void testReferenceIsReferentiallyIdenticalToNesting() {
        Machine.writeToSpace(f(INLINE), eval("[a=>[b=>'the-value']]"));
        Machine.writeToSpace(f(COMPOSED), eval("[a=>!*<" + cluster.prefix(1).extend("x") + ">]"));

        final Obj nested = eval("*" + INLINE + "/a/b");
        final Obj composed = eval("*" + COMPOSED + "/a/b");
        assertEquals("'the-value'", String.valueOf(nested), "the inline nesting should read through");
        assertEquals(String.valueOf(nested), String.valueOf(composed),
                "a reference and a nesting must be indistinguishable at the point of use");
    }

    /**
     * and the value really did come over the socket — the peer holds it, this VM never wrote it
     */
    @Test
    public void testComposedFieldIsServedByThePeer() {
        Machine.writeToSpace(f(COMPOSED), eval("[a=>!*<" + cluster.prefix(1).extend("x") + ">]"));
        assertEquals("'the-value'", String.valueOf(eval("*" + COMPOSED + "/a/b")));
        // the peer's own namespace is not in the local store, so this can only have come across the wire
        assertThrows(Exception.class, () -> Machine.readFromSpace(f(cluster.root() + "/x/b")),
                "the peer's namespace must not leak into the local store");
    }

    /**
     * the reference is live, not a snapshot: the composition is resolved per access, so a later write on the
     * far side is observed. This is the "never stale" half of the property, and the reason a composed
     * structure can be held for the life of a computation.
     */
    @Test
    public void testComposedFieldIsLiveNotASnapshot() {
        Machine.writeToSpace(f(COMPOSED), eval("[a=>!*<" + cluster.prefix(1).extend("live") + ">]"));
        cluster.send(1, "[v=>'first'].to(" + cluster.root() + "/live)");
        assertEquals("'first'", String.valueOf(eval("*" + COMPOSED + "/a/v")),
                "the composition should resolve to what the peer holds now");
        cluster.send(1, "[v=>'second'].to(" + cluster.root() + "/live)");
        assertEquals("'second'", String.valueOf(eval("*" + COMPOSED + "/a/v")),
                "a composed reference must be re-resolved on access, not frozen at composition time");
    }

    private static Obj eval(final String code) {
        return ObjmtronSerializer.parse(code).apply();
    }
}
