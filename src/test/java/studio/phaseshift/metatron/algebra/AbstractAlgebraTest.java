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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.mTypes;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.lawOf;
import static studio.phaseshift.metatron.isa.m.mInstSet.AlgebraTool;
import static studio.phaseshift.metatron.isa.m.mInstSet.INV_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MULT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NEG_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/**
 * THE TYPE-AGNOSTIC ALGEBRA VALIDATOR — what a carrier's own declaration says about it, tested.
 *
 * <p>A subclass names the TYPE under test ({@link #carrier()}) and the ELEMENT its equations are written over
 * ({@link #sample()}). The inherited test derives the CLAIMS from the type itself; no Java marker interface is
 * consulted. A type models a set of theory instances ({@code object::T ?lawq}: {@code ring => {add, mul, zero, one},
 * …}); each instance names its theory (the instance rec's tid), each theory declares its LAW BATTERY
 * ({@code mTypes.declareAxioms()}), and their union is what the carrier must satisfy. The union goes to
 * {@link AlgebraTool#analyzeType(Type, List)}, which tests every claim against every operation the type binds and
 * answers in a report. That derivation is the point: a carrier DECLARES its algebra, so the test reads the
 * declaration instead of restating it, and a theory added to a carrier is validated without touching Java.
 *
 * <p>The CSV tests below are NOT the source of truth — they are human-readable INTERPRETATIONS of the equation
 * templates the tool generates, kept because a reader can see the laws written out. Each runs only when the carrier
 * actually declares the operation the row is about ({@link AlgebraTool#binds(Type, fURI)}); otherwise it skips,
 * naming the carrier.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractAlgebraTest extends AbstractObjTest {

    /**
     * THE TYPE UNDER TEST — the carrier whose declared algebra the inherited test validates. Any mtron type works: a
     * refinement reads the operations of the type its algebra declares.
     */
    protected abstract Type carrier();

    /**
     * THE PROBE — an element of the carrier the equations are written over (it becomes {@code *a}), registered with
     * the tool through {@code AlgebraTool.registerSample}. A subclass with no representative value returns
     * {@code noobj()}; the tool then falls back to the type's declared {@code zero()}/{@code one()}, and when the type
     * names neither, the report answers {@code uncheckable "no probe"} instead of guessing.
     */
    protected abstract Obj sample();

    /**
     * THE DERIVED CONTRACT — every instance the carrier declares contributes its theory's battery, and the union is
     * validated against the carrier. A carrier that declares NO instance passes vacuously, and SAYS SO: the skip names
     * the carrier, so "nothing was declared" is never silently green.
     */
    @Test
    public void declaredInstancesEntailTheirLaws() {
        final Type carrier = this.carrier();
        final Rec instances = instances(carrier);
        assumeTrue(!instances.jvm().isEmpty(),
                () -> "carrier " + carrier.namedType() + " declares no theory instance in ?lawq — nothing to validate");

        final List<Law> claims = new ArrayList<>();
        final List<fURI> unbattered = new ArrayList<>();
        for (final Map.Entry<Obj, Obj> instance : instances.jvm().entrySet()) {
            if (!instance.getValue().isRec())
                continue;
            final fURI theory = instance.getValue().asRec().tid();
            final fURI address = null == theory ? null : theory.basePath();
            final List<Law> battery = mTypes.axiomsOf(address);
            if (battery.isEmpty())
                unbattered.add(address);
            for (final Law law : battery)
                if (!claims.contains(law))
                    claims.add(law);
        }
        assertTrue(unbattered.isEmpty(),
                () -> "carrier " + carrier.namedType() + " declares instances whose theory declares no law battery: " + unbattered);
        assertFalse(claims.isEmpty(),
                () -> "carrier " + carrier.namedType() + " declares instances but no law battery was derived: " + instances);

        AlgebraTool.registerSample(carrier.tid(), this.sample());
        LOG.warn("validating %s: %d instance(s), claims %s", carrier.namedType(), instances.jvm().size(), claims);
        final AlgebraTool.Report report = AlgebraTool.analyzeType(carrier, claims);
        assertTrue(report.ok(),
                () -> "carrier " + carrier.namedType() + " FAILS its own declared algebra " + claims + " — report " + report.toRec());
    }

    /**
     * THE CARRIER'S DECLARED ALGEBRA — instance name => theory instance rec. {@code LawTable.typeLawsOf} is the
     * serving read (what the category renders); the {@code ?lawq} declaration it serves is the fallback when no law
     * table is mounted — the same read {@code AlgebraTool} makes.
     */
    protected static Rec instances(final Type type) {
        final Rec declared = LawTable.typeLawsOf(type);
        if (!declared.jvm().isEmpty())
            return declared;
        final Obj declaration = lawOf(type);
        return declaration.isRec() ? declaration.asRec() : rec0();
    }

    /** Does the carrier's declared algebra bind this operation? The guard a hand-written interpretation asks. */
    protected boolean binds(final fURI operation) {
        return AlgebraTool.binds(this.carrier(), operation);
    }

    /** Skip this interpretation unless the carrier declares every operation the row is about. */
    protected void requires(final fURI... operations) {
        for (final fURI operation : operations)
            assumeTrue(this.binds(operation),
                    () -> "carrier " + this.carrier().namedType() + " declares no " +
                            (null == operation ? null : operation.basePath()) + " operation");
    }

    /** Write the probe into {@code *a} and READ the row as the equation it is: the tool's template, by hand. */
    protected void interpret(final String lhs, final String rhs) {
        Machine.current().memory().write(f("a"), this.sample());
        final Obj left = ObjmtronSerializer.parse(lhs).apply();
        final Obj right = ObjmtronSerializer.parse(rhs).apply();
        assertTrue(AlgebraTool.equal(left, right),
                () -> lhs + " != " + rhs + " (got " + left + " and " + right + " for " + this.carrier().namedType() + ")");
    }

    /**
     * THE ADDITIVE-GROUP TEMPLATE, WRITTEN OUT — the equations the tool generates for a group's operation and its
     * inverse ({@code a + (-a) = 0}, …). Documentation; {@link #declaredInstancesEntailTheirLaws()} is the truth.
     */
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
        this.requires(PLUS_INST_TID, NEG_INST_TID);
        this.interpret(lhs, rhs);
    }

    /**
     * THE MULTIPLICATIVE-GROUP TEMPLATE, WRITTEN OUT — {@code a·a⁻¹ = 1}, {@code 1/a = a⁻¹}, … (the row that wants the
     * numeric tolerance: {@code 23.5·(1/23.5)} is not exactly 1 in binary floating point).
     */
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
        this.requires(MULT_INST_TID, INV_INST_TID);
        this.interpret(lhs, rhs);
    }

    /**
     * THE ADDITIVE-MONOID TEMPLATE, WRITTEN OUT — identity only ({@code 0 + a = a = a + 0}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*a.zero().plus(*a.zero())                                                   % *a.zero()",
            "*a.zero().plus(*a)                                                          % *a",
            "*a.plus(*a.zero())                                                          % *a",
    }, delimiter = '%')
    public void testPlusMonoid(final String lhs, final String rhs) {
        this.requires(PLUS_INST_TID);
        this.interpret(lhs, rhs);
    }

    /**
     * THE MULTIPLICATIVE-MONOID TEMPLATE, WRITTEN OUT — {@code 1·a = a = a·1}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*a.one().mult(*a.one())                                                   % *a.one()",
            "*a.one().mult(*a)                                                         % *a",
            "*a.mult(*a.one())                                                         % *a",
    }, delimiter = '%')
    public void testMultMonoid(final String lhs, final String rhs) {
        this.requires(MULT_INST_TID);
        this.interpret(lhs, rhs);
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
        this.requires(PLUS_INST_TID, MULT_INST_TID);
        this.interpret(lhs, rhs);
    }

}
