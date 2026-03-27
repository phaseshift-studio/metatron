package studio.phaseshift.metatron.isa.mach.io.jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC Driver for metatron spaces.
 * <p>
 * Connection URL format: jdbc:metatron://host:port/space
 * Example: jdbc:metatron://localhost:7777/acme
 * <p>
 * This is a read-only driver for browsing metatron data in IntelliJ Database Tools.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MetatronDriver implements Driver {

    private static final String URL_PREFIX = "jdbc:metatron:";
    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

    static {
        try {
            DriverManager.registerDriver(new MetatronDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register MetatronDriver", e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        try {
            // Parse URL: jdbc:metatron://host:port/space
            String cleanUrl = url.substring(URL_PREFIX.length());
            if (cleanUrl.startsWith("//")) {
                cleanUrl = cleanUrl.substring(2);
            }

            String[] parts = cleanUrl.split("/", 2);
            String hostPort = parts[0];
            String space = parts.length > 1 ? parts[1] : "";

            String[] hostPortParts = hostPort.split(":");
            String host = hostPortParts[0];
            int port = hostPortParts.length > 1 ? Integer.parseInt(hostPortParts[1]) : 7777;

            return new MetatronConnection(host, port, space, info);
        } catch (Exception e) {
            throw new SQLException("Failed to connect to metatron: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return false; // We don't support full SQL
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(MetatronDriver.class.getName());
    }
}
