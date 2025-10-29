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

import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mllm.type.impl.OLLM;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mllm.type.impl.OLLM.ollm;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mollamaSpace extends MSpace<OllamaModels> {

    public static final fURI MLLM_ID = f("/mllm");
    public static final fURI MOLLAMA_SPACE = MLLM_ID.extend("space/mollama");
    private final GraphittyLogger LOG = Graphitty.log(this);
    public final fURI ollamaHost;

    public mollamaSpace(final OllamaModels models, final fURI ollamaHost, final fURI pattern, final fURI vid) {
        super(models, pattern, MOLLAMA_SPACE, vid);
        this.ollamaHost = ollamaHost;
        LOG.info("loading models: %s", models.availableModels().content().stream().map(OllamaModel::getModel).toList());
    }

    public static mollamaSpace of(final fURI ollamaHost, final fURI pattern, final fURI vid) {
        final OllamaModels models = OllamaModels.builder().baseUrl(ollamaHost.toString()).build();
        return new mollamaSpace(models, ollamaHost, pattern, vid);
    }


    private fURI modelToVid(final OllamaModel model) {
        return this.pattern.retractPattern().extend(model.getModel().replace(":", "/"));
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helper.resolveRead(this, vid, v -> {
            final Map<fURI, Obj> results = new HashMap<>();
            this.jvm.availableModels().content().stream().filter(m -> modelToVid(m).matches(v)).forEach(m -> {
                results.put(modelToVid(m), ollm(m, OLLM.OLLM_TID, modelToVid(m)));
            });
            return results;
        });
    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return NoObj.single();
    }
}
