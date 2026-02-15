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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class SerializerTest<T> extends mTest {

    protected final ObjSerializer<T> serializer;

    public SerializerTest(final ObjSerializer<T> serializer) {
        this.serializer = serializer;
    }

    public boolean ignoreFail(final String toSerialize) {
        return false;
    }

    @ParameterizedTest
    @CsvSource(value = {
            //obj
            "noobj",
            "int{0}::3",
            "real::2.123",
            "true",
            "false",
            "bool::true",
            "bool::false",
            "1",
            "0",
            "-100",
            "12.355",
            "-12.35",
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
            "[a=>b,c=>rec::[b=>d]]",
            "[a=>uri::b,c=>rec::[b=>d]]",
            "[a=>uri::b,uri::c=>rec::[b=>d]]",
            //"[a=>uri::b,str::'c'=>rec::[b=>d],uri::d=>rec::[b=>str::'d']]",
            //"addTwentyThree(){?}",
            "plus(2).mult(7)",
            "start(1).plus(2).mult(7)",
            "[=>]",
            "[,]",
            "< >",
            "[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "rec{0}::[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "{1,2,3,4,5}",
            "{true, false, 1,0, -100, 12.355, -12.35}",
            "{true, false, {1,0}, {-100, 12.355, -12.35}}",
            "{,}"
    }, delimiter = '|')
    public void testSerializeDeserializeObj(final String objString) {
        final Obj obj = mParser.eval(objString);
        Obj obj2 = null;
        try {
            final T buffer = this.serializer.write(obj);
            obj2 = serializer.read(buffer);
        } finally {
            LOG.debug("testing {{b}}%s{{/b}} serialized to %s => %s", objString, obj, obj2);
            if (this.ignoreFail(objString)) {
                final boolean areEqual = Objects.equals(obj, obj2);
                if (areEqual)
                    LOG.error("no need to ignore test %s <=> %s", objString, obj);
                else
                    LOG.debug("ignoring fail for %s <=> %s", objString, obj);
            } else {
                assertEquals(obj, obj2);
            }

        }

    }
}
