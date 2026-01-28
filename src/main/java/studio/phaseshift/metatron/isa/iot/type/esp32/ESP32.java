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

package studio.phaseshift.metatron.isa.iot.type.esp32;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.type.SoC;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;

import static studio.phaseshift.metatron.isa.iot.iotInstSet.ESP32_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public class ESP32 extends MRec implements SoC {

    public static final Type ESP32_TYPE = T(ESP32_TID);
    
    public ESP32(final fURI tid, final fURI vid) {
        super(CommonUtil.mutableMap(), tid, vid);
    }
}


