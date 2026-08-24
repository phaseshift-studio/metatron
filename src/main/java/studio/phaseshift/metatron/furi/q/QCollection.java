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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Type.LOG;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class QCollection {
    public static final fURI REFQ_PATTERN = f("refq");
    public static final fURI REFQ_TID = QPROC_TID.extend(REFQ_PATTERN);
    public static final Type REFQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(REFQ_TID).constructor(QCollection::refQ).create();
    //
    public static final fURI LINEQ_PATTERN = f("lineq");
    public static final fURI LINEQ_TID = QPROC_TID.extend(LINEQ_PATTERN);
    public static final Type LINEQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(LINEQ_TID).constructor(QCollection::lineQ).create();
    //
    public static final fURI MIMEQ_PATTERN = f("mimeq");
    public static final fURI MIMEQ_TID = QPROC_TID.extend(MIMEQ_PATTERN);
    public static final Type MIMEQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(MIMEQ_TID).constructor(QCollection::mimeQ).create();
    //
    public static final fURI MINTQ_PATTERN = f("mintq");
    public static final fURI MINTQ_TID = QPROC_TID.extend(MINTQ_PATTERN);
    public static final Type MINTQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(MINTQ_TID).constructor(QCollection::mintQ).create();
    //
    public static final fURI CONSTQ_PATTERN = f("constq");
    public static final fURI CONSTQ_TID = QPROC_TID.extend(CONSTQ_PATTERN);
    public static final Type CONSTQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(CONSTQ_TID).constructor(QCollection::constQ).create();
    //
    public static final fURI SHORTQ_PATTERN = f("shortq");
    public static final fURI SHORTQ_TID = QPROC_TID.extend(SHORTQ_PATTERN);
    public static final Type SHORTQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(SHORTQ_TID).constructor(QCollection::shortQ).create();
    //
    public static final fURI SAFEQ_PATTERN = f("safeq");
    public static final fURI SAFEQ_TID = QPROC_TID.extend(SAFEQ_PATTERN);
    public static final Type SAFEQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(SAFEQ_TID).constructor(QCollection::safeQ).create();
    //
    public static final String INCRQ = "incrq";
    public static final fURI INCRQ_PATTERN = f("incrq");
    public static final fURI INCRQ_TID = QPROC_TID.extend(INCRQ_PATTERN);
    public static final Type INCRQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(INCRQ_TID).constructor(QCollection::incrQ).create();
    //
    public static final fURI EMBEDQ_PATTERN = f("embedq");
    public static final fURI EMBEDQ_TID = QPROC_TID.extend(EMBEDQ_PATTERN);
    public static final Type EMBEDQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(EMBEDQ_TID).constructor(QCollection::embedQ).create();
    //
    public static final String DOCQ = "docq";
    public static final fURI DOCQ_PATTERN = f(DOCQ);
    public static final fURI DOCQ_TID = QPROC_TID.extend(DOCQ_PATTERN);
    // dual-mode interface doc key: a docs::T carrying 'build' (how to implement the interface)
    // alongside 'desc' (how to use it) is an interface doc.  docQ().preRead branches on the
    // implementation status of the docq'd inst — unimplemented → build docs, implemented → use docs.
    public static final fURI DOC_BUILD = f("build");
    public static final Type DOCQ_TYPE = Type.Builder.build()
            .tid(QPROC_TID)
            .vid(DOCQ_TID)
            .constructor(rec -> rec.isNoObj() || rec.asRec().isEmpty() ? QCollection.docQ() : QCollection.docQ(rec))
            .create();
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
                            uri(DOC_BUILD).maybe(), STR_TYPE,
                            uri(EXAMPLE).maybe(), LST_TYPE))
                    .constructor(arg0 -> new Docs(arg0.recValue(), DOCS_TID, null))
                    .inst(AS_INST_TID.dom(DOCS_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(lhs.toString()))
                    .create();

    //
    public static final fURI TYPEQ_PATTERN = f("T");
    public static final fURI TYPEQ_TID = QPROC_TID.extend("typeq");
    public static final Type TYPEQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(TYPEQ_TID).constructor(QCollection::typeQ).create();
    //
    public static final fURI SUBQ_PATTERN = f("subq");
    public static final fURI SUBQ_TID = QPROC_TID.extend(SUBQ_PATTERN);
    public static final fURI SUBQ_SUB_TID = SUBQ_TID.extend("sub");
    public static final fURI SUBQ_PUB_TID = SUBQ_TID.extend("pub");
    public static Type SUBQ_TYPE;
    //
    public static final fURI LOCKQ_PATTERN = f("lockq");
    public static final fURI LOCKQ_TID = QPROC_TID.extend(LOCKQ_PATTERN);
    public static final fURI LOCKQ_LOCK_TID = LOCKQ_TID.extend("lock");
    public static final Type LOCKQ_TYPE = Type.Builder.build().tid(QPROC_TID).vid(LOCKQ_TID).constructor(QCollection::lockQ).create();
    public static final Type LOCK_TYPE =
            docWrap(Type.Builder.build()
                            .tid(REC_TID)
                            .vid(LOCKQ_LOCK_TID)
                            .isaPredicate(rec(
                                    uri(USR), URI_TYPE,
                                    uri(EXPIRE).maybe(), DATETIME_TYPE))
                            .create(), null, null, mutableMap(
                            uri(USR), "the owner of the lock",
                            uri(EXPIRE), "when the lock expires (a datetime::T) — no value means never"),
                    "an advisory lock over a region of space",
                    "cs:src/#?lockq -> lock::[usr=>/usr/agent1,expire=>datetime://...]",
                    "cs:src/.../Foo.java -> ...  [-- throws while a matching lock is held --]",
                    "cs:src/#?lockq -> noobj    [-- releases the lock --]");
    public static final Type SUB_TYPE =
            docWrap(Type.Builder.build()
                            .tid(REC_TID)
                            .vid(SUBQ_SUB_TID)
                            .isaPredicate(rec(
                                    uri(TARGET).maybe().asUri(), URI_TYPE,
                                    uri(CODE), T(ALL.dom(LST_TID))))
                            .create(), null, null, mutableMap(
                            uri(TARGET), "the pattern that will trigger the code callback (automatically added when new sub created)",
                            uri(CODE), "the code to execute when target state changes"),
                    "subscribe to mutations over regions of space",
                    "abc?subq -> sub::[code=>print(==obj+1)] [-- mutations to abc generate pub::T objs pass through sub::T code --]",
                    "abc -> 5                                [-- prints 6 to stdout                                             --]",
                    "see pub::T");
    public static final Type PUB_TYPE =
            docWrap(Type.Builder.build()
                            .tid(LST_TID)
                            .vid(SUBQ_PUB_TID)
                            .isaPredicate(lst(URI_TYPE, T(ALL_STAR)))
                            .create(), null, null, mutableMap(
                            jnt(0), "the uri that triggered this publication",
                            jnt(1), "the obj that triggered this publication"),
                    "publications are generated when there is a source<=>target match with subscriptions",
                    "abc?subq -> sub::[code=>>>1+1.println(_).to(abc)] [-- mutations to abc generate pub::T objs passed through sub::T code --]",
                    "abc      -> 5                                       [-- triggers creation of pub::[abc,5]                      --]",
                    "see sub::T");

    private QCollection() {
        // do nothing 
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static int[] lineRange(final fURI lineq, final int maxLines) {
        final String lines = lineq.q(LINEQ_PATTERN.toString()).trim();
        if (lines.endsWith("+")) {
            // insert mode: push everything below down, never overwrite.
            // lineq=+ → insert at the end; lineq=N+ → insert before line N; lineq=0+ → at the top.
            final String num = lines.substring(0, lines.length() - 1).trim();
            if (num.isEmpty())
                return new int[]{maxLines, maxLines - 1}; // insert at end
            final int insert = Integer.parseInt(num);
            return new int[]{Math.min(insert, maxLines), Math.min(insert, maxLines) - 1};
        }
        final String[] lineRange = lines.split("-");
        if (lineRange.length == 0 || lineRange.length > 2)
            throw MTronException.of("not a legal line range: %s", lines);
        int start = Integer.parseInt(lineRange[0].trim());
        int stop = lineRange.length == 2 ? Integer.parseInt(lineRange[1].trim()) : start;
        if (start >= maxLines)
            return new int[]{maxLines, maxLines - 1}; // appending: the position after the last line
        if (stop >= maxLines)
            stop = maxLines - 1;
        return new int[]{start, stop};
    }

    public static QProc refQ() {
        return QProc.Helper.build(REFQ_TID, REFQ_PATTERN).postRead((u, o) ->
                        objs(Stream.of(u.q(REFQ_PATTERN).split(","))
                                .map(fURI.Singleton::f)
                                .map(Router::readFromSpace)).append(o))
                .create();
    }

    public static QProc lineQ() {
        return QProc.Helper.build(LINEQ_TID, LINEQ_PATTERN)
                .preWrite((furi, obj) -> {
                    final String objString = Str.Helper.cleanString(Router.readFromSpace(furi.removeQ(LINEQ_PATTERN)));
                    // split with -1 so trailing empties (and thus blank-line structure) are preserved
                    final String[] split = objString.split("\n", -1);
                    final int[] lineRange = lineRange(furi, split.length);
                    final String cleaned = Str.Helper.cleanString(obj);
                    // an empty replacement is a deletion (zero lines); otherwise split the replacement
                    final String[] replacement = cleaned.isEmpty() ? new String[0] : cleaned.split("\n", -1);
                    final List<String> result = new ArrayList<>(split.length + replacement.length);
                    for (int i = 0; i < lineRange[0]; i++) result.add(split[i]);
                    result.addAll(Arrays.asList(replacement));
                    for (int i = lineRange[1] + 1; i < split.length; i++) result.add(split[i]);
                    Router.writeToSpace(furi.removeQ(LINEQ_PATTERN), str(String.join("\n", result)));
                    return obj;
                }).postRead((furi, obj) -> {
                    final String objString = Str.Helper.cleanString(obj);
                    final String[] split = objString.split("\n");
                    int[] lineRange = lineRange(furi, split.length);
                    final String[] result = new String[(lineRange[1] - lineRange[0]) + 1];
                    int counter = 0;
                    for (int i = lineRange[0]; i <= lineRange[1]; i++) {
                        result[counter++] = split[i];
                    }
                    return str(String.join("\n", result));
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static QProc mimeQ() {
        return QProc.Helper.build(MIMEQ_TID, MIMEQ_PATTERN)
                .postRead((furi, obj) -> {
                    final String mime = furi.q(MIMEQ_PATTERN.toString());
                    final MIME.MIMEType mimeType = MIME.MIMEType.of(mime);
                    if (null == mimeType)
                        throw MTronException.of("unknown mime type: %s", mime);
                    if (!mime.equals(mimeType.value))
                        throw MTronException.of("mime-type mismatch: %s %s", mime, mimeType.value);
                    // Determine the content-specific MIME type from the obj's TID,
                    // falling back to URI/file extension if the TID is bare STR_TID.
                    // This is independent of the ?mimeq= value — ?mimeq= controls what
                    // we DO with the result (tag vs structural parse), not what the content IS.
                    MIME.MIMEType probed = MIME.MIMEType.fromType(obj, null);
                    if (null == probed)
                        probed = MIME.MIMEType.fromExtension(furi.name(), null);
                    // Step 1: tag with probed MIME's TID (triggers predicate validation)
                    if (null != probed) {
                        final fURI tid = probed.toTid();
                        if (null != tid) {
                            obj = obj.tid(tid);
                            // Step 2: application/x-mtron → structural parse via content serializer
                            if (MIME.MIMEType.APPLICATION_MTRON == mimeType && obj.isStr())
                                return probed.serializer().inputBytes(ByteBuffer.wrap(obj.strValue().getBytes(StandardCharsets.UTF_8)));
                            return obj;
                        }
                    }
                    // No content-type match — fall back to mimeq serialization
                    // (e.g. str "hello" with ?mimeq=application/json → "\"hello\"")
                    if (mimeType.hasSerializer())
                        return str(mimeType.serializer().write(obj).toString());
                    return obj;
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static QProc mintQ() {
        return QProc.Helper.build(MINTQ_TID, MINTQ_PATTERN)
                .preWrite((furi, obj) -> {
                    final fURI mint = CommonUtil.mintShortUUID(furi.basePath(), true);
                    final Obj mintedObj = obj.vid(mint);
                    LOG.info("vid %s minted for %s", mint, mintedObj);
                    Router.writeToSpace(mintedObj);
                    return mintedObj;
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static QProc constQ() {
        final Set<fURI> CONSTQ_FURIS = new HashSet<>();
        return QProc.Helper.build(CONSTQ_TID, CONSTQ_PATTERN)
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

    public static QProc typeQ() {
        final memSpace TYPE_SPACE = memSpace.of(rec(uri(PATTERN), uri("#")), null);
        return QProc.Helper.build(TYPEQ_TID, TYPEQ_PATTERN)
                .obj(f(SPACE), TYPE_SPACE)
                .preWrite((vid, obj) -> {
                    TYPE_SPACE.write(vid.qLess(), obj);
                    return obj;
                })
                .preRead(vid -> {
                    final Obj type = TYPE_SPACE.read(vid.qLess());
                    if (type.isNoObj()) {
                        return T(ALL.maybeSome());
                    }
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
    public static final String NO_DOCS_STRING = "no documentation available";
    public final static Rec NO_DOCS = rec(mutableMap(uri(DESC), str(NO_DOCS_STRING)), DOCS_TID, null);

    public static boolean isNoDocs(final Obj obj) {
        if (obj.isNoObj())
            return true;
        return obj.asRec().at(DESC).orElse(str("okay")).equals(str(NO_DOCS_STRING));
    }

    public static boolean hasDocs(final Obj obj) {
        if (obj.isNoObj())
            return false;
        return !obj.asRec().at(DESC).orElse(str(NO_DOCS_STRING)).equals(str(NO_DOCS_STRING));
    }

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
                INST_TABLE.computeIfAbsent(inst.tid().basePath(), k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(obj.asRec());
            }
            return obj;
        }

        @Override
        public Obj read(final fURI pattern) {
            // snapshot to avoid ConcurrentModificationException during concurrent writes
            final List<Map.Entry<fURI, Set<Rec>>> entries;
            synchronized (INST_TABLE) {
                entries = new ArrayList<>(INST_TABLE.entrySet());
            }
            return objs(entries
                    .stream()
                    .filter(kv -> kv.getKey().test(pattern.basePath().asNode()))
                    .flatMap(kv -> {
                        final Set<Rec> set = kv.getValue();
                        synchronized (set) {
                            return new ArrayList<>(set).stream();
                        }
                    })
                    .filter(i -> !pattern.hasDom() || i.asRec().at(OBJ).dom().test(T(pattern.dom().big())))
                    .filter(i -> !pattern.hasRng() || i.asRec().at(OBJ).rng().test(T(pattern.rng().big())))
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

    public static QProc docQ() {
        return docQ(noobj());
    }

    public static QProc docQ(final Obj initialDocs) {
        final memSpace OBJ_DOCS = memSpace.of(ALL, null);
        final InstSet INST_DOCS = new DocInstSet();
        final QProc docq = QProc.Helper.build(DOCQ_TID, DOCQ_PATTERN)
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
                    final Obj instDoc = INST_DOCS.read(vid.removeQ(DOCQ));
                    final Obj doc = instDoc.isNoObj() ?
                            OBJ_DOCS.read(vid.removeQ(DOCQ)).orElse(NO_DOCS.plus(rec(uri(OBJ), Router.global().read(vid.removeQ(DOCQ))))) :
                            instDoc;
                    // dual-mode interface doc: a doc carrying 'build' (how to implement) alongside
                    // 'desc' (how to use).  The branch is implementation status: an interface inst's
                    // tid is the docq'd uri itself, and the watchdog swaps in the implementation on
                    // write — so implemented iff the live inst is no longer the interface.  Unimplemented
                    // → surface the build docs; implemented → surface the use docs.
                    if (doc.isRec() && doc.asRec().has(DOC_BUILD)) {
                        final Obj live = Router.global().read(vid.removeQ(DOCQ));
                        final boolean implemented = !live.isNoObj() && live.isInst() &&
                                !live.<Inst>as().tid().basePath().equals(vid.removeQ(DOCQ));
                        if (!implemented)
                            return ((Obj) doc.asRec().at(DESC, doc.asRec().at(DOC_BUILD))).tid(DOCS_TID);
                    }
                    return doc;
                })
                .create();
        if (!initialDocs.isNoObj()) {
            initialDocs.asRec().elements().forEach(doc -> {
                docq.onWrite().get().preWrite(doc.first().uriValue().addQ("docq"), doc.second());
            });
        }
        return docq;
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static Obj shortObj(final Obj o, final int currentDepth, final int maxLength, final int maxDepth) {
        if (currentDepth >= maxDepth)
            return noobj();
        if (o.isStr()) {
            final String valueString = o.toCleanString();
            return str(valueString.substring(0, Math.min(valueString.length(), maxLength)));
        } else if (o.isUri()) {
            final String valueString = o.toCleanString();
            return uri(valueString.substring(0, Math.min(valueString.length(), maxLength)));
        } else if (o.isMono()) {
            return o;
        } else if (o.isRec()) {
            return o.asRec().jvm().entrySet().stream().map(kv -> rel(kv.getKey(), shortObj(kv.getValue(), currentDepth + 1, maxLength, maxDepth))).collect(new CommonUtil.RecCollector());
        } else if (o.isLst()) {
            return o.asLst().jvm().stream().map(e -> shortObj(e, currentDepth + 1, maxLength, maxDepth)).collect(new CommonUtil.LstCollector());
        }
        return o;
    }

    public static int DEFAULT_SHORTQ_MAX_LENGTH = 25;

    public static QProc shortQ() {
        return QProc.Helper.build(SHORTQ_TID, SHORTQ_PATTERN).
                postRead((vid, obj) -> {
                    final Integer maxLength = vid.q(SHORTQ_PATTERN.toString()).isBlank() ? DEFAULT_SHORTQ_MAX_LENGTH : vid.qValue(SHORTQ_PATTERN.toString(), Integer.class);
                    return QCollection.shortObj(obj, 0, maxLength, 2);
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static QProc safeQ() {
        return QProc.Helper.build(SAFEQ_TID, SAFEQ_PATTERN).
                qlessWrite((vid, obj) -> {
                    LOG.warn("allow {{b}}%s{{m}}::T{{X}} to be written to {{b}}%s{{X}} [{{g}}Y{{X}}/{{r}}n{{X}}]?", obj.tid().small(), vid);
                    final Obj result = instB(f("stdin"), lst()).apply();
                    if (result.strValue().toLowerCase().startsWith("n")) {
                        LOG.warn("{{r}}denying{{X}} writing to %s", vid);
                        return str("access denied");
                    } else {
                        LOG.warn("{{g}}accepting{{X}} writing to %s", vid);
                        return noobj();
                    }
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final String EMBEDQ_INCR_PATTERN = "_";

    public static QProc embedQ() {
        return QProc.Helper.build(EMBEDQ_TID, EMBEDQ_PATTERN)
                .preRead(vid -> {
                    // Derive embedding URI as a parallel collection:
                    //   drdb:msg/1/0?embedq=abc
                    //   → drdb:embedding/abc/{hash(drdb:msg/1/0)}
                    // The space's route (e.g. drdb:embedding/abc => v:abc)
                    // dispatches to the embedding space.
                    final fURI model = vid.qValue(EMBEDQ_PATTERN, fURI.class);
                    final fURI sourceVid = vid.qLess();
                    final String hash = Integer.toHexString(sourceVid.toString().hashCode());
                    final fURI embedVid = f(sourceVid.scheme() + ":embedding/" + model + "/" + hash);
                    final Obj embedding = Router.readFromSpace(embedVid);
                    if (!embedding.isNoObj())
                        return embedding;
                    // Lazy compute: read source, write to embedding URI.
                    final Obj source = Router.readFromSpace(sourceVid);
                    if (source.isNoObj())
                        return source;
                    return Router.writeToSpace(embedVid, source);
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final String INCRQ_INCR_PATTERN = "_";

    public static QProc incrQ() {
        final java.util.Map<String, AtomicLong> counters = new java.util.concurrent.ConcurrentHashMap<>();
        return QProc.Helper.build(INCRQ_TID, INCRQ_PATTERN).
                preWrite((vid, obj) -> {
                    final fURI incrPattern = vid.extend(vid.qValue(INCRQ_PATTERN, fURI.class)).resolve();
                    // Per-collection counter so each typed collection has its own ID sequence.
                    final StringBuilder prefix = new StringBuilder();
                    final List<String> newPath = new ArrayList<>();
                    for (final String p : incrPattern.path()) {
                        if (p.equals(INCRQ_INCR_PATTERN)) {
                            final AtomicLong counter = counters.computeIfAbsent(
                                    prefix.toString(), k -> new AtomicLong(0));
                            newPath.add(counter.incrementAndGet() + "");
                        } else {
                            if (!prefix.isEmpty()) prefix.append("/");
                            prefix.append(p);
                            newPath.add(p);
                        }
                    }
                    final fURI cleaned = vid.removeQ(INCRQ_PATTERN).path(newPath);
                    final Obj stored = obj.vid(cleaned);
                    // QProc handles storage itself (same pattern as tbleIncrQ).
                    // cleaned URI has no ?incrq → won't rematch on recursive write.
                    return Router.writeToSpace(cleaned, stored);
                    //return obj;
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static QProc subq() {
        final Lst subscriptions = lst(new ArrayList<>());
        return QProc.Helper.build(SUBQ_TID, SUBQ_PATTERN)
                .obj(f(OBJ), subscriptions)
                .preRead(vid -> {
                    subscriptions.logger().debug("reading: %s", vid.basePath());
                    return lst(subscriptions.elements().filter(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue())));
                })
                .preWrite((vid, obj) -> {
                    final Obj subscription;
                    final fURI subID = vid.qValue(SUBQ, fURI.class);
                    if (obj.isNoObj() || obj.isNone()) {
                        subscription = noobj();
                        subscriptions.lstValue().removeIf(existingSub ->
                                (subID != null && null != existingSub.vid() && existingSub.vid().bimatches(subID)) ||
                                        vid.basePath().bimatches(existingSub.asRec().at(TARGET).uriValue()));
                        obj.logger().info("unsubscribing from %s", vid.basePath());
                    } else if (obj.tid().basePath().equals(SUBQ_SUB_TID)) {
                        subscription = obj;
                        if (!subscription.asRec().has(TARGET))
                            subscription.asRec().at(TARGET, uri(vid.basePath()), MUTABLE);
                        if (subscriptions.lstValue().stream().noneMatch(subscription::equals)) {
                            subscriptions.lstValue().add(subscription);
                            subscription.logger().info("subscribing to %s", vid.basePath());
                        }
                    } else {
                        subscription = rec(mutableMap(uri(TARGET), uri(vid.basePath()), uri(CODE), obj), SUBQ_SUB_TID, null);
                        subscriptions.lstValue().add(subscription);
                        subscription.logger().info("subscribing to %s", vid.basePath());
                    }
                    //LOG.debug("current subscriptions: %s", subscriptions);
                    return subscription;
                })
                .qlessWrite((vid, obj) -> {
                    // subscriptions.logger().info("qless write to %s", vid.basePath());
                    if (vid.hasQ(SUBQ))
                        return noobj();
                    subscriptions.elements().filter(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue()))
                            .forEach(s -> {
                                subscriptions.logger().debug("spawning virtual thread for subscription code: %s", s);
                                virtual(s.asRec().jvm().getOrDefault(uri(CODE), noobj())).applyAsync(lst(List.of(vid.basePath().toUri(), obj), SUBQ_PUB_TID, null));
                            });
                    return noobj();
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static QProc lockQ() {
        final Lst locks = lst(new ArrayList<>());
        return QProc.Helper.build(LOCKQ_TID, LOCKQ_PATTERN)
                .obj(f(OBJ), locks)
                // read: list the locks matching the read uri pattern
                .preRead(vid -> lst(locks.elements()
                        .filter(e -> vid.basePath().test(e.asRec().at(TARGET).uriValue()))))
                // write: ?lockq=lock::[usr=>...,expire=>...] registers; ?lockq=noobj releases
                .preWrite((vid, obj) -> {
                    if (obj.isNoObj() || obj.isNone()) {
                        locks.lstValue().removeIf(existing ->
                                vid.basePath().bimatches(existing.asRec().at(TARGET).uriValue()));
                        locks.logger().info("released lock on %s", vid.basePath());
                        return noobj();
                    }
                    final Obj lock = obj.asRec().has(TARGET)
                            ? obj
                            : obj.asRec().at(TARGET, uri(vid.basePath())); // registry copy carries the pattern
                    if (locks.lstValue().stream().noneMatch(lock::equals))
                        locks.lstValue().add(lock);
                    locks.logger().info("locked %s by %s", vid.basePath(), lock.asRec().at(USR));
                    return obj; // the writer receives exactly what they wrote
                })
                // qless write: every write to the space passes through here — the conflict check.
                // a matching, unexpired lock blocks the write (advisory).  The lock's owner may
                // re-enter (their own lock doesn't block them); until threads carry an `owner`
                // field, currentOwner() resolves to noobj so any non-owner write blocks.
                .qlessWrite((vid, obj) -> {
                    final Obj writer = currentOwner();
                    for (final Obj l : locks.elements().toList()) {
                        if (!l.isRec()) continue;
                        final Rec lock = l.asRec();
                        final Obj target = lock.at(TARGET);
                        final Obj usr = lock.at(USR);
                        if (target.isNoObj() || usr.isNoObj()) continue;
                        if (vid.basePath().test(target.uriValue()) && !expired(lock.at(EXPIRE)) && !usr.equals(writer)) {
                            return fail(MTronException.of("write blocked: %s is locked by %s", vid.toUri(false), usr));
                        }
                    }
                    return noobj(); // no lock held — let the write proceed
                }).create();
    }

    /**
     * Resolve the identity of the writing thread by walking the thread's {@code source}
     * spine to its root and reading {@code owner}.  Threads do not carry an {@code owner}
     * field yet — once they do, resolve via {@code Router.THREAD_STACK}'s thread rec.
     */
    private static Obj currentOwner() {
        return noobj(); // TODO: walk thread source spine → owner when threads carry it
    }

    /**
     * True if the {@code expire} datetime::T is in the past.  No expiry → never expires.
     */
    private static boolean expired(final Obj expire) {
        if (expire.isNoObj() || !expire.isUri()) return false; // never expires
        try {
            return System.currentTimeMillis() > mathInstSet.datetimeToMillis(expire.asUri());
        } catch (final Exception e) {
            return true; // malformed expiry — treat as expired so it can't block forever
        }
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Docs internalDocWrap(final Obj obj, final String domDesc, final String rngDesc,
                                        final Map<Obj, String> argDescription, final String description, final String... examples) {
        if (obj.isNoObj())
            return new Docs("nothing").c(cInt.ZERO()).as();
        final fURI objID = obj.isInst() ? obj.tid() : obj.vid();
        if (null == objID) {
            obj.logger().warn("unable to generate docs for a vid-less obj: %s", obj);
            return new Docs("nothing").c(cInt.ZERO()).as();
        }
        final Docs doc = Docs.doc(obj, domDesc, rngDesc, argDescription, description, examples);
        final Space objSpace = Router.global().getSpaceFor(objID);
        final Optional<QProc> docq = objSpace.qs().jvm().stream().filter(q -> q.tid().basePath().equals(DOCQ_TID)).map(Obj::<QProc>as).findAny();
        if (docq.isEmpty())
            objSpace.logger().warn("no doc query attachment mounted on %s for %s", objSpace, objID);
        else if (obj.isInst()) {
            docq.get().at(INST).<Space>as().write(objID, doc);
        } else {
            docq.get().at(OBJ).<Space>as().write(objID, doc);
        }
        return doc;
    }

    public static Inst docWrap(final Inst inst, final String domDesc, final String rngDesc,
                               final Map<Obj, String> argDescription, final String description, final String... examples) {
        internalDocWrap(inst, domDesc, rngDesc, argDescription, description, examples);
        return inst;
    }

    public static Docs docWrapDocs(final Inst inst, final String domDesc, final String rngDesc,
                                   final Map<Obj, String> argDescription, final String description, final String... examples) {
        return internalDocWrap(inst, domDesc, rngDesc, argDescription, description, examples);
    }

    public static Inst docWrap(final Inst inst, final String description, final String... examples) {
        internalDocWrap(inst, inst.tid().dom().toString(), inst.tid().rng().toString(),
                inst.args().isRec() ?
                        inst.args().asRec().elements()
                                .map(r -> Tuple.Pair.with(r.first(), r.second().tid().toString()))
                                .collect(Collectors.<Tuple.Pair<Obj, String>, Obj, String>toMap(Tuple.Pair::get0, Tuple.Pair::get1)) :
                        inst.args().asLst().indexedStream().map(r -> Tuple.Pair.with(r.first(), r.second().tid().toString()))
                                .collect(Collectors.<Tuple.Pair<Obj, String>, Obj, String>toMap(Tuple.Pair::get0, Tuple.Pair::get1)),
                description,
                examples);
        return inst;
    }

    public static <OBJ extends Obj> OBJ docWrap(final OBJ obj, final String description, final String... examples) {
        internalDocWrap(obj, null, null, null, description, examples);
        return obj;
    }

    public static Type docWrap(final Type type, final String predicate, final String constructor,
                               final Map<Obj, String> predicateDescription, final String description, final String... examples) {
        internalDocWrap(type, predicate, constructor, predicateDescription, description, examples);
        return type;
    }

    public static InstSet docWrap(final InstSet instSet, final String description, final String... examples) {
        internalDocWrap(instSet, null, null, null, description, examples);
        return instSet;
    }

    public static class Docs extends MRec {

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
            final List<Str> ex = Arrays.stream(examples).map(MStr::str).toList();
            return new Docs(mutableMap(
                    uri(OBJ), inst,
                    uri(DOM), null == domDesc || domDesc.isBlank() ? noobj() : str(domDesc),
                    uri(RNG), null == rngDesc || rngDesc.isBlank() ? noobj() : str(rngDesc),
                    uri(ARGS), null == argDescription || argDescription.isEmpty() ? noobj() : rec(argDescription.entrySet().stream().map(kv -> rel(kv.getKey(), str(kv.getValue())))),
                    uri(DESC), null == description || description.isBlank() ? noobj() : str(description),
                    uri(EXAMPLE), (ex.isEmpty() ? noobj() : lst((List) ex))), DOCS_TID, null);
        }
    }
}
