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

package studio.phaseshift.metatron.isa.m.type;

import org.jspecify.annotations.NonNull;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.MONAD_IN;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public interface Code extends Call {

    @Override
    Code clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Inst> jvm();

    default Inst inst(final int index) {
        return index < this.jvm().size() ? this.jvm().get(index) : noobj();
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return this.asCode().insts().stream().allMatch(x -> x.isResolved(nested));
    }

    @Override
    default @NonNull Iterator<Obj> iterator() {
        return this.apply().iterator();
    }

    default Code rewrite() {
        return FixPointRewriter.single().apply(this);
    }

    @Override
    default Code resolve(final Obj lhs) {
        return Compiler.Helper.resolve(lhs, this);
    }

    default Inst nextInst(final Inst inst) {
        if (inst.isNoObj()) return noobj();
        int i = Integer.parseInt(inst.vid().toString()) + 1;
        for (final Inst in : this.jvm()) {
            if (Integer.parseInt(in.vid().toString()) == i)
                return in;
        }
        return noobj();
    }

    default boolean isAuto() {
        if (this.isNoObj())
            return false;
        if (this.codeValue().isEmpty())
            return false;
        final fURI firstBase = this.codeValue().getFirst().tid().basePath();
        return firstBase.equals(AUTO_FROM_INST_TID) || firstBase.equals(AUTO_AT_INST_TID) || firstBase.equals(AUTO_INST_TID);
    }

    @Override
    default Inst asInst() {
        return instLambda(this);
    }

    @Override
    default Code vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Code tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    @Override
    default Code jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Type dom() {
        return this.jvm().isEmpty() ? NOOBJ_TYPE : this.jvm().getFirst().dom(); // TODO: if unresolved, it's maybe.. is that good?
    }

    default Type rng() {
        return this.jvm().isEmpty() ? NOOBJ_TYPE : this.jvm().getLast().rng();
    }


    @Override
    default Obj apply() {
        return this.apply(noobj());
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Call code = this.tryToInst();
        if (code.isCode())
            // wrapStart prepends start(value) for a value lhs; a monadic lhs passes through unchanged and
            // rides START so the monad's loop/state context survives into the processor.
            return Machine.defaultMachine().apply(wrapStart(lhs, code.as()), lhs.isMonad() ? lhs : noobj());
        // single inst: dispatch by the inst's own monad flag. A monadic inst (loop())
        // receives the monad; a value inst is resolved and applied against the monad's obj.
        final boolean monadic = code.isInst() && code.resolve(lhs).tid().hasQ(MONAD_IN);
        final Obj arg = lhs.isMonad() && !monadic ? lhs.asMonad().obj() : lhs;
        return code.resolve(arg).apply(arg);
    }

    /**
     * Inject a runtime value into code by prepending {@code start(lhs)} — the same shape the
     * processor used to mint. This threads the element type through the whole chain (so e.g.
     * {@code as(str::T).count()} resolves against {@code int} from {@code start(1)}, not against
     * {@code noobj}). Monadic and empty starts pass the code through unchanged.
     */
    static Code wrapStart(final Obj lhs, final Code code) {
        if (lhs.isNoObj() || lhs.isMonad())
            return code;
        final List<Inst> insts = new ArrayList<>();
        insts.add(instB(START_INST_TID, lst(lhs)));
        insts.addAll(code.codeValue());
        return MCode.of(insts);
    }

    public static class CodeType {

        private CodeType() {
            // do nothing
        }

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(Tokens.CODE_TID).rng(Tokens.LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asCode().codeValue().stream().map(Obj::<Obj>as).toList()).c(c -> c.mult(lhs.c()))),
                    instC(AS_INST_TID.dom(Tokens.CODE_TID).rng(Tokens.INST_TID), lst(INST_TYPE), (lhs, inst) -> instLambda(lhs.asCode()).c(c -> c.mult(lhs.c())))));

        }

    }
    // Code resolve(final Obj start);

}