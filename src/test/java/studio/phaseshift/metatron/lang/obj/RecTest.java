package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MReal.real;
import static studio.phaseshift.metatron.lang.obj.mtron.MRec.rec;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public class RecTest extends MetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 | key                  | value
            "[a=>b]                                | c                    | noobj",
            "[a=>b]                                | a                    | b",
            "[a=>b]                                | a                    | /mtron/uri::b",
            "[a=>b]                                | a/                   | /mtron/rel::a=>b",
            "[a=>{b,c}]                            | a/                   | /mtron/rel::a=>{b,c}",
            "[1=>[2=>3]]                           | 1                    | [2=>3]",
            "[1=>[2=>3]]                           | 2                    | noobj",
            "[a=>[b=>c,d=>[e=>f]]]                 | a                    | [b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/                   | /mtron/rel::a=>[b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/b                  | c",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/d                  | [e=>f]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/d/e                | f",
            // "[a=>[b=>c,d=>[e=>f]]]                 | a/d/e/               | /mtron/rel::a/d/e=>f",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/#                  | {c,[e=>f]}",
            // "[a=>[b=>c,d=>[e=>f]]]                 | a/+                  | {c,[e=>f]}",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/+/e                | f"
    }, delimiter = '|')
    public void testType(final String rec, final String key, final String value) {
        Rec r = ObjParser.m_obj().parse(rec).get();
        Obj k = ObjParser.m_obj().parse(key).get();
        Obj v = ObjParser.m_obj().parse(value).get();
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isRec());
        assertEquals(v, actual);
    }


    @Override
    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 | key                               | value
            "[=>]                                  | [a=>b]                            | false",
            "[a=>b]                                | [=>]                              | true",
            "[a=>b]                                | [a=>b]                            | true",
            "[a=>b,c=>d]                           | [a=>b]                            | true",
            "[a=>b,c=>d]                           | [a=>b,c=>e]                       | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>2]]                  | true",
            "[a=>b,c=>[d=>[a=>b]]]                 | [a=>b,c=>[d=>get(a).is(eq(b))]]   | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(gt(0))]]          | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(gt(3))]]          | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(in(int::T[]))]]   | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(in(str::T[]))]]   | false",
    }, delimiter = '|')
    public void testMatches(final String recA, final String recB, final boolean matches) {
        super.testMatches(recA, recB, matches);
    }


    @Test
    public void testRecJavaAPI() {
        Rec r = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Graphitty.log(this).trace(r);
        assertEquals(jnt(1), r.at("a"));
        assertEquals(2, r.count());
        assertEquals(1, r.<Rec>at("b").count());
        assertEquals(jnt(3), r.<Rec>at("b").at("c"));
        /// //
        r = r.put("b/c", str("fhat"));
        Graphitty.log(this).trace(r);
        assertEquals(jnt(1), r.at("a"));
        assertEquals(2, r.count());
        assertEquals(1, r.<Rec>at("b").count());
        assertEquals(str("fhat"), r.<Rec>at("b").at("c"));
        /// ///
        r = r.put("d", real(1.0));
        assertEquals(1.0, r.at("d").realValue(), 0.001);
    }
}
