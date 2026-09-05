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

package studio.phaseshift.metatron.algebra;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
            assertTrue(this.forms.contains(PLUS_GROUP), this.obj.type() + " is not a plus group");
            final PlusGroup.O group = (PlusGroup.O) this.obj;
            assertEquals(group.zero(), group.zero().plus(group.zero()), "0 + 0         = 0");
            assertEquals(group.plus(group), group.plus(group.zero()).plus(group.zero().plus(group)), "(0+a) + (a+0) = 2a");
            assertEquals(group, group.zero().plus(group), "0 + a         = a");
            assertEquals(group, group.plus(group.zero()), "a + 0         = a");
            assertEquals(group.zero(), group.plus(group.neg()), "a + (-a)      = 0");
            assertEquals(group.zero(), group.neg().plus(group), "(-a) + a      = 0");
            assertEquals(group.zero(), group.plus(group.neg()), "a + (-a)      = 0");
            assertEquals(group.zero(), group.neg().plus(group), "(-a) + a      = 0");
            assertEquals(group.zero(), group.minus(group), "a - a         = 0");
            assertEquals(group.neg(), group.zero().minus(group), "0 - a         = (-a)");
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", group);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhsObj, rhsObj, lhs + " != " + rhs);

        } else {
            LOG.warn("skipping testing for non plus group: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.forms.contains(PLUS_GROUP), this.obj.type() + " is not a plus group");
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
            assertTrue(this.forms.contains(MULT_GROUP), this.obj.type() + " is not a mult group");
            final MultGroup.O group = (MultGroup.O) this.obj;
            assertEquals(group.one(), group.one().mult(group.one()), "1 * 1         = 1");
            assertEquals(group, group.one().mult(group), "1 * a         = a");
            assertEquals(group.mult(group), group.one().mult(group).mult(group.mult(group.one())), "(1*a) * (a*1) = a^2");
            assertEquals(group, group.mult(group.one()), "a * 1         = a");
            assertEquals(group.one(), group.mult(group.inv()), "a * (1/a)     = 1");
            assertEquals(group.one(), group.inv().mult(group), "(1/a) * a     = 1");
            assertEquals(group.one().div(group), group.inv(), "1 / a         = (1/a)");
            assertEquals(group.div(group.one()), group, "a / 1         = a");
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", group);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhsObj, rhsObj, lhs + " != " + rhs);
        } else {
            LOG.warn("skipping testing for non mult group: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.forms.contains(MULT_GROUP), this.obj.type() + " is not a mult group");
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
            assertTrue(this.forms.contains(PLUS_MONOID), this.obj.type() + " is not a plus monoid");
            final PlusMonoid.O monoid = (PlusMonoid.O) this.obj;
            assertEquals(monoid.zero(), monoid.zero().plus(monoid.zero()), "0 + 0 = 0");
            assertEquals(monoid, monoid.zero().plus(monoid), "0 + a = a");
            assertEquals(monoid, monoid.plus(monoid.zero()), "a + 0 = a");
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", monoid);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhsObj, rhsObj, lhs + " != " + rhs);
        } else {
            LOG.warn("skipping testing for non plus monoid: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.forms.contains(PLUS_MONOID), this.obj.type() + " is not a plus monoid");
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
            assertTrue(this.forms.contains(MULT_MONOID), this.obj.type() + " is not a mult monoid");
            final MultMonoid.O monoid = (MultMonoid.O) this.obj;
            assertEquals(monoid.one(), monoid.one().mult(monoid.one()), "1 * 1 = 1");
            assertEquals(monoid, monoid.one().mult(monoid), "1 * a = a");
            assertEquals(monoid, monoid.mult(monoid.one()), "a * 1 = a");
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", monoid);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhsObj, rhsObj, lhs + " != " + rhs);
        } else {
            LOG.warn("skipping testing for non mult monoid: %s %s", this.obj.type(), this.forms);
            assumeTrue(this.forms.contains(MULT_MONOID), this.obj.type() + " is not a mult monoid");
        }
    }

    ///
    /// parametric identities — the identity elements are instructions whose
    /// identity properties are realized at evaluation time, uniformly over
    /// every element of the ring.  The defining parametric law is element
    /// independence: one()/zero() do not depend on the element they are
    /// asked of (reynolds' parametricity / theorems-for-free style): a single
    /// polymorphic instruction covers what would otherwise require infinite
    /// enumeration (e.g. the universal diagonal relation for rel).
    ///
    @ParameterizedTest
    @CsvSource(value = {
            "*a.one().zero()                                                         % *a.zero()",
            "*a.zero().one()                                                         % *a.one()",
            "*a.zero().zero()                                                        % *a.zero()",
            "*a.mult(*a).one()                                                       % *a.one()",
            "*a.mult(*a).zero()                                                      % *a.zero()",
            "*a.one().mult(*a.zero())                                                % *a.zero()",
            "*a.mult(*a.zero())                                                      % *a.zero()",
            // zero-on-the-left annihilator (0*a = 0) is pinned per-conformer where it
            // renders canonically (Int/Lst green); rel splits the top-level zero render
            // (noobj vs noobj=>noobj) — see RelTest.testRelZeroSink + the canonical-zero note.

    }, delimiter = '%')
    public void testParametricIdentities(final String lhs, final String rhs) {
        final boolean plusMonoid = this.obj instanceof PlusMonoid.O && this.forms.contains(PLUS_MONOID);
        final boolean multMonoid = this.obj instanceof MultMonoid.O && this.forms.contains(MULT_MONOID);
        if (plusMonoid && multMonoid) {
            LOG.warn("testing parametric identities for %s %s", this.obj.type(), this.forms);
            final PlusMonoid.O zeroable = (PlusMonoid.O) this.obj;
            final MultMonoid.O oneable = (MultMonoid.O) this.obj;
            assertEquals(zeroable.zero(), zeroable.zero().zero(), "0 is element independent (0 of 0 = 0)");
            assertEquals(oneable.one(), oneable.mult(oneable).one(), "1 invariant under mult (1 of a*a = 1)");
            // cross-interface element independence (1 of 0 = 1, 0 of 1 = 0) is pinned by the mtron rows below
            assertEquals(zeroable.zero(), zeroable.plus(zeroable).zero(), "0 invariant under plus (0 of a+a = 0)");
            // mixed-call annihilator laws (0 * a = 0, 1 * 0 = 0) are pinned by the mtron rows below
            /// /////////////////////////////////////////////////////////////////////////
            Router.global().write("a", this.obj);
            final Obj lhsObj = ObjmtronSerializer.parse(lhs).apply();
            final Obj rhsObj = ObjmtronSerializer.parse(rhs).apply();
            assertEquals(lhsObj, rhsObj, lhs + " != " + rhs);
        } else {
            LOG.warn("skipping parametric identities (needs plus+mult monoid): %s %s", this.obj.type(), this.forms);
            assumeTrue(plusMonoid && multMonoid, this.obj.type() + " is not a rig");
        }
    }

}
