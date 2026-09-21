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

import studio.phaseshift.metatron.isa.m.type.Uri;

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

    /** The instance every renderer formats objs with (see the class note). */
    public static ObjLinkSerializer single() {
        return INSTANCE;
    }

    @Override
    public String writeUri(final Uri uri) {
        final String uriString = super.writeUri(uri);
        final boolean quoted = uriString.startsWith("<") && uriString.endsWith(">");
        return (quoted ? "<" : "") + "{{link}}"
                + (quoted ? uriString.substring(1, uriString.length() - 1) : uriString)
                + "{{/link}}" + (quoted ? ">" : "");
    }
}
