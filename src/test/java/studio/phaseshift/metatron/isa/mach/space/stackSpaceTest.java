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

package studio.phaseshift.metatron.isa.mach.space;

import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.space.stackSpace;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class stackSpaceTest extends AbstractSpaceTest {

    public stackSpaceTest() {
        super(f("t"), () -> new stackSpace(f("+/#")));
    }
    
    @Override
    public void testMonoUpdate() {
           super.testMonoUpdate();
           this.space.close(); // this is necessary due to TestScope not closing on test completion (need to move to JUnit listener model).
    }

    @Override
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        super.testMonoReadWrite(writeExpression, readExpression, expectedExpression);
        this.space.close(); // this is necessary due to TestScope not closing on test completion (need to move to JUnit listener model).
    }
}
