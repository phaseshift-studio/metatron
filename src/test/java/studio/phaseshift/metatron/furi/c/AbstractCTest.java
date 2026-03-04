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

package studio.phaseshift.metatron.furi.c;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.C;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
                assertTrue("if there is an additive inverse, then its distributive", this.distributive[i]);
        }
        LOG.error("a:  %s, b:  %s, c:  %s", a, b, c);
        LOG.error("aX: %s, bX: %s, cX: %s", aX, bX, cX);
        LOG.error("a0: %s, b0: %s, c0: %s", a0, b0, c0);
    }

    public void checkMultGroup(D aa, D bb, D cc, boolean multiplicativeInverse) {
        assertEquals("1 * 1         = 1", cc.one(), cc.one().mult(cc.one()));
        assertEquals("1 * a         = a", aa, cc.one().mult(aa));
        assertEquals("a * 1         = a", aa, aa.mult(cc.one()));
        assertEquals("(1*a) * (a*1) = a^2", aa.mult(aa), (cc.one().mult(aa)).mult((aa.mult(cc.one()))));
        if (multiplicativeInverse) {
            assertEquals("a * (1/a)     = 1", cc.one(), aa.mult(aa.inv()));
            assertEquals("(1/a) * a     = 1", cc.one(), aa.inv().mult(aa));
            assertEquals("1 / a         = (1/a)", cc.one().div(aa), aa.inv());
            assertEquals("a / 1         = a", aa.div(cc.one()), aa);
        }
    }

    public void checkPlusMonoid(D aa, D bb, D cc, boolean additiveInverse) {
        assertEquals("0 + 0         = 0", cc.zero(), cc.zero().plus(cc.zero()));
        assertEquals("0 + a         = a", aa, cc.zero().plus(aa));
        assertEquals("a + 0         = a", aa, aa.plus(cc.zero()));
        assertEquals("(0+a) + (a+0) = 2a", aa.plus(aa), (cc.zero().plus(aa)).plus((aa.plus(cc.zero()))));
        assertEquals("a + a         = 2a", aa.plus(aa), aa.plus(aa));
        assertEquals("0 + -a        = -a", aa.neg(), cc.zero().plus(aa.neg()));
        assertEquals("-a + 0        = -a", aa.neg().plus(cc.zero()), aa.neg());
        if (additiveInverse) {
            assertEquals("a + -a        = 0", cc.zero(), aa.plus(aa.neg()));
            assertEquals("-a + a        = 0", cc.zero(), aa.neg().plus(aa));
        }
    }

    public void checkPlusMultRing(D aa, D bb, D cc, boolean additiveInverse, boolean distributive) {
        assertEquals("a + a         = 2a", aa.plus(aa), aa.plus(aa));
        assertEquals("0 + -a        = -a", aa.neg(), cc.zero().plus(aa.neg()));
        assertEquals("-a + 0        = -a", aa.neg().plus(cc.zero()), aa.neg());
        assertEquals("a * a         = a^2", aa.mult(aa), aa.mult(aa));
        assertEquals("a * -a        = -a^2", aa.neg().mult(aa), aa.neg().mult(aa));
        assertEquals("-a * a        = -a^2", aa.neg().mult(aa), aa.neg().mult(aa));
        assertEquals("0 * a         = 0", cc.zero(), cc.zero().mult(aa));
        assertEquals("a * 0         = 0", cc.zero(), aa.mult(cc.zero()));
        assertEquals("a * a         = a^2", aa.mult(aa), aa.mult(aa));
        assertEquals("a * -a        = -a^2", aa.neg().mult(aa), aa.neg().mult(aa));
        assertEquals("-a * a        = -a^2", aa.neg().mult(aa), aa.neg().mult(aa));
        assertEquals("0 * a         = 0", cc.zero(), cc.zero().mult(aa));
        assertEquals("a * 0         = 0", cc.zero(), aa.mult(cc.zero()));
        assertEquals("a * -1        = -a", aa.neg(), aa.mult(cc.one().neg()));
        assertEquals("-1 * a        = -a", aa.neg(), cc.one().neg().mult(aa));
        assertEquals("-(a+b)        = -a - b", aa.plus(bb).neg(), aa.neg().minus(bb));
        if (distributive || additiveInverse) {
            assertEquals("(a+b)*(a+b)   = a^2 + 2ab + b^2", aa.plus(bb).mult(aa.plus(bb)), aa.mult(aa).plus(aa.mult(bb).plus(aa.mult(bb))).plus(bb.mult(bb)));
            assertEquals("a * (b+c)     = ab + ac", aa.mult(bb.plus(cc)), (aa.mult(bb)).plus(aa.mult(cc)));
            assertEquals("(b+c) * a     = ab + ac", bb.plus(cc).mult(aa), (bb.mult(aa)).plus(cc.mult(aa)));
        }
        if (additiveInverse) {
            assertEquals("(a+b)*(a-b)   = a^2 - b^2", aa.plus(bb).mult(aa.minus(bb)), (aa.mult(aa)).minus(bb.mult(bb)));
            assertEquals("(a-b)*(a+b)   = a^2 - b^2", aa.minus(bb).mult(aa.plus(bb)), (aa.mult(aa)).minus(bb.mult(bb)));
            assertEquals("(a+b)*(a+b)   = a^2 + 2ab + b^2", aa.plus(bb).mult(aa.plus(bb)), aa.mult(aa).plus(aa.mult(bb).plus(aa.mult(bb))).plus(bb.mult(bb)));
            assertEquals("a * (b+c)     = ab + ac", aa.mult(bb.plus(cc)), (aa.mult(bb)).plus(aa.mult(cc)));
            assertEquals("(b+c) * a     = ab + ac", bb.plus(cc).mult(aa), (bb.mult(aa)).plus(cc.mult(aa)));
        }
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testMultGroupPositive() {
        checkMultGroup(a0, b0, c0, multiplicativeInverses[0]);
    }

    @Test
    public void testPlusMonoidPositive() {
        checkPlusMonoid(a0, b0, c0, additiveInverses[0]);
    }

    @Test
    public void testPlusMultRingPositive() {
        checkPlusMultRing(a0, b0, c0, additiveInverses[0], distributive[0]);
    }

    /// /////////////////////////////////////////////////////////

    @Test
    public void testMultGroupExact() {
        checkMultGroup(aX, bX, cX, multiplicativeInverses[1]);
    }

    @Test
    public void testPlusMonoidExact() {
        checkPlusMonoid(aX, bX, cX, additiveInverses[1]);
    }

    @Test
    public void testPlusMultRingExact() {
        checkPlusMultRing(aX, bX, cX, additiveInverses[1], distributive[1]);
    }

    /// /////////////////////////////////////////////////////////

    @Test
    public void testMultGroup() {
        checkMultGroup(a, b, c, multiplicativeInverses[2]);
    }

    @Test
    public void testPlusMonoid() {
        checkPlusMonoid(a, b, c, additiveInverses[2]);
    }

    @Test
    public void testPlusMultRing() {
        checkPlusMultRing(a, b, c, additiveInverses[2], distributive[2]);
    }
}


