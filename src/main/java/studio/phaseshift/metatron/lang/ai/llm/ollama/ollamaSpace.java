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

package studio.phaseshift.metatron.lang.ai.llm.ollama;

import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.output.Response;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.GGUF;
import studio.phaseshift.metatron.lang.core.m.type.impl.MStr;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MUri;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Arrays;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.OLLAMA_TID;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM.ollm;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ollamaSpace extends MSpace<OllamaModels> {

    public static final Type OLLAMA_TYPE = T(OLLAMA_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(OLLAMA_TID), lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID), uri(HOST), T(URI_TID))))), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
        final fURI ollamaHost = inst.arg(0).<Rec>as().at(HOST).uriValue();
        final OllamaModels models = OllamaModels.builder().baseUrl(ollamaHost.toString()).build();
        final Space ollama = new ollamaSpace(models, inst.arg(0).jvm(), pattern, inst.arg(0).vid());
        Router.global().addSpace(ollama);
        return ollama;
    }));

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final kvSpace internal = new kvSpace(this.pattern, fURI.NULL);

    public ollamaSpace(final OllamaModels models, final Map<Obj, Obj> config, final fURI pattern, final fURI vid) {
        super(models, config, pattern, OLLAMA_TID, vid);
        LOG.info("available models: %s", lst(models.availableModels().content().stream().map(OllamaModel::getModel).map(MUri::uri).map(m -> (Obj) m).toList()));
    }

    public static ollamaSpace of(final fURI ollamaHost, final fURI pattern) {
        final OllamaModels models = OllamaModels.builder().baseUrl(ollamaHost.toString()).build();
        return new ollamaSpace(models, Map.of(
                uri(HOST), ollamaHost.toUri(),
                uri(PATTERN), pattern.toUri()),
                pattern,
                fURI.NULL);
    }

    private fURI modelToVid(final OllamaModel model) {
        return this.pattern.retractPattern().extend(model.getModel().replace(":", "/"));
    }

    @Override
    public Obj read(final fURI vid) {
        return this.qs().processPreRead(vid, vid).orElseGet(() -> {
            this.sjvm().availableModels().content().stream()
                    .map(model -> ollm(Tuple.Pair.with(model, this.sjvm().modelCard(model.getName()).content()), OLLM.OLLM_TID, modelToVid(model)))
                    .filter(model -> model.vid().matches(pattern))
                    .forEach(model -> {
                        final OllamaModelCard card = this.sjvm().modelCard(model.name()).content();
                        model.put(uri("uses"), objs(card.getCapabilities().stream().map(MStr::str)));
                        this.internal.write(model.vid(), model);
                        final String modelFile = card.getModelfile();
                        final String ggufFile = Arrays.stream(modelFile.split("\n")).map(String::trim).filter(line -> line.startsWith("FROM")).map(line -> line.replace("FROM ", "").trim()).findFirst().orElse(null);
                        final GGUF gguf = GGUF.of(f(ggufFile), fURI.NULL);
                        gguf.put(uri("quant"), uri(card.getDetails().getQuantizationLevel()));
                        gguf.put(uri("family"), uri(card.getDetails().getFormat()));

                        this.internal.write(model.vid().extend("guff"), gguf);
                    });
            return this.internal.read(vid);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
            if (obj.isNoObj()) {
                this.internal.read(vid).stream().filter(o -> o instanceof OLLM).map(Obj::<OLLM>as).forEach(o -> {
                    LOG.info("deleting ollama model: %s", o);
                    //this.sjvm().deleteModel(o.name());
                });
            }
            return this.internal.write(vid, obj);
        });
    }
}
