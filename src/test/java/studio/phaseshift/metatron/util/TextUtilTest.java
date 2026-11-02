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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Regression guard for the stop word sets.  The LARGE set (and, transitively,
 * ALL, which ConceptFeature uses at construction) loads
 * terrier_stop_word_list_en.txt from the classpath — that resource used to be
 * silently dropped from the uber-jar (the assembly only shipped star-star/slash/star-dot-class),
 * which made concept_feature fail at boot with "not found on classpath".
 */
public class TextUtilTest extends AbstractMetatronTest {

    @BeforeAll
    static void setUp() {
        AbstractMetatronTest.begin();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "SMALL",
            "MEDIUM",
            "LARGE",
            "ALL",
    }, delimiter = '%')
    public void testStopWordSetsLoad(final String setName) {
        final TextUtil.StopWordSet set = TextUtil.StopWordSet.valueOf(setName);
        final Set<String> words = assertDoesNotThrow(() -> TextUtil.getStopWords(set),
                "stop word set " + set + " must load (resource on classpath)");
        assertFalse(words.isEmpty(), "stop word set " + set + " loaded empty");
        if (set == TextUtil.StopWordSet.ALL)
            assertTrue(words.containsAll(TextUtil.getStopWords(TextUtil.StopWordSet.LARGE)),
                    "ALL must include the LARGE (terrier) words");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "the cat and the mat",
            "a and of but the",
    }, delimiter = '%')
    public void testStripStopwords(final String text) {
        // stop words (the, and, a, of, but) are in SMALL and LARGE — stripped
        final String stripped = TextUtil.stripStopwords(TextUtil.StopWordSet.LARGE, text);
        assertFalse(stripped.contains(" the "), "stopword 'the' should be stripped from: " + text);
    }
}
