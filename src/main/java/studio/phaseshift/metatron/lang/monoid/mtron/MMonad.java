package studio.phaseshift.metatron.lang.monoid.mtron;

import org.apache.commons.collections.IteratorUtils;
import org.javatuples.Quartet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.START_TID;

// monoid, obj, inst, state
public class MMonad extends MObj implements Monad {

    private static final GraphittyLogger LOG = Graphitty.log(MMonad.class);

    public MMonad(final Quartet<Monoid, Obj, Inst, Rec> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public Quartet<Monoid, Obj, Inst, Rec> value() {
        return (Quartet<Monoid, Obj, Inst, Rec>) this.value;
    }

    @Override
    public Monad halt() {
       this.monoid().halted().<LinkedList<Obj>>valueAs().push(this.obj());
        return this;
    }

    @Override
    public Monad clone(final Object value, final fURI tid, final fURI vid) {
        return new MMonad((Quartet<Monoid, Obj, Inst, Rec>) value, tid, vid);
    }

    @Override
    public Monad apply(final Obj inst) {
        if (this.halted()) {
            if (!this.dead())
                return this.halt();
        } else {
            if (true) {
                LOG.trace("processing inst %s with %s", this.inst(), IteratorUtils.toList(this.obj().iterator()));
                if (this.inst().tid().queryless().equals(START_TID) && this.obj().isNoObj()) {
                    this.domain_loop();
                } else {
                    IteratorUtil.iterate(IteratorUtil.consume(this.obj().iterator(), o -> {
                        final MMonad m = (MMonad) MMonad.of(this.monoid(), o, this.inst());
                        m.domain_loop();
                    }));
                }
            } else {
                LOG.debug("processing barrier %s with %s", this.inst(), IteratorUtils.toList(this.obj().iterator()));
                this.domain_loop();
            }
        }
        return this;
    }

    /// ////////////////////////////////////////////////////////////////////////

    public void domain_loop() {

        // LOG_WRITE(TRACE, this->processor_,
        //     L(FOS_TAB_2"monad at !gdomain!! of {} !m=>!! {} [!m{}!!]\n", this->toString(),
        //      current_inst_resolved -> toString(), "SIGNATURE HERE"));

        if (false) {//this.inst().f().form().isGather()) {
            LOG.trace("processing monadic domain: %s => %s", this.obj(), this.inst());
            if (this.obj().isObjs()) {
                LOG.trace("evaluating barrier: %s", this.obj());
                this.range_loop(this.inst().apply(this.obj()));
            } else {
                MMonad barrier = this.monoid().barriers().isEmpty() ?
                        new MMonad(Quartet.with(this.monoid(), new MObjs(List.of()), this.inst(), this.state()), this.tid, this.vid) :
                        (MMonad) this.monoid().barriers().elements().iterator().next();
                if (!barrier.obj().isObjs()) {
                    LOG.warn("barrier is not an objs: {}", barrier.obj());
                    barrier.obj(MObjs.of(List.of(barrier.obj())));
                }
                // TODO:!!! APPEND  this.monoid.barriers.add(new MMonad(this.monoid, barrier.obj.<Objs>as().append(this.obj), this.inst, this.bulk));
            }
        } else {
            final Obj result = this.inst().apply(this.obj());
            LOG.trace("applying %s to %s to yield %s", this.obj(), this.inst(), result);
            this.range_loop(result);
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

    public void range_loop(final Obj nextObj) {

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

        final Inst nextInst = this.monoid().code().next(this.inst()); // TODO !!!!! this.monoid.code.nextInst(inst);
        LOG.trace("processing monadic range: %s => %s", nextObj, nextInst);
        if (false) { // this.inst().isScatter()) {
            LOG.trace("scattering monad obj: {} (over {})", nextObj, nextInst);
            IteratorUtil.iterate(IteratorUtil.consume(nextObj.iterator(), o -> {
                final Monad m = MMonad.of(this.monoid(), o, nextInst);
                this.monoid().running().<LinkedList<Monad>>valueAs().add(m);
            }));
        } else {
            final Monad nextMonad = MMonad.of(this.monoid(), nextObj, nextInst);
            this.monoid().running().<LinkedList<Monad>>valueAs().add(nextMonad);
        }
    }

    public boolean equals(final Object other) {
        return other instanceof MMonad &&
                this.obj().equals(((MMonad) other).obj()) &&
                this.inst().equals(((MMonad) other).inst());
    }

    /*public void halt() {
        LOG.trace("halting monad %s", this);
        this.inst = NoObj.single();
        this.monoid.halted.add(this.obj);
        /*for(int i = 0; i < this->bulk_; i++) {
          this->processor_->halted_->push_back(std::move(this->obj_->clone()));
        }*/

    public String toString() {
        return Graphitty.string("{{b}}M{{g}}[%s{{g}}]{{r}}@%s{{X}}".formatted(this.obj(), this.inst()));
    }

    public static Monad of(final Monoid monoid, final Obj obj, final Inst inst) {
        return new MMonad(Quartet.with(monoid, obj, inst, MRec.of()), fURI.of("monad:abc"), fURI.NULL);
    }
}