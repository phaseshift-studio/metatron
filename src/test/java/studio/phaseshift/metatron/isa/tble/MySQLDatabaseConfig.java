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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * MySQL database configuration for testing using TestContainers.
 * Uses GenericContainer to avoid requiring the MySQL JDBC driver on the classpath
 * for TestContainers' internal readiness checks (uses MariaDB driver instead).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MySQLDatabaseConfig implements DatabaseConfig {

    private GenericContainer<?> mysqlContainer;
    private static final String DB_NAME = "testdb";
    private static final String DB_USER = "test";
    private static final String DB_PASS = "test";

    @Override
    public String getJdbcHost() {
        if (mysqlContainer == null || !mysqlContainer.isRunning()) {
            throw new IllegalStateException("MySQL container not started. Call setup() first.");
        }
        // Return in the format expected by tbleSpace (without jdbc: prefix)
        return "mysql://" + mysqlContainer.getHost() + ":" + mysqlContainer.getMappedPort(3306) +
               "/" + DB_NAME +
               "?user=" + DB_USER +
               "&password=" + DB_PASS +
               "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    @Override
    public String getDriverClass() {
        // Use MariaDB driver which is already in pom.xml and supports MySQL
        return "org.mariadb.jdbc.Driver";
    }

    @Override
    public Connection getConnection() throws Exception {
        if (mysqlContainer == null || !mysqlContainer.isRunning()) {
            throw new IllegalStateException("MySQL container not started. Call setup() first.");
        }
        String url = "jdbc:mysql://" + mysqlContainer.getHost() + ":" + mysqlContainer.getMappedPort(3306) +
                     "/" + DB_NAME + "?allowPublicKeyRetrieval=true&useSSL=false";
        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }

    @Override
    public void setup() throws Exception {
        mysqlContainer = new GenericContainer<>(DockerImageName.parse("mysql:8.0.33"))
                .withExposedPorts(3306)
                .withEnv("MYSQL_DATABASE", DB_NAME)
                .withEnv("MYSQL_USER", DB_USER)
                .withEnv("MYSQL_PASSWORD", DB_PASS)
                .withEnv("MYSQL_ROOT_PASSWORD", "root")
                .withCommand("--default-authentication-plugin=mysql_native_password")
                .waitingFor(Wait.forLogMessage(".*ready for connections.*", 1))
                .withStartupTimeout(java.time.Duration.ofMinutes(3));

        mysqlContainer.start();

        // Load MariaDB JDBC driver
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
