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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.FIELD_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.GROUP_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.Law.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.MONOID_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.RIG_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.RING_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.CMPLX_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.CONJUGATE_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.NAT_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The law table of {@code /m/math} — the math instset's declared laws, collocated.
 *
 * <p>A sibling to {@link mInstSetLawTable}: where {@code /m}'s table holds the base types (int, real, str,
 * …), this table holds the math types. The split matters because a refinement can carry <em>different</em>
 * structure than its base: {@code nat::T} (a positive {@code int}) is a <b>rig</b> — an additive monoid
 * (identity {@code 0}) with no additive inverse — whereas {@code int::T} is a <b>ring</b> (an additive
 * <em>group</em> via {@code neg}). Both share the same {@code plus}/{@code mult} operations, so the two
 * tables let the category reasoner distinguish {@code rig_theory::T} on {@code nat} from
 * {@code ring_theory::T} on {@code int}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mathInstSetLawTable extends LawTable {

    /**
     * The singleton — the math types have exactly one law table.
     */
    public static final mathInstSetLawTable INSTANCE = new mathInstSetLawTable();

    private mathInstSetLawTable() {
        super(MATH_ISA_TID);
        loadCmplx();
    }

    private void loadCmplx() {
        entry(PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), MINUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), commutative, right_distributive, action);
        entry(MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), DIV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), commutative, right_distributive, action);
        entry(MINUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID),
                new Derivation(start_(uri(InstSet.A)).minus_(uri(InstSet.B)), start_(uri(InstSet.A)).plus_(map_(uri(InstSet.B)).neg_())),
                action);
        entry(DIV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID),
                new Derivation(start_(uri(InstSet.A)).div_(uri(InstSet.B)), start_(uri(InstSet.A)).mult_(map_(uri(InstSet.B)).inv_())),
                right_distributive, action);
        entry(NEG_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), NEG_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), involution);
        entry(INV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), INV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), involution);
        entry(CONJUGATE_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), CONJUGATE_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), involution);
        entry(ZERO_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), null, absorbing, idempotent);
        entry(ONE_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID), null, idempotent);
    }

    @Override
    protected Rec typeLawsUncached(final Type type) {
        final fURI t = type.vid().basePath();
        if (t.equals(NAT_TID)) {
            // nat: a rig (semiring) — (add=+, mul=·, zero=0, one=1) with an additive monoid, NOT an additive
            // group: nat has no negation, so its add_monoid has no inverse, and it models rig_theory not
            // ring_theory. The operations are borrowed from int (plus/mult over int), since nat refines int.
            return rec(mutableMap(
                    uri("rig"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(Tokens.ZERO), jnt(0),
                                    uri(Tokens.ONE), jnt(1)),
                            RIG_THEORY_TID, null),
                    uri("add_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(ID), jnt(0)),
                            MONOID_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                    uri(ID), jnt(1)),
                            MONOID_THEORY_TID, null)));
        }
        if (t.equals(CMPLX_TID)) {
            // cmplx: a field — (add=+, mul=·, zero=0, one=1, inv=1/z) and its constituent groups/monoids: the
            // additive group (inv=neg) and the multiplicative group (inv=1/z), exactly like real::T.
            return rec(mutableMap(
                    uri("field"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(Tokens.ZERO), lst(real(0.0), real(0.0)),
                                    uri(Tokens.ONE), lst(real(1.0), real(0.0)),
                                    uri(INV), auto_from_(INV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst()),
                            FIELD_THEORY_TID, null),
                    uri("ring"), rec(mutableMap(
                                    uri(ADD), auto_from_(PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(MUL), auto_from_(MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(Tokens.ZERO), lst(real(0.0), real(0.0)),
                                    uri(Tokens.ONE), lst(real(1.0), real(0.0))),
                            RING_THEORY_TID, null),
                    uri("add_group"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(ID), lst(real(0.0), real(0.0)),
                                    uri(INV), auto_from_(NEG_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("mult_group"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(ID), lst(real(1.0), real(0.0)),
                                    uri(INV), auto_from_(INV_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst()),
                            GROUP_THEORY_TID, null),
                    uri("add_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(PLUS_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(ID), lst(real(0.0), real(0.0))),
                            MONOID_THEORY_TID, null),
                    uri("mult_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(MULT_INST_TID.dom(CMPLX_TID).rng(CMPLX_TID)).tryToInst(),
                                    uri(ID), lst(real(1.0), real(0.0))),
                            MONOID_THEORY_TID, null)));
        }
        return rec0();
    }
}
