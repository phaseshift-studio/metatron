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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractInstSetTest extends AbstractMetatronTest {

    protected InstSet space;
    protected final Supplier<InstSet> spaceSupplier;

    public AbstractInstSetTest(final Supplier<InstSet> instSetSupplier) {
        super();
        this.spaceSupplier = instSetSupplier;

    }

    @BeforeEach
    protected void setup() {
        this.space = this.spaceSupplier.get();
        if (null != this.space) {
            if (this.space.vid() == null)
                LOG.warn("provided space has no vid and thus can not be shutdown automatically");
            // MOUNT BEFORE SETUP. Both production imports do it in this order
            // (InstSet.INSTSET_TYPE's constructor and importInstSetStream: addSpace, then setup).
            // It matters: an instset's setup() doc-wraps every definition it declares, and
            // QCollection.internalDocWrap resolves the owning space of each id via getSpaceFor.
            // With the instset not yet mounted, those ~78 documentation writes fall through to the
            // enclosing space's doc-query space, where each deep write costs tens of milliseconds
            // (measured ~4.2s per setup, versus ~0.1s once the instset is mounted first). Since this
            // fixture runs per @ParameterizedTest row, the wrong order made every row of
            // mathInstSetTest — as()-heavy or not — pay that cost.
            Machine.current().memory().addSpace(this.space);
            this.space.setup();
        }
    }

    @AfterEach
    protected void stop() {
        if (null != this.space) {
            assertDoesNotThrow(this.space::close);
            if (null != this.space.vid())
                Machine.current().memory().removeSpace(this.space.vid());
            this.space = null;
        }
    }

    @Test
    @Disabled
    public void testInstDomRngMatching() {
        AtomicInteger hasDomRng = new AtomicInteger(0);
        AtomicInteger hasNotDomRng = new AtomicInteger(0);
        this.space.insts().forEach(inst -> {
            if (inst.hasDom() && inst.hasRng()) {
                hasDomRng.getAndIncrement();
                long d = Machine.read(inst.tid().dom(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long dash = Machine.read(inst.tid().dom(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long r = Machine.read(inst.tid().rng(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long rash = Machine.read(inst.tid().rng(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long dr = Machine.read(inst.tid().rng(null).dom(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long drash = Machine.read(inst.tid().rng(ALL).dom(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                LOG.debug("inst [%s] dom [%s] rng [%s] domRng [%s]", inst.tid().basePath(), d, r, dr);
                assertTrue(d > 0);
                if (!inst.dom().c().isZeroable())
                    assertTrue(dash > 0);
                // assertTrue(r > 0);
                if (!inst.rng().c().isZeroable())
                    assertTrue(rash > 0);
                if (r > 0)
                    assertTrue(dr > 0);
                if (!inst.dom().c().isZeroable() && !inst.rng().c().isZeroable())
                    assertTrue(drash > 0);
                //assertTrue(d <= dr);
                assertTrue(r <= dr);
                //  assertTrue(dash <= drash);
                // assertTrue(rash <= drash);
                //assertTrue(r * d <= dr || r + d <= dr);
            } else {
                hasNotDomRng.incrementAndGet();
            }
        });
        assertEquals(this.space.insts().size(), hasDomRng.get() + hasNotDomRng.get());
    }
}
