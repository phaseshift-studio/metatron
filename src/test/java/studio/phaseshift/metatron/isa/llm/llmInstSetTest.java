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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.FileSystems;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

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
        InstSet.importInstSet(WEB_ISA_TID);
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
        assertFalse(result.isFail());
        assertFalse(result.isNoObj());
        assertTrue(result.test(LLM_SKILL_TYPE));
        assertTrue(result.type().test(LLM_SKILL_TYPE));
        assertEquals(LLM_SKILL_TID, result.type().vid());
        assertNotNull(result.tid());
        Obj expected = ObjmtronSerializer.parse(expectedType).apply();
        LOG.info("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "the test reason alpha",
            "the test reason beta",
    }, delimiter = '%')
    public void testAgentFeatureFailureNamesTheEntry(final String reason) {
        // a feature entry that failed to construct must surface its reason with the
        // entry number — a bare ClassCastException (MFail not castable to Rec) used
        // to swallow the original failure
        final Map<Obj, Obj> jvm = new ConcurrentHashMap<>();
        jvm.put(uri(FEATURE), lst(noobj(), MFail.fail(reason)));
        final MTronException e = assertThrows(MTronException.class,
                () -> new Agent(jvm, LLM_AGENT_TID, null), "expected a clean failure diagnostic");
        // the original failure reason must be visible in the diagnostic — a bare
        // ClassCastException (MFail not castable to Rec) used to swallow it
        assertTrue(e.getMessage().contains("agent"), "got: " + e.getMessage());
        assertTrue(e.getMessage().contains(reason), "got: " + e.getMessage());

        // second line of defense: a fail that reaches a rec cast names itself
        final Obj failValue = MFail.fail("the asrec reason gamma");
        final MTronException r = assertThrows(MTronException.class, failValue::asRec, "expected a clean asRec diagnostic");
        //assertTrue(r.getMessage().contains("asRec onto a fail"), "got: " + r.getMessage());
        // assertTrue(r.getMessage().contains("the asrec reason gamma"), "got: " + r.getMessage());
    }
}
