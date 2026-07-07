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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/**
 * Minimal abstraction over a vector-database backend.
 * <p>
 * Each implementation (ChromaDB, Pinecone, Weaviate, Qdrant, etc.)
 * provides collection management and document CRUD with auto-generated
 * embeddings.  Implementations are discovered via
 * {@code META-INF/services/studio.phaseshift.metatron.isa.vec.space.VectorDBClient}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface VectorDBClient {

    /**
     * Information about a single collection.
     */
    record CollectionData(String name, fURI id) {
    }

    record EntityData(fURI id, Obj obj, Rec metadata, Lst embedding) {

    }

    /**
     * Result from a document get/query operation.
     */
    final class GetResult {
        // ChromaDB JSON response fields — GSON populates these directly
        private List<String> ids;
        private List<String> documents;
        private List<Map<String, Object>> metadatas;
        private List<List<Float>> embeddings;
        private List<Float> distances;

        public GetResult() {}

        /** Lazily materialized from the raw ChromaDB response fields. */
        public List<EntityData> entities() {
            if (ids == null) return List.of();
            final List<EntityData> result = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                final String doc = documents != null && i < documents.size() ? documents.get(i) : "";
                final List<Float> emb = embeddings != null && i < embeddings.size() ? embeddings.get(i) : List.of();
                result.add(new EntityData(
                        f(ids.get(i)),
                        ObjmtronSerializer.singleNoClip().read(doc),
                        rec0(),
                        lst(emb.stream().map(r -> (Obj) real(r)).toList())));
            }
            return result;
        }

        /**
         * Distance values for query results, index-aligned with {@link #entities()}.
         * Returns an empty list when the receiver was populated by a non-query
         * operation (get / getAll) where distances aren't present.
         */
        public List<Float> distances() {
            if (distances == null) return List.of();
            return distances;
        }

        public String toString() {
            return entities().toString();
        }
    }

    void setup(final fURI baseUrl, final Map<Uri,Obj> embeddingFunctions);

    // ---- Collection management ----

    /**
     * List all collections in the database.
     */
    List<CollectionData> listCollections() throws Exception;

    /**
     * Create a new collection with the given name, returning its metadata.
     */
    CollectionData createCollection(String name) throws Exception;

    /**
     * Get collection metadata by name.
     */
    CollectionData getCollection(String name) throws Exception;

    /*
     * Delete a collection by name.
     */
    void deleteCollection(String name) throws Exception;

    // ---- Document operations ----

    /**
     * Add documents to a collection (embeddings computed from document text).
     */
    void add(final fURI collectionId, final fURI collectionVID, final EntityData... entities) throws Exception;

    /**
     * Upsert documents into a collection.
     */
    void upsert(fURI collectionId, final fURI collectionVID, final EntityData... entities) throws Exception;

    /**
     * Get documents by ID.
     */
    GetResult get(fURI collectionId, List<String> ids) throws Exception;

    /**
     * Get all documents in a collection.
     */
    GetResult getAll(fURI collectionId) throws Exception;

    /**
     * Query the collection for the nearest neighbors of the given embedding vectors.
     *
     * @param collectionId    the collection to query
     * @param queryEmbeddings the query vectors, each as an {@code Lst} of {@code Real} values
     * @param nResults        maximum number of nearest neighbors per query vector
     * @return one GetResult per query vector, with distances populated
     */
    List<GetResult> query(fURI collectionId, List<Lst> queryEmbeddings, int nResults) throws Exception;

    /**
     * Delete documents by ID.
     */
    void delete(fURI collectionId, List<String> ids) throws Exception;

    /**
     * Count documents in a collection.
     */
    int count(fURI collectionId) throws Exception;

    /**
     * The embedding function used by this client.
     */
    Obj embeddingFunction(final fURI collection);

    public interface EmbedFunction {
        double[] embed(final byte[] object);

        default double[] embed(final String string) {
            return this.embed(string.getBytes());
        }

        default Lst embedToLst(final Obj obj) {
            final double[] embedding = this.embed(ObjmtronSerializer.singleNoClip().write(obj).getBytes());
            final List<Real> reals = new ArrayList<>(embedding.length);
            for (int i = 0; i < embedding.length; i++) {
                reals.add(real(embedding[i]));
            }
            return lst((List) reals);
        }
    }

    static class Helper {
        public static VectorDBClient loadService(final fURI clientURI) {
            final Optional<VectorDBClient> client = ServiceLoader.load(VectorDBClient.class)
                    .stream()
                    .filter(p -> p.type().getName().equals(clientURI.toString()))
                    .map(ServiceLoader.Provider::get)
                    .findFirst();
            if (client.isEmpty())
                throw MTronException.of("unable to locate vectordb client: %s", clientURI);
            return client.get();
        }
    }
}
