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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code {{link}}}...{@code {{/link}}} turns its text into an active link: text the furi
 * parser accepts renders as an OSC 8 hyperlink and an underline — clickable where a
 * terminal honors hyperlinks, legible as a link where one does not.  Text that is not a uri
 * is never rendered as a link: a dead link is worse than none.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GraphittyLinkTest extends AbstractMetatronTest {

    /**
     * Exactly the bytes a link renders as: the OSC 8 open carrying the uri as its target,
     * the underline, the text, the hyperlink close, and the restore that follows —
     * {@code \033[m} when no rule encloses the link, or the enclosing rule with its
     * underline dropped when one does.
     */
    private static String render(final String uri, final String enclosing) {
        final String reset = enclosing.isEmpty()
                ? "\033[m"
                : enclosing.replace("\033[", "\033[0;");
        return "\033]8;;" + uri + "\u0007\033[4m" + uri + "\033]8;;\u0007" + reset;
    }

    private static String render(final String uri) {
        return render(uri, "");
    }

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a                          % the uri of a space object",
            "/sys/thread/main                   % the uri of a thread",
            "/usr/dr/message/+                  % a wildcard read over a collection",
            "http://localhost:8777/hook         % a web uri carrying host and port",
            "http://localhost:8777/hook?a=1&b=2 % a web uri carrying query parameters"})
    public void testGoodLinkRendersAsHyperlink(final String uri, final String desc) {
        assertEquals(render(uri), Graphitty.string("{{link}}" + uri + "{{/link}}"), desc);
    }

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "a&b       % a bare ampersand defeats the furi parser",
            "a&&b      % a doubled ampersand defeats the furi parser"})
    public void testBadLinkRendersAsPlainText(final String text, final String desc) {
        assertEquals(text, Graphitty.string("{{link}}" + text + "{{/link}}"), desc);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "/m/obj/a % true  % a good link left open still commits",
            "a&b      % false % a bad link left open stays plain"},
            quoteCharacter = '"', delimiter = '%')
    public void testUnclosedLinkStillCommits(final String uri, final boolean good, final String desc) {
        assertEquals(good ? render(uri) : uri, Graphitty.string("{{link}}" + uri), desc);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "/m/obj/a % 8 % the link is measured as its uri",
            "a&b      % 3 % a bad link is measured as its text"},
            quoteCharacter = '"', delimiter = '%')
    public void testMeasureSeesPlainLinkText(final String uri, final int columns, final String desc) {
        assertEquals(uri, Graphitty.strip("{{link}}" + uri + "{{/link}}"), desc);
        assertEquals(columns, Graphitty.viewLength("{{link}}" + uri + "{{/link}}"), desc);
    }

    /**
     * A link inside a color leaves the color running after it — the same restore every
     * {@code {{/rule}}} performs — while its underline does not leak; the close of the color
     * itself then resets, as every unenclosed close does.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a % under the cyan"})
    public void testLinkInsideColorKeepsColorRunning(final String uri, final String desc) {
        assertEquals("\033[36m" + render(uri, "\033[36m") + "\033[m",
                Graphitty.string("{{c}}{{link}}" + uri + "{{/link}}{{/c}}"), desc);
    }

    /**
     * A rule between the tags ends the link there: the text captured so far is a link unto
     * itself, and the rule applies to whatever follows it.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a % a rule between the tags closes the link"})
    public void testARuleMidLinkCommitsIt(final String uri, final String desc) {
        assertEquals(render(uri) + "\033[36mx\033[m",
                Graphitty.string("{{link}}" + uri + "{{c}}x{{/c}}"), desc);
    }
}
