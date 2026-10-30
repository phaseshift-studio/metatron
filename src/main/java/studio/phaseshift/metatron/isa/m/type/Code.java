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
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.CODE_TID;
import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.branch_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public interface Code extends Call {

    // Code with coefficient {0}: noobj typed as a Code, for a method that answers a Code or nothing.
    Code CODE_NOOBJ = new MCode(List.of(), CODE_TID.zero(), null);

    static Code code_noobj() {
        return CODE_NOOBJ;
    }

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
        return Machine.current().compiler().rewriter().apply(this);
    }

    @Override
    default Code resolve(final Obj lhs) {
        return Machine.current().compiler().resolver().apply(lhs, this);
    }

    /**
     * THE NEXT INST BY LIST ORDER — the sequence is the list, the vid is only its identity.
     * <p>
     * Three short-circuits, cheapest first, then the memoized hash as a filter with equals as the only
     * confirmation: 1) the identical inst (the common case — the monad hands back what it took, and a clone
     * shares its vid reference), 2) the identical vid, 3) equal-but-distinct vids, which is the only case that
     * pays the field-by-field fURI walk. Bound by size() - 1 so only members that HAVE a successor are anchors:
     * the successor is then in range by construction, with no bounds check to borrow.
     */
    default Inst nextInst(final Inst inst) {
        if (inst.isNoObj()) return noobj();
        final List<Inst> insts = this.jvm();
        final fURI vid = inst.vid();
        final int hash = null == vid ? 0 : vid.hashCode();          // memoized in fURI (volatile int read)
        final int size = this.jvm().size();
        final int last = size - 1;
        for (int i = 0; i < last; i++) {
            final Inst check = insts.get(i);
            final fURI cvid = check.vid();
            if (inst == check || cvid == vid                            // identity, then shared vid reference
                    || (null != vid && null != cvid                     // do NOT lean on fURI's 0-sentinel
                    && cvid.hashCode() == hash && Objects.equals(cvid, vid)))   // hash filters, equals confirms
                return insts.get(i + 1);
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
        if (this.jvm().isEmpty())
            return this.tid().isZero() ? noobj() : lhs;
        return Machine.evaluate(lhs, this);
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

    /*
     * WHAT REMAINS HERE, and why: NOTHING. Every code::T operation carries a LAWS-ONLY row (plus, compose) or
     * attributes no row at all (the two `as` conversions), so all four moved to mInsts.codes(), docs and laws
     * colocated with the bodies they describe. The class stays only because mInstSet streams it
     * (`Code.CodeType.insts()`); dropping that stream and this class is a follow-up.
     */
    public static class CodeType {

        private CodeType() {
            // do nothing
        }

        public static Set<Inst> insts() {
            // every code::T operation now lives in mInsts.codes(): nothing is left to declare here
            return new LinkedHashSet<>();
        }

    }
    // Code resolve(final Obj start);

}
