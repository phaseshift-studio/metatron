package studio.phaseshift.metatron.lang.obj.mtron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

public class FluentTest extends MetatronTest {

    @Test
    public void testSimpleFluency() {
        assertEquals(jnt(11), start(jnt(1)).plus(jnt(10)).iterator().next());
        assertEquals(jnt(110), start(jnt(10)).plus(mult(jnt(10))).iterator().next());
       assertEquals(List.of(jnt(110),jnt(125)), start(jnt(10)).plus(mult(jnt(10))).split(lst(List.<Obj>of(id(),plus(jnt(15))))).merge().toList());
    }
}

