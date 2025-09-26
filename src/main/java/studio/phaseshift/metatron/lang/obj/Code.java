/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public interface Code extends Call {

    @Override
    Code clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Inst> value();

    default Inst inst(final int index) {
        return index < this.value().size() ? this.value().get(index) : NoObj.single();
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.apply().iterator();
    }

    @Override
    default Code resolve(final Obj lhs) {
        return this;
    }

    default Inst nextInst(final Inst inst) {
        final Inst nextInst = ((Supplier<Inst>) () -> {
            if (inst.isNoObj())
                return NoObj.single();
            boolean found = false;
            for (final Inst i : this.value()) {
                if (found) return i;
                if (i == inst) found = true;
            }
            return NoObj.single();
        }).get();
        this.logger().trace("fetching next inst: %s => %s", inst, nextInst);
        return nextInst;
    }

    @Override
    default Code vid(final fURI newVid) {
        return (Code) Call.super.vid(newVid);
    }

    @Override
    default Code tid(final fURI newTid) {
        return (Code) Call.super.tid(newTid);
    }

    @Override
    default Code value(final Object newValue) {
        return Call.super.value(newValue);
    }

    @Override
    default Type dom() {
        return this.value().isEmpty() ? T(fURI.NONE.zero()) : this.value().get(0).dom();
    }

    default Type rng() {
        return this.value().isEmpty() ? T(fURI.NONE.zero()) : this.value().get(this.value().size() - 1).rng();
    }

    @Override
    default Obj apply() {
        return this.apply(null);
    }

    @Override
    default Obj apply(final Obj lhs) {
        if (null != lhs && !lhs.matches(this.dom()))
            throw MTronException.of("%s ({{m}}lhs{{/m}}) (%s) does not match {{m}}code domain{{/m}} (%s): %s", lhs, lhs.rng(), this.dom(), this);
        final Monoid monoid = (null == lhs ? MMonoid.of(this) : MMonoid.of(lhs, this));
        final Obj rhs = ObjUtil.oneNoneOrAll(monoid.apply(NoObj.single()).iterator());
        if (!rhs.matches(monoid.rng()))
            throw MTronException.of("%s ({{m}}rhs{{/m}}) (%s) does not match {{m}}code range{{/m}} (%s): %s", rhs, rhs.rng(), this.rng(), this);
        return rhs;
    }

    // Code resolve(final Obj start);

}