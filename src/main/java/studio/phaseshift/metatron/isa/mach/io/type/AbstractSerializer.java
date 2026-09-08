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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An {@link MRec}-backed convenience base for {@link Serializer}s whose two
 * sides are <em>not</em> {@link Obj} — i.e. a format-to-format pair such as
 * {@code HTMLMarkdownSerializer} (markdown text ⇄ html text). Extending this
 * class makes the serializer a first-class metatron {@code Rec}: it carries a
 * tid/vid, is configurable via {@code at(...)}, and can be addressed in space,
 * exactly like the Obj serializer family.
 *
 * <p>Serializers that map {@code Obj} to/from an external format should extend
 * {@link AbstractObjSerializer} instead — it anchors {@code A = Obj} on this
 * class ({@code AbstractObjSerializer<T> extends AbstractSerializer<Obj, T>})
 * and layers on the Obj-typed API from {@link ObjSerializer}.
 *
 * @param <A> the canonical/metatron-side type
 * @param <B> the external/encoded type
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractSerializer<A, B> extends MRec implements Serializer<A, B> {

    protected AbstractSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    protected AbstractSerializer(final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), tid, vid);
    }
}
