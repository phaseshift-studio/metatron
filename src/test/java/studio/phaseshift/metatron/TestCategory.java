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

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations to categorize tests for selective execution.
 * Test classes can use @SkipInheritedTests with these tags to include/exclude specific test categories.
 */
public class TestCategory {

    /**
     * Tests for basic CRUD operations (Create, Read, Update, Delete).
     * Corresponds to {@link TestTag#CRUD}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("crud")
    public @interface Crud {
    }

    /**
     * Tests for read and write operations.
     * Corresponds to {@link TestTag#READ_WRITE}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("read_write")
    public @interface ReadWrite {
    }

    /**
     * Tests for type preservation and conversion.
     * Corresponds to {@link TestTag#TYPE}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("type")
    public @interface Type {
    }

    /**
     * Tests for boundary values and edge cases.
     * Corresponds to {@link TestTag#BOUNDARY}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("boundary")
    public @interface Boundary {
    }

    /**
     * Tests for pattern matching functionality.
     * Corresponds to {@link TestTag#PATTERN}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("pattern")
    public @interface Pattern {
    }

    /**
     * Tests for nested structures (records, documents).
     * Corresponds to {@link TestTag#NESTED}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("nested")
    public @interface Nested {
    }

    /**
     * Tests for list/array handling.
     * Corresponds to {@link TestTag#LIST}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("list")
    public @interface List {
    }

    /**
     * Tests for concurrent operations.
     * Corresponds to {@link TestTag#CONCURRENT}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("concurrent")
    public @interface Concurrent {
    }

    /**
     * Tests for special values (unicode, special chars, etc).
     * Corresponds to {@link TestTag#SPECIAL}.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("special")
    public @interface Special {
    }
}
