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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron;

import dev.langchain4j.model.chat.ChatModel;
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
                .addEdge("responder", END);   // End after the responder node

        // Compile the graph
        return stateGraph.compile();
    }
}