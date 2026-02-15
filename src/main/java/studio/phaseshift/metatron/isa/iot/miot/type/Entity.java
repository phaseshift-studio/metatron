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

package studio.phaseshift.metatron.isa.iot.miot.type;

import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.MIOT_ENTITY_TID;
import static studio.phaseshift.metatron.isa.iot.miot.miotInstSet.MIOT_THING_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Entity {

    public static final Type MIOT_ENTITY_TYPE = Type.Builder.build()
            .tid(MIOT_THING_TID)
            .vid(MIOT_ENTITY_TID).create();
}
