package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * the mtron construction contract for every concrete widget: a widget built in java must
 * survive the round trip
 *
 *     widget -> ObjmtronSerializer.write -> mtron text -> ObjmtronSerializer.read -> the same widget
 *
 * i.e. the read constructs the widget's java class (not a bare rec), write and read are
 * symmetric, and the result is equal to the original java construction. a subclass supplies
 * one small live widget via {@link #sampleWidget()} and inherits this regression gate.
 */
public abstract class AbstractWidgetTest extends AbstractInstSetTest {

    public AbstractWidgetTest(final Supplier<InstSet> instSet) {
        super(instSet);
    }

    /** one small live widget of the package under test, built in java. */
    protected abstract Object sampleWidget();

    @Test
    public void testMtronConstruction() {
        final Obj original = (Obj) sampleWidget();
        final String written = ObjmtronSerializer.single().write(original);
        final Obj reconstructed = ObjmtronSerializer.single().read(written);
        assertInstanceOf(original.getClass(), reconstructed,
                "the mtron read must construct a widget instance, not a bare rec: " + written);
        assertEquals(original, reconstructed,
                "the mtron round trip must come back equal to the java construction: " + written);
    }
}
