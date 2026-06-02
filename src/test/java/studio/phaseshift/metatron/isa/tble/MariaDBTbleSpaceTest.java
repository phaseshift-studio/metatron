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

/**
 * Test suite for tbleSpace using MariaDB via TestContainers.
 * MariaDB is MySQL-compatible and provides a drop-in replacement for MySQL.
 *
 * This test class extends AbstractTbleSpaceTest which contains all the actual test logic.
 * The only responsibility of this class is to set up and tear down the MariaDB container.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MariaDBTbleSpaceTest extends AbstractTbleSpaceTest {

    /**
     * Start the MariaDB container before all tests.
     * This is called once per test class.
     */
    @BeforeAll
    public static void setupMariaDBDatabase() throws Exception {
        staticDbConfig = new MariaDBDatabaseConfig();
        setupDatabase();
        LOG.info("MariaDB container started: " + staticDbConfig.getJdbcHost());
    }

    /**
     * Stop the MariaDB container after all tests.
     * This is called once per test class.
     */
    @AfterAll
    public static void teardownMariaDBDatabase() throws Exception {
        cleanupDatabase();
        LOG.info("MariaDB container stopped");
    }

    /**
     * Constructor that passes the MariaDB configuration to the parent class.
     */
    public MariaDBTbleSpaceTest() {
        super(staticDbConfig);
    }
}
