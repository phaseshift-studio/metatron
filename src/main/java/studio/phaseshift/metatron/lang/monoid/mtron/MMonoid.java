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

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.FROM_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.START_TID;

public class MMonoid extends MObj implements Monoid {

    private static final GraphittyLogger LOG = Graphitty.log(MMonoid.class);

    public MMonoid(final Quartet<Code, Objs, Lst, Objs> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);

        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code and generating structural monads: %s", this.code());
        Obj token = NoObj.single();
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        for (final Inst inst : this.code().value()) {
            try {
                //LOG.none("=> %s", inst.dom().tid());
                final Inst instB = inst.resolve(Inst.Resolve.B, token);
                token = instB.rng();
                if (instB.f().form().isInitial() || instB.tid().queryless().equals(START_TID) || instB.tid().queryless().equals(FROM_TID)) {
                    LOG.debug("creating initial monad at %s", instB);
                    final Monad m = MMonad.of(this, NoObj.single(), instB); // TODO: use seed
                    this.running().<LinkedList<Obj>>valueAs().add(m);
                    token = instB.arg(0);
                } else if (instB.f().form().isGather()) {
                    // many-to-?
                    LOG.debug("creating barrier monad at %s", instB);
                    final Monad m = MMonad.of(this, instB.seed(), instB);
                    this.barriers().<List<Obj>>valueAs().add(m);
                }
                resolvedCode.add(instB);
                // LOG.none("%s", instB.rng().tid());
            } catch (final Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        this.code().value().clear();
        this.code().value().addAll(resolvedCode);
        this.running().<LinkedList<Obj>>valueAs().add(MMonad.of(this, NoObj.single(), this.code().inst(0)));

    }

    @Override
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
    }


    @Override
    public Obj apply(final Obj lhs) {
        if ((!this.running().<LinkedList<Monad>>valueAs().isEmpty() || !this.barriers().isEmpty())) {
            if (!this.running().<LinkedList<Monad>>valueAs().isEmpty()) {
                final Monad m = this.running().<LinkedList<Monad>>valueAs().poll();
                 m.apply(NoObj.single());
            } else if (!this.barriers().isEmpty()) { // TODO
                final Monad barrier = this.barriers().<LinkedList<Monad>>valueAs().poll();
                barrier.apply(lhs);
            }
        }
        return NoObj.single();
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
        return new MMonoid(Quartet.with(code, MObjs.of(new LinkedList<>()), MLst.of(), MObjs.of(new LinkedList<>())), fURI.of("monoid:tid"), fURI.NONE);
    }

    public static MMonoid of(final Obj start, final Code code) {
        final List<Inst> prepended = new ArrayList<>();
        prepended.add(MInst.instB(START_TID, MLst.of(start)));
        prepended.addAll(code.codeValue());
        return new MMonoid(Quartet.with(MCode.of(prepended), MObjs.of(new LinkedList<>()), MLst.of(), MObjs.of(new LinkedList<>())), fURI.of("monoid:tid"), fURI.NONE);
    }

}
