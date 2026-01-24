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

package studio.phaseshift.metatron.lang.sys.fs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.sys.space.file.fileSpace;
import studio.phaseshift.metatron.isa.sys.space.file.fsInstSet;
import studio.phaseshift.metatron.mSpaceTest;

import java.nio.file.FileSystems;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class fsSpaceTest extends mSpaceTest {

    public fsSpaceTest() {
        super(() -> {
            //try {
            mParser.eval("*/fs");
            final fileSpace space = fileSpace.of(FileSystems.getDefault(), Map.of(), f("/tmp/#"), f("/sys/space/fs"));
            return space;
        });
        fsInstSet.create();

    }
    
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
