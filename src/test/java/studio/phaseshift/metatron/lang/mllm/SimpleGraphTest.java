/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.lang.mllm;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Unit test for simple App.
 */

public class SimpleGraphTest {

    @Test
    @Disabled("Requires internet access")
    public void testOllamaToolUse() {
        var model = OllamaChatModel.builder()
                .modelName("qwen3:4b")
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build();
        String answer = model.chat("Provide 3 short bullet points explaining why Java is awesome");
        System.out.println(answer);
    }

    @Test
    @Disabled("Requires internet access")
    public void testTinkerGraph() {
              /*

               Graph graph = TinkerGraph.open();
        GraphTraversalSource g = graph.traversal();

              g.addV("greet").property("node", new GreeterNode()).as("greet")
                .addV("chat").property("node", new ChatNode(model)).as("chat")
                .addE("next").from("greet")
                .addV("responder").property("node", new ResponderNode()).as("responder")
                .addE("next").from("chat").iterate();
        System.out.println(graph);*/
    }

    interface Assistant {
        TokenStream chat(String message);
    }


    @Test
    @Disabled("Requires internet access")
    public void shouldAnswerWithTrue() throws Exception {

        var flatModel = OllamaChatModel.builder()
                .modelName("qwen3:4b")
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .think(true)
                .returnThinking(false)
                //.maxRetries(2)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(20))
                .build();
        var streamingModel = OllamaStreamingChatModel.builder()
                .modelName("qwen3:4b")
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .think(true)
                .returnThinking(false)
                //.maxRetries(2)
                .temperature(0.0)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingModel)
                // .tools(new SimpleTools())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        TokenStream tokenStream = assistant.chat("""
                Breakdown the problem of determining the age of the universe into distinct parallel steps.
                Each step must be able to be operated on by independent agents working concurrently and autonomously.
                The result of each step, must then be able to be aggregated by a 'reducing agent' to yield the final solution.
                For each distinct step, generate a prompt that can be fed to an agent for evaluation.
                The final output should be a json array with each element of the array being a json object with two keys: 
                    1.) summary (the summary of the prompt) and 
                    2.) prompt (the prompt to feed the agent).
                """);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        ChatResponse chatResponse = futureResponse.get(5, MINUTES);
        System.out.println("\nRESPONSE:\n" + chatResponse);
        Gson gson = new Gson();
        List<LinkedTreeMap<?, ?>> list = gson.fromJson(chatResponse.aiMessage().text(), List.class);
        System.out.println("\n\n\n-----\n" + list);

        Map<String, String> parts = new HashMap<>();
        for (LinkedTreeMap<?, ?> todo : list) {
            System.out.println("SOLVING: " + todo.get("summary").toString());
            var r = flatModel.chat(todo.get("prompt").toString());
            parts.put(todo.get("summary").toString(), r);
            System.out.println("RESULT: " + r + "\n---\n");
        }
        var finalQuestion = "What is the age of the universe (in years) given the following information\n: " + parts + ".\nReturn your answer as a  single number.";
        System.out.println(finalQuestion);
        TokenStream tokenStream2 = assistant.chat(finalQuestion);
        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();
        tokenStream2.onPartialResponse(System.out::print)
                .onCompleteResponse(futureResponse2::complete)
                .onError(futureResponse2::completeExceptionally)
                .start();
        ChatResponse x = futureResponse2.get(30, MINUTES);
        System.out.println("\n\n---THE UNIVERSE IF THIS OLD: " + x.aiMessage());
    }
}
