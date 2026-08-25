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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.vec.type.MVec.vec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Model extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    public Model(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Model model(final Rec modelRec) {
        return new Model(modelRec.jvm(), modelRec.tid(), modelRec.vid());
    }

    public Uri llm() {
        return this.at(LLM).asUri();
    }

    public Uri host() {
        return this.at(HOST).asUri();
    }

    public Uri provider() {
        return this.at(PROVIDER).asUri();
    }

    public Uri protocol() {
        return this.at(PROTOCOL).asUri();
    }

    public Str apiKey() {
        return this.at(API_KEY).orElse(str0()).asStr();
    }

    public Obj size() {
        return this.at(SIZE);
    }

    public Obj quant() {
        return this.at(QUANT);
    }

    public Rec cost() {
        return this.at(COST).orElse(rec0());
    }

    public Lst skill() {
        return this.at(SKILL).orElse(lst0());
    }

    public Lst embed(final Obj toEmbed) {
        final EmbeddingModel embeddingModel = LLMFactory.createEmbeddingInteraction(this);
        // if (this.cost().isPresent())
        //     agent.addListener(new CostCalculator(this.cost().get()));
        final TextSegment embeddingString = TextSegment.from(Str.Helper.cleanString(toEmbed));
        final Response<Embedding> response = embeddingModel.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());

        return vec(response.content().vectorAsDoubleArray());
    }
}