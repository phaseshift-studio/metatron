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

package studio.phaseshift.metatron.isa.m.db.tble;

import org.testcontainers.containers.MariaDBContainer;
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
                .withPassword("testpass")
                // MariaDB Connector/J 4.0 enables TLS by default; 3.x did not. The
                // mariadb:11.2 image ships no certificates, so `have_ssl` is DISABLED
                // and the server has nothing to negotiate. Without this every
                // connection fails with "Trying to connect with ssl, but ssl not
                // enabled in the server" -- including the container's own readiness
                // probe, which is a JDBC connection (see setup()).
                .withUrlParam("sslMode", "disable");
    }

    @Override
    public String getJdbcHost() {
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("MariaDB container not started. Call setup() first.");
        }
        // Return in the format expected by tbleSpace (without jdbc: prefix).
        // sslMode=disable must ride along: tbleSpace hands this straight to
        // DriverManager, and Connector/J 4.0 would otherwise demand TLS.
        return "mariadb://" + container.getHost() + ":" + container.getFirstMappedPort() +
                "/" + container.getDatabaseName() +
                "?user=" + container.getUsername() +
                "&password=" + container.getPassword() +
                "&sslMode=disable";
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
        // A JdbcDatabaseContainer is ready when it can be CONNECTED to, not when a log
        // line or a listening port appears: its readiness gate is a JDBC poll bounded by
        // withStartupTimeoutSeconds (120s by default), and a WaitStrategy installed here
        // is never consulted. A log wait would also be wrong on its own terms -- the
        // mariadb entrypoint prints "ready for connections" from the temporary
        // initialisation server long before the real one is up (the same trap documented
        // in MySQLDatabaseConfig). MariaDBContainer retries a failed start three times,
        // so a probe that can never succeed costs 3 x 120s = ~6 minutes before the
        // reported "Could not create/start container" / "Retry limit hit".
        container.withStartupTimeoutSeconds(180);
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
