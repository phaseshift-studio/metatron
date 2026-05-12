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

import java.sql.Connection;

/**
 * Configuration interface for database-specific test setup.
 * Each database implementation (SQLite, MySQL, PostgreSQL, etc.) provides
 * its own configuration for connection strings, drivers, and lifecycle management.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface DatabaseConfig {

    /**
     * Get the JDBC connection string for this database.
     * Example: "sqlite:target/test.db" or "mysql://localhost:3306/testdb"
     */
    String getJdbcHost();

    /**
     * Get the JDBC driver class name.
     * Example: "org.sqlite.JDBC" or "com.mysql.cj.jdbc.Driver"
     */
    String getDriverClass();

    /**
     * Get a JDBC connection for direct database operations.
     */
    Connection getConnection() throws Exception;

    /**
     * Initialize the database (start containers, create files, etc.).
     * Called once before all tests.
     */
    void setup() throws Exception;

    /**
     * Clean up the database (stop containers, delete files, etc.).
     * Called once after all tests.
     */
    void teardown() throws Exception;

    /**
     * Get the database name for logging/identification.
     */
    String getDatabaseName();

    /**
     * Get SQL for creating the users table.
     * Different databases may have different syntax for data types.
     */
    default String getUsersTableDDL() {
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

    /**
     * Get SQL for creating the products table.
     */
    default String getProductsTableDDL() {
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

    /**
     * Get SQL for creating the people table.
     * Used by testMonoUpdate — schema matches the seed data in AbstractSpaceTest.
     */
    default String getPeopleTableDDL() {
        return """
               CREATE TABLE people (
                   id INTEGER PRIMARY KEY,
                   name TEXT NOT NULL,
                   age INTEGER,
                   title TEXT,
                   salary REAL,
                   company INTEGER,
                   active INTEGER
               )
               """;
        // REFERENCES companies(id)
    }

    /**
     * Get SQL for creating the companies table.
     * Used by testMonoUpdate — referenced via company FK from people table.
     */
    default String getCompaniesTableDDL() {
        return """
               CREATE TABLE companies (
                   id INTEGER PRIMARY KEY,
                   name TEXT NOT NULL,
                   city TEXT,
                   employees INTEGER,
                   public INTEGER
               )
               """;
    }

    /**
     * Get SQL for creating the rewrite_test table.
     */
    default String getRewriteTestTableDDL() {
        return """
               CREATE TABLE rewrite_test (
                   id INTEGER PRIMARY KEY,
                   value INTEGER NOT NULL,
                   name TEXT NOT NULL,
                   active INTEGER NOT NULL
               )
               """;
    }

    /**
     * Whether this database supports auto-increment primary keys.
     */
    default boolean supportsAutoIncrement() {
        return true;
    }

    /**
     * Get the boolean true value for this database (1 for SQLite, TRUE for MySQL/PostgreSQL).
     */
    default int getBooleanTrue() {
        return 1;
    }

    /**
     * Get the boolean false value for this database (0 for SQLite, FALSE for MySQL/PostgreSQL).
     */
    default int getBooleanFalse() {
        return 0;
    }
}
