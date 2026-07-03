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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * ChromaDB v2 REST API client implementing {@link VectorDBClient}.
 * <p>
 * Targets ChromaDB &ge; 1.0 with the v2 API at
 * {@code /api/v2/tenants/default_tenant/databases/default_database/collections}.
 * Discovered via {@code META-INF/services}.
 */
public class ChromaV2Client implements VectorDBClient {

    private static final Gson GSON = new Gson();
    private static final Type LIST_MAP_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private HttpClient http;
    private fURI baseUrl;
    private fURI collectionsPath;
    private Rec embeddingFunction;

    //  Map<fURI, UUID> collectionNameMapping = new LinkedHashMap<>();

    public ChromaV2Client() {
        // service loader provider requirement
    }

    public ChromaV2Client(final fURI baseUrl, final Map<Uri, Obj> embeddingFunctions) {
        this();
        this.setup(baseUrl, embeddingFunctions);
    }

    @Override
    public void setup(final fURI baseURL, final Map<Uri, Obj> embeddingFunctions) {
        this.http = HttpClient.newHttpClient();
        this.baseUrl = baseURL.asBranch();
        this.collectionsPath = f("tenants/default_tenant/databases/default_database/collections");
        this.embeddingFunction = rec((Map) embeddingFunctions);
    }


    @Override
    public Obj embeddingFunction(final fURI collection) {
        final Obj result = this.embeddingFunction.at(f("#")); // TODO: we need a reverse map for the pattern match
        if (result.isNoObj())
            throw MTronException.of("no embedding function for collection %s\navailable embeddings: %s", collection, this.embeddingFunction);
        return result;
    }

    // ---- Collection management ----

    @Override
    public List<CollectionData> listCollections() throws Exception {
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.extend(collectionsPath).toString())).GET().build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("listCollections: " + resp.body());
        final List<Map<String, Object>> raw = GSON.fromJson(resp.body(), LIST_MAP_TYPE);
        return raw.stream()
                .map(m -> new CollectionData(
                        m.get("name") != null ? m.get("name").toString() : null,
                        m.get("id") != null ? f(m.get("id").toString()) : null))
                .toList();
    }

    @Override
    public CollectionData createCollection(final String name) throws Exception {
        final String body = GSON.toJson(mutableMap("name", name));
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.extend(collectionsPath).toString()))
                .header("Content-Type", MIME.MIMEType.APPLICATION_JSON.value)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception(resp.body());
        final Map<String, Object> m = GSON.fromJson(resp.body(), Map.class);
        return new CollectionData(
                m.get("name") != null ? m.get("name").toString() : name,
                m.get("id") != null ? f(m.get("id").toString()) : null);
    }

    @Override
    public CollectionData getCollection(final String name) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.extend(collectionsPath).extend(name).toString())).GET().build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception(resp.body());
        final Map<String, Object> m = GSON.fromJson(resp.body(), Map.class);
        return new CollectionData(
                m.get("name") != null ? m.get("name").toString() : name,
                m.get("id") != null ? f(m.get("id").toString()) : null);
    }

    @Override
    public void deleteCollection(final String name) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.extend(collectionsPath).extend(name).toString())).DELETE().build();
        http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    // ---- Document operations ----

    private fURI collPath(final fURI collectionId) {
        return collectionsPath.extend(collectionId);
    }

    private Map<String, Object> createBody(final EntityData[] entities, final Obj embeddingFunction) {
        final List<List<Object>> aggregate = new ArrayList<>();
        aggregate.add(new ArrayList<>()); // ids
        aggregate.add(new ArrayList<>()); // docs
        aggregate.add(new ArrayList<>()); // metadata
        aggregate.add(new ArrayList<>()); // embeddings
        Arrays.stream(entities).forEach(entity -> {
            final List<Double> embedding = embeddingFunction.apply(entity.obj()).lstValue().stream().map(r -> r.asReal().realValue()).toList();
            aggregate.get(0).add(entity.id().toString());
            aggregate.get(1).add(ObjmtronSerializer.singleNoClip().write(entity.obj()));
            aggregate.get(2).add(entity.metadata().elements().map(r -> new AbstractMap.SimpleEntry<>(Str.Helper.cleanString(r.first()), Str.Helper.cleanString(r.second()))).collect(Collectors.toMap(a -> a, b -> b)));
            aggregate.get(3).add(embedding);
        });
        final Map<String, Object> body = Map.of(
                "ids", aggregate.get(0),
                "documents", aggregate.get(1),
                "metadatas", aggregate.get(2),
                "embeddings", aggregate.get(3));
        return body;
    }

    @Override
    public void add(final fURI collectionId, final fURI collectionVID, final EntityData[] entities) throws Exception {
        post(collPath(collectionId).extend("add").toString(), createBody(entities, this.embeddingFunction(collectionVID)));
    }

    @Override
    public void upsert(final fURI collectionId, final fURI collectionVID, final EntityData... entities) throws Exception {
        post(collPath(collectionId).extend("upsert").toString(), createBody(entities, this.embeddingFunction(collectionVID)));
    }

    @Override
    public VectorDBClient.GetResult get(final fURI collectionId, final List<String> ids) throws Exception {
        final Map<String, Object> body = Map.of(
                "ids", ids, "include", List.of("embeddings", "documents", "metadatas"));
        final String json = post(collPath(collectionId).extend("get").toString(), body);
        return GSON.fromJson(json, VectorDBClient.GetResult.class);
    }

    @Override
    public VectorDBClient.GetResult getAll(final fURI collectionId) throws Exception {
        final Map<String, Object> body = Map.of(
                "include", List.of("embeddings", "documents", "metadatas"));
        final String json = post(collPath(collectionId).extend("get").toString(), body);
        return GSON.fromJson(json, VectorDBClient.GetResult.class);
    }

    @Override
    public void delete(final fURI collectionId, final List<String> ids) throws Exception {
        post(collPath(collectionId).extend("delete").toString(), Map.of("ids", ids));
    }

    @Override
    public int count(final fURI collectionId) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(collPath(collectionId).extend("count").toString())).GET().build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return Integer.parseInt(resp.body().trim());
    }

    // ---- Internal ----

    private String post(final String path, final Object body) throws Exception {
        final String json = GSON.toJson(body);
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.extend(path).toString()))
                .header("Content-Type", MIME.MIMEType.APPLICATION_JSON.value)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception(path + ": " + resp.body());
        return resp.body();
    }
}
