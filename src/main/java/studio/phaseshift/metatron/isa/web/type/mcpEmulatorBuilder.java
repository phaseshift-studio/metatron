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

package studio.phaseshift.metatron.isa.web.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.TOOL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcpEmulatorBuilder {

    private static final GraphittyLogger LOG = Graphitty.log(mcpEmulatorBuilder.class);

    private static fURI getUserDirectory(final fURI user) {
        return f("home:" + user);
    }

    public static Map<Obj, Obj> build(final Map<Obj, Obj> base, final fURI vid) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(base);

        // ── tools ────────────────────────────────────────────────────────────
        if (!jvm.containsKey(uri(TOOL))) {
            final Rec tools = rec(mutableMap());
            tools.at(uri("adduser"), docWrap(
                    instC(M_ISA_INST_TID.dom(NOOBJ_TID.zero()).rng(URI_TID), rec(uri(USER), URI_TYPE),
                            (lhs, inst) -> {
                                final fURI userDirectory = getUserDirectory(inst.arg(f(USER), 0).uriValue());
                                if (!Router.readFromSpace(userDirectory).isNoObj())
                                    throw MTronException.of("user directory already exists: %s", userDirectory);
                                Router.writeToSpace(userDirectory, rec(TOOL, rec(), RESOURCE, rec(), PROMPT, rec()));
                                return userDirectory.toUri();
                            }), "noobj lhs", "uri of the newly created home directory",
                    Map.of(uri(USER), "the name of the user to create a home directory for"),
                    "constructs a home directory in space and returns the root of that directory"), MUTABLE);
            tools.at(uri("deluser"), docWrap(
                    instC(M_ISA_INST_TID.dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()), rec(uri(USER), URI_TYPE),
                            (lhs, inst) -> {
                                final fURI userDirectory = getUserDirectory(inst.arg(f(USER), 0).uriValue());
                                if (Router.readFromSpace(userDirectory).isNoObj())
                                    throw MTronException.of("user directory does not exists: %s", userDirectory);
                                Router.writeToSpace(userDirectory.extend("#"), noobj());
                                return str("user " + inst.arg(f(USER), 0) + " deleted");
                            }), "noobj lhs", "uri of the newly created home directory",
                    Map.of(uri(USER), "the name of the user to create a home directory for"),
                    "constructs a home directory in space and returns the root of that directory"), MUTABLE);
            // install
            tools.at(uri("install"), docWrap(
                            instC(M_ISA_INST_TID.dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                                    rec(uri(USER), URI_TYPE, uri("mcpServers"), STR_TYPE),
                                    (lhs, inst) -> {
                                        final fURI userDirectory = getUserDirectory(inst.arg(f(USER), 0).uriValue());
                                        final Rec mcpServers = ObjSimpleJSONSerializer.parse(inst.arg(f("mcpServers"), 1).strValue()).asRec();
                                        mcpServers.at("mcpServers").orElse(rec0()).elements().forEach(server -> {
                                            final String serverName = Str.Helper.cleanString(server.first());
                                            final Rec serverConfig = server.second().asRec();
                                            final Rec jsonServerConfig = serverConfig.as(JSON_TYPE).as();
                                            final Inst asInst = Router.readFromSpace(AS_INST_TID.dom(JSON_TID).rng(MCP_CLIENT_TID)).stream().findFirst().orElse(noobj()).asInst();
                                            LOG.debug("serverConfig: %s\njsonServerConfig: %s\nas-inst: %s", serverConfig, jsonServerConfig, asInst);
                                            final Obj mcpClient = asInst.apply(jsonServerConfig);
                                            LOG.debug("mcpClient: %s", mcpClient);
                                            Router.writeToSpace(userDirectory.extend(TOOL).extend(serverName), mcpClient);
                                        });
                                        return Router.readFromSpace(userDirectory.extend(TOOL).extend("+/"));
                                    }),
                            "noobj lhs", "summary of installed servers with tool/resource/prompt counts",
                            Map.of(uri(USER), "the user to install servers for",
                                    uri("mcpServers"), "mcpServers json snippet — keys are server names, values have command/args/env/host/transport/headers"),
                            "spawns mcp clients from config, discovers tools/resources/prompts, links them into the user's home directory"),
                    MUTABLE);
            // tools_list
            tools.at(uri("tools_list"),
                    docWrap(instC(M_ISA_INST_TID.dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                                    rec(uri(USER), URI_TYPE),
                                    (lhs, inst) -> from_(getUserDirectory(inst.arg(f(USER), 0).uriValue()).extend(TOOL).extend("+/").toUri()).apply()),
                            "noobj lhs", "emulated mcp tools currently available to user",
                            Map.of(uri(USER), "the user of the mcp emulator"),
                            "returns the emulated tools currently available to the user"), MUTABLE);
            // tools_call
            tools.at(uri("tools_call"), docWrap(instC(M_ISA_INST_TID.dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                            rec(uri(USER), URI_TYPE, uri(NAME), URI_TYPE, uri(ARGS), REC_TYPE),
                            (lhs, inst) -> {
                                final fURI userDir = getUserDirectory(inst.arg(f(USER), 0).uriValue());
                                final fURI toolName = inst.arg(f(NAME), 1).uriValue();
                                final Obj toolArgs = inst.arg(f(ARGS), 2);
                                final Obj tool = from_(userDir.extend(TOOL).extend(toolName).toUri()).apply();
                                LOG.debug("tool call: %s", tool);
                                if (!tool.isInst())
                                    throw MTronException.of("tool reference is not a tool: %s", tool);
                                return tool.asInst().args(toolArgs.as()).apply(noobj());
                            }),
                    "noobj lhs", "the result of an emulated mcp tool call",
                    Map.of(uri(USER), "the user of the mcp emulator",
                            uri(NAME), "the name of the emulated tool to call",
                            uri(ARGS), "the arguments for the tool to call"),
                    "returns the result of calling the emulated tool"), MUTABLE);
            /////////////////////////////////////////////////////////////////////////////////////
            jvm.put(uri(TOOL), tools);
        }
        return jvm;
    }
}
