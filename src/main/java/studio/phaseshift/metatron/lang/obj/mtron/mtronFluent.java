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
import studio.phaseshift.metatron.lang.obj.Fluent;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.ID_TID;

public class mtronFluent<F extends Fluent<F>> extends MCode implements Fluent<F> {

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


    public F start(final Obj obj) {
        return this.addInst(instB(mtronInstSet.START_TID, lst(obj)));
    }

    public F block(final Obj obj) {
        return this.addInst(instB(mtronInstSet.BLOCK_TID, lst(obj)));
    }

    public F p1us(final Obj obj) {
        return this.addInst(instB(mtronInstSet.PLUS_TID, lst(obj)));
    }

    public F mult(final Obj obj) {
        return this.addInst(instB(mtronInstSet.MULT_TID, lst(obj)));
    }

    public F map(final Obj obj) {
        return this.addInst(instB(mtronInstSet.MAP_TID, lst(obj)));
    }

    public F id() {
        return this.addInst(instB(ID_TID, lst()));
    }

    public F isA(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ISA_TID, lst(obj)));
    }

    public F in(final Obj obj) {
        return this.addInst(instB(mtronInstSet.IN_TID, lst(obj)));
    }

    public F split(final Obj obj) {
        return this.addInst(instB(mtronInstSet.SPLIT_TID, lst(obj)));
    }

    public F merge() {
        return this.addInst(instB(mtronInstSet.MERGE_TID, lst()));
    }

    public F e1se(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ELSE_TID, lst(obj)));
    }

    public F from(final Obj obj) {
        return this.addInst(instB(mtronInstSet.FROM_TID, lst(obj)));
    }

    public F to_(final Obj obj) {
        return this.addInst(instB(mtronInstSet.TO_TID, lst(obj)));
    }

    public F count() {
        return this.addInst(instB(mtronInstSet.COUNT_TID, lst()));
    }

    public F sum() {
        return this.addInst(instB(mtronInstSet.SUM_TID, lst()));
    }

    public F cross(final Obj obj) {
        return this.addInst(instB(mtronInstSet.CROSS_TID, lst(obj)));
    }

    public F get(final Obj obj) {
        return this.addInst(instB(mtronInstSet.GET_TID, lst(obj)));
    }

    public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }

    @Override
    public mtronFluent<F> clone(Object jvm, fURI tid, fURI vid) {
        return new mtronFluent<>(new ArrayList<>(this.jvm()), this.tid, this.vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends mtronFluent<F>> F start(final Obj obj) {
            return new mtronFluent<F>().start(obj);
        }

        public static <F extends mtronFluent<F>> F block(final Obj obj) {
            return new mtronFluent<F>().block(obj);
        }

        public static <F extends mtronFluent<F>> F plus(final Obj obj) {
            return new mtronFluent<F>().p1us(obj);
        }

        public static <F extends mtronFluent<F>> F mult(final Obj obj) {
            return new mtronFluent<F>().mult(obj);
        }

        public static <F extends mtronFluent<F>> F map(final Obj obj) {
            return new mtronFluent<F>().map(obj);
        }

        public static <F extends mtronFluent<F>> F isA(final Obj obj) {
            return new mtronFluent<F>().isA(obj);
        }

        public static <F extends mtronFluent<F>> F in(final Obj obj) {
            return new mtronFluent<F>().in(obj);
        }

        public static <F extends mtronFluent<F>> F id() {
            return new mtronFluent<F>().id();
        }

        public static <F extends mtronFluent<F>> F split(final Obj obj) {
            return new mtronFluent<F>().split(obj);
        }

        public static <F extends mtronFluent<F>> F merge() {
            return new mtronFluent<F>().merge();
        }

        public static <F extends mtronFluent<F>> F e1se(final Obj obj) {
            return new mtronFluent<F>().e1se(obj);
        }

        public static <F extends mtronFluent<F>> F from(final Obj obj) {
            return new mtronFluent<F>().from(obj);
        }

        public static <F extends mtronFluent<F>> F count() {
            return new mtronFluent<F>().count();
        }

        public static <F extends mtronFluent<F>> F sum() {
            return new mtronFluent<F>().sum();
        }

        public static <F extends mtronFluent<F>> F cross(final Obj obj) {
            return new mtronFluent<F>().cross(obj);
        }

        public static <F extends mtronFluent<F>> F get(final Obj obj) {
            return new mtronFluent<F>().get(obj);
        }
    }
}
