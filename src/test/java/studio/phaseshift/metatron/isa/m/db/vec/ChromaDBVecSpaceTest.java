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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.HTTP;
import static studio.phaseshift.metatron.Tokens.LLM;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.ai.llm.llmInstSet.LLM_MODEL_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
        try {
            AbstractMetatronTest.checkEquality(LOG, uri("fnembed:#"), ObjmtronSerializer.parse(space).apply().asRec().at(uri(PATTERN)), true);
            AbstractMetatronTest.checkCodeParseApply(LOG, "<fnembed:pile/volta> -> [n=>[3.0,0.25]]", "[n=>[3.0,0.25]]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "<fnembed:pile/faraday> -> [n=>[5.0,0.25]]", "[n=>[5.0,0.25]]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<fnembed:pile/volta?embedq>", "[3.0,0.25]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<fnembed:pile/faraday?embedq>", "[5.0,0.25]");
        } finally {
            unmount(f("/memory/space/vec/fnembed"));
        }
    }

    /**
     * an embedding that is no lst of reals, an empty one included, fails its write and names its source: the function
     * that answered it (here one whose range admits any answer) or the write that carried it. A cast of it, or of an
     * element in the client, would fail the write with a class cast that names neither
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "\"flat\"   % [n=>3]                             % the embedding function of collection pile answered",
            "3          % [n=>3]                             % the embedding function of collection pile answered",
            "[\"flat\"] % [n=>3]                             % the embedding function of collection pile answered",
            "[1,2]      % [n=>3]                             % the embedding function of collection pile answered",
            "[,]        % [n=>3]                             % the embedding function of collection pile answered",
            "[0.5,0.25] % [obj=>\"spark\",embed=>[1,2]]      % the embedding written to",
            "[0.5,0.25] % [obj=>\"spark\",embed=>[\"flat\"]] % the embedding written to",
    })
    void testAnEmbeddingThatIsNoLstOfRealsFailsItsWriteNamingItsSource(final String answer, final String document, final String refusal) {
        try {
            ObjmtronSerializer.parse("<" + endpoint + ">.-<[pattern => flatembed:#, host => <" + endpoint + ">, " +
                    "driver => <studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client>, q => [embedq::[=>]], " +
                    "route => -< [flatembed: => <>], config => [embed => [# => inst?#{?}<=#{?}(doc=>#::T){ " + answer + " }]]]" +
                    "@</memory/space/vec/flatembed>.as(vecspace::T)").apply();
            final Obj written = ObjmtronSerializer.parse("<flatembed:pile/volta> -> " + document).apply();
            assertTrue(written.isFail() && written.toString().contains(refusal) && written.toString().contains("not an embedding"),
                    document + " under a function answering " + answer + " fails naming its source: " + written);
        } finally {
            unmount(f("/memory/space/vec/flatembed"));
        }
    }

    /**
     * boot/space/vec/chromadb.mtron itself, read through an fs space and applied with its parameters' names, in the order
     * boot/spaces.boot.mtron runs them: the boot's model spec first, only its host moved to a localhost stand-in for the
     * ollama server, then the module, a memSpace standing in for the docker: space it brings its container up through.
     * The mount builds no model; a document that brings its embedding needs none; the first plain write builds the model
     * and keeps it, and later writes, a pushed machine's among them, build nothing more, each sending its own document to
     * the spec's model; a build that fails fails its write with its own error and leaves the spec; with no spec the
     * function answers no embedding
     */
    @Test
    public void testTheChromadbModuleEmbedsWithTheModelItBuildsOnceFromTheBootsSpec() throws IOException {
        final String boot = mParser.stripComments(Files.readString(Path.of("boot/spaces.boot.mtron")));
        final Matcher spec = Pattern.compile("\\bollama\\s*=>\\s*(\\[[^\\]]*\\])").matcher(boot);
        assertTrue(spec.find(), "boot/spaces.boot.mtron names its embedding model spec");
        final Matcher address = Pattern.compile("<(/[^>]+)>\\s*->\\s*\\*boot/args/ollama;").matcher(boot);
        assertTrue(address.find(), "boot/spaces.boot.mtron writes its spec to an address");
        assertTrue(mParser.stripComments(Files.readString(Path.of("boot/space/vec/chromadb.mtron"))).contains("*<" + address.group(1) + ">"),
                "the module reads the address the boot writes its spec to: " + address.group(1));
        final fURI model = f(address.group(1));
        final String llm = ObjmtronSerializer.parse(spec.group(1)).apply().asRec().at(uri(LLM)).uriValue().toString();
        // a build asks for the model list once (/api/tags) and for each listed model's card twice (/api/show)
        final AtomicInteger builds = new AtomicInteger();
        final List<String> embedded = new CopyOnWriteArrayList<>();
        final HttpServer ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/tags", exchange -> answer(exchange, OLLAMA_TAGS, request -> builds.incrementAndGet()));
        ollama.createContext("/api/show", exchange -> answer(exchange, OLLAMA_SHOW, request -> {}));
        ollama.createContext("/api/embed", exchange -> answer(exchange, OLLAMA_EMBED, embedded::add));
        ollama.start();
        final fURI stand = f().scheme(HTTP).host(ollama.getAddress().getAddress().getHostAddress()).port(ollama.getAddress().getPort());
        try {
            ObjmtronSerializer.parse("<" + model + "> -> " + spec.group(1)).apply();
            Machine.write(model.extend(HOST), uri(stand));
            memSpace.of(f("docker:#"), f("/memory/space/docker"));
            ObjmtronSerializer.parse("<docker:container/metatron_chroma> -> [state=>'running']").apply();
            ObjmtronSerializer.parse("fsspace::[pattern => fs:#, q => [mimeq::[=>]], route => [fs: => <.>]]@/memory/space/fs/boot").apply();
            final Obj space = ObjmtronSerializer.parse("<fs:boot/space/vec/chromadb.mtron>(saddr => <" + endpoint.host() + ">, sport => " +
                    endpoint.port() + ", simg => 'chromadb/chroma:1.5.3')").apply();
            AbstractMetatronTest.checkEquality(LOG, uri("embed:#"), space.asRec().at(uri(PATTERN)), true);
            // the mount builds no model, and a document that brings its embedding needs none
            AbstractMetatronTest.checkEquality(LOG, jnt(0), jnt(builds.get()), true);
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/given> -> [obj=>\"spark\",embed=>[0.25,0.75]]", "[obj=>\"spark\",embed=>[0.25,0.75]]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/given?embedq>", "[0.25,0.75]");
            AbstractMetatronTest.checkEquality(LOG, jnt(0), jnt(builds.get()), true);
            // the first plain write builds the model and keeps it in place of the spec
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/volta> -> [note=>\"pile\"]", "[note=>\"pile\"]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/volta?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, uri(LLM_MODEL_TID), uri(Machine.read(model).tid()), true);
            // a second write builds nothing more, nor does a write from a pushed machine, which reads the same model at the same address
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/faraday> -> [note=>\"cage\"]", "[note=>\"cage\"]");
            final Machine pushed = Machine.current().push(f("embedder"));
            try {
                AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/ampere> -> [note=>\"law\"]", "[note=>\"law\"]");
            } finally {
                pushed.pop();
            }
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/ampere?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(builds.get()), true);
            // what each write sent the server: its own document, for the spec's model
            final List<String> notes = List.of("pile", "cage", "law");
            assertEquals(notes, embedded.stream().map(body -> notes.stream().filter(body::contains).findFirst().orElse(body)).toList(),
                    "each write embeds its own document: " + embedded);
            assertTrue(embedded.stream().allMatch(body -> body.contains("\"" + llm + "\"")), "each write asks for " + llm + ": " + embedded);
            // a spec on a closed port: the build fails its write with its own error, and the spec stays for the next write
            final fURI closed = f().scheme(HTTP).host(stand.host()).port(1);
            ObjmtronSerializer.parse("<" + model + "> -> " + spec.group(1)).apply();
            Machine.write(model.extend(HOST), uri(closed));
            final Obj unreachable = ObjmtronSerializer.parse("<embed:notes/ohm> -> [note=>\"resistance\"]").apply();
            assertTrue(unreachable.isFail() && unreachable.toString().contains("ConnectException") && !unreachable.toString().contains("not an embedding"),
                    "the build's own error, as itself: " + unreachable);
            AbstractMetatronTest.checkEquality(LOG, uri(closed), Machine.read(model.extend(HOST)), true);
            // with no spec there is no model, so the function answers nothing, which its lst[real] range refuses
            Machine.write(model, noobj());
            final Obj unspecified = ObjmtronSerializer.parse("<embed:notes/blank> -> [note=>\"no model\"]").apply();
            assertTrue(unspecified.isFail() && unspecified.toString().contains("does not match inst range") && !unspecified.toString().contains("not an embedding"),
                    "no model, no embedding, and the range's own error: " + unspecified);
        } finally {
            Machine.write(model, noobj());
            ollama.stop(0);
            unmount(f("/memory/space/vec/chroma"), f("/memory/space/fs/boot"), f("/memory/space/docker"));
        }
    }

    /**
     * an ollama answer, the same whatever the request asked: a model list, a model card, or one embedding (an embed
     * sends one document, so one is what it asks for). The request's body is handed over before the answer is sent, so
     * the write that waits on the answer sees it recorded
     */
    private static void answer(final HttpExchange exchange, final String json, final Consumer<String> request) throws IOException {
        request.accept(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /**
     * unmount the spaces a test mounted, so the class's other tests run without them
     */
    private static void unmount(final fURI... vids) {
        for (final fURI vid : vids) {
            final Obj space = mounted(vid.toString());
            Machine.current().memory().removeSpace(vid);
            CommonUtil.close(space);
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
