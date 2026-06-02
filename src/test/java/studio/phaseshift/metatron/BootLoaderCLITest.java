package studio.phaseshift.metatron;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TypeCheck;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI integration tests for {@link BootLoader#main(String[])}.
 * Uses {@link BootLoader#EXIT_HANDLER} to intercept exit calls and
 * capture stdout/stderr for assertion.
 */
public class BootLoaderCLITest {

    private final ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final InputStream originalIn = System.in;

    private static final class TestExit extends RuntimeException {
        private final int code;
        TestExit(int code) { this.code = code; }
    }

    static {
        BootLoader.TESTING = true;
    }

    @BeforeAll
    static void beforeAll() {
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.values());
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
    }

    @BeforeEach
    void setUp() {
        outCapture.reset();
        errCapture.reset();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
        BootLoader.BOOTING = true;
        BootLoader.ONE_SHOT = false;
        BootLoader.EXIT_HANDLER = code -> { throw new TestExit(code); };
    }

    @AfterEach
    void tearDown() {
        BootLoader.EXIT_HANDLER = System::exit;
        BootLoader.TESTING = false;
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    // ---- Immediate-exit flags -------------------------------------------

    @Test
    void testVersionFlag() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-v"}));
        assertEquals(0, ex.code);
        assertEquals("metatron " + Tokens.METATRON_VERSION + "\n", stdout());
    }

    @Test
    void testVersionLongFlag() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"--version"}));
        assertEquals(0, ex.code);
        assertEquals("metatron " + Tokens.METATRON_VERSION + "\n", stdout());
    }

    @Test
    void testHelpFlag() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-h"}));
        assertEquals(0, ex.code);
        assertTrue(stdout().contains("Usage: metatron"));
        assertTrue(stdout().contains("-b, --boot"));
        assertTrue(stdout().contains("-e, --eval"));
        assertTrue(stdout().contains("-f, --file"));
        assertTrue(stdout().contains("$METATRON_BOOT"));
    }

    @Test
    void testHelpLongFlag() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"--help"}));
        assertEquals(0, ex.code);
        assertTrue(stdout().contains("Usage: metatron"));
    }

    @Test
    void testNoArgsShowsHelp() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{}));
        assertEquals(0, ex.code);
        assertTrue(stdout().contains("Usage: metatron"));
    }

    // ---- Error cases ----------------------------------------------------

    @Test
    void testUnknownOption() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"--bogus", "abc"}));
        assertEquals(1, ex.code);
        assertTrue(stderr().contains("unknown option"));
        //assertTrue(stderr().contains("--bogus"));
    }

    @ParameterizedTest(name = "[{index}] {0} missing argument")
    @CsvSource(value = {
            "-b  % missing -b argument",
            "-e  % missing -e argument",
            "-f  % missing -f argument",
            "--boot  % missing --boot argument",
            "--eval  % missing --eval argument",
            "--file  % missing --file argument",
    }, delimiter = '%')
    void testMissingArgument(String flag, String desc) {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{flag}));
        assertEquals(1, ex.code);
        assertTrue(stderr().contains("requires"));
    }

    // ---- Expression evaluation (-e and bare positional) ------------------

    @Test
    void testBarePositionalExpression() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"1 + 2"}));
        assertEquals(0, ex.code);
        assertEquals("3\n", stdout());
        assertEquals("", stderr());
    }

    @Test
    void testEvalSimpleExpression() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-e", "1 + 2"}));
        assertEquals(0, ex.code);
        assertEquals("3\n", stdout());
        assertEquals("", stderr());
    }

    @Test
    void testEvalQuietMode() {
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-q", "-e", "1 + 2"}));
        assertEquals(0, ex.code);
        assertEquals("3\n", stdout());
        assertEquals("", stderr());
    }

    // ---- File evaluation (-f) -------------------------------------------

    @Test
    void testEvalFile() throws Exception {
        final Path tmpFile = Files.createTempFile("mtron_test_", ".mtron");
        try {
            Files.writeString(tmpFile, "2 + 3");
            final TestExit ex = assertThrows(TestExit.class, () ->
                    BootLoader.main(new String[]{"-f", tmpFile.toString()}));
            assertEquals(0, ex.code);
            assertEquals("5\n", stdout());
            assertEquals("", stderr());
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    // ---- Legacy backward compatibility ----------------------------------

    @Test
    void testLegacySingleArgExpression() {
        // Legacy form boots the system and returns (no -e/-f, no exit).
        // load() returns because TESTING=true.
        assertDoesNotThrow(() -> BootLoader.main(new String[]{"[log=>info]"}));
        assertFalse(stderr().contains("unknown option"),
                "legacy form should not produce 'unknown option' error");
    }

    // ---- Pipe input (-p) ------------------------------------------------

    @ParameterizedTest(name = "[{index}] stdin ''{0}'' + expr ''{1}'' → ''{2}'' ({3})")
    @CsvSource(value = {
            "3       % _ + 5       % 8           % identity _ binds pipe value",
            "3       % + 5         % 8           % no _ needed, lhs is pipe",
            "[1,2]   % _ + [a,a]   % [1,2,a,a]   % list concatenation via pipe",
            "[1,2]   % + [a,a]     % [1,2,a,a]   % list concat without _",
            "hello   % _           % 'hello'     % identity returns pipe value",
    }, delimiter = '%')
    void testPipeInput(String stdinStr, String expr, String expected, String desc) {
        System.setIn(new ByteArrayInputStream(stdinStr.getBytes()));
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-p", "-e", expr}));
        assertEquals(0, ex.code);
        assertEquals(expected + "\n", stdout());
        assertEquals("", stderr());
    }

    @Test
    void testPipeWithoutPFlagIgnoresStdin() {
        // Without -p, stdin is not read even if data is available (in test mode)
        System.setIn(new ByteArrayInputStream("3".getBytes()));
        final TestExit ex = assertThrows(TestExit.class, () ->
                BootLoader.main(new String[]{"-e", "1 + 2"}));
        assertEquals(0, ex.code);
        assertEquals("3\n", stdout());
    }

    // ---- Helpers --------------------------------------------------------

    private String stdout() {
        return outCapture.toString();
    }

    private String stderr() {
        return errCapture.toString();
    }
}
