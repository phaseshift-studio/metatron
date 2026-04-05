/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.Q.Q_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Inst.*;
import static studio.phaseshift.metatron.isa.m.type.Inst.DOM;
import static studio.phaseshift.metatron.isa.m.type.Inst.OBJ;
import static studio.phaseshift.metatron.isa.m.type.Inst.RNG;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_CORE_THREAD_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class QCollection {

    public static final fURI CONSTQ_PATTERN = f("constq");
    public static final fURI CONSTQ_TID = Q_TID.extend("constq");
    public static final Type CONSTQ_TYPE = Type.Builder.build().tid(Q_TID).vid(CONSTQ_TID).constructor(QCollection::constQ).create();
    //
    public static final fURI INCRQ_PATTERN = f("incrq");
    public static final fURI INCRQ_TID = Q_TID.extend("incrq");
    public static final Type INCRQ_TYPE = Type.Builder.build().tid(Q_TID).vid(INCRQ_TID).constructor(QCollection::incrQ).create();
    //
    public static final String DOCQ = "docq";
    public static final fURI DOCQ_PATTERN = f(DOCQ);
    public static final fURI DOCQ_TID = Q_TID.extend("docq");
    public static final Type DOCQ_TYPE = Type.Builder.build().tid(Q_TID).vid(DOCQ_TID).constructor(QCollection::docQ).create();
    public static final fURI DOCS_TID = DOCQ_TID.extend("docs");
    public static final Type DOCS_TYPE =
            Type.Builder.build()
                    .tid(REC_TID)
                    .vid(DOCS_TID)
                    .isaPredicate(rec(
                            uri(OBJ).maybe().asUri(), T(ALL),
                            uri(DOM).maybe(), STR_TYPE,
                            uri(RNG).maybe(), STR_TYPE,
                            uri(ARGS).maybe(), T(ALL), // fix: noobj=>noobj slipping trhough the cracks somewhere rec(URI_TYPE,STR_TYPE).maybe(),
                            uri(DESC), STR_TYPE,
                            uri(EXAMPLE).maybe(), LST_TYPE))
                    .constructor(arg0 -> new Doc(arg0.recValue(), DOCS_TID, null))
                    .inst(AS_INST_TID.dom(DOCS_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(lhs.toString()))
                    .create();

    //
    public static final fURI TYPEQ_PATTERN = f("T");
    public static final fURI TYPEQ_TID = Q_TID.extend("typeq");
    public static final Type TYPEQ_TYPE = Type.Builder.build().tid(Q_TID).vid(TYPEQ_TID).constructor(QCollection::typeQ).create();
    //
    public static final fURI SUBQ_PATTERN = f("subq");
    public static final fURI SUBQ_TID = Q_TID.extend("subq");
    public static final fURI SUBSCRIPTION_TID = SUBQ_TID.extend("sub");
    public static final Type SUBQ_TYPE = Type.Builder.build()
            .vid(SUBQ_TID)
            .tid(REC_TID)
            .constructor(
                    instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(SUBQ_TID),
                            lst(isa_(rec()).tryToInst()),
                            (lhs, inst) -> QCollection.subq())).create();

    public static final Type SUB_TYPE =
            Type.Builder.build()
                    .vid(SUBSCRIPTION_TID)
                    .tid(REC_TID)
                    .isaPredicate(rec(TARGET, URI_TYPE, ON_RECV, T(ALL)))
                    .create();

    private QCollection() {
        // do nothing 
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q constQ() {
        final Set<fURI> CONSTQ_FURIS = new HashSet<>();
        return studio.phaseshift.metatron.furi.Q.Helper.build(CONSTQ_TID, f(CONST))
                .preRead(furi -> bool(CONSTQ_FURIS.contains(furi.noQ())))
                .preWrite((furi, obj) -> {
                    if (obj.isNoObj()) CONSTQ_FURIS.remove(furi.noQ());
                    else CONSTQ_FURIS.add(furi.noQ());
                    return noobj();
                }).qlessWrite((furi, obj) -> {
                    if (!furi.hasQ(CONST) && CONSTQ_FURIS.contains(furi.noQ()))
                        return fail(MTronException.of("%s is a constant", furi.noQ()));
                    return noobj();
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q typeQ() {
        final memSpace TYPE_SPACE = memSpace.of(rec(uri(PATTERN), uri("#")), null);
        return studio.phaseshift.metatron.furi.Q.Helper.build(TYPEQ_TID, TYPEQ_PATTERN)
                .preWrite((vid, obj) -> {
                    TYPE_SPACE.write(vid.qLess(), obj);
                    return obj;
                })
                .preRead(vid -> {
                    final Obj type = TYPE_SPACE.read(vid.qLess());
                    if (type.isNoObj())
                        return T(ALL.maybeSome());
                    return type;
                })
                .qlessWrite((vid, obj) -> {
                    final Obj type = TYPE_SPACE.read(vid.qLess());
                    if (type.isNoObj())
                        return type;
                    if (!obj.test(type))
                        throw MTronException.of(TYPEQ_TID, "%s does not match %s", obj, type);
                    return noobj();
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static final Rec NO_DOCS = rec(uri(DESC), str("no documentation available"));

    public static Q docQ() {
        final memSpace DOC_SPACE = memSpace.of(rec(), null);
        return studio.phaseshift.metatron.furi.Q.Helper.build(DOCQ_TID, DOCQ_PATTERN)
                .obj(f(OBJ), DOC_SPACE)
                .preWrite((vid, obj) -> {
                    if (obj.tid().equals(DOCS_TID))
                        DOC_SPACE.write(vid.basePath(), obj);
                    return obj;
                })
                .preRead((vid) -> {
                    final Obj obj = DOC_SPACE.read(vid.basePath()).orElse(NO_DOCS);
                    return objs(obj.stream().filter(o -> o.tid().equals(DOCS_TID)));
                })
                .create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q incrQ() {
        final AtomicLong counter = new AtomicLong(0);
        return studio.phaseshift.metatron.furi.Q.Helper.build(INCRQ_TID, f(INCR)).
                preWrite((vid, obj) -> {
                    final fURI incrPattern = vid.extend(vid.qValue(INCR, fURI.class)).resolve();
                    final List<String> newPath = new ArrayList<>();
                    for (final String p : incrPattern.path()) {
                        if (fURI.isPattern(p))
                            newPath.add(counter.incrementAndGet() + "");
                        else
                            newPath.add(p);
                    }
                    return obj.vid(vid.removeQ(INCR).path(newPath));
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q subq() {
        final Lst subscriptions = lst();
        return studio.phaseshift.metatron.furi.Q.Helper.build(SUBQ_TID, SUBQ_PATTERN)
                .obj(f(OBJ), subscriptions)
                .preRead(vid -> {
                    subscriptions.logger().debug("reading: %s", vid.basePath());
                    return lst(subscriptions.elements().filter(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue())));
                })
                .preWrite((vid, obj) -> {
                    final Obj subscription;
                    if (obj.isNoObj()) {
                        subscription = noobj();
                        subscriptions.lstValue().removeIf(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue()));
                        subscription.logger().info("unsubscribing from %s", vid.basePath());
                    } else if (obj.tid().basePath().equals(SUBSCRIPTION_TID)) {
                        subscription = obj;
                        subscriptions.lstValue().add(subscription);
                        subscription.logger().info("subscribing to %s", vid.basePath());
                    } else {
                        subscription = rec(Map.of(uri(TARGET), uri(vid.basePath()), uri(ON_RECV), obj), SUBSCRIPTION_TID, null);
                        subscriptions.lstValue().add(subscription);
                        subscription.logger().info("subscribing to %s", vid.basePath());
                    }
                    //LOG.debug("current subscriptions: %s", subscriptions);
                    return subscription;
                })
                .qlessWrite((vid, obj) -> {
                    // subscriptions.logger().info("qless write to %s", vid.basePath());
                    subscriptions.elements().filter(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue()))
                            .forEach(s -> {
                                subscriptions.logger().debug("spawning core thread for subscription recv: %s", s);
                                new CoreThread(Map.of(
                                        uri(START), lst(List.of(vid.basePath().toUri(), obj)),
                                        uri(CODE), code(s.asRec().at(ON_RECV).as()).as()), MACH_CORE_THREAD_TID, null).run();
                            });
                    return noobj();
                }).create();
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void internalDocWrap(final Obj obj, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description, final String... examples) {
        final fURI objID = obj.isInst() ? obj.tid() : obj.vid();
        if(null == objID) {
            obj.logger().warn("unable to generate docs for a vid-less obj: %s", obj);
            return;
        }
        final Doc doc = Doc.doc(obj, domDesc, rngDesc, argDescription, description, examples);
        final Space objSpace = Router.global().getSpace(objID);
        final Optional<Q> docq = objSpace.qs().jvm().stream().filter(q -> q.tid().basePath().equals(DOCQ_TID)).map(Obj::<Q>as).findAny();
        if (docq.isEmpty())
            objSpace.logger().warn("no doc query attachment mounted on %s for %s", objSpace, objID);
        else
            docq.get().at(OBJ).<Space>as().write(objID, doc);

    }

    public static Inst docWrap(final Inst inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description, final String... examples) {
        internalDocWrap(inst, domDesc, rngDesc, argDescription, description, examples);
        return inst;
    }

    public static <OBJ extends Obj> OBJ docWrap(final OBJ obj, final String description, final String... examples) {
        internalDocWrap(obj, null, null, null, description, examples);
        return obj;
    }

    public static Type docWrap(final Type type, final String predicate, final String constructor, final Map<Obj, String> predicateDescription, final String description, final String... examples) {
        internalDocWrap(type, predicate, constructor, predicateDescription, description, examples);
        return type;
    }

    public static InstSet docWrap(final InstSet instSet, final String description, final String... examples) {
        internalDocWrap(instSet, null, null, null, description, examples);
        return instSet;
    }

    public static class Doc extends MRec {

        private static final String NONE = "<none>";

        public Doc(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Doc(final Rec docRec) {
            this(docRec.jvm(), docRec.tid(), docRec.vid());
        }

        public Doc(final String description) {
            this(Map.of(uri(DESC), str(description)), DOCS_TID, null);
        }

        public static Doc empty(final Obj obj) {
            return new Doc(Map.of(uri(OBJ), obj), DOCS_TID, null);
        }

        public Poly<?, ?> args() {
            return this.at(ARGS).orElse(rec0());
        }

        public String description() {
            return this.at(Tokens.DESC).isNoObj() ? null : this.at(Tokens.DESC).strValue();
        }

        public static Doc doc(final Obj inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description, final String... examples) {
            return new Doc(rec(
                    uri(OBJ), inst,
                    uri(DOM), null == domDesc ? noobj() : str(domDesc),
                    uri(RNG), null == rngDesc ? noobj() : str(rngDesc),
                    uri(ARGS), null == argDescription ? noobj() : rec(argDescription.entrySet().stream().map(kv -> rel(kv.getKey(), str(kv.getValue())))),
                    uri(DESC), str(description),
                    uri(EXAMPLE), lst(Arrays.stream(examples).map(MStr::str))).jvm(), DOCS_TID, null);
        }
    }
}
