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

import org.jline.builtins.SyntaxHighlighter;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;

import java.nio.file.Paths;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MHighlighter implements Highlighter {
    private final Console console;
    private final SyntaxHighlighter highlighter;
  
    private MHighlighter(final Console console) {
        this.console = console;
        this.highlighter = SyntaxHighlighter.build(Paths.get("/home/killswitch/software/metatron/conf/mtron.nanorc"), "mtron");
    }

    @Override
    public AttributedString highlight(final LineReader reader, final String buffer) {
        return this.highlighter.highlight(reader.getBuffer().toString());
    }
}