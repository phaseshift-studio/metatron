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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace.FS_SPACE_TYPE;

/**
 * The agent IDE instset: {@code cs_project::T} (the project descriptor) and {@code cs_result::T}
 * (the standardized outcome), plus the {@code cs_command} wrapper that turns a command into the
 * enriched instruction — which runs it through {@link CommandRunner}, applies the user's {@code to}
 * conduit per output line, and returns {@code cs_result::T}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ideInstSetTest extends AbstractMetatronTest {

    protected static final GraphittyLogger LOG = Graphitty.log(ideInstSetTest.class);

    // lives under the build dir — /tmp is not writable in every sandbox; target/ is
    private static final Path SEARCH_ROOT = Path.of(System.getProperty("user.dir"), "target", "ide_instset_search");

    @BeforeAll
    static void loadInstSets() throws IOException {
        InstSet.importInstSet(IDE_ISA_TID, f("ide"));
        createSearchTree();
        final fsSpace space = FS_SPACE_TYPE.constructor().asInst().args(lst(rec(
                uri(PATTERN), uri("isearch:#"),
                uri(ROUTE), rec(uri("isearch:"), uri(SEARCH_ROOT.toString()))).vid(f("/sys/space/isearch")))).apply(noobj()).as();
        Router.global().addSpace(space);
    }

    @AfterAll
    static void cleanSearchTree() {
        if (Files.exists(SEARCH_ROOT))
            CommonUtil.deleteDirectory(SEARCH_ROOT);
    }

    /**
     * Deterministic tree for the project search inst:
     * <pre>
     * /tmp/ide_instset_search/
     *   pom.xml
     *   src/
     *     main/
     *       java/
     *         com/
     *           x/
     *             Greeter.java
     *             Calculator.java
     * </pre>
     */
    private static void createSearchTree() throws IOException {
        if (Files.exists(SEARCH_ROOT))
            CommonUtil.deleteDirectory(SEARCH_ROOT);
        Files.createDirectories(SEARCH_ROOT.resolve("src/main/java/com/x"));
        Files.writeString(SEARCH_ROOT.resolve("src/main/java/com/x/Greeter.java"), "public class Greeter {}");
        Files.writeString(SEARCH_ROOT.resolve("src/main/java/com/x/Calculator.java"), "public class Calculator {}");
        Files.writeString(SEARCH_ROOT.resolve("pom.xml"), "<project/>");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsProjectType() {
        // a valid project rec: root required, build/test palettes are rec-of-inst
        final Obj project = rec(uri("root"), uri("fs:/foo"),
                uri("build"), rec(uri("compile"), command()));
        assertTrue(project.test(ideInstSet.IDE_PROJECT_TYPE), "a project rec with root must satisfy cs_project::T");
        assertEquals(IDE_PROJECT_TID, project.tid(IDE_PROJECT_TID).tid());
        // a project rec missing root fails the predicate
        final Obj missingRoot = rec(uri("build"), rec(uri("compile"), command()));
        assertTrue(!missingRoot.test(ideInstSet.IDE_PROJECT_TYPE), "a project rec without root must fail cs_project::T");
    }

    @Test
    void testCsResultType() {
        final Obj result = CommandRunner.run("echo hi", noobj());
        assertTrue(result.isRec(), "the runner must emit a cs_result::T rec");
        assertEquals(IDE_RESULT_TID, result.tid());
        assertTrue(result.test(ideInstSet.IDE_RESULT_TYPE), "a runner result must satisfy cs_result::T");
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        // output is a !* auto_from ref — atDirect bypasses auto_resolve; dereferencing
        // materializes the line-stream
        final Obj output = result.asRec().atDirect(uri("output"));
        assertTrue(output.isInst(), "output must be a lazy !* ref, not the materialized stream");
        assertEquals("hi", output.apply(noobj()).strValue());
        assertTrue(result.asRec().has(uri("runtime")), "runtime is a required field");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsCommandWraps() {
        final Obj wrapper = Router.readFromSpace(IDE_COMMAND_TID);
        assertTrue(wrapper.isInst(), "cs_command must be a registered inst");
        final Obj enriched = wrapper.asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        assertTrue(enriched.isInst(), "cs_command(command=>...) must return an enriched instruction");
    }

    @Test
    void testEnrichedRunsCommand() {
        final Obj enriched = Router.readFromSpace(IDE_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        final Obj result = enriched.asInst().apply(noobj());
        assertTrue(result.isRec(), "the enriched instruction must return a cs_result::T rec");
        assertEquals(IDE_RESULT_TID, result.tid());
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        assertEquals("hi", result.asRec().atDirect(uri("output")).apply(noobj()).strValue());
    }

    @Test
    void testEnrichedToStreaming() {
        final List<String> collected = new ArrayList<>();
        final Inst to = instC(f("/m/ide/test/to").dom(STR_TID).rng(ALL.maybe()), lst(),
                (lhs, inst) -> {
                    collected.add(lhs.strValue());
                    return lhs;
                });
        final Obj enriched = Router.readFromSpace(IDE_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("echo hi"))).apply(noobj());
        // the to conduit is rec-wrapped so the arg machinery keeps it as data
        final Obj result = enriched.asInst().args(rec(uri("to"), rec(uri("code"), to))).apply(noobj());
        assertEquals(uri("success"), result.asRec().at(uri("status")));
        assertEquals(List.of("hi"), collected, "the to conduit must receive each output line");
    }

    @Test
    void testEnrichedFailsOnBadCommand() {
        final Obj enriched = Router.readFromSpace(IDE_COMMAND_TID)
                .asInst().args(rec(uri("command"), str("definitely-not-a-command-xyz"))).apply(noobj());
        final Obj result = enriched.asInst().apply(noobj());
        assertTrue(result.isRec(), "a failed command must still emit a cs_result::T rec --- %s".formatted(result));
        assertEquals(uri(ERROR), result.asRec().at(uri(STATUS)));
        // the command runs through sh — an unknown command is the shell's problem: it reports
        // the diagnostic on (merged) stdout and exits non-zero, so the rec carries no java
        // fail but must carry the shell's "not found" line in output
        final Obj output = result.asRec().at(uri("output"));
        final String lines = output.apply(noobj()).strValue();
        assertTrue(lines.contains("not found"),
                "the shell's diagnostic must be captured in output — %s".formatted(lines));
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void testCsCommandDocs() {
        final Obj doc = Router.readFromSpace(IDE_COMMAND_TID.addQ(DOCQ));
        assertTrue(doc.isRec(), "cs_command must carry documentation");
        assertTrue(doc.asRec().at(uri("desc")).strValue().contains("wrap"),
                "the cs_command docs must describe the wrapper");
    }

    @Test
    void testIdeCommandViaPrefix() {
        // drstynx.boot.mtron does import(/m/ide, ide) — the second arg is the namespace prefix used
        // to disambiguate short names across instsets. `ide:command` must resolve to the command inst
        // the same way the bare `command` redirect does (insts live under /m/ide/inst/).
        Router.global().registerPrefix(f("ide"), f("/m/ide"));
        final Obj viaPrefix = Router.readFromSpace(f("ide:command"));
        assertTrue(viaPrefix.isInst(), "ide:command must resolve to the command inst — %s".formatted(viaPrefix));
        assertEquals(Router.readFromSpace(IDE_COMMAND_TID), viaPrefix,
                "ide:command must equal the command inst at /m/ide/inst/command");
    }

    @Test
    void testProjectFindFile() {
        // ide:find — the project tree searched (repeat >> until has|isa) for a uri fragment;
        // yields the location of the found resource
        final Obj search = Router.readFromSpace(IDE_INST_TID.extend("find"));
        assertTrue(search.isInst(), "ide:find must be a registered inst");
        final Obj project = rec(uri(ROOT), uri("isearch:")).tid(IDE_PROJECT_TID);
        final Obj found = search.asInst().args(lst(uri("Greeter.java"))).apply(project);
        assertTrue(found.isUri(), "search must yield the location of the found resource — %s".formatted(found));
        assertTrue(found.uriValue().toString().contains("Greeter.java"),
                "the found location must identify the searched file — %s".formatted(found));
    }

    @Test
    void testProjectSearchNotFound() {
        // a fragment that is not in the tree must not fabricate a location
        final Obj search = Router.readFromSpace(IDE_INST_TID.extend("search"));
        final Obj project = rec(uri(ROOT), uri("isearch:")).tid(IDE_PROJECT_TID);
        final Obj found = search.asInst().args(lst(uri("NoSuchFile.java"))).apply(project);
        assertTrue(!found.isUri() || !found.uriValue().toString().contains("NoSuchFile.java"),
                "a missing file must not resolve to a location — %s".formatted(found));
    }

    @Test
    void testProjectAsSkillView() {
        // as?skill<=project() — the project projects onto the skill contract:
        // name/desc/content/tool pass through, and code is viewed as resource
        final Obj as = Router.readFromSpace(AS_INST_TID.dom(IDE_PROJECT_TID).rng(LLM_SKILL_TID));
        assertTrue(as.isInst(), "as?skill<=project() must be a registered inst");
        final Obj project = rec(uri(ROOT), uri("isearch:"),
                uri(NAME), str("scratch"),
                uri(DESC), str("an example java/mvn project"),
                uri(CONTENT), str("# scratch serves as an example"),
                uri(TOOL), lst(str("mvn_build")),
                uri(CODE), lst(uri("isearch:src/main/java/com/x/Greeter.java"))).tid(IDE_PROJECT_TID);
        final Obj skill = as.asInst().args(lst(ALL_TYPE)).apply(project);
        assertTrue(skill.toString().contains("scratch") && skill.toString().contains("an example java/mvn project"),
                "name and desc must pass through — %s".formatted(skill));
        // assertTrue(skill.toString().contains("resource") && skill.toString().contains("Greeter.java"),
        //         "the project code must be viewed as the skill resource — %s".formatted(skill));
    }

    @Test
    void testProjectAsSkillViewWithRefCode() {
        // the live shape — a code list of !* references (not plain uris):
        // the view must extract the file uri and keep the reference as lazy text
        final Obj as = Router.readFromSpace(AS_INST_TID.dom(IDE_PROJECT_TID).rng(LLM_SKILL_TID));
        final Obj project = rec(uri(ROOT), uri("isearch:"),
                uri(NAME), str("scratch"),
                uri(CODE), lst((Obj) auto_from_(f("isearch:src/main/java/com/x/Greeter.java")))).tid(IDE_PROJECT_TID);
        final Obj skill = as.asInst().args(lst(ALL_TYPE)).apply(project);
        assertTrue(skill.toString().contains("isearch:src/main/java/com/x/Greeter.java"),
                "the file uri must survive the view — %s".formatted(skill));
        assertTrue(skill.toString().contains("resource"), "the code must be viewed as the resource — %s".formatted(skill));
    }

    @Test
    void testSkillToProjectViewAbsent() {
        // the bridge is one-way: a skill has no buildable workspace behind it —
        // the reverse as is not registered (noobj reads back, and noobj is polymorphic,
        // so compare identity rather than isInst)
        final Obj back = Router.readFromSpace(AS_INST_TID.dom(LLM_SKILL_TID).rng(IDE_PROJECT_TID));
        assertEquals(noobj(), back, "skill -> project must not be a registered as-view — %s".formatted(back));
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    private static Inst command() {
        return instC(f("/m/ide/test/cmd").dom(ALL.maybe()).rng(IDE_RESULT_TID), lst(),
                (lhs, inst) -> noobj());
    }
}
