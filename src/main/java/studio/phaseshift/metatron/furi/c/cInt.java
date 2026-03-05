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

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Objects;

public class cInt implements C<Long, cInt> {
    private final Long min;
    private final Long max;

    private cInt(final long min, final long max) {
        if (min > max)
            throw MTronException.of("c min is greater than c max: %s > %s", min, max);
        this.min = min;
        this.max = max;
    }

    private cInt(final Long min, final Long max) {
        if (null != min & null != max && min > max)
            throw MTronException.of("c min is greater than c max: %s > %s", min, max);
        this.min = min;
        this.max = max;
    }

    public static final cInt C_ZERO = cInt.of(0L);
    public static final cInt C_ONE = cInt.of(1L);
    public static final cInt C_SOME = cInt.of(1L, null);

    public static cInt ZERO() {
        return C_ZERO;
    }

    public static cInt ONE() {
        return C_ONE;
    }

    public static cInt ANTIONE() {
        return cInt.of(-1L, -1L);
    }

    public static cInt SOME() {
        return C_SOME;
    }

    public static cInt MAYBE() {
        return cInt.of(0L, 1L);
    }

    public static cInt MAYBESOME() {
        return cInt.of(0L, null);
    }

    public static cInt ANY() {
        return cInt.of((Long) null, null);
    }

    public static cInt ANTIMAYBESOME() {
        return cInt.of(null, 0L);
    }

    public static cInt ANTIMAYBE() {
        return cInt.of(null, -1L);
    }

    public static cInt ANTISOME() {
        return cInt.of(null, -1L);
    }

    public static cInt of(final Long min, final Long max) {
        return new cInt(min, max);
    }

    public static cInt of(final Long exact) {
        return new cInt(exact, exact);
    }

    public static cInt of(final Integer min, final Integer max) {
        return new cInt(null == min ? null : min.longValue(), null == max ? null : max.longValue());
    }

    public static cInt of(final Integer exact) {
        return cInt.of(null == exact ? null : exact.longValue());
    }

    public static cInt of(final String parse) {
        if (parse.isEmpty())
            return cInt.ONE();
        else if (parse.equals("*"))
            return cInt.of(0L, null);
        else if (parse.equals("**"))
            return cInt.of(null, (Long) null);
        else if (parse.equals("?"))
            return cInt.of(0L, 1L);
        else if (parse.equals("??"))
            return cInt.of(-1L, 1L);
        else if (parse.equals("+"))
            return cInt.of(1L, null);
        else if (parse.equals("-?"))
            return cInt.of(-1L, 0L);
        else if (parse.equals("-"))
            return cInt.of(null, -1L);
        else if (parse.equals("-*"))
            return cInt.of(null, 0L);
            //else if (parse.equals("-+"))
            //    return cInt.of(-1L, 1L);
        else if (!parse.contains(","))
            return cInt.of(Long.valueOf(parse));
        else {
            final String[] split = parse.split(",");
            if (parse.charAt(0) == ',')
                return (1 == parse.length()) ? cInt.of(null, (Long) null) : cInt.of(null, Long.valueOf(split[1]));
            if (split.length == 1) return cInt.of(Long.valueOf(split[0]), null);
            return cInt.of(Long.valueOf(split[0]), Long.valueOf(split[1]));
        }

    }

    static final Long LONG_ZERO = 0L;

    @Override
    public boolean isZero() {
        return LONG_ZERO.equals(this.max) && LONG_ZERO.equals(this.min);
    }

    @Override
    public boolean isMaybeSome() {
        return LONG_ZERO.equals(this.min) && null == this.max;
    }

    @Override
    public Long min() {
        return this.min;
    }

    @Override
    public Long max() {
        return this.max;
    }

    @Override
    public cInt plus(final cInt rhs) {
        final Long newMin = (null == this.min || null == rhs.min) ? null : (this.min + rhs.min);
        final Long newMax = (null == this.max || null == rhs.max) ? null : (this.max + rhs.max);
        return null == newMin && null == newMax ? new cInt(0L, 0L) : new cInt(newMin, newMax);
    }

    
    /*@Override
    public cInt minus(final cInt rhs) {
        final Long newMin = (null == this.min || null == rhs.min) ? null : (this.min - rhs.min);
        final Long newMax = (null == this.max || null == rhs.max) ? null : (this.max - rhs.max);
        return null == newMin && null == newMax ? new cInt(0L, 0L) : new cInt(newMin, newMax);
    }*/


    @Override
    public cInt neg() {
        final Long min = this.max == null ? null : -this.max;
        final Long max = this.min == null ? null : -this.min;
        return new cInt(min, max);
    }
    
    public static cInt random(final Long cap) {
        final long first = -1L * RANDOM.nextLong(cap);
        final long second = RANDOM.nextLong(cap);
        return new cInt(first, second);
    }

    @Override
    public cInt div(final cInt rhs) {
        final Long minRHS = rhs.min == null ? null : rhs.min;
        final Long maxRHS = rhs.max == null ? null : rhs.max;
        final Long newMin = (this.min == null && minRHS == null) ?
                Long.valueOf(1L) : ((this.min == null || minRHS == null) ?
                null : (this.min == 0 && minRHS == 0 ? 0L : this.min / minRHS));
        final Long newMax = (this.max == null && maxRHS == null) ?
                Long.valueOf(1L) : ((this.max == null || maxRHS == null) ?
                null : (this.max == 0 && maxRHS == 0 ? 0L : this.max / maxRHS));
        return new cInt(newMin, newMax);
    }

    @Override
    public cInt mult(final cInt rhs) {
        if (this.isOne()) return rhs;
        else if (rhs.isOne()) return this;
        final Long newMin = (null == this.min || null == rhs.min) ? null : (this.min * rhs.min);
        final Long newMax = (null == this.max || null == rhs.max) ? null : (this.max * rhs.max);
        final boolean flip = null != newMin && null != newMax && newMin > newMax;
        return new cInt(flip ? newMax : newMin, flip ? newMin : newMax);
        //     return new cInt(newMin, newMax);
    }

    @Override
    public cInt clone(final Long min, final Long max) {
        return new cInt(min, max);
    }

    @Override
    public boolean isNeg() {
        return (this.min == null || this.min < 0L) && (this.max != null && this.max < 0L);
    }

    private Long[] minMax() {
        return new Long[]{this.min == null ? Long.MIN_VALUE : this.min, this.max == null ? Long.MAX_VALUE : this.max};
    }

    @Override
    public boolean within(final cInt rhs) {
        final Long[] thisMinMax = this.minMax();
        final Long[] rhsMinMax = rhs.minMax();
        return thisMinMax[0].compareTo(rhsMinMax[0]) >= 0 && thisMinMax[1].compareTo(rhsMinMax[1]) <= 0;
    }

    @Override
    public boolean contains(final cInt rhs) {
        final Long[] thisMinMax = this.minMax();
        final Long[] rhsMinMax = rhs.minMax();
        return thisMinMax[0].compareTo(rhsMinMax[0]) <= 0 && thisMinMax[1].compareTo(rhsMinMax[1]) >= 0;
    }

    @Override
    public cInt abs() {
        return this.gte(this.zero()) ? this :
                new cInt(this.min < 0 ? -1L * this.min : this.min,
                        this.max < 0 ? -1L * this.max : this.max);
    }

    @Override
    public cInt any() {
        return cInt.of(null, (Long) null);
    }

    @Override
    public cInt maybeSome() {
        return cInt.of(0L, null);
    }

    @Override
    public cInt some() {
        return cInt.of(1L, null);
    }

    @Override
    public cInt antiMaybeSome() {
        return cInt.of(null, 0L);
    }

    @Override
    public cInt antiSome() {
        return cInt.of(null, -1L);
    }

    @Override
    public cInt anyMaybe() {
        return cInt.of(-1L, 1L);
    }

    @Override
    public cInt antiMaybe() {
        return cInt.of(-1L, 0L);
    }

    @Override
    public cInt zero() {
        return cInt.of(0L, 0L);
    }

    @Override
    public cInt maybe() {
        return cInt.of(0L, 1L);
    }

    @Override
    public cInt one() {
        return cInt.of(1L, 1L);
    }

    @Override
    public String toString() {
        if (this.isAny()) // "anyMaybeSome"
            return "**";
        else if (this.isMaybe())
            return "?";
        else if (this.isSome())
            return "+";
        else if (this.isMaybeSome())
            return "*";
        else if (this.isExact())
            return "" + this.min;
        else if (this.isAnyMaybe())
            return "??";
        else if (this.isAntiMaybe())
            return "-?";
        else if (this.isAntiSome())
            return "-";
        else if (this.isAntiMaybeSome())
            return "-*";
        else
            return (null == this.min ? "" : this.min) + "," + (null == this.max ? "" : this.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.min, this.max);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof cInt && Objects.equals(this.min, ((cInt) other).min) && Objects.equals(this.max, ((cInt) other).max);
    }

    @Override
    public int compareTo(final cInt rhs) {
        Long minA = this.min() == null ? 0 : this.min();
        Long maxA = this.max() == null ? Long.MAX_VALUE : this.max();
        Long minB = rhs.min() == null ? 0 : rhs.min();
        Long maxB = rhs.max() == null ? Long.MAX_VALUE : rhs.max();
        return minA.compareTo(minB) + maxA.compareTo(maxB);
    }
}
