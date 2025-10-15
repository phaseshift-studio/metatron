/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.translate;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSONTranslatorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
                    1 | 1
                    0 | 0
                    101234 | 101234
                    0.0 | 0.0
                    0.01 | 0.01
                    12.34 | 12.34
                    "hello world" | <hello world>
                    "a/b/c" | <a/b/c>
                    [1,2,3] | [1,2,3]
                    {a:1,b:2,c:3} | [a=>1,b=>2,c=>3]
            """)
    public void testJSONTranslation(final String json, final String mtron) {
        final JSONTranslator translator = new JSONTranslator();
        final Obj j_obj = translator.translate(JsonParser.parseString(json));
        final Obj m_obj = ObjParser.parse(mtron);
        assertEquals(m_obj, j_obj);

    }

}
