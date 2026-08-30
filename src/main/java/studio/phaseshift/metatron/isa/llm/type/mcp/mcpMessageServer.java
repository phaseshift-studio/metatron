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

package studio.phaseshift.metatron.isa.llm.type.mcp;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATETIME_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler.WS_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The MCP server for an agent's message ledger.
 * <p>
 * {@code mcp_message_server} exposes an agent's chronological message ledger
 * ({@code <root>/message}) over MCP so that remote harness memory can be
 * appended ({@code add_message}), read back (latest first,
 * {@code get_messages}), and searched by pattern ({@code
 * search_messages}). Reads are bounded: default window {@value
 * #DEFAULT_MAX_MESSAGES}, hard ceiling {@value #HARD_MAX_MESSAGES}.
 * <p>
 * The ledger is discriminated by the message tid and carries the envelope
 * {@code text, time, session, depth, chat_id}:
 * <ul>
 *   <li>{@code user} — a single content message; optional {@code name} is the
 *       sender identity (multi-user conversations)</li>
 *   <li>{@code ai} — the assistant response; may carry {@code tool_requests}
 *       (each: {@code name} uri, {@code args} str, {@code contents} call id,
 *       {@code text} the rendered name(args) summary)</li>
 *   <li>{@code system} — model-facing instructions / context</li>
 *   <li>{@code thinking} — inner (reasoning) message</li>
 *   <li>{@code tool_result} — a tool's execution result (per
 *       {@code LLM_TOOL_RESULT_MESSAGE_TYPE}): {@code name} (the executed
 *       tool, uri — required) plus {@code contents} (the call id, the
 *       correlation key to the ai tool_request that requested it — the type
 *       also knows this field as {@code id})</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcpMessageServer {

    //public static final fURI MCP_MESSAGE_SERVER_TID = LLM_ISA_TID.extend("mcp").extend("mcp_message_server");
    public static final fURI MCP_MESSAGE_HTTP_TID = LLM_ISA_TID.extend("mcp").extend("mcp_message_http");
    public static final fURI MCP_MESSAGE_WS_TID = LLM_ISA_TID.extend("mcp").extend("mcp_message_ws");
    //public static final Type MCP_MESSAGE_SERVER_TYPE = mcpMessageServer.createType();
    public static final Type MCP_MESSAGE_WS_TYPE = mcpMessageServer.wsHandler();
    public static final Type MCP_MESSAGE_HTTP_TYPE = mcpMessageServer.httpHandler();

    /**
     * Default read window for {@code get_messages} / {@code search_messages}.
     */
    public static final int DEFAULT_MAX_MESSAGES = 50;

    /**
     * Hard ceiling for a single read — the ledger grows; keep remote reads bounded.
     */
    public static final int HARD_MAX_MESSAGES = 500;

    public static Type wsHandler() {
        return Type.Builder.build()
                .tid(WS_MCP_HANDLER_TID)
                .vid(MCP_MESSAGE_WS_TID)
                .isaPredicate(rec(
                        uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                        uri(RESOURCE).maybe().asUri(), T(ALL),
                        uri(PROMPT).maybe().asUri(), T(ALL)))
                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MCP_MESSAGE_WS_TID), lst(REC_TYPE), (lhs, inst) -> {
                    final Rec config = inst.arg(0).asRec();
                    return new mcp_wsHandler(rec(generateMcpConfig(MCP_MESSAGE_WS_TID, config.jvm()), REC_TID, config.vid()).asRec());
                })).create();
    }

    public static Type httpHandler() {
        return Type.Builder.build()
                .tid(HTTP_MCP_HANDLER_TID)
                .vid(MCP_MESSAGE_HTTP_TID)
                .isaPredicate(rec(
                        uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                        uri(RESOURCE).maybe().asUri(), T(ALL),
                        uri(PROMPT).maybe().asUri(), T(ALL)))
                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MCP_MESSAGE_HTTP_TID), lst(REC_TYPE), (lhs, inst) -> {
                    final Rec config = inst.arg(0).asRec();
                    return new mcp_httpHandler(rec(generateMcpConfig(MCP_MESSAGE_HTTP_TID, config.jvm()), REC_TID, config.vid()).asRec());
                })).create();
    }

    private static Map<Obj, Obj> generateMcpConfig(final fURI vid, final Map<Obj, Obj> config) {
        final Map<Obj, Obj> newConfig = mutableMap();
        newConfig.putAll(config);
        final Rec tools = rec(mutableMap());
        final Inst addMessage = docWrap(instC(vid.extend("add_message").dom(ALL.maybe()).rng(MESSAGE_TID), rec(
                ROOT, URI_TYPE,
                KIND, isa_(union_(lst(uri(USER), uri(AI), uri(SYSTEM), uri("thinking"), uri("tool_result")))).tryToInst(),
                TEXT, STR_TYPE,
                SESSION, T(URI_TID.maybe()),
                NAME, T(STR_TID.maybe()),
                CONTENTS, T(STR_TID.maybe()),
                CHAT_ID, T(INT_TID.maybe()),
                TIME, T(STR_TID.maybe()),
                TOOL_REQUESTS, T(LST_TID.maybe()),
                ATTRIBUTES, T(REC_TID.maybe())
        ), (lhs, inst) -> addMessage(inst)), "add a chat message");
        final Inst getMessages = docWrap(instC(vid.extend("get_messages").dom(ALL.maybe()).rng(LST_TID), rec(
                ROOT, URI_TYPE,
                SESSION, T(URI_TID.maybe()),
                MAX, T(INT_TID.maybe())
        ), (lhs, inst) -> readMessages(inst, 1, 2)), "get chat messages");
        final Inst searchMessages = docWrap(instC(vid.extend("search_messages").dom(ALL.maybe()).rng(LST_TID), rec(
                ROOT, URI_TYPE,
                PATTERN, STR_TYPE,
                SESSION, T(URI_TID.maybe()),
                MAX, T(INT_TID.maybe())
        ), (lhs, inst) -> searchMessages(inst)), "search chat messages");
        tools.at(mTool.toolName(addMessage.asInst().tid()), addMessage, MUTABLE);
        tools.at(mTool.toolName(getMessages.asInst().tid()), getMessages, MUTABLE);
        tools.at(mTool.toolName(searchMessages.asInst().tid()), searchMessages, MUTABLE);
        newConfig.put(uri(TOOL), tools);
        return newConfig;
    }

    // ========================================
    // Tool implementations
    // ========================================

    private static Obj addMessage(final Inst inst) {
        final Uri messageKind = uri(Str.Helper.cleanString(inst.arg(KIND, 1), true));
        final String text = Str.Helper.cleanString(inst.arg(TEXT, 2), true);
        final Obj session = inst.arg(SESSION, 3);
        final Obj name = inst.arg(NAME, 4);
        final Obj contents = inst.arg(CONTENTS, 5);
        final Obj chatId = inst.arg(CHAT_ID, 6);
        final Obj time = inst.arg(TIME, 7);
        final Obj toolRequests = inst.arg(TOOL_REQUESTS, 8);
        final Obj attributes = inst.arg(ATTRIBUTES, 9);

        final MessageBuilder builder;
        if (messageKind.equals(uri(USER))) {
            builder = MessageBuilder.build(USER_MESSAGE_TID).text(text);
            if (!name.isNoObj())
                builder.put(NAME, str(name.toCleanString())); // user ledger name = sender identity (str)
        } else if (messageKind.equals(uri(SYSTEM))) {
            builder = MessageBuilder.build(SYSTEM_MESSAGE_TID).text(text);
        } else if (messageKind.equals(uri("thinking"))) {
            builder = MessageBuilder.build(THINKING_MESSAGE_TID).text(text);
        } else if (messageKind.equals(uri(AI))) {
            builder = MessageBuilder.build(AI_MESSAGE_TID).text(text);
            if (!toolRequests.isNoObj())
                builder.put(TOOL_REQUESTS, buildToolRequests(toolRequests));
        } else if (messageKind.equals(uri("tool_result"))) {
            builder = MessageBuilder.build(TOOL_RESULT_MESSAGE_TID).text(text);
            if (!name.isNoObj())
                builder.put(NAME, uri(name.toCleanString())); // tool_result ledger name is uri::T (the tool)
            if (!contents.isNoObj())
                builder.contents(contents.toCleanString()); // call id — the join key to the requesting ai tool_request
        } else {
            throw MTronException.of("unknown kind of message: %s", messageKind);
        }

        // envelope — the native ledger carries text, time, session, depth, chat_id
        builder.depth(1); // top-level remote message (dsh-mtron bus convention: top-level depth is 1)
        if (!session.isNoObj())
            builder.session(uri(Str.Helper.cleanString(session, true)).uriValue());
        if (!chatId.isNoObj())
            builder.chatId(chatId.asInt().intValue().intValue());
        if (!time.isNoObj())
            builder.time(uri(f(Str.Helper.cleanString(time, true)), MATH_DATETIME_TID, null).uriValue());
        else
            builder.time();
        // TODO: ISO-8601 string -> metatron datetime literal conversion is unverified;
        //  for now `time` is accepted as a mtron datetime literal (or omitted for now())
        if (!attributes.isNoObj())
            attributes.asRec().jvm().forEach((k, v) -> builder.put(k.toString(), v));

        // write to <root>/message/_?incrq and return the written rec (vid assigned by the space)
        return builder.create(f(Str.Helper.cleanString(inst.arg(ROOT, 0), true)).extend(MESSAGE).extend("_").addQ(INCRQ));
    }

    private static Lst buildToolRequests(final Obj toolRequests) {
        final List<Obj> built = new ArrayList<>();
        toolRequests.<Lst>as().lstValue().forEach(entry -> {
            final Rec entryRec = entry.asRec();
            final String toolName = entryRec.at(uri(NAME)).toCleanString();
            final Obj toolArgs = entryRec.at(uri(ARGS));
            final Obj callId = entryRec.at(uri(CONTENTS));
            final Map<Obj, Obj> jvm = new LinkedHashMap<>();
            jvm.put(uri(NAME), uri(toolName)); // name is uri::T
            if (!toolArgs.isNoObj())
                jvm.put(uri(ARGS), str(toolArgs.toCleanString())); // args is str (JSON, e.g. {"0":"..."})
            if (!callId.isNoObj())
                jvm.put(uri(CONTENTS), str(callId.toCleanString())); // the tool execution request id
            // text is required on tool_request — the formatted name(args) summary
            jvm.put(uri(TEXT), str(toolName + (toolArgs.isNoObj() ? "" : "(" + toolArgs.toCleanString() + ")")));
            built.add(rec(jvm, TOOL_REQUEST_MESSAGE_TID, null));
        });
        return lst(built);
        // TODO: the native ai writer also puts an mtron-rendered copy of tool_requests into the
        //  ai message's `contents` (see the live ledger); the Obj -> mtron-string serializer
        //  idiom is unverified, so that dual write is withheld until the idiom is found
    }

    private static Obj readMessages(final Inst inst, final int sessionIdx, final int maxIdx) {
        final fURI path = f(Str.Helper.cleanString(inst.arg(ROOT, 0))).extend(MESSAGE).extend("+");
        final Obj session = inst.arg(SESSION, sessionIdx);
        final int max = Math.min(inst.arg(MAX, maxIdx).orElse(jnt(DEFAULT_MAX_MESSAGES)).asInt().intValue().intValue(), HARD_MAX_MESSAGES);
        final Obj ordered = session.isNoObj()
                ? at_(uri(path))/*.order_(rshift_(uri(TIME)))*/.apply()
                : at_(uri(path)).where_(rec(SESSION, uri(Str.Helper.cleanString(session, true))))/*.order_(rshift_(uri(TIME)))*/.apply();
        // The stream is relied on to be in the ledger's natural (append) order — the
        // ledger is append-only and the space returns records in insertion order.
        // (An explicit order_(rshift_(TIME)) clause is kept commented out above as an
        // alternative; order_ sorts ascending, so the latest N is still the tail, reversed.)
        return newestFirst(ordered, max);
    }

    private static Obj searchMessages(final Inst inst) {
        final fURI path = f(Str.Helper.cleanString(inst.arg(ROOT, 0), true)).extend(MESSAGE).extend("+");
        final String pattern = Str.Helper.cleanString(inst.arg(PATTERN, 1), true);
        final Obj session = inst.arg(SESSION, 2);
        final int max = Math.min(inst.arg(MAX, 3).orElse(jnt(DEFAULT_MAX_MESSAGES)).asInt().intValue().intValue(), HARD_MAX_MESSAGES);
        // `has` is a regex find() (case-sensitive) — the pattern matches against the record
        final Obj searched = session.isNoObj()
                ? at_(uri(path)).where_(rec(TEXT, instB(HAS_INST_TID, lst(str(pattern)))))/*.order_(rshift_(uri(TIME)))*/.apply()
                : at_(uri(path)).where_(rec(SESSION, uri(Str.Helper.cleanString(session, true)))).where_(rec(TEXT, instB(HAS_INST_TID, lst(str(pattern)))))/*.order_(rshift_(uri(TIME)))*/.apply();
        return searched.isNoObj() ? lst() : newestFirst(searched, max);
    }

    private static Obj newestFirst(final Obj ordered, final int max) {
        if (ordered.isNoObj())
            return lst();
        final List<Obj> all = ordered.stream().sorted(Comparator.comparing(o -> mathInstSet.datetimeToMillis(o.asRec().at(TIME).asUri()))).toList();
        final int n = Math.min(max, all.size());
        final List<Obj> newest = new ArrayList<>(all.subList(all.size() - n, all.size()));
        Collections.reverse(newest);
        return lst(newest);
    }

}
