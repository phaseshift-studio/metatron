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

import studio.phaseshift.metatron.algebra.rewrite.Plan;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.mInstSet.rec;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PATH_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.carriers;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.contracts;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.maybeNotIdentity;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;


/**
 * The {@code /type} registrations of the category — the 17 type blocks (the category and its
 * object, morphism, path and abstraction; the law, class and theory labels; and the nine algebraic
 * theories) with their {@code docWrap} docs, assigned into the {@code catInstSet} type fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catTypes {

    /**
     * The registered type blocks — the {@code lst} the {@code catInstSet} setup puts at {@code /type}.
     *
     * @param self the registering instset (the {@code path}/{@code abstraction} constructors read its live rewrites)
     * @return the docwrapped type registrations
     */
    public static Lst registered(final InstSet self) {
        return lst(
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
                        .isaPredicate(union_(Arrays.stream(MorphClass.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst())
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
                                    // read directly: an initial instruction held in a rec is auto-resolved on an ordinary read
                                    final Inst inst = objInstRec.atDirect(OBJ).asInst();
                                    final LawTable.Entry entry = LawTable.lawsOf(inst.tid());
                                    // no table entry and no contract of its own: the form and class are read off the dom/rng
                                    // its registrations agree on, so start's {0} => A is initial, not the bare # => # mapper
                                    final List<Inst> shapes = null == entry && !inst.tid().hasDom() ? registrations(inst) : List.of(inst);
                                    final Map<Obj, Obj> block = new LinkedHashMap<>();
                                    block.put(uri(NAME), uri(inst.tid().name()));
                                    block.put(uri(FORM), uri(formOf(shapes, inst).name()));
                                    final Lst clazz = classOf(shapes);
                                    if (0 < clazz.count())
                                        block.put(uri(CLASS), clazz);
                                    block.put(uri(SRC), auto_(instLambda(o -> rec(mutableMap(uri(OBJ), inst.dom()), OBJECT_TID, null)).tryToInst()));
                                    block.put(uri(TRGT), auto_(instLambda(o -> rec(mutableMap(uri(OBJ), inst.rng()), OBJECT_TID, null)).tryToInst()));
                                    block.put(uri(ANALYSIS), auto_(instLambda(ALL, ALL, o -> {
                                        final Map<Obj, Obj> analysis = new LinkedHashMap<>();
                                        analysis.put(uri(FAMILY), instLambda(inst.tid(), ALL, o2 -> catGraph.family(inst)).tryToInst());
                                        analysis.put(uri(CONTESTED), instLambda(inst.tid(), ALL, o2 -> catGraph.contested(inst)).tryToInst());
                                        analysis.put(uri(ORBIT), instLambda(inst.tid(), ALL, o2 -> catGraph.orbit(inst)).tryToInst());
                                        analysis.put(uri(POSITION), instLambda(inst.tid(), ALL, o2 -> catGraph.position(inst)).tryToInst());
                                        if (null != entry && null != entry.inverse())
                                            analysis.put(uri(INVERSE), uri(entry.inverse()));
                                        // the edge the partials recombine through -- computed off the laws and the
                                        // derivation, never declared (LawTable.combiner); a reducer without one has none
                                        final fURI combiner = LawTable.combinerOf(inst);
                                        if (null != combiner)
                                            analysis.put(uri(COMBINER), uri(combiner));
                                        return rec(analysis);
                                    })).tryToInst());
                                    block.putAll(objInstRec.jvm());
                                    if (null != entry && !entry.laws().isEmpty())
                                        block.put(uri(LAW), entry.laws());
                                    if (null != entry && null != entry.derivation())
                                        block.put(uri(DERIVATION), rec(uri(LHS), entry.derivation().lhs(), uri(RHS), entry.derivation().rhs()));
                                    return rec(block).clone().selfTID(MORPHISM_TID.poly(inst.typeId()));
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
                                uri("analysis/combiner").maybe(), "the fold the partials of a reducer recombine through, computed from its laws and derivation: \\((a \\uplus b) \\cdot r = (a \\cdot r \\uplus b \\cdot r) \\cdot \\mathrm{combiner}(r)\\)",
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
                                    // a type with no address models no object: answer noobj (the caller's
                                    // orElse already means "no theories"), never raise — a fail here is
                                    // stacked into fail space AND, mid-rewrite, aborts the rewrite that asked
                                    if (!obj.isType() || null == obj.vid())
                                        return noobj();
                                    object.at(LAW, LawTable.typeLawsOf(obj.asType()), MUTABLE);
                                    object.at(MORPHED_TO, auto_(instLambda((ignore, i) -> {
                                        final Obj insts = Machine.read(f("/m/inst/+").dom(obj.vid()));//.rng(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())); // TODO: constrain to instset
                                        return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                    })).tryToInst(), MUTABLE);
                                    object.at(MORPHED_FROM, auto_(instLambda((o, i) -> {
                                        final Obj insts = Machine.read(f("/m/inst/+")/*.dom(i.arg(0).orElse(uri(ALL.maybeSome())).uriValue())*/.rng(obj.vid())); // TODO: constrain to instset
                                        return objs(insts.stream().map(Obj::asInst).filter(m -> !m.tid().dom().isGeneric() && !m.tid().rng().isGeneric()).map(m -> rec(mutableMap(uri(OBJ), m), MORPHISM_TID, null)));
                                    })).tryToInst(), MUTABLE);
                                    return object.selfTID(OBJECT_TID.poly(obj.typeId()));
                                })
                                .create(), Map.of(
                                uri(MORPHED_TO), "the morphisms sourced from this object (out-edges): \\(\\mathrm{morphed\\_to}(A) = \\{f : \\mathrm{src}(f) = A\\}\\)",
                                uri(MORPHED_FROM), "the morphisms targeted at this object (in-edges): \\(\\mathrm{morphed\\_from}(A) = \\{f : \\mathrm{trgt}(f) = A\\}\\)",
                                uri(LAW), "the structural theories \\(\\mathcal{T}\\) the object models"),
                        "a type as a categorical object incident to targeting morphisms and sourcing morphisms"),
                docWrap(PATH_TYPE = Type.Builder.build()
                                .tid(CATEGORY_TID)
                                .vid(PATH_TID)
                                .isaPredicate(rec(
                                        uri(OBJ), CODE_TYPE,
                                        uri(ORIGIN).maybe(), T(PLACE_TID),
                                        uri(WHERE).maybe(), REC_TYPE,
                                        uri(PEER).maybe(), lst(REC_TYPE),
                                        T(URI_TID).maybe(), T(PLAN_TID)))
                                .constructor(arg -> {
                                    final Rec path = arg.isRec() && arg.asRec().has(uri(OBJ)) ? arg.asRec() : rec(uri(OBJ), arg);
                                    final Code code = path.atDirect(OBJ);
                                    if (!code.isCode())
                                        return noobj();
                                    // the location coordinate: attach the machine's own place as origin when the path
                                    // has none — a program is placeable only where it answers (bind requires it)
                                    if (path.at(ORIGIN).isNoObj()) {
                                        final Obj origin = Places.self();
                                        if (!origin.isNoObj())
                                            path.at(ORIGIN, origin, MUTABLE);
                                    }
                                    // the plan-driven liveness reads the abstraction, not the code: lift ONCE, then
                                    // each rewrite's out-edge is materialized iff its selector matches the morphism
                                    // list's form/law/name fields — decided endogenously (the abstraction already
                                    // computed them), so it is type-checking-independent.
                                    final Obj abstraction = ABSTRACTION_TYPE.constructor().apply(code);
                                    final List<Obj> morphisms = abstraction.isNoObj() ? List.of() : abstraction.asRec().at(OBJ).asLst().elements().toList();
                                    final Obj reach = Places.reachable();   // the distribution candidates, read once per lift
                                    for (final Inst rewrite : self.rewrites()) {
                                        try {
                                            if (!maybeNotIdentity(morphisms, rewrite))
                                                continue; // a provably-identity edge is not materialized -- the vertex lists only its live out-edges
                                            final Plan plan = catRewrite.planOf(rewrite.tid().name());
                                            if (null != plan && plan.distributed() && (reach.isNoObj() || 1 > PlanInterpreter.hops(plan, code)))
                                                continue; // a distribution rule needs reach AND a hop budget: no peers or ?hops=0 → identity
                                            final Obj result = auto_(instLambda(lhs -> PATH_TYPE.constructor().apply(rewrite.apply(code)))).tryToInst();
                                            final Obj outEdge = null == plan ? rec(mutableMap(uri(NAME), uri(rewrite.tid().name()), uri(RESULT), result), PLAN_TID, null) : plan;
                                            path.at(uri(rewrite.tid().name()), outEdge, MUTABLE);
                                        } catch (final Exception e) {
                                            //do nothing
                                        }
                                    }
                                    return path.selfTID(PATH_TID);
                                })
                                .create(), Map.of(
                                uri(OBJ), "the code \\(p\\) itself — the vertex",
                                uri(ORIGIN).maybe(), "where the code starts — the machine's own place, attached on lift; absent means not placeable (bind requires it)",
                                uri(WHERE).maybe(), "the realized placement — span → place, written by the pass that applied a distribution rule",
                                uri(PEER).maybe(), "the peer recs the deployment used",
                                T(URI_TID).maybe(), "one out-edge per live rewrite \\(r\\), keyed by the rule's name and holding its \\(\\mathrm{plan}\\): \\(\\mathrm{lower}(\\mathrm{plan}_r)(p) = r(p)\\); a rule the abstraction proves the identity on \\(p\\) is no edge, so a normal form has none"),
                        "code as a categorical path incident to iso-semantic rewritten forms"),
                docWrap(ABSTRACTION_TYPE = Type.Builder.build()
                                .tid(CATEGORY_TID)
                                .vid(ABSTRACTION_TID)
                                .isaPredicate(rec(
                                        uri(OBJ), lst(T(MORPHISM_TID)),
                                        uri(WHERE).maybe(), lst(T(CATEGORY_TID))))
                                .constructor(arg -> {
                                    final boolean located = arg.isRec() && arg.asRec().has(uri(OBJ));
                                    final Obj source = located ? arg.asRec().at(OBJ) : arg;
                                    if (source.isLst())
                                        return arg.asRec().selfTID(ABSTRACTION_TID);    // already lifted
                                    if (!source.isCode())
                                        return noobj();
                                    final Code code = source.asCode();
                                    final List<Type> carriers = carriers(code);
                                    final List<Inst> contracts = contracts(code);
                                    final List<Obj> morphisms = new ArrayList<>();
                                    for (int i = 0; i < code.insts().size(); i++) {
                                        final Inst chain = code.insts().get(i);
                                        // the declared n-tid keys the chain instruction into the law table in-graph, read at
                                        // the carrier flowing into it
                                        final LawTable.Declared declared = LawTable.declared(chain.tid().basePath(), carriers.get(i));
                                        final Inst keyed = null == declared ? chain : chain.tid(declared.tid());
                                        Obj morphism;
                                        try {
                                            morphism = MORPHISM_TYPE.constructor().apply(keyed);
                                        } catch (final Exception e) {
                                            morphism = noobj();
                                        }
                                        // lossless α: store the ORIGINAL inst in obj, so γ (walk obj in order)
                                        // recovers the exact code — the form/law/class were computed from keyed
                                        if (morphism.isNoObj())
                                            morphisms.add(rec(mutableMap(uri(OBJ), chain), MORPHISM_TID, null));
                                        else {
                                            final Rec block = morphism.asRec();
                                            block.at(uri(OBJ), chain, MUTABLE);
                                            if (null == declared)
                                                attachShape(block, chain, contracts.get(i));
                                            morphisms.add(block);
                                        }
                                    }
                                    final Rec abstraction = rec(uri(OBJ), lst(morphisms));
                                    // the place-class word (§11.4): where the code can GO — its dialed peers — or the
                                    // realized placement when the pass has written one. Computed for any code, not just
                                    // a located path: the reach is the machine's, not the program's.
                                    final Obj word = located ? placeWord(arg.asRec()) : Places.reachable();
                                    if (!word.isNoObj())
                                        abstraction.at(uri(WHERE), word, MUTABLE);
                                    return abstraction.selfTID(ABSTRACTION_TID);
                                })
                                .create(), Map.of(
                                uri(OBJ), "the abstract word \\(\\alpha(f_1 \\cdot \\ldots \\cdot f_n) = \\alpha(f_1) \\cdot \\ldots \\cdot \\alpha(f_n)\\): each chain instruction as its \\(\\mathrm{morphism}\\) carrying law, form, class and analysis, with the ORIGINAL inst in \\(\\mathrm{obj}\\) so \\(\\gamma\\) (walk \\(\\mathrm{obj}\\) in order) recovers the exact code",
                                uri(WHERE).maybe(), "the α place-class word: the located path's places as a list (the α image of \\(\\mathrm{path}.where\\) / \\(\\mathrm{path}.origin\\)) — the surface a placement guard reads"),
                        "code lifted pointwise along \\(\\alpha\\) into the classification alphabet — the pure search surface a plan's selector reads"),
                docWrap(PLAN_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(PLAN_TID)
                                .isaPredicate(rec(
                                        uri(NAME), URI_TYPE,
                                        uri(RESULT).maybe().asUri(), ALL_TYPE, // for lambda based rewrites
                                        uri(CUT).maybe().asUri(), rec(
                                                uri(FORM).maybe().asUri(), URI_TYPE,
                                                uri(LAW).maybe().asUri(), lst(LAW_TYPE),
                                                uri(ROLE).maybe().asUri(), URI_TYPE),
                                        uri(WHERE).maybe(), lst(rec(
                                                uri(SHAPE), URI_TYPE,
                                                uri(TARGET).maybe(), ALL_TYPE,
                                                uri(INST).maybe(), lst(URI_TYPE),
                                                uri(SPAN), lst(rec(
                                                        uri(SPAN), URI_TYPE,
                                                        uri(FLAGS).maybe(), lst(URI_TYPE))))).maybe()))
                                .constructor(arg -> {
                                    final Rec plan = arg.isRec() && arg.asRec().has(uri(NAME)) ? arg.asRec() : rec(uri(NAME), arg);
                                    return plan.selfTID(PLAN_TID);
                                })
                                .create(), Map.of(
                                uri(NAME), "the plan's name — the rewrite rule it embodies",
                                uri(CUT).maybe(), "the cut selector: the morphism classes (form + laws + theory) the plan anchors on",
                                uri(WHERE).maybe(), "the distribution: target + span regions the plan ships, and the instructions its target must serve (inst, as a client's inst names them)",
                                uri(RESULT).maybe(), "only for a rewrite with no declarative plan: the lambda that applies the rewrite to yield the rewritten path::T"),
                        "a rewrite rule as a first-class plan — the morphism of the rewrite category (path → path)"),
                docWrap(PLACE_TYPE = Type.Builder.build()
                                .tid(CATEGORY_TID)
                                .vid(PLACE_TID)
                                .isaPredicate(rec(
                                        uri(OBJ), URI_TYPE,
                                        uri(AUTHORITY), lst(URI_TYPE),
                                        uri(INSTSET).maybe(), lst(URI_TYPE),
                                        uri(SOURCE).maybe(), URI_TYPE,
                                        uri(LINKED_TO).maybe().asUri(), lst(REC_TYPE),
                                        uri(LINKED_FROM).maybe().asUri(), lst(REC_TYPE),
                                        uri(STATUS).maybe().asUri(), URI_TYPE))
                                .constructor(Places::place)
                                .create(), mutableMap(
                                uri(OBJ), "the machine address \\(//host[/path]\\) — who evaluates: \\(host()\\) is the roster key, \\(path()\\) the frame vid (empty = the root machine)",
                                uri(AUTHORITY), "the transports that reach the machine \\(scheme://host:port\\); two aliases of one machine are values, never two places",
                                uri(INSTSET).maybe(), "ISAs resolvable here (the vocabulary coordinate)",
                                uri(SOURCE).maybe(), "who declared it (mdns, boot profile, a dial)",
                                uri(LINKED_TO).maybe(), "outbound peers whose service is this place",
                                uri(LINKED_FROM).maybe(), "inbound peers from this place",
                                uri(STATUS).maybe(), "up / down / known, derived over the dials"),
                        "a machine as the location coordinate — where code runs, the vertex beside path::T"),
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
                        .create(), "the theory of near-rings \\(\\langle N, +, \\cdot, 0 \\rangle\\): \\(+\\) a group, \\(\\cdot\\) a monoid, one-sided distributivity, no multiplicative \\(1\\) (reserved for barrier types)")
        );
    }

    /**
     * The op's registered contracts — the dom/rng a bare instruction gleans its shape from — or the instruction itself
     * when nothing is registered under its op.
     */
    private static List<Inst> registrations(final Inst inst) {
        // read from the Java list, not catGraph.family's lst: a poly holding an initial contract ({0} => A) evaluates it
        final List<Inst> registered = catGraph.instructions().stream()
                .filter(registration -> registration.tid().basePath().equals(inst.tid().basePath())).toList();
        return registered.isEmpty() ? List.of(inst) : registered;
    }

    /**
     * Attach the form and class of the contract the resolver picked for an op the law table does not declare: the
     * seed's {@code {0} => A} reads initial and is no endomorphism. The contract is read here and never stored, since a
     * rec or lst holding an initial instruction evaluates it.
     */
    private static void attachShape(final Rec morphism, final Inst chain, final Inst contract) {
        if (null == contract || !contract.tid().hasDom() || !contract.tid().basePath().equals(chain.tid().basePath()))
            return;
        morphism.at(uri(FORM), uri(Inst.Form.of(contract).name()), MUTABLE);
        final Lst clazz = catGraph.clazz(contract);
        morphism.at(uri(CLASS), 0 < clazz.count() ? clazz : noobj(), MUTABLE);
    }

    /**
     * The form the shapes agree on, else the instruction's own: one registration ({@code start}) or overloads of one
     * coefficient shape decide it, and overloads that disagree decide nothing.
     */
    private static Inst.Form formOf(final List<Inst> shapes, final Inst inst) {
        final Set<Inst.Form> forms = new HashSet<>();
        shapes.forEach(shape -> forms.add(Inst.Form.of(shape)));
        return 1 == forms.size() ? forms.iterator().next() : Inst.Form.of(inst);
    }

    /**
     * The class labels every shape carries — sound, since a label is only forced when every contract the op might
     * run under forces it.
     */
    private static Lst classOf(final List<Inst> shapes) {
        if (1 == shapes.size())
            return catGraph.clazz(shapes.getFirst());
        final List<Obj> common = new ArrayList<>(catGraph.clazz(shapes.getFirst()).lstValue());
        shapes.stream().skip(1).forEach(shape -> common.retainAll(catGraph.clazz(shape).lstValue()));
        return lst(common);
    }

    /**
     * The place-class word (§11.4): a located path's realized placement (span → place) abstracted into a list of
     * places, or its origin when nothing has been realized — the α word of locations, the same lift as the
     * instruction word. {@code noobj} for an unlocated path.
     */
    private static Obj placeWord(final Rec path) {
        final Obj where = path.at(WHERE);
        if (where.isRec() && !where.asRec().jvm().isEmpty())
            return lst(where.asRec().values().toList());
        // the place-class word is where the code can GO — its dialed peers — not where it starts (the origin)
        return Places.reachable();
    }
}
