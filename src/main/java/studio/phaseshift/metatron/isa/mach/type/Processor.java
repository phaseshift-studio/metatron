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

import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Objs;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.thread.mThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;
import studio.phaseshift.metatron.util.MTronException;

import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.MONAD_IN;
import static studio.phaseshift.metatron.Tokens.MONAD_OUT;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code0;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Processor — runs compiled {@code code::T} to {@code obj}. A processor is the machine's execution
 * axis (a thread) <em>and</em> an {@code obj} (its state lives in a rec's jvm map), so it is both
 * an {@code mThread} and an {@code Obj}. The monad-shaped surface (run/barrier/halt queues) lives on
 * the {@code monad_processor::T} refinement, not here — a processor need not be monadic. Resolution
 * is the {@code Obj#resolve(Obj)} contract inherited from {@code Obj}, not a distinct axis.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Processor extends mThread, Machine.Component {

    /**
     * Redeclared abstract to fold the abstract {@code mThread.apply} and the default
     * {@code Obj.apply} into one contract — a processor is a callable obj, and the concrete
     * processor (a thread) supplies the single implementation.
     */
    @Override
    Obj apply(final Obj input);

    /**
     * @return the code this processor is executing
     */
    default Code code() {
        return this.at(CODE).orElse(code0());
    }

    /**
     * @return a copy of this processor executing the given code
     */
    default Processor code(final Code code) {
        return this.at(uri(CODE), code, MUTABLE).as();
    }

    /**
     * @return a copy of this processor with the given onHalt callback registered
     */
    Processor onHalt(final Consumer<Obj> halted);

    /**
     * @return the current onHalt callback
     */
    Consumer<Obj> onHalt();

    // ======================== runtime (computeArgs + apply) ========================

    /**
     * The processor's execution runtime — the per-instruction computation the machine performs
     * when it drives a monad: computeArgs, function application, the noobj/domain guards, the
     * runtime dom/rng type checks, fail propagation, and coefficient policy. Relocated out of
     * {@code Inst} (the type system) so the type layer stays data and the machine owns evaluation.
     */
    class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * inst_dom/inst_rng dispatch check. Fast-out when the lhs's tid/vid label matches the
         * target type and the coefficient/poly are consistent — trust construction-time
         * validation. Otherwise fall back to the full structural test.
         */
        public static boolean instDomRngMatch(final Obj lhs, final Type type) {
            if (type.tid().poly().isEmpty()
                    && Obj.Helper.specificTypeId(lhs).test(Obj.Helper.specificTypeId(type))
                    && lhs.c().within(type.c()))
                return true;
            return lhs.test(type);
        }

        /**
         * computeArgs — compute the inst's arguments against the runtime lhs. Blocking insts
         * return their args raw (thunks applied lazily by the inst's function); every other arg
         * is applied to the lhs.
         */
        public static Inst applyArgs(final Obj lhs, final Inst inst) {
            final boolean blocking = inst.isBlocking();
            final Poly cargs = inst.args().isLst() ?
                    lst(inst.args().lstValue()
                            .stream()
                            .map(FutureObj::<Obj>resolveFuture)
                            .map(arg -> {
                                if (blocking)
                                    return arg;
                                else {
                                    final Obj r = Objs.trySingleton(arg.apply(lhs));
                                    if (null == r)
                                        return null;
                                    // Allow template expansion: if arg is Uri or Str with templates, always return expanded result
                                    final boolean isTemplateExpansion = (arg.isUri() && arg.asUri().hasTemplates()) ||
                                            (arg.isStr() && (arg.strValue().contains("${") || arg.strValue().contains("{{{")));
                                    if (!arg.isObjCall() && !isTemplateExpansion && !r.test(arg)) {
                                        return arg;
                                    }
                                    return r;
                                }
                            }).toList()) :
                    rec(inst.args().recValue().entrySet()
                            .stream()
                            .map(kv -> rel(kv.getKey().apply(lhs), blocking ?
                                    kv.getValue() :
                                    kv.getValue().apply(lhs))))
                            .plus(rec(LHS, lhs));
            return inst.args(cargs);
        }

        /**
         * Runtime dynamic-inst resolution — the escape hatch for an inst that reached the processor
         * unresolved. Gated by {@code TypeCheck.code_resolve}: when the typer requires fully-resolved
         * code, an unresolved inst is a hard failure; otherwise it is resolved on demand (per monad)
         * via the compiler's resolver.
         */
        public static Inst resolveRuntime(final Obj lhs, final Inst inst) {
            if (TypeCheck.code_resolve.enabled())
                throw MTronException.of("inst reached runtime unresolved (code_resolve enabled): %s => %s", lhs, inst);
            return inst.resolve(lhs);
        }

        /**
         * The machine's single entry for applying one inst against a runtime lhs: resolve (the
         * code_resolve-gated dynamic escape) then the runtime (guards, computeArgs, f().apply,
         * checks, coefficient). {@code Inst.apply} and the monad loop both hand off here.
         */
        public static Obj apply(final Obj lhs, final Inst inst) {
            final Inst cinst = inst.hasf() ? inst : resolveRuntime(lhs, inst);
            return invoke(lhs, cinst, inst);
        }

        /**
         * The processor's full per-instruction runtime: the resolved inst's noobj/domain guards,
         * domain check (coefficient compression), computeArgs, function application, range check,
         * fail propagation, and coefficient policy.
         */
        public static Obj invoke(final Obj lhs, Inst cinst, final Inst original) {
            final boolean isMonadicInst = original.tid().hasQ(MONAD_IN) || original.tid().hasQ(MONAD_OUT);
            if (cinst.isNoObj())
                return fail(MTronException.of("unable to locate inst-f %s::T => %s", Obj.Helper.specificTypeId(lhs).name(), original, lhs));
            if (lhs.isNoObj() && !cinst.dom().c().isZeroable())
                return noobj();
            Obj clhs = lhs;
            boolean modulateC = false;
            if (TypeCheck.inst_dom.enabled() && !isMonadicInst && !lhs.isFail() && !lhs.isCaughtFail()
                    && !instDomRngMatch(clhs, cinst.dom()) && clhs.unique()) {
                clhs = clhs.c(cInt::one);
                // bindQ is applied by the compiler (ScoringResolver.resolveInst); the resolved
                // inst already carries ?block, so no second bindQ is needed at apply time.
                cinst = resolveRuntime(clhs, original);
                modulateC = true;
                if (!instDomRngMatch(clhs, cinst.dom()))
                    return fail("lhs range does not match inst domain: %s => %s [%s]", clhs.rng(), cinst.dom(), cinst);
            }
            final Obj rhs = applyFunction(clhs, cinst, original, isMonadicInst);
            final cInt cc = cinst.c();
            return modulateC ? rhs.c(c -> c.mult(lhs.c()).mult(cc)) : rhs.c(c -> c.mult(cc));
        }

        /**
         * The function application itself (ensure-f, applyArgs, f().apply, error funnel, range check, fail
         * propagation), which {@link #invoke(Obj, Inst, Inst)} hands off to once its guards have passed.
         * <p>
         * Private with a single caller, so it is a readability split rather than an override seam — the old
         * name {@code invokeCore} suggested otherwise.
         */
        private static Obj applyFunction(final Obj clhs, Inst cinst, final Inst original, final boolean isMonadicInst) {
            Obj rhs;
            if (!clhs.isFail() || cinst.isCatch()) {
                try {
                    if (null == cinst.f()) {
                        if (cinst.tid().basePath().equals(AS_INST_TID)) {
                            cinst = cinst.f(Inst.f.of((x, y) -> x.tid(y.arg(0).vid())));
                        } else
                            throw MTronException.of("unable to determine inst function:" +
                                    "\n\t%-10s  => %s   | [inst]" +
                                    "\n\t%-10s  => %s   |  \\_dom" +
                                    "\n\t%-10s %s=> %s   |  \\_args", clhs, cinst, clhs.type(), cinst.dom(), clhs.type(), cinst.args().elements().allMatch(clhs::test) ? "=" : "X", cinst.args());
                    }
                    final Inst cin1 = cinst;
                    final Obj clhs1 = clhs;
                    cinst = ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst_args, cin1.tid() + ""), () -> applyArgs(clhs1, cin1));
                    Memory.argStack().push(cinst.args());
                    try {
                        final Inst cin2 = cinst;
                        final Obj clhs2 = clhs;
                        rhs = ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, cin2.tid() + ""),
                                () -> Objs.trySingleton(FutureObj.resolveFuture(cin2.f().apply(clhs2, cin2))));
                        rhs = null == rhs ? noobj() : rhs;
                        if (rhs.isUncaughtFail())
                            return rhs;
                        Graphitty.log(cinst).trace("%s (lhs) => %s (inst) => %s (rhs) evaluated successfully", clhs, cinst, rhs);
                    } catch (final Exception e) {
                        if (!cinst.args().test(original.args())) {
                            final Fail child = Poly.Helper.failChild(original.args());
                            final String text = "args do not match inst args: " + Poly.Helper.mismatchText(cinst.args(), original.args());
                            throw null == child ? MTronException.of(text) : MTronException.of(child.jvm(), text);
                        } else
                            throw MTronException.funnel(e, Inst.Helper.instContext(cinst));
                    } finally {
                        Memory.argStack().pop();
                    }
                } catch (final Exception e) {
                    rhs = fail(e);
                }
                if (TypeCheck.inst_rng.enabled() && !isMonadicInst && !rhs.isType() && !rhs.isFail() && !clhs.isCaughtFail()
                        && !instDomRngMatch(rhs, cinst.rng())) {
                    final Fail child = Poly.Helper.failChild(rhs);
                    final String text = "rhs does not match inst range: " + Poly.Helper.mismatchText(cinst.rng(), rhs);
                    rhs = null == child ? fail(MTronException.of(text)) : fail(MTronException.of(child.jvm(), text));
                }
            } else {
                rhs = clhs; // propagate fail through inst unless it's a catch inst
            }
            return rhs;
        }
    }
}
