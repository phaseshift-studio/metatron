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

package studio.phaseshift.metatron.isa.m.parse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A COMMENT IS OPAQUE. Nothing inside {@code [-- --]} or {@code [== ==]} is code — not a quote, not a uri, not a
 * bracket — so a comment can say anything and every statement after it still runs.
 * <p>
 * The comment scanner once found string literals first and comments second, so the apostrophe in a comment such as
 * {@code [== the web tool's *<url> ==]} opened a string that ran to the next quote in the file. Every comment marker in
 * between was hidden, the comment closed at a much later terminator, and the statements in between were deleted as if
 * they were comment text: a boot file stopped mid-way with no error at all. Each row is a program run through
 * {@code Machine.Helper.evaluate(File)} — the path a boot file takes — and every one of its statements must answer.
 * ({@code \n} in a row is a line break.)
 */
public class CommentOpacityTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(quoteCharacter = '~', delimiter = '%', value = {
            "1;[-- an apostrophe's in a line comment --]2;3            % 1,2,3                       % an apostrophe in a line comment is text",
            "1;[== an apostrophe's in a block comment ==]2;3           % 1,2,3                       % an apostrophe in a block comment is text",
            "1;[== so the web tool's *<url> reads the web (s) ==]2;3   % 1,2,3                       % the comment that stopped the drstynx boot",
            "1;[-- the process's stdin/stdout --]\\n2;3                % 1,2,3                       % the comment that hid the mcp boot's carrier",
            "1;[-- a \"double quote --]2;3                             % 1,2,3                       % a double quote in a comment is text",
            "1;[-- three ''' and one ' --]2;3                          % 1,2,3                       % a run of quotes in a comment is text",
            "1;[-- a line comment ends at its line\\n2;3               % 1,2,3                       % an unclosed line comment ends at its line, apostrophe or not",
            "1;[-- it's open\\n2;3                                     % 1,2,3                       % an unclosed line comment with an apostrophe ends at its line too",
            "1;[== one's\\nacross lines ==]2;3                         % 1,2,3                       % a block comment spans lines",
            "1;[== it's ==]2;[-- and it's --]3                         % 1,2,3                       % two comments, each with an apostrophe",
            "'a [-- marker --] in a string';2                          % 'a [-- marker --] in a string',2 % a comment marker inside a string is text",
    })
    void testACommentIsOpaque(final String source, final String expected, final String description) throws IOException {
        final Path file = Files.createTempFile("comment-opacity", ".mtron");
        Files.writeString(file, source.replace("\\n", "\n"));
        final List<String> errors = new ArrayList<>();
        final String results = Machine.Helper.evaluate(file.toFile(), error -> errors.add(error.lineNumber() + ": " + error.parseException()))
                .map(Obj::toString)
                .collect(Collectors.joining(","));
        assertEquals(List.of(), errors, description + ": no statement may fail to parse");
        assertEquals(expected, results, description);
    }
}
