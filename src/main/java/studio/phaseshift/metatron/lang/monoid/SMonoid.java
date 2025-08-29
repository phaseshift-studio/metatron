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

import org.jline.jansi.Ansi.Color;
import studio.phaseshift.metatron.lang.inst.SInst;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.BObj.Code;
import studio.phaseshift.metatron.lang.obj.BObj.Inst;
import studio.phaseshift.metatron.lang.obj.BObj.NoObj;
import studio.phaseshift.metatron.lang.obj.BObj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import static org.jline.jansi.Ansi.ansi;

public class SMonoid {

    public static class Monad implements BMonoid.Monad {
        BObj.Obj obj;
        Inst inst;
        long bulk = 1;
        long loops = 0;
        Monoid monoid;

        public Monad(final Monoid monoid, final BObj.Obj obj, final Inst inst, final long bulk) {
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
                    //LOG_WRITE(TRACE, this->processor_, L("monad {} halting\n", this->toString()));
                    this.halt();
                }
            } else {
                // const Inst_p current_inst_resolved = TYPE_INST_RESOLVER(this->obj, this->inst);
                //final Inst currentInst = this->processor_ -> compiler_ -> resolve_inst(this->obj, this->inst);
                // LOG_WRITE(TRACE, this->processor_,
                //        L("monad {} applying to resolved inst {} !m=>!! {} [!m{}!!]\n", this->toString(),
                //       this->inst -> toString(), current_inst_resolved -> toString(), "SIGNATURE HERE"));
                if (!this.inst.isGather()) {
                    IteratorUtil.iterate(IteratorUtil.consume(this.obj.iterator(), o -> {
                        final Monad m = new Monad(this.monoid, o, this.inst, this.bulk);
                        m.domain_loop(m.inst);
                    }));
                } else {
                    this.domain_loop(this.inst);
                }
            }
        }

        /// ////////////////////////////////////////////////////////////////////////

        public void domain_loop(final Inst inst) {
            // LOG_WRITE(TRACE, this->processor_,
            //     L(FOS_TAB_2"monad at !gdomain!! of {} !m=>!! {} [!m{}!!]\n", this->toString(),
            //      current_inst_resolved -> toString(), "SIGNATURE HERE"));
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
            //  this->obj->CHECK_OBJ_TO_INST_SIGNATURE(current_inst_resolved, true);
            final Obj application = inst.apply(this.obj);
            //  System.out.println("=>" + application);
            this.range_loop(application, inst);
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
            final Inst temp = this.monoid.code.nextInst(inst);
            //  System.out.println(":::" + nextInst);
            final Inst nextInst = temp.isNoObj() ? temp : SInst.SYMBOL_TABLE.get(temp.type()).apply(temp.value().getValue0().iterator().next());


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
            if (!nextInst.isGather()) {
                IteratorUtil.iterate(IteratorUtil.consume(nextObj.iterator(), o -> {
                    final Monad m = new Monad(this.monoid, o, nextInst, this.bulk);
                    this.monoid.running.add(m);
                }));
            } else {
                this.monoid.running.add(new Monad(this.monoid, nextObj, nextInst, this.bulk));
            }
        }

        public boolean equals(final Obj other) {
            return other instanceof Monad &&
                    this.obj.equals(((Monad) other).obj()) &&
                    this.inst.equals(((Monad) other).inst());
        }

        public int hashCode() {
            return Objects.hash(this.obj, this.inst);
        }

        public void halt() {
            //   System.out.println("halting..." + this);
            this.inst = NoObj.of();
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
            return ansi().fg(Color.RED).a("M").fg(Color.GREEN).a("[").a(this.obj).fg(Color.RED).a("@").a(this.inst).fg(Color.GREEN).a("]").reset().toString();
        }
    }

    public static class Monoid implements BMonoid.Monoid {
        protected Code code;
        // unique_ptr<MonadSet> running_ = make_unique<MonadSet>();
        Queue<Monad> running = new LinkedList<>();
        Queue<Monad> barriers = new LinkedList<>();
        Queue<Obj> halted = new LinkedList<>();

        public Monoid(final Obj code) {
            if (!code.isCode()) {
                if (!code.isNoObj()) {
                    this.halted.add(code);
                }
            } else {
                this.code = code.<Code>as();
                // process bcode inst pipeline
                //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
                // setup global behavior around barriers, initials, and terminals
                boolean first = true;
                for (var obj : this.code.value()) {
                    try {
           /* const Inst_p resolved = TYPE_INST_RESOLVER(Obj::to_type(OBJ_FURI), inst);
            const Obj_p seed_copy = resolved.inst_seed(resolved);
                    if(resolved.is_gather()) {
                        // MANY_TO_??
              const Monad_p m = M(seed_copy, inst);
                        this.barriers_.push_back(m);
                        LOG_WRITE(DEBUG, this, L(FOS_TAB_2 "!ybarrier!! monad created: {}\n", m.toString()));
                    } else if(resolved.is_initial() || (first && resolved.is_map())) {
                        // ZERO/MAYBE*-TO_??
              const Monad_p m = M(noobj(), inst); // TODO: use seed
                        this.running_.push_back(m);
                        LOG_WRITE(DEBUG, this, L(FOS_TAB_2 "!ginitial!! monad created: {}\n", m.toString()));
                    }*/
                    } catch (final Exception e) {
                        // throw e;
                    }
                    first = false;
                }
                // start inst forced initial TODO: remove this as it's not sound
                if (this.running.isEmpty()) {
                    // const Obj_p seed_copy = Objs::to_objs();
                    // final BObj.Obj seed_copy = this.code.value().get(0); //.inst_seed(this.code.codevalue().front());
                    this.running.add(new Monad(this, NoObj.of(), this.code.value().get(0), 1));
                }
            }
        }

        @Override
        public Iterator<Obj> iterator() {
            List<Obj> results = new ArrayList<>();
            Obj m = null;
            while (null != (m = this.next())) {
                // System.out.println("packaging result: " + m);
                results.add(m);
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
                    final Monad m = this.running.poll();
                    m.run();
                } else if (!this.barriers.isEmpty()) { // TODO
                    final Monad barrier = this.barriers.poll();
                    barrier.run();
                }
            }
        }

        @Override
        public String toString() {
            return "MONOID[" + this.code + "]";
        }
    }

}
