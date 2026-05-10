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

package studio.phaseshift.metatron.isa.llm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.nio.file.FileSystems;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class llmInstSetTest extends AbstractInstSetTest {

    public llmInstSetTest() {
        super(llmInstSet::new);
    }

    @BeforeAll
    public static void loadFileSystem() {
        InstSet.importInstSet(MACH_ISA_TID);
        fsSpace.of(FileSystems.getDefault(), rec(
                uri(PATTERN), uri("local:#"),
                uri(ROUTE), rec(uri("local:"), uri("src/test/resources/isa/sys/space/llm/"))
        ), f("/sys/space/fs"));
    }
    
    @AfterAll
    public static void unloadFileSystem() {
        Router.global().removeSpace(f("/sys/space/fs"));
    }
    
    @ParameterizedTest
    @CsvSource(value = {
            "*<local:skills/mtron>.as(skill::T) | *skill | true",
    }, delimiter = '|')
    public void testAs(final String noNoObjCode, final String expectedType, final boolean shouldMatch) {
        Obj result = ObjmtronSerializer.parse(noNoObjCode).apply();
        assertTrue(result.test(LLM_SKILL_TYPE));
        assertTrue(result.type().test(LLM_SKILL_TYPE));
        assertNotNull(result.tid());
        Obj expected = ObjmtronSerializer.parse(expectedType).apply();
        LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }
    
    @Disabled
    @ParameterizedTest
    @CsvSource(value = {
            "abc->model::[ | *skill | true",
    }, delimiter = '|')
    public void testChatResolution(final String noNoObjCode, final String expectedType, final boolean shouldMatch) {
        Obj result = ObjmtronSerializer.parse(noNoObjCode).apply();
        assertTrue(result.test(LLM_SKILL_TYPE));
        assertTrue(result.type().test(LLM_SKILL_TYPE));
        assertNotNull(result.tid());
        Obj expected = ObjmtronSerializer.parse(expectedType).apply();
        LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }
}
