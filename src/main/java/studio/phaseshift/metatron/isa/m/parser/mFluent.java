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
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public class mFluent<F extends Fluent<F>> extends MCode implements Fluent<F>, Code {

    // ========================================
    // Constructors and Core Methods
    // ========================================

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

    // ========================================
    // Control Flow Operations
    // ========================================

    public F start_(final Obj obj) {
        return this.addInst(instB(mInstSet.START_INST_TID, lst(obj)));
    }

    public F block_(final Obj obj) {
        return this.addInst(instB(mInstSet.BLOCK_INST_TID, lst(obj)));
    }

    public F where_(final Obj obj) {
        return this.addInst(instB(mInstSet.WHERE_INST_TID, lst(obj)));
    }

    public F else_(final Obj obj) {
        return this.addInst(instB(mInstSet.ELSE_INST_TID, lst(obj)));
    }

    public F apply_(final Obj obj) {
        return this.addInst(instB(mInstSet.APPLY_INST_TID, lst(obj)));
    }

    public F catch_(final Obj obj) {
        return this.addInst(instB(mInstSet.CATCH_INST_TID, lst(obj)));
    }

    public F repeat_(final Obj obj) {
        return this.addInst(instB(mInstSet.REPEAT_INST_TID, lst(obj)));
    }

    public F end_() {
        return this.addInst(instB(mInstSet.END_INST_TID, lst()));
    }

    public F barrier_() {
        return this.addInst(instB(mInstSet.BARRIER_TID, lst()));
    }

    public F thread_(final Obj obj) {
        return this.addInst(instB(mInstSet.THREAD_INST_TID, lst(obj)));
    }

    // ========================================
    // Logical Operators
    // ========================================

    public F and_(final Obj... obj) {
        return this.addInst(instB(mInstSet.AND_INST_TID, lst(obj)));
    }

    public F or_(final Obj... obj) {
        return this.addInst(instB(mInstSet.OR_INST_TID, lst(obj)));
    }

    public F not_(final Obj obj) {
        return this.addInst(instB(mInstSet.NOT_INST_TID, lst(obj)));
    }

    public F is_(final Obj obj) {
        return this.addInst(instB(mInstSet.IS_INST_TID, lst(obj)));
    }

    public F isa_(final Obj obj) {
        return this.addInst(instB(mInstSet.ISA_INST_TID, lst(obj)));
    }

    public F has_(final Obj obj) {
        return this.addInst(instB(mInstSet.HAS_INST_TID, lst(obj)));
    }

    // ========================================
    // Comparison Operators
    // ========================================

    public F eq_(final Obj obj) {
        return this.addInst(instB(mInstSet.EQ_INST_TID, lst(obj)));
    }

    public F neq_(final Obj obj) {
        return this.addInst(instB(mInstSet.NEQ_INST_TID, lst(obj)));
    }

    public F lt_(final Obj obj) {
        return this.addInst(instB(mInstSet.LT_INST_TID, lst(obj)));
    }

    public F lte_(final Obj obj) {
        return this.addInst(instB(mInstSet.LTE_INST_TID, lst(obj)));
    }

    public F gt_(final Obj obj) {
        return this.addInst(instB(mInstSet.GT_INST_TID, lst(obj)));
    }

    public F gte_(final Obj obj) {
        return this.addInst(instB(mInstSet.GTE_INST_TID, lst(obj)));
    }

    public F regex_(final Obj obj) {
        return this.addInst(instB(mInstSet.REGEX_INST_TID, lst(obj)));
    }

    // ========================================
    // Arithmetic Operators
    // ========================================

    public F plus_(final Obj obj) {
        return this.addInst(instB(mInstSet.PLUS_INST_TID, lst(obj)));
    }

    public F mplus_(final Obj obj) {
        return this.addInst(instB(mInstSet.MPLUS_INST_TID, lst(obj)));
    }

    public F minus_(final Obj obj) {
        return this.addInst(instB(mInstSet.MINUS_INST_TID, lst(obj)));
    }

    public F mult_(final Obj obj) {
        return this.addInst(instB(mInstSet.MULT_INST_TID, lst(obj)));
    }

    public F pow_(final Obj obj) {
        return this.addInst(instB(mInstSet.POW_INST_TID, lst(obj)));
    }

    public F math_(final Obj obj) {
        return this.addInst(instB(mInstSet.MATH_INST_TID, lst(obj)));
    }

    // ========================================
    // Collection Operations
    // ========================================

    public F map_(final Obj obj) {
        return this.addInst(instB(mInstSet.MAP_INST_TID, lst(obj)));
    }

    public F filter_(final Obj obj) {
        return this.addInst(instB(mInstSet.FILTER_INST_TID, lst(obj)));
    }

    public F select_(final Obj obj) {
        return this.addInst(instB(mInstSet.SELECT_INST_TID, lst(obj)));
    }

    public F group_(final Obj obj) {
        return this.addInst(instB(mInstSet.GROUP_INST_TID, lst(obj)));
    }

    public F get_(final Obj obj) {
        return this.addInst(instB(mInstSet.GET_INST_TID, lst(obj)));
    }

    public F in_(final Obj obj) {
        return this.addInst(instB(mInstSet.MATCHES_INST_TID, lst(obj)));
    }

    public F split_(final Obj obj) {
        return this.addInst(instB(mInstSet.SPLIT_INST_TID, lst(obj)));
    }

    public F choose_(final Obj obj) {
        return this.addInst(instB(mInstSet.CHOOSE_INST_TID, lst(obj)));
    }

    public F merge_() {
        return this.addInst(instB(mInstSet.MERGE_INST_TID, lst()));
    }

    public F rmerge_(final Obj obj) {
        return this.addInst(instB(mInstSet.RMERGE_TID, lst(obj)));
    }

    public F cross_(final Obj obj) {
        return this.addInst(instB(mInstSet.SELECT_INST_TID, lst(obj)));
    }

    public F take_(final Obj obj) {
        return this.addInst(instB(mInstSet.TAKE_INST_TID, lst(obj)));
    }

    public F skip_(final Obj obj) {
        return this.addInst(instB(mInstSet.SKIP_INST_TID, lst(obj)));
    }

    public F find_(final Obj obj) {
        return this.addInst(instB(mInstSet.FIND_TID, lst(obj)));
    }

    public F fill_(final Obj obj) {
        return this.addInst(instB(mInstSet.FILL_TID, lst(obj)));
    }

    public F chain_(final Obj obj) {
        return this.addInst(instB(mInstSet.CHAIN_TID, lst(obj)));
    }

    // ========================================
    // Aggregation Functions
    // ========================================

    public F count_() {
        return this.addInst(instB(mInstSet.COUNT_INST_TID, lst()));
    }

    public F sum_() {
        return this.addInst(instB(mInstSet.SUM_INST_TID, lst()));
    }

    public F prod_() {
        return this.addInst(instB(mInstSet.PROD_INST_TID, lst()));
    }

    public F reduce_(final Obj obj) {
        return this.addInst(instB(mInstSet.REDUCE_INST_TID, lst(obj)));
    }

    public F cc_(final Obj obj) {
        return this.addInst(instB(mInstSet.CC_INST_TID, lst(obj)));
    }

    // ========================================
    // Shift Operations
    // ========================================

    public F lshift_(final Obj obj) {
        return this.addInst(instB(mInstSet.LSHIFT_INST_TID, lst(obj)));
    }

    public F rshift_(final Obj obj) {
        return this.addInst(instB(mInstSet.RSHIFT_INST_TID, lst(obj)));
    }

    // ========================================
    // Type/Conversion Operations
    // ========================================

    public F as_(final Obj obj) {
        return this.addInst(instB(mInstSet.AS_INST_TID, lst(obj)));
    }

    public F to_(final Obj obj) {
        return this.addInst(instB(mInstSet.TO_INST_TID, lst(obj)));
    }

    public F from_(final Obj obj) {
        return this.addInst(instB(mInstSet.FROM_INST_TID, lst(obj)));
    }

    public F rfrom_(final Obj obj) {
        return this.addInst(instB(mInstSet.RFROM_TID, lst(obj)));
    }

    public F auto_(final Obj obj) {
        return this.addInst(instB(mInstSet.AUTO_INST_TID, lst(obj)));
    }

    public F auto_from_(final Obj obj, final Obj defaultObj) {
        return this.addInst(instB(mInstSet.AUTO_FROM_INST_TID, lst(obj, defaultObj)));
    }

    public F type_(final Obj obj) {
        return this.addInst(instB(mInstSet.TYPE_INST_TID, lst(obj)));
    }

    public F reify_() {
        return this.addInst(instB(mInstSet.REIFY_INST_TID, lst()));
    }

    // ========================================
    // Utility Methods
    // ========================================

    public F id_() {
        return this.addInst(instB(ID_INST_TID, lst()));
    }

    public F explain_(final Obj obj) {
        return this.addInst(instB(mInstSet.EXPLAIN_INST_TID, lst(obj)));
    }

    public F at_(final Obj obj) {
        return this.addInst(instB(mInstSet.AT_INST_TID, lst(obj)));
    }

    public F ref_(final Obj obj) {
        return this.addInst(instB(mInstSet.REF_INST_TID, lst(obj)));
    }

    public F parent_(final Obj obj) {
        return this.addInst(instB(mInstSet.PARENT_INST_TID, lst(obj)));
    }

    public F within_(final Obj obj) {
        return this.addInst(instB(mInstSet.WITHIN_INST_TID, lst(obj)));
    }

    public F lift_(final Obj obj) {
        return this.addInst(instB(mInstSet.LIFT_INST_TID, lst(obj)));
    }

    public F side_(final Obj obj) {
        return this.addInst(instB(mInstSet.SIDE_INST_TID, lst(obj)));
    }

   // public F close_(final Obj obj) {
   //     return this.addInst(instB(mInstSet.CLOSE_INST_TID, lst(obj)));
   // }

    public F source_(final Obj obj) {
        return this.addInst(instB(mInstSet.SOURCE_INST_TID, lst(obj)));
    }

    public F swap_(final Obj obj) {
        return this.addInst(instB(mInstSet.SWAP_TID, lst(obj)));
    }

    public F print_(final Obj obj) {
        return this.addInst(instB(mInstSet.PRINT_INST_TID, lst(obj)));
    }

    public F failure_(final Obj obj) {
        return this.addInst(instB(mInstSet.FAILURE_INST_TID, lst(obj)));
    }

    public F q_(final Obj obj) {
        return this.addInst(instB(mInstSet.Q_INST_TID, lst(obj)));
    }

    public F rng_() {
        return this.addInst(instB(mInstSet.RNG_INST_TID, lst()));
    }

    public F dom_() {
        return this.addInst(instB(mInstSet.DOM_INST_TID, lst()));
    }

    public F tid_() {
        return this.addInst(instB(mInstSet.TID_INST_TID, lst()));
    }

    public F vid_() {
        return this.addInst(instB(mInstSet.VID_INST_TID, lst()));
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

        // ========================================
        // Core Methods
        // ========================================

        public static <F extends mFluent<F>> F inst_(final Inst inst) {
            return new mFluent<F>().addInst(inst);
        }

        // ========================================
        // Control Flow Operations
        // ========================================

        public static <F extends mFluent<F>> F start_(final Obj obj) {
            return new mFluent<F>().start_(obj);
        }

        public static <F extends mFluent<F>> F block_(final Obj obj) {
            return new mFluent<F>().block_(obj);
        }

        public static <F extends mFluent<F>> F where_(final Obj obj) {
            return new mFluent<F>().where_(obj);
        }

        public static <F extends mFluent<F>> F else_(final Obj obj) {
            return new mFluent<F>().else_(obj);
        }

        public static <F extends mFluent<F>> F apply_(final Obj obj) {
            return new mFluent<F>().apply_(obj);
        }

        public static <F extends mFluent<F>> F catch_(final Obj obj) {
            return new mFluent<F>().catch_(obj);
        }

        public static <F extends mFluent<F>> F repeat_(final Obj obj) {
            return new mFluent<F>().repeat_(obj);
        }

        public static <F extends mFluent<F>> F end_() {
            return new mFluent<F>().end_();
        }

        public static <F extends mFluent<F>> F barrier_() {
            return new mFluent<F>().barrier_();
        }

        public static <F extends mFluent<F>> F thread_(final Obj obj) {
            return new mFluent<F>().thread_(obj);
        }

        // ========================================
        // Logical Operators
        // ========================================

        public static <F extends mFluent<F>> F and_(final Obj... obj) {
            return new mFluent<F>().and_(obj);
        }

        public static <F extends mFluent<F>> F or_(final Obj... obj) {
            return new mFluent<F>().or_(obj);
        }

        public static <F extends mFluent<F>> F not_(final Obj obj) {
            return new mFluent<F>().not_(obj);
        }

        public static <F extends mFluent<F>> F is_(final Obj obj) {
            return new mFluent<F>().is_(obj);
        }

        public static <F extends mFluent<F>> F isa_(final Obj obj) {
            return new mFluent<F>().isa_(obj);
        }

        public static <F extends mFluent<F>> F has_(final Obj obj) {
            return new mFluent<F>().has_(obj);
        }

        // ========================================
        // Comparison Operators
        // ========================================

        public static <F extends mFluent<F>> F eq_(final Obj obj) {
            return new mFluent<F>().eq_(obj);
        }

        public static <F extends mFluent<F>> F neq_(final Obj obj) {
            return new mFluent<F>().neq_(obj);
        }

        public static <F extends mFluent<F>> F lt_(final Obj obj) {
            return new mFluent<F>().lt_(obj);
        }

        public static <F extends mFluent<F>> F lte_(final Obj obj) {
            return new mFluent<F>().lte_(obj);
        }

        public static <F extends mFluent<F>> F gt_(final Obj obj) {
            return new mFluent<F>().gt_(obj);
        }

        public static <F extends mFluent<F>> F gte_(final Obj obj) {
            return new mFluent<F>().gte_(obj);
        }

        public static <F extends mFluent<F>> F regex_(final Obj obj) {
            return new mFluent<F>().regex_(obj);
        }

        // ========================================
        // Arithmetic Operators
        // ========================================

        public static <F extends mFluent<F>> F plus_(final Obj obj) {
            return new mFluent<F>().plus_(obj);
        }

        public static <F extends mFluent<F>> F mplus_(final Obj obj) {
            return new mFluent<F>().mplus_(obj);
        }

        public static <F extends mFluent<F>> F minus_(final Obj obj) {
            return new mFluent<F>().minus_(obj);
        }

        public static <F extends mFluent<F>> F mult_(final Obj obj) {
            return new mFluent<F>().mult_(obj);
        }

        public static <F extends mFluent<F>> F pow_(final Obj obj) {
            return new mFluent<F>().pow_(obj);
        }

        public static <F extends mFluent<F>> F math_(final Obj obj) {
            return new mFluent<F>().math_(obj);
        }

        // ========================================
        // Collection Operations
        // ========================================

        public static <F extends mFluent<F>> F map_(final Obj obj) {
            return new mFluent<F>().map_(obj);
        }

        public static <F extends mFluent<F>> F filter_(final Obj obj) {
            return new mFluent<F>().filter_(obj);
        }

        public static <F extends mFluent<F>> F select_(final Obj obj) {
            return new mFluent<F>().select_(obj);
        }

        public static <F extends mFluent<F>> F group_(final Obj obj) {
            return new mFluent<F>().group_(obj);
        }

        public static <F extends mFluent<F>> F get_(final Obj obj) {
            return new mFluent<F>().get_(obj);
        }

        public static <F extends mFluent<F>> F in_(final Obj obj) {
            return new mFluent<F>().in_(obj);
        }

        public static <F extends mFluent<F>> F split_(final Obj obj) {
            return new mFluent<F>().split_(obj);
        }

        public static <F extends mFluent<F>> F choose_(final Obj obj) {
            return new mFluent<F>().choose_(obj);
        }

        public static <F extends mFluent<F>> F merge_() {
            return new mFluent<F>().merge_();
        }

        public static <F extends mFluent<F>> F rmerge_(final Obj obj) {
            return new mFluent<F>().rmerge_(obj);
        }

        public static <F extends mFluent<F>> F cross_(final Obj obj) {
            return new mFluent<F>().cross_(obj);
        }

        public static <F extends mFluent<F>> F take_(final Obj obj) {
            return new mFluent<F>().take_(obj);
        }

        public static <F extends mFluent<F>> F skip_(final Obj obj) {
            return new mFluent<F>().skip_(obj);
        }

        public static <F extends mFluent<F>> F find_(final Obj obj) {
            return new mFluent<F>().find_(obj);
        }

        public static <F extends mFluent<F>> F fill_(final Obj obj) {
            return new mFluent<F>().fill_(obj);
        }

        public static <F extends mFluent<F>> F chain_(final Obj obj) {
            return new mFluent<F>().chain_(obj);
        }

        // ========================================
        // Aggregation Functions
        // ========================================

        public static <F extends mFluent<F>> F count_() {
            return new mFluent<F>().count_();
        }

        public static <F extends mFluent<F>> F sum_() {
            return new mFluent<F>().sum_();
        }

        public static <F extends mFluent<F>> F prod_() {
            return new mFluent<F>().prod_();
        }

        public static <F extends mFluent<F>> F reduce_(final Obj obj) {
            return new mFluent<F>().reduce_(obj);
        }

        public static <F extends mFluent<F>> F cc_(final Obj obj) {
            return new mFluent<F>().cc_(obj);
        }

        // ========================================
        // Shift Operations
        // ========================================

        public static <F extends mFluent<F>> F lshift_(final Obj obj) {
            return new mFluent<F>().lshift_(obj);
        }

        public static <F extends mFluent<F>> F rshift_(final Obj obj) {
            return new mFluent<F>().rshift_(obj);
        }

        // ========================================
        // Type/Conversion Operations
        // ========================================

        public static <F extends mFluent<F>> F as_(final Obj obj) {
            return new mFluent<F>().as_(obj);
        }

        public static <F extends mFluent<F>> F to_(final Obj obj) {
            return new mFluent<F>().to_(obj);
        }

        public static <F extends mFluent<F>> F from_(final Obj obj) {
            return new mFluent<F>().from_(obj);
        }

        public static <F extends mFluent<F>> F rfrom_(final Obj obj) {
            return new mFluent<F>().rfrom_(obj);
        }

        public static <F extends mFluent<F>> F auto_(final Obj obj) {
            return new mFluent<F>().auto_(obj);
        }

        public static <F extends mFluent<F>> F auto_from_(final Uri uri, final Obj defaultObj) {
            return new mFluent<F>().auto_from_(uri, defaultObj);
        }

        public static <F extends mFluent<F>> F auto_from_(final Uri uri) {
            return new mFluent<F>().auto_from_(uri, noobj());
        }

        public static <F extends mFluent<F>> F auto_from_(final fURI uri) {
            return auto_from_(uri.toUri());
        }

        public static <F extends mFluent<F>> F type_(final Obj obj) {
            return new mFluent<F>().type_(obj);
        }

        public static <F extends mFluent<F>> F reify_() {
            return new mFluent<F>().reify_();
        }

        // ========================================
        // Utility Methods
        // ========================================

        public static <F extends mFluent<F>> F id_() {
            return new mFluent<F>().id_();
        }

        public static <F extends mFluent<F>> F explain_(final Obj obj) {
            return new mFluent<F>().explain_(obj);
        }

        public static <F extends mFluent<F>> F at_(final Obj obj) {
            return new mFluent<F>().at_(obj);
        }

        public static <F extends mFluent<F>> F ref_(final Obj obj) {
            return new mFluent<F>().ref_(obj);
        }

        public static <F extends mFluent<F>> F parent_(final Obj obj) {
            return new mFluent<F>().parent_(obj);
        }

        public static <F extends mFluent<F>> F within_(final Obj obj) {
            return new mFluent<F>().within_(obj);
        }

        public static <F extends mFluent<F>> F lift_(final Obj obj) {
            return new mFluent<F>().lift_(obj);
        }

        public static <F extends mFluent<F>> F side_(final Obj obj) {
            return new mFluent<F>().side_(obj);
        }

        //public static <F extends mFluent<F>> F close_(final Obj obj) {
        //    return new mFluent<F>().close_(obj);
        //}

        public static <F extends mFluent<F>> F source_(final Obj obj) {
            return new mFluent<F>().source_(obj);
        }

        public static <F extends mFluent<F>> F swap_(final Obj obj) {
            return new mFluent<F>().swap_(obj);
        }

        public static <F extends mFluent<F>> F print_(final Obj obj) {
            return new mFluent<F>().print_(obj);
        }

        public static <F extends mFluent<F>> F failure_(final Obj obj) {
            return new mFluent<F>().failure_(obj);
        }

        public static <F extends mFluent<F>> F q_(final Obj obj) {
            return new mFluent<F>().q_(obj);
        }

        public static <F extends mFluent<F>> F rng_() {
            return new mFluent<F>().rng_();
        }

        public static <F extends mFluent<F>> F dom_() {
            return new mFluent<F>().dom_();
        }

        public static <F extends mFluent<F>> F tid_() {
            return new mFluent<F>().tid_();
        }

        public static <F extends mFluent<F>> F vid_() {
            return new mFluent<F>().vid_();
        }

        public static Inst auto(final fURI pointer) {
            return auto_from_(pointer.toUri()).tryToInst().as();
        }
    }
}
