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

package studio.phaseshift.metatron.isa.grph.space;

import org.apache.tinkerpop.gremlin.structure.Element;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A Rec backed by a live TinkerPop {@link Element} (Vertex or Edge).
 * Properties are materialized into the JVM at construction time, and the
 * element reference is kept for write-back and traversal purposes.
 * <p>
 * The routed VID is set at construction and preserved through
 * {@link #self(Object, fURI, fURI)} so instruction-call traversals
 * can route back to the graph space even after type construction.
 */
public class ElementRec<T extends Element> extends MRec {

    protected final T element;
    protected final grphSpace space;

    public ElementRec(final T element, final grphSpace space,
                      final fURI tid, final fURI vid) {
        // Bypass MObj(Map,tid,vid) constructor — its objCheckAndSave(this)
        // writes to the space, which creates another VertexRec, ad infinitum.
        this.element = element;
        this.space = space;
        this.jvm = new ElementMap<>(this.element); //Rec.Helper.cleanMap(materialize(element));
        this.tid = tid;
        this.vid = vid;
    }

    /**
     * Copy TinkerPop properties into a plain LinkedHashMap.
     */
   /* private static Map<Obj, Obj> materialize(final Element element) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        element.properties().forEachRemaining(p -> map.put(uri(p.key()), MObjFactory.of().toObj(p.value())));
        return map;
    }*/
    public T element() {
        return this.element;
    }

    public grphSpace space() {
        return this.space;
    }

    public fURI elementVID() {
        return null != this.vid ? this.vid : this.space.elementVID(this.element);
    }

    /** auto_from_ calls .vid(null) which triggers self(jvm, tid, null) —
     *  reject null VID and keep the routed element VID. */
    /*@Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return super.self(Rec.Helper.cleanMap((Map<Obj, Obj>) jvm), tid, vid != null ? vid : this.vid);
    }*/

    /** Preserve ElementRec type through cloning so {@link #vid(fURI)} override
     *  survives and rejects the null VID set by auto_from_.vid(null). */
   /* @Override
    @SuppressWarnings("unchecked")
    public Rec clone() {
        //this.jvm = new ElementMap<T>(this.element);
        final ElementRec<T> clone = (ElementRec<T>) super.clone();
        //clone.jvm = new LinkedHashMap<>((Map<Obj, Obj>) this.jvm);
        return clone;
    }*/
}
