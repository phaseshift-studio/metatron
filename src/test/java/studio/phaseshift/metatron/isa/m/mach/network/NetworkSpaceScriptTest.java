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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * THE NETWORK IS A SPACE, AND ITS MAPS ARE ITS DATA. A level's peers and its listeners are each ONE entry of the
 * level's space — {@code peer} and {@code listener}, maps keyed by a member's path encoding
 * ({@code ws/lab/8555/mtron => peer::T}) — beside {@code host}, {@code status} and the root's {@code roster}. So every
 * read is the space's own addressing and none is a bespoke door: {@code *</network/+>} hands the maps out with the other
 * entries, {@code *</network/peer>} is a read like any other, a member and its fields are read at their keys' paths, and
 * the uri tree ({@code /network.>>.>>.>>}) walks down through a map's keys to its members and their fields, exactly
 * as it walks a path-keyed rec in any space ({@code /a -> [b/c=>1]} walks {@code /a/b}, then {@code /a/b/c}).
 * <p>
 * The members are dialed silently ({@code [DIAL_SILENT]}: a client that declares what a peer is talked to with and is
 * never live, so no socket opens) and the hosts are dot-free, so no address in a script reads as an instruction. A
 * pushed frame's level is used where the whole level is compared, because it holds nothing a script did not put there
 * but its {@code host} and {@code status}.
 */
public class NetworkSpaceScriptTest extends NetworkScriptTest {

    /**
     * a red line aborts its script, so the pop at its end may never run: the next script starts at the root regardless
     */
    @AfterEach
    public void backToTheRoot() {
        this.resetToRoot();
    }

    /**
     * {@code *</network/+>} HANDS OUT THE PEER MAP, and the map is read like any entry. Its members sit at their keys,
     * and the uri tree enters the map through those keys, a path segment at a time, down to the members and then into
     * their fields: the http member's key is one segment shorter than the ws member's, so the fourth step reaches the
     * ws member and the http member's fields at once.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[DIAL_SILENT ws://lab:8555/mtron]",
            "[DIAL_SILENT http://quarry:8777]",
            "*/network/+.>>ws/lab/8555/mtron.>>origin             % <ws://lab:8555>             [-- /network/+ hands out the peer map, its member at its key --]",
            "*/network/peer.>>.count()                             % 2                           [-- the map is read like any entry --]",
            "*/network/peer/ws/lab/8555/mtron/origin               % <ws://lab:8555>             [-- a member's field, at its key's path --]",
            "/network/peer.>>                                      % {</network/peer/ws>,</network/peer/http>}",
            "/network/peer.>>.>>.>>                                % {</network/peer/ws/lab/8555>,</network/peer/http/quarry/8777>}",
            "/network/peer.>>.>>.>>.>>                             % {</network/peer/ws/lab/8555/mtron>,</network/peer/http/quarry/8777/origin>,</network/peer/http/quarry/8777/host>,</network/peer/http/quarry/8777/client>}",
            "/network/peer/ws/lab/8555/mtron.>>                    % {</network/peer/ws/lab/8555/mtron/origin>,</network/peer/ws/lab/8555/mtron/host>,</network/peer/ws/lab/8555/mtron/client>}"
    })
    void testThePeerMapIsAnEntryOfTheRootLevel() {
    }

    /**
     * A LEVEL'S URI TREE IS ITS ENTRIES. A fresh frame's level holds its own {@code host} and {@code status}; the
     * first dial adds the {@code peer} entry, and the last hang-up takes it away again, because a map with no member
     * is an absent field, not an empty one.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(network_lab)",
            "/network_lab/network.>>                               % {</network_lab/network/host>,</network_lab/network/status>}                               [-- nothing dialed --]",
            "*/network_lab/network/peer.count()                    % 0",
            "[DIAL_SILENT ws://lab:8555/mtron]",
            "/network_lab/network.>>                               % {</network_lab/network/host>,</network_lab/network/peer>,</network_lab/network/status>}  [-- the map is an entry of the level --]",
            "*</network_lab/network/peer/>.>>                      % *</network_lab/network/peer>   % true   [-- x/ is x => *x: its range is the map --]",
            "*</network_lab/network/peer/>.>>ws/lab/8555/mtron.>>origin  % <ws://lab:8555>",
            "[STATE] </network_lab/network/peer/ws/lab/8555/mtron> -> noobj",
            "*/network_lab/network/peer.count()                    % 0                           [-- the last member gone, the map is gone with it --]",
            "/network_lab/network.>>                               % {</network_lab/network/host>,</network_lab/network/status>}",
            "[STATE] *~.pop(_)"
    })
    void testALevelsUriTreeIsItsEntries() {
    }

    /**
     * A PATTERN REACHES THE MEMBERS AT THEIR KEYS' DEPTH, and a hang-up is a deletion at a member's key — one surface
     * leaves and its sibling under the same service stays.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(network_lab)",
            "[DIAL_SILENT ws://lab:8555/mtron]",
            "[DIAL_SILENT ws://lab:8555/mcp]",
            "*/network_lab/network/peer/ws/lab/8555/+.>>host       % {<ws://lab:8555/mtron>,<ws://lab:8555/mcp>}",
            "/network_lab/network/peer/ws/lab/8555.>>              % {</network_lab/network/peer/ws/lab/8555/mtron>,</network_lab/network/peer/ws/lab/8555/mcp>}",
            "[STATE] </network_lab/network/peer/ws/lab/8555/mcp> -> noobj",
            "/network_lab/network/peer/ws/lab/8555.>>              % </network_lab/network/peer/ws/lab/8555/mtron>               [-- one surface hung up, its sibling stays --]",
            "*/network_lab/network/peer.>>.count()                 % 1",
            "[STATE] </network_lab/network/peer/#> -> noobj",
            "*/network_lab/network/peer.count()                    % 0",
            "[STATE] *~.pop(_)"
    })
    void testAPatternReachesTheMembersAtTheirKeysDepth() {
    }

    /**
     * A WILDCARD KEY IS A KEY. The web gateway keys as {@code http/#}, and hanging it up retracts that member alone —
     * as a pattern, the same address would take every http member with it. The map stays the level's to construct:
     * neither a member's field nor the map itself takes a written value, and the Java accessor reads the entry the
     * space holds.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(network_lab)",
            "[DIAL_SILENT http://#]",
            "[DIAL_SILENT http://quarry:8777]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount                             % 2                           [-- the accessor reads the entry the space holds --]",
            "[STATE] </network_lab/network/peer/http/#> -> noobj",
            "*</network_lab/network/peer>.>>.>>host                 % <http://quarry:8777>        [-- the gateway alone left: its key is literal, not a pattern --]",
            "[STATE] </network_lab/network/peer/http/quarry/8777/host> -> <http://elsewhere:1>",
            "*</network_lab/network/peer/http/quarry/8777/host>     % <http://quarry:8777>        [-- a member's field is not written --]",
            "[STATE] </network_lab/network/peer> -> [quarry=>1]",
            "*</network_lab/network/peer>.>>.count()                % 1                           [-- nor is the map --]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount                             % 1",
            "[STATE] *~.pop(_)"
    })
    void testAWildcardKeyIsAKey() {
    }

    /**
     * THE LISTENER MAP IS AN ENTRY TOO. A socket this machine serves through is its {@code listener} member, keyed by
     * the socket's path encoding (a loopback host keys as {@code localhost}), so it is handed out by
     * {@code *</network/+>}, read at {@code *</network/listener>}, and walked to by the uri tree.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/network/listener.>>.count()                         % 1",
            "*/network/+.>>ws/localhost/{{port}}.>>origin          % <ws://127.0.0.1:{{port}}>",
            "/network/listener.>>.>>.>>                            % </network/listener/ws/localhost/{{port}}>",
            "*/network/listener/ws/localhost/{{port}}/origin       % <ws://127.0.0.1:{{port}}>",
    }, delimiter = '%')
    void testTheListenerMapIsAnEntry(final String code, final String expected) {
        final String port = String.valueOf(generatePort());
        final fURI socket = f("ws://127.0.0.1:" + port);
        try {
            this.script("[STATE] ws_server::[origin=><" + socket + ">]");
            checkCodeParseApply(LOG, code.replace("{{port}}", port), expected.replace("{{port}}", port));
        } finally {
            Machine.current().network().unlisten(socket);
        }
    }
}
