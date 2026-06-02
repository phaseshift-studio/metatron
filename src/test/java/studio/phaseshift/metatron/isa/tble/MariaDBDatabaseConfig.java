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

import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Database configuration for MariaDB using TestContainers.
 * MariaDB is MySQL-compatible and uses the same SQL syntax.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MariaDBDatabaseConfig implements DatabaseConfig {

    private final MariaDBContainer<?> container;

    public MariaDBDatabaseConfig() {
        // Use MariaDB 11.x (latest stable version)
        this.container = new MariaDBContainer<>(DockerImageName.parse("mariadb:11.2"))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass");
    }

    @Override
    public String getJdbcHost() {
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("MariaDB container not started. Call setup() first.");
        }
        // Return in the format expected by tbleSpace (without jdbc: prefix)
        return "mariadb://" + container.getHost() + ":" + container.getFirstMappedPort() +
               "/" + container.getDatabaseName() +
               "?user=" + container.getUsername() +
               "&password=" + container.getPassword();
    }

    @Override
    public String getDriverClass() {
        return "org.mariadb.jdbc.Driver";
    }

    @Override
    public Connection getConnection() throws Exception {
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("MariaDB container not started. Call setup() first.");
        }
        return DriverManager.getConnection(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
    }

    @Override
    public void setup() throws Exception {
        if (container == null) {
             throw new IllegalStateException("MariaDB container not initialized");
        }
        container.waitingFor(Wait.forLogMessage(".*ready for connections.*", 1));
        container.withStartupTimeout(java.time.Duration.ofMinutes(3));
        container.start();
    }

    @Override
    public void teardown() throws Exception {
        container.stop();
    }

    @Override
    public String getDatabaseName() {
        return "MariaDB";
    }

    @Override
    public String getRewriteTestTableDDL() {
        return """
               CREATE TABLE rewrite_test (
                   id INT PRIMARY KEY,
                   value INT NOT NULL,
                   name VARCHAR(255) NOT NULL,
                   active BOOLEAN NOT NULL
               )
               """;
    }
    
    @Override
    public String getUsersTableDDL() {
        // MariaDB uses MySQL-compatible syntax
        return """
            CREATE TABLE IF NOT EXISTS users (
                id INT PRIMARY KEY,
                name VARCHAR(255),
                age INT,
                salary DOUBLE,
                active INT,
                email VARCHAR(255)
            )
            """;
    }

    @Override
    public String getProductsTableDDL() {
        // MariaDB uses MySQL-compatible syntax
        return """
            CREATE TABLE IF NOT EXISTS products (
                id INT PRIMARY KEY,
                product_name VARCHAR(255),
                price DOUBLE,
                in_stock INT,
                quantity INT,
                category VARCHAR(255)
            )
            """;
    }

    @Override
    public int getBooleanTrue() {
        // MariaDB uses INTEGER for booleans (1 = true)
        return 1;
    }

    @Override
    public int getBooleanFalse() {
        // MariaDB uses INTEGER for booleans (0 = false)
        return 0;
    }
}
