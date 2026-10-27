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

import studio.phaseshift.metatron.isa.m.Derivation;

import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.Law.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The law table of {@code /m} — the base types' declared PROCESS laws, collocated.
 *
 * <p>An instruction's declared laws are a fact about the instruction, and each family keeps its process rows here
 * ({@code declared ∩ process}, served to {@code morphism::T>>law}). The base types' STRUCTURAL laws — the theory
 * instances a carrier models, served to {@code object::T>>law} — are not here: a carrier declares its algebra at
 * the type itself ({@code mTypes.declareAlgebras()}), which is what makes a type definition an algebraic
 * specification. {@link #typeLawsUncached(Type)} therefore used to hold one branch per carrier and now returns the
 * empty rec: {@code LawTable.typeLaws(Type)} serves the {@code ?lawq} declaration, and this table is no longer its
 * fallback.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mInstSetLawTable extends LawTable {

    /**
     * The singleton — the base types have exactly one law table.
     */
    public static final mInstSetLawTable INSTANCE = new mInstSetLawTable();

    private mInstSetLawTable() {
        super(M_ISA_TID);
        loadInt();
        loadReal();
        loadStr();
        loadBool();
        loadBytes();
        loadUri();
        loadLst();
        loadRec();
        loadCode();
        loadAll();
    }

    /**
     * NO CARRIER LAW IS RESTATED HERE. Every base carrier declares its algebra at its own type
     * ({@code mTypes.declareAlgebras()} {@code -> Type.Builder.declare -> ?lawq}), and
     * {@link LawTable#typeLaws(Type)} serves that declaration first. The empty rec is the correct answer for a type
     * that declares nothing: a type models exactly what is written on it, and the algebra of the /m carriers is
     * written on them. The hook stays so the contract — and any family that still writes its carriers' laws in a
     * table, as {@code mathInstSetLawTable} does for nat and cmplx — keeps one place to answer.
     */
    @Override
    protected Rec typeLawsUncached(final Type type) {
        return rec0();
    }

    private void loadInt() {
        // NO `as` ROW HERE, AND NONE FOR int->str's OPPOSITE EITHER: int->str is the SOUND direction (print an int,
        // read the printed str back) and DECLARES its inverse beside itself in mInsts.ints(); str->int is LOSSY
        // ("007" -> 7 -> "7") and declares none. The pair's two rows are deleted.
        // the plus/minus pair is DERIVED: int models an add_group (op=plus, inv=neg) and minus is DEFINED as
        // plus applied to its inverted argument, so neither side's inverse is declared
        // plus, mult and minus DECLARE their laws (and, for mult, its inverse; for minus, its defining equation)
        // at their registration (mInsts.ints(), InstBuilder) -> ?lawq; no row here
        // the ONE inverse no algebra states: int models a mult_monoid, not a group (5.div(2).mult(2) is 4), so
        // mult's retraction to div is declared beside mult itself
        // no inverse: int div truncates, so x.div(a).mult(a) is not x (5.div(2).mult(2) is 4); mult's inverse is div
        // THE COMPARISONS ARE FILTERS, and a filter applies pointwise: the split axiom plus the apply axiom give
        // (a+b)c = ac + bc for it, so `right_distributive` here is TRUE in the stream ring -- not an error, and not
        // a scalar claim ((a+b) > c is not a > c or b > c). It is commented out because no DECLARED theory licenses
        // it yet, and an axiom-driven table would prune it as unlicensed; do not delete it for that reason.
        // Lemma 1 denies the law for REDUCERS, where Thm 19 gives the semi-right form (a+b)c = (ac+bc)c -- so the
        // sum/prod folds are the ones that need relabeling (semi_right_distributive), not these.
    }

    private void loadReal() {
        // `as` DECLARES ITSELF NOW: real->str is SOUND (the printed decimal reads back to the same double) and
        // states its inverse at its registration (mInsts.reals()); str->real is LOSSY and states none. No row here.
        // real is a field, so both its group sides DERIVE their inverse pairs: plus/minus from add_group, and
        // mult/div from mult_group (op=mult, inv=inv) together with div's definition div ≡ mult ∘ inv.
        // minus and div DECLARE their defining equations at their registration (mInsts.reals(), InstBuilder) -> ?lawq
    }

    private void loadStr() {
        // EVERY ROW THIS METHOD CARRIED HAS RETIRED TO THE INSTRUCTIONS THEMSELVES (mInsts.strs()). str is the
        // SOUND side of exactly ONE of its `as` pairs -- str->bytes, whose bytes are that str again -- and that
        // direction declares its inverse beside itself; str->int/real/bool/uri are LOSSY (only SOME strs are the
        // type: "007" -> 7 -> "7", and only a legal uri is a uri) and declare none. `split` and `merge` preserve
        // nothing in either direction -- an empty segment does not survive the round trip -- so neither declares an
        // inverse, and each keeps the laws its row carried: split pure, merge monoidic and pure.
    }

    private void loadBool() {
        // bool->str is SOUND ("true"/"false" reads back to the same bool) and declares its inverse beside itself
        // (mInsts.bools()); str->bool is LOSSY (false for every str that does not spell true) and declares none.
    }

    private void loadBytes() {
        // bytes->str is LOSSY (arbitrary bytes need not be UTF-8 text) and declares NO inverse; str->bytes is the
        // SOUND direction and declares its inverse beside itself (mInsts.strs()).
    }

    private void loadUri() {
        // uri->str is SOUND (a uri prints and the same uri parses back) and declares its inverse beside itself
        // (mInsts.uris()); str->uri is LOSSY (only a legal uri is one) and declares none.
        // uri->rec KEEPS ITS ROW: it is the pair's SOUND direction (a component rec reads back as its uri) and its
        // body cannot leave Uri.UriType yet -- it calls Uri.Helper.parseQ, package-private there. rec->uri is LOSSY
        // (only a rec of uri components survives) and its row is deleted: no row declares it any more.
        entry(AS_INST_TID.dom(URI_TID).rng(REC_TID), AS_INST_TID.dom(REC_TID).rng(URI_TID), pure);
    }

    private void loadLst() {
        // lst<->rec preserves nothing in EITHER direction (the round trip re-keys and reorders what it carries), so
        // both directions declare NO inverse and neither has a row: lst->rec declares itself beside lst's
        // operations (mInsts.lsts(), no inverse) and rec->lst does the same in mInsts.recs().
        // kmerge: the fold of the commutative monoid of sorted lsts (identity []) -- a merge of one merge is that
        // merge, and the merge order is free
        entry(KMERGE_INST_TID.dom(LST_TID.poly(InstSet.A.maybeSome()).maybeSome()).rng(LST_TID.poly(InstSet.A.maybeSome())), null, monoidic, commutative, pure);
    }

    private void loadRec() {
        // NO `as` ROW HERE: rec->lst and rec->uri are the LOSSY directions of their pairs and declare NO inverse
        // anywhere (mInsts.recs()). uri->rec, their sound counterpart, keeps its row in loadUri; lst->rec declares
        // itself in mInsts.lsts() with no inverse.
    }

    private void loadCode() {
        // the additive normal form (n-ary branch): commutative, associative
        entry(BRANCH_INST_TID.dom(CODE_TID).rng(CODE_TID), null, commutative, monoidic, pure);
        // the additive inverse (coeff −1): self-inverse
        entry(NEG_INST_TID.dom(CODE_TID).rng(CODE_TID), null, involution, pure);
        entry(END_INST_TID.dom(CODE_TID).rng(CODE_TID), null, unit, absorbing, pure);
        entry(ID_INST_TID.dom(CODE_TID).rng(CODE_TID), null, unit, idempotent, pure);
        // zero = end/noobj: the additive identity, absorbing in compose
        // one = id: the multiplicative identity
    }

    /**
     * The folds declared over EVERY carrier. Each is DEFINED as a pointwise map into a commutative monoid's fold, and
     * that definition is where its combiner is read from ({@link LawTable#combiner}): no combiner is declared.
     */
    private void loadAll() {
        // count: the coefficient sum -- every obj contributes its multiplicity, so it is map(1) folded by int's sum
        entry(COUNT_INST_TID.dom(ALL_STAR).rng(INT_TID), null,
                new Derivation(start_(uri(InstSet.A)).count_(), start_(uri(InstSet.A)).map_(jnt(1)).sum_()), pure);
        // order: the bag's normal form -- each obj a sorted singleton, k-way merged by the same selector
        entry(ORDER_INST_TID.dom(InstSet.A.maybeSome()).rng(LST_TID.poly(InstSet.A.maybeSome())), null,
                new Derivation(start_(uri(InstSet.A)).order_(uri(InstSet.B)), start_(uri(InstSet.A)).split_(lst(id_().tryToInst())).kmerge_(uri(InstSet.B))), pure);
        // group: the keyed fold -- no laws of its own; its combiner is POINTWISE, computed from the folds its spec holds
        entry(GROUP_INST_TID.dom(ALL_STAR).rng(REC_TID), null, pure);
    }
}
