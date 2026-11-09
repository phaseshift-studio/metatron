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

package studio.phaseshift.metatron.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * The script→recording compiler, and the fence attributes that make a doc
 * block a demo. No VM is booted here: these pin the SHAPE of the emitted
 * asciicast (v3 header, RELATIVE intervals, generated keystrokes) and the
 * parse failures a typo must produce — the evaluated-content side lives in
 * MtronPreprocessorTest.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AsciinemaRunnerTest {

    /**
     * One authored line becomes many events, the header is fixed, and the two
     * compilations are byte-identical: determinism is the property the checked-in
     * recording relies on.
     */
    @Test
    public void testScriptCompilesToDeterministicV3(@TempDir final Path tmp) throws IOException {
        final Path script = tmp.resolve("demo.cast.yml");
        Files.writeString(script, """
                title: types
                prompt: "mtron> "
                cols: 100
                rows: 30
                typing_delay: 0.05
                actions:
                  - input: "1 + 1"
                    output: ["==>2"]
                  - marker: chapter
                """);

        final Asciicast.Rendered first = AsciinemaRunner.compile(script);
        final Asciicast.Rendered second = AsciinemaRunner.compile(script);
        assertEquals(first.text(), second.text());
        assertEquals(first.text(), AsciinemaRunner.compile(tmp.resolve("demo.cast.yml")).text());

        final String header = first.text().split("\n", 2)[0];
        assertTrue(header.startsWith("{\"version\":3"), header);
        assertTrue(header.contains("\"cols\":100"), header);
        assertTrue(header.contains("\"rows\":30"), header);
        assertTrue(header.contains("\"title\":\"types\""), header);

        // one event per keystroke, plus the prompt / newline / result / marker
        assertTrue(first.text().contains("[0.05,\"o\",\"1\"]"), first.text());
        assertTrue(first.text().contains("[0.05,\"o\",\" \"]"), first.text());
        assertTrue(first.text().contains("[0.12,\"o\",\"\\r\\n\"]"), first.text());
        assertTrue(first.text().contains("[0.3,\"o\",\"==>2\\r\\n\"]"), first.text());
        assertTrue(first.text().contains("[0.2,\"m\",\"chapter\"]"), first.text());
        assertEquals(9, first.events());
    }

    /** A typo in a key is a failed build, never a silently ignored line. */
    @ParameterizedTest
    @CsvSource(delimiter = '%', quoteCharacter = '"', value = {
            "typo: 1%unknown key 'typo'",
            "actions: []%'actions' must be a non-empty list"
    })
    public void testBadScriptsFail(final String body, final String expected, @TempDir final Path tmp)
            throws IOException {
        final Path script = tmp.resolve("bad.cast.yml");
        Files.writeString(script, body);
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AsciinemaRunner.compile(script));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    /** The fence attributes, and the ones that must be rejected. */
    @Test
    public void testFenceAttributes() {
        assertEquals("demo", MtronPreprocessor.attributes("cast=demo cols=100").get("cast"));
        assertEquals("100", MtronPreprocessor.attributes("cast=demo cols=100").get("cols"));
        assertTrue(MtronPreprocessor.attributes(null).isEmpty());
        assertTrue(MtronPreprocessor.attributes("  ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> MtronPreprocessor.attributes("cast=demo bogus=1"));
        assertThrows(IllegalArgumentException.class, () -> MtronPreprocessor.attributes("cast=../../etc/passwd"));
    }

    /** A cast name is a filename, so the parser refuses a path. */
    @Test
    public void testCastNameIsAFilename(@TempDir final Path tmp) {
        assertFalse(MtronPreprocessor.attributes("cast=demo-1.2_ok").isEmpty());
    }
}
