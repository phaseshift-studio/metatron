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
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
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
         * The machine's single entry for applying one inst against a runtime lhs: take the subjects the typer names
         * ({@link Typer.Helper#bundleAndFibers} — the BUNDLE and its FIBERS, or the value alone when it is no
         * bundle), let the {@link Selector} say which instruction each one refers to
         * ({@link Selector.Helper#resolve}), and run them (guards, computeArgs, f().apply, checks, coefficient).
         * <p>
         * WHAT is a subject is NOT DECIDED HERE: a fibered type's bundle-vs-fiber choice is a type-system answer
         * ({@link Typer.Helper#applicable}, {@link Typer.Helper#supersedes}); this class executes what it names.
         */
        public static Obj apply(final Obj lhs, final Inst inst) {
            // A ONE-MEMBER STREAM IS ITS MEMBER. `objs()` collapses a single element everywhere else, but a BARRIER
            // (a reducer/gather) accumulates its input into a stream, so a single upstream value arrives wrapped in a
            // one-member objs. Unwrap it, or the delegation would be skipped for a wrapper that a reducer gathered
            // (`*marko.sum?<=real{*}` sums the reals at its fiber, not the wrapper).
            final Obj subject = lhs.isObjs() && lhs.c().isOne() && 1L == lhs.objsValue().spliterator().getExactSizeIfKnown()
                    ? lhs.objsValue().iterator().next()
                    : lhs;
            final List<Obj> candidates = Typer.Helper.bundleAndFibers(subject, inst);
            return candidates.size() == 1 ?
                    invoke(subject, Selector.Helper.resolve(subject, inst), inst) :
                    applyFiberedRec(subject, candidates, inst);
        }

        /**
         * A BUNDLE AND ITS FIBERS — applying an instruction to a fibered value is applying it to the bundle and to
         * the values of its fibers, summed.
         * <pre>
         * person[ninja]::[…].xyz()   ==   { person::[…], ninja::[…] }.xyz()
         * </pre>
         * WHAT that set holds is the {@link Typer}'s answer ({@link Typer.Helper#bundleAndFibers}): the bundle plus
         * the fibers its declaration exposes, one level deep. WHETHER the instruction is about each subject is the
         * Typer's too ({@link Typer.Helper#applicable}). This class keeps only the loop — ask the {@link Selector}
         * which instruction the subject refers to, invoke it.
         * <p>
         * The results are SUMMED as a multiplicity — the stream the fibered type promises — with {@code noobj} as the
         * identity ({@code {noobj, 29} = 29}). A subject the instruction is not about contributes nothing: the
         * identity, NOT a failure. A {@code fail::} from a subject the instruction DID apply to is not the identity
         * and propagates, or an instruction that broke on one fiber would read as a success.
         * <p>
         * THE BUNDLE SPEAKS FIRST: when the domain NAMES a type the bundle itself is, the bundle is the subject and
         * its fibers are not asked at all ({@link Typer.Helper#supersedes}). A domain that names no single subject —
         * a generic one or a base form, which is about every subject of that form — still sums over both levels.
         * <p>
         * A FILTER is the other form of the same precedence: it KEEPS OR DROPS its subject, and the subject is the
         * fibered rec itself — so when the bundle qualifies ({@code *marko.?weaponed::T} on a bundle that is
         * accepted as a weaponed through the ninja it exposes), the bundle IS the answer and its fibers are not
         * asked. When it does not qualify, the fibers are offered the filter as usual.
         */
        private static Obj applyFiberedRec(final Obj lhs, final List<Obj> candidates, final Inst inst) {
            final Inst bundleInst = Selector.Helper.resolve(lhs, inst);
            if (Typer.Helper.supersedes(lhs, bundleInst, inst))
                return invoke(lhs, bundleInst, inst);
            // the filter's verdict on the bundle is the answer when it passes; a decline (noobj) falls through, and
            // a fail is left to the loop below, which propagates it exactly as it does for any candidate
            if (Inst.Form.of(bundleInst) == Inst.Form.filter) {
                final Obj filtered = invoke(lhs, bundleInst, inst);
                if (!filtered.isNoObj() && !filtered.isFail())
                    return filtered;
            }
            final List<Obj> results = new ArrayList<>(candidates.size());
            for (final Obj candidate : candidates) {
                final Inst cinst = candidate == lhs ? bundleInst : Selector.Helper.resolve(candidate, inst);
                // NOT APPLICABLE IS THE IDENTITY, NOT A FAIL: a resolution miss, a resolution that could not
                // produce a callable instruction, or a domain about another subject contributes noobj.
                if (!Typer.Helper.applicable(candidate == lhs, candidate, cinst, inst))
                    continue;
                // invoke, not apply: the fan-out is ONE level, so a candidate that is itself a wrapper is applied
                // to as the object it is, never re-expanded into its own components
                final Obj rhs = invoke(candidate, cinst, inst);
                if (rhs.isUncaughtFail())
                    return rhs;
                if (!rhs.isNoObj())
                    results.add(rhs);
            }
            // objs() IS the multiplicity sum: empty is noobj (the identity), one is itself, and equal results
            // collapse into a coefficient (a bulked wrapper's parts count like any other stream)
            return objs(results.iterator());
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
