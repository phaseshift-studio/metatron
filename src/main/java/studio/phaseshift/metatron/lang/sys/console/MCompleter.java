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

import org.jline.builtins.Completers;
import org.jline.reader.*;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MCompleter implements Completer {
    
    // private Console console;

    public MCompleter() {

    }

    public void complete(LineReader reader, ParsedLine commandLine, final List<Candidate> candidates) {
        try {
            final Buffer buffer = reader.getBuffer();
            final Obj x = mParser.eval(buffer.toString());
            x.stream().forEach(obj -> {
                if (obj instanceof Rec) {
                    candidates.addAll(obj.<Rec>as().elements().filter(r -> r.first().isUri()).map(r -> new Candidate(
                            "/" + r.first().uriValue().toString(),
                            Graphitty.string(r.first().toString()),
                            null,
                            null,
                            "/",
                            null,
                            true)).toList());
                } else if (obj instanceof Rel) {
                    candidates.addAll(obj.<Rel>as().stream().map(Obj::<Rel>as).filter(r -> r.first().isUri()).map(r -> new Candidate(
                            "/" + r.first().uriValue().toString(),
                            Graphitty.string(r.first().toString()),
                            null,
                            null,
                            "/",
                            null,
                            true)).toList());
                }
            });
            if(!candidates.isEmpty()) {
                buffer.backspace();
                buffer.backspace();
                buffer.backspace();
            }
        } catch (final Exception e) {
            // do nothing
        }
    }
}
