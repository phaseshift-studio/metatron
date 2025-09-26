package studio.phaseshift.metatron.lang.obj.mtron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.obj.mtron.MFluent.StartLess.mult;
import static studio.phaseshift.metatron.lang.obj.mtron.MFluent.StartLess.start;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;

public class FluentTest extends MetatronTest {

    @Test
    public void testSimpleFluency() {
        assertEquals(jnt(11), start(jnt(1)).plus(jnt(10)).iterator().next());
        assertEquals(jnt(110), start(jnt(10)).plus(mult(jnt(10))).iterator().next());
//       assertEquals(List.of(jnt(110),jnt(125)), start(jnt(10)).plus(mult(jnt(10))).split(lst(List.of(id(),plus(jnt(15))))).merge().toList());

    }
}
