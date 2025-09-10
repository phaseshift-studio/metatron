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

package studio.phaseshift.metatron.lang.monoid;

import org.apache.commons.collections.IteratorUtils;
import org.jline.jansi.Ansi.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.rewrite.decoration.ExplainRewrite;
import studio.phaseshift.metatron.lang.obj.base.*;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;

import static org.jline.jansi.Ansi.ansi;
import static studio.phaseshift.metatron.lang.inst.SInst.COUNT_URI;
import static studio.phaseshift.metatron.lang.inst.SInst.SUM_URI;

public class MMonoid {

    public static class MMonad implements Monad {
        private static final Logger LOG = LoggerFactory.getLogger(MMonad.class);
        Obj obj;
        Inst inst;
        long bulk = 1;
        long loops = 0;
        MMonoid monoid;

        public MMonad(final MMonoid monoid, final Obj obj, final Inst inst, final long bulk) {
            this.monoid = monoid;
            this.obj = obj;
            this.inst = inst;
            this.bulk = bulk;
        }

        @Override
        public Obj obj() {
            return this.obj;
        }

        @Override
        public Inst inst() {
            return this.inst;
        }

        @Override
        public long bulk() {
            return this.bulk;
        }

        @Override
        public void run() {
            // System.out.println(this);
            if (this.halted()) {
                if (!this.dead()) {
                    this.halt();
                }
            } else {
                if (!Set.of(COUNT_URI, SUM_URI).contains(inst.tid())) {
                    LOG.debug("processing inst {} with {}", this.inst, IteratorUtils.toList(this.obj.iterator()));
                    IteratorUtil.iterate(IteratorUtil.consume(this.obj.iterator(), o -> {
                        final MMonad m = new MMonad(this.monoid, o, this.inst, this.bulk);
                        m.domain_loop(m.inst);
                    }));
                } else {
                    LOG.debug("processing barrier {} with {}", this.inst, IteratorUtils.toList(this.obj.iterator()));
                    this.domain_loop(this.inst);
                }
            }
        }

        /// ////////////////////////////////////////////////////////////////////////

        public void domain_loop(final Inst inst) {
            // LOG_WRITE(TRACE, this->processor_,
            //     L(FOS_TAB_2"monad at !gdomain!! of {} !m=>!! {} [!m{}!!]\n", this->toString(),
            //      current_inst_resolved -> toString(), "SIGNATURE HERE"));

            if (Set.of(COUNT_URI, SUM_URI).contains(inst.tid())) {
                if (this.obj.isObjs()) {
                  /*  LOG_WRITE(TRACE, this->processor_,
                            L("barrier monad [size: {}] fetch for processing by {} [!m{}!m]\n",
                                    this->obj->objs_value()->size(), current_inst_resolved->toString(), "SIGNATURE HERE"));*/
                    this.range_loop(inst.apply(this.obj), inst);
                } else {
                    MMonad barrier = this.monoid.barriers.isEmpty() ? new MMonad(this.monoid, new MObjs(List.of()), inst, 1) : this.monoid.barriers.remove();
                    if (!barrier.obj.isObjs()) {
                        LOG.warn("barrier does not contain and objs: {}", barrier.obj);
                        barrier.obj = new MObjs(List.of(barrier.obj), Objs.TID, fURI.NONE);
                    }

                    // TODO:!!! APPEND  this.monoid.barriers.add(new MMonad(this.monoid, barrier.obj.<Objs>as().append(this.obj), this.inst, this.bulk));

                  /*  LOG_WRITE(TRACE, this->processor_,
                            L("monad {} stored in barrier [size: {}] [!m{}!m]\n", this->toString(),
                            this->processor_ -> barriers_ -> front()->obj -> objs_value()->size(), "SIGNATURE HERE"));*/
                }
            } else {
                this.range_loop(inst.apply(this.obj), inst);
            }

          /*  if (inst.is_gather() ) {
                if (this->obj -> is_objs()){
                    LOG_WRITE(TRACE, this->processor_,
                            L("barrier monad [size: {}] fetch for processing by {} [!m{}!m]\n",
                                    this->obj -> objs_value()->
                    size(), current_inst_resolved -> toString(), "SIGNATURE HERE"));
                    range_loop(current_inst_resolved -> apply(this->obj),current_inst_resolved);
                } else{
                    this->processor_ -> barriers_ -> front()->obj -> add_obj(this->obj);
                    LOG_WRITE(TRACE, this->processor_,
                            L("monad {} stored in barrier [size: {}] [!m{}!m]\n", this->toString(),
                            this->processor_ -> barriers_ -> front()->obj -> objs_value()->size(), "SIGNATURE HERE"));
                }
            } else {*/
            //  System.out.println("evaluating " + this + "---" + inst);

            //}
        }

        /// ////////////////////////////////////////////////////////////////////////

        public void range_loop(final Obj nextObj, final Inst inst) {
           /* LOG_WRITE(TRACE, this->processor_,
                L(FOS_TAB_2"monad at !grange!! of %s !m=>!! %s [%s]\n",
                        this->processor_ -> M(next_obj, this->inst)->toString(), current_inst_resolved -> toString(),
                "SIGNATURE HERE"));
            //    next_obj->CHECK_OBJ_TO_INST_SIGNATURE(current_inst_resolved, false);*/
            //   System.out.println("..." + inst);

          /*  if (next_inst -> is_generative()) {
                LOG_WRITE(TRACE, this->processor_, L("monad {} dying [{}]\n", this->toString().c_str(), "SIGNATURE HERE"))
                ;
            } else if (next_obj -> is_objs() && !next_inst -> is_gather()) {
                //   (is_scatter(current_inst_resolved->itype()) ||
                //    is_maybe_range(current_inst_resolved->itype()))) {
                LOG_WRITE(TRACE, this->processor_,
                        L("monad {} scattering [{}]\n", this->toString().c_str(), "SIGNATURE HERE"));
                for (const Obj_p & o: *next_obj -> objs_value()){
            const Monad_p m = this->processor_ -> M(o, next_inst);
                    LOG_WRITE(TRACE, this->processor_,
                            L("monad %s !r==!gmigrating!r==>!! %s\n", this->toString(), m -> toString()));
                    this->processor_ -> running_ -> push_back(m);
                }
            } else {*/
            final Inst nextInst = null; // TODO !!!!! this.monoid.code.nextInst(inst);
            if (Set.of(COUNT_URI, SUM_URI).contains(inst.tid())) {
                LOG.debug("scattering monad obj: {} (over {})", this.obj, nextInst);
                IteratorUtil.iterate(IteratorUtil.consume(nextObj.iterator(), o -> {
                    final MMonad m = new MMonad(this.monoid, o, nextInst, this.bulk);
                    this.monoid.running.add(m);
                }));
            } else {
                this.monoid.running.add(new MMonad(this.monoid, nextObj, nextInst, this.bulk));
            }
        }

        public boolean equals(final Object other) {
            return other instanceof MMonad &&
                    this.obj.equals(((MMonad) other).obj()) &&
                    this.inst.equals(((MMonad) other).inst());
        }

        public int hashCode() {
            return Objects.hash(this.obj, this.inst);
        }

        public void halt() {
            //   System.out.println("halting..." + this);
            this.inst = NoObj.single();
            this.monoid.halted.add(this.obj);
        /*for(int i = 0; i < this->bulk_; i++) {
          this->processor_->halted_->push_back(std::move(this->obj_->clone()));
        }*/
        }

        public boolean halted() {
            return this.inst.isNoObj();
        }

        public boolean dead() {
            return this.obj.isNoObj();
        }

        public String toString() {
            return ansi()
                    .fg(Color.RED)
                    .a("M")
                    .fg(Color.GREEN)
                    .a("[")
                    .a(this.obj)
                    .fg(Color.RED)
                    .a("@")
                    .a(this.inst)
                    .fg(Color.GREEN)
                    .a("]")
                    .reset()
                    .toString();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Monoid.class);
    protected Code code;

    // todo: barrier and running to use monad set
    Queue<MMonad> running = new LinkedList<>();
    Queue<MMonad> barriers = new LinkedList<>();
    Queue<Obj> halted = new LinkedList<>();

    public MMonoid(final Obj code) {
        this(code, NoObj.single());
    }

    public MMonoid(final Obj code, final Obj start) {
        if (!code.isCode()) {
            if (!code.isNoObj()) {
                this.halted.add(code);
            }
        } else {
            this.code = new ExplainRewrite().rewrite(code.<Code>as());
            // process bcode inst pipeline
            //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
            // setup global behavior around barriers, initials, and terminals
            boolean first = true;
            for (final Inst inst : this.code.value()) {
                try {
                    final Inst resolved = BInst.SymbolTable.resolve(NoObj.of(), inst);
                    if (Set.of(COUNT_URI, SUM_URI).contains(inst.tid())) {
                        // MANY_TO_??
                        LOG.debug("barrier inst found: {} (w/ seed {})", resolved, resolved.seed());
                        final MMonad m = new MMonad(this, resolved.seed(), inst, 1);
                        this.barriers.add(m);
                        //  LOG_WRITE(DEBUG, this, L(FOS_TAB_2"!ybarrier!! monad created: {}\n", m.toString()));
                    }/* else if (resolved.isInitial() || (first && resolved.isM())) {
                            // ZERO/MAYBE*-TO_??
              const Monad_p m = M(noobj(), inst); // TODO: use seed
                            this.running_.push_back(m);
                            LOG_WRITE(DEBUG, this, L(FOS_TAB_2"!ginitial!! monad created: {}\n", m.toString()));
                        }*/
                } catch (final Exception e) {
                    //do nothing
                    //throw new RuntimeException(e);
                }
                // first = false;
            }
            for (final Obj o : start) {
                this.running.add(new MMonad(this, o, this.code.get(0), 1));
            }
        }
    }

    @Override
    public Iterator<Obj> iterator() {
        List<Obj> results = new ArrayList<>();
        Obj m = null;
        while (null != (m = this.next())) {
            m.iterator().forEachRemaining(results::add);
        }
        return results.iterator();
    }

    public Obj next() {
        while (true) {
            if (this.halted.isEmpty()) {
                if (this.running.isEmpty())
                    return null;
                this.execute();
            } else {
                final Obj end = this.halted.poll();
                if (!end.isNoObj())
                    return end;
            }
        }
    }

    public void execute() {
        if ((!this.running.isEmpty() || !this.barriers.isEmpty())) {
            if (!this.running.isEmpty()) {
                final MMonad m = this.running.poll();
                m.run();
            } else if (!this.barriers.isEmpty()) { // TODO
                final MMonad barrier = this.barriers.poll();
                barrier.run();
            }
        }
    }

    @Override
    public String toString() {
        return "MONOID[" + this.code + "]";
    }
}
