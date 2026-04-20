/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;

import java.nio.file.FileSystems;

import static junit.framework.TestCase.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fsSpaceTest extends AbstractSpaceTest {

    public fsSpaceTest() {
        super(f("/tmp"), () -> {
            mParser.eval("boot/script ->\n" +
                    "  [sh     => /bin/sh,\n" +
                    "   bash   => /bin/bash,\n" +
                    "   zsh    => /bin/zsh,\n" +
                    "   python => /usr/bin/python3,\n" +
                    "   perl   => /usr/bin/perl,\n" +
                    "   mtron  => /bin/mtron]");
            return fsSpace.of(FileSystems.getDefault(), rec(
                            uri(PATTERN), uri("test:#"), uri(SCRIPT), auto_from_(f("boot/script")),
                            uri(ROUTE), rec(uri("test:"), uri("src/test/resources/isa/sys/space/"))),
                    f("/sys/space/fs"));
        });
    }

    @BeforeAll
    public static void setupInstSet() {
        InstSet.importInstSet(MACH_ISA_TID);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*<test:#>.count().?>3             % 14",
            "*boot/script/sh                   % /bin/sh",
            "*<test:+>                         % {dir::<test:db>,dir::<test:file>,dir::<test:llm>}",
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
        final Obj shell = mParser.eval(code);
        LOG.info("loaded shell: %s", shell);
        assertTrue(shell.isStr());
        assertTrue(shell.strValue().startsWith(expected));
    }

    @Disabled
    @ParameterizedTest
    @CsvSource(value = {
            "<test:file/test-bash.bash>(1)     % /usr/bin/env bash",
    }, delimiter = '%')
    public void testShellEvaluation(final String code, final String expected) {
        final Obj shell = mParser.eval(code);
        LOG.info("loaded shell: %s", shell);
        assertTrue(shell.isStr());
        assertTrue(shell.strValue().startsWith(expected));
    }

    @Disabled
    @Override
    public void testMultiFieldUpdates(int fieldCount) {
        // DO NOTHING
    }
}
