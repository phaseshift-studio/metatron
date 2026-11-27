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
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client;
import studio.phaseshift.metatron.isa.m.db.vec.space.VectorDBClient;
import studio.phaseshift.metatron.isa.m.db.vec.space.vecEmbedQ;
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
import static studio.phaseshift.metatron.Tokens.MODEL;
import static studio.phaseshift.metatron.Tokens.QPROC;
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
     * an embedding that is no lst of reals, an empty one or one real among others included, fails its write and names
     * its source: the function that answered it (here one whose range admits any answer), or the address a carried lst
     * was written to. Each row pins the whole message, the embedding with it. A carried embedding that is no lst is no
     * carried embedding at all: the document goes to the function, whose body answers one more than its embed, an
     * answer only the function gives. A cast of it, or of an element in the client, would fail the write with a class
     * cast that names neither
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', quoteCharacter = '~', value = {
            "\"flat\"            % [n=>3]                               % the embedding function of collection pile answered 'flat', not an embedding",
            "3                   % [n=>3]                               % the embedding function of collection pile answered 3, not an embedding",
            "[\"flat\"]          % [n=>3]                               % the embedding function of collection pile answered ['flat'], not an embedding",
            "[1,2]               % [n=>3]                               % the embedding function of collection pile answered [1,2], not an embedding",
            "[,]                 % [n=>3]                               % the embedding function of collection pile answered [,], not an embedding",
            "[0.5,true]          % [n=>3]                               % the embedding function of collection pile answered [0.5,true], not an embedding",
            "*doc>>embed.plus(1) % [obj=>\"spark\",embed=>3]            % the embedding function of collection pile answered 4, not an embedding",
            "[0.5,0.25]          % [obj=>\"spark\",embed=>[,]]          % the embedding written to flatembed:pile/volta is [,], not an embedding",
            "[0.5,0.25]          % [obj=>\"spark\",embed=>[1,2]]        % the embedding written to flatembed:pile/volta is [1,2], not an embedding",
            "[0.5,0.25]          % [obj=>\"spark\",embed=>[true,false]] % the embedding written to flatembed:pile/volta is [true,false], not an embedding",
    })
    void testAnEmbeddingThatIsNoLstOfRealsFailsItsWriteNamingItsSource(final String answer, final String document, final String refusal) {
        try {
            ObjmtronSerializer.parse("<" + endpoint + ">.-<[pattern => flatembed:#, host => <" + endpoint + ">, " +
                    "driver => <studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client>, q => [embedq::[=>]], " +
                    "route => -< [flatembed: => <>], config => [embed => [# => inst?#{?}<=#{?}(doc=>#::T){ " + answer + " }]]]" +
                    "@</memory/space/vec/flatembed>.as(vecspace::T)").apply();
            final Obj written = ObjmtronSerializer.parse("<flatembed:pile/volta> -> " + document).apply();
            assertTrue(written.isFail() && written.toString().contains(refusal),
                    document + " under a function answering " + answer + " fails naming its source: " + written);
        } finally {
            unmount(f("/memory/space/vec/flatembed"));
        }
    }

    /**
     * boot/space/vec/chromadb.mtron itself, read through an fs space and applied with its parameters' names, as
     * boot/spaces.boot.mtron runs it: first the boot's model, built from its spec (only the host moved, to a localhost
     * stand-in for the ollama server) at the address the module's embedq references, then the module, a memSpace standing
     * in for the docker: space it brings its container up through. The mount builds nothing; a document that brings its
     * embedding needs no model; plain writes, a pushed machine's among them, build nothing more, each sending its own
     * document to the model; reading the embedq's model gives the model at the address; a model written there takes the
     * place of the last with no remount; and with nothing there, a plain write fails naming the reference
     */
    @Test
    public void testTheChromadbModuleEmbedsWithTheModelItsEmbedqNames() throws IOException {
        final String boot = mParser.stripComments(Files.readString(Path.of("boot/spaces.boot.mtron")));
        final Matcher spec = Pattern.compile("\\bollama\\s*=>\\s*(\\[[^\\]]*\\])").matcher(boot);
        assertTrue(spec.find(), "boot/spaces.boot.mtron names its embedding model spec");
        final Matcher address = Pattern.compile("\\*boot/args/ollama\\.as\\(model::T\\)\\.to\\((/[^)]+)\\);").matcher(boot);
        assertTrue(address.find(), "boot/spaces.boot.mtron builds its model at an address");
        assertTrue(mParser.stripComments(Files.readString(Path.of("boot/space/vec/chromadb.mtron"))).contains("embedq::[model=>!*<" + address.group(1) + ">]"),
                "the module's embedq references the address the boot builds its model at: " + address.group(1));
        final String llm = ObjmtronSerializer.parse(spec.group(1)).apply().asRec().at(uri(LLM)).uriValue().toString();
        final AtomicInteger builds = new AtomicInteger();
        final List<String> embedded = new CopyOnWriteArrayList<>();
        final HttpServer ollama = ollama(builds, embedded);
        final AtomicInteger otherBuilds = new AtomicInteger();
        final HttpServer other = ollama(otherBuilds, new CopyOnWriteArrayList<>(), "{\"model\":\"qwen3-embedding:4b\",\"embeddings\":[[0.125,0.5]],\"prompt_eval_count\":1}");
        final fURI stand = f().scheme(HTTP).host(ollama.getAddress().getAddress().getHostAddress()).port(ollama.getAddress().getPort());
        final fURI location = f(address.group(1));
        try {
            // the boot's own statement, with the boot's spec at boot/args/ollama and its host moved: to a closed port, where
            // the build fails and writes nothing, then to the stand-in, where it builds the model where the embedq points
            final String build = address.group(0).replaceAll(";$", "");
            Machine.write(f("boot/args/ollama"), ObjmtronSerializer.parse(spec.group(1)).apply());
            Machine.write(f("boot/args/ollama").extend(HOST), uri(f().scheme(HTTP).host(stand.host()).port(1)));
            ObjmtronSerializer.parse(build).apply();
            assertTrue(Machine.read(location).isNoObj(), "a build that fails at boot writes nothing: " + Machine.read(location));
            Machine.write(f("boot/args/ollama").extend(HOST), uri(stand));
            ObjmtronSerializer.parse(build).apply();
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(builds.get()), true);
            AbstractMetatronTest.checkEquality(LOG, uri(LLM_MODEL_TID), uri(Machine.read(location).tid()), true);
            memSpace.of(f("docker:#"), f("/memory/space/docker"));
            ObjmtronSerializer.parse("<docker:container/metatron_chroma> -> [state=>'running']").apply();
            ObjmtronSerializer.parse("fsspace::[pattern => fs:#, q => [mimeq::[=>]], route => [fs: => <.>]]@/memory/space/fs/boot").apply();
            final Obj space = ObjmtronSerializer.parse("<fs:boot/space/vec/chromadb.mtron>(saddr => <" + endpoint.host() + ">, sport => " +
                    endpoint.port() + ", simg => 'chromadb/chroma:1.5.3')").apply();
            AbstractMetatronTest.checkEquality(LOG, uri("embed:#"), space.asRec().at(uri(PATTERN)), true);
            // the mount builds nothing, and a document that brings its embedding needs no model
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(builds.get()), true);
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/given> -> [obj=>\"spark\",embed=>[0.25,0.75]]", "[obj=>\"spark\",embed=>[0.25,0.75]]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/given?embedq>", "[0.25,0.75]");
            // the embedq keeps the reference itself, unresolved
            assertTrue(embedq(f("/memory/space/vec/chroma")).atDirect(uri(MODEL)).isAuto(),
                    "the embedq keeps its reference: " + embedq(f("/memory/space/vec/chroma")).atDirect(uri(MODEL)));
            // a plain write embeds with the model at the address, which reading the embedq's model gives
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/volta> -> [note=>\"pile\"]", "[note=>\"pile\"]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/volta?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, uri(LLM_MODEL_TID), uri(embedq(f("/memory/space/vec/chroma")).at(uri(MODEL)).tid()), true);
            // later writes build nothing either, a write from a pushed machine among them
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
            // a document is embedded as it is: a str holding a template is not expanded, so its mtron never runs
            ObjmtronSerializer.parse("<embed:notes/template> -> \"a \\${'b'} c\"").apply();
            assertTrue(embedded.getLast().contains("${'b'}"), "the template's text is what is embedded: " + embedded.getLast());
            // a model written to the address takes the place of the last, with no remount: reading the embedq's model gives
            // it, a new write embeds with it, and what the last model embedded stays as it was
            final fURI otherStand = f().scheme(HTTP).host(stand.host()).port(other.getAddress().getPort());
            Machine.write(f("boot/args/ollama").extend(HOST), uri(otherStand));
            ObjmtronSerializer.parse(build).apply();
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(otherBuilds.get()), true);
            AbstractMetatronTest.checkEquality(LOG, uri(otherStand), embedq(f("/memory/space/vec/chroma")).at(uri(MODEL)).asRec().at(uri(HOST)), true);
            AbstractMetatronTest.checkCodeParseApply(LOG, "<embed:notes/kelvin> -> [note=>\"scale\"]", "[note=>\"scale\"]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/kelvin?embedq>", "[0.125,0.5]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<embed:notes/volta?embedq>", "[0.5,0.25]");
            // with nothing at the address, a plain write fails naming the reference
            Machine.write(location, noobj());
            final Obj unmodeled = ObjmtronSerializer.parse("<embed:notes/blank> -> [note=>\"no model\"]").apply();
            assertTrue(unmodeled.isFail() && unmodeled.toString().contains("reads no model") && unmodeled.toString().contains(location.toString()),
                    "no model at the address, which the failure names: " + unmodeled);
        } finally {
            Machine.write(location, noobj());
            Machine.write(f("boot/args/ollama"), noobj());
            ollama.stop(0);
            other.stop(0);
            unmount(f("/memory/space/vec/chroma"), f("/memory/space/fs/boot"), f("/memory/space/docker"));
        }
    }

    /**
     * the model a space's embedq references comes before its config's embedding function, and a reference that reads
     * nothing fails the write rather than fall back to the function. A spec where the model should be is built at every
     * write, and a build that fails fails the write as itself. A model that answers an empty embedding is refused under
     * the model's name. With neither a model nor a function, a write that brings no embedding fails naming the missing
     * function
     */
    @Test
    public void testAnEmbedqModelComesFirstAndAFailedBuildFailsItsWrite() throws IOException {
        final AtomicInteger builds = new AtomicInteger();
        final HttpServer ollama = ollama(builds, new CopyOnWriteArrayList<>());
        final HttpServer hollow = ollama(new AtomicInteger(), new CopyOnWriteArrayList<>(), "{\"model\":\"qwen3-embedding:4b\",\"embeddings\":[[]],\"prompt_eval_count\":1}");
        final fURI stand = f().scheme(HTTP).host(ollama.getAddress().getAddress().getHostAddress()).port(ollama.getAddress().getPort());
        final fURI closed = f().scheme(HTTP).host(stand.host()).port(1);
        final fURI hollowHost = f().scheme(HTTP).host(stand.host()).port(hollow.getAddress().getPort());
        final fURI hollowSpec = f("/sys/test/chroma/hollow");
        final fURI served = f("/sys/test/chroma/served");
        final fURI unserved = f("/sys/test/chroma/unserved");
        final fURI unbuilt = f("/sys/test/chroma/unbuilt");
        final String space = "<" + endpoint + ">.-<[pattern => %1$s:#, host => <" + endpoint + ">, " +
                "driver => <studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client>, q => [embedq::[%2$s]], " +
                "route => -< [%1$s: => <>]%3$s]@</memory/space/vec/%1$s>.as(vecspace::T)";
        try {
            ObjmtronSerializer.parse("[provider=>ollama,protocol=>ollama,host=><" + stand + ">,llm=><qwen3-embedding:4b>].as(model::T).to(" + served + ")").apply();
            ObjmtronSerializer.parse("<" + unserved + "> -> [provider=>ollama,protocol=>ollama,host=><" + closed + ">,llm=><qwen3-embedding:4b>]").apply();
            ObjmtronSerializer.parse("[provider=>ollama,protocol=>ollama,host=><" + hollowHost + ">,llm=><qwen3-embedding:4b>].as(model::T).to(" + hollowSpec + ")").apply();
            // the model ahead of the function, whose answer would be [9.0,9.0]
            ObjmtronSerializer.parse(String.format(space, "firstembed", "model=>!*<" + served + ">",
                    ", config => [embed => [# => inst?lst[real]<=#{?}(doc=>#::T){ [9.0,9.0] }]]")).apply();
            AbstractMetatronTest.checkCodeParseApply(LOG, "<firstembed:pile/volta> -> [n=>1]", "[n=>1]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<firstembed:pile/volta?embedq>", "[0.5,0.25]");
            AbstractMetatronTest.checkEquality(LOG, jnt(1), jnt(builds.get()), true);
            // with nothing where the reference points, the write fails: it does not fall back to the function
            Machine.write(served, noobj());
            final Obj unmodeled = ObjmtronSerializer.parse("<firstembed:pile/ohm> -> [n=>2]").apply();
            assertTrue(unmodeled.isFail() && unmodeled.toString().contains("reads no model"), "no model, and no fallback to the function: " + unmodeled);
            // a spec where the model should be is built at every write, and stays a spec
            ObjmtronSerializer.parse("<" + unbuilt + "> -> [provider=>ollama,protocol=>ollama,host=><" + stand + ">,llm=><qwen3-embedding:4b>]").apply();
            ObjmtronSerializer.parse(String.format(space, "specembed", "model=>!*<" + unbuilt + ">", "")).apply();
            AbstractMetatronTest.checkCodeParseApply(LOG, "<specembed:pile/volta> -> [n=>1]", "[n=>1]");
            AbstractMetatronTest.checkCodeParseApply(LOG, "<specembed:pile/faraday> -> [n=>2]", "[n=>2]");
            AbstractMetatronTest.checkEquality(LOG, jnt(3), jnt(builds.get()), true);
            AbstractMetatronTest.checkEquality(LOG, uri(LLM_MODEL_TID), uri(Machine.read(unbuilt).tid()), false);
            // a build that fails fails the write as its own error
            ObjmtronSerializer.parse(String.format(space, "failembed", "model=>!*<" + unserved + ">", "")).apply();
            final Obj unreachable = ObjmtronSerializer.parse("<failembed:pile/volta> -> [n=>1]").apply();
            assertTrue(unreachable.isFail() && unreachable.toString().contains("ConnectException") && !unreachable.toString().contains("not an embedding"),
                    "the build's own error, as itself: " + unreachable);
            // a model that answers an empty embedding is refused under its own name, not a function's
            ObjmtronSerializer.parse(String.format(space, "hollowembed", "model=>!*<" + hollowSpec + ">", "")).apply();
            final Obj hollowed = ObjmtronSerializer.parse("<hollowembed:pile/volta> -> [n=>1]").apply();
            assertTrue(hollowed.isFail() && hollowed.toString().contains("the model of collection pile answered") && hollowed.toString().contains("not an embedding"),
                    "the model's answer, under the model's name: " + hollowed);
            // neither a model nor a function
            ObjmtronSerializer.parse(String.format(space, "noembed", "=>", "")).apply();
            final Obj unembedded = ObjmtronSerializer.parse("<noembed:pile/volta> -> [n=>1]").apply();
            assertTrue(unembedded.isFail() && unembedded.toString().contains("no embedding function for collection pile"),
                    "no model and no function: " + unembedded);
        } finally {
            Machine.write(served, noobj());
            Machine.write(unserved, noobj());
            Machine.write(unbuilt, noobj());
            Machine.write(hollowSpec, noobj());
            ollama.stop(0);
            hollow.stop(0);
            unmount(f("/memory/space/vec/firstembed"), f("/memory/space/vec/specembed"), f("/memory/space/vec/failembed"),
                    f("/memory/space/vec/hollowembed"), f("/memory/space/vec/noembed"));
        }
    }

    /**
     * a localhost stand-in for an ollama server with one embedding model. It counts the builds (a build asks for the
     * model list once, /api/tags, and for each listed model's card twice, /api/show) and keeps what each embed asks for
     */
    private static HttpServer ollama(final AtomicInteger builds, final List<String> embedded) throws IOException {
        return ollama(builds, embedded, OLLAMA_EMBED);
    }

    /**
     * the stand-in, answering every embed with the given json
     */
    private static HttpServer ollama(final AtomicInteger builds, final List<String> embedded, final String embedding) throws IOException {
        final HttpServer ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/tags", exchange -> answer(exchange, OLLAMA_TAGS, request -> builds.incrementAndGet()));
        ollama.createContext("/api/show", exchange -> answer(exchange, OLLAMA_SHOW, request -> {}));
        ollama.createContext("/api/embed", exchange -> answer(exchange, embedding, embedded::add));
        ollama.start();
        return ollama;
    }

    /**
     * the embedq of the space mounted at vid, which keeps the model the space embeds with
     */
    private static QProc embedq(final fURI vid) {
        return mounted(vid.toString()).asRec().at(uri(QPROC)).asLst().<QProc>elements()
                .filter(vecEmbedQ.class::isInstance).findFirst().orElseThrow();
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
