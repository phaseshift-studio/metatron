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

package studio.phaseshift.metatron.isa.llm.space;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static io.modelcontextprotocol.spec.HttpHeaders.ACCEPT;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LocalAiModelCatalog implements ModelCatalog {

    private final String endpoint;

    public LocalAiModelCatalog(final String endpoint) {
        this.endpoint = endpoint;

    }

    @Override
    public List<ModelDescription> listModels() {
        try {
            // Ensure we're using the correct URL (http:// for LocalAI, not https://)
            final String modelsUrl = this.endpoint + "/models";
            final URL url = new URL(modelsUrl);
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Connect and read response
            conn.setRequestMethod("GET");
            conn.setRequestProperty(ACCEPT, MIME.MIMEType.APPLICATION_JSON.value);
            conn.connect();

            final BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            conn.disconnect();

            final JsonArray array = JsonParser.parseString(response.toString()).getAsJsonObject().getAsJsonArray(Tokens.DATA);
            return array.asList().stream().map(x -> ModelDescription.builder().name(x.getAsJsonObject().get(Tokens.ID).getAsString()).provider(ModelProvider.OTHER).build()).toList();
        } catch (IOException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }
}
