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
 * Marks a class as an MCP tool.
 *
 * The class must have a method annotated with @McpHandler that returns McpSchema.CallToolResult.
 * Parameters can be injected via @McpParameter annotated fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface McpTool {

    /**
     * The name of the tool (e.g., "evaluate_code").
     */
    String name();

    /**
     * A description of what the tool does.
     */
    String description();

    /**
     * Optional category for grouping tools.
     */
    String category() default "";
}
