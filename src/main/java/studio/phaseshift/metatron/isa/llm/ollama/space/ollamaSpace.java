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

package studio.phaseshift.metatron.isa.llm.ollama.space;

import dev.langchain4j.model.ollama.OllamaModels;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.llm.ollama.ollamaInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ollamaSpace extends AbstractSpace<OllamaModels> {

    public static final Type OLLAMA_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(OLLAMA_SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(HOST), URI_TYPE,
                    uri(ROUTE), rec(URI_TYPE, URI_TYPE)))
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(OLLAMA_TID), lst(T(REC_TID)), (lhs, inst) ->
                    ollamaSpace.of(new OllamaModels.OllamaModelsBuilder()
                            .baseUrl(inst.arg(0).<Rec>as().at(HOST)
                                    .uriValue().toString()).build(), inst.arg(0).jvm(), inst.arg(0).vid())
            )).create();

    private final memSpace cache;

    public static ollamaSpace of(final OllamaModels models, final Map<Obj, Obj> config, final fURI vid) {
        return new ollamaSpace(models, config, vid);
    }

    public ollamaSpace(final OllamaModels models, final Map<Obj, Obj> config, final fURI vid) {
        super(models, config, OLLAMA_TID, vid);
        this.cache = memSpace.of(this.pattern(), null);
        this.refreshModels();
    }

    protected void refreshModels() {
        final OllamaModels models = this.sjvm;
        models.availableModels().content().stream()
                .map(m -> Tuple.Pair.with(m, models.modelCard(m.getName()).content()))
                .map(m -> rec(
                        Map.of(uri(HOST), this.at(Tokens.HOST),
                                uri(NAME), uri(m.get0().getName()),
                                uri(THINK), bool(m.get1().getCapabilities().contains(THINKING)),
                                uri(SKILL), lst(m.get1().getCapabilities().stream().map(MUri::uri)),
                                uri(SIZE), jnt(m.get0().getSize(), MATH_BYTE_TID, null)), OLLAMA_OLLM_TID, null)).forEach(m -> {
                    this.write(this.pattern.retract(1).extend(m.at(NAME).uriValue()), m);
                });
    }

    @Override
    public Obj read(final fURI vid) {
        return this.cache.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.cache.write(vid, obj);
    }
}
