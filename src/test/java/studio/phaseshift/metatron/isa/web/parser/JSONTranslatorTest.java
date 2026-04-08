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

package studio.phaseshift.metatron.isa.web.parser;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.gt_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.is_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

public class JSONTranslatorTest extends AbstractMetatronTest {
    //{"_tid":"/m/rel", "_value":[1,2]}          | 1=>2
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
                    1 | 1
                    0 | 0
                    101234  | 101234
                    0.0     | 0.0
                    0.01    | 0.01
                    12.34   | 12.34
                    true    | true
                    false   | false
                    "a/b/c" | <a/b/c>
                    {"_bid":"/m/type","_tid":"/m/int", "_value":[null,null]}   | /m/int::T
                    {"_bid":"/m/inst","_tid":"/m/auto", "_value":"/m/inst/auto(/m/inst/from(abc))"}   | !(*abc)
                    {"_bid":"/m/type","_tid":"nat", "_value":[{"_tid":"/m/inst","_value":"is(gt(0))"},null]}   | nat::T[is(gt(0))]
                    {"_tid":"/m/str", "_value": "hello world"} | "hello world"
                    {"_tid":"/m/str", "_value": "a/b/c"}        | "a/b/c"
                    {"_tid":"/m/uri", "_value": "a/b/c"}        |  a/b/c
                    [1,2,3] | [1,2,3]
                    [1,"a/b",{a:1,b:2}] | [1,a/b,[a=>1,b=>2]]
                    [1,{"_tid":"/m/str", "_value":"'a/b'"},{a:1,b:2}] | [1,"'a/b'",[a=>1,b=>2]]
                    [1,{"_tid":"/m/str", "_value":"a/b"},{a:1,b:2}]   | [1,"a/b",[a=>1,b=>2]]
                    {a:1,b:2,c:3} | [a=>1,b=>2,c=>3]
                    {a:1,b:[1,2,[3,4]],c:3} | [a=>1,b=>[1,2,[3,4]],c=>3]
                    {a:1,b:[1,"2",[3.02,4]],c:3} | [a=>1,b=>[1,<2>,[3.02,4]],c=>3]
                    {a:1,b:[1,2,[3.02,4]],c:3} | [a=>1,b=>[1,2,[3.02,4]],c=>3]
                    {"_bid":"/m/inst", "_value":"plus(mult(2))"}     | plus(mult(2))
                    {"_bid":"/m/inst", "_tid":"plus?int<=int", "_value":"plus(mult(2))"}     | plus(mult(2))
                    {"_bid":"/m/inst", "_tid":"plus?rng=int{1}&dom=int{*}", "_value":"plus?int<=int{*}(mult(2))"}     | plus?int<=int{*}(mult(2))
                    {"_tid":"/m/code","_value":"1.plus(mult(2))"}   | 1.plus(mult(2))
            """)
    public void testJSONTranslation(final String json, final String mtron) {
        Router.writeToSpace("nat", INT_TYPE.predicate(is_(gt_(jnt(0)))));
        final ObjJSONSerializer translator = new ObjJSONSerializer();
        final Obj j_obj = translator.read(JsonParser.parseString(json));
        final Obj m_obj = mParser.parse(mtron);
        assertEquals(m_obj.isObjCall() ? ((Call) m_obj).tryToInst() : m_obj, j_obj);
    }

}
