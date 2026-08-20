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

package studio.phaseshift.metatron.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace.FS_SPACE_TYPE;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CommonUtilTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(CommonUtilTest.class);
    private static final Path ROOT = Path.of("/tmp/common_utils_test");

    /**
     * Deterministic fake directory tree.
     * <pre>
     * /tmp/common_utils_test/
     *   a/
     *     a1/
     *       x.txt
     *       y.txt
     *     a2/
     *       z.txt
     *   b/
     *     b1.txt
     *     b2/
     *       deep.txt
     *   c.txt
     * </pre>
     */
    private static void createFakeDirStructure() throws IOException {
        deleteFakeDirStructure(); // fresh start
        Files.createDirectories(ROOT.resolve("a/a1"));
        Files.createDirectories(ROOT.resolve("a/a2"));
        Files.createDirectories(ROOT.resolve("b/b2"));
        Files.writeString(ROOT.resolve("a/a1/x.txt"), "x content");
        Files.writeString(ROOT.resolve("a/a1/y.txt"), "y content");
        Files.writeString(ROOT.resolve("a/a2/z.txt"), "z content");
        Files.writeString(ROOT.resolve("b/b1.txt"), "b1 content");
        Files.writeString(ROOT.resolve("b/b2/deep.txt"), "deep content");
        Files.writeString(ROOT.resolve("c.txt"), "c content");
    }

    private static void deleteFakeDirStructure() throws IOException {
        if (!Files.exists(ROOT)) return;
        try (var walk = Files.walk(ROOT)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @BeforeAll
    static void setUp() throws IOException {
        AbstractMetatronTest.begin();
        createFakeDirStructure();
    }

    @AfterAll
    static void tearDown() throws IOException {
        deleteFakeDirStructure();
    }

    @Test
    public void testTreeConsumer() {
        final fsSpace space = FS_SPACE_TYPE.constructor().asInst().args(lst(rec(
                uri(PATTERN), uri("local:#"),
                uri(ROUTE), rec(uri("local:"), uri("/tmp/common_utils_test"))).vid(f("/sys/space/local")))).apply(noobj()).as();
        LOG.warn(space);
        Router.global().addSpace(space);
        final List<CommonUtil.TreeEntry> nodes = new ArrayList<>();
        CommonUtil.treeConsumer(f("local:"), 3, nodes::add);
        LOG.warn(Router.readFromSpace("local:#/"));
        // Debug: dump treeConsumer results
        for (int i = 0; i < nodes.size(); i++) {
            final var n = nodes.get(i);
            final String indent = "  ".repeat(n.depth());
            LOG.warn("[%02d] %s%s depth=%d last=%s children=%d",
                    i, indent, n.name(), n.depth(), n.isLast(), n.childCount());
        }
        // All nodes collected
        assertFalse(nodes.isEmpty(), "should have collected tree nodes");
        assertEquals(12, nodes.size(), "1 root + 3 dirs + 5 files + 3 sub-dirs");

        // DFS order from recursive +/ branch reads:
        // [idx] name depth  isLast  childCount

        // [0]  (root)        0      true    3
        assertEquals("", nodes.get(0).name());
        assertEquals(0, nodes.get(0).depth());
        assertTrue(nodes.get(0).isLast());
        assertEquals(3, nodes.get(0).childCount());

        // [1]    a            1      false   2
        assertEquals("a", nodes.get(1).name());
        assertEquals(1, nodes.get(1).depth());
        assertFalse(nodes.get(1).isLast());
        assertEquals(2, nodes.get(1).childCount());

        // [2]      a1          2      false   2
        assertEquals("a1", nodes.get(2).name());
        assertEquals(2, nodes.get(2).depth());
        assertFalse(nodes.get(2).isLast());
        assertEquals(2, nodes.get(2).childCount());

        // [3]        x.txt      3      false   0
        assertEquals("x.txt", nodes.get(3).name());
        assertEquals(3, nodes.get(3).depth());
        assertFalse(nodes.get(3).isLast());
        assertEquals(0, nodes.get(3).childCount());

        // [4]        y.txt      3      true    0
        assertEquals("y.txt", nodes.get(4).name());
        assertEquals(3, nodes.get(4).depth());
        assertTrue(nodes.get(4).isLast());
        assertEquals(0, nodes.get(4).childCount());

        // [5]      a2          2      true    1
        assertEquals("a2", nodes.get(5).name());
        assertEquals(2, nodes.get(5).depth());
        assertTrue(nodes.get(5).isLast());
        assertEquals(1, nodes.get(5).childCount());

        // [6]        z.txt      3      true    0
        assertEquals("z.txt", nodes.get(6).name());
        assertEquals(3, nodes.get(6).depth());
        assertTrue(nodes.get(6).isLast());
        assertEquals(0, nodes.get(6).childCount());

        // [7]    b            1      false   2
        assertEquals("b", nodes.get(7).name());
        assertEquals(1, nodes.get(7).depth());
        assertFalse(nodes.get(7).isLast());
        assertEquals(2, nodes.get(7).childCount());

        // [8]      b1.txt      2      false   0
        assertEquals("b1.txt", nodes.get(8).name());
        assertEquals(2, nodes.get(8).depth());
        assertFalse(nodes.get(8).isLast());
        assertEquals(0, nodes.get(8).childCount());

        // [9]      b2          2      true    1
        assertEquals("b2", nodes.get(9).name());
        assertEquals(2, nodes.get(9).depth());
        assertTrue(nodes.get(9).isLast());
        assertEquals(1, nodes.get(9).childCount());

        // [10]       deep.txt   3      true    0
        assertEquals("deep.txt", nodes.get(10).name());
        assertEquals(3, nodes.get(10).depth());
        assertTrue(nodes.get(10).isLast());
        assertEquals(0, nodes.get(10).childCount());

        // [11]   c.txt        1      true    0
        assertEquals("c.txt", nodes.get(11).name());
        assertEquals(1, nodes.get(11).depth());
        assertTrue(nodes.get(11).isLast());
        assertEquals(0, nodes.get(11).childCount());

        LOG.info("collected %d tree nodes — all DFS assertions passed", nodes.size());
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(value = {
            "intelligence | intelligence",
            "Context Windows | context_window",
            "indexing | indexing",
            "searching | searching",
            "memory systems | memory_system",
            "processes | process",
            "indexes | index",
            "buses | bus",
            "cherries | cherry",
            "stories | story",
            "apples | apple",
            "cats | cat",
            "bosses | boss",
            "boxes | box",
            "churches | church",
            "wishes | wish",
            " | ''"
    }, delimiter = '|')
    void testNormalization(String input, String expected) {
        if (input == null) input = "";
        assertEquals(expected, CommonUtil.normalize(input));
    }

    // =========================================================================
    // correctSpelling(String) — built-in dictionary
    // =========================================================================

    @ParameterizedTest(name = "[{index}] correction: ''{0}'' -> ''{1}''")
    @CsvSource(value = {
            // Exact matches — already in dictionary
            "intelligence | intelligence",
            "algorithm | algorithm",
            "language | language",
            "database | database",

            // Single-edit misspellings (deletion)
            "inteligence | intelligence",
            "algorith | algorithm",
            "languge | language",
            "databse | database",

            // Single-edit misspellings (transposition)
            "langauge | language",

            // Single-edit misspellings (substitution)
            "databaze | database",
            "mesage | message",

            // More single-edit misspellings — common typos
            "recomend | recommend",
            "seperate | separate",
            "enviroment | environment",
            "goverment | government",
            "neccessary | necessary",
            "paralel | parallel",
            "similiar | similar",
            "specfic | specific",
            "sucess | success",
            "writting | writing",

            // Short-but-viable corrections (4+ chars, single edit)
            "grph | graph",
            "netwrk | network",
            "agnt | agent",

            // Two-edit misspellings on longer words
            "intelligance | intelligence",
            "algorhythm | algorithm",

            // Words not in dictionary, not close to anything → unchanged
            "xyzzy | xyzzy",
            "wombat | wombat",

            // Short words (< 4 chars) → always unchanged
            "cat | cat",
            "the | the",
            "abc | abc",
    }, delimiter = '|')
    void testCorrectSpellingBuiltin(String input, String expected) {
        assertEquals(expected, CommonUtil.correctSpelling(input));
    }

    @Test
    void testCorrectSpellingNull() {
        assertNull(CommonUtil.correctSpelling(null));
    }

    @Test
    void testCorrectSpellingBlank() {
        assertEquals("", CommonUtil.correctSpelling(""));
        assertEquals("   ", CommonUtil.correctSpelling("   "));
    }

    // =========================================================================
    // correctSpelling(String, Set<String>) — caller-provided dictionary
    // =========================================================================

    @Test
    void testCorrectSpellingWithCustomDictionary() {
        final Set<String> dict = Set.of("intelligence", "algorithm", "database");

        // Exact match
        assertEquals("intelligence", CommonUtil.correctSpelling("intelligence", dict));

        // Single-edit correction
        assertEquals("intelligence", CommonUtil.correctSpelling("inteligence", dict));
        assertEquals("algorithm", CommonUtil.correctSpelling("algorith", dict));

        // Word not in dictionary → unchanged
        assertEquals("language", CommonUtil.correctSpelling("language", dict));

        // Short word → unchanged
        assertEquals("cat", CommonUtil.correctSpelling("cat", dict));

        // Empty dictionary → unchanged
        assertEquals("inteligence", CommonUtil.correctSpelling("inteligence", Set.of()));

        // Null dictionary → unchanged
        assertEquals("inteligence", CommonUtil.correctSpelling("inteligence", null));
    }

    @Test
    void testCorrectSpellingNoFalsePositiveOnShortDict() {
        // With a small dictionary, a word that's not close to anything
        // should not be incorrectly "corrected"
        final Set<String> dict = Set.of("neural", "token", "weight");
        assertEquals("gradient", CommonUtil.correctSpelling("gradient", dict));
        assertEquals("attention", CommonUtil.correctSpelling("attention", dict));
    }

    // =========================================================================
    // splitOnNonQuotedSequence — quoted-span aware statement splitting
    // =========================================================================

    @ParameterizedTest(name = "{3}")
    @CsvSource(delimiter = '%', value = {
            "a -> 1; b -> 2%a -> 1|b -> 2%false%unquoted semicolon splits statements",
            "t1 -> \"a;b;c\"%t1 -> \"a;b;c\"%false%double-quoted semicolon stays in the fragment",
            "x -> 'a;b'%x -> 'a;b'%false%single-quoted semicolon stays in the fragment",
            "y -> \"\"\"a ; b ; c\"\"\"%y -> \"\"\"a ; b ; c\"\"\"%false%triple-quoted span keeps all semicolons",
            "z -> \"\"\" 'asdfasdf' ;;;; \"\"\"\"%z -> \"\"\" 'asdfasdf' ;;;; \"\"\"\"%false%nested single quotes and quadruple close stay in one fragment",
            "w -> \"a\\\";b\"%w -> \"a\\\";b\"%false%escaped quote does not close the span",
            "u -> 'a;b%u -> 'a;b%false%unterminated span still shields the semicolon",
            "v -> '''a;b'''%v -> '''a;b'''%false%triple-single-quoted span keeps the semicolon",
            "q -> \"\"\"a\"b;c\"\"\"%q -> \"\"\"a\"b;c\"\"\"%false%lone quote inside a triple span is literal content",
            "x -> \"\"; y -> 42%x -> \"\"|y -> 42%false%empty string literal does not open a span",
            "x -> \"\"\"\"; y -> 42%x -> \"\"\"\"|y -> 42%false%even quote runs are empty-string pairs, not spans",
            "a;b%a|;b%true%included split character leads the following fragment",
    })
    void splitOnNonQuotedSequenceKeepsQuotedSpans(String input, String expected, String include, String desc) {
        final List<String> actual = CommonUtil.splitOnNonQuotedSequence(input, ';', Boolean.parseBoolean(include));
        final List<String> expectedFragments = List.of(expected.split("\\|", -1));
        assertEquals(expectedFragments, actual, desc);
    }

    @Test
    public void testSplitDoesNotGlueStatementsAfterEmptyString() {
        // drsynx.boot.mtron failed to boot: `body => "",` (empty string) opened
        // a 2-quote span that never closed, so the following statement
        // `console::[...]` was glued into the preceding one and the parse died.
        final String source = "agent::[feature => [body => \"\", style => 1]]@<dr>; console::[header => 1]@/usr/marko/console";
        assertEquals(List.of(
                "agent::[feature => [body => \"\", style => 1]]@<dr>",
                "console::[header => 1]@/usr/marko/console"),
                CommonUtil.splitOnNonQuotedSequence(source, ';', false));
    }
}
