package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import io.github.ollama4j.models.response.Model;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.LLM;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.Space.HOST;
import static studio.phaseshift.metatron.lang.Space.NAME;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.MLLM_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class O4JLLM extends MRec implements LLM {

    public static final fURI O4JLLM_TID = MLLM_TID.extend("o4jllm");

    public O4JLLM(final Model model, final fURI tid, final fURI vid) {
        super(modelToRec(model), O4JLLM_TID, vid);
    }

    private static Map<Obj, Obj> modelToRec(final Model model) {
        return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.getModelName()));
            put(uri("size"), jnt(model.getSize()));
            put(uri("quant"), uri(model.getModelMeta().getQuantizationLevel()));
            put(uri("family"), uri(model.getModelMeta().getFamily()));
            //   put(uri("card"), rec(model.get1().getModelInfo(), MObjFactory.of()));
        }};

    }

    public static O4JLLM ollm(final Model model, final fURI tid, final fURI vid) {
        return new O4JLLM(model, tid, vid);
    }

    public String name() {
        return this.at(NAME).strValue();
    }

    public OLLM clone() {
        return (OLLM) super.clone();
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return (OLLM) super.clone(model, tid, vid);
    }
}
