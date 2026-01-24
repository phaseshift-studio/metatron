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

package studio.phaseshift.metatron.isa.iot.space.esp32;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WemosD1Mini extends MRec implements SoC {

    public WemosD1Mini(final fURI tid, final fURI vid) {
        super(CommonUtil.mutableMap(), tid, vid);
    }
    
    public String render() {
        String rendering = """
            {{b}}+-------------------+
            {{b}}|{{w}}1  16       23  8 {{b}}| 
            {{b}}|{{w}}2  17       24  9 {{b}}|
            {{b}}|{{w}}3  18       25 10 {{b}}|
            {{b}}|{{w}}4  19       26 11 {{b}}| 
            {{b}}|{{w}}5  20       27 12 {{b}}|
            {{b}}|{{w}}6  21       28 13 {{b}}|
            {{b}}|{{w}}7  22       29 14 {{b}}|
              {{b}}\\_         {{w}}30 15{{b}}|
                 {{b}}+____{{y}}USB{{b}}_______+{{X}}
        """;
        return rendering;
    }
}
