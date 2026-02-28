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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
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
}
