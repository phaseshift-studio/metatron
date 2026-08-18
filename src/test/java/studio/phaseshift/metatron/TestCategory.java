/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations to categorize tests for selective execution.
 * Test classes opt out of categories via {@link SkipRegexTest#tags()}.
 */
public class TestCategory {

    /**
     * Write/reference operations ({@code ->}).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("write")
    public @interface Write {
    }

    /**
     * Read/dereference operations ({@code *}).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("read")
    public @interface Read {
    }

    /**
     * Update semantics ({@code >>=}, field-level modification).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("update")
    public @interface Update {
    }

    /**
     * Delete operations (node becomes {@code noobj}).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("drop")
    public @interface Drop {
    }

    /**
     * Type preservation and conversion.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("type")
    public @interface Type {
    }

    /**
     * Boundary values and edge cases.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("boundary")
    public @interface Boundary {
    }

    /**
     * Nested structures (records, documents).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("nested")
    public @interface Nested {
    }

    /**
     * List/array handling.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("list")
    public @interface List {
    }

    /**
     * Concurrent operations.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("concurrent")
    public @interface Concurrent {
    }

    /**
     * Special values (unicode, special chars, etc).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("special")
    public @interface Special {
    }

    /**
     * Rootless container operations (aggregation across siblings).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("rootless")
    public @interface Rootless {
    }
}
