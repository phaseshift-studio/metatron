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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BootLoaderTest {

    @Test
    @Disabled
    public void test() {
        try {
            BootLoader.load(mParser.parse("[host=><ws://127.0.0.1:8888>,cluster=>{<ws://127.0.0.1:7777>,<ws://walltron.local:5000>,<ws://127.0.0.1:9999>},boot=><examples/boot.mtron>,log=>info]"));
        } catch (final Exception e) {
            Graphitty.log(noobj()).error(e);
        }
        CommonUtil.sleepThread(1000);
        System.out.println(mParser.eval("/g.V().out().count()").toString());
    }
}
