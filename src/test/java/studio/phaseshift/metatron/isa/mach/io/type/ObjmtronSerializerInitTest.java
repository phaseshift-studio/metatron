package studio.phaseshift.metatron.isa.mach.io.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to isolate ObjmtronSerializer initialization issues
 */
public class ObjmtronSerializerInitTest {

    @Test
    public void testClassCanLoad() {
        // This should work if the class can be loaded at all
        assertDoesNotThrow(() -> {
            Class.forName("studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer");
        }, "ObjmtronSerializer class should load without errors");
    }

    @Test
    public void testCanInstantiate() {
        // This should work if we can create an instance
        assertDoesNotThrow(() -> {
            ObjmtronSerializer serializer = new ObjmtronSerializer();
            assertNotNull(serializer);
        }, "Should be able to instantiate ObjmtronSerializer");
    }

    @Test
    public void testVidAccessible() {
        // This should work if the VID is properly initialized
        assertDoesNotThrow(() -> {
            ObjmtronSerializer serializer = new ObjmtronSerializer();
            assertNotNull(serializer.vid());
            assertEquals("/m/mach/io/serializer/string/clean", serializer.vid().toString());
        }, "VID should be accessible and correct");
    }

    @Test
    public void testBasicSerialization() {
        // This should work if basic serialization works
        assertDoesNotThrow(() -> {
            ObjmtronSerializer serializer = new ObjmtronSerializer();
            // Test will be added once we verify instantiation works
        }, "Basic serialization should work");
    }
}
