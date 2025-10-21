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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Fluent;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.ID_TID;

public class mtronFluent<F extends Fluent<F>> extends MCode implements Fluent<F>, Code {

    protected mtronFluent() {
        this(new ArrayList<>(), mtronInstSet.CODE_TID, null);
    }

    protected mtronFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public F addInst(final Inst inst) {
        this.codeValue().add(inst);
        return (F) this;
    }


    public F start_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.START_TID, lst(obj)));
    }

    public F block_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.BLOCK_TID, lst(obj)));
    }

    public F plus_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.PLUS_TID, lst(obj)));
    }

    public F mult_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.MULT_TID, lst(obj)));
    }

    public F map_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.MAP_TID, lst(obj)));
    }

    public F id_() {
        return this.addInst(instB(ID_TID, lst()));
    }

    public F isa_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ISA_TID, lst(obj)));
    }

    public F in_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.IN_TID, lst(obj)));
    }

    public F split_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.SPLIT_TID, lst(obj)));
    }

    public F merge_() {
        return this.addInst(instB(mtronInstSet.MERGE_TID, lst()));
    }

    public F else_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ELSE_TID, lst(obj)));
    }

    public F from_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.FROM_TID, lst(obj)));
    }

    public F to_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.TO_TID, lst(obj)));
    }

    public F count_() {
        return this.addInst(instB(mtronInstSet.COUNT_TID, lst()));
    }

    public F sum_() {
        return this.addInst(instB(mtronInstSet.SUM_TID, lst()));
    }

    public F cross_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.CROSS_TID, lst(obj)));
    }

    public F get_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.GET_TID, lst(obj)));
    }

    public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }

    @Override
    public mtronFluent<F> clone(final Object jvm, final fURI tid, final fURI vid) {
        return (mtronFluent<F>) super.clone(jvm, tid, vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends mtronFluent<F>> F start_(final Obj obj) {
            return new mtronFluent<F>().start_(obj);
        }

        public static <F extends mtronFluent<F>> F block_(final Obj obj) {
            return new mtronFluent<F>().block_(obj);
        }

        public static <F extends mtronFluent<F>> F plus_(final Obj obj) {
            return new mtronFluent<F>().plus_(obj);
        }

        public static <F extends mtronFluent<F>> F mult_(final Obj obj) {
            return new mtronFluent<F>().mult_(obj);
        }

        public static <F extends mtronFluent<F>> F map_(final Obj obj) {
            return new mtronFluent<F>().map_(obj);
        }

        public static <F extends mtronFluent<F>> F isa_(final Obj obj) {
            return new mtronFluent<F>().isa_(obj);
        }

        public static <F extends mtronFluent<F>> F in_(final Obj obj) {
            return new mtronFluent<F>().in_(obj);
        }

        public static <F extends mtronFluent<F>> F id_() {
            return new mtronFluent<F>().id_();
        }

        public static <F extends mtronFluent<F>> F split_(final Obj obj) {
            return new mtronFluent<F>().split_(obj);
        }

        public static <F extends mtronFluent<F>> F merge_() {
            return new mtronFluent<F>().merge_();
        }

        public static <F extends mtronFluent<F>> F else_(final Obj obj) {
            return new mtronFluent<F>().else_(obj);
        }

        public static <F extends mtronFluent<F>> F from_(final Obj obj) {
            return new mtronFluent<F>().from_(obj);
        }

        public static <F extends mtronFluent<F>> F to_(final Obj obj) {
            return new mtronFluent<F>().to_(obj);
        }

        public static <F extends mtronFluent<F>> F count_() {
            return new mtronFluent<F>().count_();
        }

        public static <F extends mtronFluent<F>> F sum_() {
            return new mtronFluent<F>().sum_();
        }

        public static <F extends mtronFluent<F>> F cross_(final Obj obj) {
            return new mtronFluent<F>().cross_(obj);
        }

        public static <F extends mtronFluent<F>> F get_(final Obj obj) {
            return new mtronFluent<F>().get_(obj);
        }
    }
}
