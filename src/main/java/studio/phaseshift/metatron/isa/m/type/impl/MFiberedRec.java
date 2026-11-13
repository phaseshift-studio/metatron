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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.type.FiberedRec;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/**
 * A {@link FiberedRec} over an {@link MRec}: a bundle's fields, read through its fibers.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MFiberedRec extends MRec implements FiberedRec {

    private MFiberedRec() {
        super();
    }

    /**
     * {@code bundle} as a fibered rec: itself when it is one, else a wrapper over its fields — no copy, no check and no
     * write, since the fields, type and address are the bundle's own.
     */
    public static FiberedRec fiberedRec(final Rec bundle) {
        return bundle instanceof FiberedRec fibered ? fibered : (FiberedRec) new MFiberedRec().self(bundle.jvm(), bundle.tid(), bundle.vid());
    }

    @Override
    public List<fURI> fibers() {
        return this.type().tid().poly().stream().map(fiber -> f(fiber.trim())).toList();
    }

    @Override
    public Obj lift(final Inst inst) {
        final List<Obj> results = new ArrayList<>();
        Rec moved = rec(new LinkedHashMap<>(this.jvm()));
        boolean held = true;
        for (final fURI fiber : this.fibers()) {
            final Obj value = this.at(fiber);
            if (value.isNoObj())
                continue;
            // ONE LEVEL DEEP: a fiber resolves the call against the object it holds, so a bundle at a fiber is
            // applied to as itself and never lifted again
            final Inst cinst = inst.hasf() ? inst : inst.resolve(value);
            if (!FiberedRec.Helper.applicable(false, value, cinst, inst))
                continue;
            final Obj rhs = Processor.Helper.invoke(value, cinst, inst);
            if (rhs.isUncaughtFail())
                return rhs;
            if (!rhs.isNoObj())
                results.add(rhs);
            // A FIBER HOLDS WHAT IT HELD: a result goes back at a named fiber, at the multiplicity and as the type of
            // the value it replaces. Asked of the fiber, never of the rebuilt bundle: a bundle is also what any of its
            // fibers is, so the bundle's own test passes on another fiber's account.
            // A NAMESPACE-QUALIFIED FIBER IS NEVER HELD: it belongs to the wild fiber (`rec[geo:+]`), and the rec it
            // wraps is a feature whose geometry ANSWERS — the result surfaces rather than being written back
            held = held && !fiber.hasPattern() && !fiber.hasScheme() && rhs.c().equals(value.c()) && rhs.test(value.type());
            if (held)
                moved = moved.at(fiber, rhs);
        }
        // A FIBER THAT CHANGES TYPE MAKES THE CALL A PROJECTION (Marko, 2026-11-09): not the rec::T, but the
        // results of the instruction, summed (fight: ninja => str answers the str)
        return results.isEmpty() || !held ? objs(results.iterator()) : this.clone(moved.jvm(), this.tid(), this.vid());
    }
}
