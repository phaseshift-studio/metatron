/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

import java.util.Objects;

public class cInt implements C<Long, cInt> {
    private final Long min;
    private final Long max;

    private cInt(final Long min, final Long max) {
        this.min = min;
        this.max = max;
    }

    public static cInt ZERO() {
        return cInt.of(0L);
    }

    public static cInt ONE() {
        return cInt.of(1L);
    }

    public static cInt SOME() {
        return cInt.of(1L, null);
    }

    public static cInt MAYBE() {
        return cInt.of(0L, 1L);
    }

    public static cInt MAYBESOME() {
        return cInt.of(0L, null);
    }

    public static cInt of(final Long min, final Long max) {
        return new cInt(min, max);
    }

    public static cInt of(final Long exact) {
        return new cInt(exact, exact);
    }

    public static cInt of(final String parse) {
        if (parse.isEmpty())
            return cInt.of(1L);
        else if (parse.equals("**"))
            return cInt.of(null, null);
        else if (parse.equals("*"))
            return cInt.of(0L, null);
        else if (parse.equals("?"))
            return cInt.of(0L, 1L);
        else if (parse.equals("??"))
            return cInt.of(-1L, 1L);
        else if (parse.equals("+"))
            return cInt.of(1L, null);
            // else if (parse.equals(","))
            //    return cInt.of(null, null);
        else if (!parse.contains(","))
            return cInt.of(Long.valueOf(parse));
        else {
            final String[] split = parse.split(",");
            if (parse.charAt(0) == ',')
                return (1 == parse.length()) ? cInt.of(null, null) : cInt.of(null, Long.valueOf(split[1]));
            if (split.length == 1) return cInt.of(Long.valueOf(split[0]), null);
            return cInt.of(Long.valueOf(split[0]), Long.valueOf(split[1]));
        }

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

    @Override
    public cInt neg() {
        final Long min = this.max == null ? null : -this.max;
        final Long max = this.min == null ? null : -this.min;
        return new cInt(min, max);
    }

    @Override
    public cInt mult(final cInt rhs) {
        final Long newMin = (null == this.min || null == rhs.min) ? null : (this.min * rhs.min);
        final Long newMax = (null == this.max || null == rhs.max) ? null : (this.max * rhs.max);
        return new cInt(newMin, newMax);
    }

    @Override
    public cInt clone(final Long min, final Long max) {
        return new cInt(min, max);
    }

    @Override
    public boolean isNeg() {
        return this.min != null && this.min < 0L && this.max != null && this.max < 0L;
    }

    @Override
    public boolean within(final cInt rhs) {
        Long minA = this.min() == null ? Long.MIN_VALUE : this.min();
        Long maxA = this.max() == null ? Long.MAX_VALUE : this.max();
        Long minB = rhs.min() == null ? Long.MIN_VALUE : rhs.min();
        Long maxB = rhs.max() == null ? Long.MAX_VALUE : rhs.max();
        return minA.compareTo(minB) >= 0 && maxA.compareTo(maxB) <= 0;
    }

    @Override
    public cInt any() {
        return cInt.of(null, null);
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
        if (this.isAny())
            return "**";
        else if (this.isMaybe())
            return "?";
        else if (this.isSome())
            return "+";
        else if (this.isMaybeSome())
            return "*";
        else if (this.isExact())
            return "" + this.min;
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
