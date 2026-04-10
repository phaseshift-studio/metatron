/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.llm.space;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MEMORY_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpaceChatMemoryStore implements ChatMemoryStore {

    private static final SpaceChatMemoryStore INSTANCE = new SpaceChatMemoryStore();
    private static final GraphittyLogger LOG = Graphitty.log(SpaceChatMemoryStore.class);

    public static SpaceChatMemoryStore single() {
        return INSTANCE;
    }

    private SpaceChatMemoryStore() {
        // do nothing
    }

    public List<ChatMessage> getMessages(final Object memoryId) {
        final Lst messages = Router.readFromSpace((fURI) memoryId).orSupply(() -> lst(new ArrayList<>(), LLM_MEMORY_TID, (fURI) memoryId));
        final List<ChatMessage> llmMessages = messages.isEmpty() ?
                new ArrayList<>() :
                messages.elements().map(e -> messageFromJson(ObjSimpleJSONSerializer.single().write(e).toString())).collect(Collectors.toCollection(ArrayList::new));
        LOG.debug("getting messages for %s:\n%s\n---\n%s", memoryId, messages, llmMessages);
        return llmMessages;
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        final String json = messagesToJson(messages);
        final Obj obj = ObjSimpleJSONSerializer.parse(json);//.as(OLLM_AI_MEMORY);
        LOG.debug("updating messages for %s: %s", memoryId, obj);
        obj.vid((fURI) memoryId); // TODO: type them memory and user/ai memory
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        LOG.debug("deleting messages for %s", memoryId);
        Router.writeToSpace((fURI) memoryId, noobj());
    }
}
