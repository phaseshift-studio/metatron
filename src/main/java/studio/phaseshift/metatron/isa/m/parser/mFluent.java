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

package studio.phaseshift.metatron.isa.m.parser;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Fluent;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public class mFluent<F extends Fluent<F>> extends MCode implements Fluent<F>, Code {

    protected mFluent() {
        this(new ArrayList<>(), mInstSet.CODE_TID, null);
    }

    protected mFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public F addInst(final Inst inst) {
        this.codeValue().add(inst);
        return (F) this;
    }


    public F start_(final Obj obj) {
        return this.addInst(instB(mInstSet.START_INST_TID, lst(obj)));
    }

    public F as_(final Obj obj) {
        return this.addInst(instB(mInstSet.AS_INST_TID, lst(obj)));
    }

    public F or_(final Obj... obj) {
        return this.addInst(instB(mInstSet.OR_INST_TID, lst(obj)));
    }

    public F and_(final Obj... obj) {
        return this.addInst(instB(mInstSet.AND_INST_TID, lst(obj)));
    }

    public F apply_(final Obj obj) {
        return this.addInst(instB(mInstSet.APPLY_INST_TID, lst(obj)));
    }

    public F block_(final Obj obj) {
        return this.addInst(instB(mInstSet.BLOCK_INST_TID, lst(obj)));
    }

    public F where_(final Obj obj) {
        return this.addInst(instB(mInstSet.WHERE_INST_TID, lst(obj)));
    }

    public F plus_(final Obj obj) {
        return this.addInst(instB(mInstSet.PLUS_INST_TID, lst(obj)));
    }

    public F mult_(final Obj obj) {
        return this.addInst(instB(mInstSet.MULT_INST_TID, lst(obj)));
    }

    public F map_(final Obj obj) {
        return this.addInst(instB(mInstSet.MAP_INST_TID, lst(obj)));
    }

    public F id_() {
        return this.addInst(instB(ID_INST_TID, lst()));
    }

    public F isa_(final Obj obj) {
        return this.addInst(instB(mInstSet.ISA_INST_TID, lst(obj)));
    }

    public F is_(final Obj obj) {
        return this.addInst(instB(mInstSet.IS_INST_TID, lst(obj)));
    }

    public F lte_(final Obj obj) {
        return this.addInst(instB(mInstSet.LTE_INST_TID, lst(obj)));
    }

    public F lt_(final Obj obj) {
        return this.addInst(instB(mInstSet.LT_INST_TID, lst(obj)));
    }

    public F gte_(final Obj obj) {
        return this.addInst(instB(mInstSet.GTE_INST_TID, lst(obj)));
    }

    public F gt_(final Obj obj) {
        return this.addInst(instB(mInstSet.GT_INST_TID, lst(obj)));
    }

    public F in_(final Obj obj) {
        return this.addInst(instB(mInstSet.MATCHES_INST_TID, lst(obj)));
    }

    public F split_(final Obj obj) {
        return this.addInst(instB(mInstSet.SPLIT_INST_TID, lst(obj)));
    }

    public F merge_() {
        return this.addInst(instB(mInstSet.MERGE_INST_TID, lst()));
    }

    public F else_(final Obj obj) {
        return this.addInst(instB(mInstSet.ELSE_INST_TID, lst(obj)));
    }

    public F from_(final Obj obj) {
        return this.addInst(instB(mInstSet.FROM_INST_TID, lst(obj)));
    }

    public F auto_(final Obj obj) {
        return this.addInst(instB(mInstSet.AUTO_INST_TID, lst(obj)));
    }

    public F auto_from_(final Obj obj) {
        return this.addInst(instB(mInstSet.AUTO_FROM_INST_TID, lst(obj)));
    }

    public F to_(final Obj obj) {
        return this.addInst(instB(mInstSet.TO_INST_TID, lst(obj)));
    }

    public F eq_(final Obj obj) {
        return this.addInst(instB(mInstSet.EQ_INST_TID, lst(obj)));
    }

    public F count_() {
        return this.addInst(instB(mInstSet.COUNT_INST_TID, lst()));
    }

    public F sum_() {
        return this.addInst(instB(mInstSet.SUM_INST_TID, lst()));
    }

    public F prod_() {
        return this.addInst(instB(mInstSet.PROD_INST_TID, lst()));
    }

    public F select_(final Obj obj) {
        return this.addInst(instB(mInstSet.SELECT_INST_TID, lst(obj)));
    }

    public F rshift_(final Obj obj) {
        return this.addInst(instB(mInstSet.RSHIFT_INST_TID, lst(obj)));
    }

    public F lshift_(final Obj obj) {
        return this.addInst(instB(mInstSet.LSHIFT_INST_TID, lst(obj)));
    }

    public F cross_(final Obj obj) {
        return this.addInst(instB(mInstSet.SELECT_INST_TID, lst(obj)));
    }

    public F get_(final Obj obj) {
        return this.addInst(instB(mInstSet.GET_INST_TID, lst(obj)));
    }

    /*public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }*/

    public F domrng(final fURI dom, final fURI rng) {
        final Inst last = this.insts().remove(this.insts().size() - 1);
        this.insts().add(last.tid(last.tid().dom(dom).rng(rng)));
        this.logger().info(this.insts());
        return (F) this;
    }

    @Override
    public mFluent<F> clone(final Object jvm, final fURI tid, final fURI vid) {
        return (mFluent<F>) super.clone(jvm, tid, vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends mFluent<F>> F inst_(final Inst inst) {
            return new mFluent<F>().addInst(inst);
        }

        public static <F extends mFluent<F>> F start_(final Obj obj) {
            return new mFluent<F>().start_(obj);
        }

        public static <F extends mFluent<F>> F as_(final Obj obj) {
            return new mFluent<F>().as_(obj);
        }

        public static <F extends mFluent<F>> F select_(final Obj obj) {
            return new mFluent<F>().select_(obj);
        }

        public static <F extends mFluent<F>> F block_(final Obj obj) {
            return new mFluent<F>().block_(obj);
        }

        public static <F extends mFluent<F>> F plus_(final Obj obj) {
            return new mFluent<F>().plus_(obj);
        }

        public static <F extends mFluent<F>> F mult_(final Obj obj) {
            return new mFluent<F>().mult_(obj);
        }

        public static <F extends mFluent<F>> F map_(final Obj obj) {
            return new mFluent<F>().map_(obj);
        }

        public static <F extends mFluent<F>> F isa_(final Obj obj) {
            return new mFluent<F>().isa_(obj);
        }

        public static <F extends mFluent<F>> F is_(final Obj obj) {
            return new mFluent<F>().is_(obj);
        }

        public static <F extends mFluent<F>> F lte_(final Obj obj) {
            return new mFluent<F>().lte_(obj);
        }

        public static <F extends mFluent<F>> F lt_(final Obj obj) {
            return new mFluent<F>().lt_(obj);
        }

        public static <F extends mFluent<F>> F gte_(final Obj obj) {
            return new mFluent<F>().gte_(obj);
        }

        public static <F extends mFluent<F>> F gt_(final Obj obj) {
            return new mFluent<F>().gt_(obj);
        }

        public static <F extends mFluent<F>> F where_(final Obj obj) {
            return new mFluent<F>().where_(obj);
        }

        public static <F extends mFluent<F>> F in_(final Obj obj) {
            return new mFluent<F>().in_(obj);
        }

        public static <F extends mFluent<F>> F id_() {
            return new mFluent<F>().id_();
        }

        public static <F extends mFluent<F>> F split_(final Obj obj) {
            return new mFluent<F>().split_(obj);
        }

        public static <F extends mFluent<F>> F merge_() {
            return new mFluent<F>().merge_();
        }

        public static <F extends mFluent<F>> F or_(final Obj... obj) {
            return new mFluent<F>().or_(obj);
        }

        public static <F extends mFluent<F>> F and_(final Obj... obj) {
            return new mFluent<F>().and_(obj);
        }

        public static <F extends mFluent<F>> F else_(final Obj obj) {
            return new mFluent<F>().else_(obj);
        }

        public static <F extends mFluent<F>> F auto_(final Obj obj) {
            return new mFluent<F>().auto_(obj);
        }

        public static <F extends mFluent<F>> F auto_from_(final Uri uri) {
            return new mFluent<F>().auto_from_(uri);
        }

        public static <F extends mFluent<F>> F from_(final Obj obj) {
            return new mFluent<F>().from_(obj);
        }

        public static <F extends mFluent<F>> F to_(final Obj obj) {
            return new mFluent<F>().to_(obj);
        }

        public static <F extends mFluent<F>> F count_() {
            return new mFluent<F>().count_();
        }

        public static <F extends mFluent<F>> F sum_() {
            return new mFluent<F>().sum_();
        }

        public static <F extends mFluent<F>> F prod_() {
            return new mFluent<F>().prod_();
        }

        public static <F extends mFluent<F>> F eq_(final Obj obj) {
            return new mFluent<F>().eq_(obj);
        }

        public static <F extends mFluent<F>> F cross_(final Obj obj) {
            return new mFluent<F>().cross_(obj);
        }

        public static <F extends mFluent<F>> F apply_(final Obj obj) {
            return new mFluent<F>().apply_(obj);
        }

        public static <F extends mFluent<F>> F get_(final Obj obj) {
            return new mFluent<F>().get_(obj);
        }

        public static <F extends mFluent<F>> F rshift_(final Obj obj) {
            return new mFluent<F>().rshift_(obj);
        }

        public static <F extends mFluent<F>> F lshift_(final Obj obj) {
            return new mFluent<F>().lshift_(obj);
        }

        public static Inst auto(final fURI pointer) {
            return auto_from_(pointer.toUri()).tryToInst().as();
        }
    }
}
