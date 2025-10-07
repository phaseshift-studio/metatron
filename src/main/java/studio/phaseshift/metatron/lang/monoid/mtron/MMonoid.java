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
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.MTonoid;
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

    public MMonoid(final Quartet<Code, Objs, Lst, Objs> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public MTonoid resolve(final Obj lhs) {
        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code and generating structural monads:\n        [{{y}}PREPILED{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, prettyPrintCode(new StringBuilder(), this.code(), 0, 7).toString());
        Obj token = lhs;
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        fURI dom = null;
        fURI rng = null;
        for (final Inst inst : this.code().value()) {
            try {
                LOG.debug("   {{g}}=>{{/g}} resolving inst %s of %s", inst, null == token ? "[0]" : token);
                final Inst instB = inst.resolve(token);
                /*if(!resolvedCode.isEmpty()) {
                  final Inst instA = resolvedCode.remove(resolvedCode.size() - 1);
                  resolvedCode.add(instA.tid(instA.tid().rng(instB.tid().dom())));
                }*/
                if (null == dom)
                    dom = instB.tid().query().get(fURI.DOM.toString(), fURI.class);
                rng = instB.tid().query().get(fURI.RNG.toString(), fURI.class);
                resolvedCode.add(instB);
                token = instB.rng();
                if (instB.isInitial()) {
                    LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", instB);
                    token = instB.arg(0);
                    //this.running().append(MMonad.of(NoObj.single(), instB));
                } else if (instB.isGather()) {
                    // many-to-?
                    LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", instB);
                    final Monad m = MMonad.of(MObjs.of(new LinkedList<>(/*List.of(instB.seed())*/)), instB);
                    this.barriers().<LinkedList<Obj>>valueAs().add(m);
                }
                // LOG.none("%s", instB.rng().tid());
            } catch (final Exception e) {
                resolvedCode.add(inst);
                LOG.warn("runtime resolution of %s required: not enough context to determine inst", null == inst ? "[0]" : inst);
                //e.printStackTrace();
            }
        }
        final Code resolved = MCode.of(resolvedCode);//.tid(code().tid().query(fURI.DOM, Optional.ofNullable(dom).orElse(fURI.ANY.any())).query(fURI.RNG, Optional.ofNullable(rng).orElse(fURI.ANY.any())));
        LOG.debug("resolved monoidal code:\n        [{{g}}COMPILED{{/g}}]\n%s", prettyPrintCode(new StringBuilder(), resolved, 0, 7).toString());
        return this.code(resolved);
    }

    MTonoid compute() {
        final Code code = this.code();
        this.running().append(MMonad.of(NoObj.single(), code.inst(0)));
        while (true) {
            final Monad m = this.running().remove();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                try {
                    //final Obj no = m.obj().c()
                    final Monad n = m.liftC().apply(code.nextInst(m.inst())).dropC();
                    LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                    if (!n.dead()) {
                        if (n.halted()) {
                            LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                            n.obj().iterator().forEachRemaining(o -> this.halted().append(o));
                        } else if (n.inst().isGather()) {
                            final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().peek();
                            LOG.trace("{{m}}====|{{/m}} appending to barrier %s", n);
                            if (null == barrier)
                                throw MTronException.of("barrier should exist: %s", n.inst());
                            barrier.obj().append(n.obj());
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
                    throw MTronException.of(e, "unable to evaluate inst of %s", m);
                }
            } else if (!this.barriers().isEmpty()) {
                final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Inst nextInst = code.nextInst(barrier.inst());
                    final Obj result = barrier.inst().apply(barrier.obj());
                    if (nextInst.isGather()) { // barrier-to-barrier can do direct handoff of result set
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final Monad nextBarrier = this.barriers().<LinkedList<Monad>>valueAs().peek();
                        if (null == nextBarrier)
                            throw MTronException.of("barrier should exist: %s", nextInst);
                        nextBarrier.obj().append(result);
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
        final MMonoid code = (MMonoid) this.resolve(lhs);
        return code.compute().halted();
    }

    @Override
    public String toString() {
        return "MONOID[" + this.code() + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.vid, this.tid);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MTonoid && Objects.equals(this.value, ((MTonoid) other).value());
    }

    @Override
    public Quartet<Code, Objs, Lst, Objs> value() {
        return (Quartet<Code, Objs, Lst, Objs>) this.value;
    }

    @Override
    public MTonoid clone(Object value, fURI tid, fURI vid) {
        return new MMonoid((Quartet<Code, Objs, Lst, Objs>) value, tid, vid);
    }

    public static MMonoid of(final Code code) {
        return new MMonoid(Quartet.with(code, MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), MONOID_TID, fURI.NULL);
    }

    public static MMonoid of(final Obj start, final Code code) {
        final List<Inst> prepended = new ArrayList<>();
        prepended.add(MInst.instB(mtronInstSet.START_TID, MLst.of(start)));
        prepended.addAll(code.codeValue());
        return new MMonoid(Quartet.with(MCode.of(prepended), MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), MONOID_TID, fURI.NULL);
    }

}
