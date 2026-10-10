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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/math/cat")
public class catInstSet extends AbstractInstSet {

    public static final fURI CAT_ISA_TID = MATH_ISA_TID.extend("cat");
    public static final fURI CATEGORY_TID = CAT_ISA_TID.extend("category");
    public static final fURI MORPHISM_TID = CATEGORY_TID.extend("morphism");
    public static final fURI OBJECT_TID = CATEGORY_TID.extend("object");
    public static final fURI THEORY_TID = CAT_ISA_TID.extend("theory");
    public static final fURI RING_THEORY_TID = THEORY_TID.extend("ring_theory");
    public static final fURI GROUP_THEORY_TID = THEORY_TID.extend("group_theory");
    public static final fURI MONOID_THEORY_TID = THEORY_TID.extend("monoid_theory");
    public static final fURI FIELD_THEORY_TID = THEORY_TID.extend("field_theory");
    public static final fURI RIG_THEORY_TID = THEORY_TID.extend("rig_theory");
    public static final fURI BOOLEAN_THEORY_TID = THEORY_TID.extend("boolean_theory");
    public static final fURI LATTICE_THEORY_TID = THEORY_TID.extend("lattice_theory");
    public static final fURI SEMILATTICE_THEORY_TID = THEORY_TID.extend("semilattice_theory");
    public static final fURI NEAR_RING_THEORY_TID = THEORY_TID.extend("near_ring_theory");
    public static final fURI LAW_TID = CAT_ISA_TID.extend("law");
    public static final fURI CLASS_TID = CAT_ISA_TID.extend("class");


    public catInstSet() {
        super(new LinkedHashMap<>(Map.<Obj, Obj>of(uri(PATTERN), uri(CAT_ISA_TID.extend(ALL)), uri(QPROC), lst())), INSTSET_TID, CAT_ISA_TID);
    }


    public static Type CATEGORY_TYPE;

    /**
     * {@code morphism::T} — the edge block: the morphism's own algebra
     */
    public static Type MORPHISM_TYPE;

    /// //////////////////////////////////////////////////////////////
    // the algebraic theories — a theory is a named tuple of op-edges (role => op-tid); algebraic_theory::T
    // is the nominal (structure-blind) super-type grouping them for bookkeeping and inference

    /**
     * {@code theory::T} — nominal super-type of the algebraic theories
     */
    public static Type THEORY_TYPE;

    /**
     * {@code ring_theory::T} — (add, mul, zero, one): additive and multiplicative ops with their identities
     */
    public static Type RING_THEORY_TYPE;

    /**
     * {@code group_theory::T} — (op, id, inv): an operation with its identity and inverse
     */
    public static Type GROUP_THEORY_TYPE;

    /**
     * {@code monoid_theory::T} — (op, id): an associative operation with its identity
     */
    public static Type MONOID_THEORY_TYPE;
    public static Type RIG_THEORY_TYPE;
    public static Type BOOLEAN_THEORY_TYPE;
    public static Type SEMILATTICE_THEORY_TYPE;
    public static Type NEAR_RING_THEORY_TYPE;
    public static Type FIELD_THEORY_TYPE;
    public static Type LATTICE_THEORY_TYPE;
    /**
     * {@code law::T} — the process-law union: one label from the morphism's declared process laws
     */
    public static Type LAW_TYPE;

    /**
     * {@code class::T} — the set-theoretic class union: one label from the morphism's set-theoretic classification
     */
    public static Type CLASS_TYPE;

    /**
     * {@code object::T} — the vertex block: the algebraic theories the object models, keyed by the
     * user's name for each theory instance (an object can model several — even several of one theory)
     */
    public static Type OBJECT_TYPE;


    public record Finding(List<Inst> insts, Position kind, String reason) {
    }


    public void setup() {
        this.selfTID(CAT_ISA_TID);
        // self-register the per-instset law tables before any object/morphism resolves laws through the router
        mInstSetLawTable.INSTANCE.getClass();
        mathInstSetLawTable.INSTANCE.getClass();
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(PATTERN), uri(CAT_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        docWrap(CATEGORY_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(CATEGORY_TID)
                                        .isaPredicate(rec(
                                                uri(OBJ), ALL_TYPE,
                                                uri(INSTSET).maybe(), lst(URI_TYPE),
                                                uri(ORBIT).maybe(), T(CATEGORY_TID)))
                                        .create(), Map.of(
                                        uri(OBJ), "the type \\(X\\) lifted to a vertex: \\(\\mathrm{obj} = X\\)",
                                        uri(INSTSET).maybe(), "uri patterns \\(P\\) selecting the instsets \\(\\mathcal{S}\\) from which the category's morphisms are generated",
                                        uri(ORBIT).maybe(), "the reversible-core component: \\(\\mathrm{orbit}(X) = \\{Y : X \\sim^{*} Y\\}\\), where \\(X \\sim Y \\iff X \\to Y \\wedge Y \\to X\\)"),
                                "a categorical structure to be refined into an object or morphism"),
                        docWrap(LAW_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(LAW_TID)
                                .isaPredicate(union_(Arrays.stream(Law.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst())
                                .create(), "the process laws a morphism obeys — the union of the process-law labels"),
                        docWrap(CLASS_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(CLASS_TID)
                                .isaPredicate(union_(Arrays.stream(Class.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst())
                                .create(), "the set-theoretic class of a morphism — the union of the class labels"),
                        docWrap(MORPHISM_TYPE = Type.Builder.build()
                                        .tid(CATEGORY_TID)
                                        .vid(MORPHISM_TID)
                                        .isaPredicate(rec(
                                                uri(OBJ), INST_TYPE,
                                                uri(FORM), union_(Arrays.stream(Inst.Form.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst(),
                                                uri(SRC).maybe(), T(OBJECT_TID),
                                                uri(TRGT).maybe(), T(OBJECT_TID),
                                                uri(ANALYSIS).maybe(), T(REC_TID),
                                                uri(LAW).maybe(), lst(LAW_TYPE.c(cInt.SOME())),
                                                uri(CLASS).maybe(), lst(CLASS_TYPE.c(cInt.SOME())),
                                                uri(DERIVATION).maybe(), rec(uri(LHS), CODE_TYPE, uri(RHS), CODE_TYPE)))
                                        .constructor(arg -> {
                                            final Rec objInstRec = arg.isRec() && arg.asRec().has(OBJ) ? arg.asRec() : rec(uri(OBJ), arg);
                                            final Inst inst = objInstRec.at(OBJ).asInst();
                                            final LawTable.Entry entry = mInstSetLawTable.INSTANCE.lookup(inst.tid());
                                            final Map<Obj, Obj> block = new LinkedHashMap<>();
                                            block.put(uri(NAME), uri(inst.tid().name()));
                                            block.put(uri(FORM), uri(Inst.Form.of(inst).name()));
                                            final Lst clazz = catInstSet.clazz(inst);
                                            if (0 < clazz.count())
                                                block.put(uri(CLASS), clazz);
                                            block.put(uri(SRC), auto_(instLambda(o -> rec(mutableMap(uri(OBJ), inst.dom()), OBJECT_TID, null)).tryToInst()));
                                            block.put(uri(TRGT), auto_(instLambda(o -> rec(mutableMap(uri(OBJ), inst.rng()), OBJECT_TID, null)).tryToInst()));
                                            block.put(uri(ANALYSIS), auto_(instLambda(ALL, ALL, o -> {
                                                final Map<Obj, Obj> analysis = new LinkedHashMap<>();
                                                analysis.put(uri(FAMILY), instLambda(inst.tid(), ALL, o2 -> catInstSet.family(inst)).tryToInst());
                                                analysis.put(uri(CONTESTED), instLambda(inst.tid(), ALL, o2 -> catInstSet.contested(inst)).tryToInst());
                                                analysis.put(uri(ORBIT), instLambda(inst.tid(), ALL, o2 -> catInstSet.orbit(inst)).tryToInst());
                                                analysis.put(uri(POSITION), instLambda(inst.tid(), ALL, o2 -> catInstSet.position(inst)).tryToInst());
                                                if (null != entry && null != entry.inverse())
                                                    analysis.put(uri(INVERSE), uri(entry.inverse()));
                                                return rec(analysis);
                                            })).tryToInst());
                                            block.putAll(objInstRec.jvm());
                                            if (null != entry && !entry.laws().isEmpty())
                                                block.put(uri(LAW), entry.laws());
                                            if (null != entry && null != entry.derivation())
                                                block.put(uri(DERIVATION), rec(uri(LHS), entry.derivation().lhs(), uri(RHS), entry.derivation().rhs()));
                                            return rec(block).clone().selfTID(MORPHISM_TID);
                                        })
                                        .create(),
                                mutableMap(uri(FORM), "the n-tid coefficient shape \\((c_{\\mathrm{dom}}, c_{\\mathrm{rng}})\\) in regex notation: \\(\\mathrm{mapper} = (1,1),\\; \\mathrm{filter} = (1, ?),\\; \\mathrm{reducer} = (^{\\ast}, 1),\\; \\mathrm{flatmapper} = (1, ^{+}),\\; \\ldots\\)",
                                        uri(SRC), "the source object the morphism leaves: \\(\\mathrm{src}(f) = \\mathrm{dom}(f)\\)",
                                        uri(TRGT), "the target object the morphism enters: \\(\\mathrm{trgt}(f) = \\mathrm{rng}(f)\\)",
                                        uri("analysis/position").maybe(), "the edge's place among its siblings: \\(\\mathrm{position}(f) \\subseteq \\{\\mathrm{duplicate}, \\mathrm{ambiguous}, \\mathrm{incomparable}, \\mathrm{coupling}, \\mathrm{isochain}, \\mathrm{retract}\\}\\)",
                                        uri("analysis/contested").maybe(), "the witness edges behind the ambiguous/incomparable labels: \\(\\mathrm{contested}(f) = \\{g : \\mathrm{trgt}(g) = \\mathrm{trgt}(f) \\wedge \\mathrm{src}(g) \\perp \\mathrm{src}(f)\\} \\cup \\{g : \\mathrm{src}(g) = \\mathrm{src}(f) \\wedge \\mathrm{trgt}(g) \\perp \\mathrm{trgt}(f)\\}\\)",
                                        uri("analysis/orbit").maybe(), "the reversible-core component of the morphism's dom: \\(\\mathrm{orbit}(f) = \\{X : \\mathrm{dom}(f) \\sim^{*} X\\}\\)",
                                        uri("analysis/family").maybe(), "the same-name siblings: \\(\\mathrm{family}(f) = \\{g : \\mathrm{op}(g) = \\mathrm{op}(f)\\}\\)",
                                        uri("analysis/inverse").maybe(), "the opposing edge \\(f^{-1}\\) with \\(f \\cdot f^{-1} = 1\\)",
                                        uri(LAW), "the declared process laws \\(\\mathcal{L}\\) the morphism obeys, e.g. \\(\\mathrm{commutative}: f(x,y) = f(y,x)\\)",
                                        uri(CLASS), "the set-theoretic class of the morphism: \\(\\mathrm{class}(f) \\subseteq \\{\\mathrm{endo}, \\mathrm{iso}, \\mathrm{auto}, \\mathrm{mono}, \\mathrm{epi}, \\mathrm{section}, \\mathrm{retraction}\\}\\)",
                                        uri(DERIVATION), "the declared derivation of the morphism: \\(\\mathrm{lhs}\\) the derived instruction and \\(\\mathrm{rhs}\\) its primitive composition, e.g. \\(a / b \\mapsto a \\cdot b^{-1}\\)"),
                                "an inst as a categorical morphism incident to a source object (dom) and a target object (rng)"),
                        docWrap(OBJECT_TYPE = Type.Builder.build()
                                        .tid(CATEGORY_TID)
                                        .vid(OBJECT_TID)
                                        .isaPredicate(rec(
                                                uri(OBJ), TYPE_TYPE,
                                                uri(MORPHED_TO).maybe().asUri(), lst(T(MORPHISM_TID)),
                                                uri(MORPHED_FROM).maybe().asUri(), lst(T(MORPHISM_TID)),
                                                uri(LAW).maybe().asUri(), rec(URI_TYPE, THEORY_TYPE)))
                                        .constructor(arg -> {
                                            final Rec object = arg.isRec() && arg.asRec().has(uri(OBJ)) ? arg.asRec() : rec(uri(OBJ), arg);
                                            final Obj obj = object.at(OBJ);
                                            object.at(LAW, LawTable.typeLawsOf(obj.asType()), MUTABLE);
                                            object.at(MORPHED_TO, auto_(instLambda((ignore, i) -> {
                                                final Obj insts = Machine.read(f("/m/inst/+").dom(obj.vid()));//.rng(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())); // TODO: constrain to instset
                                                return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                            })).tryToInst(), MUTABLE);
                                            object.at(MORPHED_FROM, auto_(instLambda((o, i) -> {
                                                final Obj insts = Machine.read(f("/m/inst/+")/*.dom(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())*/.rng(obj.vid())); // TODO: constrain to instset
                                                return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                            })).tryToInst(), MUTABLE);
                                            return object.selfTID(OBJECT_TID);
                                        })
                                        .create(), Map.of(
                                        uri(MORPHED_TO), "the morphisms sourced from this object (out-edges): \\(\\mathrm{morphed\\_to}(A) = \\{f : \\mathrm{src}(f) = A\\}\\)",
                                        uri(MORPHED_FROM), "the morphisms targeted at this object (in-edges): \\(\\mathrm{morphed\\_from}(A) = \\{f : \\mathrm{trgt}(f) = A\\}\\)",
                                        uri(LAW), "the structural theories \\(\\mathcal{T}\\) the object models"),
                                "a type as a categorical object incident to targeting morphisms and sourcing morphisms"),
                        docWrap(THEORY_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(THEORY_TID)
                                //.isaPredicate(rec())
                                .create(), "the nominal super-type of the algebraic theories: a theory names its operations by role, e.g. \\(\\mathrm{ring} \\mapsto \\{\\mathrm{add}, \\mathrm{mul}, \\mathrm{zero}, \\mathrm{one}\\}\\)"),
                        docWrap(RING_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(RING_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(ADD), INST_TYPE,
                                        uri(MUL), INST_TYPE,
                                        uri(ZERO), ALL_TYPE,
                                        uri(ONE), ALL_TYPE))
                                .create(), "the theory of rings \\(\\langle R, +, \\cdot, 0, 1 \\rangle\\): \\(+\\) an abelian group, \\(\\cdot\\) a monoid, and \\(a \\cdot (b + c) = a \\cdot b + a \\cdot c\\)"),
                        docWrap(GROUP_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(GROUP_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OP), INST_TYPE,
                                        uri(ID), ALL_TYPE,
                                        uri(INV), INST_TYPE))
                                .create(), "the theory of groups \\(\\langle G, \\cdot, 1, {}^{-1} \\rangle\\): \\(g \\cdot g^{-1} = g^{-1} \\cdot g = 1\\)"),
                        docWrap(MONOID_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(MONOID_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OP), INST_TYPE,
                                        uri(ID), ALL_TYPE))
                                .create(), "the theory of monoids \\(\\langle M, \\cdot, 1 \\rangle\\): \\(m \\cdot 1 = 1 \\cdot m = m\\) and \\((m \\cdot n) \\cdot p = m \\cdot (n \\cdot p)\\)"),
                        docWrap(FIELD_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(FIELD_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(ADD), INST_TYPE,
                                        uri(MUL), INST_TYPE,
                                        uri(ZERO), ALL_TYPE,
                                        uri(ONE), ALL_TYPE,
                                        uri(INV), INST_TYPE))
                                .create(), "the theory of fields \\(\\langle F, +, \\cdot, 0, 1, {}^{-1} \\rangle\\): a commutative ring where every non-\\(0\\) element has a multiplicative inverse, \\(x \\cdot x^{-1} = 1\\)"),
                        docWrap(RIG_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(RIG_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(ADD), INST_TYPE,
                                        uri(MUL), INST_TYPE,
                                        uri(ZERO), ALL_TYPE,
                                        uri(ONE), ALL_TYPE))
                                .create(), "the theory of rigs (semirings) \\(\\langle S, +, \\cdot, 0, 1 \\rangle\\): an additive and a multiplicative monoid with \\(\\cdot\\) distributive over \\(+\\), and no additive inverses"),
                        docWrap(BOOLEAN_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(BOOLEAN_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OR), INST_TYPE,
                                        uri(AND), INST_TYPE,
                                        uri(NOT), INST_TYPE,
                                        uri(ZERO), ALL_TYPE,
                                        uri(ONE), ALL_TYPE))
                                .create(), "the theory of Boolean algebras \\(\\langle B, \\lor, \\land, \\lnot, 0, 1 \\rangle\\): a complemented distributive lattice, \\(b \\lor \\lnot b = 1\\) and \\(b \\land \\lnot b = 0\\)"),
                        docWrap(LATTICE_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(LATTICE_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(MEET), INST_TYPE,
                                        uri(JOIN), INST_TYPE,
                                        uri(BOTTOM), ALL_TYPE,
                                        uri(TOP), ALL_TYPE))
                                .create(), "the theory of lattices \\(\\langle L, \\sqcap, \\sqcup, \\bot, \\top \\rangle\\): every pair of elements has a meet and a join, bounded by \\(\\bot\\) and \\(\\top\\)"),
                        docWrap(SEMILATTICE_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(SEMILATTICE_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OP), INST_TYPE,
                                        uri(ID), ALL_TYPE))
                                .create(), "the theory of semilattices \\(\\langle S, \\sqcup, 0 \\rangle\\): an idempotent commutative monoid (a single meet or join side of a lattice)"),
                        docWrap(NEAR_RING_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(NEAR_RING_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(ADD), INST_TYPE,
                                        uri(MUL), INST_TYPE,
                                        uri(ZERO), ALL_TYPE))
                                .create(), "the theory of near-rings \\(\\langle N, +, \\cdot, 0 \\rangle\\): \\(+\\) a group, \\(\\cdot\\) a monoid, one-sided distributivity, no multiplicative \\(1\\) (reserved for barrier types)")),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(ALL).rng(MORPHISM_TID), lst(MORPHISM_TYPE), (lhs, inst) -> MORPHISM_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(OBJECT_TID), lst(OBJECT_TYPE), (lhs, inst) -> OBJECT_TYPE.constructor().apply(lhs))),
                uri(REWRITE), lst(
                        /* =====================================================================================
                         * (4) THE GRAPH/RELATION-DRIVEN FAMILY — next slice, deliberately NOT wired yet.
                         *
                         * Every rewrite below reads a single instruction's own declaration (its theory roles
                         * and process laws). The family after it reads an instruction's RELATION to the other
                         * types in the category — the metadata catInstSet already computes but the rewriter
                         * does not consume yet:
                         *
                         *   analysis.inverse   the declared opposing edge f^-1  (f . f^-1 = 1)
                         *   class              endo / iso / auto / mono / epi / section / retraction
                         *   position           duplicate / ambiguous / incomparable / coupling / isochain / retract
                         *   orbit              the reversible-core component of the endpoint
                         *   the cast subgraph  the as-edges, their refinements, and `implicit()`'s synthesized casts
                         *
                         * Candidate rules, each sound only under the named class/position guard:
                         *
                         *   inverse_pair_elision  x.as(B).as(A) |-> x        when as?B<=A . as?A<=B is `coupling`
                         *                                                    (a true two-sided inverse -- NOT when the
                         *                                                    round trip is lossy: `retract`/`isochain`)
                         *   cast_fusion           x.as(A).as(B) |-> x.as(B)  when B.refines(A) (the intermediate
                         *                                                    re-tag is the identity; `reaches`/`refines`
                         *                                                    already decide it)
                         *   endo_cast_drop        x.as(X::T) |-> x           when x is already X::T (an endo edge)
                         *   section_cancellation  r . s |-> e                when `clazz` says r is a `retraction`
                         *                                                    and s a `section` (a split mono/epi pair)
                         *   derived_cancellation  div(mult(a,b), b) |-> a    composed from `derivation` + `inverse`
                         *
                         * Two things gate this family, and they are why it is a separate slice:
                         *
                         *   1. it needs the CAST SUBGRAPH (`graph()`, `check()`, `implicit()`), not one inst. The
                         *      guard is a whole-graph query, so the rule is not a local list filter like the ones
                         *      below -- it has to be driven from `catInstSet.check()`/`Finding`s, not from
                         *      `code.insts()`.
                         *   2. the dual direction -- `derivation_expansion` (minus |-> plus . neg,
                         *      div |-> mult . inv) -- must NOT be registered beside `derivation_contraction`.
                         *      The two alternate forever, so no length-stable rewriter stage can settle on a
                         *      form (the shipped `fixpoint_rewriter` would never close its stable window), and
                         *      which form wins becomes nondeterministic.
                         *      Expansion is a LOWERING policy (for a target that lacks the primitive), so it
                         *      belongs behind an explicit lowering flag/plan, never in the default rewrite set.
                         * ===================================================================================== */

                        // (1) the unit law, for EVERY theory instance the operand's type declares: op(id) |-> e,
                        // plus a bare identity operation (code's id) which is the identity morphism.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("theory_unit_removal"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (0 == insts.size())
                                        return code;
                                    final Map<Type, Rec> laws = new HashMap<>();
                                    final List<Inst> kept = insts.stream()
                                            .filter(i -> !isUnit(i, laws) && !isIdentity(i, laws))
                                            .toList();
                                    return kept.size() == insts.size() ? code : code(kept);
                                }), "the unit law of every theory the operand's type models: an operation applied to its own identity drops out, \\(\\mathrm{op}(\\mathrm{id}) \\leadsto \\varepsilon\\), and a bare identity operation is the identity morphism. e.g. \\(x + 0 \\leadsto x\\), \\(x \\cdot 1 \\leadsto x\\), \\(x \\cdot \\langle\\rangle \\leadsto x\\), \\(x \\cdot \\mathrm{id} \\leadsto x\\), \\(x \\cdot . \\leadsto x\\)"),

                        // (2a) the involution law: an adjacent pair of the SAME self-inverse operation is the
                        // identity -- neg.neg, reverse.reverse, not.not, conj.conj, inv.inv.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("theory_involution"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapsePairs(insts,
                                            (a, b) -> same(a, b) && isInvolution(a, carrier));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the involution law \\(f \\cdot f \\leadsto \\varepsilon\\) (period two) for any operation whose declared process laws include \\(\\mathrm{involution}\\) -- \\(\\mathrm{neg} \\cdot \\mathrm{neg} \\leadsto \\varepsilon\\), \\(\\mathrm{reverse} \\cdot \\mathrm{reverse} \\leadsto \\varepsilon\\), \\(\\mathrm{not} \\cdot \\mathrm{not} \\leadsto \\varepsilon\\)"),

                        // (2b) the idempotent law: an adjacent pair of the SAME idempotent operation is that
                        // operation -- ucase.ucase, zero.zero, one.one, an idempotent join (bool or/and).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_idempotent"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapseDuplicates(insts,
                                            (a, b) -> same(a, b) && declares(a, carrier, Law.idempotent));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the idempotent law \\(f \\cdot f \\leadsto f\\) for any operation whose declared process laws include \\(\\mathrm{idempotent}\\) -- \\(\\mathrm{ucase} \\cdot \\mathrm{ucase} \\leadsto \\mathrm{ucase}\\), \\(\\mathrm{zero} \\cdot \\mathrm{zero} \\leadsto \\mathrm{zero}\\)"),

                        // (2c) the absorbing law: an absorbing operation swallows the chain it is applied to
                        // (f . g = f), so every operation before it is dead code.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_absorbing"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    final int seed = (!insts.isEmpty() && START_INST_TID.equals(insts.get(0).tid().basePath())) ? 1 : 0;
                                    final Type carrier = carrier(code);
                                    for (int i = seed; i < insts.size(); i++)
                                        if (declares(insts.get(i), carrier, Law.absorbing)) {
                                            if (i <= seed)
                                                return code;
                                            final List<Inst> out = new ArrayList<>(insts.subList(0, seed));
                                            out.addAll(insts.subList(i, insts.size()));
                                            return code(out);
                                        }
                                    return code;
                                }), "the absorbing law \\(f \\circ g = f\\): an operation whose declared process laws include \\(\\mathrm{absorbing}\\) is a constant of the chain, so everything before it is dead code -- \\(\\ldots \\cdot g \\cdot \\mathrm{zero} \\leadsto \\ldots \\cdot \\mathrm{zero}\\), \\(x + 3 \\cdot \\mathrm{zero} \\leadsto x \\cdot \\mathrm{zero}\\)"),

                        // (2d) the monoidic law on a fold: a fold of a fold is the fold (reducing one element is
                        // the identity of the reduction), so an adjacent identical reducer/join collapses.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_monoidic"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapseDuplicates(insts,
                                            (a, b) -> same(a, b) && isFold(a, carrier) && declares(a, carrier, Law.monoidic));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the monoidic law on a fold (\\(\\mathrm{reducer}\\)/\\(\\mathrm{join}\\) form): a fold of a fold is the fold, \\(f \\cdot f \\leadsto f\\) -- \\(\\mathrm{sum} \\cdot \\mathrm{sum} \\leadsto \\mathrm{sum}\\), \\(\\mathrm{merge} \\cdot \\mathrm{merge} \\leadsto \\mathrm{merge}\\)"),


                        // (2f) the poset law: compose adjacent comparisons of the same order -- the stricter
                        // bound wins, so the two filters are one filter.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_poset"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    boolean changed = false;
                                    final Type carrier = carrier(code);
                                    for (int i = 0; i < insts.size(); i++) {
                                        final Inst composed = (i + 1 < insts.size()) ? posetCompose(insts.get(i), insts.get(i + 1), carrier) : null;
                                        if (null != composed) {
                                            out.add(composed);
                                            changed = true;
                                            i++;
                                        } else
                                            out.add(insts.get(i));
                                    }
                                    return changed ? code(out) : code;
                                }), "the poset law \\(x > a \\wedge x > b \\iff x > \\max(a, b)\\): adjacent comparisons of the same ordered type compose to the stricter bound -- \\(\\mathrm{gt}(a) \\cdot \\mathrm{gt}(b) \\leadsto \\mathrm{gt}(\\max(a, b))\\), \\(\\mathrm{lt}(a) \\cdot \\mathrm{lt}(b) \\leadsto \\mathrm{lt}(\\min(a, b))\\)"),

                        // (3a) inverse cancellation: an adjacent declared inverse pair with equal operands
                        // cancels to the identity -- the group law g . g^-1 = 1, read off Entry.inverse.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("inverse_cancellation"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    boolean changed = false;
                                    final Type carrier = carrier(code);
                                    for (int i = 0; i < insts.size(); i++) {
                                        if (i + 1 < insts.size() && inversePair(insts.get(i), insts.get(i + 1), carrier)) {
                                            changed = true;
                                            i++;
                                        } else
                                            out.add(insts.get(i));
                                    }
                                    return changed ? code(out) : code;
                                }), "the inverse law \\(f \\cdot f^{-1} \\leadsto \\varepsilon\\) on the declared opposing edge: an adjacent inverse pair with equal operands cancels -- \\(x + 3 - 3 \\leadsto x\\), \\(x \\cdot 2 \\div 2 \\leadsto x\\)"),

                        // (5) the stream lift: `map` is transparent on the value it wraps, so map(f) |-> f.
                        // This stays WITH map (its shape {?} -> {?} also matches `is`, so a shape-only
                        // generalization would unwrap a predicate); one rule covers both map(f) |-> f and the
                        // nesting map(map(f)) |-> map(f) (the outer unwrap exposes the inner map, next pass).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("form_map_unwrap"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    boolean changed = false;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    final Type carrier = carrier(code);
                                    for (final Inst inst : insts) {
                                        final Obj wrapped = mapArgument(inst, carrier);
                                        if (null != wrapped) {
                                            out.add(wrapped.asInst());
                                            changed = true;
                                        } else
                                            out.add(inst);
                                    }
                                    return changed ? code(out) : code;
                                }), "the stream lift \\(\\mathrm{map}\\) is the identity on a scalar, so a mapping of an instruction unwraps: \\(\\mathrm{map}(f) \\leadsto f\\), which also collapses \\(\\mathrm{map}(\\mathrm{map}(f)) \\leadsto \\mathrm{map}(f)\\)"),

                        // (3b) derivation contraction: fold a derived instruction's primitive composition back
                        // to the derived operation -- plus . neg |-> minus, mult . inv |-> div.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("derivation_contraction"),
                                code -> {
                                    final List<Derivation> derivations = LawTable.derivations();
                                    if (derivations.isEmpty())
                                        return code;
                                    List<Inst> out = code.insts();
                                    for (final Derivation d : derivations)
                                        out = contract(out, d);
                                    return code(out);
                                }), "folds a derived instruction's primitive composition back to the derived operation, from the type's declared \\(\\mathrm{derivation}\\): \\(\\mathrm{plus} \\cdot \\mathrm{neg} \\leadsto \\mathrm{minus}\\), \\(\\mathrm{mult} \\cdot \\mathrm{inv} \\leadsto \\mathrm{div}\\)")
        ))));
        docWrap(this, "categorical realization of types and insts as objects and morphisms");
        super.setup();
    }


    /**
     * The carrier type of a code chain — the type of the seed (the {@code start(x)} wrapper's arg).
     * Argless ops ({@code neg()}) have no arg to derive it from, so the rewrite reads it off the seed.
     */
    private static Type carrier(final Code code) {
        final List<Inst> insts = code.insts();
        if (!insts.isEmpty() && START_INST_TID.equals(insts.get(0).tid().basePath()))
            return insts.get(0).arg(0).type();
        return T(ALL);
    }

    /**
     * The rewrite's address — {@code /m/math/cat/inst/rewrite/<name>}.
     */
    private static fURI rewriteTID(final String name) {
        return CAT_ISA_TID.extend(INST).extend(REWRITE).extend(name);
    }

    /**
     * Two chain elements are the same operation: same op address and same operands. This is what makes a
     * law-driven fold sound — {@code neg·neg} collapses, {@code neg·inv} does not.
     */
    private static boolean same(final Inst a, final Inst b) {
        return sameOp(a, b) && a.args().equals(b.args());
    }

    /**
     * Two chain elements are the same operation, whatever their operands — the guard for a law that
     * relates an operation to itself (poset comparison composition).
     */
    private static boolean sameOp(final Inst a, final Inst b) {
        return a.tid().basePath().equals(b.tid().basePath());
    }

    /**
     * Collapse adjacent pairs the predicate accepts to nothing — the equation {@code f·f ↦ ε} shared by the
     * involution, idempotent and monoidic-fold laws.
     */
    private static List<Inst> collapsePairs(final List<Inst> insts, final BiPredicate<Inst, Inst> match) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && match.test(insts.get(i), insts.get(i + 1)))
                i += 2; // f·f => ε — drop the pair
            else
                result.add(insts.get(i++));
        }
        return result;
    }

    /**
     * Collapse adjacent duplicate pairs to a single instance — the equation {@code f·f ↦ f} shared by the
     * idempotent law and by a monoidic fold (a reduction of one element is that element).
     */
    private static List<Inst> collapseDuplicates(final List<Inst> insts, final BiPredicate<Inst, Inst> match) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && match.test(insts.get(i), insts.get(i + 1))) {
                result.add(insts.get(i));
                i += 2; // f·f => f — keep one
            } else
                result.add(insts.get(i++));
        }
        return result;
    }

    /**
     * The involution test: the operation's own declared {@code involution} law, or the carrier's declared
     * additive-group inverse (the role path that keeps a type whose ops are not in the process-law table —
     * e.g. {@code cmplx}'s {@code neg} — collapsing as before).
     */
    private static boolean isInvolution(final Inst inst, final Type carrier) {
        if (declares(inst, carrier, Law.involution))
            return true;
        final Inst inv = TheoryHelper.invInst(carrier);
        return !inv.isNoObj() && inv.test(inst);
    }

    /**
     * The unit test: an operation applied to its own theory identity — {@code op(id)} — for every theory
     * instance the operand's type models. The theory roles are read from {@code object::T.law}, so this one
     * rule covers {@code x + 0}, {@code x · 1}, {@code x · <>} (uri), {@code x · .} (machine), {@code x · id}
     * (code) and every user-declared monoid/group/ring/… The law blocks are memoized per rewrite pass by the
     * caller's map, since a type's theories are shared by every instruction over it.
     */
    private static boolean isUnit(final Inst inst, final Map<Type, Rec> laws) {
        if (inst.args().isEmpty())
            return false;
        final List<Type> operands = List.of(inst.dom(), inst.arg(0).type());
        for (final Type operand : operands)
            for (final Map.Entry<Obj, Obj> instance : laws.computeIfAbsent(operand, TheoryHelper::instances).jvm().entrySet()) {
                if (!instance.getValue().isRec())
                    continue;
                final Rec block = instance.getValue().asRec();
                for (final String[] roles : TheoryHelper.UNIT_ROLES) {
                    final Inst unit = TheoryHelper.unit(block, roles[0], roles[1]);
                    if (!unit.isNoObj() && unit.test(inst))
                        return true;
                }
            }
        return false;
    }

    /**
     * The bare-identity test: an instruction with no operands that <em>is</em> a theory's identity operation
     * (the role holds an instruction, not a literal) — the code ring's {@code id}. Its occurrence in a chain
     * is the identity morphism, hence dead weight.
     */
    private static boolean isIdentity(final Inst inst, final Map<Type, Rec> laws) {
        if (!inst.args().isEmpty())
            return false;
        for (final Map.Entry<Obj, Obj> instance : laws.computeIfAbsent(T(CODE_TID), TheoryHelper::instances).jvm().entrySet()) {
            if (!instance.getValue().isRec())
                continue;
            final Rec block = instance.getValue().asRec();
            for (final String role : TheoryHelper.IDENTITY_ROLES) {
                final fURI identity = TheoryHelper.identityAddress(block, role);
                if (null != identity && inst.tid().basePath().equals(identity))
                    return true;
            }
        }
        return false;
    }

    /**
     * A fold: the declared n-tid coefficient shape gathers many to one ({@code reducer} or {@code join}) and
     * the operation is declared {@code monoidic} — the shape a repeated fold is idempotent on. The chain
     * instruction is retyped with its declared address so the shape is legible before resolution.
     */
    private static boolean isFold(final Inst inst, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        if (null == declared)
            return false;
        final Inst.Form form = Inst.Form.of(inst.tid(declared.tid()));
        return (Inst.Form.reducer == form || Inst.Form.join == form) && declares(declared, Law.monoidic);
    }

    /**
     * Whether an instruction declares a process law for the carrier's type. The chain instruction carries no
     * endpoints (the rewriter runs before resolution), so the declaration is resolved by op address,
     * disambiguated by the operand type — never guessed across types.
     */
    private static boolean declares(final Inst inst, final Type carrier, final Law law) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        return null != declared && declares(declared, law);
    }

    private static boolean declares(final LawTable.Declared declared, final Law law) {
        return declared.entry().laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(law.name()));
    }

    /**
     * Compose two adjacent comparisons of the same ordered type to the stricter single bound — the poset law
     * {@code x > a ∧ x > b ⇔ x > max(a, b)}. Null when the pair is not composable (not declared
     * {@code poset}, different ops, or non-literal operands), so the caller keeps the original pair.
     */
    private static Inst posetCompose(final Inst a, final Inst b, final Type carrier) {
        if (!sameOp(a, b) || !declares(a, carrier, Law.poset) || 1 != a.args().count() || 1 != b.args().count())
            return null;
        final String op = a.tid().basePath().name();
        final boolean upper = op.equals(GT_INST_TID.name()) || op.equals(GTE_INST_TID.name());
        final boolean lower = op.equals(LT_INST_TID.name()) || op.equals(LTE_INST_TID.name());
        if (!upper && !lower)
            return null;
        final Obj x = a.arg(0);
        final Obj y = b.arg(0);
        final Obj bound;
        if (x.isInt() && y.isInt())
            bound = jnt(upper ? Math.max(x.asInt().intValue(), y.asInt().intValue()) : Math.min(x.asInt().intValue(), y.asInt().intValue()));
        else if (x.isReal() && y.isReal())
            bound = real(upper ? Math.max(x.asReal().realValue(), y.asReal().realValue()) : Math.min(x.asReal().realValue(), y.asReal().realValue()));
        else if (x.isStr() && y.isStr()) {
            final int comparison = x.asStr().strValue().compareTo(y.asStr().strValue());
            bound = (upper ? 0 <= comparison : 0 >= comparison) ? x : y;
        } else
            return null;
        return a.args(lst(bound));
    }

    /**
     * Adjacent inverse pair with equal operands — the group law {@code f·f⁻¹ = 1}. The pairing is the edge's
     * declared {@code inverse} (either direction), so it holds for plus/minus, mult/div, and every declared
     * inverse pair, and only when the operands cancel exactly.
     */
    private static boolean inversePair(final Inst a, final Inst b, final Type carrier) {
        return isInverseOf(a, b, carrier) || isInverseOf(b, a, carrier);
    }

    /**
     * Whether {@code b} is the declared inverse of {@code a} with equal operands. Endpoints are unavailable
     * before resolution, so the operand type that selected the declaration is the type guard.
     */
    private static boolean isInverseOf(final Inst a, final Inst b, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(a.tid().basePath(), carrier);
        return null != declared && null != declared.entry().inverse()
                && b.tid().basePath().equals(declared.entry().inverse().basePath())
                && a.args().equals(b.args());
    }

    /**
     * The stream lift, {@code map} — kept keyed on the operation itself: its {@code {?} → {?}} coefficient
     * shape is shared by other instructions ({@code is} is also {@code {?} → {?}}), so a shape-only test
     * would unwrap a predicate. The chain instruction carries no endpoints, so the op <em>address</em> is
     * what identifies it.
     */
    private static boolean isMap(final Inst inst) {
        return inst.tid().basePath().equals(MAP_INST_TID);
    }

    /**
     * The instruction a {@code map} wraps, or null when it wraps no instruction. Before resolution the
     * wrapped operand arrives as a uri (the op symbol), so it is read back as an instruction.
     */
    private static Obj mapArgument(final Inst inst, final Type carrier) {
        if (1 != inst.args().count() || !isMap(inst))
            return null;
        final Obj arg = inst.arg(0);
        if (arg.isObjInst())
            return arg;
        if (!arg.isUri())
            return null;
        final fURI op = arg.uriValue().isAbsolute() ? arg.uriValue() : M_ISA_TID.extend(INST).extend(arg.uriValue().name());
        final Inst wrapped = instB(op, lst());
        // keep the chain's pre-resolution form (the op address), but only when the address is a real op
        if (!Machine.read(op).isNoObj())
            return wrapped;
        return null;
    }

    /**
     * Match a generic pattern against a source, binding the generic fURIs ({@code A}, {@code B}, …) as they are
     * met. A generic uri matches anything (binding it); an inst matches by tid and recurses into its args.
     */
    private static boolean matchGenerics(final Obj pattern, final Obj source, final Map<fURI, Obj> bindings) {
        if (pattern.isUri() && pattern.uriValue().isGeneric()) {
            final fURI key = pattern.uriValue();
            final Obj prior = bindings.get(key);
            if (null == prior) {
                bindings.put(key, source);
                return true;
            }
            return prior.equals(source);
        }
        if (pattern.isInst() && source.isInst()) {
            final Inst p = pattern.asInst();
            final Inst s = source.asInst();
            if (!s.tid().test(p.tid()))
                return false;
            if (p.args().count() != s.args().count())
                return false;
            for (int i = 0; i < p.args().count(); i++)
                if (!matchGenerics(p.arg(i), s.arg(i), bindings))
                    return false;
            return true;
        }
        if (pattern.isCode() && source.isCode()) {
            final List<Inst> ps = pattern.asCode().insts();
            final List<Inst> ss = source.asCode().insts();
            if (ps.size() != ss.size())
                return false;
            for (int i = 0; i < ps.size(); i++)
                if (!matchGenerics(ps.get(i), ss.get(i), bindings))
                    return false;
            return true;
        }
        // a structural pattern (inst/code) never matches a non-structural source — falling through to
        // source.test(pattern) here would let a scalar pass a nested-composition pattern (e.g. the `2`
        // of plus(2) "matching" the map(B).neg() arg of the minus derivation)
        if (pattern.isInst() || pattern.isCode() || source.isInst() || source.isCode())
            return false;
        return source.test(pattern);
    }

    /**
     * Contract the primitive composition of a derivation back to its derived instruction — {@code mult·inv ↦ div}.
     */
    private static List<Inst> contract(final List<Inst> insts, final Derivation d) {
        final List<Inst> out = new ArrayList<>();
        for (final Inst inst : insts) {
            final Map<fURI, Obj> bindings = new HashMap<>();
            final Inst rhs = d.rhs().insts().getLast();
            if (matchGenerics(rhs, inst, bindings)) {
                final Inst lhs = d.lhs().insts().getLast();
                out.add((Inst) Inst.Helper.substituteGenerics(lhs, bindings));
            } else {
                out.add(inst);
            }
        }
        return out;
    }

    /**
     * The law axis: the process laws a morphism obeys — a property of the operation itself, never the whole
     * algebra (those are the theory types on {@code object::T}).
     */
    // NOTES: the structural laws — semilattice, boolean, near_ring, field, rig, lattice, ring, group, … — are not
    // process laws: they name an algebraic *structure*, so they are theory types on object::T (monoid_theory::T,
    // group_theory::T, ring_theory::T, field_theory::T, rig_theory::T, boolean_theory::T, lattice_theory::T,
    // semilattice_theory::T, near_ring_theory::T). The former provenance axis (syntactic/declared/semantic) is dropped
    // too: mInstSetLawTable serves the declared process laws; the rest are derived (syntactic) or computed (semantic) when wired.
    public enum Law {
        /**
         * an annihilator: {@code a·x = a} for every {@code x} (absorbing element of the operation)
         */
        absorbing,
        /**
         * the reflexive-transitive iterate — {@code aⁿ} over the operation (a Kleene closure)
         */
        kleene,
        /**
         * pass-through-or-drop ({0,1}); the optional/zeroable lift of a value
         */
        partial,
        /**
         * commutes through the stream ring's parallel/serial structure — the coefficient floats off F\Fr
         */
        floatable,
        /**
         * cannot float past a barrier — a reduce anchors the coefficient in place
         */
        blocked,
        /**
         * a monoid/group acting on a carrier — {@code f(gh)x = f(g)f(h)x}
         */
        action,
        /**
         * an atomic/indecomposable element — no nontrivial factorization
         */
        prime,
        /**
         * distributes over {@code +} on the left — {@code a(b+c) = ab+ac}
         */
        left_distributive,
        /**
         * distributes over {@code +} on the right — {@code (a+b)c = ac+bc}
         */
        right_distributive,
        /**
         * a non-associative binary op with an identity element
         */
        magmadic,
        /**
         * an associative binary op with an identity element
         */
        monoidic,
        /**
         * {@code a·b = b·a}
         */
        commutative,
        /**
         * the carrier is partially ordered, and these insts are its comparisons
         */
        poset,
        /**
         * the identity element — {@code e·x = x·e = x}
         */
        unit,
        /**
         * self-inverse — {@code f(f(x)) = x} (period two)
         */
        involution,
        /**
         * {@code f(f(x)) = f(x)}
         */
        idempotent,
        /**
         * some power is zero — {@code fⁿ = 0}
         */
        nilpotent,
        /**
         * nonzero {@code a} with {@code a·b = 0} for some nonzero {@code b}
         */
        zero_divisor,
        /**
         * generated by one element — every value is a power of a single generator
         */
        cyclic;
    }

    /**
     * The position axis: the six relations an edge takes part in, derived from its endpoints' place in
     * the graph. Enum constants are lowercase so {@code name()} is the mtron label.
     */
    public enum Position {
        /**
         * another inst of the same family maps the same dom to the same rng — a redundant registration
         */
        duplicate,
        /**
         * contested by an incomparable sibling that overlaps — dispatch has no most-specific winner
         */
        ambiguous,
        /**
         * contested by an incomparable, disjoint sibling — dispatch stays total
         */
        incomparable,
        /**
         * an opposing edge exists — a candidate inverse pair
         */
        coupling,
        /**
         * endpoints linked by a chain of couplings — one orbit of the reversible core
         */
        isochain,
        /**
         * mutually reachable and round trip idempotent — a subobject retraction
         */
        retract;
    }

    /**
     * The class axis: the set-theoretic classification of a morphism — whether it is self-mapping (endo),
     * invertible (iso), or split (section/retraction, hence mono/epi). Enum constants are lowercase so
     * {@code name()} is the mtron label. Only the sound implications are computed (see {@link #clazz(Inst)});
     * a proper (non-split) mono/epi needs composition or a declared class table, which is not wired yet.
     */
    public enum Class {
        /**
         * self-mapping — {@code dom(f) = rng(f)}
         */
        endo,
        /**
         * an invertible endomorphism — {@code dom(f) = rng(f)} and {@code f} is iso
         */
        auto,
        /**
         * invertible — a self-inverse (involution); later, any two-sided inverse
         */
        iso,
        /**
         * left-cancellable — {@code f·g = f·h ⇒ g = h} (here: a split monomorphism)
         */
        mono,
        /**
         * right-cancellable — {@code g·f = h·f ⇒ g = h} (here: a split epimorphism)
         */
        epi,
        /**
         * split mono — {@code f} has a left inverse
         */
        section,
        /**
         * split epi — {@code f} has a right inverse
         */
        retraction;
    }

    /// //////////////////////////////////////////////////////////////
    // the graph — derived from every registered inst, memoized, invalidated on registration writes

    private static volatile List<Inst> CACHED_INSTS;
    private static final Map<String, Lst> POSITION_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> CONTESTED_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Obj> ORBIT_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> FAMILY_MEMO = new ConcurrentHashMap<>();

    /**
     * drop every derived map — call on any inst-registration write
     */
    public static void invalidate() {
        CACHED_INSTS = null;
        POSITION_MEMO.clear();
        CONTESTED_MEMO.clear();
        ORBIT_MEMO.clear();
        FAMILY_MEMO.clear();
    }

    /**
     * every registered instruction across every inst set
     */
    public static List<Inst> instructions() {
        final List<Inst> cached = CACHED_INSTS;
        if (null != cached)
            return cached;
        final List<Inst> all = new ArrayList<>();
        for (final Obj obj : Machine.current().memory().spaces().values().toList()) {
            final Space space = obj.as();
            if (!(space instanceof InstSet instSet))
                continue;
            // new-style instsets serve their registrations from the table
            all.addAll(instSet.insts());
            // old-style instsets (e.g. mInstSet) hold their insts in the jvm map under /inst —
            // read the map directly, since space.at() routes through the table-backed read
            final Obj live = instSet.jvm().get(uri(INST));
            if (null != live && live.isLst())
                live.lstValue().stream().filter(Obj::isObjInst).map(Obj::<Inst>as).forEach(all::add);
        }
        final List<Inst> distinct = all.stream().distinct().toList();
        CACHED_INSTS = distinct;
        return distinct;
    }

    /**
     * the directed type graph G: dom basePath -> rng basePath — degenerate endpoints are not vertices
     */
    public static Map<fURI, Set<fURI>> graph(final List<Inst> all) {
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : all) {
            if (degenerate(inst.dom()) || degenerate(inst.rng()))
                continue;
            final fURI dom = inst.tid().dom().basePath();
            final fURI rng = inst.tid().rng().basePath();
            graph.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
        }
        return graph;
    }

    /**
     * generics, the root, and noobj are not objects of the category — only named types are
     */
    private static boolean degenerate(final Type type) {
        return type.isGeneric() || type.isRootType();
    }

    /**
     * the reversible core G ∩ G⁻¹ — edges that exist in both directions
     */
    public static Map<fURI, Set<fURI>> reversible(final Map<fURI, Set<fURI>> graph) {
        final Map<fURI, Set<fURI>> reversible = new HashMap<>();
        for (final Map.Entry<fURI, Set<fURI>> edge : graph.entrySet())
            for (final fURI to : edge.getValue())
                if (graph.getOrDefault(to, Set.of()).contains(edge.getKey())) {
                    reversible.computeIfAbsent(edge.getKey(), k -> new HashSet<>()).add(to);
                    reversible.computeIfAbsent(to, k -> new HashSet<>()).add(edge.getKey());
                }
        return reversible;
    }

    public static boolean reaches(final Map<fURI, Set<fURI>> graph, final fURI a, final fURI b) {
        final Set<fURI> seen = new HashSet<>();
        final ArrayDeque<fURI> stack = new ArrayDeque<>();
        stack.push(a);
        while (!stack.isEmpty()) {
            final fURI node = stack.pop();
            if (node.equals(b))
                return true;
            if (!seen.add(node))
                continue;
            graph.getOrDefault(node, Set.of()).forEach(stack::push);
        }
        return false;
    }

    /**
     * a vertex's address in the graph: the type's name, or its tid when it has no name of its own
     */
    public static fURI object(final Type type) {
        return (null != type.vid() ? type.vid() : type.tid()).basePath();
    }

    private static boolean refines(final Type a, final Type b) {
        return a.testNominally(b) && a.c().within(b.c());
    }

    private static boolean incomparable(final Type a, final Type b) {
        if (a.isRootType() || a.isGeneric() || b.isRootType() || b.isGeneric())
            return false;
        return !refines(a, b) && !refines(b, a);
    }

    /// //////////////////////////////////////////////////////////////
    // the four computes

    /**
     * the position labels of the inst — where the edge sits among the others
     */
    public static Lst position(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = POSITION_MEMO.get(key);
        if (null != cached)
            return cached;
        final List<Inst> all = instructions();
        final Map<fURI, Set<fURI>> graph = graph(all);
        final fURI out = object(inst.dom());
        final fURI in = object(inst.rng());
        final fURI op = inst.tid().basePath();
        final EnumSet<Position> kinds = EnumSet.noneOf(Position.class);
        // a redundant mapping: another inst of the same family casts the same endpoints
        final long same = all.stream()
                .filter(i -> i.tid().basePath().equals(op) && object(i.dom()).equals(out) && object(i.rng()).equals(in))
                .count();
        if (same > 1)
            kinds.add(Position.duplicate);
        // contested endpoints: siblings sharing this edge's rng with an incomparable dom, or sharing its
        // dom with an incomparable rng — overlapping (same base, cross-over reachable) vs disjoint
        for (final Inst other : all) {
            if (!object(other.rng()).equals(in) || object(other.dom()).equals(out))
                continue;
            if (incomparable(inst.dom(), other.dom()))
                kinds.add(overlap(object(inst.dom()), object(other.dom()), graph) ? Position.ambiguous : Position.incomparable);
        }
        for (final Inst other : all) {
            if (!object(other.dom()).equals(out) || object(other.rng()).equals(in))
                continue;
            if (incomparable(inst.rng(), other.rng()))
                kinds.add(overlap(object(inst.rng()), object(other.rng()), graph) ? Position.ambiguous : Position.incomparable);
        }
        // the relation between the edge's own endpoints
        if (out.equals(in))
            kinds.add(Position.retract);
        else if (graph.getOrDefault(out, Set.of()).contains(in) && graph.getOrDefault(in, Set.of()).contains(out))
            kinds.add(Position.coupling);
        else {
            final Map<fURI, Set<fURI>> reversible = reversible(graph);
            if (reaches(reversible, out, in))
                kinds.add(Position.isochain);
            else if (reaches(graph, out, in) && reaches(graph, in, out))
                kinds.add(Position.retract);
        }
        final Lst result = kinds.stream().map(k -> uri(k.name())).collect(new CommonUtil.LstCollector());
        POSITION_MEMO.put(key, result);
        return result;
    }

    private static boolean overlap(final fURI mine, final fURI theirs, final Map<fURI, Set<fURI>> graph) {
        return mine.equals(theirs) && reaches(graph, mine, theirs);
    }

    /**
     * the witness edges behind the ambiguous/incomparable labels — the contesting siblings
     */
    public static Lst contested(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = CONTESTED_MEMO.get(key);
        if (null != cached)
            return cached;
        final List<Inst> all = instructions();
        final Map<fURI, Set<fURI>> graph = graph(all);
        final fURI out = object(inst.dom());
        final fURI in = object(inst.rng());
        final Set<fURI> witnesses = new LinkedHashSet<>();
        for (final Inst other : all) {
            if (!object(other.rng()).equals(in) || object(other.dom()).equals(out))
                continue;
            if (incomparable(inst.dom(), other.dom()))
                witnesses.add(other.tid());
        }
        for (final Inst other : all) {
            if (!object(other.dom()).equals(out) || object(other.rng()).equals(in))
                continue;
            if (incomparable(inst.rng(), other.rng()))
                witnesses.add(other.tid());
        }
        final Lst result = witnesses.stream().map(MUri::uri).collect(new CommonUtil.LstCollector());
        CONTESTED_MEMO.put(key, result);
        return result;
    }

    /**
     * the reversible-core component of the inst's dom — everything the type can invert to and back
     */
    public static Obj orbit(final Inst inst) {
        if (inst.isNoObj())
            return inst;
        final String key = inst.tid().toString();
        final Obj cached = ORBIT_MEMO.get(key);
        if (null != cached && !cached.isNoObj())
            return cached;
        final Map<fURI, Set<fURI>> reversible = reversible(graph(instructions()));
        final Set<fURI> seen = new LinkedHashSet<>();
        final ArrayDeque<fURI> stack = new ArrayDeque<>();
        stack.push(object(inst.dom()));
        while (!stack.isEmpty()) {
            final fURI node = stack.pop();
            if (!seen.add(node))
                continue;
            reversible.getOrDefault(node, Set.of()).forEach(stack::push);
        }
        final Obj result = objs(seen.stream().sorted().map(f -> rec(mutableMap(uri(OBJ), T(f)), OBJECT_TID, null)));
        ORBIT_MEMO.put(key, result);
        return result;
    }

    /**
     * the same-op sibling registrations — the inst's own family
     */
    public static Lst family(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = FAMILY_MEMO.get(key);
        if (null != cached)
            return cached;
        final Lst result = instructions().stream()
                .filter(i -> i.tid().basePath().equals(inst.tid().basePath()))
                .map(Obj::<Obj>as)
                .collect(new CommonUtil.LstCollector());
        FAMILY_MEMO.put(key, result);
        return result;
    }

    /**
     * The set-theoretic class of the inst — a lst of class-label uris, computed conservatively (sound: no
     * label is emitted unless it is mathematically forced). The invertible case is the declared involution
     * law ({@code f·f = 1} makes {@code f} its own inverse, hence iso; an iso is both a section and a
     * retraction, hence both mono and epi). A proper (non-split) mono/epi is not yet detected.
     */
    public static Lst clazz(final Inst inst) {
        if (inst.isNoObj())
            return lst();
        final boolean endo = object(inst.dom()).equals(object(inst.rng()));
        final LawTable.Entry entry = mInstSetLawTable.INSTANCE.lookup(inst.tid());
        final boolean iso = null != entry && entry.laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(Law.involution.name()));
        final List<Obj> labels = new ArrayList<>();
        if (endo)
            labels.add(uri(Class.endo.name()));
        if (iso) {
            labels.add(uri(Class.iso.name()));
            if (endo)
                labels.add(uri(Class.auto.name()));
            labels.add(uri(Class.section.name()));
            labels.add(uri(Class.retraction.name()));
            labels.add(uri(Class.mono.name()));
            labels.add(uri(Class.epi.name()));
        }
        return lst(labels);
    }

    /**
     * Materialize a category block's lazy {@code !compute_*} auto_calls: copy the block and resolve every
     * auto field to its computed value, leaving the declared fields untouched. The stored block stays a
     * declaration — this returns the fully-populated view {@code ?catq} serves.
     *
     * @param block the stored object/morphism block (declared fields plus lazy compute pointers)
     * @return a fully-populated copy
     */
    public static Rec materialize(final Rec block) {
        final Map<Obj, Obj> filled = new LinkedHashMap<>();
        block.jvm().forEach((key, value) -> filled.put(key, value.dereference()));
        return rec(filled, block.tid(), block.vid());
    }

    /// //////////////////////////////////////////////////////////////
    // the as-graph audit — the cast subgraph of the category

    /**
     * The cast family, minus the identity casts ({@code as?X<=X} are trivially true, not graph edges).
     */
    private static List<Inst> asInsts() {
        return Machine.read(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(inst -> !inst.tid().dom().equals(inst.tid().rng()))
                .sorted(Comparator.comparing(inst -> inst.tid().toString()))
                .toList();
    }

    /**
     * Audit the cast subgraph for the requested {@link Position kinds}: {@code duplicate} casts, {@code ambiguous}/{@code incomparable} contesting
     * doms, and {@code coupling}/{@code isochain}/{@code retract} reversibility — each at its tightest kind,
     * with a reason.
     *
     * @param kinds the position kinds to check (empty means all)
     * @return the findings found, each as a {@link Finding}
     */
    public static Set<Finding> check(final Position... kinds) {
        final Set<Finding> findings = new HashSet<>();
        if (!Machine.loaded())
            return findings;
        final Set<Position> requested = 0 == kinds.length
                ? EnumSet.allOf(Position.class)
                : EnumSet.copyOf(Arrays.asList(kinds));
        final List<Inst> asInsts = asInsts();
        if (asInsts.isEmpty())
            return findings;
        if (requested.contains(Position.duplicate) || requested.contains(Position.ambiguous) || requested.contains(Position.incomparable)) {
            // undirected cast graph (dom <-> rng), keyed by basePath — used to decide whether two same-base
            // sibling doms capture the same values (reachable via a cross-over path)
            final Map<fURI, Set<fURI>> graph = new HashMap<>();
            for (final Inst inst : asInsts) {
                final fURI dom = inst.tid().dom().basePath();
                final fURI rng = inst.tid().rng().basePath();
                graph.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
                graph.computeIfAbsent(rng, k -> new HashSet<>()).add(dom);
            }
            final Map<fURI, List<Inst>> byRng = new LinkedHashMap<>();
            for (final Inst inst : asInsts)
                byRng.computeIfAbsent(inst.tid().rng(), k -> new ArrayList<>()).add(inst);
            for (final List<Inst> group : byRng.values()) {
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        final Type domA = group.get(i).dom();
                        final Type domB = group.get(j).dom();
                        final Type rngType = group.get(i).rng();
                        if (group.get(i).tid().dom().equals(group.get(j).tid().dom())) {
                            if (requested.contains(Position.duplicate))
                                findings.add(new Finding(group, Position.duplicate, "duplicate as: dom " + group.get(i).dom() + " -> rng " + rngType.namedType() + " appears twice"));
                        } else if (incomparable(domA, domB)) {
                            final boolean sameBase = domA.tid().basePath().equals(domB.tid().basePath());
                            final boolean overlapping = sameBase && reaches(graph, group.get(i).tid().dom().basePath(), group.get(j).tid().dom().basePath());
                            final Position kind = overlapping ? Position.ambiguous : Position.incomparable;
                            if (requested.contains(kind))
                                findings.add(new Finding(group, kind,
                                        (overlapping ? "ambiguous" : "incomparable") + " as: dom " + group.get(i).dom() + " vs dom " + group.get(j).dom() + " for rng " + rngType.namedType()));
                        }
                    }
                }
            }
        }
        if (requested.contains(Position.coupling) || requested.contains(Position.isochain) || requested.contains(Position.retract))
            findings.addAll(retracts(asInsts, requested));
        return findings;
    }

    /**
     * the coupling/isochain/retract pair relations over the cast subgraph — one finding per type pair
     */
    private static Set<Finding> retracts(final List<Inst> asInsts, final Set<Position> requested) {
        final Set<Finding> retracts = new HashSet<>();
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : asInsts)
            graph.computeIfAbsent(inst.tid().dom().basePath(), k -> new HashSet<>()).add(inst.tid().rng().basePath());
        final Map<fURI, Set<fURI>> reversible = reversible(graph);
        final List<fURI> nodes = new ArrayList<>(graph.keySet());
        nodes.sort(Comparator.comparing(fURI::toString));
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final fURI a = nodes.get(i);
                final fURI b = nodes.get(j);
                final Position kind;
                if (graph.getOrDefault(a, Set.of()).contains(b) && graph.getOrDefault(b, Set.of()).contains(a))
                    kind = Position.coupling;
                else if (reaches(reversible, a, b))
                    kind = Position.isochain;
                else if (reaches(graph, a, b) && reaches(graph, b, a))
                    kind = Position.retract;
                else
                    continue;
                if (requested.contains(kind))
                    retracts.add(new Finding(List.of(), kind, typeName(a) + " ⇄ " + typeName(b)));
            }
        }
        return retracts;
    }

    private static String typeName(final fURI f) {
        final cInt c = f.c();
        return f.name() + (null == c || c.isOne() ? "" : "{" + c + "}");
    }

    /**
     * Manifest the implicit casts that arise from the subtype hierarchy of the types already present in the
     * explicit cast subgraph: a type {@code T} refining {@code A} ({@code T.test(A)}) needs no explicit
     * {@code as?A<=T} — the cast is just the removal of the constraint, realized by re-tagging to {@code A}'s
     * vid. Only direct refinements are emitted; generics, the root, and {@code noobj} are excluded.
     *
     * @return the manifested implicit {@code as} instructions (empty when nothing is registered)
     */
    public static List<Inst> implicit() {
        final List<Inst> implicit = new ArrayList<>();
        if (!Machine.loaded())
            return implicit;
        final List<Type> types = Machine.read(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .flatMap(inst -> List.of(inst.dom().vid(), inst.rng().vid()).stream())
                .map(fURI::basePath)
                .distinct()
                .sorted(Comparator.comparing(fURI::toString))
                .map(vid -> T(vid))
                .filter(t -> !t.isGeneric() && !t.isRootType() && !t.vid().basePath().equals(NOOBJ_TID))
                .toList();
        for (final Type src : types) {
            for (final Type dst : types) {
                if (src.vid().equals(dst.vid()))
                    continue;
                if (src.test(dst))
                    implicit.add(instC(AS_INST_TID.dom(src.vid()).rng(dst.vid()), lst(dst), (lhs, inst) -> lhs.vid(inst.arg(0).vid())));
            }
        }
        return implicit;
    }

    /**
     * Compose and store the object block for a type — the obj elevator plus the algebraic theories the type
     * models, each theory instance keyed by the user's name. Nests with docWrap:
     * {@code catWrap(docWrap(INT_TYPE, ...), mutableMap(uri("ring"), rec(...)))}.
     *
     * @param type     the type the block describes
     * @param theories the declared theory instances — {@code theory-name => theory-structure rec}
     * @return the type, unchanged
     */
    public static Type catWrap(final Type type, final Map<Obj, Obj> theories) {
        final Rec objectRec = rec(mutableMap(uri(OBJ), type));
        objectRec.jvm().put(uri(LAW), rec(theories));
        // Machine.write(type.vidOrTid().addQ(CATQ_PATTERN.toString()), objectRec.tid(catInstSet.OBJECT_TID));
        return type;
    }
}
