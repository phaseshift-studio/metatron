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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import studio.phaseshift.metatron.TestReport;

/**
 * Test suite for tbleSpace with PostgreSQL database using TestContainers.
 * Extends AbstractTbleSpaceTest to inherit all common relational database tests.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// No @SkipRegexTest here: every inherited row runs on PostgreSQL. The rows MySQL and MariaDB still
// skip — a second `>>` into a list-valued KV entry — are specific to fURIAwareIndexedSchema, the
// MQTT/generated-column schema that initializeSchema() installs for the MySQL family; PostgreSQL and
// SQLite use TypedKeyValueSchema and handle the same rows.
@TestReport
public class PostgreSQLTbleSpaceTest extends AbstractTbleSpaceTest {

    public PostgreSQLTbleSpaceTest() {
        super(new PostgreSQLDatabaseConfig());
    }

    @BeforeAll
    public static void setupPostgreSQLDatabase() throws Exception {
        staticDbConfig = new PostgreSQLDatabaseConfig();
        setupDatabase();
    }

    @AfterAll
    public static void cleanupPostgreSQLDatabase() throws Exception {
        cleanupDatabase();
    }

    // All common tests are inherited from AbstractTbleSpaceTest
    // Add PostgreSQL-specific tests below if needed
}
