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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.resolver.ScoringInstResolver;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.LOOP;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * Compiler — lowers {@code code::T} to {@code code::T} by composing a schedule of named, overridable
 * stages. This is the strategic contract, not a black box: the members are the discrete steps every
 * compiler author will reach for, each with a rock-solid default, so writing a compiler is "override
 * the one stage you disagree with" rather than "implement {@code apply} from scratch."
 * <p>
 * The vocabulary:
 * <ul>
 *   <li>{@link #rewrite} / {@link #applyRewrite} — fixpoint over the machine's rewrite rules;</li>
 *   <li>{@link #resolve} / {@link #resolveInst} — thread the type through, resolving one inst at a
 *       time (the resolved inst's {@code rng()} is the "rhs" threaded to the next);</li>
 *   <li>{@link #bind} — the generic-binding stage (currently folded into {@code resolveInst} via
 *       {@code Inst.Helper.bindGenerics}; identity until separated);</li>
 *   <li>{@link #type} — compile-time type resolution, the Typer (currently the global
 *       {@code TypeGraph}; identity until separated).</li>
 * </ul>
 * {@link #apply} is only the default schedule over that vocabulary — {@code rewrite → resolve →
 * bind → type}. A different schedule (e.g. a fixpoint of {@code rewrite → resolve}) is a different
 * {@code apply}, or a different mtron expression composed from the same stage insts.
 * <p>
 * <b>Java first, inst-ify later.</b> These stages stay Java methods while we perfect them — the
 * {@code TID}/{@code TYPE}/{(lhs, inst) -> …} wrapping is fixed noise until the algorithm under it
 * is settled. Once happy, each method becomes a native inst with the same name, a one-line body:
 * <pre>
 *   rewrite  →  instC(REWRITE_INST_TID, lst(), (lhs, inst) -> this.rewrite(lhs.asCode()))
 *   resolve  →  instC(RESOLVE_INST_TID, lst(), (lhs, inst) -> this.resolve(lhs.asCode()))
 *   bind     →  instC(BIND_INST_TID,    lst(), (lhs, inst) -> this.bind(lhs.asCode()))
 *   type     →  instC(TYPE_INST_TID,    lst(), (lhs, inst) -> this.type(lhs.asCode()))
 * </pre>
 * so {@code rewrite => resolve => bind => type} becomes a compiler in mtron.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Compiler extends Machine.Component, Rec {

    // ======================== schedule ========================

    /**
     * The default schedule — {@code rewrite → resolve → bind → type}. Override to re-order, repeat,
     * or interleave the stages; a compiler is the composition, the stages are the vocabulary.
     */
    @Override
    default Code apply(final Obj code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        Code c = code.asCode();
        c = this.rewrite(c);
        c = this.resolve(c);
        c = this.bind(c);
        c = this.type(c);
        return c;
    }

    // ======================== stage · rewrite ========================

    /**
     * Apply the machine's rewrite rules until the code stabilizes (fixpoint). The convergence
     * window is the {@code loop} rec entry (default one stable pass); each pass applies every rule
     * via {@link #applyRewrite}.
     */
    default Code rewrite(final Code code) {
        final AtomicReference<Code> rewritten = new AtomicReference<>(code);
        int hash = code.hashCode();
        int done = this.at(LOOP).orElse(jnt(2)).intValue().intValue();
        while (done != 0) {
            this.machine().instset().at(REWRITE).elements()
                    .forEach(r -> rewritten.set(this.applyRewrite(r, rewritten.get())));
            if (hash == (hash = rewritten.get().hashCode()))
                done--;
        }
        return rewritten.get();
    }

    /**
     * Apply one rewrite rule to the code. A rule may yield new code, {@code noobj} (empty), or
     * fail — anything else is a malformed rewrite.
     */
    default Code applyRewrite(final Obj rewrite, final Code code) {
        final Obj rewritten = rewrite.apply(code);
        if (rewritten.isCode())
            return rewritten.asCode();
        if (rewritten.isNoObj())
            return MCode.code0();
        throw MTronException.of("rewrite %s rewrote to non-code %s", rewrite, rewritten);
    }

    // ======================== stage · resolve ========================

    /**
     * Resolve the whole code against its start obj — threads the type, one inst at a time.
     */
    default Code resolve(final Code code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        return this.resolve(Helper.getStartObj(code), code);
    }

    /**
     * Resolve the whole code against an explicit lhs — threads the type, one inst at a time.
     */
    default Code resolve(final Obj lhs, final Obj code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        return ScoringInstResolver.INSTANCE.get().resolveCode(lhs, code.asCode());
    }

    /**
     * Resolve a single instruction against its lhs. The resolved inst's {@code rng()} is the "rhs"
     * threaded into the next instruction — that coefficient is the boundary where a gather
     * ({@code dom_c = *}) must survive, not be flattened to a mapper.
     */
    default Inst resolveInst(final Obj lhs, final Inst inst) {
        return ScoringInstResolver.INSTANCE.get().resolveInst(lhs, inst);
    }

    // ======================== stage · bind ========================

    /**
     * Bind generic variables. Identity for now — the binding runs inside {@link #resolveInst}
     * ({@code Inst.Helper.bindGenerics}); this is the seam a compiler overrides once binding is
     * split out as its own pass.
     */
    default Code bind(final Code code) {
        return code;
    }

    // ======================== stage · type ========================

    /**
     * Compile-time type resolution (the Typer). Identity for now — this is the global
     * {@code TypeGraph}; this is the seam a machine overrides to carry its own resolver.
     */
    default Code type(final Code code) {
        return code;
    }

    // ======================== Helper ========================

    class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * The obj a code starts from — the initial instruction's argument, or {@code noobj()}.
         */
        public static Obj getStartObj(final Code code) {
            final Inst startInst = code.insts().getFirst();
            return startInst.isInitial() ? startInst.arg(0) : noobj();
        }
    }
}
