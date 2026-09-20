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

package studio.phaseshift.metatron.isa.llm;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.List.of;

/**
 * The est breakdown of a chat's usage rides on the listener's
 * {@code onRequest} — and the real models are all streaming.  This pins the
 * premise: a streaming model fires the request listener before its first
 * network attempt (driven through the publisher the streaming contract
 * returns, against a refused endpoint so no provider call is ever made),
 * and never fires a response for a call that never answered.
 */
public class StreamingListenerTest extends AbstractMetatronTest {

    @Test
    public void aStreamingModelFiresTheRequestListenerBeforeItsFirstAttempt() {
        final List<String> phases = Collections.synchronizedList(new ArrayList<>());
        final ChatModelListener listener = new ChatModelListener() {
            @Override
            public void onRequest(final ChatModelRequestContext ctx) {
                phases.add("request");
            }

            @Override
            public void onResponse(final ChatModelResponseContext ctx) {
                phases.add("response");
            }

            @Override
            public void onError(final ChatModelErrorContext ctx) {
                phases.add("error");
            }
        };
        final StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl("http://127.0.0.1:1") // refused — the probe only needs the phase list
                .modelName("probe")
                .timeout(Duration.ofMillis(1500))
                .listeners(of(listener))
                .build();
        // the streaming contract is a publisher — the call only runs once it is subscribed
        final Flow.Publisher<ChatModelStreamingEvent> events = model.chat(UserMessage.from("a probe"));
        events.subscribe(new Flow.Subscriber<ChatModelStreamingEvent>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final ChatModelStreamingEvent event) {
                // the probe has no business in the content
            }

            @Override
            public void onError(final Throwable throwable) {
                // expected: there is no endpoint at 127.0.0.1:1
            }

            @Override
            public void onComplete() {
            }
        });
        // the request listener fires synchronously before the attempt — allow a beat for delivery
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (phases.isEmpty() && System.nanoTime() < deadline)
            Thread.yield();
        assertTrue(phases.contains("request"), "onRequest fired before the attempt — phases: " + phases);
        assertFalse(phases.contains("response"), "no response arrived, none may be reported — phases: " + phases);
    }
}
