package studio.phaseshift.metatron;

import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.List;
import java.util.Map;

// Node that adds a greeting
class GreeterNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("GreeterNode executing. Current messages: " + state.messages());
        return Map.of(SimpleState.MESSAGES_KEY, "Hello from GreeterNode!");
    }
}

// Node that adds a response
class ResponderNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("ResponderNode executing. Current messages: " + state.messages());
        List<String> currentMessages = state.messages();
        return Map.of(SimpleState.MESSAGES_KEY, currentMessages.toString());
    }
}

class ChatNode implements NodeAction<SimpleState> {
    private final ChatModel model;

    public ChatNode(ChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("ChatNode executing. Current messages: " + state.messages());
        List<String> currentMessages = state.messages();
        return Map.of(SimpleState.MESSAGES_KEY, this.model.chat(currentMessages.get(0)));
    }
}