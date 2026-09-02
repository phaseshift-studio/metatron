package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MESSAGE_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The system-message contract for the agent — the single owner of system-message
 * state, construction, and submission.
 * <p>
 * <b>Single channel.</b> There is exactly ONE way system messages reach the model:
 * <ol>
 *   <li>The agent's persistent <b>base</b> instruction, configured in this feature's
 *       rec as {@code base => "you are a helpful assistant"}.  This is the durable
 *       system context.</li>
 *   <li>Per-chat <b>contributions</b> from other features, added via
 *       {@link #addSystemMessage(String)} (through
 *       {@code agent.feature(SYSTEM).<SystemFeature>as()} — cross-feature
 *       communication).  These are the dynamic context: concepts, claims, loose ends.</li>
 * </ol>
 * The final system message is {@code base + "\n" + join(contributions)}.  It is both
 * <b>persisted to the session ledger</b> (written by {@link #onBeforeChat} as a
 * {@code SYSTEM_MESSAGE_TID} message, so the session memory carries it across turns and
 * it is URI-addressable) and <b>submitted to the model</b> (via
 * {@link #systemMessage()} through the transformer in {@code Agent.chat()}).
 * <p>
 * <b>Lifecycle.</b> The <em>contributions</em> are per-chat and ephemeral: cleared by
 * {@link #clearSystemMessages()} on {@code onCompleteResponse} / {@code onError} (and as
 * a safety net in {@code Agent.chat()}'s {@code finally} for the interrupted path).
 * The <em>base</em> is durable — it persists in the ledger, so the next session's memory
 * still carries the agent's core instruction.  Features with dynamic context (concepts,
 * claims, loose ends) must re-add their contribution on every {@code onBeforeChat},
 * reading from space.
 * <p>
 * <b>Construction timing.</b> {@link #systemMessage()} composes base + contributions and
 * is called at chat-build time (Phase 2, after ALL {@code onBeforeChat} hooks have run).
 * Feature dispatch order is not guaranteed, so composing in the hook could miss a
 * contributor's message; the composition happens when {@code Agent.chat()} builds the
 * service.
 */
public class SystemFeature extends AbstractFeature {

    private static final String DEFAULT_SYSTEM_MESSAGE =
            """
            you are an ai agent in metatron (http://metatron.phaseshift.studio).
            """;
    
    /*
            you are interacting with the user through %s.
            
            Memories you previously stored (use memory_search for anything not listed):
            - (#2, pinned) [metatron project] Metatron is a distributed, data-oriented JVM (Java 24) at /home/killswitch/software/metatron with the mtron functional language; the repo's hard rules are: never run git, tests must run on JDK 24, and new code must ship @ParameterizedTest+@CsvSource tests.
            - (#18) [metatron reflector reflect-mat annotations status] [metatron reflect.mat materializer delivered, green 2026-08-31] Additive annotation→type/inst materializer complete: Reflector + @JDoc/@JInst/@JType (+@JType.Constructor) in src/main/java/studio/phaseshift/metron/isa/m/type/reflect/mat/ (note two-segment path m/type, NOT one-segment mtype); test ReflectorTest 9/9 + mInstSetTest 707 via docker gate on JDK 24, no git. API: reflect(Class)→Emitted(Type,List<Inst>); install(jvm, Class...) writes each inst at full tid AND bare noQ().one() name, type at vid; slotKey() implements the four named coefficient commons ({1,1} bare, {0,1} maybe {?}, {1,UNBOUNDED} some {+}, {0,UNBOUNDED} maybeSome {*}, JType.UNBOUNDED sentinel). Behavioral proof = DIRECT invocation of emitted Inst (Router read-back + apply/args-apply) — mtron dot-name resolution (e.g. *3.probeAdd(4)) requires the inst to be in a REGISTERED InstSet space pattern, which a raw Router.write does not join; that parser-index migration is the deferred "reverse-conversion" follow-up the user authorized for later. Do-not-touch: sibling integrated pkg .../m/type/reflect/ (JRec/JDocs/etc) + llmInstSet.
            (9 more memories not shown; use memory_search)
            
            Paths prefixed with @ are files explicitly referenced by the user. Use the read tool when their contents are needed; do not claim to have inspected a file before reading it.
            
            Use the read tool — not shell commands like cat — to inspect text files. Results include line numbers. Use offset and limit to continue reading large files.
            
            Use the write tool to create files or completely replace file contents. Existing files are overwritten, so read an existing file first (the default fs-observation-policy requires it) and prefer edit for targeted changes.
            
            Use the edit tool for targeted changes to existing UTF-8 text files. It replaces literal old_string with new_string; by default old_string must appear exactly once. If old_string appears multiple times, provide a more specific old_string or set replace_all to true. Read the file first (the default fs-observation-policy requires it), unless you just created or edited it in this session.
            
            Use the glob tool — not shell find — to discover files by path pattern. A pattern with no "/" matches basenames at any depth, so "*" matches every file in the tree rather than its top level. Results are files only, never directories, and include hidden and ignored files: a result that fits comes back in modification-time order, while a larger one keeps the modification-time-ordered head.
            
            Use the grep tool — not shell grep or rg — to search file contents. Use read on a matched file when you need surrounding context.
            
            Check the [exit code: N] marker on every bash result; investigate failures before moving on.
            
            Track every background job id you start. You are notified in-session when a job finishes — do not busy-poll or sleep on one; keep working on independent steps and do not duplicate a running job's work. Before giving a final answer, collect every still-relevant job with job_output (set wait: true only when you are genuinely blocked on it), and job_kill jobs that stopped mattering.
            
            Use the web_search tool to discover current information on the web. The required queries array accepts 1–4 non-empty search queries; use a one-item array for a single search. It returns an optional answer plus a list of source URLs. Use the returned source snippets when available, and cite the relevant URLs as markdown links.
            
            Use codegraph for structural questions about code: where a symbol is declared, what calls it, what it calls, what a change to it reaches, and how one symbol reaches another. It answers from a pre-built index, so it is both faster and more precise than grepping for a name, which also matches comments, strings, and unrelated identifiers. Use search/read instead for literal text, and when codegraph reports no index for a workspace. When status reports no index, call codegraph_index once to build one — it runs on its own, longer timeout budget than a query — then retry. Results reflect the last time the workspace was indexed; a declaration added since then is absent.
            
            Use goal tools for one long-running completion objective in the current session. create_goal may infer goal intent from a direct human request in any language; do not create a goal for routine single-turn work. Call get_goal before update_goal and copy its exact goal_id and revision. After session resume or fork, an active goal is disarmed: when a human asks to continue or resume in any wording or language, use update_goal action resume to rearm it. Mark complete only when the objective is actually achieved. Mark blocked only after the same blocking condition persists for at least 3 consecutive goal rounds, and report that concrete condition in blocked_reason; difficulty, uncertainty, or useful remaining work is not blocked.
            
            Use the workflow tool ONLY when the user explicitly asks for a workflow or for large multi-agent orchestration: you write a JavaScript script (the tool description documents the exact format) that fans work out across many subagents with phases and structured results. For one or two delegations, prefer plain subagent calls.
            
            Use the ralph tool ONLY when the direct human explicitly asks for a Ralph loop or fresh-agent iterative execution. Each Ralph round starts a fresh child with no conversation seed and uses the shared workspace as durable memory. Completion and blockers are worker reports, not independent evaluation. Use same-session goal tools for ordinary long-running objectives, and plain subagents or workflows for bounded delegation and fan-out.
            
            Use subagent in the background by default. Start independent delegations together in one assistant message and continue useful work while they run. Set `run_in_background: false` only when your next action depends on that subagent's result. When a background run settles, the runtime sends you a notice containing its outcome and any final assistant message.
            
            Use subagent_fork in the background by default. Start independent delegations together in one assistant message and continue useful work while they run. Set `run_in_background: false` only when your next action depends on that subagent's result. When a background run settles, the runtime sends you a notice containing its outcome and any final assistant message.
            
            When you successfully create or modify files, mention the primary outputs in your final response. To make those and any other changed-file references clickable in Web, format them as Markdown inline code using the exact file-tool path, or a basename when unique among the files changed in that turn.
           */

    /**
     * Key in this feature's jvm holding the last-written system message text.
     */
    private static final String LAST = "last_system_text";

    private final List<String> systemMessages = new ArrayList<>();

    public SystemFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Append a per-chat system-message contribution.  Call during {@code onBeforeChat}
     * via {@code agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage(...)}.
     * Cleared by {@link #clearSystemMessages()} after each chat completes.
     */
    public void addSystemMessage(final String text) {
        this.systemMessages.add(text);
    }

    public List<String> getSystemMessages() {
        return this.systemMessages;
    }

    /**
     * The final system message for this chat: the persistent {@code base} instruction
     * followed by the per-chat contributions, joined with newlines.
     *
     * <pre>
     *   &lt;base&gt;
     *   &lt;contribution 0&gt;
     *   &lt;contribution 1&gt;
     *   ...
     * </pre>
     * <p>
     * Called by {@code Agent.chat()} at service-build time, after all {@code onBeforeChat}
     * hooks have run.
     */
    public String systemMessage() {
        final String base = this.at(uri("base")).orElse(str(DEFAULT_SYSTEM_MESSAGE)).strValue().trim();
        final String dynamic = String.join("\n", this.systemMessages);
        if (base.isBlank())
            return dynamic;
        return dynamic.isBlank() ? base : base + "\n" + dynamic;
    }

    /**
     * Clear this chat's <em>contributions</em>.  The {@code base} field is durable and
     * remains.  Called by {@code onCompleteResponse} / {@code onError} (and
     * {@code Agent.chat()}'s {@code finally} as a safety net).
     */
    public void clearSystemMessages() {
        this.systemMessages.clear();
    }

    /**
     * Persist the final system message (base + current contributions) to the session
     * ledger as a {@code SYSTEM_MESSAGE_TID} message, so the session memory carries it
     * across turns and it is URI-addressable.
     * <p>
     * <b>Write-on-change.</b> Matches LangChain4j's system-message semantics: there is
     * exactly one system message, and it is only re-written when its content changes.
     * The last-written text is tracked in this feature's jvm ({@code LAST}); if the
     * composed message is unchanged, no new ledger row is created.
     */
    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String text = this.systemMessage();
        if (text.isBlank() || !agent.hasFeature(LLM_MESSAGE_FEATURE_TID))
            return noobj();

        final String lastText = this.at(uri(LAST)).orElse(str("")).strValue();
        if (text.equals(lastText))
            return noobj();

        final fURI sessionVID = agent.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION).uriValue();
        try {
            MessageBuilder.build(SYSTEM_MESSAGE_TID)
                    .text(text)
                    .time()
                    .session(sessionVID)
                    .depth(agent.chatDepth())
                    .chatId(agent.chatId())
                    .create(agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ));
            this.at(uri(LAST), str(text), MUTABLE);
        } catch (final Exception e) {
            this.logger().warn("system message write failed (non-blocking): %s", e.getMessage());
        }
        return noobj();
    }

    /**
     * Chat completed — clear this chat's contributions so the next chat re-surfaces its
     * own.  The {@code base} persists.  {@code Agent.chat()}'s {@code finally} also
     * clears as a safety net for the interrupted path (which fires neither
     * {@code onCompleteResponse} nor {@code onError}).
     */
    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        this.clearSystemMessages();
    }

    /**
     * Chat failed — clear this chat's contributions (safety: don't leak a failed chat's
     * context into the next chat).  The {@code base} persists.
     */
    @Override
    public void onError(final Agent agent, final Fail fail) {
        this.clearSystemMessages();
    }
}
