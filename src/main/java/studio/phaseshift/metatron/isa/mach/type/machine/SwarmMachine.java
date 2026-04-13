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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.type.monad.BasicPCMonad.pcmonad;
import static studio.phaseshift.metatron.isa.mach.type.monad.BasicPCMonad.pcmonad;

;

public class SwarmMachine extends AbstractMachine implements Machine {

    public static final int MAX_FAILS = 10;
    public static final fURI MACH_SWARM_MACHINE_TID = MACH_MACHINE_TID.extend("swarm");
    public static final Type MACH_SWARM_MACHINE_TYPE = Type.Builder.build()
            .tid(MACH_MACHINE_TID)
            .vid(MACH_SWARM_MACHINE_TID)
            .constructor(machine -> SwarmMachine.machine(machine.jvm(), machine.tid(), machine.vid()))
            .create();

    // code / running / barriers / halted
    protected SwarmMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static SwarmMachine of(final Call code) {
        return new SwarmMachine(Map.of(uri(CODE), code.isCode() ? code.as() : new MCode(code.insts(), CODE_TID, null)), MACH_ISA_TID, null);
    }

    public static SwarmMachine machine(final Map<Obj, Obj> machineState, final fURI tid, final fURI vid) {
        return new SwarmMachine(machineState, tid, vid);
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
            return new SwarmMachine(Map.of(uri(CODE), MCode.of(prepended)), MACH_ISA_TID, null);
        } else {
            return SwarmMachine.of(code);
        }
    }

    @Override
    public Machine resolve(final Obj lhs) {
        final Code resolvedCode = this.code().resolve(lhs);
        final Machine mach = this.code(resolvedCode);
        for (final Inst inst : mach.code().jvm()) {
            if (inst.isInitial()) {
                LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", inst);
                this.running().append(pcmonad(noobj(), inst, resolvedCode));
            } else if (inst.isGather()) {
                // many-to-?
                LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", inst);
                final PCMonad m = pcmonad(objs0(), inst, resolvedCode);
                mach.barriers().<LinkedList<Obj>>jvmAs().add(m);
            }
        }
        return mach;
    }


    @Override
    public Obj apply(final Obj lhs) {
       // if (!this.paused.get()) {
            Router.global().stats().monadicStats().resetMonads();
            final Code code = this.resolve(lhs).code();
            if (this.running().c().isZero()) {
                this.running().append(pcmonad(noobj(), code.insts().getFirst(), code));
            }
      //  }
        while (this.doProcessing() && this.infiniteFailCounter.get() < MAX_FAILS) {
            final PCMonad m = (PCMonad) this.running().take();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                final PCMonad x = this.split(m);
                final PCMonad n = x.apply();
                LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                if (n.obj().isFail())
                    this.infiniteFailCounter.incrementAndGet();
                if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isZeroable())) {
                    if (n.inst().isGather()) {
                        final PCMonad barrier = this.barriers().<LinkedList<PCMonad>>jvmAs().peek();
                        LOG.trace("{{m}}====|{{/m}} appending living obj to barrier %s", n);
                        if (null == barrier)
                            throw MTronException.of("barrier should exist: %s", n.inst());
                        barrier.obj().append(n.obj());
                        Router.global().stats().monadicStats().incrBarrierMonads(1L);
                    } else {
                        this.running().append(n);
                    }
                } else if (!n.dead()) {
                    if (n.halted()) {
                        LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                        //this.processHalted(n.obj());
                        //this.halted().append(n.obj());
                        // n.obj().iterator().forEachRemaining(this::processHalted);
                        n.obj().iterator().forEachRemaining(no -> {
                            Router.global().stats().monadicStats().incrHaltedMonads(1L);
                            this.onHalt().accept(no);
                        });
                    } else {
                        LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                        n.obj().iterator().forEachRemaining(no -> {
                            this.running().append(n.obj(no));
                        });
                    }
                } else if (n.zombie() && n.inst().dom().c().isZeroable()) {
                    LOG.trace("{{c}}====>{{/c}} walking undead zombie monad %s", n);
                    this.running().append(n);
                } else {
                    Router.global().stats().monadicStats().incrKilledMonads(1L);
                    LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
                }
            } else if (!this.barriers().isEmpty()) {
                final PCMonad barrier = this.barriers().<LinkedList<PCMonad>>jvmAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Obj result = barrier.inst().apply(barrier.obj());
                    final Inst nextInst = code.nextInst(barrier.inst());
                    if (nextInst.isGather()) { // barrier-to-barrier can do direct handoff of result set
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final PCMonad nextBarrier = this.barriers().<LinkedList<PCMonad>>jvmAs().peek();
                        if (null == nextBarrier)
                            throw MTronException.of("barrier should exist: %s", nextInst);
                        nextBarrier.obj().append(result);
                    } else if (nextInst.isBatching()) {
                        Router.global().stats().monadicStats().incrBarrierMonads(-1L);
                        this.running().append(pcmonad(result, nextInst, code));
                        Router.global().stats().monadicStats().incrRunningMonads(1L);
                    } else { // barrier-to-other requires an unrolling of result set
                        LOG.trace("  {{m}}==|{{/m}} scattering barrier obj %s to %s", result, nextInst);
                        result.forEach(o -> {
                            final PCMonad n = pcmonad(o, nextInst, code);
                            Router.global().stats().monadicStats().incrBarrierMonads(-1L);
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
        if (this.interrupted.get()) {
            return fail(MTronException.of(Graphitty.sillyPrint("machine interrupted", false, true)));
        } else if (this.infiniteFailCounter.get() >= MAX_FAILS) {
            return fail(MTronException.of(Graphitty.sillyPrint("machine failed", false, true)), fail(MTronException.of("infinite fail-loop detected"), fail("obj/inst coefficients yielding unsolvable monad")));
        } else if (this.paused.get()) {
            LOG.info(Graphitty.sillyPrint("machine paused", false, true));
            return noobj();
        } else
            return objs(this.halted());
    }
}
