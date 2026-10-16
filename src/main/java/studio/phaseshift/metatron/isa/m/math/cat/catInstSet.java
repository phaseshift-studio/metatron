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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
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
    public static final fURI CATEGORY_TID = CAT_ISA_TID.extend("category");
    public static final fURI MORPHISM_TID = CATEGORY_TID.extend("morphism");
    public static final fURI OBJECT_TID = CATEGORY_TID.extend("object");
    public static final fURI PATH_TID = CATEGORY_TID.extend("path");
    public static final fURI ABSTRACTION_TID = CATEGORY_TID.extend("abstraction");
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

    public static Inst instLawLambda = instLambda((lhs, inst) -> {
        if (inst.tid().basePath().name().equals(Law.floatable)) {

        }
        return noobj();
    });

    public static Inst createInstLawLambda(final fURI id) {
        return instLawLambda.tid(f(Law.valueOf(id.name()).name()).q(id.qMap()).poly(id.poly()));
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
                        instC(AS_INST_TID.dom(ALL).rng(MORPHISM_TID), lst(MORPHISM_TYPE), (lhs, inst) -> MORPHISM_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(OBJECT_TID), lst(OBJECT_TYPE), (lhs, inst) -> OBJECT_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(PATH_TID), lst(PATH_TYPE), (lhs, inst) -> PATH_TYPE.constructor().apply(lhs)),
                        instC(AS_INST_TID.dom(ALL).rng(ABSTRACTION_TID), lst(ABSTRACTION_TYPE), (lhs, inst) -> ABSTRACTION_TYPE.constructor().apply(lhs)),
                        instC(CAT_C_INST_TID.dom(ALL.maybe()).rng(CODE_TID.maybe()), lst(URI_TYPE), (lhs, inst) -> {
                            final List<Inst> newCode = lhs.isNoObj() ?
                                    new ArrayList<>() :
                                    new ArrayList<>(lhs.isCode() ?
                                            lhs.asCode().insts() :
                                            lhs.isInst() ?
                                                    lhs.tid().basePath().equals(BLOCK_INST_TID) ?
                                                            lhs.asInst().arg(0).asCall().insts() :
                                                            (lhs.tid().basePath().equals(CAT_ISA_TID.extend("inst").extend("c")) ?
                                                                    List.of(lhs.apply().asInst().c(lhs.c())) :
                                                                    List.of(lhs.asInst())) :
                                                    List.of(start_(lhs).tryToInst().asInst()));
                            newCode.add(createInstLawLambda(inst.arg(0).uriValue()).c(inst.arg(0).c()));
                            return block_(code(newCode)).tryToInst();
                        })),
                uri(REWRITE), catRewrite.rules())
                ));
        docWrap(this, "categorical realization of types and insts as objects and morphisms");
        super.setup();
    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>(List.of(
                //   Sugar.prefix("=?=", List.of(WHERE_INST_TID), 1),
                Sugar.prefix("^", List.of(CAT_C_INST_TID), 1)));
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