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

package studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as an MCP tool parameter.
 *
 * The field will be automatically populated from the tool call arguments.
 * The JSON schema will be auto-generated based on the field type and annotations.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface McpParameter {

    /**
     * The name of the parameter in the JSON schema.
     * If not specified, uses the field name.
     */
    String name() default "";

    /**
     * A description of the parameter.
     */
    String description();

    /**
     * Whether this parameter is required.
     */
    boolean required() default true;

    /**
     * Default value as a string (will be converted to field type).
     */
    String defaultValue() default "";
}
