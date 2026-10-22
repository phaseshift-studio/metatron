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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.IO_ISA_TID;

@ExtendWith(TestData.TestDataExtension.class)
public abstract class AbstractMetatronTest {
    static {
        BootLoader.TESTING = true;
    }

    protected static final Random RANDOM = new Random();
    protected GraphittyLogger LOG = Graphitty.log(this);
    protected static GraphittyLogger STATIC_LOG = Graphitty.log(AbstractMetatronTest.class);

    /**
     * The ports {@link #generatePort()} draws from: above the live server's 8555/8777, and below the kernel's
     * ephemeral range (32768+ on Linux, 49152+ on macOS and Windows). The kernel hands ephemeral ports to every
     * outgoing connection, and a loopback client holding a port blocks a listener's bind on it even with
     * SO_REUSEADDR on, so a port probed free inside that range can be taken by a dial before the server binds it.
     * Below it, only another explicit bind can take a probed port.
     */
    private static final int PORT_FLOOR = 10000;
    private static final int PORT_CEILING = 32768;
    private static final int PORT_ATTEMPTS = 100;

    /**
     * A port nothing on this host holds. Surefire runs several forks at once, each starting servers, so a random
     * port alone collides now and then, and the server that loses the bind surfaces later as a refused dial. A
     * candidate is handed out only once a probe socket has bound it.
     */
    public static int generatePort() {
        return RANDOM.ints(PORT_ATTEMPTS, PORT_FLOOR, PORT_CEILING)
                .filter(AbstractMetatronTest::isFree)
                .findFirst()
                .orElseThrow(() -> MTronException.of("no free port in [%s,%s) after %s attempts", PORT_FLOOR, PORT_CEILING, PORT_ATTEMPTS));
    }

    /**
     * Can a server bind this port? The probe binds the wildcard address, so a socket on any local address refuses
     * the port, and it binds with SO_REUSEADDR off (Java turns it on for server sockets), so a lingering TIME_WAIT
     * or a client's local end refuses it too.
     */
    private static boolean isFree(final int port) {
        try (final ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(port));
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * The space the current machine's memory has mounted at {@code vid}, or noobj. Ask the index, not a read:
     * {@code [..]@vid} anchors the config rec at the vid whether or not the construction it feeds succeeds.
     */
    protected static Obj mounted(final String vid) {
        return Machine.current().memory().spaces().jvm().getOrDefault(f(vid).toUri(), noobj());
    }

    // the VM is booted once per test class, not once per test-case instance. the
    // constructor calls begin() so a subclass that bypasses @BeforeAll still boots,
    // but re-booting per @ParameterizedTest row is the dominant test-harness cost.
    private static volatile boolean BOOTED = false;

    public AbstractMetatronTest() {
        begin();

    }

    @BeforeAll
    public static void begin() {
        if (BOOTED)
            return;
        BOOTED = true;
        //memSpace.of(f("/sys/#"), null);
        // every type assertion ON except code_resolve — the same stance DocsUtil boots with. The suite
        // must exercise ENFORCEMENT, not just the type system's functions; code_resolve stays off because
        // it demands fully-resolved code and most tests evaluate dynamically-built code.
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.code_resolve);
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec());
        InstSet.importInstSet(IO_ISA_TID);
    }

    @AfterAll
    public static void end() {
        try {
            // A console is process-wide (Console.LOCAL_INSTANCE and its terminal are static) and surefire reuses its
            // forks, so a console one class leaves open is found by every class the fork runs after it: widgets float
            // onto its surface and tools take their mode from it (that is how NetworkPanelToolTest once hung a fork).
            if (null != Console.LOCAL_INSTANCE)
                closeConsole(Console.LOCAL_INSTANCE);
        } finally {
            BootLoader.close();
            BOOTED = false;
        }
    }

    /**
     * Close a console a test built. {@link Console#close()} gives back the console statics; the floating surface's
     * render thread is a daemon it leaves to the process exit, and in a reused fork that thread would poll every
     * couple of milliseconds for the rest of the run, so it is stopped here.
     */
    protected static void closeConsole(final Console console) {
        console.close();
        console.getFloatingSurface().shutdown();
    }

    /**
     * Pop every pushed frame until the root machine is current again, so a failed mid-script assertion never leaks a
     * frame into the next test. No-op at the root.
     */
    protected void resetToRoot() {
        // compare by vid, not object identity: after a scripted push/pop the current machine can be a distinct
        // instance that still names the root (/.); popping a root whose parent isn't a Machine is a no-op, so an
        // identity check would spin forever.
        final fURI rootVID = Machine.root().vid();
        while (Machine.current() != null && !Machine.current().vid().equals(rootVID))
            Machine.current().pop();
    }

    /**
     * A test-side action a script may invoke as {@code [DIRECTIVES] <name> [args…]}.
     * <p>
     * Directives exist for the facts a mtron expression cannot reach: starting a peer cluster, mounting a space
     * that claims an authority, or probing a Java-side predicate such as the network's {@code own} / {@code
     * isPeer}. A directive that establishes state does it the way the test would; a directive that probes writes
     * its answer to an address the script then asserts on, so the observation stays in the script.
     */
    @FunctionalInterface
    protected interface Directive {
        void apply(String... args) throws Exception;
    }

    /**
     * The directives THIS suite offers, by name. Empty by default — a suite that needs infrastructure exposes it
     * here rather than reaching around the script, which keeps the script the single readable account of what is
     * being proven.
     */
    protected Map<String, Directive> directives() {
        return Map.of();
    }

    /**
     * SCRIPT VARIABLES — the bridge between a directive that creates a DYNAMIC fact (an allocated port, an
     * ephemeral authority) and the static script lines that must speak it. A directive binds a name and every
     * later line has {@code {{name}}} replaced before it is parsed — directive lines included, so one directive's
     * fact can feed another's arguments. Empty by default: a suite that binds nothing runs byte-identically.
     */
    private final Map<String, String> scriptVars = new LinkedHashMap<>();

    /**
     * Bind a script variable: every {@code {{name}}} in later lines becomes {@code value}.
     */
    protected void bind(final String name, final String value) {
        this.scriptVars.put(name, value);
    }

    private String substitute(String line) {
        if (!line.contains("{{"))
            return line;
        for (final Map.Entry<String, String> var : this.scriptVars.entrySet())
            line = line.replace("{{" + var.getKey() + "}}", var.getValue());
        return line;
    }

    /**
     * Run one directive call: the first token names the directive (in CAPS), the rest are its arguments.
     */
    private void directive(final String call) {
        final String[] parts = call.split("\\s+");
        final Directive directive = this.directives().get(parts[0]);
        if (null == directive)
            throw MTronException.of("no such directive: %s (this suite offers %s)", parts[0], this.directives().keySet());
        try {
            directive.apply(Arrays.copyOfRange(parts, 1, parts.length));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * The language's own annotation form — {@code [-- … --]} — is a comment anywhere it appears, so a script may
     * annotate a directive line the same way it annotates an expression.
     */
    private static String stripComment(final String line) {
        final int at = line.indexOf("[--");
        return at < 0 ? line : line.substring(0, at);
    }

    /**
     * A stateful, sequential test script. Unlike {@code @CsvSource} — whose rows are independent and stateless — each
     * line runs in order, so a machine can be pushed/popped mid-script and every observation is checked as it happens.
     * This is the durable form of a console session: a thought about a mutation's consistency is ONE line.
     * <p>
     * Line forms:
     * <pre>
     *   [NAME args…]               run the test-side directive NAME (CAPS), offered by this suite
     *   [STATE] &lt;mtron&gt;            execute the mtron (mutate machine state: push, pop, write, …)
     *   // &lt;comment&gt;               ignored (blank lines too), and [-- … --] is stripped anywhere
     *   &lt;code&gt; % &lt;expected&gt;        2 columns — checkCodeParseApply(code, expected); express relations with eq/neq/gt/…
     *   &lt;lhs&gt; % &lt;rhs&gt; % &lt;equals&gt;   3 columns — checkEquality(eval(lhs), eval(rhs), equals)
     * </pre>
     */
    protected void script(final String... lines) {
        for (int i = 0; i < lines.length; i++) {
            final String line = this.substitute(lines[i].trim());
            if (line.isEmpty() || line.startsWith("//"))
                continue;
            try {
                // [NAME args…] — a test-side directive, bracketed like [STATE] and named in CAPS so it is obvious
                // at a glance that the line leaves mtron. [-- … --] is stripped first: the language's own
                // annotation form is a comment wherever it appears, including on a directive line.
                if (line.startsWith("[") && !line.startsWith("[STATE]")) {
                    final int close = line.indexOf(']');
                    if (close < 0)
                        fail("a directive line must close its bracket: " + line);
                    final String call = (stripComment(line.substring(1, close)) + " "
                            + stripComment(line.substring(close + 1))).trim();
                    if (call.isEmpty() || call.startsWith("--"))
                        continue; // a bare [-- comment --]
                    LOG.debug("[directive] %s", call);
                    this.directive(call);
                    continue;
                }
                if (line.startsWith("[STATE] ")) {
                    final String mtron = line.substring("[STATE] ".length()).trim();
                    LOG.debug("[state] %s", mtron);
                    ObjmtronSerializer.parse(mtron).apply();
                    continue;
                }
                final String[] parts = stripComment(line).split("%", -1);
                if (parts.length == 3)
                    checkEquality(LOG, ObjmtronSerializer.parse(parts[0].trim()).apply(),
                            ObjmtronSerializer.parse(parts[1].trim()).apply(), Boolean.parseBoolean(parts[2].trim()));
                else if (parts.length == 2)
                    checkCodeParseApply(LOG, parts[0].trim(), parts[1].trim());
                else
                    fail("script line must be [STATE] <mtron>, <code> % <expected>, or <lhs> % <rhs> % <equals>: " + line);
            } catch (final AssertionError | RuntimeException e) {
                System.out.println("SCRIPT LINE " + (i + 1) + " FAILED: " + line);
                throw e;
            }
        }
    }

    public static void checkMatches(final GraphittyLogger LOG, final String lhs, final String rhs, final boolean matches) {
        final Obj a = ObjmtronSerializer.parse(lhs);
        final Obj b = ObjmtronSerializer.parse(rhs);
        final boolean m = a.test(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public static void checkMatchesByID(final GraphittyLogger LOG, final String lhs, final String rhs, final boolean matches) {
        final Obj a = ObjmtronSerializer.parse(lhs).apply(jnt(1));
        final Obj b = ObjmtronSerializer.parse(rhs);
        final boolean m = a.testByID(b);
        LOG.debug("testing %s matches by ID %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public static void checkCodeEvaluate(final GraphittyLogger LOG, final String lhs, final String expected) {
        final Obj a = ObjmtronSerializer.parse(lhs);
        final Obj b = ObjmtronSerializer.parse(expected);
        final Obj actual = b.apply(a);
        LOG.debug("testing %s => %s [expected:%s]", a, b, actual);
        if (!expected.trim().equals("<ERROR>")) {
            boolean noFails = a.stream().noneMatch(Obj::isFail);
            if (!noFails)
                a.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "should not have failed");
            assertTrue(actual.stream().noneMatch(Obj::isFail), "should not have failed");
            assertEquals(b, actual);
        } else {
            final boolean fails = actual.stream().anyMatch(Obj::isFail);
            if (!fails)
                a.elements().forEach(LOG::error);
            assertTrue(fails, "should have failed");
        }

    }

    public static void checkCodeEvaluate(final GraphittyLogger LOG, final String evaluate, final String fetchResult, final String expectedResult) {
        final Obj evaluation = ObjmtronSerializer.parse(evaluate).apply();
        final Obj actual = ObjmtronSerializer.parse(fetchResult).apply();
        final Obj expected = ObjmtronSerializer.parse(expectedResult).apply();
        LOG.debug("testing %s; %s [expected:%s]", evaluation, actual, expected);
        if (!expectedResult.trim().equals("<ERROR>")) {
            boolean noFails = evaluation.stream().noneMatch(Obj::isFail);
            if (!noFails)
                evaluation.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "evaluation should not have failed");
            noFails = actual.stream().noneMatch(Obj::isFail);
            if (!noFails)
                actual.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "actual should not have failed");
            assertEquals(expected, actual);
        } else {
            boolean fails = evaluation.stream().anyMatch(Obj::isFail);
            if (!fails) {
                fails = actual.stream().anyMatch(Obj::isFail);
                if (!fails) {
                    evaluation.elements().forEach(LOG::error);
                    actual.elements().forEach(LOG::error);
                }
                assertTrue(fails, "should have failed");
            }
            assertTrue(fails, "should have failed");
        }

    }

    public static void checkEquality(final GraphittyLogger LOG, final Obj a, final Obj b, final boolean equals) {
        LOG.debug("testing %s == %s [expected:%s]", a, b, equals);
        if (equals)
            assertEquals(a, b, Graphitty.string("failed %s != %s", a, b));
        else
            assertNotEquals(a, b, Graphitty.string("failed %s == %s", a, b));
    }

    public static void checkSpaceMutation(final GraphittyLogger LOG, final String stateCode, final String mutationCode, final Map<fURI, String> expected) {
        final Obj stateResult = ObjmtronSerializer.parse(stateCode).apply();
        final Obj mutationResult = ObjmtronSerializer.parse(mutationCode).apply();
        LOG.debug("testing %s <= %s", stateResult, mutationResult);
        expected.forEach((k, v) -> {
            final Obj actual = Machine.read(k);
            final Obj desired = ObjmtronSerializer.parse(v).apply();
            LOG.debug("\t%s [expected] == %s [actual]", desired, actual);
            assertEquals(desired, actual);
        });
    }

    public static Tuple.Quartet<Obj, Long, Obj, Long> checkParsePerformance(final GraphittyLogger LOG, final Supplier<Obj> lhs, final Supplier<Obj> rhs) {
        final Tuple.Pair<Obj, Long> parseResult = CommonUtil.clock(lhs);
        assertInstanceOf(Call.class, parseResult.get0());
        final Tuple.Pair<Obj, Long> evalResult = CommonUtil.clock(parseResult.get0(), rhs.get());
        assertEquals(jnt(3), evalResult.get0());
        return Tuple.Quartet.with(parseResult.get0(), parseResult.get1(), evalResult.get0(), evalResult.get1());
    }

    public static void checkCodeRewrite(final GraphittyLogger LOG, final String code, final String expected, final String expectedResult, boolean checkTIDs) {
        final Code firstStage = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = firstStage.rewrite().tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        if (checkTIDs) {
            assertFalse(secondStage.insts().isEmpty());
            assertEquals(secondStage.insts().size(), compilation.insts().size());
            for (int i = 0; i < compilation.insts().size(); i++) {
                assertEquals(compilation.insts().get(i).tid().basePath(), secondStage.insts().get(i).tid().basePath());
            }
            assertEquals(result, firstStage.apply(noobj()));
        } else {

            LOG.debug("testing compilation %s => %s [expected:%s]", firstStage, secondStage, compilation);
            assertEquals(secondStage, compilation);
            Obj actual = firstStage.apply(noobj());
            LOG.debug("testing evaluation 1 %s => %s [expected:%s]", firstStage, actual, result);
            assertEquals(result, actual);
            actual = secondStage.apply(noobj());
            LOG.debug("testing evaluation 2 %s => %s [expected:%s]", secondStage, actual, result);
            assertEquals(result, actual);
            actual = compilation.apply(noobj());
            LOG.debug("testing evaluation 3 %s => %s [expected:%s]", compilation, actual, result);
            assertEquals(result, actual);
        }
    }

    public static void checkCodeParseApply(final GraphittyLogger LOG, final String lhs, final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj a = ObjmtronSerializer.parse(lhs);
                final Obj b = ObjmtronSerializer.parse(code);
                final Obj actual = b.apply(a);
                LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, expected);
                fail("expected error but got " + actual);
            } catch (final Exception e) {
                LOG.debug("testing %s.%s => %s [expected:%s]", lhs, code, e.getMessage(), expected);
            }
        } else {
            final Obj a = ObjmtronSerializer.parse(lhs);
            final Obj b = ObjmtronSerializer.parse(code);
            final Obj ex = ObjmtronSerializer.parse(expected);
            final Obj actual = b.apply(a);
            LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
            checkEquality(LOG, ex, actual, true);
        }
    }

    public static void checkBasicCodeParseApply(final GraphittyLogger LOG, final String code, final String expected) {
        assertEquals(ObjmtronSerializer.parse(code).apply(), ObjmtronSerializer.parse(expected).apply());
    }

    public static void checkCodeParseApply(final GraphittyLogger LOG, final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            final List<Obj> results;
            try {
                final Obj cd = code.contains(";") ? ObjmtronSerializer.parse(code).apply() : ObjmtronSerializer.parse(code);
                // walk the result here, exactly once: a lazy stream is consumed by its walk and may throw only then
                results = (cd.isFail() ? cd : cd.apply(noobj())).stream().toList();
            } catch (final Exception e) {
                // a throw is the failure the row expects
                LOG.debug("testing %s => %s [expected:%s]", code, e.getMessage(), expected);
                return;
            }
            LOG.debug("testing %s => %s [expected:%s]", code, results, expected);
            assertTrue(results.stream().anyMatch(Obj::isFail), Graphitty.string("should have failed: %s => %s", code, results));
        } else {
            final Obj cd = Machine.parse(code);
            final Obj exParsed = Machine.parse(expected);
            final Obj ex = exParsed.isCall() ? exParsed.apply() : exParsed;
            final Obj actual = cd.apply(noobj());
            LOG.debug("testing %s => %s => %s [expected:%s]", code, code, actual, ex);
            if (actual.isReal() && ex.isReal()) {
                assertEquals(actual.realValue(), ex.realValue(), 0.00001d);
                assertEquals(actual.c(), ex.c());
                assertEquals(actual.tid(), ex.tid());
            } else {
                if (!actual.equals(ex) && actual.stream().anyMatch(Obj::isFail))
                    LOG.error("expectation led to failure: %s", actual);
                if (ex.tid().hasPoly()) ///  TODO: how to handle generalization of a polynomial as it relates to equality
                    assertTrue(actual.test(ex));
                else
                    assertEquals(ex, actual);
            }
            
          /*  final Obj acd = serializer.read(serializer.write(cd));
            final Obj aex = serializer.read(serializer.write(ex));
            final Obj aactual = serializer.read(serializer.write(actual));
            LOG.debug("testing (de)serialization %s => %s [expected:%s]", acd, aactual, aex);
            assertEquals(aex, aactual); */
        }
    }

    /**
     * Utility method for distributed testing - check if URI requires cross-host routing
     */
    public static boolean isCrossHostUri(final GraphittyLogger LOG, final fURI uri) {
        boolean isCrossHost = uri.hasAuthority();
        LOG.debug("URI {{b}}%s{{X}} is cross-host: %s", uri, isCrossHost);
        return isCrossHost;
    }

    /**
     * Utility method for distributed testing - simulate network delay
     */
    public static void simulateNetworkDelay(final GraphittyLogger LOG, int milliseconds) {
        try {
            LOG.debug("Simulating network delay of {{b}}%d{{X}} ms", milliseconds);
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("network delay simulation interrupted");
        }
    }

    /**
     * Utility method to get router statistics
     */
    public static Obj getRouterStatistics(final GraphittyLogger LOG) {
        if (Machine.loaded()) {
            return Machine.current().at(uri("stats"));
        }
        LOG.warn("router not loaded, cannot get stats");
        return noobj();
    }

}
