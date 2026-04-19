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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * SQLite database configuration for testing.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SqliteDatabaseConfig implements DatabaseConfig {

    private final String dbPath;

    public SqliteDatabaseConfig(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public String getJdbcHost() {
        return "sqlite:" + dbPath;
    }

    @Override
    public String getDriverClass() {
        return "org.sqlite.JDBC";
    }

    @Override
    public Connection getConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @Override
    public void setup() throws Exception {
        // Load SQLite JDBC driver
        Class.forName(getDriverClass());

        // Delete existing test database
        final File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Override
    public void teardown() throws Exception {
        // Clean up database file
        final File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Override
    public String getDatabaseName() {
        return "SQLite";
    }

    @Override
    public String getUsersTableDDL() {
        return """
               CREATE TABLE users (
                   id INTEGER PRIMARY KEY,
                   name TEXT,
                   age INTEGER,
                   salary REAL,
                   active INTEGER,
                   email TEXT
               )
               """;
    }

    @Override
    public String getProductsTableDDL() {
        return """
               CREATE TABLE products (
                   id INTEGER PRIMARY KEY,
                   product_name TEXT,
                   price REAL,
                   in_stock INTEGER,
                   quantity INTEGER,
                   category TEXT
               )
               """;
    }

    @Override
    public String getRewriteTestTableDDL() {
        return """
               CREATE TABLE rewrite_test (
                   id INTEGER PRIMARY KEY,
                   value INTEGER NOT NULL,
                   name TEXT NOT NULL,
                   active INTEGER NOT NULL
               )
               """;
    }
}
