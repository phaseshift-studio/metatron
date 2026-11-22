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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.type.FiberedRec;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.BLOCK;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Selector — the per-call strategy of the resolution stage: given a left-hand-side and the user
 * instruction being resolved, it fetches the candidate instructions the call may resolve to,
 * orders them by its own rule, and returns the first candidate its {@link Binder} can make
 * concrete, or {@code null} when nothing matches.
 * <p>
 * A selector is a machine component ({@code selector::T}) and the strategy half of
 * {@code resolver::T}: the resolver owns the walk (threading the type through a whole
 * {@code code::T}) and hands each instruction to its selector. The binder arrives as an argument
 * rather than as a field so that one selector composes with any binder, and neither holds global
 * state:
 * {@code resolver::[selector=>specificity_selector::T, binder=>generic_binder::T]}.
 * <p>
 * The ordering rule is the whole strategy — and it is where the interesting variation lives:
 * {@code specificity_selector::T} scores by signature specificity and takes the best,
 * {@code firstfind_selector::T} takes the first viable candidate in read order. A selector that
 * cannot resolve an instruction returns {@code null} rather than failing, leaving it for runtime
 * resolution.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Selector extends Component {

    /**
     * Resolve one user instruction against {@code lhs}: fetch and order its candidates, bind each in
     * order with {@code binder}, and return the first that binds.
     *
     * @param lhs    the left-hand-side object being operated on
     * @param user   the user instruction being resolved (contains args, dom/rng hints)
     * @param binder the binder that makes a candidate concrete or rejects it
     * @return the resolved instruction, or {@code null} if no candidate binds
     */
    Inst apply(final Obj lhs, final Inst user, final Binder binder);

    /**
     * THE INSTRUCTION A CALL REFERS TO — given an lhs and an f-less instruction, the f-bearing instruction it names.
     * This is the whole job of the selector at the call site: fetch the candidates the call may mean, order them,
     * and hand back the one the binder could make concrete. Resolution is not the executor's question: the processor
     * only invokes what the selector hands it.
     * <p>
     * An instruction that already carries its function refers to itself and is returned as-is. On a fibered rec, a
     * call about its fibers refers to the LIFT of the instruction ({@link FiberedRec.Helper#resolve}), so the
     * processor runs a bundle's call like any other.
     */
    final class Helper {

        /**
         * THE CALL FRAME, a selector's last resort (Marko, 2026-11-22: "the resolver will not match an existing pattern,
         * then it will check if it's a rec with lhs and args, and if so, apply it accordingly"). A call whose args are
         * exactly {@code [lhs=>x, args=>a]} is its own application written as data: {@code f(lhs=>x, args=>a)} is
         * {@code x.f(a)}, the args {@code a} an ordered call's lst, a keyed call's rec, or a lone value as one arg. The
         * frame runs {@code start(x).f(a)}, so the call is resolved and applied as any chain's is, per monad or over
         * the whole stream as {@code f} declares, whatever the chain hands the frame. The frame itself runs per monad
         * ({@code #{?}}, which also takes the empty head of a chain), so a stream of n monads answers n copies, as any
         * constant function does. Its args stay raw ({@code ?block}): the processor computes a keyed call's args and
         * adds its own {@code lhs} to them, and the frame reads neither. Null when the call is no frame.
         */
        public static Inst tryRawRecArgs(final Inst user) {
            if (!isRawRecArgs(user))
                return null;
            final Map<Obj, Obj> frame = user.args().asRec().jvm();
            final Obj on = frame.get(uri(LHS));
            final Obj args = frame.get(uri(ARGS));
            final fURI tid = user.tid().removeQ(ARGS);
            final Inst call = instB(tid, args.isPoly() ? args.asPoly() : lst(args));
            return instC(tid.dom(ALL.maybe()).rng(ALL.maybeSome()).addQ(BLOCK), user.args(),
                    (ignored, self) -> start_(on).addInsts(List.of(call)).apply(noobj()));
        }

        /**
         * Whether a call's args are a frame, exactly {@code [lhs=>x, args=>a]}. A FRAME MATCHES NO PATTERN: {@code lhs}
         * is the processor's own key (it adds the lhs to every keyed call's computed args), so no signature can declare
         * it, and an open-world rec signature, which takes keys it never named, would otherwise take the frame as its own
         * call and bind its parameters to nothing ({@code repeat(lhs=>…, args=>…)} ran with no code).
         */
        public static boolean isRawRecArgs(final Inst user) {
            if (!user.args().isRec())
                return false;
            final Map<Obj, Obj> frame = user.args().asRec().jvm();
            return 2 == frame.size() && frame.containsKey(uri(LHS)) && frame.containsKey(uri(ARGS));
        }

        private Helper() {
            // do nothing
        }

        /**
         * The f-bearing instruction {@code inst} refers to against {@code lhs}, or {@code inst} itself when it
         * already carries its function — on a bundle, its lift when it is about the bundle's fibers
         * ({@link FiberedRec.Helper#resolve}). A machine whose typer requires fully-resolved code refuses the escape:
         * an unresolved instruction reaching the executor is then a hard failure rather than a second-chance
         * resolution.
         *
         * @param lhs  the object the instruction is being applied to
         * @param inst the instruction, resolved or not
         * @return the instruction with its function, resolved against {@code lhs} when it had none
         */
        public static Inst resolve(final Obj lhs, final Inst inst) {
            if (!inst.hasf() && Typer.Helper.codeResolve())
                throw MTronException.of("inst reached runtime unresolved (code_resolve enabled): %s => %s", lhs, inst);
            return FiberedRec.Helper.resolve(lhs, inst.hasf() ? inst : inst.resolve(lhs), inst);
        }
    }
}
