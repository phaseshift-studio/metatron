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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.BLOCK;
import static studio.phaseshift.metatron.Tokens.CODE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
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

    /**
     * A CODE TESTS A CODE PATTERN BY BINDING IT ({@link Helper#bind}), so plus(int::T@A).mult(A::T) admits
     * plus(2).mult(2) and not plus(2).mult('age'). Against an instruction, a code is tested as the instruction it is, and
     * fails when it is not one. A code that is a reference ({@link #isAuto()}) is assumed to be what it refers to.
     */
    @Override
    default boolean test(final Obj other) {
        // A REFERENCE IS ASSUMED LEGAL: !*, !@ and ! name something elsewhere or later, and a test can neither
        // dereference the reference nor evaluate the code to see what it would be
        if (this == other || this.isAuto())
            return true;
        if (other.isCode())
            return null != Helper.bind(this.insts(), other.asCode().insts());
        if (other.isObjInst()) {
            final Call inst = this.tryToInst();
            return inst.isObjInst() && inst.test(other);
        }
        return Call.super.test(other);
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return this.asCode().insts().stream().allMatch(x -> x.isResolved(nested));
    }

    /**
     * A CODE IS A STREAM: iterating it runs it, so its objs are what it answers. A BLOCKED code ({@code ?block}) is held
     * instead, one obj, itself (Marko, 2026-11-13): a negative span with no converse leaves its backward part as such a
     * code, {@code start(x).{m,-1}inst}, which, run again, answers itself, so a stream that ran it would never end.
     */
    @Override
    default @NonNull Iterator<Obj> iterator() {
        return this.tid().hasQ(BLOCK) ? List.<Obj>of(this).iterator() : this.apply().iterator();
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
        // the end of the code (the zero that maps to nothing) has no successor. A zero-point inst has one: the dead
        // monad its zero leaves walks on to an initial inst, as one a filter drops does
        if (inst.isNoObj() && inst.isTerminal()) return noobj();
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
     * WHAT REMAINS HERE, and why: NOTHING. Every code::T operation is built through InstBuilder in
     * mInsts.code_insts(), docs and laws colocated with the bodies they describe. The class stays only because
     * mInstSet streams it (`Code.CodeType.insts()`); dropping that stream and this class is a follow-up.
     */
    public static class CodeType {

        private CodeType() {
            // do nothing
        }

        public static Set<Inst> insts() {
            // every code::T operation now lives in mInsts.code_insts(): nothing is left to declare here
            return new LinkedHashSet<>();
        }

    }

    class Helper {
        private Helper() {
            // do nothing
        }

        /**
         * WHAT A CODE BINDS AGAINST A CODE PATTERN, or null when it does not match: instruction by instruction, each
         * instruction testing its pattern as a value tests a type, with one binding of the pattern's generics and value
         * names for the whole length (the binder's walk: {@link Inst.Helper#declarations}, then
         * {@link Inst.Helper#bindGenerics} per instruction). The answer is the value names and what they matched;
         * {@link Code#test} asks only that there is one, and {@code update()} pushes it for its change.
         * <p>
         * EVERY NAME MUST BIND (Marko, 2026-11-08). A code argument is typed by its range, so a value of that range meets
         * it ({@code mult(2.0)} is {@code mult(real::T)}, as {@code mult(map(#::T@b).inv())} is), but a name declared inside
         * the code has nothing to bind in a value. A pattern that leaves one of its names unbound does not match: whatever
         * reads the binding would read nothing in that name's place.
         * <p>
         * AN INSTRUCTION'S SPAN IS A RUN OF INSTRUCTIONS (Marko, 2026-11-09: {@code {span}inst{point}}, as a type's span is a
         * run of slots): {@code {+}#({*}#::T)} is one or more instructions of any name and arguments. A spanned pattern is laid
         * over the code as a lst type's entries are laid over its indices ({@link Type.Helper#unfold}), and each instruction
         * binds the slot it falls under, its span removed. A finite span unfolds in the parser, so only a ranged one
         * reaches here.
         */
        public static Map<fURI, Obj> bind(final List<Inst> users, final List<Inst> patterns) {
            final Set<fURI> declared = new HashSet<>();
            final Map<fURI, fURI> generics = Inst.Helper.declarations(patterns, declared);
            if (null == generics)
                return null;
            final List<? extends Obj> slots = patterns.stream().allMatch(pattern -> pattern.tid().b().isOne())
                    ? (users.size() == patterns.size() ? patterns : null) : Type.Helper.unfold(users, patterns);
            if (null == slots)
                return null;
            final Map<fURI, Obj> names = new LinkedHashMap<>();
            for (int i = 0; i < users.size(); i++) {
                final Inst slot = slots.get(i).asInst();
                // a span in the pattern is a run of args: it binds at the user's shape, as a declaration does at a call's
                final Inst pattern = Inst.Helper.unfold(slot, users.get(i));
                if (null == pattern || !users.get(i).test(slot) || null == Inst.Helper.bindGenerics(noobj(), pattern, users.get(i), generics, names))
                    return null;
            }
            return names.keySet().containsAll(declared) ? names : null;
        }
    }
    // Code resolve(final Obj start);

}
