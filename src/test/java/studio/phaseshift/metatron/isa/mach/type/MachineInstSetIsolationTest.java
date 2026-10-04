package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import studio.phaseshift.metatron.isa.Space;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import java.util.concurrent.atomic.AtomicReference;
import studio.phaseshift.metatron.isa.m.type.Type;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Isolation and coordinate semantics for the machine's INSTRUCTION SET, with TYPES as the kind under test.
 * <p>
 * Measured, not assumed: {@code write(vid, aType)} populates BOTH views of the ISA — the space read and the typed
 * accessor — so types isolate exactly like consts and the framework stays on {@code write}/{@code read}. The extra
 * assertion below pins that agreement down, because it is the kind of thing that can drift.
 */
public class MachineInstSetIsolationTest extends AbstractSpaceIsolationTest {

    @Override
    protected Space view(final Machine machine) {
        return machine.instset();
    }

    @Override
    protected fURI key(final String name) {
        // the ISA's jurisdiction is absolute -- it claims /m/#, so its addresses are /m/...
        return f("/m/" + name);
    }

    @Override
    protected Obj value(final int n) {
        return T(f("/m/iso" + n));
    }

    /** A written type must be visible through the SPACE read and the TYPED accessor alike -- one space, two views. */
    @Test
    public void testBothViewsOfAWrittenTypeAgree() {
        final Machine machine = Machine.defaultMachine();
        final Obj type = value(7);
        machine.instset().write(key("agree"), type);
        assertEquals(type, machine.instset().read(key("agree")), "the space read sees what was written");
        assertEquals(true, machine.instset().types().contains(type), "and so does the typed accessor");
    }
}
