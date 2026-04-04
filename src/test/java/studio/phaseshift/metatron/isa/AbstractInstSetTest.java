/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractInstSetTest extends AbstractMetatronTest {

    protected InstSet space;
    protected final Supplier<InstSet> spaceSupplier;

    public AbstractInstSetTest(final Supplier<InstSet> instSetSupplier) {
        this.spaceSupplier = instSetSupplier;
    }

    @BeforeEach
    protected void setup() {
        this.space = this.spaceSupplier.get();
        if (null != this.space) {
            this.space.setup();
            if (this.space.vid() == null)
                LOG.warn("provided space has no vid and thus can not be shutdown automatically");
            Router.global().addSpace(this.space);
        }
    }

    @AfterEach
    protected void stop() {
        if (null != this.space) {
            if (null != this.space.vid()) {
                assertDoesNotThrow(this.space::close);
                Router.global().removeSpace(this.space.vid());
            }
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
                long d = Router.readFromSpace(inst.tid().dom(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long dash = Router.readFromSpace(inst.tid().dom(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long r = Router.readFromSpace(inst.tid().rng(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long rash = Router.readFromSpace(inst.tid().rng(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long dr = Router.readFromSpace(inst.tid().rng(null).dom(null)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
                long drash = Router.readFromSpace(inst.tid().rng(ALL).dom(ALL)).stream().filter(i -> Objects.equals(i.tid().basePath(), inst.tid().basePath())).count();
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
