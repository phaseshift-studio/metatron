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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.AUTHORITY;
import static studio.phaseshift.metatron.Tokens.INSTSET;
import static studio.phaseshift.metatron.Tokens.LINKED_FROM;
import static studio.phaseshift.metatron.Tokens.LINKED_TO;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.SOURCE;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Place — the location coordinate: a machine a chunk runs on, the vertex beside {@code path::T}. It is the Java
 * facade over the {@code place::T} rec — {@link #obj()} is the machine address ({@code //host[/path]}),
 * {@link #authorities()} the transports, and the edge/status accessors resolve the derived {@code auto} insts that
 * {@link Places} hangs off the rec. Like {@link Plan}, it keeps no Java state: every accessor reads
 * {@code this.at(key)} over the backing map.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Place extends MRec {

    public Place(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The machine address — {@code //host[/path]}: who evaluates. {@code host()} is the roster key, {@code path()}
     * the frame vid (empty = the host's root machine).
     */
    public fURI obj() {
        return this.at(OBJ).uriValue();
    }

    /**
     * The transports that reach the machine ({@code scheme://host:port}), sorted.
     */
    public List<fURI> authorities() {
        final Obj authority = this.at(AUTHORITY);
        return authority.isNoObj() ? List.of() : authority.asLst().elements().map(Obj::uriValue).toList();
    }

    /**
     * The ISAs resolvable here (the vocabulary coordinate), empty until §14 joins it.
     */
    public List<fURI> instset() {
        final Obj instset = this.at(INSTSET);
        return instset.isNoObj() ? List.of() : instset.asLst().elements().map(Obj::uriValue).toList();
    }

    /**
     * Who declared this place ({@code mdns}, boot profile, a dial), or null when undeclared.
     */
    public fURI source() {
        final Obj source = this.at(SOURCE);
        return source.isNoObj() ? null : source.uriValue();
    }

    /**
     * The outbound peers — the edges this machine dialed to this place (resolves the derived {@code auto} inst).
     */
    public Obj linkedTo() {
        return this.resolve(LINKED_TO);
    }

    /**
     * The inbound peers — the edges this machine accepted from this place (resolves the derived {@code auto} inst).
     */
    public Obj linkedFrom() {
        return this.resolve(LINKED_FROM);
    }

    /**
     * The liveness lattice — {@code up} / {@code down} / {@code known} (resolves the derived {@code auto} inst), or
     * null when the field is absent.
     */
    public String status() {
        final Obj status = this.resolve(STATUS);
        return status.isNoObj() ? null : status.uriValue().name();
    }

    private Obj resolve(final String key) {
        final Obj auto = this.atDirect(key);
        return auto.isObjInst() ? auto.apply(noobj()) : auto;
    }

    /**
     * Reconstruct a Place from a raw {@code rec} — the read-side of the same {@code place::T} encoding.
     */
    public static Place of(final Rec rec) {
        return new Place(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }
}
