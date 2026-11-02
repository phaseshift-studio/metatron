/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m.ai.llm.type.mcp;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.ai.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.MCP_SERVER_TID;

/**
 * Static builder provider for MCP server types.
 * <p>
 * {@link Builder} DELEGATES to {@link TypeBuilder} with a cumulative {@code tool(Inst)} step: each tool inst is keyed
 * under its {@code mTool} name, and {@code create()} produces the {@code mcp_server::T} type whose constructor
 * materializes the {@code mcpServer} (see {@code mcpMessageServer.createType()} for a complete example).
 * <p>
 * It is a WRAPPER, not a subclass: a type builder is {@code final} because {@code create()} is the one place a type
 * is composed and queued (a subclass that widened the composition would publish a type nobody declared). The fluent
 * steps this class adds are delegation, and {@code create()} is the builder's own — never a second composition.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MCPServerUtil {

    private MCPServerUtil() {
        // do nothing
    }


    // ========================================
    // Builder
    // ========================================

    public static class Builder {

        /**
         * The tools this server type declares, keyed by their {@code mTool} name.
         */
        public final Rec tools = rec();

        /**
         * The type being declared — every fluent step below is this builder's.
         */
        private final TypeBuilder builder = TypeBuilder.build().tid(MCP_SERVER_TID);

        public static Builder build() {
            return new Builder();
        }

        public Builder tool(final Inst inst) {
            this.tools.at(uri(mTool.toolName(inst.asInst().tid())), inst, Rec.MUTABLE);
            return this;
        }

        public Builder vid(final fURI vid) {
            this.builder.vid(vid);
            return this;
        }

        public Builder tid(final fURI tid) {
            this.builder.tid(tid);
            return this;
        }

        public Type create() {
            return this.builder.create();
        }
    }
}
