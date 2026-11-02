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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.RSHIFT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPLIT_INST_TID;

/**
 * A SUGAR IS REGISTERED ONCE. An instruction set's sugars are registered every time it is constructed or mounted —
 * every boot re-imports /m, and a test fixture builds an ISA per row — and each registration generated a fresh parser,
 * kept in a static set that compared parsers by identity, so it grew for as long as the JVM lived. Registering a sugar
 * that is already registered (the same tokens, position, inst chain and arity) is now a no-op.
 */
public class SugarRegistrationTest extends AbstractMetatronTest {

    /**
     * sugars spelled exactly as {@code mInstSet} declares them, so re-registering them is re-registering /m's own
     */
    private static List<Sugar> declaredByM() {
        return List.of(
                Sugar.prefix(">>", List.of(RSHIFT_INST_TID), 1),
                Sugar.prefix(">>", List.of(RSHIFT_INST_TID), 0),
                Sugar.prefix("+", List.of(PLUS_INST_TID), 1),
                Sugar.prefix("-<", List.of(SPLIT_INST_TID), 1));
    }

    @Test
    public void testReRegisteringASugarAddsNoParser() {
        final int registered = mParser.addSugar(declaredByM().getFirst()).size();
        for (int round = 0; round < 3; round++)
            declaredByM().forEach(mParser::addSugar);
        assertEquals(registered, mParser.addSugar(declaredByM().getFirst()).size(),
                "registering /m's sugars again, three times over, adds no parser");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "[tally=>1,score=>2]>>score  % 2",
            "5+3                        % 8",
            "[tally=>1,score=>2]>>tally % 1"})
    public void testSugarsStillParseAfterReRegistration(final String code, final String expected) {
        declaredByM().forEach(mParser::addSugar);
        checkCodeParseApply(LOG, code, expected);
    }
}
