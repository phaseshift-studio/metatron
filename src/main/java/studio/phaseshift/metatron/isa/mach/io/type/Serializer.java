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

import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

/**
 * A serializer maps data between two representations of the same information:
 * the canonical/metatron-side type {@code A} (e.g. {@code Obj}, or a markdown
 * document) and the external/encoded type {@code B} (e.g. {@code JsonElement},
 * a html document, raw bytes).
 *
 * <p>The two canonical methods are the encode/decode pair:
 * <ul>
 *     <li>{@link #write(Object)} maps {@code A → B} — serialize the canonical
 *     value <em>out</em> into the external form;</li>
 *     <li>{@link #read(Object)} maps {@code B → A} — parse the external form
 *     <em>back in</em> to the canonical value.</li>
 * </ul>
 * {@link #writeAToB(Object)} and {@link #writeBToA(Object)} are self-documenting
 * aliases of the same two directions (they delegate to {@code write}/{@code read}).
 *
 * <p>The Obj serializer family pins {@code A} to {@code Obj}:
 * {@code ObjSerializer<T> extends Serializer<Obj, T>} — every concrete Obj
 * serializer ({@code ObjJSONSerializer<JsonElement>}, {@code ObjMarkdownSerializer<Node>},
 * ...) is a {@code Serializer<Obj, X>}. A serializer whose two sides are not
 * {@code Obj} (e.g. {@code HTMLMarkdownSerializer}: markdown text ⇄ html text)
 * extends {@link AbstractSerializer} directly with its own {@code A}/{@code B}.
 *
 * <p>Being a {@link Rec} is what makes a serializer a first-class metatron
 * value (tid/vid, {@code at(...)} config, addressability in space). The Obj
 * serializer family carries {@code Rec} on {@link ObjSerializer} itself
 * ({@code ObjSerializer<T> extends Rec, Serializer<Obj, T>}); non-Obj pair
 * serializers get it from {@link AbstractSerializer}, which extends {@link MRec}.
 *
 * @param <A> the canonical/metatron-side type (the {@code write} input / {@code read} output)
 * @param <B> the external/encoded type (the {@code write} output / {@code read} input)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Serializer<A, B> {

    /**
     * Encode: map the canonical value {@code A} into the external form {@code B}.
     */
    B write(final A a) throws MTronException;

    /**
     * Decode: map the external form {@code B} back into the canonical value {@code A}.
     */
    A read(final B b) throws MTronException;

    /**
     * Directional alias of {@link #write(Object)}: {@code A → B}.
     */
    default B writeAToB(final A a) throws MTronException {
        return this.write(a);
    }

    /**
     * Directional alias of {@link #read(Object)}: {@code B → A}.
     */
    default A writeBToA(final B b) throws MTronException {
        return this.read(b);
    }
}
