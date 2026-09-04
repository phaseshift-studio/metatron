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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
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
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
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
    @Disabled
    void testProjectFindFile() {
        // ide:find — the project tree searched (repeat >> until has|isa) for a uri fragment;
        // yields the location of the found resource
        final Obj search = Router.readFromSpace(IDE_INST_TID.extend("find"));
        LOG.warn(search);
        assertTrue(search.isInst(), "ide:find must be a registered inst");
        final Obj project = start_(uri("isearch:")).as_(IDE_PROJECT_TYPE).apply();
        Router.writeToSpace("temp", project);
        LOG.warn(project);
        final Obj found = search.asInst().args(lst(uri("Greeter"))).apply(uri("temp"));
        LOG.warn(found);
        assertTrue(found.isUri(), "search must yield the location of the found resource — %s".formatted(found));
        //   assertTrue(found.uriValue().toString().contains("Greeter.java"),
        //         "the found location must identify the searched file — %s".formatted(found));
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
        assertTrue(skill.toString().contains("scratch"), "name must pass through — %s".formatted(skill));
        assertTrue(skill.toString().contains("an example java/mvn project"), "desc must pass through — %s".formatted(skill));
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

    @Test
    @Disabled
    public void testPullAfterSpaceRoundTrip() throws Exception {
        // the agent-ide pull, end to end, on the CONTRACT shape — a space-rooted
        // project (*<uri>), since the pull stores code/idx beside the root in the
        // SAME space (the root uri is its own space location).  Steps:
        //   1. register a space routing the project's non-reserved scheme (probe:)
        //      against the repo root (fs:/src: are reserved and stripped from the
        //      fURI basePath, so they can never match a space pattern);
        //   2. build the live project from *<probe:...> (as(project::T) strips the
        //      deref's leading slash and walks CWD-relative per its startsWith("src")
        //      contract), store it under pp, re-read;
        //   3. call the stored class inst — the pull reads its own java source
        //      through the probe space, parses it, appends the ide:java rec to the
        //      code list beside the project root, and publishes idx/<class>.
        final java.util.function.Function<String, Obj> run = code -> {
            final Obj cd = ObjmtronSerializer.parse(code);
            return cd.apply(noobj());
        };
        final fsSpace repoSrc = FS_SPACE_TYPE.constructor().asInst().args(lst(rec(
                uri(PATTERN), uri("probe:#"),
                uri(ROUTE), rec(uri("probe:"), uri(System.getProperty("user.dir")))).vid(f("/sys/space/repoProbe")))).apply(noobj()).as();
        Router.global().addSpace(repoSrc);

        final String root = "src/test/resources/scratch";
        final String probeRoot = "probe:" + root;
        final java.nio.file.Path codeFile = java.nio.file.Path.of(System.getProperty("user.dir"), root, "code");
        final java.nio.file.Path idxFile = java.nio.file.Path.of(System.getProperty("user.dir"), root, "idx");
        try {
            run.apply("*<" + probeRoot + ">.as(project::T).to(pp)");

            // acceptance — the pull.  SANCTIONED CALL SHAPE (marko): pp/src is the rec
            // that is the domain of its own Greeter instruction, and the inst is called
            // directly — pp/src/Greeter() — no star in front of the inst name (the
            // star-dot form *pp/src.Greeter() is the other legal shape).
            final Obj pulled = run.apply("pp/src/Greeter()");
            final String pullOut = trunc(pulled, 4000);
            if (pullOut.contains("no space location"))
                throw new AssertionError("pull hit the bare-value contract: " + pullOut);
            if (pullOut.contains("no active space"))
                throw new AssertionError("pull could not route its source read: " + pullOut);
            if (!pulled.isRec())
                throw new AssertionError("the pull should return the pulled class value (an ide:java rec), but failed: " + pullOut);
            if (!pullOut.contains("Greeter"))
                throw new AssertionError("the pulled rec should carry the class, but was: " + pullOut);
            if (!java.nio.file.Files.exists(codeFile))
                throw new AssertionError("the pull did not write the code list beside the project root (missing %s)".formatted(codeFile));
            final String codeContent = java.nio.file.Files.readString(codeFile);
            if (!codeContent.contains("Greeter"))
                throw new AssertionError("the code list beside the project root does not hold the parsed class: " + trunc(codeContent, 400));

            // acceptance — the reprojection.  idx is a SEPARATE inst (ide:index) on the
            // code base — code is the source, idx is a re-slice of it: class => kind =>
            // name => the !@ anchors into the code list elements.
            final Obj indexInst = Router.readFromSpace(IDE_INST_TID.extend("index"));
            if (!indexInst.isInst())
                throw new AssertionError("ide:index must be a registered inst, but was: " + indexInst);
            final Obj idxGen = indexInst.asInst().apply(uri(probeRoot));
            final String idxOut = trunc(idxGen, 4000);
            if (idxOut.contains("no code list"))
                throw new AssertionError("ide:index found no code list to reproject: " + idxOut);
            if (!idxOut.contains("Greeter"))
                throw new AssertionError("the idx reprojection should carry the class, but was: " + idxOut);
            if (!idxOut.contains("/code/0/"))
                throw new AssertionError("the idx anchors should reference the code list elements (code/0/...): " + idxOut);

            // acceptance — the anchor round trip: idx -> anchor -> the stored code value.
            // the first entry (class Greeter, kind field, name GREETING) must resolve to
            // the member rec held by code/0.
            final Obj anchor = idxGen.asRec().at("Greeter").asRec().at("field").asRec().at("GREETING").stream().toList().getFirst();
            final Obj memberAtAnchor = anchor.isUri() ? Router.readFromSpace(anchor.uriValue()) : str("the first idx anchor was not an uri: " + anchor);
            final String memberOut = trunc(memberAtAnchor, 400);
            if (memberOut.contains("no space location") || memberOut.contains("no active space"))
                throw new AssertionError("the idx anchor did not resolve through the space to the code value: " + memberOut);
            if (!memberOut.contains("GREETING"))
                throw new AssertionError("the anchor should land on the GREETING member, but resolved to: " + memberOut);
        } finally {
            // the pull writes code/idx beside the fixture — keep the repo clean
            java.nio.file.Files.deleteIfExists(codeFile);
            java.nio.file.Files.deleteIfExists(idxFile);
        }
    }

    // ── the noobj reserved-word boundary (engine fix a): identifiers that
    //    merely begin with `noobj` must remain parseable — the `noobjSpace`
    //    class key in the drstynx project artifact used to trip the bare
    //    `noobj` literal and NPE the deserializer ─────────────────────────
    @ParameterizedTest
    @CsvSource(value = {
            // the reserved word `noobj` keeps working as a value ...
            "noobj                        % noobj",
            "1.map(noobj)                 % noobj",
            "noobj{,}                     % noobj",
            "[noobj=>1]                   % [=>]",
            "[noobj=>noobj]               % [=>]",
            "[A=>noobj,B=>1]              % [B=>1]",
            "{noobj,noobjX}               % noobjX",
            // noobj-prefixed identifiers are NOT swallowed (the noobjSpace bug) ...
            "[noobjSpace=>1, x=>2]        % [noobjSpace=>1, x=>2]",
            "[noobjx=>1]                  % [noobjx=>1]",
            "[xnoobj=>1]                  % [xnoobj=>1]",
            "[noobjSpace=>inst?#{*}<=#{?}(#{*}::T)] % [noobjSpace=>inst?#{*}<=#{?}(#{*}::T)]",
    }, delimiter = '%')
    @Disabled
    void testNoobjReservedKeyBoundaries(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    @Disabled
    void testProjectArtifactReadsBack() {
        // the 23KB drstynx project artifact carries 521 class slots; slot #100 is
        // noobjSpace, a reserved-word-colliding key.  Reading it back must now
        // surface either a rec/object or a clean (messaGED) failure — never a
        // bare null-message NPE from the deserializer
        final String content;
        try {
            content = java.nio.file.Files.readString(java.nio.file.Path.of("metatron.ide.mtron"));
        } catch (final java.io.IOException ex) {
            throw new AssertionError("cannot read the project artifact (is it present at the repo root?)", ex);
        }
        if (!content.contains("noobjSpace")) {
            throw new AssertionError("the project artifact under test should carry the noobjSpace class slot; the fixture changed — update this test: " + trunc(content, 300));
        }
        final Obj out;
        try {
            out = ObjmtronSerializer.parse(content).apply(noobj());
        } catch (final Throwable ex) {
            // the NPE regression (null message, HashMap.merge) must be gone; a
            // clean, messaged rejection (e.g. the type predicate naming its
            // mismatched field) is acceptable — the deserialize side is fixed
            final String msg = String.valueOf(ex.getMessage());
            if (null == ex.getMessage() || msg.isBlank())
                throw new AssertionError("reading the project artifact still surfaces a bare null-message NPE — the diagnostic path (engine fix c) did not take: " + ex);
            if (msg.contains("does not match"))
                return; // clean type rejection — deserialization itself succeeded (noobjSpace now parses)
            throw new AssertionError("expected the project artifact to read back (or fail cleanly with a message), but it threw: " + msg);
        }
        if (out.isFail() && !out.toString().contains("does not match")) {
            throw new AssertionError("the project artifact should read to a rec (or a clean type-mismatch fail), but failed with: " + out);
        }
        final String rendered = out.toString();
        if (!rendered.contains("noobjSpace"))
            throw new AssertionError("the read-back artifact should carry the noobjSpace slot, but rendered: " + trunc(rendered, 400));
    }

    @Test
    void testIdeaCommandWrapsPlainAndSpliced() {
        // a plain command string wraps into the enriched runner inst
        final Obj plain = ObjmtronSerializer.parse("ide:command('echo hello')").apply(noobj());
        if (plain.isFail())
            throw new AssertionError("ide:command returned a fail: " + plain);
        if (!plain.isInst())
            throw new AssertionError("ide:command should wrap into the runner inst, but flowed: " + plain);
        // the drstynx boot shape: the command string splices a side binding
        ObjmtronSerializer.parse("side(temp -> \".\")").apply(noobj());
        final Obj splice = ObjmtronSerializer.parse("ide:command('mvn -f ${*temp} compile')").apply(noobj());
        if (splice.isFail())
            throw new AssertionError("ide:command with a spliced side binding returned a fail: " + splice);
    }

    private static String trunc(final String s, final int max) {
        if (null == s)
            return "null";
        return s.length() <= max ? s : s.substring(0, max) + " (…+" + (s.length() - max) + ")";
    }

    private static String trunc(final Obj o, final int max) {
        return trunc(null == o ? null : o.toString(), max);
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    private static Inst command() {
        return instC(f("/m/ide/test/cmd").dom(ALL.maybe()).rng(IDE_RESULT_TID), lst(),
                (lhs, inst) -> noobj());
    }
}
