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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.builtins.Commands;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjLinkSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjConsoleSerializer extends ObjLinkSerializer {

    public ObjConsoleSerializer() {
        super();          // the link tagging comes from the parent; the clipping is this class's
    }

    private static final ObjConsoleSerializer INSTANCE = new ObjConsoleSerializer();

    /**
     * Nothing to scroll: there is no terminal to scroll it in.
     * <p>
     * This class serializes for whoever is drawing — the console, a test, another UI — and the
     * console is only one of them.  Reading a terminal height to decide
     * whether a value needs a pager is console business, so it happens only where a terminal
     * exists; without one the text is written as it is.
     */
    private boolean hasTerminal() {
        return null != Console.getTerminal() && !BootLoader.TESTING;
    }

    private String clipString(final String string, final int maxLength) {
        if (!hasTerminal()) return string;
        final int count = string.split("\n").length;
        if (count > maxLength) {
            try {
                Commands.less(Console.getTerminal(), new ByteArrayInputStream(Highlighter.format(string).getBytes(UTF_8)), new PrintStream(Console.getTerminal().output()), System.err, Paths.get(""), new String[]{"--ignorercfiles"});
                return string;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return string;
    }

    private String clipObj(final Obj obj) {
        //if (!obj.hasVID())
        return clipString(super.write(obj), hasTerminal() ? Console.getTerminal().getHeight() : Integer.MAX_VALUE);
        /*final Obj clone = obj.clone();
        final fURI vid = clone.vid();
        clone.selfVID(vid);
        final String cloneString = clipString(super.write(clone), Console.getTerminal().getHeight());
        return cloneString + "@" + this.writeUri(vid.toUri());*/
    }


    @Override
    public String write(final Obj obj) {
        return this.clipObj(obj);
    }

    /*@Override
    public String writeNoObj(final NoObj n) {
        return this.clipObj(n);
    }

    @Override
    public String writeFail(final Fail f) {
        return this.clipObj(f);
    }

    @Override
    public String writeBool(final Bool b) {
        return this.clipObj(b);
    }

    @Override
    public String writeInt(final Int i) {
        return this.clipObj(i);
    }

    @Override
    public String writeReal(final Real r) {
        return this.clipObj(r);
    }

    @Override
    public String writeStr(final Str s) {
        return this.clipObj(s);
    }

    @Override
    public String writeRel(final Rel r) {
        return this.clipObj(r);
    }

    @Override
    public String writeLst(final Lst l) {
        return this.clipObj(l);
    }

    @Override
    public String writeRec(final Rec r) {
        return this.clipObj(r);
    }

    @Override
    public String writeCode(final Code c) {
        return this.clipObj(c);
    }

    @Override
    public String writeObjs(final Objs o) {
        return this.clipObj(o);
    }

    public String writeType(final Type t) {
        return super.write(t);
    }*/

    @Override
    public String writeInst(final Inst inst) {
        return Obj.Helper.getAutoPointer(inst).map(furi -> (inst.isAutoFrom() ? "!*" : "!@") + this.writeUri(furi.toUri())).orElseGet(() -> super.writeInst(inst));
    }
}
