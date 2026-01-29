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
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.mTest;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class InstSetTest extends mTest {

    protected Space space;
    protected final Supplier<Space> spaceSupplier;

    public InstSetTest(final Supplier<Space> instSetSupplier) {
        this.spaceSupplier = instSetSupplier;
    }

    @BeforeEach
    protected void setup() {
        this.space = this.spaceSupplier.get();
        if (null != this.space) {
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
}
