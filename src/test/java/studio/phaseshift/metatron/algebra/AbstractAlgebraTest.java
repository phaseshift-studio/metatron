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

package studio.phaseshift.metatron.algebra;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static studio.phaseshift.metatron.algebra.Form.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractAlgebraTest<O extends Obj> extends AbstractObjTest {

    protected O obj;
    protected Set<Form> forms;

    public AbstractAlgebraTest(final O obj, final Set<Form> forms) {
        super();
        this.obj = obj;
        this.forms = forms;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*a.zero() + *a.zero()                                                      % *a.zero()",
            "*a.zero().plus(*a).plus(*a.plus(*a.zero()))                                % *a.plus(*a)",
            "*a.zero().plus(*a)                                                         % *a",
            "*a.plus(*a.zero())                                                         % *a",
            "*a.plus(*a.neg())                                                          % *a.zero()",
            "*a.neg().plus(*a)                                                          % *a.zero()",
            "*a.minus(*a)                                                               % *a.zero()",
            "*a.zero().minus(*a)                                                        % *a.neg()",
    }, delimiter = '%')
    public void testPlusGroup(final String lhs, final String rhs) {
        if (this.obj instanceof PlusGroup.O) {
            LOG.warn("testing plus group for %s %s", this.obj.type(), this.forms);
            assertTrue(this.obj.type() + " is not a plus group", this.forms.contains(PLUS_GROUP));
            final PlusGroup.O group = (PlusGroup.O) this.obj;
            assertEquals("0 + 0         = 0", group.zero(), group.zero().plus(group.zero()));
            assertEquals("(0+a) + (a+0) = 2a", group.plus(group), group.plus(group.zero()).plus(group.zero().plus(group)));
            assertEquals("0 + a         = a", group, group.zero().plus(group));
            assertEquals("a + 0         = a", group, group.plus(group.zero()));
            assertEquals("a + (-a)      = 0", group.zero(), group.plus(group.neg()));
            assertEquals("(-a) + a      = 0", group.zero(), group.neg().plus(group));
            assertEquals("a + (-a)      = 0", group.zero(), group.plus(group.neg()));
            assertEquals("(-a) + a      = 0", group.zero(), group.neg().plus(group));
            assertEquals("a - a         = 0", group.zero(), group.minus(group));
            assertEquals("0 - a         = (-a)", group.neg(), group.zero().minus(group));
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", group);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhs + " != " + rhs, lhsObj, rhsObj);

        } else {
            LOG.warn("skipping testing for non plus group: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.obj.type() + " is not a plus group", this.forms.contains(PLUS_GROUP));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*a.one().mult(*a.one())                                                    % *a.one()",
            "*a.one().mult(*a).mult(*a.mult(*a.one()))                                  % *a.mult(*a)",
            "*a.one().mult(*a)                                                          % *a",
            "*a.mult(*a.one())                                                          % *a",
            "*a.mult(*a.inv())                                                          % *a.one()",
            "*a.inv().mult(*a)                                                          % *a.one()",
            "*a.div(*a)                                                                 % *a.one()",
            "*a.one().div(*a)                                                           % *a.inv()",
    }, delimiter = '%')
    public void testMultGroup(final String lhs, final String rhs) {
        if (this.obj instanceof MultGroup.O) {
            LOG.warn("testing mult group for %s %s", this.obj.type(), this.forms);
            assertTrue(this.obj.type() + " is not a mult group", this.forms.contains(MULT_GROUP));
            final MultGroup.O group = (MultGroup.O) this.obj;
            assertEquals("1 * 1         = 1", group.one(), group.one().mult(group.one()));
            assertEquals("1 * a         = a", group, group.one().mult(group));
            assertEquals("(1*a) * (a*1) = a^2", group.mult(group), group.one().mult(group).mult(group.mult(group.one())));
            assertEquals("a * 1         = a", group, group.mult(group.one()));
            assertEquals("a * (1/a)     = 1", group.one(), group.mult(group.inv()));
            assertEquals("(1/a) * a     = 1", group.one(), group.inv().mult(group));
            assertEquals("1 / a         = (1/a)", group.one().div(group), group.inv());
            assertEquals("a / 1         = a", group.div(group.one()), group);
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", group);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhs + " != " + rhs, lhsObj, rhsObj);
        } else {
            LOG.warn("skipping testing for non mult group: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.obj.type() + " is not a mult group", this.forms.contains(MULT_GROUP));
        }
    }


    @ParameterizedTest
    @CsvSource(value = {
            "*a.zero().plus(*a.zero())                                                   % *a.zero()",
            "*a.zero().plus(*a)                                                          % *a",
            "*a.plus(*a.zero())                                                          % *a",
    }, delimiter = '%')
    public void testPlusMonoid(final String lhs, final String rhs) {
        if (this.obj instanceof PlusMonoid.O) {
            LOG.warn("testing plus monoid for %s", this.obj.type());
            assertTrue(this.obj.type() + " is not a plus monoid", this.forms.contains(PLUS_MONOID));
            final PlusMonoid.O monoid = (PlusMonoid.O) this.obj;
            assertEquals("0 + 0 = 0", monoid.zero(), monoid.zero().plus(monoid.zero()));
            assertEquals("0 + a = a", monoid, monoid.zero().plus(monoid));
            assertEquals("a + 0 = a", monoid, monoid.plus(monoid.zero()));
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", monoid);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhs + " != " + rhs, lhsObj, rhsObj);
        } else {
            LOG.warn("skipping testing for non plus monoid: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.obj.type() + " is not a plus monoid", this.forms.contains(PLUS_MONOID));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*a.one().mult(*a.one())                                                   % *a.one()",
            "*a.one().mult(*a)                                                         % *a",
            "*a.mult(*a.one())                                                         % *a",
    }, delimiter = '%')
    public void testMultMonoid(final String lhs, final String rhs) {
        if (this.obj instanceof MultMonoid.O) {
            LOG.warn("testing mult monoid for %s", this.obj.type());
            assertTrue(this.obj.type() + " is not a mult monoid", this.forms.contains(MULT_MONOID));
            final MultMonoid.O monoid = (MultMonoid.O) this.obj;
            assertEquals("1 * 1 = 1", monoid.one(), monoid.one().mult(monoid.one()));
            assertEquals("1 * a = a", monoid, monoid.one().mult(monoid));
            assertEquals("a * 1 = a", monoid, monoid.mult(monoid.one()));
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", monoid);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhs + " != " + rhs, lhsObj, rhsObj);
        } else {
            LOG.warn("skipping testing for non mult monoid: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.obj.type() + " is not a mult monoid", this.forms.contains(MULT_MONOID));
        }
    }

}
