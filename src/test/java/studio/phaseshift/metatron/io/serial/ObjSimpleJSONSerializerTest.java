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

package studio.phaseshift.metatron.io.serial;

import com.google.gson.JsonElement;
import studio.phaseshift.metatron.SerializerTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjSimpleJSONSerializerTest extends SerializerTest<JsonElement> {
    public ObjSimpleJSONSerializerTest() {
        super(new ObjSimpleJSONSerializer());
    }

    public boolean ignoreFail(final String toSerialize) {
        final Obj obj = mParser.parse(toSerialize);
        if(obj.isNoObj())
            return false;
        if (!obj.c().isOne())
            return true;
        if(obj.isUri() && !obj.uriValue().toString().trim().isEmpty() && toSerialize.startsWith("<") && toSerialize.endsWith(">"))
            return false;
        return obj.isObjs() ||
                (obj.isLst() && !obj.asLst().isEmpty()) ||
                (obj.isUri() && obj.uriValue().toString().trim().isEmpty());
    }

}

