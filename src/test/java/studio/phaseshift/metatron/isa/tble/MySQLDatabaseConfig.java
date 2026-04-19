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

import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * MySQL database configuration for testing using TestContainers.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MySQLDatabaseConfig implements DatabaseConfig {

    private MySQLContainer<?> mysqlContainer;

    @Override
    public String getJdbcHost() {
        if (mysqlContainer == null) {
            throw new IllegalStateException("MySQL container not started. Call setup() first.");
        }
        // Return in the format expected by tbleSpace (without jdbc: prefix)
        // Include username and password in the URL for MySQL authentication
        return "mysql://" + mysqlContainer.getHost() + ":" + mysqlContainer.getFirstMappedPort() +
               "/" + mysqlContainer.getDatabaseName() +
               "?user=" + mysqlContainer.getUsername() +
               "&password=" + mysqlContainer.getPassword();
    }

    @Override
    public String getDriverClass() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public Connection getConnection() throws Exception {
        if (mysqlContainer == null) {
            throw new IllegalStateException("MySQL container not started. Call setup() first.");
        }
        return DriverManager.getConnection(
                mysqlContainer.getJdbcUrl(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword()
        );
    }

    @Override
    public void setup() throws Exception {
        // Start MySQL container with specific version and configuration
        mysqlContainer = new MySQLContainer<>("mysql:8.0.33")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withEnv("MYSQL_ROOT_PASSWORD", "root")
                .withCommand("--default-authentication-plugin=mysql_native_password")
                .withStartupTimeout(java.time.Duration.ofMinutes(2));  // Give it more time
        mysqlContainer.start();

        // Wait a bit for MySQL to be fully ready
        Thread.sleep(2000);

        // Load MySQL JDBC driver
        Class.forName(getDriverClass());
    }

    @Override
    public void teardown() throws Exception {
        if (mysqlContainer != null) {
            mysqlContainer.stop();
            mysqlContainer = null;
        }
    }

    @Override
    public String getDatabaseName() {
        return "MySQL";
    }

    @Override
    public String getUsersTableDDL() {
        return """
               CREATE TABLE users (
                   id INT PRIMARY KEY,
                   name VARCHAR(255),
                   age INT,
                   salary DOUBLE,
                   active BOOLEAN,
                   email VARCHAR(255)
               )
               """;
    }

    @Override
    public String getProductsTableDDL() {
        return """
               CREATE TABLE products (
                   id INT PRIMARY KEY,
                   product_name VARCHAR(255),
                   price DOUBLE,
                   in_stock BOOLEAN,
                   quantity INT,
                   category VARCHAR(255)
               )
               """;
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
    public int getBooleanTrue() {
        return 1; // MySQL stores BOOLEAN as TINYINT(1)
    }

    @Override
    public int getBooleanFalse() {
        return 0;
    }
}
