package studio.phaseshift.metatron.lang.inst;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.MemSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.fURI.*;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.*;

public class InstTest {

    @BeforeAll
    public static void setup() {
        BootLoader.load();
    }

    @ParameterizedTest
    @CsvSource(value = {
            // furi | tid | dom | range
            "/mtron/plus?dom=/mtron/int&rng=/mtron/int|/mtron/plus|/mtron/int|/mtron/int",
            "/mtron/mult/a?dom=+&rng=+|/mtron/mult/a|+|+" },
            delimiter = '|')
    public void testDomRng(final String f, final String op, final String dom, final String rng) {
        final fURI furi = fURI.of(f);
        final Inst inst = MInst.instA(furi);
        assertEquals(op, inst.tid().path());
        assertEquals(fURI.of(dom),inst.dom().tid());
        assertEquals(fURI.of(rng), inst.rng().tid());
    }

    @Test
    public void testInstObj() {
       assertEquals(PLUS_TID.query(DOM,INT_TID).query(RNG,INT_TID),  new MInstSet(fURI.of("/mnt/mtron")).resolve(MInt.of(2),MInst.instA(fURI.of("plus"))).tid());
       // assertEquals(START_TID.query(DOM,NOOBJ_TID).query(RNG, ANY),  new MInstSet().resolve(NoObj.single(),MInst.instA(fURI.of("start"))).tid());
       //System.out.println(i);
    }
}
