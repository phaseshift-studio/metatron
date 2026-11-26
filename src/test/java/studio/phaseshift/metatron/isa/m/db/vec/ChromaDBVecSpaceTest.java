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

import com.github.dockerjava.api.model.Ulimit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client;
import studio.phaseshift.metatron.isa.m.db.vec.space.VectorDBClient;
import studio.phaseshift.metatron.isa.m.db.vec.space.vecSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HTTP;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.ai.llm.llmInstSet.LLM_MODEL_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Test suite for {@link vecSpace} backed by a TestContainers-managed
 * ChromaDB 1.5.3 instance (v2 API).
 * <p>
 * Uses {@link GenericContainer} directly (following the
 * {@code MySQLDatabaseConfig} pattern).  The 0.1.7 Java client is
 * incompatible with ChromaDB &ge; 1.0 — this test uses
 * {@link ChromaV2Client} instead.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipRegexTest({
        @SkipRegexTest.Skip(method = "testRshiftUriGraphSpine")
})
public class ChromaDBVecSpaceTest extends AbstractVecSpaceTest {

    private static final int CHROMADB_PORT = 8000;
    private static GenericContainer<?> chromaContainer;
    private static fURI endpoint;

    @BeforeAll
    public static void importTheModelInstructions() {
        InstSet.importInstSet(f("/m/ai/llm"));
    }

    @BeforeAll
    public static void startContainer() {
        chromaContainer = new GenericContainer<>(
                DockerImageName.parse("chromadb/chroma:1.5.3"))
                .withExposedPorts(CHROMADB_PORT)
                // the server keeps four files open for every collection it has indexed (an HNSW index's header, data,
                // length and link lists), and a deleted collection keeps its four: clearDatabase drops the collection a
                // case wrote, so every case that writes costs the server four descriptors. Docker's default soft limit of
                // 1024 runs out at about 250 such cases, and then the server's SQLite answers "(code: 14) unable to open
                // database file"
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withUlimits(List.of(new Ulimit("nofile", 65536L, 65536L))))
                .waitingFor(Wait.forHttp("/api/v2/heartbeat")
                        .forPort(CHROMADB_PORT))
                .withStartupTimeout(Duration.ofMinutes(3));

        chromaContainer.start();

        endpoint = f().scheme(HTTP).host(chromaContainer.getHost()).port(chromaContainer.getMappedPort(CHROMADB_PORT)).extend("/api/v2");
        staticClient = new ChromaV2Client(endpoint, mutableMap(uri("#"), lst(real(0.1), real(0.2))));

        // Reset any leftover data from previous test runs
        try {
            for (final VectorDBClient.CollectionData c : staticClient.listCollections()) {
                staticClient.deleteCollection(c.name());
            }
        } catch (final Exception e) {
            System.err.println("chromadb startup cleanup warning: " + e.getMessage());
        }

        System.out.println("chromaDB 1.5.3 container started at: " + endpoint);
    }

    @Test
    public void testAnEntityThatCarriesItsEmbeddingNeedsNoEmbeddingFunction() throws Exception {
        // a client set up with no embedding function: a vecspace whose config names none
        final VectorDBClient bare = new ChromaV2Client(endpoint, new LinkedHashMap<>());
        final VectorDBClient.CollectionData coll = bare.createCollection("bare");
        bare.upsert(coll.id(), f("bare"), new VectorDBClient.EntityData(f("given"), str("spark"), rec0(), lst(real(0.25), real(0.75))));
        AbstractMetatronTest.checkEquality(LOG, lst(real(0.25), real(0.75)),
                bare.get(coll.id(), List.of("given")).entities().getFirst().embedding(), true);
        // an entity that carries none has nothing to embed it
        assertThrows(MTronException.class, () -> bare.upsert(coll.id(), f("bare"),
                new VectorDBClient.EntityData(f("made"), str("arc"), rec0(), null)));
    }

    /**
     * the embedding function a vecspace's config names is an mtron instruction run on each document as it is written. The
     * space is built as boot/space/vec/chromadb.mtron builds its own: a split (the module splits the container it found,
     * this test the endpoint), with the function in a plain config rec, so building the space keeps the function and does
     * not run it. A body that answers from the document shows it runs per document
     */
    @Test
    public void testAConfigEmbeddingFunctionEmbedsEachDocumentAsItIsWritten() {
        final String space = "<" + endpoint + ">.-<[pattern => fnembed:#, host => <" + endpoint + ">, " +
                "driver => <studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client>, q => [embedq::[=>]], " +
                "route => -< [fnembed: => <>], config => [embed => [# => inst?lst[real]<=#{?}(doc=>#::T){ *doc>>n }]]]" +
                "@</memory/space/vec/fnembed>.as(vecspace::T)";
        AbstractMetatronTest.checkEquality(LOG, uri("fnembed:#"), ObjmtronSerializer.parse(space).apply().asRec().at(uri(PATTERN)), true);
        ObjmtronSerializer.parse("<fnembed:pile/volta> -> [n=>[3.0,0.25]]").apply();
        ObjmtronSerializer.parse("<fnembed:pile/faraday> -> [n=>[5.0,0.25]]").apply();
        AbstractMetatronTest.checkCodeParseApply(LOG, "*<fnembed:pile/volta?embedq>", "[3.0,0.25]");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*<fnembed:pile/faraday?embedq>", "[5.0,0.25]");
    }

    /**
     * boot/space/vec/chromadb.mtron itself, read through an fs space and applied with its parameters' names as
     * boot/spaces.boot.mtron calls it, a memSpace standing in for the docker: space it brings its container up through, and
     * a localhost stand-in for the ollama server that boot's model spec names (only the host is moved). The space mounts
     * with no model; a document that brings its embedding needs none; with no spec the function answers no embedding; with
     * the boot's spec the first plain write builds the model and keeps it, and later writes, a pushed machine's among
     * them, build nothing more; a build that fails fails its write with its own error and leaves the spec
     */
    @Test
    public void testTheChromadbModuleEmbedsWithTheModelItBuildsOnceFromTheBootsSpec() throws IOException {
        final String boot = Files.readString(Path.of("boot/spaces.boot.mtron"));
        final Matcher spec = Pattern.compile("\\bollama\\s*=>\\s*(\\[[^\\]]*\\])").matcher(boot);
        assertTrue(spec.find(), "boot/spaces.boot.mtron names its embedding model spec");
        final Matcher address = Pattern.compile("<(/[^>]+)>\\s*->\\s*\\*boot/args/ollama;").matcher(boot);
        assertTrue(address.find(), "boot/spaces.boot.mtron writes its spec to an address");
        assertTrue(Files.readString(Path.of("boot/space/vec/chromadb.mtron")).contains("*<" + address.group(1) + ">"),
                "the module reads the address the boot writes its spec to: " + address.group(1));
        final AtomicInteger builds = new AtomicInteger();
        final AtomicInteger embeds = new AtomicInteger();
        final HttpServer ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/tags", exchange -> answer(exchange, builds, OLLAMA_TAGS));
        ollama.createContext("/api/show", exchange -> answer(exchange, new AtomicInteger(), OLLAMA_SHOW));
        ollama.createContext("/api/embed", exchange -> answer(exchange, embeds, OLLAMA_EMBED));
        ollama.start();
        try {
            memSpace.of(f("docker:#"), f("/memory/space/docker"));
            ObjmtronSerializer.parse("<docker:container/metatron_chroma> -> [state=>'running']").apply();
            ObjmtronSerializer.parse("fsspace::[pattern => fs:#, q => [mimeq::[=>]], route => [fs: => <.>]]@~/memory/space/fs/root").apply();
            final URI server = URI.create(endpoint.toString());
            final Obj space = ObjmtronSerializer.parse("<fs:boot/space/vec/chromadb.mtron>(saddr => <" + server.getHost() + ">, sport => " +
                    server.getPort() + ", simg => 'chromadb/chroma:1.5.3')").apply();
            AbstractMetatronTest.checkEquality(LOG, uri("embed:#"), space.asRec().at(uri(PATTERN)), true);
            // a document that brings its embedding needs no model: the server is not asked
            ObjmtronSerializer.parse("<embed:notes/given> -> [obj=>\"spark\",embed=>[0.25,0.75]]").apply();
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/given?embedq>", "[0.25,0.75]");
            // with no spec at the address there is no model, so the function answers nothing, which its lst[real] range refuses
            final Obj unspecified = ObjmtronSerializer.parse("<embed:notes/blank> -> [note=>\"no model\"]").apply();
            assertTrue(unspecified.isFail() && unspecified.toString().contains("does not match inst range"), "no model, no embedding: " + unspecified);
            // the boot's spec, its host moved to the stand-in: the first plain write builds the model once and keeps it
            ObjmtronSerializer.parse("<" + address.group(1) + "> -> " + spec.group(1)).apply();
            ObjmtronSerializer.parse("<" + address.group(1) + "/host> -> <http://127.0.0.1:" + ollama.getAddress().getPort() + ">").apply();
            assertFalse(ObjmtronSerializer.parse("<embed:notes/volta> -> [note=>\"pile\"]").apply().isFail(), "the first write embeds");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/volta?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, uri(LLM_MODEL_TID), uri(Machine.read(f(address.group(1))).tid()), true);
            // a second write builds nothing more
            assertFalse(ObjmtronSerializer.parse("<embed:notes/faraday> -> [note=>\"cage\"]").apply().isFail(), "the second write embeds");
            // nor does a write from a pushed machine, which reads the same model at the same address
            final Machine pushed = Machine.current().push(f("embedder"));
            try {
                assertFalse(ObjmtronSerializer.parse("<embed:notes/ampere> -> [note=>\"law\"]").apply().isFail(), "a pushed machine embeds");
            } finally {
                pushed.pop();
            }
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/ampere?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(builds.get()), true);
            AbstractMetatronTest.checkEquality(LOG, jnt(3), jnt(embeds.get()), true);
            // a spec on a closed port: the build fails its write with its own error, and the spec stays for the next write
            ObjmtronSerializer.parse("<" + address.group(1) + "> -> " + spec.group(1)).apply();
            ObjmtronSerializer.parse("<" + address.group(1) + "/host> -> <http://127.0.0.1:1>").apply();
            final Obj unreachable = ObjmtronSerializer.parse("<embed:notes/ohm> -> [note=>\"resistance\"]").apply();
            assertTrue(unreachable.isFail() && unreachable.toString().contains("Connect"), "the build's own error: " + unreachable);
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<" + address.group(1) + "/host>", "<http://127.0.0.1:1>");
        } finally {
            ObjmtronSerializer.parse("<" + address.group(1) + "> -> noobj").apply();
            ollama.stop(0);
        }
    }

    /**
     * an ollama answer: what a real server sends for its model list, its model card and an embedding of every input
     */
    private static void answer(final HttpExchange exchange, final AtomicInteger hits, final String json) throws IOException {
        hits.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static final String OLLAMA_DETAILS = "{\"format\":\"gguf\",\"family\":\"qwen3\",\"families\":[\"qwen3\"],\"parameter_size\":\"4.0B\",\"quantization_level\":\"Q4_K_M\"}";
    private static final String OLLAMA_TAGS = "{\"models\":[{\"name\":\"qwen3-embedding:4b\",\"model\":\"qwen3-embedding:4b\",\"modified_at\":\"2026-11-25T00:00:00Z\"," +
            "\"size\":2500000000,\"digest\":\"stand-in\",\"details\":" + OLLAMA_DETAILS + "}]}";
    private static final String OLLAMA_SHOW = "{\"license\":\"\",\"modelfile\":\"\",\"parameters\":\"\",\"template\":\"\",\"details\":" + OLLAMA_DETAILS + "," +
            "\"model_info\":{\"general.architecture\":\"qwen3\",\"qwen3.context_length\":40960},\"capabilities\":[\"embedding\"],\"modified_at\":\"2026-11-25T00:00:00Z\"}";
    private static final String OLLAMA_EMBED = "{\"model\":\"qwen3-embedding:4b\",\"embeddings\":[[0.5,0.25]],\"prompt_eval_count\":1}";

    @AfterAll
    public static void stopContainer() {
        staticClient = null;
        if (chromaContainer != null && chromaContainer.isRunning()) {
            chromaContainer.stop();
        }
    }
}
