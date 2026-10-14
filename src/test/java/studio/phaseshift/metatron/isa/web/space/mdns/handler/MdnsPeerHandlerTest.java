/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.web.space.mdns.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.web.space.mdns.handler.mdns_peerHandler.dialable;
import static studio.phaseshift.metatron.isa.web.space.mdns.handler.mdns_peerHandler.segment;

/**
 * THE ROSTER HOLDS URIs THAT ARE USABLE AS THEY STAND. A value is handed straight to a client as its host
 * (read at an index from the roster) with nothing to take apart, and a key is a valid uri segment
 * so the rec stays well typed. Two things can corrupt that — a responder's route is UNTRUSTED text, and a DNS-SD
 * instance name is not necessarily a valid segment — and both are normalized here, with no network involved.
 */
public class MdnsPeerHandlerTest extends AbstractMetatronTest {

    private static final String PEER_HOST = "10.0.0.1";
    private static final int PEER_PORT = 8555;

    private static InetAddress peer() {
        try {
            return InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/                               % ws://10.0.0.1:8555/                  % the root route",
            "/mtron                          % ws://10.0.0.1:8555/mtron             % a real route is kept",
            "/,version=0.1-SNAPSHOT          % ws://10.0.0.1:8555/                  % an announcer that welded its TXT together: recover the route it meant",
            "/mtron,version=0.1-SNAPSHOT     % ws://10.0.0.1:8555/mtron             % ...and the same when a real route precedes the junk",
            "mtron                           % ws://10.0.0.1:8555/                  % a route must be a PATH, so a bare name is refused",
            "/mtron?out=mtron                % ws://10.0.0.1:8555/mtron?out=mtron    % a query legitimately belongs to the route",
            "/a b                            % ws://10.0.0.1:8555/                  % whitespace is refused",
            "/mtron/mcp                      % ws://10.0.0.1:8555/mtron/mcp         % a deeper route is kept whole",
    }, delimiter = '%')
    void testAdvertisedRouteBecomesAUsableAddress(final String route, final String expected, final String desc) {
        assertEquals(expected, dialable(peer(), PEER_PORT, "ws", route).toString(), desc);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "metatron       % metatron      % a plain instance name is already a segment",
            "probe-155476   % probe-155476  % dots and dashes are legal in a segment",
            "metatron (2)   % metatron-2    % JmDNS disambiguates a collision with a space and parens",
            "lab a/b        % lab-a-b       % anything else becomes a dash rather than a broken uri",
    }, delimiter = '%')
    void testInstanceNameBecomesAValidSegment(final String instance, final String expected, final String desc) {
        assertEquals(expected, segment(instance), desc);
    }

    /**
     * the CONTRACT itself, stated directly: whatever the roster holds can be dialed as-is
     */
    @Test
    void testTheValueNeedsNoParsingToBeDialed() {
        final fURI value = dialable(peer(), PEER_PORT, null, "/,version=0.1-SNAPSHOT"); // the worst advertisement seen
        assertTrue(value.hasAuthority(), "a roster value must carry an authority: " + value);
        assertEquals(PEER_HOST, value.host(), "the resolved address is the host: " + value);
        assertEquals(PEER_PORT, value.port(), "the SRV port is the port: " + value);
        assertFalse(value.toString().contains(","), "no metadata may be welded into the address: " + value);
        assertFalse(value.toString().contains(VERSION), "no TXT property may leak into the address: " + value);
    }

    private static final String VERSION = "version";
}
