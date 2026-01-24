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

package studio.phaseshift.metatron.isa.sys.type.console;

import org.jline.builtins.Nano;
import org.jline.builtins.Options;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.io.serial.ObjStringSerializer;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Editor {

    public static boolean of(final Console console, final Object object) {
        try {
            Options options = Options.compile(Nano.usage()).parse(new String[]{
                    "--tabsize=2",
                    "--tabstospaces",
                    "--tempfile",
                    "--autoindent",
                    "--emptyline"});
            final Nano nano = new Nano(console.getTerminal(), Paths.get(""), options, console.getConfigurations());
            final File objFile = object instanceof File ?
                    (File) object :
                    (object instanceof Obj ?
                            Editor.createSourceFile(Highlighter.unformat(object.toString())) :
                            Editor.createSourceFile(object.toString()));
            nano.title = Graphitty.sillyPrint("metatron", false, true);
            nano.open(objFile.getPath());
            nano.smoothScrolling = true;
            nano.run();
            try (final BufferedReader reader = new BufferedReader(new FileReader(objFile))) {
                final String content = reader.lines().reduce((a, b) -> a + b + "\n").orElse("").trim();
                console.getReader().getBuffer().clear();
                console.getReader().getBuffer().write(content);
            }
            return true;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static File createSourceFile(final String source) {
        try {
            File objFile = File.createTempFile("console-", ".mtron");
            Files.writeString(objFile.toPath(), source);
            return objFile;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static File createObjFile(final Obj obj) {
        try {
            final File objFile = File.createTempFile("console-", ".mtron");
            final ObjStringSerializer serializer = ObjStringSerializer.build().prettyPrint(true).create();
            Files.writeString(objFile.toPath(), Highlighter.unformat(serializer.write(obj)));
            return objFile;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
