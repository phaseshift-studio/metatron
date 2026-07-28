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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MInst extends MObj implements Inst {
    public MInst(final Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? M_ISA_INST_TID : tid, vid);
    }


    @Override
    public Inst clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Triplet<Poly, Inst.f, Obj> jvm() {
        return (Triplet<Poly, Inst.f, Obj>) this.jvm;
    }

    @Override
    public Inst c(final cInt c) {
        return (Inst) super.c(c);
    }

    public static Inst instA(final fURI tid) {
        return new MInst(Triplet.with(null, null, NoObj.noobj()), tid, null);
    }

    public static Inst instB(final fURI tid, final Poly args) {
        return new MInst(Triplet.with(args, null, NoObj.noobj()), tid, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        return new MInst(Triplet.with(args, Inst.f.of(f), NoObj.noobj()), tid, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final Function<Obj, Obj> f) {
        return new MInst(Triplet.with(args, Inst.f.of(f), NoObj.noobj()), tid, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f, final Obj seed) {
        return new MInst(Triplet.with(args, Inst.f.of(f), seed), tid, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final String code) {
        return new MInst(Triplet.with(args, Inst.f.of(code), NoObj.noobj()), tid, null);
    }

    public static Inst instLambda(final BiFunction<Obj, Inst, Obj> f) {
        return instLambda(lst(T(ALL.maybeSome())), f);
    }

    public static Inst instLambda(final Lst args, final BiFunction<Obj, Inst, Obj> f) {
        return instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), args, f);
    }

    public static Inst instLambda(final fURI dom, final fURI rng, final BiFunction<Obj, Inst, Obj> f) {
        return instC(M_ISA_INST_TID.dom(dom).rng(rng), lst(T(ALL.maybeSome())), f);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tid, this.vid);
    }

    @Override
    public String toShortString() {
        if (this.tid().basePath().equals(AUTO_FROM_INST_TID))
            return "!*" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(AUTO_AT_INST_TID) && this.arg(1).isNoObj())
            return "!@" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(AUTO_INST_TID))
            return "!" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(FROM_INST_TID))
            return "*" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(ISA_INST_TID))
            return "?" + this.arg(0).toShortString();
        final String internal = this.args().elements()
                .map(Obj::toShortString)
                .reduce("", (a, b) -> a + b + ",");
        return this.tid().qLess().small() +
                ((!this.tid().rng().equals(ALL) && !this.tid().dom().equals(ALL)) ? "?" + this.tid().rng().small() + "<=" + this.tid.dom().small() : "") +
                "(" + (internal.isEmpty() ? "" : internal.substring(0, internal.length() - 1)) + ")" +
                ((null == this.f() || this.f().isLambda()) ? "" : "{" + this.f() + "}");
    }


    @Override
    public boolean equals(final Object other) {
        return (other instanceof Inst otherInst) &&
                Objects.equals(this.tid, otherInst.tid()) &&
                Objects.equals(this.args(), otherInst.args()) &&
                Objects.equals(this.f(), otherInst.f()) &&
                Objects.equals(this.vid, otherInst.vid());
        /*Objects.equals(this.value,((Obj) other).value())*/
    }
}