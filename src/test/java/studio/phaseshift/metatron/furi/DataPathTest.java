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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DataPathTest extends AbstractMetatronTest {

    // =======================================================================
    // of() — full fURI decomposition (4 segments + extension)
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "mydb/users/abc/name/extra/deep | mydb | users | abc  | name | extra/deep",
            "mydb/users/abc/name            | mydb | users | abc  | name | null",
            "mydb/users/abc                 | mydb | users | abc  | null | null",
            "mydb/users                     | mydb | users | null | null | null",
            "mydb                           | mydb | null  | null | null | null",
            "                               | null | null  | null | null | null",
    }, delimiter = '|', nullValues = "null")
    public void testOf(final String furiStr, final String db, final String collection,
                       final String entry, final String field, final String extension) {
        final fURI vid = f(furiStr);
        final DataPath dp = DataPath.of(vid);

        assertEquals(db, dp.db(), "db mismatch for " + furiStr);
        assertEquals(collection, dp.collection(), "collection mismatch for " + furiStr);
        assertEquals(entry, dp.entry(), "entry mismatch for " + furiStr);
        assertEquals(field, dp.field(), "field mismatch for " + furiStr);
        assertEquals(null == extension ? null : f(extension), dp.extension(), "extension mismatch for " + furiStr);
    }

    // =======================================================================
    // ofSpaceRelative() — space-relative fURI with explicit db name
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "users/abc/name/extra | mydb | mydb | users  | abc  | name | extra",
            "users/abc/name       | mydb | mydb | users  | abc  | name | null",
            "users/abc            | mydb | mydb | users  | abc  | null | null",
            "users                | mydb | mydb | users  | null | null | null",
            "                     | mydb | mydb | null  | null | null | null",
    }, delimiter = '|', nullValues = "null")
    public void testOfSpaceRelative(final String furiStr, final String db, final String expDb,
                                     final String collection, final String entry,
                                     final String field, final String extension) {
        final fURI vid = f(furiStr);
        final DataPath dp = DataPath.ofSpaceRelative(vid, db);

        assertEquals(expDb, dp.db(), "db mismatch for " + furiStr);
        assertEquals(collection, dp.collection(), "collection mismatch for " + furiStr);
        assertEquals(entry, dp.entry(), "entry mismatch for " + furiStr);
        assertEquals(field, dp.field(), "field mismatch for " + furiStr);
        assertEquals(null == extension ? null : f(extension), dp.extension(), "extension mismatch for " + furiStr);
    }

    // =======================================================================
    // spaceURI() — reconstruct the space prefix URI
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "mydb/users/abc/name | mydb/users/abc/name",
            "mydb/users/abc      | mydb/users/abc",
            "mydb/users          | mydb/users",
            "mydb                | mydb",
            "                    | ",
    }, delimiter = '|', nullValues = "null")
    public void testSpaceURI(final String furiStr, final String expected) {
        final DataPath dp = DataPath.of(f(furiStr));
        assertEquals(f(expected), dp.spaceURI(), "spaceURI mismatch for " + furiStr);
    }

    // =======================================================================
    // has* accessors
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "mydb/users/abc/name | true  | true  | true  | true  | false",
            "mydb/users/abc      | true  | true  | true  | false | false",
            "mydb/users          | true  | true  | false | false | false",
            "mydb                | true  | false | false | false | false",
            "                    | false | false | false | false | false",
            "mydb/users/abc/name/extra | true | true | true | true | true",
    }, delimiter = '|')
    public void testHasMethods(final String furiStr, final boolean hasDb, final boolean hasCollection,
                                final boolean hasEntry, final boolean hasField, final boolean hasExtension) {
        final DataPath dp = DataPath.of(f(furiStr));

        assertEquals(hasDb, dp.hasDb(), "hasDb mismatch for " + furiStr);
        assertEquals(hasCollection, dp.hasCollection(), "hasCollection mismatch for " + furiStr);
        assertEquals(hasEntry, dp.hasEntry(), "hasEntry mismatch for " + furiStr);
        assertEquals(hasField, dp.hasField(), "hasField mismatch for " + furiStr);
        assertEquals(hasExtension, dp.hasExtension(), "hasExtension mismatch for " + furiStr);
    }

    // =======================================================================
    // wildcard inspection — # cascades to all descendants, + does not
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            //                 db    coll  entry field  ext
            "     mydb/#     | false | true  | true  | true  | true",
            "     mydb/+/abc | false | true  | false | false | false",
            "     #/abc      | true  | true  | true  | true  | true",
            "     +/abc      | true  | false | false | false | false",
            "     mydb/+/+   | false | true  | true  | false | false",
            "     mydb/+/#   | false | true  | true  | true  | true",
            "     mydb/+/abc/name | false | true | false | false | false",
    }, delimiter = '|')
    public void testWildcardCascade(final String furiStr, final boolean dbWild, final boolean collWild,
                                     final boolean entryWild, final boolean fieldWild, final boolean extWild) {
        final DataPath dp = DataPath.of(f(furiStr));

        assertEquals(dbWild, dp.dbIsWildcard(), "dbIsWildcard mismatch for " + furiStr);
        assertEquals(collWild, dp.collectionIsWildcard(), "collectionIsWildcard mismatch for " + furiStr);
        assertEquals(entryWild, dp.entryIsWildcard(), "entryIsWildcard mismatch for " + furiStr);
        assertEquals(fieldWild, dp.fieldIsWildcard(), "fieldIsWildcard mismatch for " + furiStr);
        assertEquals(extWild, dp.extensionIsWildcard(), "extensionIsWildcard mismatch for " + furiStr);
    }

    // =======================================================================
    // fieldPathStr() — dot-joined field + extension
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "mydb/users/abc/name/extra/deeper | name.extra.deeper",
            "mydb/users/abc/name/extra        | name.extra",
            "mydb/users/abc/name              | name",
            "mydb/users/abc                   | null",
    }, delimiter = '|', nullValues = "null")
    public void testFieldPathStr(final String furiStr, final String expected) {
        final DataPath dp = DataPath.of(f(furiStr));
        assertEquals(expected, dp.fieldPathStr(), "fieldPathStr mismatch for " + furiStr);
    }

    // =======================================================================
    // vid() — fully-qualified VID from space pattern
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "users/abc/name | mongo:#   | null | mongo:users/abc/name",
            "users/abc      | mongo:#   | null | mongo:users/abc",
            "users          | mongo:#   | null | mongo:users",
            "               | mongo:#   | null | mongo:",
            "users/abc      | /g/#      | g    | /g/users/abc",
    }, delimiter = '|', nullValues = "null")
    public void testVid(final String furiStr, final String spacePattern, final String db, final String expected) {
        final DataPath dp = DataPath.ofSpaceRelative(f(furiStr), db);
        assertEquals(f(expected), dp.vid(f(spacePattern)), "vid mismatch for " + furiStr);
    }

    // =======================================================================
    // extendedDataPath() — recursive extension decomposition
    // =======================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c/d/e/f/g/h | a/b/c/d | true",
            "a/b/c/d/e       | a/b/c/d | true",
            "a/b/c/d         | a/b/c/d | false",
    }, delimiter = '|')
    public void testExtendedDataPath(final String furiStr, final String baseFuri, final boolean hasExtension) {
        final DataPath dp = DataPath.of(f(furiStr));
        assertEquals(hasExtension, dp.hasExtension(), "hasExtension mismatch for " + furiStr);

        if (hasExtension) {
            final DataPath extended = dp.extendedDataPath();
            assertNotNull(extended, "expected non-null extendedDataPath for " + furiStr);
            assertEquals(f(baseFuri), dp.spaceURI(), "base spaceURI mismatch for " + furiStr);
        } else {
            assertNull(dp.extendedDataPath(), "expected null extendedDataPath for " + furiStr);
        }
    }
}
