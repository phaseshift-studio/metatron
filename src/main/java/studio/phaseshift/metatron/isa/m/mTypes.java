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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE /m TYPE DECLARATIONS — the sibling of {@link mInsts}, and the counterpart of {@code catTypes}: the instset's
 * file stays the instset (constants, sugar, the aggregation seam), while its TYPES are declared here, one line per
 * carrier, in the same shape {@code catTypes} uses.
 * <p>
 * A type is declared by its ADDRESSES ({@code tid} = the representation it refines, {@code vid} = its name), its
 * PREDICATE (the refinement) and — the axis this whole effort has been moving — its ALGEBRA ({@code law => ...}),
 * the theory instances the type models. The algebra is declared with {@code Type.Builder} and persisted to
 * {@code ?lawq} by the drain that runs when this set is mounted, which is why it can be written here, before any
 * space exists.
 * <p>
 * The ADDRESSES below are declared against the {@code Tokens} addresses only, so the fields depend on nothing in
 * {@link mInstSet} — no class-initialisation cycle, whatever loads first. The ALGEBRAS are the exception and
 * deliberately a method ({@link #declareAlgebras()}): a rec names instruction and theory addresses that only exist
 * once {@code mInstSet} has initialised, so it can be written here but not <em>initialised</em> here.
 * {@code mInstSet} re-exports the types by name for the (many) call sites that read {@code mInstSet.INT_TYPE}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mTypes {

    private mTypes() {
    }

    public static final Type BOOL_TYPE = Type.Builder.build().tid(BOOL_TID).vid(BOOL_TID).create();

    public static final Type BYTES_TYPE = Type.Builder.build().tid(BYTES_TID).vid(BYTES_TID).create();

    public static final Type INT_TYPE = Type.Builder.build().tid(INT_TID).vid(INT_TID).create();

    public static final Type REAL_TYPE = Type.Builder.build().tid(REAL_TID).vid(REAL_TID).create();

    public static final Type STR_TYPE = Type.Builder.build().tid(STR_TID).vid(STR_TID).create();

    public static final Type URI_TYPE = Type.Builder.build().tid(URI_TID).vid(URI_TID).create();

    public static final Type REL_TYPE = Type.Builder.build().tid(REL_TID).vid(REL_TID).create();

    public static final Type LST_TYPE = Type.Builder.build().tid(LST_TID).vid(LST_TID).create();

    public static final Type REC_TYPE = Type.Builder.build().tid(REC_TID).vid(REC_TID).create();

    public static final Type INST_TYPE = Type.Builder.build().tid(M_ISA_INST_TID).vid(M_ISA_INST_TID).create();

    public static final Type CODE_TYPE = Type.Builder.build().tid(CODE_TID).vid(CODE_TID).create();

    public static final Type NOOBJ_TYPE = Type.Builder.build()
            .tid(NOOBJ_TID.zero()).vid(NOOBJ_TID.zero())
            .predicate((lhs, inst) -> noobj())
            .create();

    /**
     * THE MACHINE'S ADDRESS — {@code machInstSet.MACH_MACHINE_TID} is exactly this expression, spelled here rather
     * than imported. It is the one address this file does not read from {@code Tokens}, and the reason is the same
     * class-initialisation cycle {@link #declareAlgebras()} exists to avoid, one instset over: reading that class's
     * constant while this set initialises observes the field at its default, because the machine's own set has not
     * reached it. The ADDRESS is the fact and it is a {@code Tokens} fact ({@code machInstSet} derives it the same
     * way), so {@code declareAlgebras()} can key the machine's type vid ({@code /m/mach/machine}) without touching
     * that class — and the machine's set still drains the declaration when it mounts.
     */
    private static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");

    /**
     * DECLARE EVERY CARRIER'S ALGEBRA — one {@code Type.Builder.declare} per carrier, holding the theory instances
     * the carrier models, keyed by instance name ({@code ring}, {@code add_group}, …). Each role points at the LIVE
     * instruction through an {@code auto_from_} ({@code !*}) pointer, so the block names the operation rather than
     * copying it, and each instance is typed by its theory ({@code ring_theory::T}, …).
     * <p>
     * THIS IS A METHOD, NEVER STATIC FIELD INITIALIZERS: the recs name the instruction tids ({@code PLUS_INST_TID},
     * …, initialized in {@link mInstSet}) and the theory tids ({@code RING_THEORY_TID}, …). A field here would
     * close a class-initialisation cycle — this class initialises while {@code mInstSet} does, so a field reading
     * its constants can observe their defaults. {@code mInstSet}'s end-of-class static block invokes this after
     * every constant exists.
     * <p>
     * The declaration is STAGED only; {@code ?lawq} is the store of record, written by the drain that runs when the
     * set is mounted, and {@code LawTable.typeLaws} serves it from there. The law table restates none of it.
     */
    public static void declareAlgebras() {
        // int: a ring — an add_group (plus/neg) and a mult_monoid (mult/1, no mult_group: integer division
        // truncates, so 5.div(2).mult(2) is 4) — with the monoid constituents spelled out
        Type.Builder.declare(INT_TYPE, rec(mutableMap(
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
                        MONOID_THEORY_TID, null))));
        // real: a field — a ring where the non-zero mult elements are a group (inv = 1/x); plus the constituents
        Type.Builder.declare(REAL_TYPE, rec(mutableMap(
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
                        MONOID_THEORY_TID, null))));
        // bool: a Boolean algebra — or=join(PLUS), and=meet(MULT), not=complement — a complemented distributive
        // lattice and a rig (idempotent semiring) with bottom=false / top=true
        Type.Builder.declare(BOOL_TYPE, rec(mutableMap(
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
                        LATTICE_THEORY_TID, null))));
        // str: a monoid under concatenation (PLUS), identity ""
        Type.Builder.declare(STR_TYPE, rec(mutableMap(
                uri("concat_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(STR_TID).rng(STR_TID)).tryToInst(),
                                uri(ID), str("")),
                        MONOID_THEORY_TID, null))));
        // uri: a monoid under extension (mult), identity the empty uri <>; branch (plus) is a commutative
        // semigroup with no identity — a/b + <> = {a/b,<>}, never a/b, so there is no zero object, only a one.
        Type.Builder.declare(URI_TYPE, rec(mutableMap(
                uri("mult_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(MULT_INST_TID.dom(URI_TID).rng(URI_TID)).tryToInst(),
                                uri(ID), uri("")),
                        MONOID_THEORY_TID, null))));
        // bytes: a monoid under concatenation (PLUS), identity the empty buffer
        Type.Builder.declare(BYTES_TYPE, rec(mutableMap(
                uri("concat_monoid"), rec(mutableMap(
                                uri(OP), auto_from_(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID)).tryToInst(),
                                uri(ID), bytes(new byte[0])),
                        MONOID_THEORY_TID, null))));
        // code: the function/stream ring — add=branch (parallel split/merge), mul=compose (serial),
        // zero=end (the 0-function, ≡ id{0}::T), one=id (the parametric identity), inv=additive inverse (coeff −1)
        Type.Builder.declare(CODE_TYPE, rec(mutableMap(
                uri("ring"), rec(mutableMap(
                                uri(ADD), auto_from_(PLUS_INST_TID.dom(CODE_TID).rng(CODE_TID)).tryToInst(),
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
                        MONOID_THEORY_TID, null))));
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
        Type.Builder.declare(carrier(MACH_MACHINE_TID), rec(mutableMap(
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
                        MONOID_THEORY_TID, null))));
    }

    /**
     * THE CARRIER A DECLARATION IS KEYED BY — {@code Type.Builder.declare} reads only the {@code vid}, so a carrier
     * whose type this class does not itself declare (the machine, built in {@code machInstSet.setup()}) is named by
     * its address. A BARE type is all the declaration needs, and building it here (rather than holding a field)
     * keeps the cross-instset address the only cross-instset fact on this class's initialisation path.
     */
    private static Type carrier(final fURI tid) {
        return Type.Builder.build().tid(tid).vid(tid).create();
    }
}
