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

package studio.phaseshift.metatron.isa.mach.io;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/mach/io")
public class ioInstSet extends AbstractInstSet {

    public static final fURI IO_ISA_TID = MACH_ISA_TID.extend("io");
    public static final fURI OBJ_SERIALIZER_TID = IO_ISA_TID.extend("serializer");
    public static final fURI OBJ_CLEAN_STRING_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("string").extend("clean");
    public static final fURI OBJ_BYTE_BUFFER_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("bytebuffer");
    public static final fURI OBJ_SIMPLE_JSON_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("json").extend("simple");

    public static final Type OBJ_SERIAL_TYPE = Type.Builder.build().tid(OBJ_SERIALIZER_TID).vid(OBJ_SERIALIZER_TID).create();

    public ioInstSet() {
        super(IO_ISA_TID, IO_ISA_TID);
    }

    @Override
    public Set<Obj> consts() {
        return new LinkedHashSet<>(List.of(
                new ObjCleanStringSerializer(),
                new ObjByteBufferSerializer(),
                new ObjSimpleJSONSerializer()
        ));
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>(List.of(
                OBJ_SERIAL_TYPE
        ));
    }

}
