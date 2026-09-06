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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;

import java.util.List;
import java.util.Scanner;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class HumanStreamingModel implements StreamingChatModel {

    private static final GraphittyLogger LOG = Graphitty.log(HumanStreamingModel.class);
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
        // Run on a separate thread so the caller isn't blocked
        ThreadExecutor.instance().submit(() -> {
            try {
                final List<ChatMessage> messages = chatRequest.messages();
                final ChatMessage lastMessage = messages.getLast();

                // Print ALL messages so the human sees the system prompt + history
                LOG.none("\n--- {{c}}context{{/c}} ---\n");
                for (var msg : chatRequest.messages()) {
                    if (msg instanceof SystemMessage sys) {
                        LOG.none("[{{y}}SYSTEM{{/y}}] %s\n", sys.text().trim());
                    } else if (msg instanceof UserMessage user) {
                        LOG.none("[{{y}}USER{{/y}}] %s\n", user.singleText().trim());
                    } else if (msg instanceof AiMessage ai) {
                        if (ai.hasToolExecutionRequests())
                            LOG.none("[{{y}}AI  {{/y}}] → calls %s\n", ai.toolExecutionRequests().getFirst().name().trim());
                        else
                            LOG.none("[{{y}}AI  {{/y}}] %s\n", ai.text().trim());
                    }
                }

                // Print available tools
                final List<ToolSpecification> tools = chatRequest.toolSpecifications();
                if (tools != null && !tools.isEmpty()) {
                    LOG.none("\n--- {{c}}available tools{{/c}} ---\n");
                    for (ToolSpecification tool : tools) {
                        LOG.none(" • %s - %s\n", tool.name(), tool.description());
                        LOG.none("     %s\n", tool.parameters().properties());
                    }
                }
                LOG.none("---------------------\n\n");
                LOG.none("response (or 'call:<tool>:<json_args> to invoke a tool)\n");
                if (lastMessage.type().equals(ChatMessageType.USER))
                    LOG.none("--- human agent (respond to): %s ---\n", ((UserMessage) lastMessage).singleText());
                else if (lastMessage.type().equals(ChatMessageType.TOOL_EXECUTION_RESULT))
                    LOG.none("-- tool result: %s ---\n", ((ToolExecutionResultMessage) lastMessage).toolName());


                String humanResponse = "";
                final StringBuilder completeResponse = new StringBuilder();
                while (!humanResponse.contains("<<done>>")) {
                    humanResponse = scanner.nextLine().trim();
                    if (humanResponse.startsWith("call:")) {
                        final String[] parts = humanResponse.split(":", 3);
                        if (parts.length < 3) {
                            handler.onError(new IllegalArgumentException("format: call:<tool_name>:<json_args>"));
                            return;
                        }
                        final String toolName = parts[1].trim();
                        final String arguments = parts[2].trim();
                        final ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                                .id("call_" + System.currentTimeMillis())
                                .name(toolName)
                                .arguments(arguments)
                                .build();
                        handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(toolReq)).build());
                    } else {
                        completeResponse.append(humanResponse.replace("<<done>>", ""));
                        for (final String word : humanResponse.split("(?<=\\s)")) {
                            handler.onPartialResponse(word);
                        }
                    }
                }
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(completeResponse.toString().trim())).build());
            } catch (Exception e) {
                handler.onError(e);
            }
        });
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.builder()
                .modelName("human:latest")
                .build();
    }
}