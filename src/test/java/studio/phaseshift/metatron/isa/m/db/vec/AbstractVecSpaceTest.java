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

package studio.phaseshift.metatron.isa.m.db.vec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractDataPathTest;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.db.vec.space.VectorDBClient;
import studio.phaseshift.metatron.isa.m.db.vec.space.vecSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.db.vec.vecInstSet.VEC_ISA_TID;

/**
 * Abstract base test suite for {@link vecSpace}.
 * <p>
 * Tests follow the {@code @ParameterizedTest + @CsvSource} convention
 * using mtron expressions and
 * {@link AbstractMetatronTest#checkCodeParseApply}.
 * Seed data is declared via {@link TestData @TestData} annotations.
 * <p>
 * ChromaDB is a blob store with associated vectors.  It does not support
 * nested records, type-preserving round-trips beyond scalar types,
 * or sequential partial updates.  Inherited tests that rely on those
 * semantics are {@code @Disabled} with explanatory notes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractVecSpaceTest extends AbstractDataPathTest {

    protected static final fURI SPACE_VID = f("/memory/space/vctr/test");
    protected static final String COLL = "test";
    protected static VectorDBClient staticClient;

    public AbstractVecSpaceTest() {
        super(f("vctr:" + COLL + "/r"), spaceSupplier());
    }

    // ---- mtron $$ substitution for inherited parameterized tests ----

    @Override
    public fURI getTestDataUriPrefix() {
        return f("vctr:" + COLL + "/");
    }

    @Override
    public String make(final String expression, final java.lang.reflect.Method testMethod) {
        if (expression.contains("$$")) {
            return expression.replace("$$", "vctr:" + COLL);
        }
        return super.make(expression, testMethod);
    }

    private static Supplier<Space> spaceSupplier() {
        return () -> {
            if (staticClient == null)
                throw new IllegalStateException("staticClient not initialized.");
            return vecSpace.of(staticClient,
                    rec(
                            uri(PATTERN), uri("vctr:#"),
                            uri(HOST), uri("http://chromadb:8000/api/v2"),
                            uri(QPROC), lst(QCollection.embedQ()),
                            uri(DRIVER), uri("studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client"),
                            uri(ROUTE), rec(uri("vctr:"), uri("")),
                            uri(CONFIG), rec(uri(EMBED), rec(uri("#"), lst(real(0.1), real(2.3))))
                    ).jvm(),
                    SPACE_VID
            );
        };
    }

    // =========================================================================
    //  Lifecycle
    // =========================================================================

    @BeforeAll
    public static void setupInstSet() throws Exception {
        InstSet.importInstSet(VEC_ISA_TID);
    }

    @BeforeEach
    public void clearDatabase() {
        try {
            for (final VectorDBClient.CollectionData c : staticClient.listCollections()) {
                staticClient.deleteCollection(c.name());
            }
        } catch (final Exception e) {
            System.err.println("ChromaDB cleanup warning: " + e.getMessage());
        }
    }

    /*@BeforeEach
    public void seedData() {
        final Space s = this.space;
        s.write(f("vctr:" + COLL + "/a"), jnt(345));
        s.write(f("vctr:" + COLL + "/b"), str("hello"));
        s.write(f("vctr:" + COLL + "/c"), real(3.14));
        s.write(f("vctr:" + COLL + "/rec1"), rec(
                uri("name"), str("Alice"),
                uri("age"), jnt(30)));
    }*/

    // =========================================================================
    //  AbstractDataPathTest — collection→Type contract
    // =========================================================================

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*<vctr:+>               % collection",
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        super.testDataPathSegmentTypes(code, segmentType);
    }

    // =========================================================================
    //  Core vecSpace tests — mInstSetTest style
    // =========================================================================

    @ParameterizedTest
    @TestData({"<vctr:test/a> -> 345",
            "<vctr:test/b> -> \"hello\"",
            "<vctr:test/c> -> 3.1400",
            "<vctr:test/rec1> -> [name=>\"Alice\",age=>30]"})
    @CsvSource(value = {
            // scalar read-back (type preserved via ObjmtronSerializer)
            "*<vctr:test/a>                             % 345",
            "*<vctr:test/b>                             % \"hello\"",
            "*<vctr:test/c>                             % 3.1400",
            // rec read-back
            "*<vctr:test/rec1>                          % [name=>\"Alice\",age=>30]",
            // rec field navigation
            "*<vctr:test/rec1/name>                     % \"Alice\"",
            "*<vctr:test/rec1/age>                      % 30",
            // wildcard listing
            "*<vctr:test/+>.count()                     % 4",
            // embedding virtual field (384-d from TestEmbeddingFunction)
            "*<vctr:test/a?embedq>.>-.>-.count()          % 2",
            // non-existent
            "*<vctr:test/noSuch>                        % noobj",
            "*<vctr:nosuch/x>                           % noobj",
    }, delimiter = '%')
    public void testVecRead(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData({"<vctr:test/given> -> [obj=>\"spark\",embed=>[0.25,0.75]]",
            "<vctr:test/noted> -> [obj=>\"coil\",embed=>[0.5,0.25],meta=>[inventor=>\"nikola\"]]",
            "<vctr:test/made> -> \"arc\""})
    @CsvSource(value = {
            // a write that carries its embedding stores its obj with that embedding, not the collection's
            "*<vctr:test/given>                     % \"spark\"",
            "*<vctr:test/given?embedq>              % [0.25,0.75]",
            // ... and a write that carries metadata is stored too (a vecSpace read answers no metadata:
            // testUpsertKeepsMetadata reads it back through the client)
            "*<vctr:test/noted>                     % \"coil\"",
            "*<vctr:test/noted?embedq>              % [0.5,0.25]",
            // a plain write is embedded by the collection's embedding function
            "*<vctr:test/made>                      % \"arc\"",
            "*<vctr:test/made?embedq>.>-.>-.count() % 2",
    }, delimiter = '%')
    public void testVecEmbedding(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData({"<vctr:test/m/0/0> -> \"ohm\"", "<vctr:test/m/1/0> -> \"volt\"",
            "<vctr:test/s> -> \"watt\"", "<vctr:test/s/a> -> \"amp\"", "<vctr:test/s/a> -> noobj",
            "<vctr:test/t> -> \"watt\"", "<vctr:test/t/a> -> \"amp\"", "<vctr:test/t> -> noobj",
            "<vctr:test/s/e> -> [obj=>\"joule\",embed=>[0.5,0.25]]"})
    @CsvSource(value = {
            // a collection names a document by its path below the collection: keys that end alike are documents apart
            "*<vctr:test/m/0/0>           % \"ohm\"",
            "*<vctr:test/m/1/0>           % \"volt\"",
            "*<vctr:test/m/+/0>.count()   % 2",
            // a delete removes the document its address names, never the one at its entry: s/a went and s stays
            "*<vctr:test/s>               % \"watt\"",
            "*<vctr:test/s/a>             % noobj",
            // ... t went and t/a stays, and t reads as its subtree
            "*<vctr:test/t/a>             % \"amp\"",
            "*<vctr:test/t>               % [a=>\"amp\"]",
            // ?embedq reads a document below an entry by its own name
            "*<vctr:test/s/e?embedq>      % [0.5,0.25]",
    }, delimiter = '%')
    public void testVecDocumentsNamedByPath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData({"<vctr:test/m/0/0> -> \"ohm\"", "<vctr:test/m/1/0> -> \"volt\"", "<vctr:test/m/+/0> -> \"amp\"",
            "<vctr:test/s> -> \"watt\"", "<vctr:test/s/a> -> \"joule\"", "<vctr:test/s/b/c> -> \"farad\"",
            "<vctr:test/k> -> \"kelvin\"", "<vctr:test/s/#> -> noobj"})
    @CsvSource(value = {
            // a pattern write writes every document it matches
            "*<vctr:test/m/0/0>       % \"amp\"",
            "*<vctr:test/m/1/0>       % \"amp\"",
            // a noobj written to s/# deletes every document it matches: s itself (# matches no segment too) and all below
            "*<vctr:test/s/a>         % noobj",
            "*<vctr:test/s/b/c>       % noobj",
            "*<vctr:test/s>           % noobj",
            "*<vctr:test/k>           % \"kelvin\"",
            // a document needs a name below its collection: a write at the collection itself is refused
            "<vctr:test> -> \"arc\"    % <ERROR>",
    }, delimiter = '%')
    public void testVecPatternWrites(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    // =========================================================================
    //  Inherited tests — DISABLED (ChromaDB blob-store semantics)
    // =========================================================================

    @Override
    @Disabled("ChromaDB is a blob store — no partial-field updates")
    public void testMonoReadWrite(String w, String r, String e) {
        super.testMonoReadWrite(w, r, e);
    }

    @Override
    @Disabled("ChromaDB has no implicit parent container")
    public void testMonoRootlessReadWrites() {
    }

    @Override
    @Disabled("ChromaDB upserts replace entire documents")
    public void testMonoUpdate() {
    }

  /*  @Override
    @Disabled("ChromaDB flattens metadata — no nested record round-trip")
    public void testNestedRecords(int depth) {
    }*/

  /*  @Override
    @Disabled("ChromaDB documents are text blobs — no type-change tracking")
    public void testTypeChanges(String d, Obj a, Obj b) {
    }*/

    @Override
    @Disabled("ChromaDB has flat metadata — no multi-field update semantics")
    public void testMultiFieldUpdates(int fieldCount) {
    }

    @Override
    @Disabled("ChromaDB upserts are idempotent — no sequential counter")
    public void testSequentialUpdates(int iterations) {
    }

    @Override
    @Disabled("ChromaDB has no in-place poly mutation")
    public void testPolyReadWrite(String w, String m, String r, String e) {
    }

    @Override
    @Disabled("ChromaDB is flat — no depth-based access")
    public void testMonoDepth(String l, String e) {
    }

    @Override
    @Disabled("ChromaDB is a blob store — no sequential CRUD tracking")
    public void testBasicCRUD(String d, String k, String v) {
    }

    // =========================================================================
    //  VectorDBClient.query() tests
    // =========================================================================

    @Test
    public void testQueryNearestNeighbors() throws Exception {
        final String collName = "querytest";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);

        // Write 5 documents — all get the same constant embedding [0.1, 0.2]
        staticClient.upsert(coll.id(), f(collName),
                new VectorDBClient.EntityData(f("a"), str("alpha"), rec0(), null),
                new VectorDBClient.EntityData(f("b"), str("beta"), rec0(), null),
                new VectorDBClient.EntityData(f("c"), str("gamma"), rec0(), null),
                new VectorDBClient.EntityData(f("d"), str("delta"), rec0(), null),
                new VectorDBClient.EntityData(f("e"), str("epsilon"), rec0(), null));

        // Bulk query with a single vector
        final Lst queryVec = lst(real(0.1), real(0.2));
        final List<VectorDBClient.GetResult> results = staticClient.query(coll.id(), List.of(queryVec), 3);
        assertEquals(1, results.size(), "one result per query vector");
        final VectorDBClient.GetResult result = results.get(0);

        // nResults should cap return count
        assertEquals(3, result.entities().size(), "nResults should cap returned entities");
        assertEquals(3, result.distances().size(), "distances size should match entities size");

        // All docs share the same embedding → all distances should be ~0
        for (int i = 0; i < result.distances().size(); i++) {
            final float d = result.distances().get(i);
            assertTrue(d >= 0.0f && d < 0.001f,
                    "distance should be near zero for identical vectors, got " + d);
        }

        // Returned ids should be a subset of the written docs
        final Set<String> expectedIds = Set.of("a", "b", "c", "d", "e");
        for (final VectorDBClient.EntityData entity : result.entities()) {
            assertTrue(expectedIds.contains(entity.id().name()),
                    "returned id should be one of the written docs: " + entity.id());
        }
    }

    @Test
    public void testQueryRespectsNResults() throws Exception {
        final String collName = "querylimit";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);

        // Write 10 documents
        for (int i = 0; i < 10; i++) {
            staticClient.upsert(coll.id(), f(collName),
                    new VectorDBClient.EntityData(f(String.valueOf(i)), str("doc" + i), rec0(), null));
        }

        final Lst queryVec = lst(real(0.1), real(0.2));

        // Ask for fewer than exist
        assertEquals(1, staticClient.query(coll.id(), List.of(queryVec), 1).get(0).entities().size(),
                "nResults=1 should return exactly 1");
        assertEquals(5, staticClient.query(coll.id(), List.of(queryVec), 5).get(0).entities().size(),
                "nResults=5 should return exactly 5");

        // Ask for more than exist — should return all available
        assertEquals(10, staticClient.query(coll.id(), List.of(queryVec), 20).get(0).entities().size(),
                "nResults > count should return all available documents");
    }

    @Test
    public void testUpsertKeepsEachEntitysEmbedding() throws Exception {
        final String collName = "keeps";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);

        // one batch: an entity that carries an embedding, and one that carries none
        staticClient.upsert(coll.id(), f(collName),
                new VectorDBClient.EntityData(f("given"), str("spark"), rec0(), lst(real(0.25), real(0.75))),
                new VectorDBClient.EntityData(f("made"), str("arc"), rec0(), null));

        final List<VectorDBClient.EntityData> read = staticClient.get(coll.id(), List.of("given", "made")).entities();
        final VectorDBClient.EntityData given = read.stream().filter(e -> e.id().equals(f("given"))).findFirst().orElseThrow();
        final VectorDBClient.EntityData made = read.stream().filter(e -> e.id().equals(f("made"))).findFirst().orElseThrow();

        // an entity's own embedding is the one stored
        AbstractMetatronTest.checkEquality(LOG, lst(real(0.25), real(0.75)), given.embedding(), true);
        // the embedding function ([0.1,0.2], stored as 32-bit floats) embeds the entity that carries none
        assertEquals(0.1, made.embedding().lstValue().get(0).asReal().realValue(), 1e-6);
        assertEquals(0.2, made.embedding().lstValue().get(1).asReal().realValue(), 1e-6);
    }

    @Test
    public void testUpsertKeepsMetadata() throws Exception {
        final String collName = "notes";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);

        staticClient.upsert(coll.id(), f(collName),
                new VectorDBClient.EntityData(f("noted"), str("coil"), rec(uri("inventor"), str("nikola"), uri("year"), str("1891")), null),
                new VectorDBClient.EntityData(f("plain"), str("arc"), rec0(), null));

        final List<VectorDBClient.EntityData> read = staticClient.get(coll.id(), List.of("noted", "plain")).entities();
        final VectorDBClient.EntityData noted = read.stream().filter(e -> e.id().equals(f("noted"))).findFirst().orElseThrow();
        final VectorDBClient.EntityData plain = read.stream().filter(e -> e.id().equals(f("plain"))).findFirst().orElseThrow();

        AbstractMetatronTest.checkEquality(LOG, str("nikola"), noted.metadata().at(uri("inventor")), true);
        AbstractMetatronTest.checkEquality(LOG, str("1891"), noted.metadata().at(uri("year")), true);
        // an entity written without metadata reads none back
        AbstractMetatronTest.checkEquality(LOG, rec0(), plain.metadata(), true);
    }

    @Test
    public void testCountCountsTheCollectionsDocuments() throws Exception {
        final String collName = "census";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);
        assertEquals(0, staticClient.count(coll.id()), "a new collection holds no document");

        staticClient.upsert(coll.id(), f(collName),
                new VectorDBClient.EntityData(f("volta"), str("pile"), rec0(), null),
                new VectorDBClient.EntityData(f("faraday"), str("cage"), rec0(), null),
                new VectorDBClient.EntityData(f("ampere"), str("law"), rec0(), null));
        assertEquals(3, staticClient.count(coll.id()));

        // an upsert of a stored id replaces its document, and a delete removes one
        staticClient.upsert(coll.id(), f(collName), new VectorDBClient.EntityData(f("volta"), str("battery"), rec0(), null));
        staticClient.delete(coll.id(), List.of("ampere"));
        assertEquals(2, staticClient.count(coll.id()));

        // a collection the server does not hold is an error the server names, never a number misread from its answer
        assertThrows(MTronException.class, () -> staticClient.count(f("00000000-0000-0000-0000-000000000000")));
    }

    @Test
    public void testGetResultDistancesEmptyForNonQuery() throws Exception {
        final String collName = "nonquery";
        staticClient.createCollection(collName);
        final VectorDBClient.CollectionData coll = staticClient.getCollection(collName);

        staticClient.upsert(coll.id(), f(collName),
                new VectorDBClient.EntityData(f("x"), str("test"), rec0(), null));

        // get() should NOT populate distances
        final VectorDBClient.GetResult getResult = staticClient.get(coll.id(), List.of("x"));
        assertTrue(getResult.distances().isEmpty(),
                "distances should be empty for get() results");

        // getAll() should NOT populate distances
        final VectorDBClient.GetResult allResult = staticClient.getAll(coll.id());
        assertTrue(allResult.distances().isEmpty(),
                "distances should be empty for getAll() results");
    }

}
