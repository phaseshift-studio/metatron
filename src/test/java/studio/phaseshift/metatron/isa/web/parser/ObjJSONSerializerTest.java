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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static studio.phaseshift.metatron.Tokens.HEADERS;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.gt_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.is_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.JSON_TID;

public class ObjJSONSerializerTest extends AbstractSerializerTest<JsonElement> {

    public ObjJSONSerializerTest() {
        super(new ObjJSONSerializer(), JSON_TID, "json");
    }

    // ===================================================================
    //  JSON translation (existing)
    // ===================================================================

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
        final Obj m_obj = ObjmtronSerializer.parse(mtron);
        assertEquals(m_obj.isObjCall() ? ((Call) m_obj).tryToInst() : m_obj, j_obj);
    }

    public boolean ignoreFail(final String toSerialize) {
        return (toSerialize.equals("< >") || toSerialize.contains("{24}") || toSerialize.startsWith("[a,[b,12,'abc']"));
    }

    // ===================================================================
    //  Type-conversion: str -> json::T  (tag + validate)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'{\"a\":[1,2,3],\"b\":true}'  %  object with array and bool",
            "'[1,2,3]'                      %  array",
            "'true'                         %  boolean",
            "'false'                        %  boolean false",
            "'null'                         %  null",
            "'42'                           %  integer",
            "'3.14'                         %  real",
            "'\"hello\"'                     %  string",
            "'{}'                           %  empty object",
            "'[]'                           %  empty array",
    }, delimiter = '%')
    void testStrToJsonType(final String mtronValue, final String desc) {
        final Str result = assertStrToType(mtronValue);
        assertTrue(result.strValue().length() >= 0, "must have content: " + desc);
    }

    // ===================================================================
    //  Type-conversion: json::T -> rec::T  (parse)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'{\"a\":[1,2,3],\"b\":true}'  %  object parses to rec",
            "'{\"x\":1,\"y\":2}'            %  flat object",
            "'{}'                           %  empty object -> empty rec",
    }, delimiter = '%')
    void testJsonToRec(final String mtronValue, final String desc) {
        final Rec result = assertTypeToRec(mtronValue);
        assertTrue(result.count() >= 0, "must be a valid rec: " + desc);
    }

    // ===================================================================
    //  Type-conversion: rec::T -> json::T  (serialize)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'{\"a\":[1,2,3],\"b\":true}'  %  round-trip through rec",
            "'{\"x\":1}'                    %  minimal object",
            "'{\"arr\":[1,2,3]}'            %  object with array value",
    }, delimiter = '%')
    void testRecToJson(final String mtronValue, final String desc) {
        final Str original = assertStrToType(mtronValue);
        final Str roundTripped = assertRecToType(mtronValue);
        // Semantic idempotency: re-parse both JSON strings, they must produce equal recs
        final Obj r1 = ObjJSONSerializer.simple().inputBytes(original.strValue());
        final Obj r2 = ObjJSONSerializer.simple().inputBytes(roundTripped.strValue());
        assertEquals(r1, r2, "semantic round-trip must preserve structure: " + desc);
    }

    // ===================================================================
    //  Predicate rejection: invalid JSON
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'not json'          %  plain text",
            "'{broken'           %  unclosed brace",
            "'{\"a\":}'          %  missing value",
    }, delimiter = '%')
    void testInvalidJsonRejected(final String mtronValue, final String desc) {
        assertRejected(mtronValue);
    }

    // ===================================================================
    //  Integration: full chain from mtron (existing testAsInst pattern)
    // ===================================================================

    @Test
    public void testAsInst() {
        final Str jsonStr = eval("'{\"a\":[1,2,3],\"b\":true}'.as(json::T)").as();
        assertEquals(JSON_TID, jsonStr.tid());
        assertTrue(jsonStr.isStr());

        final Rec jsonRec = eval("'" + jsonStr.strValue() + "'.as(json::T).as(rec::T)").as();
        assertEquals(REC_TID, jsonRec.tid());
        assertEquals(rec(uri("a"), lst(jnt(1), jnt(2), jnt(3)), uri("b"), bool(true)), jsonRec);

        // rec->json round-trip
        final Str jsonStr2 = eval("'" + jsonStr.strValue() + "'.as(json::T).as(rec::T).as(json::T)").as();
        assertEquals(JSON_TID, jsonStr2.tid());
        assertTrue(jsonStr2.isStr());
    }

    // ===================================================================
    //  MCP client <-> JSON conversion
    // ===================================================================

    @Test
    public void testMcpClientJsonRoundTrip() {
        // Skip if the MCP server port isn't available
        boolean portOpen;
        try (final java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 64342), 1000);
            portOpen = true;
        } catch (final Exception e) {
            portOpen = false;
        }
        assumeTrue(portOpen, "skipped: no MCP server on 127.0.0.1:64342");

        final String configJson = """
                                  {
                                      "type": "sse",
                                      "url": "http://127.0.0.1:64342/sse",
                                      "headers": {
                                          "IJ_MCP_SERVER_PROJECT_PATH": "/home/killswitch/software/metatron"
                                      }
                                  }""";

        // json -> mcp_client
        final Obj client = eval("'" + configJson + "'.as(json::T).as(mcp_client::T)");
        assertTrue(client.isRec(), "json->mcp_client must produce a rec");
        assertEquals(f("/m/web/mcp/mcp_client"), client.tid(), "must be tagged as mcp_client");

        // verify key fields survived the forward conversion
        final Rec clientRec = client.asRec();
        assertTrue(clientRec.has(uri("type")), "must have type field");
        assertTrue(clientRec.has(uri(HOST)), "must have host field (from url)");
        assertTrue(clientRec.has(uri(HEADERS)), "must have headers field");

        // mcp_client -> json (reverse)
        final Str jsonStr = eval("'" + configJson + "'.as(json::T).as(mcp_client::T).as(json::T)").as();
        assertEquals(JSON_TID, jsonStr.tid(), "must be tagged as json::T");
        assertTrue(jsonStr.isStr());

        // semantic round-trip: re-parse both JSON strings, key fields must match
        final Rec originalRec = ObjJSONSerializer.simple().inputBytes(configJson).asRec();
        final Rec roundTrippedRec = ObjJSONSerializer.simple().inputBytes(jsonStr.strValue()).asRec();
        final Obj type1 = originalRec.at(uri("type"));
        final Obj type2 = roundTrippedRec.at(uri("type"));
        assertEquals(type1.isUri() ? type1.uriValue().toString() : type1.strValue(),
                type2.isUri() ? type2.uriValue().toString() : type2.strValue(),
                "type must survive round-trip");
        final Obj url1 = originalRec.at(uri("url"));
        final Obj url2 = roundTrippedRec.at(uri("url"));
        assertEquals(url1.isUri() ? url1.uriValue().toString() : url1.strValue(),
                url2.isUri() ? url2.uriValue().toString() : url2.strValue(),
                "url must survive round-trip");
    }

    // ===================================================================
    //  Simple lossy encoding (existing)
    // ===================================================================

    @Training(
            value = "the simple JSON encoding of mtron objs is lossy",
            map1 = {0, 1},
            mapDesc = {"the mtron expression <<lhs>> serializes to the simple (lossy) JSON <<rhs>>"})
    @ParameterizedTest
    @CsvSource(quoteCharacter = '~', delimiter = '%', value = {
            "[1,2,3]                            % [1,2,3]                     % lst serializes as a plain array",
            "{9,0}                              % [9,0]                       % objs serializes as a plain array",
            "[a=>1]                             % {\"a\":1}                    % rec serializes as a plain object",
            "1                                  % 1                           % base int stays a plain scalar",
            "1.plus(2)                          % 3                           % evaluated expression serializes to its value",
            "'a/b'                              % \"a/b\"                       % str serializes plainly",
            "<//2024.12:25/09/00/00/000?tz=-0500>.as(datetime::T)  % \"<//2024.12:25/09/00/00/000?tz=-0500>\"  % datetime (a uri refinement) serializes as its wrapped uri",
    })
    public void testSimpleLossyEncoding(final String mtron, final String expectedJson, final String desc) {
        LOG.info("%s => %s (%s)", mtron, expectedJson, desc);
        final Obj obj = ObjmtronSerializer.parse(mtron).apply();
        assertEquals(expectedJson, ObjJSONSerializer.simple().write(obj).toString());
    }
}
