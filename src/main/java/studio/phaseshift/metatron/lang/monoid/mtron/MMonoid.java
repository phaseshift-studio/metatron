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

package studio.phaseshift.metatron.lang.monoid.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.MTonoid;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.MInstSet.MTRON_TID;
import static studio.phaseshift.metatron.ui.ObjStringSerializer.prettyPrintCode;
import static studio.phaseshift.metatron.util.Tuple.Quartet;

;

public class MMonoid extends MObj implements MTonoid {

    public static final fURI MONOID_TID = MTRON_TID.extend("lang/monoid");

    private final GraphittyLogger LOG = Graphitty.log(this);

    public static void load() {
        //  Router.global().write(MONOID_TID,T(MONOID_TID));
        //  Router.global().write(MMonad.MMONAD_TID, T(MMonad.MMONAD_TID));
    }

    // code running barriers halted
    public MMonoid(final Quartet<Code, Obj, Lst, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public MTonoid resolve(final Obj lhs) {
        final Code resolvedCode = this.code().resolve(lhs);
        final MTonoid mt = this.code(resolvedCode);
        for (final Inst inst : mt.code().value()) {
            if (inst.isInitial()) {
                LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", inst);
                this.running().append(MMonad.of(NoObj.single(), inst));
            } else if (inst.isGather()) {
                // many-to-?
                LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", inst);
                final Monad m = MMonad.of(MObjs.empty(), inst);
                mt.barriers().<LinkedList<Obj>>valueAs().add(m);
            }
        }
        return mt;
    }

    MTonoid compute() {
        final Code code = this.code();
        if(this.running().c().isZero())
            this.running().append(MMonad.of(NoObj.single(),code.insts().get(0)));
        while (true) {
            final Monad m = (Monad) this.running().take();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                try {
                    //final Obj no = m.obj().c()
                    final Monad n = m.apply(code.nextInst(m.inst()));
                    LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                    if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isNoObjable())) {
                        if (n.inst().isGather()) {
                            final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().peek();
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
                    throw MTronException.of(e, "unable to evaluate %s", m);
                }
            } else if (!this.barriers().isEmpty()) {
                final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Obj result = barrier.inst().apply(barrier.obj());
                    final Inst nextInst = code.nextInst(barrier.inst());
                    if (nextInst.isGather()) { // barrier-to-barrier can do direct handoff of result set
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final Monad nextBarrier = this.barriers().<LinkedList<Monad>>valueAs().peek();
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
        return this;
    }

    @Override
    public Obj apply(final Obj lhs) {
        final MMonoid code =  (MMonoid) this.resolve(lhs);
        return code.compute().halted();
    }

    /*@Override
    public String toString() {
        return Obj.Helper.objToString(this);
    }*/

    /*@Override
    public int hashCode() {
        return Objects.hash(this.value, this.vid, this.tid);
    }*/

   /* @Override
    public boolean equals(final Object other) {
        return other instanceof MTonoid && Objects.equals(this.value, ((MTonoid) other).value());
    }*/

    @Override
    public Quartet<Code, Obj, Lst, Obj> value() {
        return (Quartet<Code, Obj, Lst, Obj>) this.value;
    }

    @Override
    public MTonoid clone(Object value, fURI tid, fURI vid) {
        return new MMonoid((Quartet<Code, Obj, Lst, Obj>) value, tid, vid);
    }

    public static MMonoid of(final Code code) {
        return new MMonoid(Quartet.with(code, MObjs.empty(), MLst.of(new LinkedList<>()), MObjs.empty()), MONOID_TID, fURI.NULL);
    }

    public static MMonoid of(final Obj start, final Code code) {
        if (!start.isNoObj()) {
            final List<Inst> prepended = new ArrayList<>();
            prepended.add(MInst.instB(mtronInstSet.START_TID, MLst.of(start)));
            prepended.addAll(code.codeValue());
            return new MMonoid(Quartet.with(MCode.of(prepended), MObjs.empty(), MLst.of(new LinkedList<>()), MObjs.empty()), MONOID_TID, fURI.NULL);
        } else {
            return MMonoid.of(code);
        }
    }

}
