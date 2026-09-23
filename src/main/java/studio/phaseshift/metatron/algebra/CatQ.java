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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * {@code ?catq} — the category query processor. Mirrors {@code ?docq}'s pattern: the q-proc carries a
 * memspace store, a preWrite stores the category block (an {@code object::T} or {@code morphism::T}), a
 * preRead returns it, and {@link #catWrap} composes the morphism block — declared values plus lazy compute
 * pointers — at registration time.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CatQ {

    public static final String CATQ = "catq";
    public static final fURI CATQ_PATTERN = f(CATQ);
    public static final fURI CATQ_TID = QPROC_TID.extend(CATQ_PATTERN);
    public static final Type CATQ_TYPE = Type.Builder.build()
            .tid(QPROC_TID)
            .vid(CATQ_TID)
            .constructor(CatQ::catQ)
            .create();

    private CatQ() {
    }

    public static QProc catQ() {
        final memSpace CATEGORY_SPACE = memSpace.of(ALL, null);
        return QProc.Helper.build(CATQ_TID, CATQ_PATTERN)
                .obj(f(OBJ), CATEGORY_SPACE)
                .preWrite((vid, obj) -> {
                    //Int.IntType.type();
                    return CATEGORY_SPACE.write(vid.qLessExceptDomRng(), obj);
                    
                    /*final fURI vidBig = vid.big();
                    if (!vidBig.equals(vid))
                        return Router.writeToSpace(vidBig, obj);
                    final Rec morph = obj.isRec() ? obj.asRec() : rec(mutableMap(uri(OBJ), obj));
                    if (vid.hasRng())
                        INST_MORPHS.write(vidBig.removeQ(MORPHQ), morph);
                    else
                        OBJ_MORPHS.write(vidBig.removeQ(MORPHQ), morph);*/
                    // the morph block declares on top of the inst registry — a write here never changes the graph
                    // return morph;
                })
                .preRead((vid) -> {
                    final fURI plain = vid.qLessExceptDomRng();
                    final Obj block = CATEGORY_SPACE.read(plain);
                    return block.isNoObj() ? block : Category.materialize(block.asRec());
                })
                .create();
    }

    /**
     * Resolve the compute inst's uri arg back to the registered inst — values only, never applied by the
     * resolver, so the tid survives the call untouched.
     */
    private static Inst targetInst(final Inst inst) {
        final fURI tid = inst.arg(0).uriValue();
        final Obj target = Router.readFromSpace(Router.loaded() ? tid.big() : tid);
        return target.isNoObj() ? null : target.asInst();
    }

    /**
     * Compose and store the morphism block for an inst — the declared entries are values, the computed axes
     * are lazy pointers ({@code !compute_*(*<tid>)} auto insts evaluated on touch). Nests with docWrap:
     * {@code catWrap(docWrap(instC(...), ..., Map.of(...)))}.
     *
     * @param inst  the instruction the block describes
     * @param morph the declared entries — law labels and relation targets (e.g. {@code [law=>[...], inverse=>minus?int<=int]})
     * @return the inst, unchanged
     */
    public static Inst catWrap(final Inst inst, final Map<Obj, Obj> morph) {
        final Map<Obj, Obj> block = new LinkedHashMap<>();
        block.put(uri(FORM), uri(Inst.Form.of(inst).name()));
        block.put(uri(POSITION), auto_(instC(Category.COMPUTE_POSITION_TID, lst(uri(inst.tid())), (lhs, i) -> {
            final Inst target = targetInst(i);
            return null == target ? noobj() : Category.position(target);
        })).tryToInst());
        block.put(uri(CONTESTED), auto_(instC(Category.COMPUTE_CONTESTED_TID, lst(uri(inst.tid())), (lhs, i) -> {
            final Inst target = targetInst(i);
            return null == target ? noobj() : Category.contested(target);
        })).tryToInst());
        block.put(uri(ORBIT), auto_(instC(Category.COMPUTE_ORBIT_TID, lst(uri(inst.tid())), (lhs, i) -> {
            final Inst target = targetInst(i);
            return null == target ? noobj() : Category.orbit(target);
        })).tryToInst());
        block.put(uri(FAMILY), auto_(instC(Category.COMPUTE_FAMILY_TID, lst(uri(inst.tid())), (lhs, i) -> {
            final Inst target = targetInst(i);
            return null == target ? noobj() : Category.family(target);
        })).tryToInst());
        block.putAll(morph);
        final Rec morphRec = rec(mutableMap(uri(OBJ), inst));
        morphRec.jvm().putAll(block);
        // store via the q-proc attached to the inst's own space (the internalDocWrap pattern)
        // final Space objSpace = Router.global().getSpaceFor(inst.tid());
        Router.writeToSpace(inst.tid().addQ(CATQ_PATTERN.toString()), morphRec.tid(Category.MORPHISM_TID));
       /* final Optional<QProc> morphq = objSpace.qs().jvm().stream()
                .filter(q -> q.tid().basePath().equals(MORPHQ_TID))
                .map(Obj::<QProc>as)
                .findAny();
        if (morphq.isEmpty()) {
            if (objSpace.hasVID())
                objSpace.logger().warn("no morph query attachment mounted on %s for %s", objSpace, inst.tid());
        } else {
            morphq.get().at(INST).<Space>as().write(inst.tid(), morphRec);
        }*/
        return inst;
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
        Router.writeToSpace(type.vidOrTid().addQ(CATQ_PATTERN.toString()), objectRec.tid(Category.OBJECT_TID));
        return type;
    }
}
