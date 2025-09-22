/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.monoid.mtron;

import org.javatuples.Quartet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.MTRON_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.START_TID;

public class MMonoid extends MObj implements Monoid {

    public static final fURI MONOID_TID = MTRON_TID.extend("lang/monoid");

    private final GraphittyLogger LOG = Graphitty.log(this);

    public MMonoid(final Quartet<Code, Objs, Lst, Objs> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    Code resolve(final Obj start) {
        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code and generating structural monads:\n\t%s {{g}}=>{{/g}} %s", start, this.code());
        Obj token = start;
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        fURI dom = null;
        fURI rng = null;
        for (final Inst inst : this.code().value()) {
            try {
                LOG.debug("   {{g}}=>{{/g}} resolving inst %s", inst);
                final Inst instB = inst.resolve(Inst.Resolve.B, token);
                if (null == dom)
                    dom = instB.tid().queryValue(fURI.DOM, fURI.class);
                rng = instB.tid().queryValue(fURI.RNG, fURI.class);
                resolvedCode.add(instB);
                token = instB.rng();
                if (instB.isInitial()) {
                    LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", instB);
                    token = instB.arg(0);
                    //this.running().append(MMonad.of(NoObj.single(), instB));
                } else if (instB.isGather()) {
                    // many-to-?
                    LOG.trace("  {{g}}==>{{/g}} creating {{y}}barrier{{/y}} monad at %s", instB);
                    final Monad m = MMonad.of(MObjs.of(new LinkedList<>(/*List.of(instB.seed())*/)), instB);
                    this.barriers().<LinkedList<Obj>>valueAs().add(m);
                }
                // LOG.none("%s", instB.rng().tid());
            } catch (final Exception e) {
                resolvedCode.add(inst);
                LOG.warn("runtime resolution of %s required: %s", inst, e.getMessage());
                //e.printStackTrace();
            }
        }
        final Code resolved = MCode.of(resolvedCode).tid(code().tid().query(fURI.DOM, Optional.ofNullable(dom).orElse(fURI.ANY)).query(fURI.RNG, Optional.ofNullable(rng).orElse(fURI.ANY)));
        LOG.debug("resolved monoidal code: %s", resolved);
        return resolved;
    }

    @Override
    public Obj apply(final Obj lhs) {
        final Code code = this.resolve(lhs);
        this.running().append(MMonad.of(NoObj.single(), code.inst(0)));
        while (true) {
            final Monad m = this.running().<LinkedList<Monad>>valueAs().poll();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                final Monad n = m.apply(code.nextInst(m.inst()));
                LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                if (!n.dead()) {
                    if (n.halted()) {
                        LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                        n.obj().iterator().forEachRemaining(p -> this.halted().<Queue<Obj>>valueAs().add(p));
                    } else if (n.inst().isGather()) {
                        final Monad barrier = this.barriers().<List<Monad>>valueAs().get(0);
                        LOG.trace("{{m}}====>{{/m}} appending to barrier %s", n);
                        if (null == barrier)
                            throw MTronException.of("barrier should exist: %s", n.inst());
                        barrier.obj().append(n.obj());
                    } else {
                        LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                        n.obj().iterator().forEachRemaining(no -> this.running().<LinkedList<Monad>>valueAs().add(n.obj(no)));
                    }
                } else if (n.zombie()) {
                    LOG.trace("{{c}}====>{{/c}} walking undead zombie monad %s", n);
                    this.running().<LinkedList<Monad>>valueAs().add(n);
                } else {
                    LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
                }
            } else if (!this.barriers().isEmpty()) {
                final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}|={{/m}} processing barrier monad %s", barrier);
                    final Inst nextInst = code.nextInst(barrier.inst());
                    final Obj result = barrier.inst().apply(barrier.obj());
                    if (nextInst.dom().tid().coefficientValue().isOne())
                        result.forEach(o -> {
                            LOG.trace("  {{m}}|==>{{/m}} scattering output barrier obj %s", o);
                            this.running().<LinkedList<Monad>>valueAs().add(MMonad.of(o, nextInst));
                        });
                    else {
                        LOG.trace("  {{m}}|==>{{/m}} passing output barrier obj %s", result);
                        this.running().<LinkedList<Monad>>valueAs().add(MMonad.of(result, nextInst));
                    }
                }
            } else {
                LOG.trace("{{b}}monad {{g}}processing completed{{X}}");
                break;
            }
        }
        return this.halted();
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
        return other instanceof Monoid && Objects.equals(this.value, ((Monoid) other).value());
    }

    @Override
    public Quartet<Code, Objs, Lst, Objs> value() {
        return (Quartet<Code, Objs, Lst, Objs>) this.value;
    }

    @Override
    public Monoid clone(Object value, fURI tid, fURI vid) {
        return new MMonoid((Quartet<Code, Objs, Lst, Objs>) value, tid, vid);
    }

    public static MMonoid of(final Code code) {
        return new MMonoid(Quartet.with(code, MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), MONOID_TID, fURI.NULL);
    }

    public static MMonoid of(final Obj start, final Code code) {
        final List<Inst> prepended = new ArrayList<>();
        prepended.add(MInst.instB(START_TID, MLst.of(start)));
        prepended.addAll(code.codeValue());
        return new MMonoid(Quartet.with(MCode.of(prepended), MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), MONOID_TID, fURI.NULL);
    }

}
