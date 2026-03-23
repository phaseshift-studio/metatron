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
