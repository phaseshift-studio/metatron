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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.START_TID;

public class MMonoid extends MObj implements Monoid {

    private static final GraphittyLogger LOG = Graphitty.log(MMonoid.class);

    public MMonoid(final Quartet<Code, Objs, Lst, Objs> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

  /*  @Override
    public Iterator<Obj> iterator() {
        List<Obj> results = new ArrayList<>();
        Obj m;
        while (null != (m = this.next())) {
            m.iterator().forEachRemaining(results::add);
        }
        return results.iterator();
    }

    private Obj next() {
        while (true) {
            if (this.halted().<List<Obj>>valueAs().isEmpty()) {
                if (this.running().<List<Obj>>valueAs().isEmpty())
                    return null;
                this.apply(NoObj.single());
            } else {
                final Obj end = this.halted().<Queue<Obj>>valueAs().poll();
                if (!end.isNoObj())
                    return end;
            }
        }
    }*/


    Code resolve(final Obj start) {
        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code and generating structural monads:\n\t%s {{g}}=>{{/g}} %s", start, this.code());
        Obj token = start;
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        for (final Inst inst : this.code().value()) {
            try {
                LOG.debug("{{g}}=>{{/g}} resolving inst %s", inst);
                final Inst instB = inst.resolve(Inst.Resolve.B, token);
                resolvedCode.add(instB);
                token = instB.rng();
                if (instB.isInitial()) {
                    LOG.debug("{{g}}==>{{/g}} creating initial monad at %s", instB);
                    token = instB.arg(0);
                } else if (instB.isGather()) {
                    // many-to-?
                    LOG.debug("{{g}}==>{{/g}} creating barrier monad at %s", instB);
                    final Monad m = MMonad.of(this, MObjs.of(new LinkedList<>()), instB);
                    this.barriers().<LinkedList<Obj>>valueAs().add(m);
                }
                // LOG.none("%s", instB.rng().tid());
            } catch (final Exception e) {
                resolvedCode.add(inst);
                LOG.warn("runtime resolution of %s required: %s", inst, e.getMessage());
                e.printStackTrace();
            }
        }
        final Code resolved = MCode.of(resolvedCode);
        LOG.debug("resolved monoidal code: %s", resolved);
        return resolved;
    }

    @Override
    public Obj apply(final Obj lhs) {
        final Code code = this.resolve(lhs);
        lhs.stream().forEach(o -> {
            this.running().<Queue<Monad>>valueAs().add(MMonad.of(this, o, code.inst(0)));
        });
        while (true) {
            final Monad m = this.running().<LinkedList<Monad>>valueAs().poll();
            if (null != m) {
                LOG.trace("{{g}}=>{{/g}} processing monad %s", m);
                if (m.inst().isInitial()) {

                }
                // no need to check isGather -- simply determine whether to flatten the monad or not and everything should consequence from that
                (m.inst().isInitial() ? NoObj.single() : m.obj()).stream().forEach(o -> {
                    LOG.trace("{{g}}==>{{/g}} processing obj %s at %s [%s]", o, m.inst(), m.inst().isInitial() ? "initial" : "midway");
                    final Monad n = m.obj(o).apply(code.next(m.inst()));
                    LOG.trace("{{g}}===>{{/g}} post-processing monad %s", n);
                    if (!n.dead()) {
                        if (n.halted()) {
                            LOG.trace("{{g}}====>{{/g}} halting monad %s", n);
                            n.obj().stream().forEach(p -> this.halted().<Queue<Obj>>valueAs().add(p));
                        } else if (n.inst().isGather()) {
                            final Monad barrier = this.barriers().<List<Monad>>valueAs().get(0);
                            LOG.trace("{{g}}====>{{/g}} appending to barrier %s", n);
                            if (null == barrier)
                                throw MTronException.of("barrier should exist: %s", n.inst());
                            barrier.obj().append(n.obj());
                        } else {
                            LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                            this.running().<LinkedList<Monad>>valueAs().add(n);
                        }
                    }
                });
            } else if (!this.barriers().isEmpty()) {
                final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().poll();
                if (null != barrier) {
                    LOG.trace("{{m}}=>{{/m}} processing barrier monad %s", barrier);
                    final Inst nextInst = code.next(barrier.inst());
                    final Obj result = barrier.inst().apply(barrier.obj());
                    if (nextInst.dom().tid().coefficientValue().isOne())
                        result.forEach(o -> {
                            LOG.trace("{{m}}==>{{/m}} scattering output barrier obj %s", o);
                            this.running().<LinkedList<Monad>>valueAs().add(MMonad.of(this, o, nextInst));
                        });
                    else if (nextInst.dom().tid().coefficientValue().isZero()) {
                        this.running().<LinkedList<Monad>>valueAs().add(MMonad.of(this, NoObj.single(), nextInst));
                    } else {
                        LOG.trace("{{m}}==>{{/m}} passing output barrier obj %s", result);
                        this.running().<LinkedList<Monad>>valueAs().add(MMonad.of(this, result, nextInst));
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
    public Quartet<Code, Objs, Lst, Objs> value() {
        return (Quartet<Code, Objs, Lst, Objs>) this.value;
    }

    @Override
    public Monoid clone(Object value, fURI tid, fURI vid) {
        return new MMonoid((Quartet<Code, Objs, Lst, Objs>) value, tid, vid);
    }

    public static MMonoid of(final Code code) {
        return new MMonoid(Quartet.with(code, MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), fURI.of("monoid:tid"), fURI.NULL);
    }

    public static MMonoid of(final Obj start, final Code code) {
        final List<Inst> prepended = new ArrayList<>();
        prepended.add(MInst.instB(START_TID, MLst.of(start)));
        prepended.addAll(code.codeValue());
        return new MMonoid(Quartet.with(MCode.of(prepended), MObjs.of(new LinkedList<>()), MLst.of(new LinkedList<>()), MObjs.of(new LinkedList<>())), fURI.of("monoid:tid"), fURI.NULL);
    }

}
