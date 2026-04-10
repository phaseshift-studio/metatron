package studio.phaseshift.metatron.isa.mach.io.jdbc;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.net.MClient;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * JDBC Connection to a metatron server via WebSocket.
 * <p>
 * Connects to a remote metatron server using the Native protocol over WebSocket.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MetatronConnection implements Connection {

    private final String host;
    private final int port;
    private final String defaultSpace;
    private final Properties properties;
    private MClient client;
    private boolean closed = false;
    private String currentSpace;

    public MetatronConnection(String host, int port, String defaultSpace, Properties properties) throws SQLException {
        this.host = host;
        this.port = port;
        this.defaultSpace = defaultSpace;
        this.currentSpace = defaultSpace;
        this.properties = properties;

        // Connect to metatron server via WebSocket
        try {
            String wsUrl = "ws://" + host + ":" + port;
            fURI serverUri = f(wsUrl);
            this.client = new MClient(serverUri, new ObjmtronSerializer());
            this.client.connect();

            // Wait for connection to establish (max 5 seconds)
            int retries = 50;
            while (!this.client.isOpen() && retries > 0) {
                Thread.sleep(100);
                retries--;
            }

            if (!this.client.isOpen()) {
                throw new SQLException("Failed to connect to metatron server at " + wsUrl);
            }
        } catch (Exception e) {
            throw new SQLException("Failed to connect to metatron server", e);
        }
    }

    public MClient getClient() {
        return client;
    }

    public String getCurrentSpace() {
        return currentSpace;
    }

    public void setCurrentSpace(String space) {
        this.currentSpace = space;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new MetatronStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return sql; // No translation needed
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        // Read-only, no transactions
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return true; // Always auto-commit for read-only
    }

    @Override
    public void commit() throws SQLException {
        // Read-only, no-op
    }

    @Override
    public void rollback() throws SQLException {
        // Read-only, no-op
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            if (client != null) {
                client.close();
            }
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new MetatronDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        // Always read-only for now
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return true; // Read-only driver
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        // Catalog = space in metatron
        this.currentSpace = catalog;
    }

    @Override
    public String getCatalog() throws SQLException {
        return currentSpace;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        // No transactions
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_NONE;
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
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        // No-op
    }

    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("PreparedStatement not supported yet");
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !closed;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // No-op
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // No-op
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return new Properties();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array not supported");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Struct not supported");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        // Schema = space in metatron
        this.currentSpace = schema;
    }

    @Override
    public String getSchema() throws SQLException {
        return currentSpace;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        // No-op
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
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
            throw new SQLException("Connection is closed");
        }
    }
}
