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

package studio.phaseshift.metatron.lang.net.iot;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.SpaceTest;
import studio.phaseshift.metatron.lang.net.web.webInstSet;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class mqttSpaceTest extends SpaceTest {
    
    
    @BeforeAll
    public static void setup() {
        webInstSet.create();
        MoquetteServer.run();;
        SPACE = () -> {
            try {
               
                final mqttSpace space = mqttSpace.of(Map.of(
                        uri(HOST), uri("mqtt://127.0.0.1:1883"),
                        uri(PREFIX), uri("/"),
                        uri(PATTERN), uri("/t/#")), fURI.of("/sys/router/space/t"));
                space.directWriter().apply(f("#"),noobj());
                return space;
            } catch (Exception e) {
                Graphitty.log(mqttSpaceTest.class).warn("skipping test as no test server is running");
              //  assumeTrue(false);
                return null;
            }
        };
    }

}
