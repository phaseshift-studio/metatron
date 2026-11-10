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

package studio.phaseshift.metatron.isa.m.mach.type;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Selector;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.m.sys.type.ExecutionStack;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronUISerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.transientFail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * processor::T — the architecture: a machine component (never a thread). It is the schema of computation, one per
 * machine, and a computation's state is its computer's, not its own: it only indexes the computers it mints
 * ({@code ~/processor/computer/<n>}) and holds the machine's fail stack ({@code ~/processor/fail}).
 * {@link #apply(Code)} instantiates a {@link Computer} — the instance that runs the code. The per-instruction runtime
 * lives in {@link Helper} (resolve-one → guards → function application → coefficient), inherited by every call path.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Processor extends Component {

    /**
     * INSTANTIATE — apply this architecture to {@code code} and mint a computing instance. The returned computer is
     * dormant until {@code apply}/{@code applyAsync} (inherited from {@code mThread}) starts it.
     */
    Computer apply(Code code);

    /**
     * @return the computers this architecture has minted — its index, a Rec field keyed by each computer's number
     * ({@code ~/processor/computer/<n>}).
     */
    Rec computers();

    /**
     * REAP every computer this architecture indexes — the dual of {@link #apply(Code)}, the same shape as
     * {@code index}/{@code drop}.
     */
    void close();

    // ======================== runtime (computeArgs + apply) ========================

    class Helper {

        private Helper() {
            // do nothing
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
                                            (arg.isStr() && arg.asStr().hasTemplates());
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
         * The machine's single entry for applying one inst against a runtime lhs: let the {@link Selector} say which
         * instruction the call refers to ({@link Selector.Helper#resolve}) and run it (guards, computeArgs,
         * f().apply, checks, coefficient).
         */
        public static Obj apply(final Obj lhs, final Inst inst) {
            // A ZERO POINT ANNIHILATES (0a = 0, Marko 2026-11-10): the inst's output, times zero, is nothing for any
            // lhs, so the inst is neither resolved nor run
            if (inst.isNoObj())
                return noobj();
            // A SPAN IS A RUN, COMPUTED, NEVER WRITTEN INTO THE CODE (Marko, 2026-11-10): for each length i the span admits,
            // the lhs runs i steps of the inst without its span, and the runs' results append. {2,3}plus(1) at 1 is the
            // branch of its unrolls, plus(1).plus(1) and plus(1).plus(1).plus(1), merging at noobj: {3,4}. Each step keeps
            // the inst's point, as {2}plus{3}(34) is plus{3}(34).plus{3}(34) (Theorem 7), and the zero-step run is the lhs.
            // The code keeps the span, so a code pattern still reads {?}neg() as a run of none or one. An open span
            // ({+}, {*}) has no last run and still applies once
            final cInt span = inst.tid().b();
            if (!span.isOne() && null != span.min() && null != span.max() && 0 <= span.min()) {
                final Inst step = inst.tid(inst.tid().b(null));
                Obj result = noobj();
                for (long i = span.min(); i <= span.max(); i++) {
                    Obj current = lhs;
                    // a step that gathers, the swarm's barrier, reduces the whole stream: {1,2,3}.{0,1}sum() is {1,2,3,6}.
                    // Any other step maps over it obj by obj, as the swarm runs it, so a flatmap's stream feeds the next
                    // step one obj at a time
                    for (long j = 0; j < i; j++)
                        current = step.isGather() ? apply(current, step) : current.stream().map(o -> apply(o, step)).reduce(noobj(), Obj::append);
                    result = result.append(current);
                }
                return result;
            }
            // A ONE-MEMBER STREAM IS ITS MEMBER. `objs()` collapses a single element everywhere else, but a BARRIER
            // (a reducer/gather) accumulates its input into a stream, so a single upstream value arrives wrapped in a
            // one-member objs. Unwrap it, so the call is resolved against the value the reducer gathered rather than
            // against the wrapper.
            final Obj subject = lhs.isObjs() && lhs.c().isOne() && 1L == lhs.objsValue().spliterator().getExactSizeIfKnown()
                    ? lhs.objsValue().iterator().next()
                    : lhs;
            return invoke(subject, Selector.Helper.resolve(subject, inst), inst);
        }

        /**
         * The processor's full per-instruction runtime: the resolved inst's noobj/domain guards,
         * domain check (coefficient compression), computeArgs, function application, range check,
         * fail propagation, and coefficient policy.
         */
        public static Obj invoke(final Obj lhs, Inst cinst, final Inst original) {
            final boolean isMonadicInst = original.tid().hasQ(MONAD_IN) || original.tid().hasQ(MONAD_OUT);
            if (cinst.isNoObj())
                return unresolved(lhs, original);
            if (lhs.isNoObj() && !cinst.dom().c().isZeroable())
                return noobj();
            Obj clhs = lhs;
            boolean modulateC = false;
            if (Typer.Helper.instDom() && !isMonadicInst && !lhs.isFail() && !lhs.isCaughtFail()
                    && !Typer.Helper.domRngMatch(clhs, cinst.dom()) && clhs.unique()) {
                clhs = clhs.c(cInt::one);
                // bindQ is applied by the compiler (ScoringResolver.resolveInst); the resolved
                // inst already carries ?block, so no second bindQ is needed at apply time.
                cinst = Selector.Helper.resolve(clhs, original);
                modulateC = true;
                if (cinst.isNoObj())
                    return unresolved(lhs, original);
                if (!Typer.Helper.domRngMatch(clhs, cinst.dom()))
                    return transientFail(MTronException.of("lhs range does not match inst domain: %s => %s [%s]", clhs.rng(), cinst.dom(), cinst));
            }
            final Obj rhs = applyFunction(clhs, cinst, original, isMonadicInst);
            final cInt cc = cinst.c();
            return modulateC ? rhs.c(c -> c.mult(lhs.c()).mult(cc)) : rhs.c(c -> c.mult(cc));
        }

        /**
         * The answer to an inst that cannot be resolved against the lhs. The uncaught fails the lhs carries (the lhs
         * itself, or members of the stream a gather collected) pass it, as they pass an inst that resolves
         * ({@link #applyFunction}), coefficient included: "unable to locate inst-f fail::T" in their place would bury
         * the upstream cause. With none, the miss is the failure. A catch is never missed for a fail: its domain is any
         * obj, so it resolves against the fail and reaches {@link #applyFunction}.
         */
        private static Obj unresolved(final Obj lhs, final Inst original) {
            final List<Obj> failed = lhs.stream().filter(Obj::isUncaughtFail).toList();
            return failed.isEmpty() ?
                    transientFail(MTronException.of("unable to locate inst-f %s::T => %s", Obj.Helper.specificTypeId(lhs).name(), original)) :
                    objs(failed).c(c -> c.mult(original.c()));
        }

        /**
         * The first computed argument that is an uncaught fail, if any: positional args in order, then named values.
         */
        private static Optional<Obj> failedArg(final Poly<?, ?> args) {
            final Stream<Obj> values = args.isLst() ? args.lstValue().stream() : args.recValue().values().stream();
            return values.filter(Obj::isUncaughtFail).findFirst();
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
                                    "\n\t%-10s %s=> %s   |  \\_args", clhs.toCleanString(), cinst, clhs.type(), cinst.dom(), clhs.type(), cinst.args().elements().allMatch(clhs::test) ? "=" : "X", cinst.args());
                    }
                    final Inst cin1 = cinst;
                    final Obj clhs1 = clhs;
                    // A CALL THAT NAMES EVERY ARGUMENT KEYS ITS FRAME (Marko, 2026-11-08: names in any instruction): the
                    // body reads (plus(2)@a) as *a, as it reads (a=>plus(2)). The names are the arguments' own, read before
                    // they compute; the instruction's args stay positional for its function
                    final List<fURI> names = Inst.Helper.slotNames(cin1.args());
                    cinst = ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst_args, cin1.tid() + ""), () -> applyArgs(clhs1, cin1));
                    // an argument that failed to compute fails the inst, as a failed lhs already does: bound into an
                    // argument, the fail would be read as a plain value (false, nothing) and vanish
                    if (!cinst.isCatch() && !cinst.isBlocking()) {
                        final Optional<Obj> failed = Helper.failedArg(cinst.args());
                        if (failed.isPresent())
                            return failed.get();
                    }
                    final Inst computed = cinst;
                    Memory.argStack().push(null == names || names.size() != computed.args().count() ? computed.args() :
                            rec(IntStream.range(0, names.size()).mapToObj(i -> rel(uri(names.get(i)), computed.arg(i)))));
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
                        // AN ARGUMENT MISMATCH IS THE CONTRACT'S TO CLAIM, AND THE BODY'S EXCEPTION IS ALWAYS KEPT. The
                        // computed args are held to the types the contract DECLARES (Inst.Helper.admitsArgs), never to
                        // the args as written, which a computed one (a block, a deref, a nested chain) never matches: a
                        // write refused inside ref read "args do not match inst args", and the refusal was dropped
                        if (!Inst.Helper.admitsArgs(cinst, cinst)) {
                            // the declared types against the computed args, drawn as the type mismatch draws its diff
                            final Lst declared = lst(cinst.tid().args().stream().map(type -> (Obj) T(type)).toList());
                            throw MTronException.of(e, "args do not match inst args %s\n%s", declared,
                                    ObjmtronUISerializer.diagnostic().write(Poly.Helper.diffObjRecursion(cinst.args(), declared)));
                        }
                        throw MTronException.funnel(e, Inst.Helper.instContext(cinst));
                    } finally {
                        Memory.argStack().pop();
                    }
                } catch (final Exception e) {
                    rhs = transientFail(MTronException.of(e));
                }
                if (Typer.Helper.instRng() && !isMonadicInst && !rhs.isType() && !rhs.isFail() && !clhs.isCaughtFail()
                        && !Typer.Helper.domRngMatch(rhs, cinst.rng())) {
                    final Fail child = Poly.Helper.failChild(rhs);
                    final String text = "rhs does not match inst range -- " + Poly.Helper.mismatchText(cinst.rng(), rhs) + ":" + cinst;
                    rhs = null == child ? transientFail(MTronException.of(text)) : transientFail(MTronException.of(child.jvm(), text));
                }
            } else {
                rhs = clhs; // propagate fail through inst unless it's a catch inst
            }
            return rhs;
        }
    }
}
