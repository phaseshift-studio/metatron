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

package studio.phaseshift.metatron.isa.tble;

import org.junit.jupiter.api.*;

/**
 * Test suite for tbleSpace with MySQL database using TestContainers.
 * Extends AbstractTbleSpaceTest to inherit all common database tests.
 * <p>
 * The M33/M34 cross-ref rows used to be skipped here (as they were in {@code MariaDBTbleSpaceTest}):
 * the table is created from the first record, so {@code sqlTypeForMono} typed {@code ca} INTEGER from
 * the initial {@code 0}, and the merge path then bound a {@code !*uri} cross-ref into that INTEGER
 * column — silently coerced, losing the value. {@code ExistingTableSchema} now widens a column to
 * TEXT when the value cannot be represented in it
 * ({@code needsTextColumn} / {@code widenColumnToText}).
 * <p>
 * The {@code testRshiftUriGraphSpine} rows were skipped for a different reason: a second {@code >>}
 * into a <em>list</em> value ({@code kv/test/rshift/x/y} holding {@code [z=>1,zz=>[2,3]]}) returned
 * {@code noobj} while PostgreSQL ran it green. That value lives in ONE row and Java-level
 * {@code unrollPoly} decomposes it — which needs the parent row back from the read.
 * {@code TypedKeyValueSchema} returns every row for a pattern read, so PostgreSQL always got it;
 * {@code fURIAwareIndexedSchema.readMqttPattern} matched on generated segment columns, which a parent
 * row cannot satisfy for a deeper path. It now also matches ancestors of the concrete prefix.
 * <p>
 * Nothing is skipped here any more, matching {@code PostgreSQLTbleSpaceTest}.
 * <p>
 * Container startup — a separate, genuinely MySQL-specific defect, where the first of MySQL's two
 * "ready for connections" lines returned before the port was accepting — is fixed in
 * {@link MySQLDatabaseConfig}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MySQLTbleSpaceTest extends AbstractTbleSpaceTest {

    public MySQLTbleSpaceTest() {
        super(staticDbConfig);
    }

    @BeforeAll
    public static void setupMySQLDatabase() throws Exception {
        // Initialize the static config before calling setupDatabase
        staticDbConfig = new MySQLDatabaseConfig();
        setupDatabase();
    }

    @AfterAll
    public static void cleanupMySQLDatabase() throws Exception {
        cleanupDatabase();
    }

    // All common tests are inherited from AbstractTbleSpaceTest
    // Add MySQL-specific tests below if needed
}
