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

package studio.phaseshift.metatron.isa.llm.type.mcp;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;

/**
 * Static builder provider for MCP server types.
 * <p>
 * {@link Builder} extends {@code Type.Builder} with a cumulative
 * {@code tool(Inst)} step: each tool inst is keyed under its {@code mTool}
 * name, and {@code Type.Builder#create()} produces the {@code mcp_server::T}
 * type whose constructor materializes the {@code mcpServer} (see
 * {@code mcpMessageServer.createType()} for a complete example).
 * <p>
 * Note: do not add a {@code create()} override here — it would shadow
 * {@code Type.Builder#create()} and re-dispatch to itself.
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

    public static class Builder extends Type.Builder {

        public final Rec tools = rec();

        public static Builder build() {
            return new Builder().tid(MCP_SERVER_TID);
        }

        public Builder tool(final Inst inst) {
            this.tools.at(uri(mTool.toolName(inst.asInst().tid())), inst, Rec.MUTABLE);
            return this;
        }

        public Builder vid(final fURI vid) {
            return (Builder) super.vid(vid);
        }

        public Builder tid(final fURI vid) {
            return (Builder) super.tid(vid);
        }
    }
}
