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

package studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.annotated;

import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpHandler;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpParameter;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.List;

/**
 * Example of annotation-based tool with multiple parameters.
 *
 * Demonstrates:
 * - Multiple parameters
 * - Optional parameters with defaults
 * - Different parameter types (String, int, boolean)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@McpTool(
        name = "query_data",
        description = "Query metatron data sources with optional filtering and limits.",
        category = "query"
)
public class AnnotatedQueryTool {

    private static final GraphittyLogger LOG = Graphitty.log(AnnotatedQueryTool.class);

    @McpParameter(
            name = "query",
            description = "The metatron query expression to execute",
            required = true
    )
    private String query;

    @McpParameter(
            name = "limit",
            description = "Maximum number of results to return",
            required = false,
            defaultValue = "100"
    )
    private int limit;

    @McpParameter(
            name = "format",
            description = "Output format: 'compact' or 'verbose'",
            required = false,
            defaultValue = "compact"
    )
    private String format;

    @McpParameter(
            name = "include_metadata",
            description = "Whether to include metadata in results",
            required = false,
            defaultValue = "false"
    )
    private boolean includeMetadata;

    @McpHandler
    public Obj execute() {
        LOG.debug("executing query: %s (limit=%d, format=%s, metadata=%b)",
                query, limit, format, includeMetadata);

        // Execute the query - just return the Obj!
        // The framework will convert it to structured JSON
        final Obj queryObj = mParser.parse(query);
        final Obj result = queryObj.apply();

        // Could optionally wrap result with metadata if needed
        // For now, just return the raw result
        // The LLM will get structured JSON automatically
        return result;
    }
}
