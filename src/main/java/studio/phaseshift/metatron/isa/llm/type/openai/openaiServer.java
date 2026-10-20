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

package studio.phaseshift.metatron.isa.llm.type.openai;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.YieldTurnSignal;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.OPENAI_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.nowDatetime;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The transport-agnostic OpenAI protocol core — the analog of
 * {@code mcpServer.handleMessage}, but agent-shaped: the OpenAI {@code model}
 * field <em>is</em> the agent, so this core owns both the wire translation and
 * the agent routing.
 * <p>
 * Holds a {@code model} registry — {@code !*} pointers to pre-designed agents
 * (an agent <em>is</em> the addressable model, wrapping its own LLM model) —
 * and nothing else.  The {@code openai_http} type declares
 * {@code model => lst[agent]::T} (Java: {@code T(LST_TID.poly(LLM_AGENT_TID))}),
 * so the constructor resolves the {@code !*} refs and this core iterates
 * {@code Agent}s directly — no uri/rec massaging.  The config rides the
 * transport's route mount ({@code openai_http::[model=>[!*dr, !*another]]});
 * this rec is composed with the handler's jvm, so both read the same
 * {@code model} rec.  No {@code HttpExchange}, no {@code SseStream}, no socket —
 * the transport pushes results over the wire, exactly as {@code mcp_httpHandler}
 * composes {@code mcpServer}.  Every method is dispatch-testable without a socket.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class openaiServer extends MRec {

    public static final fURI OPENAI_SERVER_TID = OPENAI_TID.extend("openai_server");

    public static final Type OPENAI_SERVER_TYPE = Type.Builder.build()
            .tid(OPENAI_TID)
            .vid(OPENAI_SERVER_TID)
            .isaPredicate(rec(
                    uri(MODEL).maybe().asUri(), T(LST_TID.poly(LLM_AGENT_TID))))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(OPENAI_SERVER_TID), lst(T(REC_TID)),
                    (lhs, inst) -> new openaiServer(mutableMap(inst.arg(0).asRec().jvm()), OPENAI_SERVER_TID, inst.arg(0).vid())))
            .create();

    public openaiServer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public openaiServer(final Rec recClone) {
        this(mutableMap(recClone.jvm()), recClone.tid(), recClone.vid());
    }

    // ── transport-agnostic contract ─────────────────────────────────

    /**
     * The {@code GET /v1/models} body — each registered agent as a model,
     * id'd by its {@code name} (or vid).
     */
    public Obj handleModels() {
        final List<Obj> models = new ArrayList<>();
        for (final Agent agent : this.models()) {
            models.add(rec(
                    uri(ID), str(this.agentId(agent)),
                    uri(OBJECT), str(MODEL),
                    uri("created"), nowDatetime(),
                    uri("owned_by"), str("metatron"),
                    uri(TYPE), agent));
        }
        return rec(uri(OBJECT), str("list"), uri(DATA), lst(models));
    }

    /**
     * A health/probe body — many harnesses ping the endpoint before the real call.
     */
    public Obj handleInfo() {
        return rec(
                uri(OBJECT), str(INFO),
                uri(NAME), str("metatron-openai"),
                uri("models"), lst(this.agentIds()));
    }

    /**
     * The {@code POST /v1/chat/completions} body (non-stream).  Routes by
     * {@code model}, feeds the last user message to {@code Agent.chat}, and
     * frames the {@code ChatFrame} into an OpenAI completion rec.  Errors come
     * back as the OpenAI error envelope, never a raw throw.
     */
    public Obj handleCompletion(final Rec request) {
        final String modelId = request.at(uri(MODEL)).isNoObj()
                ? null : Str.Helper.cleanString(request.at(uri(MODEL)));
        final Agent base = this.resolveModel(modelId);
        if (null == base)
            return this.frameError("model_not_found", "no agent matches model '" + modelId + "'");
        final String model = null == modelId ? this.agentId(base) : modelId;
        // detach the agent into a per-request stack clone — mutating its services never
        // touches the persistent space agent
        final Agent agent = Agent.agent(base.vid(null).asRec());
        final ExternalHarnessContext context = ExternalHarnessContext.of(request);
        final Rec system = context.systemMessage();
        if (null != system) {
            final Obj content = system.at(uri(TEXT));
            if (!content.isNoObj())
                agent.requireService(SystemService.class).addSystemMessage(content.toCleanString());
        }
        final String prompt = context.currentPrompt();
        if (null == prompt)
            return this.frameError("invalid_request_error", "no user message in request");
        final ChatFrame frame;
        try {
            frame = agent.chat(prompt);
        } catch (final Exception e) {
            return this.frameError("server_error", null == e.getMessage() ? e.getClass().getSimpleName() : e.getMessage());
        }
        return this.frameCompletion(agent, model, frame);
    }

    /**
     * Stream a chat completion: resolve the agent, stream its partial responses as
     * OpenAI chunk recs to {@code chunkSink}, then emit the terminal chunk and run
     * {@code done}.  The chunk id is read from {@code agent.chatId()} lazily inside
     * the sink — the id is advanced at the start of {@code chat()}, before any delta
     * fires.  Errors are emitted as a single error-envelope chunk before {@code done}.
     */
    public void streamCompletion(final Rec request, final Consumer<Obj> chunkSink, final Runnable done) {
        final String modelId = request.at(uri(MODEL)).isNoObj()
                ? null : Str.Helper.cleanString(request.at(uri(MODEL)));
        final Agent base = this.resolveModel(modelId);
        if (null == base) {
            chunkSink.accept(this.frameError("model_not_found", "no agent matches model '" + modelId + "'"));
            done.run();
            return;
        }
        final Agent agent = Agent.agent(base.vid(null).asRec());
        final ExternalHarnessContext context = ExternalHarnessContext.of(request);
        final Rec system = context.systemMessage();
        if (null != system) {
            final Obj content = system.at(uri(TEXT));
            if (!content.isNoObj())
                agent.requireService(SystemService.class).addSystemMessage(content.toCleanString());
        }
        final String prompt = context.currentPrompt();
        if (null == prompt) {
            chunkSink.accept(this.frameError("invalid_request_error", "no user message in request"));
            done.run();
            return;
        }
        agent.chat(prompt, text -> chunkSink.accept(this.frameChunk(agent, modelId, text, false)));
        chunkSink.accept(this.frameChunk(agent, modelId, null, true));
        done.run();
    }

    private Obj frameChunk(final Agent agent, final String modelId, final String text, final boolean terminal) {
        final Obj delta = terminal ? rec() : rec(uri(CONTENT), str(text));
        final Rec choice = rec(
                uri(INDEX), jnt(0),
                uri("delta"), delta,
                uri("finish_reason"), terminal ? str(STOP) : noobj());
        return rec(
                uri(ID), str("chatcmpl-" + agent.chatId()),
                uri(OBJECT), str("chat.completion.chunk"),
                uri("created"), jnt((int) (System.currentTimeMillis() / 1000L)),
                uri(MODEL), str(modelId),
                uri("choices"), lst(choice));
    }

    /**
     * The HTTP status a result body implies: 200 unless it is an error envelope.
     */
    public static int status(final Obj result) {
        final Obj error = result.asRec().at(uri(ERROR));
        if (error.isNoObj())
            return 200;
        final Obj code = error.asRec().at(uri(CODE));
        final String codeStr = code.isStr() ? code.strValue() : "";
        return switch (codeStr) {
            case "model_not_found" -> 404;
            case "invalid_request_error" -> 400;
            default -> 500;
        };
    }

    // ── internals ────────────────────────────────────────────────────

    private List<Agent> models() {
        final Obj a = this.at(uri(MODEL));
        final Lst list = a.isLst() ? a.asLst() : lst();
        final List<Agent> agents = new ArrayList<>();
        for (final Obj ref : list.elements().toList())
            agents.add(Agent.agent(ref.asRec()));
        return agents;
    }

    private List<Obj> agentIds() {
        final List<Obj> ids = new ArrayList<>();
        for (final Agent agent : this.models())
            ids.add(str(this.agentId(agent)));
        return ids;
    }

    /**
     * Match a {@code model} id to an agent: exact {@code name} match wins; a
     * blank/absent id falls back to the first registered agent.
     */
    private Agent resolveModel(final String modelId) {
        Agent fallback = null;
        for (final Agent agent : this.models()) {
            if (null == fallback)
                fallback = agent;
            if (null != modelId && !modelId.isBlank() && this.agentId(agent).equals(modelId))
                return agent;
        }
        return (null == modelId || modelId.isBlank()) ? fallback : null;
    }

    private String agentId(final Agent agent) {
        final Obj name = agent.at(uri(NAME));
        if (name.isStr() && !name.strValue().isBlank())
            return name.strValue();
        return null != agent.vid() ? agent.vid().toString() : AGENT;
    }

    private Obj frameToolCalls(final Agent agent, final String modelId, final YieldTurnSignal yield) {
        final Rec function = rec(uri(NAME), str(yield.toolName), uri("arguments"), str(null == yield.arguments ? "" : yield.arguments));
        final Rec toolCall = rec(uri(ID), str(null == yield.toolCallId ? "" : yield.toolCallId), uri("type"), str("function"), uri("function"), function);
        final Rec message = rec(uri(ROLE), str("assistant"), uri("tool_calls"), lst(toolCall));
        final Rec choice = rec(uri("index"), jnt(0), uri("message"), message, uri("finish_reason"), str("tool_calls"));
        return rec(
                uri(ID), str("chatcmpl-" + agent.chatId()),
                uri(OBJECT), str("chat.completion"),
                uri("created"), jnt((int) (System.currentTimeMillis() / 1000L)),
                uri(MODEL), str(modelId),
                uri("choices"), lst(choice));
    }

    private Obj frameCompletion(final Agent agent, final String modelId, final ChatFrame frame) {
        final String text = null == frame.chat() ? "" : frame.chat();
        final Rec message = rec(uri(ROLE), str("assistant"), uri("content"), str(text));
        final Rec choice = rec(uri("index"), jnt(0), uri("message"), message, uri("finish_reason"), str("stop"));
        return rec(
                uri(ID), str("chatcmpl-" + agent.chatId()),
                uri(OBJECT), str("chat.completion"),
                uri("created"), jnt((int) (System.currentTimeMillis() / 1000L)),
                uri(MODEL), str(modelId),
                uri("choices"), lst(choice));
    }

    private Obj frameError(final String code, final String message) {
        return rec(uri("error"), rec(
                uri("message"), str(message),
                uri("type"), str(code),
                uri("code"), str(code)));
    }
}
