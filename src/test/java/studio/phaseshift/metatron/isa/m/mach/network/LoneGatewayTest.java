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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.rewrite.plan.Shard;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;

/**
 * THE WEB'S GATEWAYS LEAVE A MACHINE LONE ({@code Shard.lone}): a gateway is a peer with a host whose client serves no
 * eval, so no lane can be dealt to it, and it hides no peer that takes lanes. Each row dials both, as boot.mtron does,
 * and the hang-up drops them, so no other test of this class's VM meets them.
 */
public class LoneGatewayTest extends AbstractMetatronTest {

    private static final fURI SIBLING = f("loop://sibling:8555");
    // the gateways as boot.mtron dials them
    private static final List<String> GATEWAYS = List.of("http_client::[host=><http://#>]", "http_client::[host=><https://#>]");

    @AfterEach
    void hangUp() {
        Machine.current().network().drop(SIBLING);
        Machine.current().network().drop(f("http://#"));
        Machine.current().network().drop(f("https://#"));
    }

    /**
     * Each row: the chain % whether a peer that takes lanes is dialed beside the gateways % whether the machine is lone.
     * Both gateways are checked among the peers first: without them, the rule before tier 1 (no peer at all) would
     * answer lone too.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4}).plus(1).sum()                                % false % true",
            "start({1,2,3,4}).plus(1).sum()                                % true  % false",
            "start?place=[local,local,local,local]({1,2,3,4}).plus(1).sum() % false % false",
    }, delimiter = '%')
    void testAGatewayTakesNoLane(final String mtron, final boolean sibling, final boolean lone) {
        GATEWAYS.forEach(Machine::evaluate);
        for (final fURI gateway : List.of(f("http://#"), f("https://#")))
            checkEquality(LOG, bool(false), bool(Machine.current().network().peer(gateway.pathEncoding()).isNoObj()), true);
        if (sibling)
            Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code code = ObjmtronSerializer.parse(mtron.trim()).asCode();
        checkEquality(LOG, bool(lone), bool(Shard.lone(Machine.current().network().peers(), code)), true);
    }
}
