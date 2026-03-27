package studio.phaseshift.metatron.isa.mach.io.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to simulate how IntelliJ loads the JDBC driver
 */
public class JDBCDriverLoadTest {

    @Test
    public void testDriverCanBeLoaded() {
        // This simulates what IntelliJ does when you add a JDBC driver
        assertDoesNotThrow(() -> {
            Class<?> driverClass = Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");
            assertNotNull(driverClass);
            System.out.println("Driver class loaded: " + driverClass.getName());
        }, "Should be able to load MetatronDriver class");
    }

    @Test
    public void testDriverCanBeInstantiated() {
        // This simulates creating an instance of the driver
        assertDoesNotThrow(() -> {
            Class<?> driverClass = Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            assertNotNull(driver);
            System.out.println("Driver instantiated: " + driver.getClass().getName());
        }, "Should be able to instantiate MetatronDriver");
    }

    @Test
    public void testDriverRegistration() {
        // This simulates registering the driver with DriverManager
        assertDoesNotThrow(() -> {
            Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");
            Driver driver = DriverManager.getDriver("jdbc:metatron://localhost:7777/test");
            assertNotNull(driver);
            System.out.println("Driver registered and retrieved: " + driver.getClass().getName());
        }, "Should be able to register and retrieve driver from DriverManager");
    }
}
