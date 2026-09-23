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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;

/**
 * The serializer a renderer uses: {@link ObjmtronSerializer} plus one thing — every uri is wrapped
 * in {@code {{link}}}, which is how a renderer that can draw links learns that a value is a uri
 * (graphitty turns the rule into an OSC 8 hyperlink a click can resolve).
 * <p>
 * Deliberately without any of the console's business — no clipping, no pager, no terminal.  A
 * renderer draws for whoever is looking: the console, a log line, a test, another UI, and the
 * console is only one of them.  Tagging uris here rather than in the console's serializer is what
 * keeps {@code Graphitty} from reaching into console code to format an {@code int}: with the
 * tagging in the console's serializer, a low-level type test needed a terminal to build its
 * assertion message.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjLinkSerializer extends ObjmtronSerializer {

    public ObjLinkSerializer() {
        super(true);
    }

    /**
     * This class's instance.
     * <p>
     * Declared rather than inherited, because a factory that is inherited hands out the wrong type:
     * {@link ObjmtronSerializer#single()} answers with a plain serializer whose {@code writeUri}
     * emits the uri as text, so a subclass that only overrides {@code writeUri} is never the one
     * asked to write it.  Whoever subclasses this must do the same.
     */
    private static final ObjLinkSerializer INSTANCE = new ObjLinkSerializer();

    /**
     * The instance every renderer formats objs with (see the class note).
     */
    public static ObjLinkSerializer single() {
        return INSTANCE;
    }

    @Override
    public String write(final Obj obj) {
        if (obj.isInst())
            return this.writeInst(obj.as());
        else if (obj.isUri())
            return this.writeUri(obj.as());
        else
            return super.write(obj);
    }

    @Override
    protected StringBuilder handleVID(final StringBuilder sb, final Obj obj) {
        if (null == obj.vid())
            return sb;
        // through writeUri, not wrapUri: this is a uri written into the output, and a renderer tags
        // uris where the serializer writes them.  Going around it left every vid -- and every type
        // named inside a refinement or a collection -- unclickable while plain uri values were fine
        final fURI vid = Router.loaded() ? Router.global().redirect(obj.vid(), false) : obj.vid();
        return sb.append("{{y}}@{{/y}}").append(writeUriExtension(vid.toUri(), "y", true));
    }

    @Override
    protected StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isInst())
            sb.append(this.writeInst(obj.as()));
        else if (obj.isUri())
            sb.append(this.writeUri(obj.as()));
        else
            return super.writeClip(sb, obj);
        return sb;
    }

    /**
     * An instruction is written as its address, its type and its arguments, and the whole of it is
     * the label of a link whose target is its vid — only the address is a uri, so the explicit
     * target form is what lets all of it answer a click and still follow the right uri.
     */
    @Override
    public String writeInst(final Inst inst) {
        // The body is rendered by the PLAIN serializer, and the one link wraps all of it.  Rendering
        // it here would tag the uri and type inside the instruction, and a link inside a link ends
        // the outer one (graphitty's rule): the first inner link closed this wrapper, so the
        // instruction was not clickable while its arguments were.
        if (Obj.Helper.isAutoPointer(inst)) {
            final fURI pointer = Obj.Helper.getAutoPointer(inst).get();
            return "{{link:" + pointer + "}}" + super.writeInst(inst) + "{{/link}}";
        } else {
            return super.writeInst(inst);
        }
    }

    protected String writeUriExtension(final Uri uri, final String color, boolean big) {
        final String uriString = super.writeUri(uri);
        final boolean quoted = uriString.startsWith("<") && uriString.endsWith(">");
        final StringBuilder sb = new StringBuilder();
        handleTID(sb, uri, true);
        sb.append(quoted ? "<" : "");
        sb.append(uri.c().isOne() ? "" : ("{" + uri.c() + "}"));
        sb.append("{{").append(color).append("}}{{link}}").append(big ? uri.uriValue().one().big() : uri.uriValue().one()).append("{{/link}}{{/").append(color).append("}}");
        sb.append(quoted ? ">" : "");
        return sb.toString();
    }


    @Override
    public String writeUri(final Uri uri) {
        return this.writeUriExtension(uri, "b", false);
    }
}
