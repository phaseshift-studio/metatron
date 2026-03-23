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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.reader.*;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MCompleter implements Completer {

    protected Console console;

    public MCompleter(final Console console) {
        this.console = console;
    }

    public void complete(LineReader reader, ParsedLine commandLine, final List<Candidate> candidates) {
        try {
            final Buffer buffer = reader.getBuffer();
            if (!buffer.toString().isEmpty() && buffer.toString().endsWith(". ")) {
                final String b = buffer.toString().substring(0, buffer.toString().length() - 2);
                final Obj o = mParser.parse(b);
                if (o.isCode()) {
                    final Inst lastInst = o.resolve(noobj()).codeValue().getLast();
                    final Obj insts = Router.readFromSpace(f("/m/inst/#?dom=" + lastInst.tid().rng()));
                    insts.forEach(i -> candidates.add(new Candidate(i.<Inst>as().tid().basePath() + "(" + (i.<Inst>as().args().isEmpty() ? ")" : ""), Graphitty.string(i.toString()), null, null, "", null, false)));
                }
            } else if (!buffer.toString().isEmpty() && buffer.toString().charAt(buffer.toString().length() - 1) == ' ') {
                final Obj o = mParser.parse(buffer.toString());
                if (o.isCode())
                    candidates.add(new Candidate("", new Explain(o.as()).format(), null, null, "", null, false));
            } else {
                final Obj results = mParser.eval(buffer.toString());
                results.forEach(obj -> candidates.addAll(makeCandidate(obj, results.unique())));
            }
        } catch (final Exception e) {
            // do nothing
        }
    }

    private List<Candidate> makeCandidate(final Obj obj, final boolean unique) {
        if (obj.isRec()) {
            return obj.<Rec>as().elements()
                    .filter(r -> r.first().isUri())
                    .map(r -> new Candidate(
                            "/" + r.first().uriValue().toString(),
                            Graphitty.string(r.first().toString()),
                            null, //r.first().uriValue().toString(),
                            null,
                            "/",
                            null,
                            !r.second().isPoly())).toList();
        } else if (obj.isRel()) {
            return obj.<Rel>as().stream()
                    .map(Obj::<Rel>as)
                    .filter(r -> r.first().isUri())
                    .map(r -> new Candidate(
                            "*" + r.first().uriValue().toString(),
                            Graphitty.string(r.first().toString()),
                            null, //r.first().uriValue().toString(),
                            null,
                            "/",
                            null,
                            !r.second().isPoly())).toList();
        } else {
            return List.of();
        }
    }
}
