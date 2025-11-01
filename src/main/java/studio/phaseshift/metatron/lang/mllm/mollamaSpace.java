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
import studio.phaseshift.metatron.lang.mkv.mkvSpace;
import studio.phaseshift.metatron.lang.mllm.type.impl.OLLM;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MUri;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mllm.type.impl.OLLM.ollm;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mollamaSpace extends MSpace<OllamaModels> {

    public static final fURI MLLM_ID = f("/mllm");
    public static final fURI MOLLAMA_TID = MLLM_ID.extend("space/mollama");
    private final fURI ollamaHost;
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final mkvSpace internal = new mkvSpace(this.pattern, fURI.NULL);

    public mollamaSpace(final OllamaModels models, final fURI ollamaHost, final fURI pattern, final fURI vid) {
        super(models, pattern, MOLLAMA_TID, vid);
        this.ollamaHost = ollamaHost;
        LOG.info("available models: %s", lst(models.availableModels().content().stream().map(OllamaModel::getModel).map(MUri::uri).map(m -> (Obj) m).toList()));
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
        this.jvm().availableModels().content().stream()
                .map(model -> ollm(Tuple.Pair.with(model, this.ollamaHost), OLLM.OLLM_TID, modelToVid(model)))
                .filter(model -> model.vid().matches(pattern))
                .forEach(model -> this.internal.write(model.vid(), model));
        return this.internal.read(vid);

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.internal.write(vid, obj);
    }
}
