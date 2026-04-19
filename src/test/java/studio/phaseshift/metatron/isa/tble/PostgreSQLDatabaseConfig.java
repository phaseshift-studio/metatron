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

package studio.phaseshift.metatron.isa.tble;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * PostgreSQL database configuration for testing using TestContainers.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PostgreSQLDatabaseConfig implements DatabaseConfig {

    private PostgreSQLContainer<?> postgresContainer;

    @Override
    public String getJdbcHost() {
        if (postgresContainer == null) {
            throw new IllegalStateException("PostgreSQL container not started. Call setup() first.");
        }
        // Return in the format expected by tbleSpace (without jdbc: prefix)
        // Include username and password in the URL for PostgreSQL authentication
        return "postgresql://" + postgresContainer.getHost() + ":" + postgresContainer.getFirstMappedPort() +
               "/" + postgresContainer.getDatabaseName() +
               "?user=" + postgresContainer.getUsername() +
               "&password=" + postgresContainer.getPassword();
    }

    @Override
    public String getDriverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    public Connection getConnection() throws Exception {
        if (postgresContainer == null) {
            throw new IllegalStateException("PostgreSQL container not started. Call setup() first.");
        }
        return DriverManager.getConnection(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword()
        );
    }

    @Override
    public void setup() throws Exception {
        // Start PostgreSQL container
        postgresContainer = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withStartupTimeout(java.time.Duration.ofMinutes(2));  // Give it more time
        postgresContainer.start();

        // Wait a bit for PostgreSQL to be fully ready
        Thread.sleep(2000);

        // Load PostgreSQL JDBC driver
        Class.forName(getDriverClass());
    }

    @Override
    public void teardown() throws Exception {
        if (postgresContainer != null) {
            postgresContainer.stop();
            postgresContainer = null;
        }
    }

    @Override
    public String getDatabaseName() {
        return "PostgreSQL";
    }

    @Override
    public String getUsersTableDDL() {
        return """
               CREATE TABLE users (
                   id INTEGER PRIMARY KEY,
                   name VARCHAR(255),
                   age INTEGER,
                   salary DOUBLE PRECISION,
                   active INTEGER,
                   email VARCHAR(255)
               )
               """;
    }

    @Override
    public String getProductsTableDDL() {
        return """
               CREATE TABLE products (
                   id INTEGER PRIMARY KEY,
                   product_name VARCHAR(255),
                   price DOUBLE PRECISION,
                   in_stock INTEGER,
                   quantity INTEGER,
                   category VARCHAR(255)
               )
               """;
    }

    @Override
    public String getRewriteTestTableDDL() {
        return """
               CREATE TABLE rewrite_test (
                   id INTEGER PRIMARY KEY,
                   value INTEGER NOT NULL,
                   name VARCHAR(255) NOT NULL,
                   active INTEGER NOT NULL
               )
               """;
    }

    @Override
    public int getBooleanTrue() {
        return 1; // Will be converted to TRUE in SQL
    }

    @Override
    public int getBooleanFalse() {
        return 0; // Will be converted to FALSE in SQL
    }
}
