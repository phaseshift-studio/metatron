/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.io.net;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.translate.ObjParser;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjByteBufferSerializerTest extends MetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            //obj
            "noobj",
            "int{0}::3",
            "true", "false", "1", "0", "-100", "12.355", "-12.35",
            "\"this is a string\"",
            "\"\"\"this is a multilinestring\"\"\"",
            "<http://test.uri.com>",
            "<http://test.uri.com?a=b&c=d>",
            "/mtron/test",
            "uri{24}::a/b/c",
            "[<a>,<b>,<c>,<d>]",
            "rec::[a=>b,c=>d]",
            "[<a>=>b,c=><d>]",
            "[a=>b,c=>[b=>d]]",
            "addTwentyThree(){ plus(23) }",
            "start(1).plus(2).mult(7)",
            "[=>]",
            "[,]",
            "[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "rec{0}::[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "{1,2,3,4,5}",
            "{,}"
    }, delimiter = '|')
    public void testKeyValue(final String objString) {
        ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final Obj obj = ObjParser.parse(objString);
        final ByteBuffer buffer = serializer.write(obj);
        final String objString2 = new String(buffer.array());
        final Obj obj2 = serializer.read(buffer);
        LOG.debug("testing {{b}}%s{{/b}} serialized to %s [expected: {{b}}%s{{g}}=>{{/g}}%s {{X}}]", objString, obj, objString2, obj2);
        assertEquals(obj, obj2);
    }

}
