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

package studio.phaseshift.metatron.lang.ai.llm.ollama;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ollamaSpace {//extends MSpace<OllamaModels> {

   /* protected final fURI internalPrefix;

    public static final Type OLLAMA_TYPE =
            T(OLLAMA_TID, null,
                    instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(OLLAMA_TID),
                            lst(T(REC_TID, isa_(rec(
                                    uri(Tokens.PATTERN), T(URI_TID),
                                    uri(Tokens.HOST), T(URI_TID),
                                    uri(STORE).maybe(), T(REC_TID))))), (lhs, inst) -> {
                                final fURI pattern = inst.arg(0).orElse(rec()).<Rec>as().at(Tokens.PATTERN).uriValue();
                                final fURI ollamaHost = inst.arg(0).orElse(rec()).<Rec>as().at(Tokens.HOST).uriValue();
                                final OllamaModels models = OllamaModels.builder().baseUrl(ollamaHost.toString()).build();
                                final Space ollama = new ollamaSpace(models, inst.arg(0).jvm(), pattern, inst.arg(0).vid());
                                Router.global().addSpace(ollama);
                                return ollama;
                            }));

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Space internal;

    public ollamaSpace(final OllamaModels models, final Map<Obj, Obj> config, final fURI pattern, final fURI vid) {
        super(models, config, pattern, OLLAMA_TID, vid);
        this.internal = (Space) (config.containsKey(uri(STORE)) ? config.get(uri(STORE)) : new kvSpace(pattern, fnull));
        this.internalPrefix = this.internal.at(Tokens.PREFIX).orElse(uri("")).uriValue();
        LOG.info("available models: %s", lst(models.availableModels().content().stream().map(OllamaModel::getModel).map(MUri::uri).map(m -> (Obj) m).toList()));
    }

    public static ollamaSpace of(final fURI ollamaHost, final fURI pattern) {
        final OllamaModels models = OllamaModels.builder().baseUrl(ollamaHost.toString()).build();
        return new ollamaSpace(models, Map.of(
                uri(Tokens.HOST), ollamaHost.toUri(),
                uri(Tokens.PATTERN), pattern.toUri(),
                uri(Tokens.STORE), new kvSpace(pattern, fnull)),
                pattern,
                fnull);
    }

    private fURI modelToVid(final String modelName) {
        return this.pattern.retractPattern().extend(modelName.replace(":", "/"));
    }

    @Override
    public Obj read(final fURI vid) {
        final Obj result = this.internal.read(internalMapping(vid, false));
        if (!result.isNoObj())
            return result;
        else {
         //   this.findModel(vid);
            final Obj result2 = this.internal.read(internalMapping(vid, false));
            if (!result2.isNoObj())
                return result2;
            try {
                LOG.info("attempting to pull model: {{y}}%s{{X}}", vid.removePrefix(this.pattern.retractPattern()).toString());
                final String version = vid.name();
                new Ollama(this.at(Tokens.HOST).uriValue().toString()).pullModel(vid.removePrefix(this.pattern.retractPattern()).retract().toString() + ":" + version);
            } catch (final Exception e) {
                LOG.warn(e.getMessage());
            }
            return this.internal.read(internalMapping(vid, false));
        }
    }

    protected fURI internalMapping(final fURI furi, final boolean internalToExternal) {
        if (internalToExternal)
            return furi.removePrefix(this.internalPrefix);
        else
            return this.internalPrefix.extend(furi);
    }


    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            if (obj.isNoObj()) {
                this.internal.read(internalMapping(vid, false)).stream().filter(o -> o instanceof OLLM).map(Obj::<OLLM>as).forEach(o -> {
                    LOG.info("deleting ollama model: %s", o);
                    //this.sjvm().deleteModel(o.name());
                });
            }
            return this.internal.write(internalMapping(vid, false), obj);
        });
    }

    /*private void findModel(final fURI modelPattern) {
        this.sjvm().availableModels().content().stream()
                .map(model -> {
                    final OllamaModelCard card = this.sjvm().modelCard(model.getName()).content();
                    final OLLM ollm = ollm(Tuple.Pair.with(model, card), OLLM.OLLM_TID, modelToVid(model.getName()));
                    return Tuple.Pair.with(card, ollm);
                })
                .filter(pair -> pair.get1().vid().matches(modelPattern))
                .forEach(pair -> {
                    try {
                        final Obj gguf = this.internal.read(internalMapping(pair.get1().vid().extend(Tokens.GGUF_KEY), false));
                        if (gguf.isNoObj()) {
                            this.internal.write(internalMapping(pair.get1().vid().extend(Tokens.GGUF_KEY), false), fail(MTronException.of("temp")));
                            final String ggufFilePath = Arrays.stream(pair.get0().getModelfile().split("\n"))
                                    .map(String::trim)
                                    .filter(line -> line.startsWith(Tokens.FROM))
                                    .map(line -> line.replace(Tokens.FROM, "").trim())
                                    .findFirst()
                                    .orElse(null);
                            if (ggufFilePath != null) {
                                LOG.info("onboarding ollm gguf into space: %s", pair.get1().vid().extend(Tokens.GGUF_KEY));
                                GGUF.of(f(ggufFilePath),
                                        null,// pair.get1().vid().extend(Tokens.GGUF_KEY),Common.isInt(pair.get0().getDetails().getParameterSize()) ? Long.parseLong(pair.get0().getDetails().getParameterSize())) : noobj()))
                                        f(pair.get0().getDetails().getQuantizationLevel()),
                                        f(pair.get0().getDetails().getFormat()), pair.get1().vid().extend(Tokens.GGUF_KEY));
                            }
                        }
                    } catch (final Exception e) {
                        LOG.warn(e);
                        this.internal.write(internalMapping(pair.get1().vid().extend(Tokens.GGUF_KEY), false), fail(e));
                    }
                });*/
    //}
}
