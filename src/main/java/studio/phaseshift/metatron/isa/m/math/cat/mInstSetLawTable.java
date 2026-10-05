/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.BOOLEAN_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.FIELD_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.GROUP_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.LATTICE_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.Law.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.MONOID_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.RIG_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.RING_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The law table of {@code /m} — the base types' declared laws, collocated.
 *
 * <p>Both axes of the {@link LawTable} live here: the process laws each base type's operations obey
 * (the {@code declared ∩ process} cell served to {@code morphism::T>>law}), and the structural theories
 * each base type models (the theory recs served to {@code object::T>>law}). The base types' laws never
 * (or rarely) change, so one table per family beats declarations scattered across the type classes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mInstSetLawTable extends LawTable {

    /**
     * The singleton — the base types have exactly one law table.
     */
    public static final mInstSetLawTable INSTANCE = new mInstSetLawTable();

    private mInstSetLawTable() {
        loadInt();
        loadReal();
        loadStr();
        loadBool();
        loadBytes();
        loadUri();
        loadLst();
        loadRec();
    }

    @Override
    protected Rec typeLawsUncached(final Type type) {
        final fURI t = type.vid().basePath();
        if (t.equals(INT_TID)) {
            // int: a ring — (add=+, mul=·, zero=0, one=1) and its constituent group/monoids
            return rec(mutableMap(
                    uri("ring"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(Tokens.ZERO), jnt(0),
                                    uri(Tokens.ONE), jnt(1)),
                            RING_THEORY_TID, null),
                    uri("add_group"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(ID), jnt(0),
                                    uri(INV), auto_from_(NEG_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("add_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(ID), jnt(0)),
                            MONOID_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(ID), jnt(1)),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(REAL_TID)) {
            // real: a field — a ring where the non-zero mult elements are a group (inv = 1/x); plus the constituents
            return rec(mutableMap(
                    uri("field"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(Tokens.ZERO), real(0.0d),
                                    uri(Tokens.ONE), real(1.0d),
                                    uri(INV), auto_from_(INV_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                            FIELD_THEORY_TID, null),
                    uri("ring"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(Tokens.ZERO), real(0.0d),
                                    uri(Tokens.ONE), real(1.0d)),
                            RING_THEORY_TID, null),
                    uri("add_group"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(ID), real(0.0d),
                                    uri(INV), auto_from_(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("mult_group"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(ID), real(1.0d),
                                    uri(INV), auto_from_(INV_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("add_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(ID), real(0.0d)),
                            MONOID_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID)).tryToInst(),
                                    uri(ID), real(1.0d)),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(BOOL_TID)) {
            // bool: a Boolean algebra — or=join(PLUS), and=meet(MULT), not=complement — a complemented distributive
            // lattice and a rig (idempotent semiring) with bottom=false / top=true
            return rec(mutableMap(
                    uri("boolean"), rec(mutableMap(
                                    uri(OR), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(AND), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(NOT), auto_from_(NOT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(Tokens.ZERO), bool(false),
                                    uri(Tokens.ONE), bool(true)),
                            BOOLEAN_THEORY_TID, null),
                    uri("rig"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(Tokens.ZERO), bool(false),
                                    uri(Tokens.ONE), bool(true)),
                            RIG_THEORY_TID, null),
                    uri("lattice"), rec(mutableMap(
                                    uri(MEET), auto_from_(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(JOIN), auto_from_(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID)).tryToInst(),
                                    uri(BOTTOM), bool(false),
                                    uri(TOP), bool(true)),
                            LATTICE_THEORY_TID, null)));
        }
        if (t.equals(STR_TID)) {
            // str: a monoid under concatenation (PLUS), identity ""
            return rec(mutableMap(
                    uri("concat_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(STR_TID).rng(STR_TID)).tryToInst(),
                                    uri(ID), str("")),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(URI_TID)) {
            // uri: monoids under concatenation (both PLUS and MULT concatenate), identity "."
            return rec(mutableMap(
                    uri("plus_concat_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                    uri(ID), uri(".")),
                            MONOID_THEORY_TID, null),
                    uri("mult_concat_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                    uri(ID), uri(".")),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(BYTES_TID)) {
            // bytes: a monoid under concatenation (PLUS), identity the empty buffer
            return rec(mutableMap(
                    uri("concat_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID)).tryToInst(),
                                    uri(ID), bytes(new byte[0])),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(CODE_TID)) {
            // code: the function/stream ring — add=branch (parallel split/merge), mul=compose (serial),
            // zero=end (the 0-function, ≡ id{0}::T), one=id (the parametric identity), inv=additive inverse (coeff −1)
            return rec(mutableMap(
                    uri("ring"), rec(mutableMap(
                                    uri(ADD), auto_from_(BRANCH_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(MUL), auto_from_(COMPOSE_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(Tokens.ZERO), auto_from_(END_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(Tokens.ONE), auto_from_(ID_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                            RING_THEORY_TID, null),
                    uri("add_group"), rec(mutableMap(
                                    uri(OP), auto_from_(BRANCH_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(ID), auto_from_(END_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(INV), auto_from_(NEG_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(COMPOSE_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
                                    uri(ID), auto_from_(ID_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst()),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(MACH_MACHINE_TID)) {
            // machine: the frame system is a GROUPOID — the objects are machines (frames), the morphisms are the
            // invertible URI extensions, composition is descent (push) and its inverse is ascent (pop). The
            // extensions are the NAMES of the morphisms, so there are infinitely many: one per address. At any
            // one object that endomorphism monoid is FREE on the extensions, and the machine stack is one
            // particular such structure — the groupoid of frames. Its identity is `here` (`.`), matching uri::T
            // below, so `push … pop` reduces to the identity exactly as `a/../` does.
            //
            // The morphisms act on the ADDRESS, which is why the operation is bound to the uri ring's `*`
            // (path composition): push is vid·e and pop is vid·e⁻¹, and the strict-descendant requirement is
            // what makes e invertible — an escaping extension is not in this groupoid at all.
            return rec(mutableMap(
                    uri("free_groupoid"), rec(mutableMap(
                                    uri("objects"), uri(MACH_MACHINE_TID),
                                    uri("morphisms"), uri(URI_TID),
                                    uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                    uri(ID), uri("."),
                                    uri(INV), uri("..")),
                            GROUP_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                    uri(ID), uri(".")),
                            MONOID_THEORY_TID, null)));
        }
        return rec0();
    }

    private void loadInt() {
        entry(AS_INST_TID.dom(INT_TID).rng(STR_TID), AS_INST_TID.dom(STR_TID).rng(INT_TID));
        entry(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), MINUS_INST_TID.dom(INT_TID).rng(INT_TID), commutative, right_distributive, action);
        entry(MULT_INST_TID.dom(INT_TID).rng(INT_TID), DIV_INST_TID.dom(INT_TID).rng(INT_TID), commutative, right_distributive, action);
        entry(MINUS_INST_TID.dom(INT_TID).rng(INT_TID), PLUS_INST_TID.dom(INT_TID).rng(INT_TID), action);
        entry(DIV_INST_TID.dom(INT_TID).rng(INT_TID), MULT_INST_TID.dom(INT_TID).rng(INT_TID));
        entry(NEG_INST_TID.dom(INT_TID).rng(INT_TID), NEG_INST_TID.dom(INT_TID).rng(INT_TID), involution);
        entry(ZERO_INST_TID.dom(INT_TID).rng(INT_TID), null, absorbing, idempotent);
        entry(ONE_INST_TID.dom(INT_TID).rng(INT_TID), null, idempotent);
        entry(GT_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(GTE_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(LT_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(LTE_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(MEAN_INST_TID.dom(INT_TID.maybeSome()).rng(REAL_TID), null, magmadic, commutative);
        entry(SUM_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), null, monoidic, commutative, right_distributive);
        entry(PROD_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), null, monoidic, commutative, right_distributive);
    }

    private void loadReal() {
        entry(AS_INST_TID.dom(REAL_TID).rng(STR_TID), AS_INST_TID.dom(STR_TID).rng(REAL_TID));
        entry(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID), MINUS_INST_TID.dom(REAL_TID).rng(REAL_TID), commutative, right_distributive, action);
        entry(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID), DIV_INST_TID.dom(REAL_TID).rng(REAL_TID), commutative, right_distributive, action);
        entry(MINUS_INST_TID.dom(REAL_TID).rng(REAL_TID), PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID), action);
        entry(DIV_INST_TID.dom(REAL_TID).rng(REAL_TID), MULT_INST_TID.dom(REAL_TID).rng(REAL_TID));
        entry(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID), NEG_INST_TID.dom(REAL_TID).rng(REAL_TID), involution);
        entry(INV_INST_TID.dom(REAL_TID).rng(REAL_TID), INV_INST_TID.dom(REAL_TID).rng(REAL_TID), involution);
        entry(GT_INST_TID.dom(REAL_TID).rng(BOOL_TID), null, right_distributive);
        entry(GTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), null, right_distributive);
        entry(LT_INST_TID.dom(REAL_TID).rng(BOOL_TID), null, right_distributive);
        entry(LTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), null, right_distributive);
        entry(MEAN_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), null, magmadic, commutative);
        entry(SUM_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), null, monoidic, commutative, right_distributive);
        entry(PROD_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), null, monoidic, commutative, right_distributive);
    }

    private void loadStr() {
        entry(AS_INST_TID.dom(STR_TID).rng(BYTES_TID), AS_INST_TID.dom(BYTES_TID).rng(STR_TID));
        entry(AS_INST_TID.dom(STR_TID).rng(BOOL_TID), AS_INST_TID.dom(BOOL_TID).rng(STR_TID));
        entry(AS_INST_TID.dom(STR_TID).rng(INT_TID), AS_INST_TID.dom(INT_TID).rng(STR_TID));
        entry(AS_INST_TID.dom(STR_TID).rng(REAL_TID), AS_INST_TID.dom(REAL_TID).rng(STR_TID));
        entry(AS_INST_TID.dom(STR_TID).rng(URI_TID), AS_INST_TID.dom(URI_TID).rng(STR_TID));
        entry(REVERSE_INST_TID.dom(STR_TID).rng(STR_TID), REVERSE_INST_TID.dom(STR_TID).rng(STR_TID), involution);
        entry(ZERO_INST_TID.dom(STR_TID).rng(STR_TID), null, unit, idempotent);
        entry(SPLIT_INST_TID.dom(STR_TID).rng(LST_TID), MERGE_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID));
        entry(MERGE_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), SPLIT_INST_TID.dom(STR_TID).rng(LST_TID), monoidic);
        entry(GT_INST_TID.dom(STR_TID).rng(BOOL_TID), null, poset);
        entry(GTE_INST_TID.dom(STR_TID).rng(BOOL_TID), null, poset);
        entry(LT_INST_TID.dom(STR_TID).rng(BOOL_TID), null, poset);
        entry(LTE_INST_TID.dom(STR_TID).rng(BOOL_TID), null, poset);
        entry(PLUS_INST_TID.dom(STR_TID).rng(STR_TID), null, monoidic);
        entry(SUM_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), null, monoidic);
        entry(UCASE_INST_TID.dom(STR_TID).rng(STR_TID), null, idempotent);
        entry(LCASE_INST_TID.dom(STR_TID).rng(STR_TID), null, idempotent);
    }

    private void loadBool() {
        entry(AS_INST_TID.dom(BOOL_TID).rng(STR_TID), AS_INST_TID.dom(STR_TID).rng(BOOL_TID));
        entry(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID), null, commutative, monoidic, idempotent);
        entry(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID), null, commutative, monoidic, idempotent);
        entry(NOT_INST_TID.dom(BOOL_TID).rng(BOOL_TID), NOT_INST_TID.dom(BOOL_TID).rng(BOOL_TID), involution);
    }

    private void loadBytes() {
        entry(AS_INST_TID.dom(BYTES_TID).rng(STR_TID), AS_INST_TID.dom(STR_TID).rng(BYTES_TID));
        entry(ZERO_INST_TID.dom(BYTES_TID).rng(BYTES_TID), null, unit, idempotent);
        entry(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID), null, monoidic);
    }

    private void loadUri() {
        entry(AS_INST_TID.dom(URI_TID).rng(STR_TID), AS_INST_TID.dom(STR_TID).rng(URI_TID));
        entry(AS_INST_TID.dom(URI_TID).rng(REC_TID), AS_INST_TID.dom(REC_TID).rng(URI_TID));
        entry(REVERSE_INST_TID.dom(URI_TID).rng(URI_TID), REVERSE_INST_TID.dom(URI_TID).rng(URI_TID), involution);
        entry(PLUS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), null, monoidic);
        entry(MULT_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), null, monoidic);
        entry(SUM_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), null, monoidic);
        entry(PROD_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), null, monoidic);
    }

    private void loadLst() {
        entry(AS_INST_TID.dom(LST_TID).rng(REC_TID), AS_INST_TID.dom(REC_TID).rng(LST_TID));
        entry(REVERSE_INST_TID.dom(LST_TID).rng(LST_TID), REVERSE_INST_TID.dom(LST_TID).rng(LST_TID), involution);
        entry(ZERO_INST_TID.dom(LST_TID).rng(LST_TID), null, unit, idempotent);
        entry(PLUS_INST_TID.dom(LST_TID).rng(LST_TID), null, monoidic);
        entry(SUM_INST_TID.dom(LST_TID.maybeSome()).rng(LST_TID), null, monoidic);
    }

    private void loadRec() {
        entry(AS_INST_TID.dom(REC_TID).rng(LST_TID), AS_INST_TID.dom(LST_TID).rng(REC_TID));
        entry(AS_INST_TID.dom(REC_TID).rng(URI_TID), AS_INST_TID.dom(URI_TID).rng(REC_TID));
        entry(ZERO_INST_TID.dom(REC_TID).rng(REC_TID), null, unit, idempotent);
        entry(REVERSE_INST_TID.dom(REC_TID).rng(REC_TID), REVERSE_INST_TID.dom(REC_TID).rng(REC_TID), involution);
        entry(PLUS_INST_TID.dom(REC_TID).rng(REC_TID), null, monoidic);
        entry(MPLUS_INST_TID.dom(REC_TID).rng(REC_TID), null, monoidic);
        entry(SUM_INST_TID.dom(REC_TID.maybeSome()).rng(REC_TID), null, monoidic);
    }
}
