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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.c.cInt;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;

public interface Int extends Mono, Ring.O<Int> {

    @Override
    Int clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Long jvm();

    default Int jvm(final Long jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Int tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Int vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Int c(cInt c) {
        return (Int) Mono.super.c(c);
    }

    @Override
    default Int zero() {
        return jnt(0);
    }

    @Override
    default Int one() {
        return jnt(1);
    }

    @Override
    default Int plus(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) + (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Int mult(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) * (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Int neg() {
        return this.jvm(-1 * this.intValue());
    }
}
