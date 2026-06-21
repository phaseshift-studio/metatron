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

package studio.phaseshift.metatron.isa.mach.io.type;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.tble.space.ExistingTableSchema.ColumnMetadata;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for {@link ObjSQLSerializer} JSON detection and {@link ColumnMetadata} default probing.
 */
@DisplayName("ObjSQLSerializer — JSON detection")
public class ObjSQLSerializerTest {

    static {
        BootLoader.TESTING = true;
    }

    @BeforeAll
    static void beforeAll() {
        memSpace.of(f("/sys/#"), null);
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.values());
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec(uri(LOGG), uri(LogObj.getSLF4J().toString().toLowerCase())));
    }

    @AfterAll
    static void afterAll() {
        BootLoader.close();
    }

    ///////////////////////////////////////////////////////////////////////////
    // readMaybeJSON

    /// ////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("readMaybeJSON")
    class ReadMaybeJSON {

        @ParameterizedTest(name = "[{index}] {0} → lst ({2})")
        @CsvSource(value = {
                "[]                    % empty JSON array",
                "[1, 2, 3]             % simple int array",
                "[{\"a\":1}, {\"b\":2}] % array of objects",
                "  [1,2,3]             % leading whitespace",
        }, delimiter = '%')
        void testJSONArray(String input, String description) {
            final Obj result = ObjSQLSerializer.readMaybeJSON(input);
            assertTrue(result.isLst(), "expected Lst, got " + result.getClass().getSimpleName());
        }

        @ParameterizedTest(name = "[{index}] {0} → rec ({2})")
        @CsvSource(value = {
                "{}                    % empty JSON object",
                "{\"name\":\"marko\"}    % simple object",
                "{\"a\":1,\"b\":[1,2]}   % nested array in object",
                "  {\"key\":\"val\"}     % leading whitespace",
        }, delimiter = '%')
        void testJSONObject(String input, String description) {
            final Obj result = ObjSQLSerializer.readMaybeJSON(input);
            assertTrue(result.isRec(), "expected Rec, got " + result.getClass().getSimpleName());
        }

        @ParameterizedTest(name = "[{index}] {0} → str ({2})")
        @CsvSource(value = {
                "hello                % plain text",
                "123                  % numeric text",
                "true                 % boolean text",
                "                     % empty string",
                "  plain text         % leading whitespace, no JSON",
        }, delimiter = '%')
        void testPlainString(String input, String description) {
            final Obj result = ObjSQLSerializer.readMaybeJSON(input);
            assertTrue(result.isStr(), "expected Str, got " + result.getClass().getSimpleName());
        }

        @Test
        @DisplayName("null returns str with null jvm")
        void testNull() {
            final Obj result = ObjSQLSerializer.readMaybeJSON(null);
            assertTrue(result.isStr());
            assertNull(result.strValue());
        }

        @Test
        @DisplayName("JSON array produces a Lst with parsed elements")
        void testArrayContent() {
            final Obj result = ObjSQLSerializer.readMaybeJSON("[1, 2, 3]");
            assertTrue(result.isLst());
            final Lst lst = result.asLst();
            assertEquals(3L, lst.count());
        }

        @Test
        @DisplayName("JSON object produces a Rec with parsed fields")
        void testObjectContent() {
            final Obj result = ObjSQLSerializer.readMaybeJSON("{\"name\":\"marko\",\"age\":46}");
            assertTrue(result.isRec());
            final Rec rec = result.asRec();
            assertTrue(rec.has(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("name")));
            assertTrue(rec.has(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("age")));
        }

        @Test
        @DisplayName("nested JSON object round-trips correctly")
        void testNestedObject() {
            final Obj result = ObjSQLSerializer.readMaybeJSON("{\"mem\":[1,2,3],\"max\":15}");
            assertTrue(result.isRec());
            final Rec rec = result.asRec();
            final Obj mem = rec.at(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("mem"));
            assertTrue(mem.isLst());
            assertEquals(3L, mem.asLst().count());
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // ColumnMetadata JSON default probing

    /// ////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("ColumnMetadata.isDefaultJSONArray / isDefaultJSONObject")
    class ColumnMetadataDefaults {

        @ParameterizedTest(name = "[{index}] default={0} → isArray={1} isObject={2} ({3})")
        @CsvSource(value = {
                "[]             % true  % false % empty JSON array",
                "[1,2,3]        % true  % false % populated array",
                "json_array()   % true  % false % SQL function form (array)",
                "'[]'           % true  % false % quoted empty array",
                "{}             % false % true  % empty JSON object",
                "{\"a\":1}       % false % true  % populated object",
                "json_object()  % false % true  % SQL function form (object)",
                "'{}'           % false % true  % quoted empty object",
                "NULL           % false % false % null default",
                "               % false % false % empty default",
        }, delimiter = '%', nullValues = "NULL")
        void testDefaults(String columnDefault, boolean expectArray, boolean expectObject, String description) {
            final ColumnMetadata col = new ColumnMetadata("test_col", java.sql.Types.VARCHAR,
                    "JSON", true, columnDefault);
            assertEquals(expectArray, col.isDefaultJSONArray(), "isDefaultJSONArray");
            assertEquals(expectObject, col.isDefaultJSONObject(), "isDefaultJSONObject");
        }

        @Test
        @DisplayName("construction without default has null default")
        void testNoDefault() {
            final ColumnMetadata col = new ColumnMetadata("test_col", java.sql.Types.VARCHAR, "JSON");
            assertFalse(col.isDefaultJSONArray());
            assertFalse(col.isDefaultJSONObject());
        }
    }
}
