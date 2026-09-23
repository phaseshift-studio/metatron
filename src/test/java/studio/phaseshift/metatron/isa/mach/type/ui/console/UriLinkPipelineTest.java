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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every uri the console serializer writes reaches the screen as a clickable link, which means the
 * tag it wraps the uri in has to survive the console's highlight step: the result path is
 * {@code Graphitty.string(Highlighter.format(serializer.write(obj)))}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UriLinkPipelineTest extends AbstractMetatronTest {

    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "{{link}}/usr/dr/message/+{{/link}}      % the tag the serializer writes",
            "<{{link}}/usr/dr/message/+{{/link}}>    % and the quoted form it writes for a quoted uri",
            "==>{{link}}/sys/thread/main{{/link}}    % and a tag after a result prefix",
    })
    void testTheTagSurvivesHighlightingAndBecomesAHyperlink(final String highlightedInput, final String description) {
        // the underline is part of what this test pins, so it is set explicitly: the flag is a
        // console-wide setting (:links) that defaults off, and inheriting it from whatever test
        // ran first is how this assertion passed once and would silently stop testing what it
        // claims to
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(true);
        try {
            final String rendered = Graphitty.string(Highlighter.format(highlightedInput));
            assertEquals(true, rendered.contains("\033]8;;" + (highlightedInput.contains("/usr") ? "/usr/dr/message/+" : "/sys/thread/main")),
                    description + ": the tag must survive the highlighter (rendered: "
                            + rendered.replace("\033", "<ESC>") + ")");
            assertEquals(true, rendered.contains("\033[4m"), description + ": and be underlined");
        } finally {
            Graphitty.linkUnderline(previous);
        }
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "/usr/dr/message/+ % a wildcard read",
            "/sys/thread/main  % a thread",
    }, delimiter = '%')
    void testTheLinkCanBeTurnedBackIntoTypedText(final String uri, final String description) {
        // the underline is the visual affordance and it is optional; the uri itself — what a click
        // needs — is not
        final boolean previous = Graphitty.linkUnderline();
        Graphitty.linkUnderline(false);
        try {
            final String plain = Graphitty.string("{{link}}" + uri + "{{/link}}");
            assertEquals(false, plain.contains("\033[4m"), description + ": no underline when the visual is off");
            assertEquals(true, plain.contains("\033]8;;"), description + ": but still a link to click");
        } finally {
            // restore the setting we found, not a value we assume: leaking on (or off) into the
            // next test class is exactly the state this test was written to make predictable
            Graphitty.linkUnderline(previous);
        }
    }
}
