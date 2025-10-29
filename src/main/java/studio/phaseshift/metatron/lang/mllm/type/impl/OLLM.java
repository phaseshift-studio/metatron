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

package studio.phaseshift.metatron.lang.mllm.type.impl;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mllm.mollamaSpace;
import studio.phaseshift.metatron.lang.mllm.type.LLM;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObj;
import studio.phaseshift.metatron.space.Router;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM extends MObj implements LLM {

    public static final fURI OLLM_TID = f("/mllm/ollm");

    public OLLM(final OllamaModel model, final fURI tid, final fURI vid) {
        super(model, tid, vid);
        this.addInst(instC(f("chat").dom(this.tid()).rng(STR_TID.maybeSome()), lst(T(STR_TID)), (lhs, inst) -> {
            return str(OllamaChatModel.builder().baseUrl(this.getOllamaEndpoint().toString()).modelName(this.jvm().getName()).build().chat(inst.arg(0).strValue()));
        }));
    }

    public static OLLM ollm(final OllamaModel model, final fURI tid, final fURI vid) {
        return new OLLM(model, tid, vid);
    }

    public fURI getOllamaEndpoint() {
        return ((mollamaSpace) Router.global().getSpace(this.vid)).ollamaHost;
    }

    public OllamaModel jvm() {
        return (OllamaModel) this.jvm;
    }

    public OLLM clone() {
        return (OLLM) super.clone();
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return super.clone(model, tid, vid);
    }

    private fURI modelToVid(final OllamaModel model) {
        return this.vid().retractPattern().extend(model.getModel().replace(":", "/"));
    }
}
