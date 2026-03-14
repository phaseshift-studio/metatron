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

package studio.phaseshift.metatron.isa.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LLMFactory {

    private LLMFactory() {
    }

    public static StreamingChatModel createModel(final Rec llm, String modelName) {
        final fURI provider = llm.at(f(PROVIDER).extend(NAME)).uriValue();
        final String host = llm.at(HOST).uriValue().toString();
        //   final boolean toolUse = !llm.at(TOOL).isNoObj();
        final boolean thinking = llm.at(THINK).orElse(bool(false)).boolValue();
        // final Uri memory = llm.at(MEMORY).orElse(null);
        final Str api_key = llm.at(API_KEY).orElse(null);
        final String model = llm.at(NAME).uriValue().toString();
        return switch (provider.toString().toLowerCase()) {
            case "ollama" -> OllamaStreamingChatModel.builder()
                    .baseUrl(host)
                    .modelName(model)
                    .think(thinking)
                    .returnThinking(thinking)
                    .build();

            case "openai" -> OpenAiStreamingChatModel.builder()
                    .apiKey(api_key.strValue())
                    .modelName(modelName)
                    .returnThinking(thinking)
                    .build();

            default -> throw MTronException.of("unsupported LLM provider: %s", provider);
        };
    }
}
