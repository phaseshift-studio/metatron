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

package studio.phaseshift.metatron.isa.grph.space;

import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.isa.SpaceTest;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.grph.space.tp3.tp3Space;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static studio.phaseshift.metatron.Tokens.LOAD;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class tp3SpaceTest extends SpaceTest {
    final static Rec config = rec(PATTERN, uri("/t/#"), uri(LOAD), uri("modern"));

    public tp3SpaceTest() {
        super(() -> tp3Space.of(config, f("/sys/space/tp3")));
        grphInstSet.create();
    }
}
