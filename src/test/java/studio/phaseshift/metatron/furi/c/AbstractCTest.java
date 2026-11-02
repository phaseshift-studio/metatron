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

package studio.phaseshift.metatron.furi.c;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.C;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractCTest<T extends Comparable<T>, D extends C<T, D>> extends AbstractMetatronTest {
    protected D a;
    protected D b;
    protected D c;
    protected D aX;
    protected D bX;
    protected D cX;
    protected D a0;
    protected D b0;
    protected D c0;
    protected boolean[] multiplicativeInverses;
    protected boolean[] additiveInverses;
    protected boolean[] distributive;

    // positive/exact/complete
    public AbstractCTest(final D a, final D b, final D c, boolean[] multiplicativeInverses, boolean[] additiveInverses, boolean[] distributive) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.aX = a.most();
        this.bX = b.most();
        this.cX = c.most();
        this.a0 = a.most().gt(c.zero()) ? c.clone(c.zero().min(), a.max()) : c.clone(c.zero().min(), a.most().neg().max());
        this.b0 = b.most().gt(c.zero()) ? c.clone(c.zero().min(), b.max()) : c.clone(c.zero().min(), b.most().neg().max());
        this.c0 = c.most().gt(c.zero()) ? c.clone(c.zero().min(), c.max()) : c.clone(c.zero().min(), c.most().neg().max());
        this.multiplicativeInverses = multiplicativeInverses;
        this.additiveInverses = additiveInverses;
        this.distributive = distributive;
        for (int i = 0; i < 3; i++) {
            if (this.additiveInverses[i])
                assertTrue(this.distributive[i], "if there is an additive inverse, then its distributive");
        }
        LOG.debug("[positive] a0: %s, b0: %s, c0: %s", a0, b0, c0);
        LOG.debug("[exact]    aX: %s, bX: %s, cX: %s", aX, bX, cX);
        LOG.debug("[complete] a:  %s, b:  %s, c:  %s", a, b, c);
    }

    public void checkMultGroup(D aa, D cc) {
        assertEquals(cc.one(), cc.one().mult(cc.one()), "1 * 1         = 1");
        assertEquals(aa, cc.one().mult(aa), "1 * a         = a");
        assertEquals(aa, aa.mult(cc.one()), "a * 1         = a");
        assertEquals(aa.mult(aa), (cc.one().mult(aa)).mult((aa.mult(cc.one()))), "(1*a) * (a*1) = a^2");
        assertEquals(cc.one(), aa.mult(aa.inv()), "a * (1/a)     = 1");
        assertEquals(cc.one(), aa.inv().mult(aa), "(1/a) * a     = 1");
        assertEquals(cc.one().div(aa), aa.inv(), "1 / a         = (1/a)");
        assertEquals(aa.div(cc.one()), aa, "a / 1         = a");
    }

    public void checkMultMonoid(D aa, D cc) {
        assertEquals(cc.one(), cc.one().mult(cc.one()), "1 * 1         = 1");
        assertEquals(aa, cc.one().mult(aa), "1 * a         = a");
        assertEquals(aa, aa.mult(cc.one()), "a * 1         = a");
        assertEquals(aa.mult(aa), (cc.one().mult(aa)).mult((aa.mult(cc.one()))), "(1*a) * (a*1) = a^2");
    }

    public void checkPlusMonoid(D aa, D cc) {
        assertEquals(cc.zero(), cc.zero().plus(cc.zero()), "0 + 0         = 0");
        assertEquals(aa, cc.zero().plus(aa), "0 + a         = a");
        assertEquals(aa, aa.plus(cc.zero()), "a + 0         = a");
        assertEquals(aa.plus(aa), (cc.zero().plus(aa)).plus((aa.plus(cc.zero()))), "(0+a) + (a+0) = 2a");
        assertEquals(aa.plus(aa), aa.plus(aa), "a + a         = 2a");
    }

    public void checkPlusGroup(D aa, D cc) {
        assertEquals(cc.zero(), cc.zero().plus(cc.zero()), "0 + 0         = 0");
        assertEquals(aa, cc.zero().plus(aa), "0 + a         = a");
        assertEquals(aa, aa.plus(cc.zero()), "a + 0         = a");
        assertEquals(aa.plus(aa), (cc.zero().plus(aa)).plus((aa.plus(cc.zero()))), "(0+a) + (a+0) = 2a");
        assertEquals(aa.plus(aa), aa.plus(aa), "a + a         = 2a");
        assertEquals(aa.neg(), cc.zero().plus(aa.neg()), "0 + -a        = -a");
        assertEquals(aa.neg().plus(cc.zero()), aa.neg(), "-a + 0        = -a");
        assertEquals(cc.zero(), aa.plus(aa.neg()), "a + -a        = 0");
        assertEquals(cc.zero(), aa.neg().plus(aa), "-a + a        = 0");
    }

    public void checkPlusMultRing(D aa, D bb, D cc, boolean additiveInverse, boolean distributive) {
        assertEquals(aa.plus(aa), aa.plus(aa), "a + a         = 2a");
        assertEquals(aa.neg(), cc.zero().plus(aa.neg()), "0 + -a        = -a");
        assertEquals(aa.neg().plus(cc.zero()), aa.neg(), "-a + 0        = -a");
        assertEquals(aa.mult(aa), aa.mult(aa), "a * a         = a^2");
        assertEquals(aa.neg().mult(aa), aa.neg().mult(aa), "a * -a        = -a^2");
        assertEquals(aa.neg().mult(aa), aa.neg().mult(aa), "-a * a        = -a^2");
        assertEquals(cc.zero(), cc.zero().mult(aa), "0 * a         = 0");
        assertEquals(cc.zero(), aa.mult(cc.zero()), "a * 0         = 0");
        assertEquals(aa.mult(aa), aa.mult(aa), "a * a         = a^2");
        assertEquals(aa.neg().mult(aa), aa.neg().mult(aa), "a * -a        = -a^2");
        assertEquals(aa.neg().mult(aa), aa.neg().mult(aa), "-a * a        = -a^2");
        assertEquals(cc.zero(), cc.zero().mult(aa), "0 * a         = 0");
        assertEquals(cc.zero(), aa.mult(cc.zero()), "a * 0         = 0");
        assertEquals(aa.neg(), aa.mult(cc.one().neg()), "a * -1        = -a");
        assertEquals(aa.neg(), cc.one().neg().mult(aa), "-1 * a        = -a");
        assertEquals(aa.plus(bb).neg(), aa.neg().minus(bb), "-(a+b)        = -a - b");
        if (distributive || additiveInverse) {
            assertEquals(aa.plus(bb).mult(aa.plus(bb)), aa.mult(aa).plus(aa.mult(bb).plus(aa.mult(bb))).plus(bb.mult(bb)), "(a+b)*(a+b)   = a^2 + 2ab + b^2");
            assertEquals(aa.mult(bb.plus(cc)), (aa.mult(bb)).plus(aa.mult(cc)), "a * (b+c)     = ab + ac");
            assertEquals(bb.plus(cc).mult(aa), (bb.mult(aa)).plus(cc.mult(aa)), "(b+c) * a     = ab + ac");
        }
        if (additiveInverse) {
            assertEquals(aa.plus(bb).mult(aa.minus(bb)), (aa.mult(aa)).minus(bb.mult(bb)), "(a+b)*(a-b)   = a^2 - b^2");
            assertEquals(aa.minus(bb).mult(aa.plus(bb)), (aa.mult(aa)).minus(bb.mult(bb)), "(a-b)*(a+b)   = a^2 - b^2");
            assertEquals(aa.plus(bb).mult(aa.plus(bb)), aa.mult(aa).plus(aa.mult(bb).plus(aa.mult(bb))).plus(bb.mult(bb)), "(a+b)*(a+b)   = a^2 + 2ab + b^2");
            assertEquals(aa.mult(bb.plus(cc)), (aa.mult(bb)).plus(aa.mult(cc)), "a * (b+c)     = ab + ac");
            assertEquals(bb.plus(cc).mult(aa), (bb.mult(aa)).plus(cc.mult(aa)), "(b+c) * a     = ab + ac");
        }
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testPositiveMultStructure() {
        if (multiplicativeInverses[0])
            checkMultGroup(a0, c0);
        else 
            LOG.warn("skipping testing [MULTIPLICATIVE GROUP] for [positive] %s", cInt.class.getSimpleName());
        checkMultMonoid(a0, c0);
    }

    @Test
    public void testPositivePlusStructure() {
        if (additiveInverses[0])
            checkPlusGroup(a0, c0);
        else 
            LOG.warn("skipping testing [ADDITIVE GROUP] for [positive] %s", cInt.class.getSimpleName());
        checkPlusMonoid(a0, c0);
    }

    @Test
    public void testPositivePlusMultStructure() {
        checkPlusMultRing(a0, b0, c0, additiveInverses[0], distributive[0]);
    }

    /// /////////////////////////////////////////////////////////

    @Test
    public void testExactMultStructure() {
        if (multiplicativeInverses[1])
            checkMultGroup(aX, cX);
        else
            LOG.warn("skipping testing [MULTIPLICATIVE GROUP] for [exact] %s", cInt.class.getSimpleName());
        checkMultMonoid(aX, cX);
    }

    @Test
    public void testExactPlusStructure() {
        if (additiveInverses[1])
            checkPlusGroup(aX, cX);
        else
            LOG.warn("skipping testing [ADDITIVE GROUP] for [exact] %s", cInt.class.getSimpleName());
        checkPlusMonoid(aX, cX);
    }

    @Test
    public void testExactPlusMultStructure() {
        checkPlusMultRing(aX, cX, cX, additiveInverses[1], distributive[1]);
    }

    /// /////////////////////////////////////////////////////////

    @Test
    public void testMultStructure() {
        if (multiplicativeInverses[2])
            checkMultGroup(a, c);
        else
            LOG.warn("skipping testing [MULTIPLICATIVE GROUP] for [complete] %s", cInt.class.getSimpleName());
        checkMultMonoid(a, c);
    }

    @Test
    public void testPlusStructure() {
        if (additiveInverses[2])
            checkPlusGroup(a, c);
        else
            LOG.warn("skipping testing [ADDITIVE GROUP] for [complete] %s", cInt.class.getSimpleName());
        checkPlusMonoid(a, c);
    }

    @Test
    public void testPlusMultStructure() {
        checkPlusMultRing(a, b, c, additiveInverses[2], distributive[2]);
    }
}


