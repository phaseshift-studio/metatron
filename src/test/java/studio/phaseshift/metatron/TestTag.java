/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron;

/**
 * Enumeration of test tags for categorizing and filtering tests.
 * These tags can be used with @SkipInheritedTests to exclude groups of tests.
 */
public enum TestTag {
    /**
     * Tests for basic CRUD operations (Create, Read, Update, Delete).
     */
    CRUD("crud"),

    /**
     * Tests for read and write operations.
     */
    READ_WRITE("read_write"),

    /**
     * Tests for type preservation and conversion.
     */
    TYPE("type"),

    /**
     * Tests for boundary values and edge cases.
     */
    BOUNDARY("boundary"),

    /**
     * Tests for pattern matching functionality.
     */
    PATTERN("pattern"),

    /**
     * Tests for nested structures (records, documents).
     */
    NESTED("nested"),

    /**
     * Tests for list/array handling.
     */
    LIST("list"),

    /**
     * Tests for concurrent operations.
     */
    CONCURRENT("concurrent"),

    /**
     * Tests for special values (unicode, special chars, etc).
     */
    SPECIAL("special");

    private final String tagName;

    TestTag(String tagName) {
        this.tagName = tagName;
    }

    /**
     * Get the string representation of this tag (used by JUnit @Tag).
     */
    public String getTagName() {
        return tagName;
    }

    @Override
    public String toString() {
        return tagName;
    }
}
