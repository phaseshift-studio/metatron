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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * {@link Memory#mostSpecific(Rec, fURI)} — the sort order that makes space selection deterministic.
 * <p>
 * A scheme-less catch-all ({@code #}) matches <em>every</em> address via the {@code #}-path rule, so
 * {@code mostSpecific} must rank it below any pattern that shares the address's scheme. The pinned order is:
 * <ol>
 *   <li>a pattern carrying a scheme outranks one without (so {@code http://#} beats {@code #} for
 *       {@code http://…}, and {@code db:#} beats {@code #} for {@code db:…});</li>
 *   <li>among otherwise-equal patterns, the more concrete path wins ({@code /m/#} beats {@code #});</li>
 *   <li>the catch-all {@code #} is the fallback when nothing else covers the address.</li>
 * </ol>
 */
public class MemoryTest extends AbstractMetatronTest {

    private static Rec spaces;

    @BeforeAll
    public static void setupSpaces() {
        spaces = rec(
                uri("#"), memSpace.of(f("#"), null),
                uri("http://#"), memSpace.of(f("http://#"), null),
                uri("db:#"), memSpace.of(f("db:#"), null),
                uri("/m/#"), memSpace.of(f("/m/#"), null));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://localhost:8555/index.html % http://#",
            "http://localhost:8555 % http://#",
            "db:users/1 % db:#",
            "/m/inst/plus % /m/#",
            "ftp://example.com/file % #",
    }, delimiter = '%')
    void testMostSpecific(final String vidStr, final String expectedPattern) {
        final Space space = Memory.mostSpecific(spaces, f(vidStr));
        assertNotNull(space, "mostSpecific(" + vidStr + ") should resolve a space");
        assertEquals(expectedPattern, space.pattern().toString(), "mostSpecific(" + vidStr + ")");
    }
}
