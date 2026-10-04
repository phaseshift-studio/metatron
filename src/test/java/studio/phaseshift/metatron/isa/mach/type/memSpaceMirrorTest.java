package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Space mirroring, instance 1: {@link memSpace}, a real patterned Space, mounted by hand rather than imported.
 * The simplest possible instance of the framework — two operations and a value — which is what shows the
 * framework is Space-agnostic: nothing here knows about ISAs, networks or memories.
 */
public class memSpaceMirrorTest extends AbstractMachineSpaceMirrorTest {

    @Override
    protected void mountInto(final Machine machine, final String pattern, final String address, final Obj marker) {
        final boolean booting = studio.phaseshift.metatron.BootLoader.BOOTING;
        studio.phaseshift.metatron.BootLoader.BOOTING = true;
        try {
            final memSpace space = memSpace.of(f(pattern), f("/spaces" + address.replace('/', '_')));
            space.write(f(address), marker);
            machine.mount(space);        // the primitive: reconcile the self-registration, then mount at this level
        } finally {
            studio.phaseshift.metatron.BootLoader.BOOTING = booting;
        }
    }

    @Override
    protected Obj readThrough(final Machine machine, final String address) {
        return machine.read(f(address));
    }

    @Override
    protected Obj value(final int n) {
        return jnt(n);
    }
}
