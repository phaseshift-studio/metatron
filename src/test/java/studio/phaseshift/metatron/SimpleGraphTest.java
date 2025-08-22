package studio.phaseshift.metatron;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit test for simple App.
 */
public class SimpleGraphTest {

    @Test
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

    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() throws Exception {
        Graph graph = TinkerGraph.open();
        GraphTraversalSource g = graph.traversal();


        var model = OllamaChatModel.builder()
                .modelName("qwen3:4b")
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build();

        g.addV("greet").property("node", new GreeterNode()).as("greet")
                .addV("chat").property("node", new ChatNode(model)).as("chat")
                .addE("next").from("greet")
                .addV("responder").property("node", new ResponderNode()).as("responder")
                .addE("next").from("chat").iterate();
        System.out.println(graph);

      //  for (var item : SimpleGraph.generate(model).stream(Map.of(SimpleState.MESSAGES_KEY, "Let's begin!"))) {
      //      System.out.println(item);
      //  }
    }
}
