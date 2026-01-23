/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.core.mach.type.impl;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MCode;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInst;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;
import studio.phaseshift.metatron.lang.core.mach.type.Machine;
import studio.phaseshift.metatron.lang.core.mach.type.Monad;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.CODE_TID;
import static studio.phaseshift.metatron.lang.core.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.mach.machInstSet.DROP_TID;
import static studio.phaseshift.metatron.lang.core.mach.machInstSet.MACH_INSTSET_TID;
import static studio.phaseshift.metatron.util.MTronException.mexcept;
import static studio.phaseshift.metatron.util.Tuple.Quartet;

;

public class MMachine extends MObj implements Machine {

    private final GraphittyLogger LOG = Graphitty.log(this);
    private Consumer<Obj> onHalt = o -> this.halted().append(o);
    public static Supplier<Obj> RUNNING_SUPPLIER = ListMonad::of;
    public boolean interrupted = false;

    // code / running / barriers / halted
    public MMachine(final Quartet<Code, Obj, Lst, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public static Machine of(final Call code) {
        return new MMachine(Quartet.with(code.isInst() ? new MCode(List.of(code.as()), CODE_TID, fURI.fnull) : code.as(), RUNNING_SUPPLIER.get(), lst(new LinkedList<>()), MObjs.empty()), MACH_INSTSET_TID, fURI.fnull);
    }

    public Machine onHalt(final Consumer<Obj> onHalt) {
        this.onHalt = onHalt;
        return this;
    }

    public Consumer<Obj> onHalt() {
        return this.onHalt;
    }


    public static Machine of(final Obj start, final Code code) {
        if (!start.isNoObj()) {
            final List<Inst> prepended = new ArrayList<>();
            prepended.add(MInst.instB(mInstSet.START_INST_TID, lst(start)));
            prepended.addAll(code.codeValue());
            return new MMachine(Quartet.with(MCode.of(prepended), RUNNING_SUPPLIER.get(), lst(new LinkedList<>()), MObjs.empty()), MACH_INSTSET_TID, fURI.fnull);
        } else {
            return MMachine.of(code);
        }
    }

    @Override
    public Machine resolve(final Obj lhs) {
        final Code resolvedCode = this.code().resolve(lhs);
        final Machine mach = this.code(resolvedCode);
        for (final Inst inst : mach.code().jvm()) {
            if (inst.isInitial()) {
                LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", inst);
                this.running().append(MMonad.of(noobj(), inst));
            } else if (inst.isGather()) {
                // many-to-?
                LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", inst);
                final Monad m = MMonad.of(MObjs.empty(), inst);
                mach.barriers().<LinkedList<Obj>>jvmAs().add(m);
            }
        }
        return mach;
    }

    protected Monad split(final Monad monad) {
        if (monad.obj().unique() && monad.inst().dom().c().isOne() || monad.inst().dom().c().isAny())
            return monad;
        final Tuple.Pair<Obj, Obj> pair = monad.obj().take(cInt.of(monad.inst().dom().c().max()));
        if (!pair.get1().isNoObj())
            this.running().append(monad.obj(pair.get1()));
        LOG.trace("   {{g}}=>{{/g}} splitting monad %s / %s (inst: %s)", pair.get0(), pair.get1(), monad.inst());
        return monad.obj(pair.get0());
    }

    public void interrupt() {
        this.interrupted = true;
    }

    @Override
    public Obj apply(final Obj lhs) {
        final Code code = this.resolve(lhs).code();
        if (this.running().c().isZero())
            this.running().append(MMonad.of(noobj(), code.insts().getFirst()));
        while (!this.interrupted) {
            final Monad m = (Monad) this.running().take();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                try {
                    final Monad x = this.split(m);
                    if (x.inst().tid().basePath().equals(DROP_TID)) {
                        this.running().append(MMonad.of(x, code.nextInst(x.inst())));
                    } else {
                        final Monad n = x.apply(code.nextInst(x.inst()));
                        LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                        if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isZeroable())) {
                            if (n.inst().isGather()) {
                                final Monad barrier = this.barriers().<LinkedList<Monad>>jvmAs().peek();
                                LOG.trace("{{m}}====|{{/m}} appending living obj to barrier %s", n);
                                if (null == barrier)
                                    throw MTronException.of("barrier should exist: %s", n.inst());
                                barrier.obj().append(n.obj());
                            } else {
                                this.running().append(n);
                            }
                        } else if (!n.dead()) {
                            if (n.halted()) {
                                LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                                // n.obj().iterator().forEachRemaining(this::processHalted);
                                n.obj().iterator().forEachRemaining(this.onHalt());
                            } else {
                                LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                                n.obj().iterator().forEachRemaining(no -> this.running().append(n.obj(no)));
                            }
                        } else if (n.zombie() && n.inst().dom().c().isZeroable()) {
                            LOG.trace("{{c}}====>{{/c}} walking undead zombie monad %s", n);
                            this.running().append(n);
                        } else {
                            LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
                        }
                    }
                } catch (final Exception e) {
                    return mexcept("unable to evaluate %s", m).cause(e).asFail();
                }
            } else if (!this.barriers().isEmpty()) {
                final Monad barrier = this.barriers().<LinkedList<Monad>>jvmAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Obj result = barrier.inst().apply(barrier.obj());
                    final Inst nextInst = code.nextInst(barrier.inst());
                    if (nextInst.isGather()) { // barrier-to-barrier can do direct handoff of result set
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final Monad nextBarrier = this.barriers().<LinkedList<Monad>>jvmAs().peek();
                        if (null == nextBarrier)
                            throw MTronException.of("barrier should exist: %s", nextInst);
                        nextBarrier.obj().append(result);
                    } else if (nextInst.isBatching()) {
                        this.running().append(MMonad.of(result, nextInst));
                    } else { // barrier-to-other requires an unrolling of result set
                        LOG.trace("  {{m}}==|{{/m}} scattering barrier obj %s to %s", result, nextInst);
                        result.forEach(o -> {
                            final Monad n = MMonad.of(o, nextInst);
                            LOG.trace(" {{m}}===|{{/m}} scattering %s", n);
                            this.running().append(n);
                        });
                    }
                }
            } else {
                LOG.trace("{{b}}monad {{g}}processing completed{{X}}");
                break;
            }
        }
        if (this.interrupted) {
            LOG.warn(Graphitty.sillyPrint("process interrupted", true, true));
            return noobj();
        } else
            return this.halted();
    }

    @Override
    public Quartet<Code, Obj, Lst, Obj> jvm() {
        return (Quartet<Code, Obj, Lst, Obj>) this.jvm;
    }

    @Override
    public Machine clone(Object jvm, fURI tid, fURI vid) {
        return new MMachine((Quartet<Code, Obj, Lst, Obj>) jvm, tid, vid);
    }
}
