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
import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE COMPONENT AS A SPACE — the network reference script suite (the memory half is {@code MemoryScriptTest}, the
 * processor half {@code ProcessorScriptTest}).
 * <p>
 * A component is a SPACE, not a rec beside a private cache. {@code ~/network} answers the network space itself,
 * carrying its configuration ({@code pattern}, {@code q}); a processor attached after boot at {@code ~/network/q}
 * answers at the branch ({@code ~/network/q?analq}); and every address below the branch — {@code roster},
 * {@code peer}, a mounted {@code ws_server} — is the space's own field or data, not a Java side table reached by a
 * bespoke door.
 * <p>
 * {@code BasicNetwork} does not satisfy this yet, which is what these scripts are for: each line states an
 * observation and its expected answer, so the refactor is measured against the contract instead of a comment.
 */
@Disabled
public class NetworkComponentScriptTest extends AbstractMetatronTest {

    /**
     * {@code *~/network} IS the network space: it answers, its type is a {@code memSpace}, its vid is the branch
     * address, and its configuration is read below it.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/network.count()                               % 1                  [-- the branch answers the network --]",
            "*~/network.?/m/mach/component/network::T.count()  % 1                  [-- it is a network::T --]",
            "*~/network.?/m/space/memspace::T.count()          % 1                  [-- BasicNetwork IS a memSpace --]",
            // "@~/network.vid()                                 % </network>         [-- the branch address is its vid --]",
            "*~/network.>>pattern.?uri::T.count()                % 1                  [-- the space carries its pattern --]",
            "*~/network/q.?lst::T.count()                      % 1                  [-- the space carries its q list, a lst::T --]"
    })
    void testTheNetworkBranchIsTheNetworkSpace() {
    }

    /**
     * A q-proc attached after boot at {@code ~/network/q} is in the LIVE list, answers at the branch, and is
     * retracted by {@code /network/q -> noobj}.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/network/q.>>.?~analq::T.count()                % 0                  [-- no analq processor yet --]",
            "[STATE] @~/network/q >>= mult([analq::T])",
            "*~/network/q.>>.?~analq::T.count()                % 1                  [-- the update landed on the live network --]",
            "*~/network/q?analq.?/m/mach/analysis::T.count()    % 1                  [-- the attached processor answers at the branch --]",
            "[STATE] /network/q -> noobj",
            "*~/network/q.>>.?~analq::T.count()                % 0                  [-- the deletion retracts it --]"
    })
    void testAQProcLandsOnTheLiveNetwork() {
    }

    /**
     * The roster and the peer table are ADDRESSES below the branch — the roster a rec (the {@code roster::T} the
     * config declares), the peer table empty until something is dialed.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/network/roster.?rec::T.count()                 % 1                  [-- the roster is a rec at its address --]",
            "*~/network/peer.count()                           % 0                  [-- no peer has been dialed --]"
    })
    void testTheRosterAndPeerAreAddresses() {
    }

    /**
     * A server mounted below the branch IS the address it was mounted at, and mounting it puts the self-loop in the
     * peer table.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] ws_server::[origin=><ws://localhost:1111>]@~/network/web/ws",
            "*~/network/web/ws.?ws_server::T.count()           % 1                  [-- the mounted server answers at its address --]",
            "*~/network/peer.count()                           % 1                  [-- the self-loop is in the peer table --]",
            "[STATE] /network/web/ws -> noobj"
    })
    void testAServerMountedBelowTheNetworkIsReadBack() {
    }

    /**
     * A RED LINE ABORTS ITS SCRIPT, so a failed attach would leave the network's q list holding whatever the failed
     * line wrote — and a non-q-proc in that list makes the NEXT write through the layer throw (the layer casts the
     * entries). The teardown is therefore Java-side and defensive: it keeps the q-procs and drops everything else,
     * so one red script is not another's cause.
     */
    @AfterEach
    void resetTheNetworksProcessors() {
        final Addressable network = (Addressable) Machine.current().network();
        final List<Obj> qProcs = network.qs().lstValue().stream()
                .filter(qProc -> qProc instanceof QProc)
                .toList();
        ((Obj) network).asRec().at(uri(QPROC), lst(qProcs.toArray(Obj[]::new)), MUTABLE);
    }
}
