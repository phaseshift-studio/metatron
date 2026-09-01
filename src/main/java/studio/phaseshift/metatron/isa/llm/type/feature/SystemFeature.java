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
        final String base = this.at(uri("base")).orElse(str("")).strValue().trim();
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
