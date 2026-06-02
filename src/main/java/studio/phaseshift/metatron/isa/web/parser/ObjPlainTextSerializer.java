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

package studio.phaseshift.metatron.isa.web.parser;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjPlainTextSerializer extends AbstractObjSerializer<String> {

    public static final fURI OBJ_PLAIN_TEXT_SERIALIZER_VID = f("/m/mach/io/serializer/plain/text");
    public static final ObjPlainTextSerializer INSTANCE = new ObjPlainTextSerializer();

    public static final ObjPlainTextSerializer single() {
        return INSTANCE;
    }


    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return str(new String(bytes.array()));
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return ByteBuffer.wrap(obj.toString().getBytes());
    }

    @Override
    public Obj read(final String data) throws MTronException {
        return str(data);
    }

    @Override
    public String write(final Obj obj) throws MTronException {
        return obj.toString();
    }

    @Override
    public fURI vid() {
        return OBJ_PLAIN_TEXT_SERIALIZER_VID;
    }
}
