/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations to categorize Space tests for selective execution.
 * Subclasses can use @Tag to include/exclude specific test categories.
 */
public class SpaceTestCategory {

    /**
     * Tests for basic CRUD operations (Create, Read, Update, Delete).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("crud")
    public @interface Crud {
    }

    /**
     * Tests for type preservation and conversion.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("type")
    public @interface Type {
    }

    /**
     * Tests for boundary values and edge cases.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("boundary")
    public @interface Boundary {
    }

    /**
     * Tests for pattern matching functionality.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("pattern")
    public @interface Pattern {
    }

    /**
     * Tests for nested structures (records, documents).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("nested")
    public @interface Nested {
    }

    /**
     * Tests for list/array handling.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("list")
    public @interface List {
    }

    /**
     * Tests for concurrent operations.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("concurrent")
    public @interface Concurrent {
    }

    /**
     * Tests for special values (unicode, special chars, etc).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("special")
    public @interface Special {
    }
}
