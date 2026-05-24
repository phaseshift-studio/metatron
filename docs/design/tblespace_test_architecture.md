p# TbleSpace Test Infrastructure

This directory contains a generalized test infrastructure for testing `tbleSpace` with multiple relational databases.

## Architecture

The test infrastructure is designed to be database-agnostic, allowing the same test suite to run against different databases (SQLite, MySQL, PostgreSQL, etc.).

### Components

1. **`DatabaseConfig`** - Interface defining database-specific configuration
   - JDBC connection details
   - DDL statements for table creation
   - Database lifecycle management (setup/teardown)

2. **`AbstractTbleSpaceTest`** - Abstract base test class
   - Contains all common database-agnostic tests
   - Implements `CommonRewritesTestContract`
   - Provides helper methods for test setup/cleanup

3. **Database-specific configurations:**
   - `SqliteDatabaseConfig` - File-based SQLite database
   - `MySQLDatabaseConfig` - MySQL via TestContainers
   - `PostgreSQLDatabaseConfig` - PostgreSQL via TestContainers

4. **Concrete test classes:**
   - `SqliteTbleSpaceTest` - Runs all tests against SQLite
   - `MySQLTbleSpaceTest` - Runs all tests against MySQL
   - `PostgreSQLTbleSpaceTest` - Runs all tests against PostgreSQL (example)

## How It Works

Each concrete test class:
1. Extends `AbstractTbleSpaceTest`
2. Provides a `DatabaseConfig` implementation in its constructor
3. Inherits all common tests automatically
4. Can add database-specific tests if needed

Example:
```java
public class MySQLTbleSpaceTest extends AbstractTbleSpaceTest {
    public MySQLTbleSpaceTest() {
        super(new MySQLDatabaseConfig());
    }
    // All tests inherited from AbstractTbleSpaceTest
}
```

## Adding a New Database

To add support for a new database (e.g., Oracle, SQL Server):

1. **Create a configuration class** implementing `DatabaseConfig`:
   ```java
   public class OracleDatabaseConfig implements DatabaseConfig {
       // Implement all methods
   }
   ```

2. **Create a test class** extending `AbstractTbleSpaceTest`:
   ```java
   public class OracleTbleSpaceTest extends AbstractTbleSpaceTest {
       public OracleTbleSpaceTest() {
           super(new OracleDatabaseConfig());
       }
   }
   ```

3. **Run the tests** - All common tests will automatically run against the new database!

## Test Categories

The inherited tests cover:
- **Field-level operations** - Read/write individual fields
- **Row-level operations** - Read/write entire rows
- **Type conversions** - Boolean, integer, real, string edge cases
- **Insert operations** - Creating new rows
- **Update operations** - Modifying existing data
- **Comprehensive operations** - Complex multi-step scenarios

## TestContainers

MySQL and PostgreSQL configurations use [TestContainers](https://www.testcontainers.org/) to automatically:
- Start a database container before tests
- Stop and clean up after tests
- Provide isolated test environments

## Benefits

1. **DRY Principle** - Write tests once, run against all databases
2. **Easy Extension** - Add new databases with minimal code
3. **Consistency** - Same test coverage across all databases
4. **Isolation** - Each database test is independent
5. **CI/CD Ready** - TestContainers work in CI environments
