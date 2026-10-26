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

import studio.phaseshift.metatron.algebra.rewrite.InstPattern;
import studio.phaseshift.metatron.algebra.rewrite.Plan;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/math/cat")
public class catInstSet extends AbstractInstSet {

    public static final fURI CAT_ISA_TID = MATH_ISA_TID.extend("cat");
    public static final fURI CAT_INST_TID = CAT_ISA_TID.extend("inst");
    public static final fURI CAT_C_INST_TID = CAT_INST_TID.extend("c");
    public static final fURI CAT_LOWER_INST_TID = CAT_INST_TID.extend("lower");
    public static final fURI CAT_BIND_INST_TID = CAT_INST_TID.extend("bind");
    public static final fURI CATEGORY_TID = CAT_ISA_TID.extend("category");
    public static final fURI MORPHISM_TID = CATEGORY_TID.extend("morphism");
    public static final fURI OBJECT_TID = CATEGORY_TID.extend("object");
    public static final fURI PATH_TID = CATEGORY_TID.extend("path");
    public static final fURI ABSTRACTION_TID = CATEGORY_TID.extend("abstraction");
    public static final fURI PLAN_TID = CATEGORY_TID.extend("plan");
    public static final fURI PLACE_TID = CATEGORY_TID.extend("place");
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
     * {@code object::T} — the vertex block: the algebraic theories the object models, keyed by the
     * user's name for each theory instance (an object can model several — even several of one theory)
     */
    public static Type OBJECT_TYPE;
    /**
     * {@code morphism::T} — the edge block: the morphism's own algebra
     */
    public static Type MORPHISM_TYPE;
    public static Type PATH_TYPE;
    /**
     * {@code abstraction::T} — a path's obj lifted pointwise along \(\alpha\): each chain instruction as its
     * {@code morphism::T}, so the laws, forms, theories, classes and analyses are read in-graph rather than
     * from a table outside it
     */
    public static Type ABSTRACTION_TYPE;
    /**
     * {@code plan::T} — the morphism of the rewrite category ({@code path → path}): a rewrite rule as data,
     * {@code [name, cut, route]} — the cut is its selector, the routes its distribution — carrying no lambda;
     * the one {@code lower(plan::T)} instruction applies it
     */
    public static Type PLAN_TYPE;

    /**
     * {@code place::T} — the location coordinate: the machine a chunk runs on, as a vertex beside {@code path::T}.
     * {@code obj} is the machine address ({@code //host[/path]}), {@code authority} the transports that reach it,
     * and the edge/status fields are derived from the roster and peer map.
     */
    public static Type PLACE_TYPE;

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
     * The law slot — the instruction the {@code ^} sugar appends ({@code c}): a predicate over an instruction that
     * passes it on when it obeys the slot's law and answers noobj when it does not (a filter). The law is the slot's
     * own name. {@code floatable} and {@code blocked} are read off the instruction's form, every other law off its
     * declaration ({@link InstPattern#matches}), so a slot fails closed on an instruction nothing declares — and
     * anything that is not an instruction obeys no law.
     */
    public static Inst instLawLambda = instLambda((lhs, inst) -> {
        final Law law = Law.valueOf(inst.tid().basePath().name());
        return lhs.isObjInst() && InstPattern.law(law).matches(lhs.asInst()) ? lhs : noobj();
    });

    public static Inst createInstLawLambda(final fURI id) {
        final Law law = Arrays.stream(Law.values())
                .filter(l -> l.name().equals(id.name()))
                .findFirst()
                .orElseThrow(() -> MTronException.of("%s is not a law", id.name()));
        return instLawLambda.tid(f(law.name()).q(id.qMap()).poly(id.poly()));
    }

    public void setup() {
        this.selfTID(CAT_ISA_TID);
        // self-register the per-instset law tables before any object/morphism resolves laws through the router
        mInstSetLawTable.INSTANCE.getClass();
        mathInstSetLawTable.INSTANCE.getClass();
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(PATTERN), uri(CAT_ISA_TID.extend(ALL)),
                uri(TYPE), catTypes.registered(this),
                uri(INST), lst(
                        instC(CAT_LOWER_INST_TID.dom(PATH_TID).rng(PATH_TID), lst(PLAN_TYPE), (lhs, inst) -> {
                            // lower(plan::T): path → path — read the plan rec, apply it to the path's code, re-lift,
                            // carrying the realized placement (where the emitter shipped the shard).
                            final Plan plan = Plan.of(inst.arg(0).asRec());
                            final Code code = lhs.asRec().at(OBJ).asCode();
                            return PATH_TYPE.constructor().apply(realized(PlanInterpreter.lower(plan, code)));
                        }),
                        instC(CAT_BIND_INST_TID.dom(PATH_TID).rng(PATH_TID), lst(PLAN_TYPE), (lhs, inst) -> {
                            // bind(plan::T): path → path — the cut-less operator: re-route located code. Symmetric
                            // with lower: the gating (a cut-less plan, an admissible target) lives in
                            // PlanInterpreter.bind and relocate; a declined bind is the identity, the path unchanged.
                            final Plan plan = Plan.of(inst.arg(0).asRec());
                            final Code code = lhs.asRec().at(OBJ).asCode();
                            return PATH_TYPE.constructor().apply(realized(PlanInterpreter.bind(plan, code)));
                        }),
                        instC(AS_INST_TID.dom(ALL).rng(MORPHISM_TID), lst(MORPHISM_TYPE), (lhs, _) -> MORPHISM_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(OBJECT_TID), lst(OBJECT_TYPE), (lhs, _) -> OBJECT_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(PATH_TID), lst(PATH_TYPE), (lhs, _) -> PATH_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(ABSTRACTION_TID), lst(ABSTRACTION_TYPE), (lhs, _) -> ABSTRACTION_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(PLACE_TID), lst(PLACE_TYPE), (lhs, _) -> PLACE_TYPE.constructor().apply(lhs))),
                uri(REWRITE), catRewrite.rules())
        ));
        docWrap(this, "categorical realization of types and insts as objects and morphisms");
        super.setup();
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

    /**
     * The realized placement of an emitted layout — the fan-out {@code select}'s routes
     * ({@code virtual::[code=>eval(<uri>)]}), lifted to places, keyed by the shard's AT span. {@code noobj} when the
     * emitter shipped nothing (the layout has no {@code select}).
     * <p>
     * MOVE LANDMINE: a {@code move} relocation emits a bare terminal {@code to(<target>)} — no {@code select} — so
     * this read-back would report no realized place for a moved program. When {@code move} ships, it needs its own
     * realized-placement read-back (recognize the terminal {@code to} route), or this must.
     */
    static Obj realizedWhere(final Code emitted) {
        final List<Obj> places = emitted.insts().stream()
                .filter(inst -> SELECT_INST_TID.equals(inst.tid().basePath()))
                .findFirst()
                .map(select -> select.arg(0).asLst().lstValue().stream()
                        .map(route -> route.asRec().at(uri(CODE)).asInst().arg(0).uriValue().host())
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(host -> Places.place(uri("//" + host)))
                        .filter(p -> !p.isNoObj())
                        .toList())
                .orElse(List.of());
        if (places.isEmpty())
            return noobj();
        return rec(rec(uri(SPAN), uri("at")), lst(places));
    }

    /**
     * The re-lift source: the emitted code, or a rec carrying the realized {@code where} when the emitter shipped.
     */
    static Obj realized(final Code emitted) {
        final Obj where = realizedWhere(emitted);
        return where.isNoObj() ? emitted : rec(mutableMap(uri(OBJ), emitted, uri(WHERE), where));
    }
}