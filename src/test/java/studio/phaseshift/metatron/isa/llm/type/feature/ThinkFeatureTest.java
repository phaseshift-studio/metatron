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

package studio.phaseshift.metatron.isa.llm.type.feature;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Str;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.Str.pendingTemplateTail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ThinkFeatureTest extends AbstractMetatronTest {

    @Test
    public void testPendingTemplateTail() {
        // Complete text or text with only complete templates: nothing held.
        assertEquals("", pendingTemplateTail("no templates here"));
        assertEquals("", pendingTemplateTail("complete {{{expr}}} done"));
        assertEquals("", pendingTemplateTail("complete {{{a}}} and ${b} done"));
        assertEquals("", pendingTemplateTail(""));
        // Chunk ends mid-{{{...}}}: hold the unclosed opener and its content.
        assertEquals("{{{x", pendingTemplateTail("partial {{{x"));
        assertEquals("{{{", pendingTemplateTail("just an opener {{{"));
        // A template completing mid-text, then a new partial: hold the partial.
        assertEquals("{{{y", pendingTemplateTail("abc {{{x}}} then {{{y"));
        assertEquals("${y", pendingTemplateTail("abc {{{x}}} then ${y"));
        // Trailing { / $ that could begin an opener in the next chunk.
        assertEquals("{", pendingTemplateTail("ends with {"));
        assertEquals("{{", pendingTemplateTail("ends with {{"));
        assertEquals("$", pendingTemplateTail("ends with $"));
        // The complete template is released; the trailing partial is held.
        assertEquals("${x", pendingTemplateTail("partial ${x"));
        // Nested templates: hold from the innermost unclosed opener.
        assertEquals("{{{x", pendingTemplateTail("nested {{{ {{{x"));
        assertEquals("", pendingTemplateTail("complete {{{ {{{x}}} }}} done"));
        assertEquals("{{{y", pendingTemplateTail("abc {{{x}}} then {{{ {{{y"));
    }
}
