package studio.phaseshift.metatron.isa.mach.io.jdbc;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import java.sql.*;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * JDBC Statement for executing queries against metatron spaces.
 * <p>
 * Supports basic SQL queries:
 * - SELECT * FROM path
 * - SELECT * FROM path WHERE id = value
 * - SELECT field FROM path
 * <p>
 * Translates SQL to metatron patterns and executes via router.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MetatronStatement implements Statement {

    private final MetatronConnection connection;
    private boolean closed = false;
    private ResultSet currentResultSet;
    private int maxRows = 0;

    // Simple SQL patterns
    private static final Pattern SELECT_ALL = Pattern.compile(
            "SELECT\\s+\\*\\s+FROM\\s+([\\w/]+)(?:\\s+WHERE\\s+id\\s*=\\s*['\"]?([^'\"\\s]+)['\"]?)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SELECT_FIELD = Pattern.compile(
            "SELECT\\s+([\\w,\\s]+)\\s+FROM\\s+([\\w/]+)(?:\\s+WHERE\\s+id\\s*=\\s*['\"]?([^'\"\\s]+)['\"]?)?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for test/keep-alive queries like SELECT 'keep alive', SELECT 1, etc.
    private static final Pattern SELECT_LITERAL = Pattern.compile(
            "SELECT\\s+(?:'[^']*'|\"[^\"]*\"|\\d+)(?:\\s+(?:AS\\s+)?\\w+)?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for USE statement to switch spaces
    private static final Pattern USE_STATEMENT = Pattern.compile(
            "USE\\s+([\\w]+)",
            Pattern.CASE_INSENSITIVE
    );

    public MetatronStatement(MetatronConnection connection) {
        this.connection = connection;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();

        try {
            // Check if this is a USE statement
            Matcher useMatcher = USE_STATEMENT.matcher(sql.trim());
            if (useMatcher.matches()) {
                String space = useMatcher.group(1);
                connection.setCurrentSpace(space);
                // Return empty result set for USE statement
                currentResultSet = createDummyResultSet();
                return currentResultSet;
            }

            // Check if this is a test/keep-alive query
            if (SELECT_LITERAL.matcher(sql.trim()).matches()) {
                // Return a simple dummy result for keep-alive queries
                currentResultSet = createDummyResultSet();
                return currentResultSet;
            }

            // Parse SQL and convert to metatron pattern
            String pattern = translateSqlToPattern(sql);

            // Execute query via MClient - send pattern as mtron code to evaluate
            Obj queryObj = mParser.eval("*" + pattern);
            Obj resultObj = connection.getClient().sendRecvObj(queryObj).get();

            // Wrap result in a simple list for the ResultSet
            // The ResultSet will handle flattening the Obj
            java.util.List<Space.IdObj> resultList = new java.util.ArrayList<>();

            if (resultObj.isLst()) {
                // Result is a list - wrap each element
                int index = 0;
                for (Obj item : resultObj.asLst()) {
                    resultList.add(new Space.IdObj(f(pattern + "/" + index), item));
                    index++;
                }
            } else if (!resultObj.isNoObj()) {
                // Single result - wrap it
                resultList.add(new Space.IdObj(f(pattern), resultObj));
            }

            // Create and return ResultSet
            currentResultSet = new MetatronResultSet(resultList.iterator(), this);
            return currentResultSet;
        } catch (Exception e) {
            throw new SQLException("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Creates a dummy ResultSet for test/keep-alive queries.
     * Returns an empty result set that satisfies JDBC requirements.
     */
    private ResultSet createDummyResultSet() {
        java.util.List<Space.IdObj> emptyList = new java.util.ArrayList<>();
        return new MetatronResultSet(emptyList.iterator(), this);
    }

    /**
     * Translates simple SQL queries to metatron patterns.
     * <p>
     * Examples:
     * - "SELECT * FROM customers" → "space:customers/+"
     * - "SELECT * FROM customers WHERE id=357" → "space:customers/357"
     * - "SELECT name FROM customers" → "space:customers/+/name"
     */
    private String translateSqlToPattern(String sql) throws SQLException {
        String space = connection.getCurrentSpace();
        if (space == null || space.isEmpty()) {
            throw new SQLException("No space selected. Use 'USE space_name' or specify in connection URL");
        }

        // Try SELECT * FROM table [WHERE id=value]
        Matcher selectAll = SELECT_ALL.matcher(sql.trim());
        if (selectAll.matches()) {
            String table = selectAll.group(1);
            String id = selectAll.group(2);

            if (id != null) {
                // SELECT * FROM table WHERE id=value → space:table/value
                return space + ":" + table + "/" + id;
            } else {
                // SELECT * FROM table → space:table/+
                return space + ":" + table + "/+";
            }
        }

        // Try SELECT field FROM table [WHERE id=value]
        Matcher selectField = SELECT_FIELD.matcher(sql.trim());
        if (selectField.matches()) {
            String fields = selectField.group(1).trim();
            String table = selectField.group(2);
            String id = selectField.group(3);

            // For now, just handle single field
            String field = fields.split(",")[0].trim();

            if (id != null) {
                // SELECT field FROM table WHERE id=value → space:table/value/field
                return space + ":" + table + "/" + id + "/" + field;
            } else {
                // SELECT field FROM table → space:table/+/field
                return space + ":" + table + "/+/" + field;
            }
        }

        // If no pattern matches, try to use it as a direct pattern
        // This allows raw metatron patterns like "*acme:customers/+"
        if (sql.trim().startsWith("*")) {
            return sql.trim().substring(1); // Remove leading *
        }

        throw new SQLException("Unsupported SQL syntax: " + sql +
                "\nSupported: SELECT * FROM table [WHERE id=value]" +
                "\n          SELECT field FROM table [WHERE id=value]" +
                "\n          *pattern (raw metatron pattern)");
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported in read-only driver");
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            if (currentResultSet != null) {
                currentResultSet.close();
            }
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        // No-op
    }

    @Override
    public int getMaxRows() throws SQLException {
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        this.maxRows = max;
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        // No-op
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        // No-op
    }

    @Override
    public void cancel() throws SQLException {
        // No-op
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        // No-op
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Named cursors not supported");
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        executeQuery(sql);
        return true; // Always returns ResultSet
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return -1; // No updates
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false; // Only one ResultSet
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }

    @Override
    public void clearBatch() throws SQLException {
        // No-op
    }

    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("Generated keys not supported");
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        // No-op
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        // No-op
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }
}
