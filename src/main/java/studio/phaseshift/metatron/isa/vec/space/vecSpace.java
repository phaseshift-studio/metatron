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

package studio.phaseshift.metatron.isa.vec.space;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_EMBEDDING_TYPE;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A ChromaDB-backed {@link Space} that stores type-preserving
 * {@link ObjmtronSerializer}-serialized documents with auto-generated
 * vector embeddings via ChromaDB v2 API (&ge; 1.0).
 *
 * <h3>URI Pattern</h3>
 * <pre>
 *   vctr:/collection/entry/field/extension...
 * </pre>
 *
 * <h3>Storage model</h3>
 * <p>Every value written to the space is serialized via
 * {@link ObjmtronSerializer} and stored as the ChromaDB document text.
 * The embedding is generated from the serialized form so type information
 * (e.g. {@code jnt::345}) is preserved in the vector representation.
 * On read, documents are deserialized back to the original typed Obj.</p>
 *
 * <h3>Virtual fields</h3>
 * <dl>
 *   <dt>{@code embedding}</dt>
 *   <dd>Returns the document's raw vector embedding as a {@link Lst}
 *       of {@code real} values.</dd>
 * </dl>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class vecSpace extends AbstractSpace<VectorDBClient> implements SchemaSpace {
    // ---- Field keys ------------------------------------------------------------

    public static final String DOC_ID = "_id";
    public static final String DOC_TEXT = "_text";
    public static final String EMBEDDING = "embedding";
    // ---- Type system -----------------------------------------------------------

    public static final fURI VEC_SPACE_TID = VEC_ISA_TID.extend(SPACE).extend("vecspace");

    public static final Type VCTR_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(VEC_SPACE_TID)
            .isaPredicate(rec(
                    uri(HOST), URI_TYPE,
                    uri(DRIVER), URI_TYPE,
                    uri(CONFIG), rec(uri(EMBED), rec(URI_TYPE, ALL_TYPE))))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(VEC_SPACE_TID),
                    lst(REC_TYPE),
                    (lhs, inst) -> {
                        final VectorDBClient client = VectorDBClient.Helper.loadService(inst.arg(0).asRec().at(DRIVER).uriValue());
                        client.setup(lhs.asRec().at(HOST).uriValue(), mutableMap((Map) inst.arg(0).asRec().at(CONFIG).orElse(rec0()).at(EMBED).orElse(rec0()).recValue()));
                        return vecSpace.of(client, inst.arg(0).recValue(), inst.arg(0).vid());
                    }))
            .create();

    // ---- Instance state --------------------------------------------------------

    private InstSet schemaInstset;

    // =========================================================================
    //  Factory
    // =========================================================================

    public static vecSpace of(final VectorDBClient client,
                              final Map<Obj, Obj> config, final fURI vid) {
        return new vecSpace(client, new LinkedHashMap<>(config), vid);
    }

    // =========================================================================
    //  Constructor
    // =========================================================================

    protected vecSpace(final VectorDBClient client,
                       final Map<Obj, Obj> config, final fURI vid) {
        super(client, config, VEC_SPACE_TID, vid);
        final Lst qprocs = this.at(uri(QPROC)).orElse(lst()).asLst();
        if (!qprocs.isEmpty()) {
            final List<QProc> snapshot = new ArrayList<>(qprocs.<QProc>elements().toList());
            this.at(uri(QPROC), lst(), MUTABLE);
            for (final QProc q : snapshot)
                this.addQ(q);
        }

        LOG.info("connected {{b}}%s{{X}}", this.at(HOST));
        initializeSchema();
    }

    // =========================================================================
    //  Schema
    // =========================================================================

    private void initializeSchema() {
        final fURI schemaVid = this.vid().extend(INSTSET);
        try {
            final Set<Type> types = new LinkedHashSet<>();
            for (final VectorDBClient.CollectionData c : this.sjvm().listCollections()) {
                if (c.name() == null) continue;
                types.add(collectionType(c.name(), schemaVid));
            }
            this.schemaInstset = createSchemaInstset(schemaVid, types);
        } catch (final Exception e) {
            LOG.warn("could not list collections");
            this.schemaInstset = createSchemaInstset(schemaVid, Set.of());
        }
        Router.global().addSpace(this.schemaInstset);
        this.schemaInstset.setup();
        this.at(uri(SCHEMA), this.schemaInstset, MUTABLE);
    }

    private static Type collectionType(final String name, final fURI schemaVid) {
        return Type.Builder.build()
                .tid(REC_TID)
                .vid(schemaVid.extend(name))
                .isaPredicate(rec(
                        uri(DOC_ID), URI_TYPE,
                        uri(DOC_TEXT), STR_TYPE,
                        uri(EMBEDDING), LST_TYPE))
                .create();
    }

    private static InstSet createSchemaInstset(final fURI schemaVid,
                                               final Set<Type> types) {
        return new AbstractInstSet(
                mutableMap(
                        uri(PATTERN), uri(schemaVid.extend(ALL)),
                        uri(TYPE), lst(types.stream().map(t -> (Obj) t).toList())),
                INSTSET_TID, schemaVid) {
            @Override
            public void setup() {
                super.setup();
            }
        };
    }

    @Override
    public InstSet schema() {
        return this.schemaInstset;
    }

    private void onCollectionCreated(final String name) {
        if (this.schemaInstset == null) return;
        final Type type = collectionType(name, this.schemaInstset.vid());
        Router.writeToSpace(type.vid(), type);
        LOG.info("registered type {{b}}%s{{X}} for collection %s", type.vid(), name);
    }

    // =========================================================================
    //  Collection helpers
    // =========================================================================

    private fURI resolveCollectionId(final String name, final boolean create) {
        try {
            final VectorDBClient.CollectionData coll = this.sjvm().getCollection(name);
            return coll.id();
        } catch (final Exception e) {
            if (create) {
                LOG.warn("collection %s not found, creating: %s", name, e.getMessage());
                try {
                    final VectorDBClient.CollectionData created = this.sjvm().createCollection(name);
                    onCollectionCreated(name);
                    return created.id();
                } catch (final Exception ex) {
                    throw MTronException.of(ex);
                }
            }
            throw MTronException.of(e);
        }
    }

    // =========================================================================
    //  QProc override
    // =========================================================================

    @Override
    public Space addQ(final QProc qProc) {
        final QProc toAdd;
        if (qProc.pattern().equals(
                studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN))
            toAdd = new vecIncrQ(this);
        else if (qProc.pattern().equals(
                studio.phaseshift.metatron.furi.q.QCollection.EMBEDQ_PATTERN))
            toAdd = new vecEmbedQ(this);
        else
            toAdd = qProc;
        final Obj key = uri(QPROC);
        if (this.at(key).isNoObj())
            this.at(key, lst(), MUTABLE);
        this.at(key).asLst().add(toAdd, MUTABLE);
        return this;
    }

    // =========================================================================
    //  I/O — directReader
    // =========================================================================

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            try {
                final fURI aligned = Space.Helper.routeFromSpace(pattern, this.routes());
                final DataPath dp = DataPath.withoutDB(aligned);
                // ── collection-level → schema type ──
                if (dp.hasCollection() && !dp.hasEntry()) {
                    final Iterator<IdObj> schemaResults =
                            resolveCollectionSchema(dp.collection());
                    if (schemaResults.hasNext())
                        return collect(schemaResults, pattern);
                    return IteratorUtil.of();
                }

                if (!dp.hasCollection()) return IteratorUtil.of();

                final fURI collId = resolveCollectionId(dp.collection(), false);
                final String docId = aligned.toString();
                // ── wildcard entry → all documents ──
                if (dp.entryIsWildcard()) {
                    final VectorDBClient.GetResult all = this.sjvm().getAll(collId);
                    return resultToIdObjs(all).iterator();
                }
                // ── exact entry ──
                final VectorDBClient.GetResult result = this.sjvm().get(collId, List.of(f(docId).name()));
                LOG.debug("retrieving: %s => %s", collId, result);
                return IteratorUtil.asIterator(result.entities().stream().map(e -> IdObj.of(e.id(), e.obj())));
            } catch (final Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("does not exist"))
                    return IteratorUtil.of();
                throw MTronException.of(e);
            }
        };
    }

    private List<IdObj> resultToIdObjs(final VectorDBClient.GetResult all) {
        return all.entities().stream().map(e -> IdObj.of(e.id(), e.obj())).toList();
    }

    private Iterator<IdObj> collect(final Iterator<IdObj> raw,
                                    final fURI pattern) {
        final List<IdObj> all = new ArrayList<>();
        raw.forEachRemaining(kv -> all.add(IdObj.of(
                Space.Helper.routeToSpace(kv.furi(), this.routes()), kv.obj())));
        return all.iterator();
    }

    // =========================================================================
    //  I/O — directWriter
    // =========================================================================

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            LOG.debug("writing to %s: %s", pattern, obj);
            try {
                final fURI aligned = Space.Helper.routeFromSpace(pattern, this.routes());
                final DataPath dp = DataPath.withoutDB(aligned);
                LOG.debug("furi aligned: %s", aligned);
                if (!dp.hasCollection())
                    throw MTronException.of("vecspace write requires a collection: %s", aligned);

                // ── DELETE ──
                if (obj.isNoObj()) {
                    if (dp.hasEntry() && !dp.entryIsWildcard()) {
                        LOG.debug("deleting %s", dp);
                        final fURI collId = resolveCollectionId(dp.collection(), false);
                        this.sjvm().delete(collId, List.of(dp.entry()));
                    }
                    return noobj();
                }

                // ── WRITE: docId = routed path (e.g. "test/a") ──
                final fURI collId = resolveCollectionId(dp.collection(), true);
                boolean isAlreadyEmbedded = obj.test(VEC_EMBEDDING_TYPE);
                final Obj rawObj = isAlreadyEmbedded ? obj.asRec().at(OBJ) : obj;
                final Lst embedding = isAlreadyEmbedded ? obj.asRec().at(EMBED) : this.sjvm().embeddingFunction(f(dp.collection())).apply(obj).as();
                final Rec metadata = isAlreadyEmbedded ? obj.asRec().at(META).orElse(rec0()) : rec0();
                if (embedding.isFail())
                    throw embedding.asFail().asException();
                final VectorDBClient.EntityData entity = new VectorDBClient.EntityData(f(aligned.name()), rawObj, metadata, embedding);
                this.sjvm().upsert(collId, f(dp.entry()), entity);
                LOG.debug("wrote: %s => %s", collId, entity);
                return obj;
                //return pattern.hasPattern() ? obj : obj.selfVID(pattern);

            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    // =========================================================================
    //  Similarity query
    // =========================================================================

    /**
     * Query a collection for nearest neighbors of the given embedding vectors.
     * Delegates to the underlying {@link VectorDBClient#query}.
     *
     * @param collectionName  the collection to query
     * @param queryEmbeddings the query vectors
     * @param nResults        max results per query vector
     * @return one GetResult per query vector
     */
    public List<VectorDBClient.GetResult> query(final String collectionName,
                                                final List<Lst> queryEmbeddings,
                                                final int nResults) throws Exception {
        final fURI collId = resolveCollectionId(collectionName, false);
        return this.sjvm().query(collId, queryEmbeddings, nResults);
    }

    // =========================================================================
    //  Lifecycle
    // =========================================================================

    @Override
    public void close() {
        try {
            SchemaSpace.super.close();
        } finally {
            super.close();
        }
    }
}
