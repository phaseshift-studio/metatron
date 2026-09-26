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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.OBJECT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TheoryHelper extends MRec {

    public TheoryHelper(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static TheoryHelper from(final Rec rec) {
        return rec instanceof TheoryHelper ? (TheoryHelper) rec : new TheoryHelper(rec.jvm(), rec.tid(), rec.vid());
    }

    public static fURI tidToKey(final fURI theoryTID) {
        return f(theoryTID.name().replace("_theory", ""));
    }

    public static Obj zeroElement(final fURI theoryTID, final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(tidToKey(theoryTID)).orElse(rec0());
        return typeTheory.at(ZERO);
    }

    public static Obj oneElement(final fURI theoryTID, final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(tidToKey(theoryTID)).orElse(rec0());
        return typeTheory.at(ONE);
    }

    public static Inst plusInst(final fURI theoryTID, final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(tidToKey(theoryTID)).orElse(rec0());
        return typeTheory.at(ADD).orElse(typeTheory.at(OP));
    }

    public static Inst plusZeroInst(final fURI theoryTID, final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(tidToKey(theoryTID)).orElse(rec0());
        return typeTheory.at(ADD).orElse(typeTheory.at(OP)).asInst().args(lst(TheoryHelper.zeroElement(theoryTID, type)));
    }

    public static Inst multOneInst(final fURI theoryTID, final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(tidToKey(theoryTID)).orElse(rec0());
        return typeTheory.at(MUL).orElse(typeTheory.at(OP)).asInst().args(lst(TheoryHelper.oneElement(theoryTID, type)));
    }

    /**
     * The involution op of the operand's additive group — {@code add_group.inv} (e.g. {@code neg}).
     * Unlike the ring unit ops, {@code inv} is argless, so this returns the bare inv inst; the caller
     * matches {@code op()} pairs against it.
     */
    public static Inst invInst(final Type type) {
        final Rec typeTheory = OBJECT_TYPE.constructor().apply(type).orElse(rec0()).at(LAW).orElse(rec0()).at(f("add_group")).orElse(rec0());
        return typeTheory.at(INV).asInst();
    }
}
