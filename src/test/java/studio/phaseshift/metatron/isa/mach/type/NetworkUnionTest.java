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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A frame's reachable peers: the roster it dialed, read through to the one it inherited.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkUnionTest extends AbstractMetatronTest {

    private static BasicNetwork roster(final String authority, final String transport) {
        return new BasicNetwork(mutableMap(uri("ws://" + authority), str(transport)));
    }

    /** a frame reaches what it inherited — otherwise every nested machine would have to redial the world */
    @Test
    public void testTransportReadsThroughToThePrevious() {
        final NetworkUnion network = new NetworkUnion(roster("alpha:8555", "A"), roster("beta:8555", "B"));

        assertEquals("A", network.transportOf("alpha:8555").jvm(), "the inherited peer must stay reachable");
        assertEquals("B", network.transportOf("beta:8555").jvm(), "and so must the one this frame dialed");
        assertTrue(network.transportOf("gamma:8555").isNoObj(),
                "an authority nobody declared must resolve to nothing, so an undeclared host is not a peer");
    }

    /** a frame that redials an authority it inherited overrides it for itself, and only for itself */
    @Test
    public void testAFrameOverridesAnInheritedAuthorityForItself() {
        final BasicNetwork previous = roster("alpha:8555", "inherited");
        final NetworkUnion network = new NetworkUnion(previous, roster("alpha:8555", "redialed"));

        assertEquals("redialed", network.transportOf("alpha:8555").jvm(), "the frame's own declaration wins");
        assertEquals("inherited", previous.transportOf("alpha:8555").jvm(),
                "and the level it inherited from is untouched");
    }

    /** reach composes: a frame adds authorities, it never subtracts the ones it inherited */
    @Test
    public void testAuthoritiesAreTheUnionOfBothLevels() {
        final NetworkUnion network = new NetworkUnion(roster("alpha:8555", "A"), roster("beta:8555", "B"));

        assertEquals(Set.of("alpha:8555", "beta:8555"), network.authorities(),
                "both levels' authorities are reachable: " + network.authorities());
    }

    /** the property the lease rests on: a frame releases what it dialed and leaves what it inherited alone */
    @Test
    public void testCloseReleasesOnlyWhatTheFrameDialed() {
        final Tracked previous = new Tracked();
        final Tracked current = new Tracked();
        final NetworkUnion network = new NetworkUnion(previous, current);

        network.close();

        assertTrue(current.closed, "the frame's own roster must be released");
        assertFalse(previous.closed, "an inherited roster must never be released by the frame that borrowed it");
    }

    /** a roster that records that it was closed */
    private static final class Tracked extends BasicNetwork implements AutoCloseable {
        private boolean closed = false;

        private Tracked() {
            super();
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}
