package studio.phaseshift.metatron;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class SimpleGraph {

    public static CompiledGraph<?> generate(final ChatModel model) throws GraphStateException {
        ResponderNode responderNode = new ResponderNode();
        GreeterNode greeterNode = new GreeterNode();
        ChatNode chatNode = new ChatNode(model);
        // Define the graph structure
        var stateGraph = new StateGraph<>(SimpleState.SCHEMA, SimpleState::new)
                .addNode("greeter", node_async(greeterNode))
                .addNode("chat", node_async(chatNode))
                .addNode("responder", node_async(responderNode))
                // Define edges
                .addEdge(START, "greeter") // Start with the greeter node
                .addEdge("greeter", "chat")
                .addEdge("chat", "responder")
                .addEdge("responder", END)   // End after the responder node
                ;
        // Compile the graph
        return stateGraph.compile();
    }
}