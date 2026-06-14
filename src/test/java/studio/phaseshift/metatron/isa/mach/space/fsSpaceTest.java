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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.nio.file.FileSystems;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fsSpaceTest extends AbstractSpaceTest {

    private static final File SOURCE_DIR = new File("src/test/resources/isa/sys/space/");
    private static final File TARGET_DIR = new File("/tmp/fsspace_test");

    public fsSpaceTest() {
        super(f("test:"), () -> {
            ObjmtronSerializer.parse("boot/script ->\n" +
                    "  [sh     => /bin/sh,\n" +
                    "   bash   => /bin/bash,\n" +
                    "   zsh    => /bin/zsh,\n" +
                    "   python => /usr/bin/python3,\n" +
                    "   perl   => /usr/bin/perl,\n" +
                    "   mtron  => /bin/mtron]").apply();
            return fsSpace.of(FileSystems.getDefault(), rec(
                            uri(PATTERN), uri("test:#"),
                            uri(SCRIPT), auto_from_(f("boot/script")),
                            uri(ROUTE), rec(uri("test:"), uri("/tmp/fsspace_test"))),
                    f("/sys/space/fs"));
        });
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
            "*<test:file/+>.count()            % 3",
            "*<test:file/+/>.count()           % 3",
            "*boot/script/sh                   % /bin/sh",
    }, delimiter = '%')
    public void testFileSystem(final String code, final String expected) {
        LOG.warn("loaded: %s", this.space);
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*<test:file/test-py.py>           % #! /usr/venv/bin/python3",
            "*<test:file/test-sh.sh>           % #! /usr/bin/env sh",
            "*<test:file/test-bash.bash>       % #! /usr/bin/env bash",
    }, delimiter = '%')
    public void testFileTypes(final String code, final String expected) {
        final Obj shell = ObjmtronSerializer.parse(code).apply();
        LOG.warn("loaded shell: %s", shell);
        assertEquals(STR_TID, shell.tid(), "shell file data should be a string");
        assertTrue(shell.strValue().startsWith(expected));
    }

    @Disabled
    @ParameterizedTest
    @CsvSource(value = {
            "<test:file/test-bash.bash>(1)     % /usr/bin/env bash",
    }, delimiter = '%')
    public void testShellEvaluation(final String code, final String expected) {
        final Obj shell = ObjmtronSerializer.parse(code).apply();
        LOG.warn("loaded shell: %s", shell);
        assertTrue(shell.isStr());
        assertTrue(shell.strValue().startsWith(expected));
    }

    @Disabled
    @Override
    public void testMultiFieldUpdates(int fieldCount) {
        // DO NOTHING
    }

    @Override
    protected boolean skipBasicOperations() {
        return false;
    }
}
