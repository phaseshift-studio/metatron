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

package studio.phaseshift.metatron.lang.sys.console;

import org.jline.reader.*;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Code;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer;
import studio.phaseshift.metatron.ui.Box;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.List;

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
            if (!buffer.toString().isEmpty() && buffer.toString().charAt(buffer.toString().length() - 1) == ' ') {
                final Obj o = mParser.parse(buffer.toString());
                if (o.isCode()) {
                    final Code code = o.resolve(NoObj.noobj()).as();
                    final String pretty = Graphitty.string(
                            new Box(ObjStringSerializer.prettyPrintCode(code, 0), Box.BASIC_BORDER)
                                    .bottom(new Box(new Profile(code).toString(), Box.BASIC_BORDER)).toString());
                    candidates.add(new Candidate("", pretty, null, null, "", null, false));
                }
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
                            r.first().uriValue().toString(),
                            Graphitty.string(r.second().toString()),
                            "/",
                            r.first().uriValue().toString(),
                            !r.second().isPoly())).toList();
        } else if (obj.isRel()) {
            return obj.<Rel>as().stream()
                    .map(Obj::<Rel>as)
                    .filter(r -> r.first().isUri())
                    .map(r -> new Candidate(
                            "*" + r.first().uriValue().toString(),
                            Graphitty.string(r.first().toString()),
                            r.first().uriValue().toString(),
                            Graphitty.string(r.second().toString()),
                            "/",
                            r.first().uriValue().toString(),
                            !r.second().isPoly())).toList();
        } else {
            return List.of();
        }
    }
}
