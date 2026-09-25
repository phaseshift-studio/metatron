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
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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
    public static final fURI LAW_TID = CAT_ISA_TID.extend("law");


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

    /**
     * {@code law::T} — the process-law union: one label from the morphism's declared process laws
     */
    public static Type LAW_TYPE;

    /**
     * {@code object::T} — the vertex block: the algebraic theories the object models, keyed by the
     * user's name for each theory instance (an object can model several — even several of one theory)
     */
    public static Type OBJECT_TYPE;


    public record Finding(List<Inst> insts, Position kind, String reason) {
    }


    public void setup() {
        this.selfTID(CAT_ISA_TID);
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
                        docWrap(MORPHISM_TYPE = Type.Builder.build()
                                        .tid(CATEGORY_TID)
                                        .vid(MORPHISM_TID)
                                        .isaPredicate(rec(
                                                uri(OBJ), INST_TYPE,
                                                uri(FORM), union_(Arrays.stream(Inst.Form.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst(),
                                                uri(SRC).maybe(), T(OBJECT_TID),
                                                uri(TRGT).maybe(), T(OBJECT_TID),
                                                uri(ANALYSIS).maybe(), T(REC_TID),
                                                uri(LAW).maybe(), lst(LAW_TYPE.c(cInt.SOME()))))
                                        .constructor(arg -> {
                                            final Rec objInstRec = arg.isRec() && arg.asRec().has(OBJ) ? arg.asRec() : rec(uri(OBJ), arg);
                                            final Inst inst = objInstRec.at(OBJ).asInst();
                                            final CatLawTable.Entry entry = CatLawTable.lookup(inst.tid());
                                            final Map<Obj, Obj> block = new LinkedHashMap<>();
                                            block.put(uri(FORM), uri(Inst.Form.of(inst).name()));
                                            block.put(uri(SRC), instLambda(inst.tid(), ALL, o -> rec(mutableMap(uri(OBJ), inst.dom()), OBJECT_TID, null)).tryToInst());
                                            block.put(uri(TRGT), instLambda(inst.tid(), ALL, o -> rec(mutableMap(uri(OBJ), inst.rng()), OBJECT_TID, null)).tryToInst());
                                            block.put(uri(ANALYSIS), auto_(instLambda(inst.tid(), ALL, o -> {
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
                                            return rec(block);
                                        })
                                        .create(),
                                Map.of(uri(FORM), "the n-tid coefficient shape \\((c_{\\mathrm{dom}}, c_{\\mathrm{rng}})\\) in regex notation: \\(\\mathrm{mapper} = (1,1),\\; \\mathrm{filter} = (1, ?),\\; \\mathrm{reducer} = (^{\\ast}, 1),\\; \\mathrm{flatmapper} = (1, ^{+}),\\; \\ldots\\)",
                                        uri(SRC), "the source object the morphism leaves: \\(\\mathrm{src}(f) = \\mathrm{dom}(f)\\)",
                                        uri(TRGT), "the target object the morphism enters: \\(\\mathrm{trgt}(f) = \\mathrm{rng}(f)\\)",
                                        uri("analysis/position").maybe(), "the edge's place among its siblings: \\(\\mathrm{position}(f) \\subseteq \\{\\mathrm{duplicate}, \\mathrm{ambiguous}, \\mathrm{incomparable}, \\mathrm{coupling}, \\mathrm{isochain}, \\mathrm{retract}\\}\\)",
                                        uri("analysis/contested").maybe(), "the witness edges behind the ambiguous/incomparable labels: \\(\\mathrm{contested}(f) = \\{g : \\mathrm{trgt}(g) = \\mathrm{trgt}(f) \\wedge \\mathrm{src}(g) \\perp \\mathrm{src}(f)\\} \\cup \\{g : \\mathrm{src}(g) = \\mathrm{src}(f) \\wedge \\mathrm{trgt}(g) \\perp \\mathrm{trgt}(f)\\}\\)",
                                        uri("analysis/orbit").maybe(), "the reversible-core component of the morphism's dom: \\(\\mathrm{orbit}(f) = \\{X : \\mathrm{dom}(f) \\sim^{*} X\\}\\)",
                                        uri("analysis/family").maybe(), "the same-name siblings: \\(\\mathrm{family}(f) = \\{g : \\mathrm{op}(g) = \\mathrm{op}(f)\\}\\)",
                                        uri("analysis/inverse").maybe(), "the opposing edge \\(f^{-1}\\) with \\(f \\cdot f^{-1} = 1\\)",
                                        uri(LAW), "the declared process laws \\(\\mathcal{L}\\) the morphism obeys, e.g. \\(\\mathrm{commutative}: f(x,y) = f(y,x)\\)"),
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
                                            object.at(LAW, CoreMaker.typeLaws(obj.asType()), MUTABLE);
                                            object.at(MORPHED_TO, auto_(instLambda(obj.vid(), ALL, (ignore, i) -> {
                                                final Obj insts = Router.readFromSpace(f("/m/inst/+").dom(obj.vid()));//.rng(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())); // TODO: constrain to instset
                                                return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                            })).tryToInst(), MUTABLE);
                                            object.at(MORPHED_FROM, auto_(instLambda(obj.vid(), ALL, (o, i) -> {
                                                final Obj insts = Router.readFromSpace(f("/m/inst/+")/*.dom(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())*/.rng(obj.vid())); // TODO: constrain to instset
                                                return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                            })).tryToInst(), MUTABLE);
                                            return object;
                                        })
                                        .create(), Map.of(
                                        uri(MORPHED_TO), "the morphisms sourced from this object (out-edges): \\(\\mathrm{morphed\\_to}(A) = \\{f : \\mathrm{src}(f) = A\\}\\)",
                                        uri(MORPHED_FROM), "the morphisms targeted at this object (in-edges): \\(\\mathrm{morphed\\_from}(A) = \\{f : \\mathrm{trgt}(f) = A\\}\\)",
                                        uri(LAW), "the structural theories \\(\\mathcal{T}\\) the object models"),
                                "a type as a categorical object incident to targeting morphisms and sourcing morphisms"),
                        docWrap(THEORY_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(THEORY_TID)
                                .isaPredicate(rec())
                                .create(), "the nominal super-type of the algebraic theories: a theory names its operations by role, e.g. \\(\\mathrm{ring} \\mapsto \\{\\mathrm{add}, \\mathrm{mul}, \\mathrm{zero}, \\mathrm{one}\\}\\)"),
                        docWrap(RING_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(RING_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(ADD), INST_TYPE,
                                        uri(MUL), INST_TYPE,
                                        uri(ZERO), INST_TYPE,
                                        uri(ONE), INST_TYPE))
                                .create(), "the theory of rings \\(\\langle R, +, \\cdot, 0, 1 \\rangle\\): \\(+\\) an abelian group, \\(\\cdot\\) a monoid, and \\(a \\cdot (b + c) = a \\cdot b + a \\cdot c\\)"),
                        docWrap(GROUP_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(GROUP_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OP), INST_TYPE,
                                        uri(ID), INST_TYPE,
                                        uri(INV), INST_TYPE))
                                .create(), "the theory of groups \\(\\langle G, \\cdot, 1, {}^{-1} \\rangle\\): \\(g \\cdot g^{-1} = g^{-1} \\cdot g = 1\\)"),
                        docWrap(MONOID_THEORY_TYPE = Type.Builder.build()
                                .tid(THEORY_TID)
                                .vid(MONOID_THEORY_TID)
                                .isaPredicate(rec(
                                        uri(OP), INST_TYPE,
                                        uri(ID), INST_TYPE))
                                .create(), "the theory of monoids \\(\\langle M, \\cdot, 1 \\rangle\\): \\(m \\cdot 1 = 1 \\cdot m = m\\) and \\((m \\cdot n) \\cdot p = m \\cdot (n \\cdot p)\\)")),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(ALL).rng(MORPHISM_TID), lst(MORPHISM_TYPE), (lhs, inst) -> MORPHISM_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(OBJECT_TID), lst(OBJECT_TYPE), (lhs, inst) -> OBJECT_TYPE.constructor().apply(lhs))))));
        docWrap(this, "categorical realization of types and insts as objects and morphisms");
        super.setup();
    }


    private static class CoreMaker {

        public static Rec typeLaws(final Type type) {
            if (type.tid().basePath().equals(INT_TID)) {
                return rec(mutableMap(
                        uri("ring"), rec(mutableMap(
                                        uri(ADD), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(MUL), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(Tokens.ZERO), auto_from_(ZERO_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(Tokens.ONE), auto_from_(ONE_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                                RING_THEORY_TID, null),
                        uri("add_group"), rec(mutableMap(
                                        uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(ID), auto_from_(ZERO_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(INV), auto_from_(NEG_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                                GROUP_THEORY_TID, null),
                        uri("add_monoid"), rec(mutableMap(
                                        uri(OP), auto_from_(PLUS_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(ID), auto_from_(ZERO_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                                MONOID_THEORY_TID, null),
                        uri("mult_monoid"), rec(mutableMap(
                                        uri(OP), auto_from_(MULT_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst(),
                                        uri(ID), auto_from_(ONE_INST_TID.dom(INT_TID).rng(INT_TID)).tryToInst()),
                                MONOID_THEORY_TID, null)));
            }
            return rec0();
        }
    }

    /**
     * The law axis: the process laws a morphism obeys — a property of the operation itself, never the whole
     * algebra (those are the theory types on {@code object::T}).
     */
    // NOTES: the structural laws — semilattice, boolean, near_ring, ring, group — are not process laws:
    // they name an algebraic *structure*, so they are theory types on object::T (ring_theory::T, group_theory::T,
    // monoid_theory::T exist; semilattice_theory::T, boolean_theory::T, near_ring_theory::T are future). The former
    // provenance axis (syntactic/declared/semantic) is dropped too: CatLawTable serves the declared process laws;
    // the rest are derived (syntactic) or computed (semantic) when wired.
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
     * The authoring helper: a lst of law-label uris for a {@link catInstSet#catWrap} declaration —
     * {@code laws(Law.commutative, Law.right_distributive)}.
     */
    public static Lst laws(final Law... laws) {
        return lst(Arrays.stream(laws).map(l -> (Obj) uri(l.name())).toList());
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
        for (final Obj obj : Router.global().spaces().values().toList()) {
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
        return Router.readFromSpace(AS_INST_TID).stream()
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
        if (!Router.loaded())
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
        if (!Router.loaded())
            return implicit;
        final List<Type> types = Router.readFromSpace(AS_INST_TID).stream()
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
        // Router.writeToSpace(type.vidOrTid().addQ(CATQ_PATTERN.toString()), objectRec.tid(catInstSet.OBJECT_TID));
        return type;
    }
}
