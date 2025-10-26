/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.vm;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;
import studio.phaseshift.metatron.vm.util.RunningMonads;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static studio.phaseshift.metatron.util.MTronException.mexcept;
import static studio.phaseshift.metatron.util.Tuple.Quartet;
import static studio.phaseshift.metatron.vm.machInstSet.MTRON_MACH_TID;

;

public class MMachine extends MObj implements Machine {

    private final GraphittyLogger LOG = Graphitty.log(this);

    // code running barriers halted
    public MMachine(final Quartet<Code, Obj, Lst, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public static Machine of(final Code code) {
        return new MMachine(Quartet.with(code, RunningMonads.of(), MLst.of(new LinkedList<>()), MObjs.empty()), MTRON_MACH_TID, fURI.NULL);
    }


    public static Machine of(final Obj start, final Code code) {
        if (!start.isNoObj()) {
            final List<Inst> prepended = new ArrayList<>();
            prepended.add(MInst.instB(mtronInstSet.START_TID, MLst.of(start)));
            prepended.addAll(code.codeValue());
            return new MMachine(Quartet.with(MCode.of(prepended), RunningMonads.of(), MLst.of(new LinkedList<>()), MObjs.empty()), MTRON_MACH_TID, fURI.NULL);
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
                this.running().append(MMonad.of(NoObj.single(), inst));
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
        final Tuple.Pair<Obj, Obj> pair = monad.obj().take(monad.inst());
        if (!pair.get1().isNoObj())
            this.running().append(monad.obj(pair.get1()));
        LOG.trace("   {{g}}=>{{/g}} splitting monad %s / %s (inst: %s)", pair.get0(), pair.get1(), monad.inst());
        return monad.obj(pair.get0());
    }

    @Override
    public Obj apply(final Obj lhs) {
        final Code code = this.resolve(lhs).code();
        if (this.running().c().isZero())
            this.running().append(MMonad.of(NoObj.single(), code.insts().get(0)));
        while (true) {
            final Monad m = (Monad) this.running().take();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                try {
                    final Monad x = this.split(m);
                    final Monad n = x.apply(code.nextInst(x.inst()));
                    LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                    if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isNoObjable())) {
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
                            n.obj().iterator().forEachRemaining(o -> this.halted().append(o));
                        } else {
                            LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                            n.obj().iterator().forEachRemaining(no -> this.running().append(n.obj(no)));
                        }
                    } else if (n.zombie() && n.inst().dom().c().isNoObjable()) {
                        LOG.trace("{{c}}====>{{/c}} walking undead zombie monad %s", n);
                        this.running().append(n);
                    } else {
                        LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
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
        //return this;
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
