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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Where a message feature's ledger actually is, and that its window slides.
 *
 * <p>A feature that sets its own {@code root} names the <em>message home</em>, not the ledger
 * inside it: the append path adds the ledger segment ({@code root/message/_?incrq}) and the store
 * reads the same collection back ({@code <session home>/message}).  Resolving the field bare put
 * user prompts beside the ledger, so the provider only ever held its own replies.
 *
 * <p>The scenario drives the production write path — session policy row, per-turn store, per-turn
 * {@code ChatMemory} — with more turns than the window holds, which is what separates "the window
 * slides" from "the window is the right size".
 */
public class MessageLedgerRootTest extends AbstractMetatronTest {

    private static final fURI TEST_SPACE = f("/usr/test");
    private static final int MAX = 6;
    private static final int TURNS = 14;

    @BeforeAll
    public static void mountLedgerSpace() {
        InstSet.importInstSet(f("/m/llm"));
        InstSet.importInstSet(MATH_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    /**
     * A feature-rooted and an agent-rooted agent must produce the same ledger — the one the store
     * reads — and the same sliding window.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "a feature root names the message home   % true",
            "no feature root inherits the agent home % false",
    }, delimiter = '%')
    public void userMessagesStayInTheLedgerAndTheWindowSlides(final String label, final boolean featureRoot) {
        final fURI home = TEST_SPACE.extend("ledgerroot").extend(featureRoot ? "featured" : "inherited");
        final fURI sessionVID = home.extend(SESSION).extend("1");
        final WindowMessageFeature feature = messageFeature(home.extend(MESSAGE), sessionVID, featureRoot);
        final Agent agent = agent("ledger-root-agent", home, feature);
        assertEquals(home.extend(MESSAGE), feature.getRoot(agent),
                "[" + label + "] the ledger root is the message collection under the agent home");
        for (int turn = 1; turn <= TURNS; turn++) {
            feature.advanceChatId(agent);
            feature.onBeforeChat(agent);
            feature.addMessage(agent, MessageBuilder.build(USER_MESSAGE_TID)
                    .text("what is the word for turn " + turn + "?")
                    .contents("what is the word for turn " + turn + "?")
                    .session(sessionVID)
                    .depth(agent.chatDepth())
                    .chatId(agent.chatId())
                    .time()
                    .create());
            feature.memory().add(AiMessage.from("the word is turn" + turn));
            final List<ChatMessage> window = feature.memory().messages();
            assertTrue(window.size() <= MAX,
                    "[" + label + "] turn " + turn + ": window holds " + window.size() + " > " + MAX);
            assertEquals("what is the word for turn " + turn + "?", lastUserText(window),
                    "[" + label + "] turn " + turn + ": the newest prompt must be in the window, got " + texts(window));
            assertEquals("the word is turn" + turn, lastAiText(window),
                    "[" + label + "] turn " + turn + ": the newest reply must be in the window, got " + texts(window));
        }
        final int ledger = Machine.read(home.extend(MESSAGE).extend("+/")).stream().map(Obj::asRel).toList().size();
        assertEquals(2 * TURNS, ledger,
                "[" + label + "] every prompt and reply must land in " + home + "/message");
    }

    private static WindowMessageFeature messageFeature(final fURI home, final fURI sessionVID, final boolean featureRoot) {
        final Rec algorithm = rec(mutableMap(uri(NAME), uri("message_window"), uri("max"), jnt(MAX)));
        final Map<Obj, Obj> config = mutableMap(
                uri(SESSION), uri(sessionVID.toString()),
                uri(ALGORITHM), algorithm);
        if (featureRoot)
            config.put(uri(ROOT), uri(home.toString()));
        return new WindowMessageFeature(config, LLM_WINDOW_MESSAGE_FEATURE_TID, null);
    }

    private static Agent agent(final String name, final fURI home, final WindowMessageFeature feature) {
        return Agent.agent(rec(mutableMap(
                uri(NAME), str(name),
                uri(ROOT), uri(home.toString()),
                uri(FEATURE), lst(List.of(feature))), LLM_AGENT_TID, null));
    }

    private static String lastUserText(final List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--)
            if (messages.get(i) instanceof UserMessage user)
                return userText(user);
        return "<no user message>";
    }

    private static String lastAiText(final List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--)
            if (messages.get(i) instanceof AiMessage ai)
                return ai.text();
        return "<no ai message>";
    }

    private static String userText(final UserMessage user) {
        return user.hasSingleText() ? user.singleText()
                : user.contents().stream().filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text()).reduce("", (a, b) -> a + b);
    }

    private static List<String> texts(final List<ChatMessage> messages) {
        return messages.stream().map(m -> {
            if (m instanceof AiMessage ai)
                return "ai:" + ai.text();
            if (m instanceof UserMessage user)
                return "user:" + userText(user);
            return m.getClass().getSimpleName();
        }).toList();
    }
}
