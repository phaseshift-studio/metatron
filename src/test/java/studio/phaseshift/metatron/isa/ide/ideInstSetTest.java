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

package studio.phaseshift.metatron.isa.ide;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.CS_COMMAND_TID;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.CS_PROJECT_TID;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.CS_RESULT_TID;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.IDE_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The agent IDE instset: {@code cs_project::T} (the project descriptor) and {@code cs_result::T}
 * (the standardized outcome), plus the {@code cs_command} wrapper that turns a command into the
 * enriched instruction — which runs it through {@link csRunner}, applies the user's {@code to}
 * conduit per output line, and returns {@code cs_result::T}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ideInstSetTest extends AbstractMetatronTest {

    protected static final GraphittyLogger LOG = Graphitty.log(ideInstSetTest.class);

    @BeforeAll
    static void loadInstSets() {
        InstSet.importInstSet(IDE_ISA_TID);
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsProjectType() {
        // a valid project rec: root required, build/test palettes are rec-of-inst
        final Obj project = rec(uri("root"), uri("fs:/foo"),
                uri("build"), rec(uri("compile"), command()));
        assertTrue(project.test(ideInstSet.CS_PROJECT_TYPE), "a project rec with root must satisfy cs_project::T");
        assertEquals(CS_PROJECT_TID, project.tid(CS_PROJECT_TID).tid());
        // a project rec missing root fails the predicate
        final Obj missingRoot = rec(uri("build"), rec(uri("compile"), command()));
        assertTrue(!missingRoot.test(ideInstSet.CS_PROJECT_TYPE), "a project rec without root must fail cs_project::T");
    }

    @Test
    void testCsResultType() {
        final Obj result = csRunner.run("echo hi", noobj());
        assertTrue(result.isRec(), "the runner must emit a cs_result::T rec");
        assertEquals(CS_RESULT_TID, result.tid());
        assertTrue(result.test(ideInstSet.CS_RESULT_TYPE), "a runner result must satisfy cs_result::T");
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        assertEquals("hi", result.asRec().at(uri("output")).strValue());
        assertTrue(result.asRec().has(uri("runtime")), "runtime is a required field");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsCommandWraps() {
        final Obj wrapper = Router.readFromSpace(CS_COMMAND_TID);
        assertTrue(wrapper.isInst(), "cs_command must be a registered inst");
        final Obj enriched = wrapper.asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        assertTrue(enriched.isInst(), "cs_command(command=>...) must return an enriched instruction");
    }

    @Test
    void testEnrichedRunsCommand() {
        final Obj enriched = Router.readFromSpace(CS_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        final Obj result = enriched.asInst().apply(noobj());
        assertTrue(result.isRec(), "the enriched instruction must return a cs_result::T rec");
        assertEquals(CS_RESULT_TID, result.tid());
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        assertEquals("hi", result.asRec().at(uri("output")).strValue());
    }

    @Test
    void testEnrichedToStreaming() {
        final List<String> collected = new ArrayList<>();
        final Inst to = instC(f("/m/ide/test/to").dom(STR_TID).rng(ALL.maybe()), lst(),
                (lhs, inst) -> {
                    collected.add(lhs.strValue());
                    return lhs;
                });
        final Obj enriched = Router.readFromSpace(CS_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        // the to conduit is rec-wrapped so the arg machinery keeps it as data
        final Obj result = enriched.asInst().args(rec(uri("to"), rec(uri("code"), to))).apply(noobj());
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        assertEquals(List.of("hi"), collected, "the to conduit must receive each output line");
    }

    @Test
    void testEnrichedFailsOnBadCommand() {
        final Obj enriched = Router.readFromSpace(CS_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("definitely-not-a-command-xyz"))).apply(noobj());
        final Obj result = enriched.asInst().apply(noobj());
        assertTrue(result.isRec(), "a failed command must still emit a cs_result::T rec");
        assertEquals(uri("failure"), result.asRec().at(uri("status")));
        final Obj fails = result.asRec().at(uri("fails"));
        assertTrue(fails.isLst() && !fails.asLst().elements().toList().isEmpty(),
                "a failed command must collect the exception in fails");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsCommandDocs() {
        final Obj doc = Router.readFromSpace(CS_COMMAND_TID.addQ(DOCQ));
        assertTrue(doc.isRec(), "cs_command must carry documentation");
        assertTrue(doc.asRec().at(uri("desc")).strValue().contains("wrap"),
                "the cs_command docs must describe the wrapper");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    private static Inst command() {
        return instC(f("/m/ide/test/cmd").dom(ALL.maybe()).rng(CS_RESULT_TID), lst(),
                (lhs, inst) -> noobj());
    }
}
