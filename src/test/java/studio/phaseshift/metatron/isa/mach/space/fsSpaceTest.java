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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.file.fsSpace;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.nio.file.FileSystems;

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
        super(f("test"),() -> {
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
                            uri(ROUTE), rec(uri("test:"), uri("src/test/resources/isa/sys/file/"))),
                    f("/sys/space/fs"));
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*<test:#>.count().?>3              % 4",
            "*boot/script/sh                    % /bin/sh",
            "*<test:space/test-py.py>           % file::<test:space/test-py.py?p=rwxr-xr-x>",
            "*<test:space/test-py.py>           % file::<test:space/test-py.py?p=rwxr-xr-x>",
            "*<test:space/test-sh.sh>           % file::<test:space/test-sh.sh?p=rwxr-xr-x>",
            "*<test:space/test-bash.bash>       % file::<test:space/test-bash.bash?p=rwxr-xr-x>",
            // "<test:space/test-sh.sh>()          % \"metatron 0.1-SNAPSHOT\"",
    }, delimiter = '%')
    public void testShell(final String code, final String expected) {
        LOG.warn("loaded: %s",this.space);
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    // Disable all abstract tests - fsSpace is for file system operations, not general CRUD
    @Override @Disabled public void testStringCornerCases(String description, String value) {}
    @Override @Disabled public void testIntegerBoundaries(String description, long value) {}
    @Override @Disabled public void testRealBoundaries(String description, double value) {}
    @Override @Disabled public void testBooleanValues(String description, boolean value) {}
    @Override @Disabled public void testNonExistentAccess(String key) {}
    @Override @Disabled public void testSequentialUpdates(int iterations) {}
    @Override @Disabled public void testBasicCRUD(String description, String key, String valueStr) {}
    @Override @Disabled public void testTypePreservation(String description, Obj value) {}
    @Override @Disabled public void testNestedRecords(int depth) {}
    @Override @Disabled public void testListHandling(String description, studio.phaseshift.metatron.isa.m.type.Lst listValue, int expectedCount) {}
    @Override @Disabled public void testTypeChanges(String description, Obj initialValue, Obj updatedValue) {}
    @Override @Disabled public void testMultiFieldUpdates(int fieldCount) {}
    @Override @Disabled public void testSpecialStringValues(String description, String value) {}
    @Override @Disabled public void testEmptyRecords(int testNumber) {}
}
