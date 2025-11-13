package studio.phaseshift.metatron.lang.ai.llm.llama;

import com.github.tjake.jlama.model.llama.LlamaModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.ai.llm.ollama.ollamaSpace;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MUri;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.OLLAMA_TID;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM.ollm;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class llamaSpace {}/*extends MSpace<LlamaModel> {

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