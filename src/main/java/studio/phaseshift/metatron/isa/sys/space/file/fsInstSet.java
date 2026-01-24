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

package studio.phaseshift.metatron.isa.sys.space.file;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.else_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.sys.space.file.fileSpace.FS_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fsInstSet extends MInstSet {

    public static final fURI FS_INSTSET_TID = fURI.of("/fs");
    public static final fURI FILE_TID = FS_INSTSET_TID.extend("file");
    public static final String FILE_TID_STRING = "/fs/file";
    public static final fURI IMAGE_TID = FS_INSTSET_TID.extend("image");

    public fsInstSet(final fURI vid) {
        super(FS_INSTSET_TID, vid);
    }

    public static fsInstSet create() {
        return new fsInstSet(fURI.fnull);
    }

    @Override
    public Set<Type> types() {
        return Set.of(
                FS_TYPE,
                T(IMAGE_TID),
                T(FILE_TID, isa_(URI_TYPE),
                        instC(INST_TID.dom(ALL.maybe()).rng(FILE_TID), lst(T(URI_TID)),
                                (lhs, inst) -> fileSpace.makeFile(Path.of(inst.arg(0).uriValue().toString())))));

    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(AS_INST_TID.dom(FILE_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> {
                    try {
                        final File file = fileSpace.resolveFile(lhs);
                        final byte[] data = new byte[(int) file.length()];
                        try (final FileInputStream fis = new FileInputStream(file)) {
                            fis.read(data);
                        }
                        return bytes(ByteBuffer.wrap(data));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                }),
                instC(AS_INST_TID.dom(URI_TID).rng(FILE_TID), lst(T(FILE_TID)), (lhs, inst) -> fileSpace.makeFile(Path.of(lhs.uriValue().toString()))),
                instC(AS_INST_TID.dom(BYTES_TID).rng(IMAGE_TID), lst(T(IMAGE_TID), else_(real(1.0d))),
                        (lhs, inst) -> str(ImageHelper.convertToAscii(lhs.bytesValue(), inst.arg(1).realValue())).tid(IMAGE_TID))));

    }
}