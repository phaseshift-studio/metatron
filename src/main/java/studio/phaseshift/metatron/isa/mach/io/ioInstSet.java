/*
 * metatron: a distributed virtual machine and language
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
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_SERIALIZER_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/mach/io")
public class ioInstSet extends AbstractInstSet {

    public static final fURI IO_ISA_TID = MACH_ISA_TID.extend("io");

    public ioInstSet() {
        super(mutableMap(uri(PATTERN), uri(IO_ISA_TID.extend(ALL))), INSTSET_TID, IO_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(TYPE), lst(OBJ_SERIALIZER_TYPE),
                uri(CONST), lst(
                        new ObjmtronSerializer(),
                        new ObjByteBufferSerializer(),
                        new ObjSimpleJSONSerializer(),
                        new ObjBSONSerializer())
        )));
        super.setup();
    }
}
