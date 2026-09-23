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
 * parser accepts renders as an OSC 8 hyperlink — clickable where a terminal honors
 * hyperlinks.  The underline a link is drawn with is a visual affordance of its own: the
 * console's {@code :links} setting turns it on and off (off is the default), and a link
 * drawn without the underline is still a link a click resolves.  What never happens is a
 * dead link: text that is not a uri, and a uri the console has made unclickable, are
 * drawn as the text they are — a dead link is worse than none.
 *
 * <p>The underline is process-wide state, so every row that asserts it sets the setting it
 * expects and restores the one it found — inheriting the flag from whatever test ran
 * before is how a test like this one silently stops testing what it claims to.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GraphittyLinkTest extends AbstractMetatronTest {

    /**
     * Exactly the bytes a link renders as: the OSC 8 open carrying the uri as its target,
     * the underline when it is on, the text, the hyperlink close, and the restore that
     * follows — {@code \033[m} when no rule encloses the link, or the enclosing rule with
     * its underline dropped when one does.
     */
    private static String render(final String uri, final String enclosing, final boolean underline) {
        final String reset = enclosing.isEmpty()
                ? "\033[m"
                : enclosing.replace("\033[", "\033[0;");
        return "\033]8;;" + uri + "\u0007" + (underline ? "\033[4m" : "") + uri + "\033]8;;\u0007" + reset;
    }

    /**
     * The bytes a text span renders as with no link: the underline when it is on (a
     * decoration in its own right), the text when it is not.
     */
    private static String plain(final String text, final boolean underline) {
        return underline ? "\033[4m" + text + "\033[m" : text;
    }

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a                          % true  % the uri of a space object, underlined",
            "/m/obj/a                          % false % the uri of a space object, the default visual",
            "/sys/thread/main                   % true  % the uri of a thread, underlined",
            "/sys/thread/main                   % false % the uri of a thread, the default visual",
            "/usr/dr/message/+                  % true  % a wildcard read over a collection, underlined",
            "/usr/dr/message/+                  % false % a wildcard read over a collection, the default visual",
            "http://localhost:8777/hook         % true  % a web uri carrying host and port, underlined",
            "http://localhost:8777/hook         % false % a web uri carrying host and port, the default visual",
            "http://localhost:8777/hook?a=1&b=2 % true  % a web uri carrying query parameters, underlined",
            "http://localhost:8777/hook?a=1&b=2 % false % a web uri carrying query parameters, the default visual"})
    public void testGoodLinkRendersAsHyperlink(final String uri, final boolean underline, final String desc) {
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(underline);
        try {
            assertEquals(render(uri, "", underline), Graphitty.string("{{link}}" + uri + "{{/link}}"), desc);
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "a&b       % true  % a bare ampersand defeats the furi parser, underlined as decoration",
            "a&b       % false % a bare ampersand defeats the furi parser, drawn as its own text",
            "a&&b      % true  % a doubled ampersand defeats the furi parser, underlined as decoration",
            "a&&b      % false % a doubled ampersand defeats the furi parser, drawn as its own text"})
    public void testBadLinkRendersAsPlainText(final String text, final boolean underline, final String desc) {
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(underline);
        try {
            assertEquals(plain(text, underline), Graphitty.string("{{link}}" + text + "{{/link}}"), desc);
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a % true  % a good link left open still commits, underlined",
            "/m/obj/a % false % a good link left open still commits, the default visual",
            "a&b      % true  % a bad link left open stays plain, underlined as decoration",
            "a&b      % false % a bad link left open stays plain, the default visual"})
    public void testUnclosedLinkStillCommits(final String uri, final boolean underline, final String desc) {
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(underline);
        try {
            assertEquals(Graphitty.linkable(uri) ? render(uri, "", underline) : plain(uri, underline),
                    Graphitty.string("{{link}}" + uri), desc);
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    /**
     * Measuring a link sees the text a reader sees: the underline setting is a rendering
     * courtesy and must not change what strip() or viewLength() report — the same reason the
     * rule machinery only runs at all when ansi is on.  No flag is set here on purpose.
     */
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
            "/m/obj/a % true  % under the cyan, underlined",
            "/m/obj/a % false % under the cyan, the default visual"})
    public void testLinkInsideColorKeepsColorRunning(final String uri, final boolean underline, final String desc) {
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(underline);
        try {
            assertEquals("\033[36m" + render(uri, "\033[36m", underline) + "\033[m",
                    Graphitty.string("{{c}}{{link}}" + uri + "{{/link}}{{/c}}"), desc);
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    /**
     * A rule between the tags ends the link there: the text captured so far is a link unto
     * itself, and the rule applies to whatever follows it.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a % true  % a rule between the tags closes the link, underlined",
            "/m/obj/a % false % a rule between the tags closes the link, the default visual"})
    public void testARuleMidLinkCommitsIt(final String uri, final boolean underline, final String desc) {
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(underline);
        try {
            assertEquals(render(uri, "", underline) + "\033[36mx\033[m",
                    Graphitty.string("{{link}}" + uri + "{{c}}x{{/c}}"), desc);
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    /**
     * Clicking off ({@code :link off} and friends): the uri is drawn as the text it is — no
     * OSC 8 span for a click to resolve — and the underline, when on, is all the eye gets.
     * The setting is checked where the link is built AND where a click is resolved, so the
     * two can never disagree.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "/m/obj/a % true  % clicking off, the underline is the eye's only cue",
            "/m/obj/a % false % clicking off, the uri is the text it is"})
    public void testUnclickableLinkDrawsText(final String uri, final boolean underline, final String desc) {
        final boolean previousClickable = Graphitty.linkClickable();
        final boolean previousUnderline = Graphitty.linkUnderline();
        Graphitty.linkClickable(false);
        Graphitty.linkUnderline(underline);
        try {
            assertEquals(plain(uri, underline), Graphitty.string("{{link}}" + uri + "{{/link}}"), desc);
        } finally {
            Graphitty.linkClickable(previousClickable);
            Graphitty.linkUnderline(previousUnderline);
        }
    }
}
