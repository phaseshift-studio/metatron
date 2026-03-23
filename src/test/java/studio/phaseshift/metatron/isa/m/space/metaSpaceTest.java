/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class metaSpaceTest extends AbstractSpaceTest {

    private static Space SYS_SPACE = null;
    private static Space META1_SPACE = null;
    private static Space META2_SPACE = null;
    //  private static Space TEST_SPACE = null;

    public metaSpaceTest() {
        super(f("/cluster/a/t"), () -> {
            final List<Obj> peers = List.of(uri("ws://localhost:6666"), uri("ws://localhost:7777"));
            // TEST_SPACE = memSpace.of(rec(uri(PATTERN), uri("/test/#")), f("/sys/space/test"));
            /*META1_SPACE = metaSpace.of(rec(
                            uri(PATTERN), uri("/cluster/a/t/#"),
                            uri(HOST), peers.get(0),
                            uri(ROUTE), rec(uri("/cluster/a"), uri("/test")),
                            uri(PEERS), lst(peers)),
                    f("/sys/space/meta1"));*/
            META2_SPACE = metaSpace.of(rec(
                            uri(PATTERN), uri("/cluster/a/t/#"),
                            uri(HOST), peers.get(1),
                            uri(ROUTE), rec(uri("/cluster/a/t"), uri("/test"))
                            /* uri(PEERS), lst(peers)*/),
                    f("/sys/space/meta2"));
            return META2_SPACE;
        });

    }

    @Test
    @Disabled
    public void testDistribution() {
        final int meta1Port = generatePort();
        final int meta2Port = generatePort();
        final List<Obj> peers = List.of(uri("ws://localhost:" + meta1Port), uri("ws://localhost:" + meta2Port));
        LOG.warn("\n\tmeta1Port: %s\n\tmeta2Port: %s", meta1Port, meta2Port);
        META1_SPACE = metaSpace.of(rec(
                        uri(PATTERN), uri("/cluster/a/t/#"),
                        uri(HOST), peers.get(0),
                        uri(PERSIST), uri("/tmp/meta1.mtron"),
                        //uri(ROUTE), rec(uri("/cluster/a/t"), uri("/test")),
                        uri(PEERS), lst(peers)),
                f("/sys/space/meta1"));
        CommonUtil.sleepThread(1000);
        META2_SPACE = metaSpace.of(rec(
                uri(PATTERN), uri("/cluster/b/t/#"),
                uri(HOST), peers.get(1),
                uri(PERSIST), uri("/tmp/meta2.mtron"),
                uri(PEERS), lst(peers)), f("/sys/space/meta2"));
        final Map<fURI, Obj> data = generateRandomData(f("/cluster/a/t"), 100);
        CommonUtil.sleepThread(1000);
        data.forEach(META1_SPACE::write);
        META1_SPACE.close();
        META2_SPACE.close();
    }
}
