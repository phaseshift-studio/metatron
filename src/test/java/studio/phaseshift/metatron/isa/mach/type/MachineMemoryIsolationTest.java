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

/**
 * Isolation and coordinate semantics for a machine's MEMORY — the binding space, reached through the accessor that is
 * frame-aware, so a live frame's own level answers. A memory's jurisdiction is RELATIVE names (the {@code +/#}
 * catch-all), which is why {@link #key(String)} is unqualified here while the ISA instance's is absolute: the address
 * form follows the pattern each space claims.
 */
public class MachineMemoryIsolationTest extends AbstractSpaceIsolationTest {

    @Override
    protected Space view(final Machine machine) {
        return machine.memory();
    }

    @Override
    protected fURI key(final String name) {
        return f(name);
    }

    @Override
    protected Obj value(final int n) {
        return jnt(n);
    }
}
