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
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
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

import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The MCP server for an agent's message ledger.
 * <p>
 * {@code mcp_message} exposes an agent's chronological message ledger
 * ({@code <root>/message}) over MCP so that remote harness memory can be
 * appended ({@code add_message}), read back (latest first,
 * {@code get_messages}), and searched by pattern ({@code
 * search_messages}).
 * <p>
 * Reads go through the ledger gateway —
 * {@code SpaceChatSessionStore.busWindow}: the stream is scoped by
 * session (a required argument; a vid under the agent's root such as
 * {@code <root>/session/<id>}), turns and {@code ai}/{@code tool_result}
 * pairs stay intact at the window boundary, and the window never crosses
 * the newest compaction (which summarizes everything before it). Records
 * come back full-fidelity — vids, thinking, envelope — newest first.
 * Windows are bounded: default {@value #DEFAULT_MAX_MESSAGES}, hard
 * ceiling {@value #HARD_MAX_MESSAGES}.
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
 *   <li>{@code compaction} — a compaction sentinel (the
 *       {@code message/compaction} record): {@code text} is the resume
 *       summary; optional {@code in}/{@code out}/{@code compression}
 *       statistics ride the generic attributes merge. Reads bound the live
 *       window at the newest sentinel (see {@code SpaceChatSessionStore}
 *       {@code stopAt})</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcpMessageServer {

    public static final fURI MCP_MESSAGE_SERVER_TID = LLM_ISA_TID.extend("mcp").extend("mcp_message");
    public static final Type MCP_MESSAGE_SERVER_TYPE = mcpMessageServer.server();

    /**
     * Default read window for {@code get_messages} / {@code search_messages}.
     */
    public static final int DEFAULT_MAX_MESSAGES = 50;

    /**
     * Hard ceiling for a single read — the ledger grows; keep remote reads bounded.
     */
    public static final int HARD_MAX_MESSAGES = 500;

    /**
     * The single transport-agnostic MCP server for the message ledger. Both
     * {@code httpSpace} and {@code wsSpace} wrap this {@link mcpServer} in their
     * respective transport handler ({@code mcp_httpHandler} / {@code
     * mcp_wsHandler}).
     */
    public static Type server() {
        return Type.Builder.build()
                .tid(MCP_SERVER_TID)
                .vid(MCP_MESSAGE_SERVER_TID)
                .isaPredicate(rec(
                        uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE).maybe(),
                        uri(RESOURCE).maybe().asUri(), T(ALL),
                        uri(PROMPT).maybe().asUri(), T(ALL)))
                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MCP_MESSAGE_SERVER_TID), lst(REC_TYPE), (lhs, inst) -> {
                    final Rec config = inst.arg(0).asRec();
                    return new mcpServer(generateMcpConfig(MCP_MESSAGE_SERVER_TID, config.jvm()), MCP_SERVER_TID, config.vid());
                })).create();
    }

    private static Map<Obj, Obj> generateMcpConfig(final fURI vid, final Map<Obj, Obj> config) {
        final Map<Obj, Obj> newConfig = mutableMap();
        newConfig.putAll(config);
        final Rec tools = rec(mutableMap());
        final Inst addMessage = docWrap(instC(vid.extend("add_message").dom(ALL.maybe()).rng(MESSAGE_TID), rec(
                ROOT, URI_TYPE,
                KIND, isa_(union_(lst(uri(USER), uri(AI), uri(SYSTEM), uri("thinking"), uri("tool_result"), uri("compaction")))).tryToInst(),
                TEXT, STR_TYPE,
                SESSION, URI_TYPE,
                NAME, T(STR_TID.maybe()),
                CONTENTS, T(STR_TID.maybe()),
                CHAT_ID, T(INT_TID.maybe()),
                TIME, T(STR_TID.maybe()),
                TOOL_REQUESTS, T(LST_TID.maybe()),
                ATTRIBUTES, T(REC_TID.maybe())
        ), (lhs, inst) -> addMessage(inst)), "add a chat message");
        final Inst getMessages = docWrap(instC(vid.extend("get_messages").dom(ALL.maybe()).rng(LST_TID), rec(
                ROOT, URI_TYPE,
                SESSION, URI_TYPE,
                MAX, T(INT_TID.maybe())
        ), (lhs, inst) -> readMessages(inst, 1, 2)), "get chat messages");
        final Inst searchMessages = docWrap(instC(vid.extend("search_messages").dom(ALL.maybe()).rng(LST_TID), rec(
                ROOT, URI_TYPE,
                PATTERN, STR_TYPE,
                SESSION, URI_TYPE,
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
        } else if (messageKind.equals(uri("compaction"))) {
            // the compaction sentinel — its text is the resume summary; the
            // optional in/out/compression statistics ride the generic
            // attributes merge below (the same shape compactSession writes)
            builder = MessageBuilder.build(COMPACTION_MESSAGE_TID).text(text);
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
        Obj requests = toolRequests;
        if (requests.isStr()) {
            // an mcp client may deliver tool_requests as a json string — the
            // json serializer decodes it to a typed lst(rec); the mtron parser
            // garbles json (list-of-recs lands as objs). the same idiom the
            // emulator's install uses for its mcpServers snippet
            requests = ObjJSONSerializer.simple().inputBytes(Str.Helper.cleanString(requests, true));
        }
        final List<Obj> built = new ArrayList<>();
        requests.<Lst>as().lstValue().forEach(entry -> {
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

    /**
     * The bus store for this ledger — the top-level (depth 1) session view;
     * the chat id never isolates at depth 1.  The store is told the memory
     * root it serves: the bus is (root, session) over the space and needs
     * no agent (the agent's root points at the agent — a different thing).
     */
    private static SpaceChatSessionStore storeAt(final fURI rootF, final fURI sessF) {
        final Space space = Router.global().getSpaceFor(rootF);
        if (null == space)
            throw MTronException.of("no space serves the ledger root: %s", rootF);
        return new SpaceChatSessionStore(null, space, 1, 0, rootF);
    }

    private static Obj readMessages(final Inst inst, final int sessionIdx, final int maxIdx) {
        final fURI rootF = f(Str.Helper.cleanString(inst.arg(ROOT, 0), true));
        final fURI sessF = f(Str.Helper.cleanString(inst.arg(SESSION, sessionIdx), true));
        final int max = Math.min(inst.arg(MAX, maxIdx).orElse(jnt(DEFAULT_MAX_MESSAGES)).asInt().intValue().intValue(), HARD_MAX_MESSAGES);
        // The store owns the window — session scope, turn/tool-pair integrity,
        // and the bus sentinel — and hands back full-fidelity records (vids,
        // thinking, envelope); the bus sees them newest-first
        final List<Rec> window = storeAt(rootF, sessF).busWindow(sessF, max);
        Collections.reverse(window);
        return lst(window.stream().<Obj>map(m -> m).toList());
    }

    private static Obj searchMessages(final Inst inst) {
        final fURI rootF = f(Str.Helper.cleanString(inst.arg(ROOT, 0), true));
        final String pattern = Str.Helper.cleanString(inst.arg(PATTERN, 1), true);
        final fURI sessF = f(Str.Helper.cleanString(inst.arg(SESSION, 2), true));
        final int max = Math.min(inst.arg(MAX, 3).orElse(jnt(DEFAULT_MAX_MESSAGES)).asInt().intValue().intValue(), HARD_MAX_MESSAGES);
        // search over the bus window (sentinel-safe, pair-safe) — a
        // case-sensitive regex find() against each record's text
        final java.util.regex.Pattern rx = java.util.regex.Pattern.compile(pattern);
        final List<Rec> window = storeAt(rootF, sessF).busWindow(sessF, max);
        final List<Obj> hits = new ArrayList<>();
        for (final Rec message : window)
            if (message.at(uri(TEXT)).isStr() && rx.matcher(Str.Helper.cleanString(message.at(uri(TEXT)))).find())
                hits.add(message);
        Collections.reverse(hits);
        return lst(hits);
    }

}
