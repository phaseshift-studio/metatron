package studio.phaseshift.metatron.isa.mach.io.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JDBC driver loading with assertions explicitly enabled
 */
public class JDBCDriverWithAssertionsTest {

    static {
        // Enable assertions programmatically
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
    }

    @Test
    public void testDriverLoadWithAssertions() {
        System.out.println("Assertions enabled: " + JDBCDriverWithAssertionsTest.class.desiredAssertionStatus());

        assertDoesNotThrow(() -> {
            Class<?> driverClass = Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            assertNotNull(driver);
            System.out.println("Driver loaded successfully with assertions enabled");
        }, "Should be able to load driver even with assertions enabled");
    }

    @Test
    public void testSerializerLoadWithAssertions() {
        System.out.println("Testing ObjSimpleJSONSerializer with assertions...");

        assertDoesNotThrow(() -> {
            Class<?> serializerClass = Class.forName("studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer");
            Object serializer = serializerClass.getDeclaredConstructor().newInstance();
            assertNotNull(serializer);
            System.out.println("ObjSimpleJSONSerializer loaded successfully");
        }, "Should be able to load ObjSimpleJSONSerializer");
    }
}
