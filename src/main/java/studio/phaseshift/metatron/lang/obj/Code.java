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
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface Code extends Obj {

    @Override
    Code clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Inst> value();

    default Inst inst(final int index) {
        return index < this.value().size() ? this.value().get(index) : NoObj.single();
    }

    default Inst next(final Inst inst) {
      final Inst nextInst =  ((Supplier<Inst>) () -> {
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
        return (Code) Obj.super.vid(newVid);
    }

    @Override
    default Code tid(final fURI newTid) {
        return (Code) Obj.super.tid(newTid);
    }

    @Override
    default Code value(final Object newValue) {
        return (Code) Obj.super.value(newValue);
    }

    @Override
    default Obj apply(final Obj lhs) {
        return ObjUtil.oneNoneOrAll(MMonoid.of(lhs,this).apply(NoObj.single()).iterator());
    }

    default Code resolve(final Inst.Resolve desiredResolution, final Obj lhs) {
        final AtomicReference<Obj> token = new AtomicReference<>(lhs);
        return this.value(this.value().stream().map(i -> {
           Inst rinst = i.resolve(desiredResolution,token.get());
           token.set(rinst.dom());
           return rinst;
        }).toList());
    }

   // Code resolve(final Obj start);

}