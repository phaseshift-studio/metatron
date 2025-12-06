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

package studio.phaseshift.metatron.lang.ai.llm.llama;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class llamaSpace {
}/*extends MSpace<LlamaModel> {

    protected static final Type OLLAMA_TYPE = T(OLLAMA_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(OLLAMA_TID), lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID), uri(HOST), T(URI_TID))))), (lhs, inst) -> {
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
        this.sjvm().availableModels().content().stream()
                .map(model -> ollm(Tuple.Triplet.with(model, this.sjvm().modelCard(model.getName()).content(), this.jvm().get(uri(HOST)).uriValue()), OLLM.OLLM_TID, modelToVid(model)))
                .filter(model -> model.vid().matches(pattern))
                .forEach(model -> this.internal.write(model.vid(), model));
        return this.internal.read(vid);

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isNoObj()) {
            this.internal.read(vid).stream().filter(o -> o instanceof OLLM).map(Obj::<OLLM>as).forEach(o -> {
                LOG.info("deleting ollama model: %s", o);
                //this.sjvm().deleteModel(o.name());
            });
        }
        return this.internal.write(vid, obj);
    }
}
*/