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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_CORE_THREAD_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

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
                            uri(OBJ).maybe().asUri(), T(ALL.maybeSome()),
                            uri(DOM).maybe(), STR_TYPE,
                            uri(RNG).maybe(), STR_TYPE,
                            uri(ARGS).maybe(), T(ALL), // fix: noobj=>noobj slipping trhough the cracks somewhere rec(URI_TYPE,STR_TYPE).maybe(),
                            uri(DESC), STR_TYPE,
                            uri(EXAMPLE).maybe(), LST_TYPE))
                    .constructor(arg0 -> new Docs(arg0.recValue(), DOCS_TID, null))
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
    public static final Type SUBQ_TYPE = Type.Builder.build().vid(SUBQ_TID).tid(Q_TID).constructor(QCollection::subq).create();
    public static final Type SUB_TYPE =
            docWrap(Type.Builder.build()
                            .vid(SUBSCRIPTION_TID)
                            .tid(REC_TID)
                            .isaPredicate(rec(
                                    TARGET, URI_TYPE,
                                    ON_RECV, T(ALL.dom(LST_TID))))
                            .create(), "a subscription specification", "", mutableMap(
                            uri(TARGET), "the address scope of the subscription",
                            uri(ON_RECV), "a callback when scope of subscription changes"),
                    "a subscription for subq qproc",
                    "abc?subq -> |(?[uri::T,#::T].print(_))  [-- [target,new_obj] to on_recv --]");

    private QCollection() {
        // do nothing 
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q constQ() {
        final Set<fURI> CONSTQ_FURIS = new HashSet<>();
        return Q.Helper.build(CONSTQ_TID, CONSTQ_PATTERN)
                .preRead(furi -> bool(CONSTQ_FURIS.contains(furi.noQ())))
                .preWrite((furi, obj) -> {
                    if (obj.isNoObj()) {
                        CONSTQ_FURIS.remove(furi.noQ());
                    } else {
                        CONSTQ_FURIS.add(furi.noQ());
                        return obj;
                    }
                    return noobj();
                }).qlessWrite((furi, obj) -> {
                    if (!furi.hasQ(CONSTQ) && CONSTQ_FURIS.contains(furi.noQ()))
                        return fail(MTronException.of("%s is a constant", furi.noQ()));
                    return noobj();
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q typeQ() {
        final memSpace TYPE_SPACE = memSpace.of(rec(uri(PATTERN), uri("#")), null);
        return Q.Helper.build(TYPEQ_TID, TYPEQ_PATTERN)
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
    protected final static Rec NO_DOCS = rec(mutableMap(uri(DESC), str("no documentation available")), DOCS_TID, null);

    protected final static class DocInstSet extends AbstractInstSet {
        protected final Map<fURI, Set<Rec>> INST_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
        protected final Map<fURI, Rec> REWRITE_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());


        public DocInstSet() {
            super(true);
        }

        @Override
        public Obj write(final fURI vid, final Obj obj) {
            //if (vid.hasRng()) {
            final Inst inst = obj.asRec().at(OBJ).as();
            if (inst.dom().isCode()) {
                REWRITE_TABLE.put(inst.tid(), obj.asRec());
            } else {
                Router.global().registerRedirect(f(vid.name()), vid);
                INST_TABLE.computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashSet<>()).add(obj.asRec());
            }
            return obj;
        }

        @Override
        public Obj read(final fURI pattern) {
            return objs(INST_TABLE.entrySet()
                    .stream()
                    .filter(kv -> kv.getKey().test(pattern.basePath().asNode()))
                    .flatMap(kv -> kv.getValue().stream())
                    .filter(i -> !pattern.hasDom() || i.asRec().at(OBJ).dom().tid().test(pattern.dom().big()))
                    .filter(i -> !pattern.hasRng() || i.asRec().at(OBJ).rng().tid().test(pattern.rng().big()))
                    .map(i -> pattern.isNode() ? i : rel(i.asRec().at(OBJ).tid().toUri(), i)))
                    .append(objs(TYPE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(REWRITE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(CONST_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))));
        }
    }

    public static Q docQ() {
        final memSpace OBJ_DOCS = memSpace.of(ALL, null);
        final InstSet INST_DOCS = new DocInstSet();
        return Q.Helper.build(DOCQ_TID, DOCQ_PATTERN)
                .obj(f(INST), INST_DOCS)
                .obj(f(OBJ), OBJ_DOCS)
                .preWrite((vid, obj) -> {
                    final Rec doc = obj.tid().equals(DOCS_TID) ? obj.asRec() : new Docs(obj.toCleanString());
                    if (vid.hasRng()) {
                        INST_DOCS.write(vid.removeQ(DOCQ), doc);
                    } else {
                        OBJ_DOCS.write(vid.removeQ(DOCQ), doc);
                    }
                    return doc;
                })
                .preRead((vid) -> {
                    final Obj doc = INST_DOCS.read(vid.removeQ(DOCQ));
                    return doc.isNoObj() ?
                            OBJ_DOCS.read(vid.removeQ(DOCQ)).orElse(NO_DOCS.plus(rec(uri(OBJ), Router.global().read(vid.removeQ(DOCQ))))) :
                            doc;
                })
                .create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q incrQ() {
        final AtomicLong counter = new AtomicLong(0);
        return Q.Helper.build(INCRQ_TID, INCRQ_PATTERN).
                preWrite((vid, obj) -> {
                    final fURI incrPattern = vid.extend(vid.qValue(INCRQ_PATTERN, fURI.class)).resolve();
                    final List<String> newPath = new ArrayList<>();
                    for (final String p : incrPattern.path()) {
                        if (fURI.isPattern(p))
                            newPath.add(counter.incrementAndGet() + "");
                        else
                            newPath.add(p);
                    }
                    return obj.vid(vid.removeQ(INCRQ_PATTERN).path(newPath));
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q subq() {
        final Lst subscriptions = lst();
        return Q.Helper.build(SUBQ_TID, SUBQ_PATTERN)
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
                        subscription = rec(mutableMap(uri(TARGET), uri(vid.basePath()), uri(ON_RECV), obj), SUBSCRIPTION_TID, null);
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
                                new CoreThread(mutableMap(
                                        uri(START), lst(List.of(vid.basePath().toUri(), obj)),
                                        uri(CODE), code(s.asRec().at(ON_RECV).asCall()).as()), MACH_CORE_THREAD_TID, null).run();
                            });
                    return noobj();
                }).create();
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void internalDocWrap(final Obj obj, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description, final String... examples) {
        if (obj.isNoObj())
            return;
        final fURI objID = obj.isInst() ? obj.tid() : obj.vid();
        if (null == objID) {
            obj.logger().warn("unable to generate docs for a vid-less obj: %s", obj);
            return;
        }
        final Docs doc = Docs.doc(obj, domDesc, rngDesc, argDescription, description, examples);
        final Space objSpace = Router.global().getSpace(objID);
        final Optional<Q> docq = objSpace.qs().jvm().stream().filter(q -> q.tid().basePath().equals(DOCQ_TID)).map(Obj::<Q>as).findAny();
        if (docq.isEmpty())
            objSpace.logger().warn("no doc query attachment mounted on %s for %s", objSpace, objID);
        else if (obj.isInst()) {
            docq.get().at(INST).<Space>as().write(objID, doc);
        } else {
            docq.get().at(OBJ).<Space>as().write(objID, doc);
        }
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

    public static class Docs extends MRec {

        private static final String NONE = "<none>";

        public Docs(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Docs(final Rec docRec) {
            this(docRec.jvm(), docRec.tid(), docRec.vid());
        }

        public Docs(final String description) {
            this(mutableMap(uri(DESC), str(description)), DOCS_TID, null);
        }

        public static Docs empty(final Obj obj) {
            return new Docs(mutableMap(uri(OBJ), obj), DOCS_TID, null);
        }

        public Poly<?, ?> args() {
            return this.at(ARGS).orElse(rec0());
        }

        public String description() {
            return this.at(Tokens.DESC).isNoObj() ? null : this.at(Tokens.DESC).strValue();
        }

        public List<String> examples() {
            return this.at(EXAMPLE).elements().map(Obj::strValue).toList();
        }

        public static Docs doc(final Rec docRec) {
            return new Docs(docRec.jvm(), docRec.tid(), docRec.vid());
        }

        public static Docs doc(final Obj inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description, final String... examples) {
            return new Docs(rec(
                    uri(OBJ), inst,
                    uri(DOM), null == domDesc ? noobj() : str(domDesc),
                    uri(RNG), null == rngDesc ? noobj() : str(rngDesc),
                    uri(ARGS), null == argDescription ? noobj() : rec(argDescription.entrySet().stream().map(kv -> rel(kv.getKey(), str(kv.getValue())))),
                    uri(DESC), str(description),
                    uri(EXAMPLE), lst(Arrays.stream(examples).map(MStr::str))).jvm(), DOCS_TID, null);
        }
    }
}
