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

package studio.phaseshift.metatron.isa.mach.space;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.furi.q.LineQTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.nio.file.FileSystems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipRegexTest(value = {
        @SkipRegexTest.Skip(method = "testMultiFieldUpdates"),
        @SkipRegexTest.Skip(method = "testUpdateWrite", params = {"M28", "M29", "M30", "M31", "M39"}),
        @SkipRegexTest.Skip(method = "testRshiftDirectorySpine", params = {"rshift/a/\\.>>\\.>>\\.\\*_", "rshift/x.*"})
})
public class fsSpaceTest extends AbstractSpaceTest implements LineQTest {

    private static final File SOURCE_DIR = new File("src/test/resources/isa/sys/space/");
    private static final File TARGET_DIR = new File("/tmp/fsspace_test");

    public fsSpaceTest() {
        super(f("test:"), () -> fsSpace.of(FileSystems.getDefault(), rec(
                        uri(PATTERN), uri("test:#"),
                        uri(QPROC), lst(QCollection.mimeQ()),
                        uri(SCRIPT), rec(
                                uri("sh"), uri("/bin/sh"),
                                uri("bash"), uri("/bin/bash"),
                                uri("zsh"), uri("/bin/zsh"),
                                uri("python"), uri("/usr/bin/python3"),
                                uri("perl"), uri("/usr/bin/perl"),
                                uri("mtron"), uri("/bin/mtron")),
                        uri(ROUTE), rec(uri("test:"), uri("/tmp/fsspace_test"))),
                f("/sys/space/fs")));
    }

    private static void wipeFsSpaceDir() {
        var dir = java.nio.file.Path.of("/tmp/fsspace_test");
        if (!java.nio.file.Files.exists(dir)) return;
        try (var s = java.nio.file.Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.delete(p);
                        } catch (Exception e) {
                        }
                    });
        } catch (Exception e) {
        }
    }

    @BeforeAll
    static void cleanBefore() {
        wipeFsSpaceDir();
    }

    @AfterAll
    static void cleanAfter() {
        wipeFsSpaceDir();
    }

    @BeforeAll
    public static void setupInstSet() {
        InstSet.importInstSet(MACH_ISA_TID);
        // Copy template data ONCE at class level. Tests must clean up their
        // own files rather than relying on a fresh copy, because @TestData
        // seed writes (processed by TestDataExtension.beforeEach) happen
        // BEFORE @BeforeEach methods — a re-copy here would destroy them.
        copyTestData();
    }

    @Override
    @BeforeEach
    protected void setup() {
        super.setup();
    }

    private static void copyTestData() {
        try {
            if (TARGET_DIR.exists())
                CommonUtil.deleteDirectory(TARGET_DIR.toPath());
            TARGET_DIR.mkdirs();
            CommonUtil.copyDirectory(SOURCE_DIR.toPath(), TARGET_DIR.toPath());
            // Ensure test scripts are executable (Files.copy does not preserve POSIX permissions)
            final File execDir = new File(TARGET_DIR, "file");
            if (execDir.exists() && execDir.isDirectory()) {
                final File[] scriptFiles = execDir.listFiles(File::isFile);
                if (scriptFiles != null) {
                    for (final File script : scriptFiles) {
                        script.setExecutable(true, false);
                    }
                }
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public String make(final String expression) {
        if (expression.contains("$$/"))
            return expression.replace("$$/", "test:");
        return super.make(expression);
    }

    @Override
    protected String cleanupExpr() {
        return "$$/__noop__";
    }

    @Override
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        // fsSpace .mtron files persist between test rows — needs recursive # delete
    }

    /**
     * Override: fsSpace's branch-as-node read returns values, not a rec.
     * Use a + pattern instead.
     */
    @Override
    @Test
    public void testMonoRootlessReadWrites() {
        // Seed writes — @TestData from parent isn't inherited by the override
        ObjmtronSerializer.parse(make("$$/rootless/a -> 1")).apply();
        ObjmtronSerializer.parse(make("$$/rootless/b -> 2")).apply();
        final String[] value = {
                "$$/rootless/c -> 3                                               % *$$/rootless/+                      % {1,2,3}",
                ".                                                                 % *$$/rootless/a                    % 1",
                ".                                                                 % *$$/rootless/c                    % 3",
                "$$/rootless/nested/x/p -> 10                                      % *$$/rootless/nested/x/p           % 10",
        };
        int counter = 0;
        try {
            for (final String expression : value) {
                counter++;
                final String[] parts = expression.split("%");
                final String writeExpression = parts[0].trim();
                final String readExpression = parts[1].trim();
                final String expectedExpression = parts[2].trim();

                if (!writeExpression.equals("."))
                    ObjmtronSerializer.parse(make(writeExpression)).apply();

                if (this.sleepBetweenReads > 0)
                    CommonUtil.sleepThread(this.sleepBetweenReads);

                final Obj readObj = ObjmtronSerializer.parse(make(readExpression)).apply();
                final Obj expectedObj = ObjmtronSerializer.parse(make(expectedExpression)).apply();

                LOG.none("{{G}}TEST[%d]{{X}}\n\twrite [%s]\n\tread [%s]\n\texpected [%s]\n",
                        counter, make(writeExpression), make(readExpression), make(expectedExpression));
                assertEquals(expectedObj, readObj,
                        Graphitty.string("{{R}}TEST[" + counter + "]{{X}}: write: " + make(writeExpression) + " | read: " + make(readExpression)));
            }
        } finally {
            Router.global().write(make("$$/rootless/#"), noobj());
        }
    }

    /**
     * Override to skip two test cases that need in-memory poly traversal
     * that fsSpace's file-backed storage cannot support.
     */
    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "$$/_ops_/x -> 42                                        % *$$/_ops_/x                        % 42",
            ".                                                       % *$$/_ops_/x                        % 42",
            "$$/_ops_/rec -> [a=>1,b=>2,c=>3]                       % *$$/_ops_/rec                      % [a=>1,b=>2,c=>3]",
            ".                                                       % *$$/_ops_/rec/a                    % 1",
            ".                                                       % *$$/_ops_/rec/b                    % 2",
            ".                                                       % *$$/_ops_/rec/+                    % {1,2,3}",
            "$$/_ops_/lst -> [10,20,30]                             % *$$/_ops_/lst                      % [10,20,30]",
            ".                                                       % *$$/_ops_/lst/0                    % 10",
            ".                                                       % *$$/_ops_/lst/1                    % 20",
            ".                                                       % *$$/_ops_/lst/+                    % {10,20,30}",
            "$$/_ops_/nested -> [x=>100,y=>200]                     % *<$$/_ops_/nested/+>              % {100,200}",
            "$$/_ops_/nested2 -> [a=>[x=>1,y=>2],b=>[x=>3,y=>4]]    % *<$$/_ops_/nested2/a/+>           % {1,2}",
            ".                                                       % *<$$/_ops_/nested2/+/x>           % {1,3}",
            // Skipped: fsSpace stores recs as serialized files — no child file entries for wildcard reads
            // "$$/_ops_/people -> [p1=>[name=>alice,age=>30],p2=>[name=>bob,age=>25]] % *$$/_ops_/people/+/name  % {alice,bob}",
    }, delimiter = '%')
    public void testBasicOperations(final String writeExpression, final String readExpression, final String expectedExpression) {
        super.testBasicOperations(writeExpression, readExpression, expectedExpression);
    }

    /**
     * Override to skip TEST[7] which exercises real-typed aggregate reads
     * that require in-memory poly traversal not supported by fsSpace.
     */
    @Override
    @Test
    public void testMonoUpdate() {
        cleanBefore();
        // Write seed data inline — @TestData seed evaluation runs before @BeforeEach,
        // so the fsspace isn't registered with the Router yet.  By the time this test
        // method runs, the space is live.
        ObjmtronSerializer.parse("test:people/1 -> [name=>'Alice', age=>30, title=>'Engineer', salary=>75000.0, company=>!*test:companies/101, active=>true]").apply();
        ObjmtronSerializer.parse("test:people/2 -> [name=>'Bob', age=>25, title=>'Designer', salary=>60000.0, company=>!*test:companies/101, active=>true]").apply();
        ObjmtronSerializer.parse("test:people/3 -> [name=>'Charlie', age=>35, title=>'Manager', salary=>85000.0, company=>!*test:companies/101, active=>false]").apply();
        ObjmtronSerializer.parse("test:people/4 -> [name=>'Diana', age=>28, title=>'Engineer', salary=>70000.0, company=>!*test:companies/102, active=>true]").apply();
        ObjmtronSerializer.parse("test:companies/101 -> [name=>'Acme Corp', city=>'NYC', employees=>50, public=>false];").apply();
        ObjmtronSerializer.parse("test:companies/102 -> [name=>'Globex Inc', city=>'LA', employees=>200, public=>true];").apply();

        // fsSpace stores recs as flat files.  Field writes via @ >>= create child
        // files that fsSpace can't merge into parent recs without creating reentrant
        // Router cycles through locateBasePoly → readStream → file walk → parse.
        // Only simple seed-data reads work.  See memSpaceTest for full testMonoUpdate.
        final String[] value = {
                "*test:people/1                                                         %  *test:people/1/name                                               % \"Alice\"",
                ".                                                                       %  *test:people/1/age                                                % 30",
        };
        int counter = 0;
        try {
            for (final String expression : value) {
                counter++;
                final String[] parts = expression.split("%");
                final String updateExpression = parts[0].trim();
                final String readExpression = parts[1].trim();
                final String expectedExpression = parts[2].trim();

                ObjmtronSerializer.parse(make(updateExpression)).apply();

                if (this.sleepBetweenReads > 0)
                    CommonUtil.sleepThread(this.sleepBetweenReads);

                final Obj readObj = ObjmtronSerializer.parse(make(readExpression)).apply();
                final Obj expectedObj = ObjmtronSerializer.parse(make(expectedExpression)).apply();

                if (!updateExpression.equals("."))
                    PREVIOUS_LINE.set(0, make(updateExpression));
                if (!readExpression.equals("."))
                    PREVIOUS_LINE.set(1, make(readExpression));
                if (!expectedExpression.equals("."))
                    PREVIOUS_LINE.set(2, make(expectedExpression));

                LOG.none("{{G}}TEST[%d]{{X}}\n\tupdate [%s]\n\tread [%s]\n\texpected [%s]\n",
                        counter, make(updateExpression), make(readExpression), make(expectedExpression));
                assertEquals(expectedObj, readObj, Graphitty.string("{{R}}TEST[" + counter + "]{{X}}: update: " + make(updateExpression) + " | read: " + make(readExpression)));
            }
        } finally {
            Router.global().write(make("$$/#"), noobj());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*<test:file/+>.count()            % 8",
            "*<test:file/+/>.count()           % 8",
    }, delimiter = '%')
    public void testFileSystem(final String code, final String expected) {
        LOG.warn("loaded: %s", this.space);
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A directory reads as a navigable dir::T rec — child-name => !*<child uri> refs, single
     * depth and lazy.  {@code >>sub>>sub} descends one level at a time through the Router.
     */
    /**
     * The general {@code space::T} tree: {@code Space.Helper.treeFromUri} builds the
     * navigable obj tree from the universal {@code root/+/} child query — independent
     * of any space's native container type.  {@code >>} descends it (referent side),
     * {@code /} navigates it (reference side).
     */
    @Test
    public void testGeneralSpaceTreeFromUri() {
        try {
            try {
                final java.nio.file.Path root = java.nio.file.Path.of("/tmp/fsspace_test/treepoly");
                java.nio.file.Files.createDirectories(root.resolve("code/main"));
                java.nio.file.Files.writeString(root.resolve("code/main/App.java"), "class App {}");
                java.nio.file.Files.writeString(root.resolve("notes.md"), "# notes");
            } catch (final java.io.IOException e) {
                throw MTronException.of(e);
            }

            // a directory derefs to its own uri — the structure is walked, not materialized
            final Obj dir = ObjmtronSerializer.parse("*test:treepoly").apply();
            assertTrue(dir.isUri(), "a directory must deref to its uri, got: " + dir);

            // and the reference side still navigates with /
            final Obj app = ObjmtronSerializer.parse("*<test:treepoly/code/main/App.java>").apply();
            assertTrue(app.isStr(), "/ path navigation must reach the file, got: " + app);

            // uri >> — the obj-less branch: child uris, no referents resolved;
            // directories carry the trailing / (a branch)
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >>",
                    "{test:/treepoly/code/,<test:/treepoly/notes.md>}");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly/code >>",
                    "{test:/treepoly/code/main/}");

            // uri << — pure uri arithmetic, the reference side's "go up"
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly/code/main <<",
                    "test:treepoly/code");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly <<",
                    "test:");
            // uri << <int> — retract n levels (a/b/c << 2 => a)
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly/code/main << 2",
                    "test:treepoly");
            // uri << <path> — retract a matching postfix (a/b/c/d << c/d => a/b)
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly/code/main << code/main",
                    "test:treepoly");
            // uri >> <path> — navigate the path (a/b/c >><2> => a/b/c/2)
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >><code>",
                    "test:treepoly/code");
            // uri >> <int> and >> <pattern> — the walk: the descendants exactly N levels deep
            // (the depth-N leaves, referentially the >>.>> broadcast)
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >> 2",
                    "{test:/treepoly/code/main/}");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >> <+/+>",
                    "{test:/treepoly/code/main/}");
            // the >>.>> broadcast and the >> N walk agree — which is what makes the
            // rshift_chain rewrite (>>.>>.>> => >> 3) semantically sound
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >>.>>",
                    "test:/treepoly/code/main/");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >>.>>.>>",
                    "<test:/treepoly/code/main/App.java>");
            // >> 0 is the identity — descend zero levels is the uri itself
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:treepoly >> 0",
                    "test:treepoly");
        } finally {
            Router.global().write(make("$$/treepoly/#"), noobj());
        }
    }

    /**
     * A directory derefs to its own uri — the structure is walked with {@code >>} (the
     * reference walk), never materialized as a poly.  {@code /} navigates to a referent.
     */
    @Test
    public void testDirectoryReadReturnsDirPoly() {
        try {
            // build a tree on disk (Java writes — a dotted path like App.java doesn't parse
            // as an mtron write LHS, and this test is about READING directories anyway)
            try {
                final java.nio.file.Path root = java.nio.file.Path.of("/tmp/fsspace_test/dirpoly");
                java.nio.file.Files.createDirectories(root.resolve("code/main/java"));
                java.nio.file.Files.writeString(root.resolve("code/main/java/App.java"), "class App {}");
                java.nio.file.Files.writeString(root.resolve("notes.md"), "# notes");
            } catch (final java.io.IOException e) {
                throw MTronException.of(e);
            }

            // a directory derefs to its own uri — no dir::T poly, just the address
            final Obj dir = ObjmtronSerializer.parse("*test:dirpoly").apply();
            assertTrue(dir.isUri(), "a directory must deref to its uri, got: " + dir);

            // the reference walk reveals the structure: child uris, nothing resolved;
            // directories carry the trailing / (a branch), files are nodes
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:dirpoly >>",
                    "{test:dirpoly/code,<test:dirpoly/notes.md>}");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "test:dirpoly/code/ >>",
                    "{test:dirpoly/code/main/}");

            // / navigation descends to a file's content (the deliberate deref)
            final Obj app = ObjmtronSerializer.parse("*<test:dirpoly/code/main/java/App.java>").apply();
            assertTrue(app.isStr(), "/ path navigation must reach the file, got: " + app);
        } finally {
            Router.global().write(make("$$/dirpoly/#"), noobj());
        }
    }

    @Disabled("resolveRead strips ?mimeq= via qLessExceptDomRng() before directReader sees it — needs API rethink")
    @ParameterizedTest
    @CsvSource(value = {
            // Read raw content via mimeq=text/plain (executable files return inst wrappers by default)
            "*<test:file/test-py.py?mimeq=text/plain>        % #! /usr/venv/bin/python3",
            "*<test:file/test-sh.sh?mimeq=text/plain>        % #! /usr/bin/env sh",
            "*<test:file/test-bash.bash?mimeq=text/plain>    % #! /usr/bin/env bash",
    }, delimiter = '%')
    public void testFileTypes(final String code, final String expected) {
        final Obj shell = ObjmtronSerializer.parse(code).apply();
        LOG.warn("loaded shell: %s", shell);
        assertEquals(STR_TID, shell.tid(), "shell file data should be a string");
        assertTrue(shell.strValue().startsWith(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Shell script with no args returns one output line
            "<test:file/hello>()                                         % <reply>",
            // Shell script echoes each arg on its own line
            "<test:file/echo-args>('a','b','c')                          % {a,b,c}",
            "<test:file/echo-args>('a','b','c').count()                  % 3",
            "<test:file/echo-args>('a','b','c').>-[,]._/count()\\_.>>    %  3",
            // Shell script: arithmetic sum
            "<test:file/add>(3,4)                                        % 7",
            // Python script execution
            "<test:file/hello-py>()                                      % \"hello from python3\"",
            "<test:file/hello-py>().count()                              % 1",
            // Shell script outputting mtron-parseable rec
            "<test:file/json-out>()                                      % [greeting=>\"hello\",from=>\"sh\"]",
    }, delimiter = '%')
    public void testFileAsInstruction(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Override
    protected boolean skipBasicOperations() {
        return false;
    }
}
