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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The authority algebra, now owned by the Network component.
 * <p>
 * It lives there rather than on the Router because it is not routing — it is what a network <em>is</em>. Every
 * question about whether two addresses denote the same service, or whether a host names this machine, is a
 * question about reachability, and reachability is the Network's business.
 * <p>
 * Pinned here because it moved: a relocation should be verified rather than assumed, and these are the semantics
 * the authority guard's loop-prevention rests on. Getting the loopback aliasing wrong is not cosmetic — the boot
 * binds the wildcard while a peer addresses the loopback name, so an alias-blind comparison makes a Router forward
 * a request to <em>itself</em>.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkHelperTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "0.0.0.0:8555   % localhost:8555  % true",
            "127.0.0.1:8555 % localhost:8555  % true",
            "[::1]:8555     % localhost:8555  % true",
            "127.0.0.1      % localhost       % true",
            "localhost:1    % localhost:2     % false",
            "machineA:8555  % machineB:8555   % false",
            "machineA       % machineA        % true",
            "machineA:8555  % machineA        % true",
    }, delimiter = '%')
    void testSameAuthority(final String a, final String b, final boolean same) {
        assertEquals(same, Network.Helper.sameAuthority(a, b),
                "%s and %s should %s denote the same service".formatted(a, b, same ? "" : "not"));
    }

    /**
     * an unknown authority is nobody, rather than everything
     */
    @Test
    public void testNullAuthoritiesAreNeverTheSame() {
        assertFalse(Network.Helper.sameAuthority(null, "localhost:8555"));
        assertFalse(Network.Helper.sameAuthority("localhost:8555", null));
        assertFalse(Network.Helper.sameAuthority(null, null));
    }

    /**
     * the components, including the bracketed IPv6 form that naive colon-splitting gets wrong
     */
    @Test
    public void testAuthorityComponents() {
        assertEquals("localhost", Network.Helper.hostOf("localhost:8555"));
        assertEquals("8555", Network.Helper.portOf("localhost:8555"));
        assertEquals("[::1]", Network.Helper.hostOf("[::1]:8555"),
                "a bracketed IPv6 host must not be split at its own colons");
        assertEquals("8555", Network.Helper.portOf("[::1]:8555"));
        assertNull(Network.Helper.portOf("localhost"), "no port given is not an empty port");
        assertNull(Network.Helper.hostOf(null));
    }

    /**
     * the four names that all mean this machine
     */
    @Test
    public void testLoopbackHosts() {
        for (final String host : new String[]{"0.0.0.0", "127.0.0.1", "localhost", "::1", "[::1]"})
            assertTrue(Network.Helper.isLoopbackHost(host), host + " names this machine");
        assertFalse(Network.Helper.isLoopbackHost("machineA"));
        assertFalse(Network.Helper.isLoopbackHost(null));
    }
}
