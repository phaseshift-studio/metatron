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

package studio.phaseshift.metatron.isa.mach.type.processor.monad;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.processor.Monad;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

public interface StatefulMonad extends Monad<Lst> {

    // processor-stage timing (read by the profile() instruction): split = coefficient splitting,
    // apply = the instruction's f, next = the successor-inst lookup. MONADS/COEFF_SUM measure the
    // bulk-compression: monads propagated vs the total coefficient magnitude they carried.
    AtomicLong T_SPLIT = new AtomicLong(0);
    AtomicLong T_APPLY = new AtomicLong(0);
    AtomicLong T_NEXT = new AtomicLong(0);
    AtomicLong MONADS = new AtomicLong(0);
    AtomicLong COEFF_SUM = new AtomicLong(0);

    // per-instruction stats, keyed by the resolved inst's vid (its index in the code sequence)
    ConcurrentHashMap<fURI, AtomicLong> INST_MONAD_IN = new ConcurrentHashMap<>();
    ConcurrentHashMap<fURI, AtomicLong> INST_MONAD_OUT = new ConcurrentHashMap<>();
    ConcurrentHashMap<fURI, AtomicLong> INST_TIME = new ConcurrentHashMap<>();
    ConcurrentHashMap<fURI, AtomicLong> INST_COEFF_IN = new ConcurrentHashMap<>();
    ConcurrentHashMap<fURI, AtomicLong> INST_COEFF_OUT = new ConcurrentHashMap<>();

    static void resetTimings() {
        T_SPLIT.set(0);
        T_APPLY.set(0);
        T_NEXT.set(0);
        MONADS.set(0);
        COEFF_SUM.set(0);
        INST_MONAD_IN.clear();
        INST_MONAD_OUT.clear();
        INST_TIME.clear();
        INST_COEFF_IN.clear();
        INST_COEFF_OUT.clear();
    }

    /**
     * the coefficient magnitude of an obj — its exact multiplicity when it has one, else 1
     * (the bulk it carries as a single monad).
     */
    static long coeffMagnitude(final Obj obj) {
        final cInt c = obj.c();
        return null != c.min() && c.min().equals(c.max()) ? c.min() : 1L;
    }

    /**
     * diagnostic: record this monad's propagation — its coefficient magnitude (the number of
     * elements it carries when bulked) is added to the running sum, for the bulk-compression ratio.
     */
    default void countPropagation() {
        MONADS.incrementAndGet();
        final cInt c = this.obj().c();
        COEFF_SUM.addAndGet(null != c.min() && c.min().equals(c.max()) ? c.min() : 1L);
    }

    @Override
    StatefulMonad clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Lst jvm();

    default StatefulMonad nextInst() {
        return this.jvm(lst(CommonUtil.arrayList(this.obj(), this.code().nextInst(this.inst()), this.state(), this.code())));
    }

    default StatefulMonad next(final Obj obj) {
        final Code code = this.code();
        final Rec state = code.tid().hasQ(PATH) ? this.accruePath(obj) : this.state();
        final long t0 = System.nanoTime();
        final Inst nxt = code.nextInst(this.inst());
        final long t1 = System.nanoTime();
        T_NEXT.addAndGet(t1 - t0);
        final fURI curVid = this.inst().vid();
        if (null != curVid) {
            INST_MONAD_OUT.computeIfAbsent(curVid, k -> new AtomicLong()).incrementAndGet();
            INST_COEFF_OUT.computeIfAbsent(curVid, k -> new AtomicLong()).addAndGet(coeffMagnitude(obj));
        }
        final fURI nxtVid = nxt.vid();
        if (null != nxtVid) {
            INST_MONAD_IN.computeIfAbsent(nxtVid, k -> new AtomicLong()).incrementAndGet();
            INST_COEFF_IN.computeIfAbsent(nxtVid, k -> new AtomicLong()).addAndGet(coeffMagnitude(obj));
        }
        return this.jvm(lst(CommonUtil.arrayList(obj, nxt, state, code)));
    }

    /**
     * accrete the walked path — append the applied inst and its result obj to the path accumulator
     */
    default Rec accruePath(final Obj obj) {
        //return rec();
        return this.state().at(uri(PATH), this.path().add(this.inst()).add(obj)).as();
    }

    default Lst loopStack() {
        return this.state().at(LOOP).orElse(lst0()).asLst();
    }

    default Int loop() {
        final List<Obj> stack = this.loopStack().jvm();
        return stack.isEmpty() ? jnt(0) : jnt(stack.get(stack.size() - 1).intValue());
    }

    default StatefulMonad incrLoop(final int incr) {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        if (stack.isEmpty())
            stack.add(jnt(incr));
        else
            stack.set(stack.size() - 1, jnt(stack.get(stack.size() - 1).intValue() + incr));
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    default StatefulMonad pushLoop() {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        stack.add(jnt(0));
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    /**
     * Pop the loop counter stack — called when a repeat invocation exits so a
     * chained repeat starts its counter at 0.
     */
    default StatefulMonad popLoop() {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        if (!stack.isEmpty())
            stack.remove(stack.size() - 1);
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    default boolean isLoopback() {
        return this.state().at(uri(LOOPBACK)).orElse(BOOL_FALSE).boolValue();
    }

    default StatefulMonad loopback(final boolean loopback) {
        return this.state(this.state().at(uri(LOOPBACK), bool(loopback)).as());
    }

    default Rec state() {
        return this.jvm().asLst().jvm().get(2).orElse(rec());
    }

    default Inst inst() {
        return this.jvm().asLst().jvm().get(1).c(c -> c.mult(this.c())).as();
    }

    default Obj obj() {
        final Obj inner = this.jvm().asLst().jvm().getFirst();
        return inner.isObjs() ? inner : inner.c(c -> c.mult(this.c()));
    }

    default Code code() {
        return this.jvm().asLst().jvm().get(3).orElse(MCode.code(List.of(noobj()))).c(c -> c.mult(this.c())).as();
    }

    /**
     * the walked path — the accreted chain {@code [morph, obj, morph, obj, …]}
     */
    default Lst path() {
        return this.state().at(uri(PATH)).orElse(lst0()).asLst();
    }

    /**
     * the {@code monad_in=<uri>} read lens — the component space is a uri path:
     * <ul>
     *   <li>{@code obj}/{@code inst}/{@code code}/{@code state} — the four positional fields</li>
     *   <li>{@code state/<field>} — a nested read into the state rec (e.g. {@code state/loop}, {@code state/path})</li>
     *   <li>{@code +} — the whole monad's contents {@code [obj, inst, state, code]} (never the {@code PCMonad} object)</li>
     * </ul>
     */
    default Obj component(final fURI name) {
        if ("+".equals(name.name()))
            return this.jvm();
        final List<String> path = name.path();
        if ("state".equals(path.isEmpty() ? "" : path.getFirst()) && path.size() > 1) {
            final fURI field = name.tail(path.size() - 1);
            return switch (field.name()) {
                case "loop" -> this.loop();   // the loop counter (top of stack)
                case "path" -> this.path();   // the walked path
                default -> this.state().at(uri(field)).orElse(noobj());
            };
        }
        return switch (name.name()) {
            case "obj" -> this.obj();
            case "inst" -> this.inst();
            case "code" -> this.code();
            case "state" -> this.state();
            default -> this.state().at(uri(name)).orElse(noobj());
        };
    }

    /**
     * rebuild a monad from its contents {@code [obj, inst, state, code]} — the inverse of
     * {@link #component(fURI) component("+")}. This is the only way a {@code monad_in=+} lens
     * hands an instruction the monad's parts (as a plain list) without exposing the {@code PCMonad}.
     */
    static StatefulMonad of(final Obj contents) {
        final Lst jvm = contents.asLst();
        return BasicStatefulMonad.statefulMonad(
                jvm.jvm().getFirst(),
                jvm.jvm().get(1).as(),
                jvm.jvm().get(2).orElse(rec()),
                jvm.jvm().get(3).orElse(MCode.code(List.of(noobj()))));
    }


    @Override
    StatefulMonad tid(final fURI tid);

    @Override
    default StatefulMonad c(final cInt c) {
        return (StatefulMonad) Monad.super.c(c);
    }

    @Override
    default StatefulMonad c(final Function<cInt, cInt> func) {
        return (StatefulMonad) Monad.super.c(func);
    }

    @Override
    default StatefulMonad c(final Long exact) {
        return this.c(cInt.of(exact));
    }

    default StatefulMonad obj(final Obj obj) {
        return this.clone(lst(obj, this.inst(), this.state(), this.code()), this.tid(), this.vid());
    }

    default StatefulMonad inst(final Inst inst) {
        return this.clone(lst(this.obj(), inst, this.state(), this.code()), this.tid(), this.vid());
    }

    default StatefulMonad state(final Rec state) {
        return this.clone(lst(this.obj(), this.inst(), state, this.code()), this.tid(), this.vid());
    }


    @Override
    default Type dom() {
        return T(MACH_MONAD_TID);
    } // TODO: is this what we need?

    @Override
    default Type rng() {
        return T(MACH_MONAD_TID);
    }

    @Override
    StatefulMonad clone();

    @Override
    default Obj apply() {
        if (this.halted())
            return this;
        final fURI tid = this.inst().tid();
        // read lens: which component to feed the instruction (default obj)
        final fURI in = tid.hasQ(MONAD_IN) ? f(tid.q(MONAD_IN)) : null;
        final Obj input = null == in ? this.obj() : this.component(in);
        final long t0 = System.nanoTime();
        final Obj result = Processor.Helper.apply(input, this.inst());
        final long t1 = System.nanoTime();
        T_APPLY.addAndGet(t1 - t0);
        final fURI vid = this.inst().vid();
        if (null != vid) {
            INST_TIME.computeIfAbsent(vid, k -> new AtomicLong()).addAndGet(t1 - t0);
        }
        // write lens: where the result lands (default obj). '+' = the monad's new contents,
        // returned raw for the machine to re-queue; other write-targets are future work.
        if (tid.hasQ(MONAD_OUT) && "+".equals(f(tid.q(MONAD_OUT)).name())) {
            // terminal output: next() is never called for this inst (no successor), so record its
            // out-flow here — a gather (sum?int<=int{*}) emits one monad carrying its coefficient.
            final fURI outVid = this.inst().vid();
            if (null != outVid && !result.isNoObj()) {
                INST_MONAD_OUT.computeIfAbsent(outVid, k -> new AtomicLong()).incrementAndGet();
                INST_COEFF_OUT.computeIfAbsent(outVid, k -> new AtomicLong()).addAndGet(coeffMagnitude(result));
            }
            return result;
        }
        return this.next(result);
    }


}