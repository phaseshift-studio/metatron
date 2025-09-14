package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;
import studio.phaseshift.metatron.lang.obj.mtron.MType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.INT_TID;

public class TypeTest {

    @Test
    public void testType() {
        Type t = MType.of(INT_TID);
        assertEquals(INT_TID,t.tid());
        assertEquals(INT_TID,t.vid());
        assertEquals(t.tid(),t.vid());
        assertTrue(MInt.of(23).matches(t));
    }
}
