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

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.Watermarks;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * The mid-chat channel — conversation that happens <em>inside</em> a turn.
 *
 * <p>A long agentic turn can run for hours and hundreds of tool calls.  The
 * ordinary chat protocol has no room in it: the model's only way to say anything
 * is to finish, and the user's only way to say anything is to wait.  This feature
 * opens the channel in both directions.
 *
 * <h3>Outbound</h3>
 * The model tags its own thought text with {@code <<txt:midchat>>...} and the
 * remark is relayed to the user as it arrives — harvested off the streaming
 * thinking hook, so it lands mid-turn rather than at the end.  The markup never
 * reaches the user, and the remark is also written to the ledger as an
 * {@code ai_message} carrying the {@link studio.phaseshift.metatron.isa.llm.llmInstSet#AI_MIDCHAT_TID}
 * subtype.
 *
 * <h3>Inbound</h3>
 * What the user types while the turn is in flight is queued on the agent
 * ({@code Agent.pushMidChatMessage}) and delivered inside the tool result of the
 * call it answered, under {@code pending_messages} — see
 * the {@code on_tool_result} stage, which this feature contributes.  It reaches the
 * ledger too, as a {@code user_message} carrying
 * {@link studio.phaseshift.metatron.isa.llm.llmInstSet#USER_MIDCHAT_TID}.
 *
 * <h3>Why subtypes rather than a message kind</h3>
 * LC4j builds its memory from the base tid, so writing these as ordinary user and
 * ai messages means the conversation conducted mid-iteration simply <em>becomes</em>
 * history on the next turn — no projection, no summary, no re-injection.  The
 * subtype is provenance: it lets the window tell a real prompt from a mid-chat
 * remark and keep a turn's own narration out of its own context.
 */
public class MidChatFeature extends AbstractFeature {

    /**
     * This feature's watermark identity.  Declared on the config rec as
     * {@code watermark => [key=>..., tag=>...]} to override either.
     *
     * <p>The codec is {@code txt}, not the mtron default: the body is prose, and
     * mtron is a structural parse that plain English fails.  Verified —
     * {@code parse('I am on it')} is a parse error, and before this was declared
     * the whole remark was dropped and the raw markup leaked into the response.
     */
    static final String WATERMARK_KEY = "midchat";
    static final String WATERMARK_CODEC = "txt";

    private static final String MIDCHAT_INSTRUCTIONS = """
                                                       While you are working — thinking, calling tools, iterating — you can say
                                                       something to the user without ending your turn. Tag your thought text:
                                                       
                                                           <<txt:midchat>>
                                                           still tracing the argument routing; two calls in and it looks like the optional dom slot
                                                           <</txt:midchat>>
                                                       
                                                       The remark is relayed to the user as you produce it, and removed from what you
                                                       emit. Use it for progress, for a question that blocks you, or for a result worth
                                                       reporting before the turn ends.
                                                       
                                                       Anything the user sends back while you work arrives inside your next tool result
                                                       under a pending_messages field. Treat those as speaking for the user, and treat
                                                       them as taking priority over your current objective.
                                                       """;

    /**
     * Thought text that may still become a watermark — the streaming carry buffer.
     * See {@link Watermarks#pendingTail(String)}: a marker split across chunks is
     * held here rather than relayed twice or rendered raw.
     */
    private final StringBuilder hold = new StringBuilder();

    public MidChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_FEATURE_TID);
    }

    /**
     * Register this feature's skill with the SkillFeature gateway — the gateway is
     * the owner of the skill channel; this feature is a contributor.
     */
    private void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(mutableMap(
                uri(NAME), uri(LLM_MIDCHAT_FEATURE_TID.name()),
                uri(DESC), str("speak to the user mid-iteration, and hear them back inside your next tool result"),
                uri(CONTENT), str(Watermarks.instructions(Watermarks.codec(this, WATERMARK_CODEC),
                        Watermarks.key(this, WATERMARK_KEY), MIDCHAT_INSTRUCTIONS))))));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
        this.surfaceWatermarkRejections(agent);
        this.hold.setLength(0);
        return noobj();
    }

    /**
     * Harvest the model's remarks off the thinking stream as they arrive, and put each
     * one back where its markup stood.
     *
     * <p>Thinking is the right stream in both directions: it is where the model was
     * already told to tag, and unlike the response text it is not rendered to the user
     * on the way past — the response is written out live, so a marker there would flash
     * its raw markup on screen before anything could strip it.
     *
     * <p>Returns the thought with this feature's own markup replaced by the remark it
     * carried, so the raw tag never reaches the thinking ledger.  A marker split across
     * chunks is held rather than emitted half-written, which is why the thought handed
     * on is the visible part: the held tail rides in on the next chunk.
     */
    @Override
    public Obj onPartialThinking(final Agent agent, final Obj thought) {
        if (!thought.isStr())
            return noobj();
        final String text = this.hold + thought.strValue();
        this.hold.setLength(0);
        final String tail = Watermarks.pendingTail(text);
        this.hold.append(tail);
        final String visible = text.substring(0, text.length() - tail.length());
        final Watermarks.Scan scan = Watermarks.scan(visible, ON_PARTIAL_THINKING, false);
        if (tail.isEmpty() && scan.hits().isEmpty())
            return noobj(); // nothing held, nothing of ours — the thought passes untouched
        final StringBuilder rebuilt = new StringBuilder();
        int cursor = 0;
        for (final Watermarks.Hit hit : scan.hits()) {
            if (!this.isMine(hit))
                continue; // another feature's tag is another feature's to harvest
            rebuilt.append(visible, cursor, hit.start());
            rebuilt.append(this.relay(agent, hit));
            cursor = hit.end();
        }
        rebuilt.append(visible.substring(cursor));
        return str(rebuilt.toString());
    }

    /**
     * Whether a watermark carries this feature's key — the tag is the address.
     */
    private boolean isMine(final Watermarks.Hit hit) {
        return hit.key().equals(Watermarks.key(this, WATERMARK_KEY));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        // whatever the stream never settled is not a watermark — an opener with no
        // closer costs one signal, and holding it past the turn would leak it into
        // the next one
        this.hold.setLength(0);
        this.noteWatermarkFailure(result, WATERMARK_CODEC, WATERMARK_KEY);
    }

    /**
     * Relay one remark to the user: the console now, the ledger for good.
     *
     * @return the remark as it goes back into the thought, where its markup stood
     */
    private String relay(final Agent agent, final Watermarks.Hit hit) {
        if (hit.decoded().isFail()) {
            this.rejectWatermark("%s was not applied: %s".formatted(
                    Watermarks.marker(WATERMARK_CODEC, hit.key()),
                    Str.Helper.cleanString(hit.decoded())));
            return "";
        }
        final String text = Str.Helper.cleanString(hit.decoded());
        if (text.isBlank())
            return "";
        StatusLine.message(str("\uD83D\uDCAC %s".formatted(text)));
        this.write(agent, MessageBuilder.build(AI_MESSAGE_TID)
                .sub(AI_MIDCHAT_TID)
                .text(text)
                .time()
                .session(agent.sessionVID())
                .depth(agent.chatDepth())
                .chatId(agent.chatId()));
        return text;
    }

    /**
     * Wrap the tool result the model is about to see with whatever the user said
     * while this turn was in flight.
     *
     * <p>Implemented as the {@code on_tool_result} stage because {@code mToolExecutor}
     * is the only place a tool result can be shaped — {@code onToolExecuted} is an
     * observational LC4j listener and {@code beforeToolExecution} is never dispatched.
     * The drained messages are written to the ledger as well: without that, an hour of
     * mid-iteration conversation lived only inside the tool result that carried it
     * and died when the turn did.
     */
    @Override
    public Obj onToolResult(final Agent agent, final Obj result, final String requestId) {
        final Lst pending = this.pendingMessages(agent);
        if (pending.isEmpty())
            return result;
        // Reading them is what retires them — this is the only consumer that clears, so
        // a message the agent never reads stays pending, and is announced as pending.
        this.drain(agent);
        this.publishInbound(agent, pending);
        this.announce(agent, pending);
        return rec(RESULT, result, PENDING_MESSAGES, pending);
    }

    /**
     * The address this feature's pending messages live at — its own subspace, under its
     * declared root (or the agent's when it declares none).
     *
     * <p>An address, and not a field on this rec.  {@code Agent.agent(rec)} builds a
     * fresh agent on every call and the feature it carries is constructed with it, so a
     * field written by the pusher is a field the running turn never sees — the rec
     * carries no vid, which means {@code at(PENDING_MESSAGES, ...)} reaches no space at
     * all.  Only an address is shared by two instances, and only an address is where an
     * agent's state can live.
     */
    private fURI pendingURI(final Agent agent) {
        final Obj declared = this.at(ROOT);
        final Obj inherited = agent.at(ROOT);
        if (declared.isUri())
            return declared.uriValue().extend(this.tid().name()).extend(PENDING_MESSAGES);
        if (inherited.isUri())
            return inherited.uriValue().extend(this.tid().name()).extend(PENDING_MESSAGES);
        throw MTronException.of("the mid-chat feature has nowhere to keep pending messages: %s", this.tid());
    }

    /**
     * The messages waiting to be read.
     */
    public Lst pendingMessages(final Agent agent) {
        final Obj pending = Router.readFromSpace(this.pendingURI(agent));
        return pending.isLst() ? pending.asLst() : lst();
    }

    /**
     * Queue a message for the next tool result.
     */
    public void push(final Agent agent, final Rec message) {
        Router.writeToSpace(this.pendingURI(agent), this.pendingMessages(agent).add(message));
    }

    /**
     * Read and retire the queue.
     */
    public Lst drain(final Agent agent) {
        final Lst messages = this.pendingMessages(agent);
        Router.writeToSpace(this.pendingURI(agent), lst());
        return messages;
    }

    /**
     * Put the read into the agent's thoughts, at the moment it happened.
     *
     * <p>The thought stream is the right home for it because the position is the
     * information: these went into the tool result the agent just got, so they belong
     * between the thinking that preceded that call and the thinking that follows it.
     * Announcing them on the next thinking chunk would carry state across the boundary
     * and strand the read whenever a turn ends without another chunk; announcing them
     * on every chunk would repeat them.  Appending here does neither.
     */
    private void announce(final Agent agent, final Lst pending) {
        if (!agent.hasFeature(LLM_THINK_FEATURE_TID))
            return;
        final String listing = pending.elements()
                .map(message -> "\n" + Str.Helper.cleanString(message.asRec().at(uri(MESSAGE))))
                .collect(Collectors.joining());
        agent.feature(LLM_THINK_FEATURE_TID).<ThinkFeature>as()
                .append(agent, str("\n{{r}}%s\n{{y}}pending messages{{/y}}:%s\n{{r}}%s\n".formatted("-".repeat(10), listing, "-".repeat(10))));
    }

    /**
     * Surface messages still queued when the turn ended — they have no tool call
     * left to ride out on.  The symmetric half of
     * {@code ToolFeature.handleOrphanToolRequests}: a pushed message is always
     * surfaced, whether or not a tool ever ran to carry it.
     */
    public void handleOrphanMidChatMessages(final Agent agent) {
        final Lst orphans = this.drain(agent);
        if (orphans.isEmpty())
            return;
        this.logger().warn("closing the mid-chat channel with %d undelivered message(s)", orphans.count());
        this.publishInbound(agent, orphans);
    }

    /**
     * The user's half of the channel — durable, and tagged so the window can tell
     * it from a real prompt.
     *
     * <p>The queued rec holds the user's words under {@code message} (the shape
     * {@code Agent.pushMidChatMessage} is given), not under {@code text} — reading
     * the wrong key here silently persisted an empty body for every mid-chat
     * message, which is why a long mid-iteration conversation left nothing behind.
     */
    private void publishInbound(final Agent agent, final Lst messages) {
        messages.elements().forEach(message -> this.write(agent, MessageBuilder.build(USER_MESSAGE_TID)
                .sub(USER_MIDCHAT_TID)
                .text(message.asRec().at(uri(MESSAGE)).strValue())
                .contents(message.asRec().at(uri(MESSAGE)).strValue())
                .time(message.asRec().at(TIME).uriValue())
                .session(agent.sessionVID())
                .depth(agent.chatDepth())
                .chatId(agent.chatId())));
    }

    /**
     * Where mid-chat messages are written: the feature's own {@code root} when it
     * declares one, else the agent's — the same convention {@code ChatFeature} and
     * {@code ThinkFeature} follow for their own ledger writes.
     */
    private fURI ledgerRoot(final Agent agent) {
        final Obj root = this.at(ROOT);
        return root.isNoObj() ? agent.at(ROOT).uriValue() : root.uriValue();
    }

    /**
     * Append one message to the ledger.  Best-effort: this runs inside streaming
     * and tool callbacks, and a persistence failure must not abort the turn.
     */
    private void write(final Agent agent, final MessageBuilder message) {
        final fURI root = this.ledgerRoot(agent);
        final Space space = Router.global().getSpaceFor(root.extend(MESSAGE));
        if (!space.hasQ(f(INCRQ))) {
            this.logger().warn("mid-chat persistence requires an incrq message space: %s", space.vidOrTid());
            return;
        }
        try {
            message.create(root.extend(MESSAGE).extend("_").addQ(INCRQ));
        } catch (final Exception e) {
            this.logger().warn("mid-chat message write failed: %s", e.getMessage());
        }
    }
}
