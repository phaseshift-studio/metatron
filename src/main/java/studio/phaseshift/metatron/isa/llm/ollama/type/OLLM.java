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

package studio.phaseshift.metatron.isa.llm.ollama.type;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM {
    
    /*public OLLM(final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        super(modelToRec(model), tid, vid);
    }
    

    public static OLLM ollm(final fURI host, final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        final OLLM ollm = new OLLM(model, tid, vid);
        return ollm.at(uri(HOST), uri(host), MUTABLE).as();
    }

    public String name() {
        return this.at(NAME).uriValue().toString();
    }

    public OLLM clone() {
        return this;
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return (OLLM) super.clone(model, tid, vid);
    }

    public static final ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("mtron")
            .description("evaluate an metatron expression")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("code", "metatron code to evaluate")
                    .required("code")
                    .build())
            .build();

    public static class MetatronTools {

        public MetatronTools() {

        }

        @Tool("executes metatron code and returns an obj result")
        Obj evaluate(
                @P("the metatron code to evaluate") String code
        ) {
            return mParser.eval(code);
        }
    }*/
}