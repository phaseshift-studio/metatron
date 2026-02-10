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

package studio.phaseshift.metatron.isa.sys.space;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.SpaceTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.mTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ExtendWith(TestData.TestDataExtension.class)
public class fsSpaceTest extends SpaceTest {

    public fsSpaceTest() {
      /*  super(f("/tmp/"),() -> {
           return fsSpace.of(FileSystems.getDefault(), rec(uri(PATTERN), uri("/tmp/#"), uri(REWRITE), rec(uri(""), uri(""))), f("/sys/space/fs"));
          
        });
        sysInstSet.create();*/
        super(() -> noobjSpace.single());

    }

    @ParameterizedTest
    @TestData(values = {
            "root -> <src/test/resources/isa/sys/>",
            "boot/script ->\n" +
                    "  [sh     => /bin/sh,\n" +
                    "   bash   => /bin/bash,\n" +
                    "   zsh    => /bin/zsh,\n" +
                    "   python => /usr/bin/python3,\n" +
                    "   perl   => /usr/bin/perl,\n" +
                    "   mtron  => /bin/mtron]",
            "fs::[pattern=><test:#>,rewrite=><test:>=>!*root,script=>!*boot/script]@/sys/space/fs/test"
    })
    @Disabled
    @CsvSource(value = {
            "*root                              % <src/test/resources/isa/sys/>",
            "*boot/script/sh                    % /bin/sh",
            "*<test:space/test-py.py>           % file::<test:space/test-py.py?p=rw-rw-r-->",
            "*<test:space/test-sh.sh>           % file::<test:space/test-sh.sh?p=rw-rw-r-->",
            "*<test:space/test-bash.bash>       % file::<test:space/test-bash.bash?p=rw-rw-r-->",
    }, delimiter = '%')
    public void testShell(final String code, final String expected) {
        mTest.testCode(LOG, code, expected);
    }

    @Disabled
    @ParameterizedTest
    @CsvSource(value = {
            "</tmp/file.jpg> -> 0xab2356abcd        % a",
            "*</tmp/file.jpg>    % abc"
    }, delimiter = '%')
    public void testImage(final String code, final String expected) {
        final Obj resultObj = mParser.eval(code);
        final Obj checkObj = mParser.eval(expected);
        assertNotEquals(noobj(), checkObj);
        assertEquals(checkObj, resultObj);
    }

}
