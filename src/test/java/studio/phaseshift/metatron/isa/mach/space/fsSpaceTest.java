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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.io.space.file.fsSpace;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.nio.file.FileSystems;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
// @TestSkip(testClass = SpaceTest.class, testMethods = {"testMonoReadWrite"}) TODO: this should pass?? something's weird
public class fsSpaceTest extends AbstractSpaceTest {

    public fsSpaceTest() {
        super(() -> {
            BootLoader.loadInstSetProvider(MACH_ISA_TID);
            mParser.eval("boot/script ->\n" +
                    "  [sh     => /bin/sh,\n" +
                    "   bash   => /bin/bash,\n" +
                    "   zsh    => /bin/zsh,\n" +
                    "   python => /usr/bin/python3,\n" +
                    "   perl   => /usr/bin/perl,\n" +
                    "   mtron  => /bin/mtron]");
            return fsSpace.of(FileSystems.getDefault(), rec(
                            uri(PATTERN), uri("test:#"), uri(SCRIPT), auto_from_(f("boot/script")),
                            uri(ROUTE), rec(uri("test:"), uri("src/test/resources/isa/sys/"))),
                    f("/sys/space/mem"));
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*<test:#>.count().?>3              % 4",
            "*boot/script/sh                    % /bin/sh",
            "*<test:space/test-py.py>           % file::<test:space/test-py.py?p=rwxrwxrwx>",
            "*<test:space/test-py.py>           % file::<test:space/test-py.py?p=rwxrwxrwx>",
            "*<test:space/test-sh.sh>           % file::<test:space/test-sh.sh?p=rwxrwxrwx>",
            "*<test:space/test-bash.bash>       % file::<test:space/test-bash.bash?p=rwxrwxrwx>",
            // "<test:space/test-sh.sh>()          % \"metatron 0.1-SNAPSHOT\"",
    }, delimiter = '%')
    public void testShell(final String code, final String expected) {
        AbstractMetatronTest.testCode(LOG, code, expected);
    }


}
