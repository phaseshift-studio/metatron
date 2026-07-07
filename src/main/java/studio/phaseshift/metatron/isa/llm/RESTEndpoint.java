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

package studio.phaseshift.metatron.isa.llm;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static studio.phaseshift.metatron.Tokens.LLM;
import static studio.phaseshift.metatron.Tokens.MODEL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RESTEndpoint {

    private RESTEndpoint() {
        // do nothing
    }

    public static Rec handleOllama(final fURI baseURI, final Rec preModel) {
        final Rec ollamaModel = Router.writeToSpace(baseURI.extend("api").extend("show"), rec(uri(MODEL), str(preModel.at(LLM).uriValue().toString()))).as();

        final Rec postModel = rec(preModel.recValue());
        preModel.logger().info("HERE %s", ollamaModel.at(f("model_info").extend("general.parameter_count")));
        postModel.at("param", ollamaModel.at(f("model_info").extend("general.parameter_count")), MUTABLE);
        postModel.at("capabilities", ollamaModel.at("capabilities"), MUTABLE);
        return postModel;
    }
}
